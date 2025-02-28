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

package org.apache.kylin.metadata.filter;

import java.nio.ByteBuffer;

import org.apache.kylin.common.util.BytesUtil;

/**
 * A simple code system where all values are strings and conform to string comparison system.
 *
 * @author yangli9
 */
public class StringCodeSystem implements IFilterCodeSystem<String> {

    public static final StringCodeSystem INSTANCE = new StringCodeSystem();

    protected StringCodeSystem() {
        // singleton
    }

    @Override
    public boolean isNull(String value) {
        return value == null;
    }

    @Override
    public int compare(String tupleValue, String constValue) {
        return tupleValue.compareTo(constValue);
    }

    @Override
    public void serialize(String value, ByteBuffer buffer) {
        BytesUtil.writeUTFString(value, buffer);
    }

    @Override
    public String deserialize(ByteBuffer buffer) {
        return BytesUtil.readUTFString(buffer);
    }

}
