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
package org.apache.kylin.rest.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.persistence.ResourceStore;
import org.apache.kylin.common.util.HadoopUtil;
import org.apache.kylin.common.util.SetThreadName;
import org.apache.kylin.tool.HDFSMetadataTool;
import org.apache.kylin.tool.MetadataTool;
import org.springframework.stereotype.Service;

import lombok.val;

@Service
public class MetadataBackupService {

    private ExecutorService executors = Executors.newSingleThreadExecutor();

    public void backupAll() throws Exception {

        try (SetThreadName ignored = new SetThreadName("MetadataBackupWorker")) {
            String[] args = new String[] { "-backup", "-compress", "-dir", getBackupDir() };
            backup(args);
            executors.submit(this::rotateAuditLog);
        }
    }

    public void backup(String[] args) throws Exception {
        val kylinConfig = KylinConfig.getInstanceFromEnv();
        HDFSMetadataTool.cleanBeforeBackup(kylinConfig);
        val metadataTool = new MetadataTool(kylinConfig);
        metadataTool.execute(args);
    }

    public void rotateAuditLog() {
        val kylinConfig = KylinConfig.getInstanceFromEnv();
        val resourceStore = ResourceStore.getKylinMetaStore(kylinConfig);
        val auditLogStore = resourceStore.getAuditLogStore();
        auditLogStore.rotate();
    }

    public String backupProject(String project) throws Exception {
        val folder = LocalDateTime.now(Clock.systemDefaultZone()).format(MetadataTool.DATE_TIME_FORMATTER) + "_backup";
        String[] args = new String[] { "-backup", "-compress", "-project", project, "-folder", folder, "-dir",
                getBackupDir() };
        backup(args);
        return StringUtils.appendIfMissing(getBackupDir(), "/") + folder;
    }

    private String getBackupDir() {
        return HadoopUtil.getBackupFolder(KylinConfig.getInstanceFromEnv());

    }

}
