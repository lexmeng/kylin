/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.engine.spark.job.stage.build.partition

import com.google.common.collect.{Lists, Queues, Sets}
import org.apache.kylin.common.persistence.transaction.UnitOfWork
import org.apache.kylin.common.persistence.transaction.UnitOfWork.Callback
import org.apache.kylin.common.util.HadoopUtil
import org.apache.kylin.engine.spark.job.stage.BuildParam
import org.apache.kylin.engine.spark.job.stage.build.BuildStage
import org.apache.kylin.engine.spark.job.stage.build.FlatTableAndDictBase.Statistics
import org.apache.kylin.engine.spark.job.{KylinBuildEnv, PartitionExec, SanityChecker, SegmentJob}
import org.apache.kylin.engine.spark.model.PartitionFlatTableDesc
import org.apache.kylin.metadata.cube.cuboid.PartitionSpanningTree
import org.apache.kylin.metadata.cube.cuboid.PartitionSpanningTree.PartitionTreeNode
import org.apache.kylin.metadata.cube.model._
import org.apache.kylin.metadata.model.TblColRef
import org.apache.spark.sql.datasource.storage.StorageStoreUtils
import org.apache.spark.sql.{Dataset, Row}

import java.util.Objects
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.forkjoin.ForkJoinPool

abstract class PartitionBuildStage(jobContext: SegmentJob, dataSegment: NDataSegment, buildParam: BuildParam)
  extends BuildStage(jobContext, dataSegment, buildParam) with PartitionExec {
  protected final val newBuckets = //
    jobContext.getReadOnlyBuckets.asScala.filter(_.getSegmentId.equals(segmentId)).toSeq

  protected lazy val spanningTree: PartitionSpanningTree = buildParam.getPartitionSpanningTree

  protected lazy val flatTableDesc: PartitionFlatTableDesc = buildParam.getTableDesc
  protected lazy val flatTable: PartitionFlatTableAndDictBase = buildParam.getPartitionFlatTable

  // Thread unsafe, read only.
  private lazy val cachedPartitionFlatTableDS: Map[java.lang.Long, Dataset[Row]] = buildParam.getCachedPartitionFlatTableDS

  // Thread unsafe, read only
  private lazy val cachedPartitionFlatTableStats: Map[java.lang.Long, Statistics] = buildParam.getCachedPartitionFlatTableStats

  // Thread unsafe
  // [layout, [partition, dataset]]
  private val cachedPartitionDS = mutable.HashMap[Long, mutable.HashMap[Long, Dataset[Row]]]()

  // Thread unsafe
  // [layout, [partition, sanity]]
  private var cachedPartitionSanity: Option[mutable.HashMap[Long, mutable.HashMap[Long, Long]]] = None

  override protected def columnIdFunc(colRef: TblColRef): String = flatTableDesc.getColumnIdAsString(colRef)

  override protected def buildLayouts(): Unit = {
    // Flat table? Sanity cache?
    //    beforeBuildLayouts()

    // Maintain this variable carefully.
    var remainingTaskCount = 0

    // Share failed task 'throwable' with main thread.
    val failQueue = Queues.newLinkedBlockingQueue[Option[Throwable]]()

    // Main loop: build layouts.
    while (spanningTree.nonSpanned()) {
      if (resourceContext.isAvailable) {
        // Drain immediately.
        drain()
        // Use the latest data segment.
        val segment = jobContext.getSegment(segmentId)
        val nodes = spanningTree.span(segment).asScala
        val tasks = nodes.flatMap(node => getPartitionTasks(segment, node.asInstanceOf[PartitionTreeNode]))
        remainingTaskCount += tasks.size
        // Submit tasks, no task skip inside this foreach.
        tasks.foreach { task =>
          runtime.submit(() => try {
            // If unset
            setConfig4CurrentThread()
            // Build layout.
            buildPartition(task)
            // Offer 'Node' if everything was well.
            failQueue.offer(None)
          } catch {
            // Offer 'Throwable' if unexpected things happened.
            case t: Throwable => failQueue.offer(Some(t))
          })
        }
        if (0 != tasks.size) {
          KylinBuildEnv.get().buildJobInfos.recordCuboidsNumPerLayer(segmentId, tasks.size)

          val layoutCount = KylinBuildEnv.get().buildJobInfos.getSeg2cuboidsNumPerLayer.get(segmentId).asScala.sum
          onBuildLayoutSuccess(layoutCount / partitions.size())
        }
      }
      // Poll and fail fast.
      remainingTaskCount -= failFastPoll(failQueue, 3L, TimeUnit.SECONDS)
    }
    // Await or fail fast.
    while (remainingTaskCount > 0) {
      remainingTaskCount -= failFastPoll(failQueue)
    }
    // Drain immediately after all layouts built.
    drain()
  }

  protected def buildPartitionStatistics(partition: Long //
                                         , partitionFlatTableDS: Dataset[Row]): Statistics = {
    // Maybe exist metadata operations.
    setConfig4CurrentThread()
    flatTable.gatherPartitionStatistics(partition, partitionFlatTableDS)
  }

  override protected def buildSanityCache(): Unit = {
    if (!config.isSanityCheckEnabled) {
      return
    }
    // Collect statistics for root nodes.
    val rootNodes = spanningTree.getRootNodes.asScala.map(_.asInstanceOf[PartitionTreeNode])
    if (rootNodes.isEmpty) {
      return
    }

    logInfo(s"Segment $segmentId build sanity cache.")
    val parallel = rootNodes.map { node =>
      val layout = node.getLayout
      val partition = node.getPartition
      val partitionDS = getCachedPartitionDS(dataSegment, layout, partition)
      (layout, partition, partitionDS)
    }.par
    val processors = Runtime.getRuntime.availableProcessors
    val forkJoinPool = new ForkJoinPool(Math.max(processors, rootNodes.size / 8))
    try {
      parallel.tasksupport = new ForkJoinTaskSupport(forkJoinPool)
      val collected = parallel.map { case (layout, partition, partitionDS) =>
        val sanityCount = SanityChecker.getCount(partitionDS, layout)
        (layout.getId, partition, sanityCount)
      }.seq
      val sanityMap = mutable.HashMap[Long, mutable.HashMap[Long, Long]]()
      collected.foreach { case (layout, partition, sanityCount) =>
        sanityMap.getOrElseUpdate(layout, mutable.HashMap[Long, Long]()).put(partition, sanityCount)
      }
      assert(collected.size == rootNodes.size, //
        s"Collect sanity for root nodes went wrong: ${collected.size} == ${rootNodes.size}")
      cachedPartitionSanity = Some(sanityMap)
      logInfo(s"Segment $segmentId finished build sanity cache $sanityMap.")
    } finally {
      forkJoinPool.shutdownNow()
    }
  }

  private def getPartitionTasks(segment: NDataSegment, node: PartitionTreeNode): Seq[PartitionBuildTask] = {
    val layouts = node.getLayouts.asScala // skip layouts
      .filterNot(layout => needSkipPartition(layout.getId, node.getPartition, segment))
    if (layouts.isEmpty) {
      return Seq.empty
    }
    val sanityCount = getCachedPartitionSanity(node)
    if (node.parentIsNull) {
      // Build from flat table
      layouts.map { layout =>
        PartitionBuildTask(layout, node.getPartition, None, cachedPartitionFlatTableDS(node.getPartition), sanityCount, segment)
      }
    } else {
      // Build from data layout
      val parentLayout = node.getParent.getLayout
      val parentDS = getCachedPartitionDS(segment, parentLayout, node.getPartition)

      layouts.map { layout =>
        PartitionBuildTask(layout, node.getPartition, Some(parentLayout), parentDS, sanityCount, segment)
      }
    }
  }

  private def getCachedPartitionSanity(node: PartitionTreeNode): Long = {
    // Not enabled.
    if (!config.isSanityCheckEnabled) {
      return SanityChecker.SKIP_FLAG
    }

    // From flat table.
    if (Objects.isNull(node.getRootNode)) {
      assert(cachedPartitionFlatTableStats.contains(node.getPartition), //
        s"Partition flat tale statistics should have been cached: ${node.getPartition}")
      return cachedPartitionFlatTableStats(node.getPartition).totalCount
    }

    // From data partition.
    if (cachedPartitionSanity.isEmpty) {
      return SanityChecker.SKIP_FLAG
    }
    val rootNode = node.getRootNode.asInstanceOf[PartitionTreeNode]
    val cachedLayout = cachedPartitionSanity.get
    val layout = rootNode.getLayout
    assert(cachedLayout.contains(layout.getId), //
      s"Root node's layout sanity should have been cached: ${layout.getId}")
    val cachedPartition = cachedLayout(layout.getId)
    assert(cachedPartition.contains(rootNode.getPartition), //
      s"Root node's layout partition sanity should have been cached: ${layout.getId} ${rootNode.getPartition}")
    cachedPartition(rootNode.getPartition)
  }

  private def getCachedPartitionDS(segment: NDataSegment, //
                                   layout: LayoutEntity, //
                                   partition: Long): Dataset[Row] = synchronized {
    cachedPartitionDS.getOrElseUpdate(layout.getId, // or update
      mutable.HashMap[Long, Dataset[Row]]()).getOrElseUpdate(partition, // or update
      StorageStoreUtils.toDF(segment, layout, partition, sparkSession))
  }

  sealed case class PartitionBuildTask(layout: LayoutEntity //
                                       , partition: Long //
                                       , parentLayout: Option[LayoutEntity] //
                                       , parentDS: Dataset[Row] //
                                       , sanityCount: Long //
                                       , segment: NDataSegment = dataSegment)


  private def buildPartition(task: PartitionBuildTask): Unit = {
    val layoutDS = wrapLayoutDS(task.layout, task.parentDS)
    val parentDesc = if (task.parentLayout.isEmpty) "flat table" else task.parentLayout.get.getId
    val readableDesc = s"Segment $segmentId build layout partition ${task.layout.getId},${task.partition} from $parentDesc"
    // set layout mess in build job infos
    val layout: NDataLayout = if (task.parentLayout.isEmpty) null
    else task.segment.getLayout(task.parentLayout.get.getId)
    val layoutIdsFromFlatTable = KylinBuildEnv.get().buildJobInfos.getParent2Children.getOrDefault(layout, Sets.newHashSet())
    layoutIdsFromFlatTable.add(task.layout.getId)
    KylinBuildEnv.get().buildJobInfos.recordParent2Children(layout, layoutIdsFromFlatTable)
    newLayoutPartition(task.segment, task.layout, task.partition, layoutDS, readableDesc, Some(new SanityChecker(task.sanityCount)))
  }

  private def needSkipPartition(layout: Long, partition: Long, segment: NDataSegment = dataSegment): Boolean = {
    // Check layout data.
    val dataLayout = segment.getLayout(layout)
    if (Objects.isNull(dataLayout)) {
      return false
    }

    // Check partition data.
    val dataPartition = dataLayout.getDataPartition(partition)
    if (Objects.isNull(dataPartition)) {
      return false
    }

    // Check job id.
    if (jobId.equals(dataPartition.getBuildJobId)) {
      logInfo(s"Segment $segmentId skip build layout partition $layout $partition")
      return true
    }
    false
  }

  override protected def tryRefreshColumnBytes(): Unit = {
    if (cachedPartitionFlatTableStats.isEmpty) {
      logInfo(s"Segment $segmentId skip refresh column bytes.")
      return
    }
    logInfo(s"Segment $segmentId refresh column bytes.")
    val partitionStats = cachedPartitionFlatTableStats
    UnitOfWork.doInTransactionWithRetry(new Callback[Unit] {
      override def process(): Unit = {
        val dataflowManager = NDataflowManager.getInstance(config, project);
        val copiedDataflow = dataflowManager.getDataflow(dataflowId).copy()
        val copiedSegment = copiedDataflow.getSegment(segmentId)
        val dataflowUpdate = new NDataflowUpdate(dataflowId)
        val newAdds = Lists.newArrayList[SegmentPartition]()
        partitionStats.foreach { case (partitionId, stats) => //
          val segmentPartition = newSegmentPartition(copiedSegment, partitionId, newAdds)
          segmentPartition.setSourceCount(stats.totalCount)
          // By design, no fencing.
          val columnBytes = segmentPartition.getColumnSourceBytes
          stats.columnBytes.foreach(kv => columnBytes.put(kv._1, kv._2))
        }
        copiedSegment.getMultiPartitions.addAll(newAdds)
        mergeSegmentStatistics(copiedSegment)
        dataflowUpdate.setToUpdateSegs(copiedSegment)
        // The afterward step would dump the meta to hdfs-store.
        // We should only update the latest meta in mem-store.
        // Make sure the copied dataflow here is the latest.
        dataflowManager.updateDataflow(dataflowUpdate)
      }
    }, project)
  }

  override protected def cleanup(): Unit = {

    super.cleanup()

    // Cleanup extra files.
    val fs = HadoopUtil.getWorkingFileSystem
    // Fact table view.
    val ftvPath = flatTableDesc.getFactTableViewPath
    if (fs.exists(ftvPath)) {
      fs.delete(ftvPath, true)
    }

    // Flat table.
    val ftPath = flatTableDesc.getFlatTablePath
    if (fs.exists(ftPath)) {
      fs.delete(ftPath, true)
    }
  }
}
