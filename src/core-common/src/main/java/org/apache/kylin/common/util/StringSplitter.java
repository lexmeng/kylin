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

package org.apache.kylin.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * @author George Song (ysong1)
 *
 */
public class StringSplitter {
    public static String[] split(String str, String delimiter) {
        // The optimized split function
        List<String> list = new ArrayList<String>();
        int index = 0, offset = 0;
        int l = delimiter.length();
        if (str.startsWith(delimiter)) {
            // in case the first field is empty
            list.add("");
            offset = offset + l;
        }
        while ((index = str.indexOf(delimiter, index + 1)) != -1) {
            list.add(str.substring(offset, index));
            offset = index + l;
        }
        // add the last field, or the str doesn't contain delimiter at all
        list.add(str.substring(offset));
        return list.toArray(new String[0]);
    }
}
