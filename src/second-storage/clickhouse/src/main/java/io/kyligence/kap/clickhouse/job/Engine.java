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

package io.kyligence.kap.clickhouse.job;

import java.io.File;
import java.net.URI;

public class Engine {
    public static final String DEFAULT = "MergeTree()";

    final TableEngineType tableEngineType;
    final URI rootPath;
    final String siteURL;
    final TableSourceType tableSourceType;

    public Engine(TableEngineType tableEngineType, String rootPath, String siteURL, TableSourceType tableSourceType) {
        this.tableEngineType = tableEngineType;
        this.tableSourceType = tableSourceType;
        if (tableSourceType == TableSourceType.UT) {
            this.rootPath = new File(rootPath).toURI();
            this.siteURL = siteURL;
        } else {
            this.rootPath = null;
            this.siteURL = siteURL;
        }
    }

    public String apply(String path) {
        return tableSourceType.transformFileUrl(path, siteURL, rootPath);
    }
}
