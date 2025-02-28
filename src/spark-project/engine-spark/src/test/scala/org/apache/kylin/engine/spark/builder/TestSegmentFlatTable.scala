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

package org.apache.kylin.engine.spark.builder

import org.apache.kylin.common.KylinConfig
import org.apache.kylin.engine.spark.model.SegmentFlatTableDesc
import org.apache.kylin.metadata.cube.cuboid.AdaptiveSpanningTree
import org.apache.kylin.metadata.cube.cuboid.AdaptiveSpanningTree.AdaptiveTreeBuilder
import org.apache.kylin.metadata.cube.model._
import org.apache.kylin.metadata.model.{SegmentRange, TableDesc, TableRef}
import org.apache.spark.SparkExecutorInfo
import org.apache.spark.sql.common.{LocalMetadata, SharedSparkSession, SparderBaseFunSuite}
import org.mockito.Mockito

import scala.collection.JavaConverters._


class TestSegmentFlatTable extends SparderBaseFunSuite with SharedSparkSession with LocalMetadata {

  private val PROJECT = "infer_filter"
  private val MODEL_NAME1 = "89af4ee2-2cdb-4b07-b39e-4c29856309ab"

  def getTestConfig: KylinConfig = {
    KylinConfig.getInstanceFromEnv
  }

  test("testSegmentFlatTable") {
    getTestConfig.setProperty("kylin.engine.persist-flattable-enabled", "false")
    getTestConfig.setProperty("kylin.engine.count.lookup-table-max-time", "0")
    getTestConfig.setProperty("kylin.source.record-source-usage-enabled", "false")

    val dfMgr: NDataflowManager = NDataflowManager.getInstance(getTestConfig, PROJECT)
    val df: NDataflow = dfMgr.getDataflow(MODEL_NAME1)
    // cleanup all segments first
    val update = new NDataflowUpdate(df.getUuid)
    update.setToRemoveSegsWithArray(df.getSegments.asScala.toArray)
    dfMgr.updateDataflow(update)

    val seg = dfMgr.appendSegment(df, new SegmentRange.TimePartitionedSegmentRange(0L, 1356019200000L))
    val toBuildTree = new AdaptiveSpanningTree(getTestConfig, new AdaptiveTreeBuilder(seg, seg.getIndexPlan.getAllLayouts))
    val flatTableDesc = new SegmentFlatTableDesc(getTestConfig, seg, toBuildTree)
    val flatTable = new SegmentFlatTable(spark, flatTableDesc)
    assert(flatTable.newTableDS(df.getModel.getAllTables.iterator().next()) != null)

    val tableRef: TableRef = df.getModel.getAllTables.iterator().next()
    val tableDesc: TableDesc = tableRef.getTableDesc
    tableDesc.setRangePartition(true)
    val ref = new TableRef(df.getModel, tableDesc.getName, tableDesc, false)
    assert(flatTable.newTableDS(ref) != null)
  }

  test("waitTillWorkerRegistered") {
    getTestConfig.setProperty("kylin.engine.persist-flattable-enabled", "false")
    getTestConfig.setProperty("kylin.engine.count.lookup-table-max-time", "0")
    getTestConfig.setProperty("kylin.source.record-source-usage-enabled", "false")

    val project1 = "default"
    val model1 = "abe3bf1a-c4bc-458d-8278-7ea8b00f5e96"

    val dfMgr: NDataflowManager = NDataflowManager.getInstance(getTestConfig, project1)
    val df: NDataflow = dfMgr.getDataflow(model1)
    // cleanup all segments first
    val update = new NDataflowUpdate(df.getUuid)
    update.setToRemoveSegsWithArray(df.getSegments.asScala.toArray)
    dfMgr.updateDataflow(update)

    val seg = dfMgr.appendSegment(df, new SegmentRange.TimePartitionedSegmentRange(0L, 1356019200000L))
    val toBuildTree = new AdaptiveSpanningTree(getTestConfig, new AdaptiveTreeBuilder(seg, seg.getIndexPlan.getAllLayouts))
    val flatTableDesc = new SegmentFlatTableDesc(getTestConfig, seg, toBuildTree)

    val spiedSparkSession = Mockito.spy(spark)
    val spiedSparkContext = Mockito.spy(spark.sparkContext)
    val spiedTracker = Mockito.spy(spark.sparkContext.statusTracker)
    val flatTable = new SegmentFlatTable(spiedSparkSession, flatTableDesc)
    Mockito.when(spiedSparkSession.sparkContext).thenReturn(spiedSparkContext)
    Mockito.when(spiedSparkContext.statusTracker).thenReturn(spiedTracker)
    Mockito.when(spiedTracker.getExecutorInfos)
      .thenReturn(Array.empty[SparkExecutorInfo])
      .thenCallRealMethod()
    flatTable.waitTillWorkerRegistered()
  }
}
