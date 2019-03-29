/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.compatibility.common.tradefed.util;

import junit.framework.TestCase;

/**
 * Unit tests for {@link CollectorUtil}
 */
public class CollectorUtilTest extends TestCase {

    private static final String UNFORMATTED_JSON = "{"
            + "\"stream_name_1\":"
            + "{\"id\":1,\"key1\":\"value1\"},"
            + "\"stream_name_2\":"
            + "{\"id\":1,\"key1\":\"value3\"},"
            + "\"stream_name_1\":"
            + "{\"id\":2,\"key1\":\"value2\"},"
            + "}";

    private static final String REFORMATTED_JSON = "{"
            + "\"stream_name_2\":"
            + "["
            + "{\"id\":1,\"key1\":\"value3\"}"
            + "],"
            + "\"stream_name_1\":"
            + "["
            + "{\"id\":1,\"key1\":\"value1\"},"
            + "{\"id\":2,\"key1\":\"value2\"}"
            + "]"
            + "}";

    public void testReformatJsonString() throws Exception {
        String reformattedJson = CollectorUtil.reformatJsonString(UNFORMATTED_JSON);
        assertEquals(reformattedJson, REFORMATTED_JSON);
    }
}
