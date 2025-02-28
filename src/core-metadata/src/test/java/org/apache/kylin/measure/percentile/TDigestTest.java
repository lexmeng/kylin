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

package org.apache.kylin.measure.percentile;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.apache.kylin.common.util.MathUtil;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.tdunning.math.stats.TDigest;

@Ignore
public class TDigestTest {
    @Test
    public void testBasic() {
        int times = 1;
        int compression = 100;
        for (int t = 0; t < times; t++) {
            TDigest tDigest = TDigest.createAvlTreeDigest(compression);
            Random random = new Random();
            int dataSize = 10000;
            List<Double> dataset = Lists.newArrayListWithCapacity(dataSize);
            for (int i = 0; i < dataSize; i++) {
                double d = random.nextDouble();
                tDigest.add(d);
                dataset.add(d);
            }
            Collections.sort(dataset);

            double actualResult = tDigest.quantile(0.5);
            double expectedResult = MathUtil.findMedianInSortedList(dataset);
            assertEquals(expectedResult, actualResult, 0.01);
        }
    }
}
