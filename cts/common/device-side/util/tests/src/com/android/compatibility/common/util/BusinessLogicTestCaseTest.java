/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.compatibility.common.util;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for {@line BusinessLogicTestCase}.
 */
@RunWith(AndroidJUnit4.class)
public class BusinessLogicTestCaseTest {

    private static final String KEY_1 = "key1";
    private static final String KEY_2 = "key2";
    private static final String VALUE_1 = "value1";
    private static final String VALUE_2 = "value2";

    DummyTest mDummyTest;
    DummyTest mOtherDummyTest;

    @Before
    public void setUp() {
        mDummyTest = new DummyTest();
        mOtherDummyTest = new DummyTest();
    }

    @Test
    public void testMapPut() throws Exception {
        mDummyTest.mapPut("instanceMap", KEY_1, VALUE_1);
        assertTrue("mapPut failed for instanceMap", mDummyTest.instanceMap.containsKey(KEY_1));
        assertEquals("mapPut failed for instanceMap", mDummyTest.instanceMap.get(KEY_1), VALUE_1);
        assertTrue("mapPut affected wrong instance", mOtherDummyTest.instanceMap.isEmpty());
    }

    @Test
    public void testStaticMapPut() throws Exception {
        mDummyTest.mapPut("staticMap", KEY_2, VALUE_2);
        assertTrue("mapPut failed for staticMap", mDummyTest.staticMap.containsKey(KEY_2));
        assertEquals("mapPut failed for staticMap", mDummyTest.staticMap.get(KEY_2), VALUE_2);
        assertTrue("mapPut on static map should affect all instances",
                mOtherDummyTest.staticMap.containsKey(KEY_2));
    }

    public static class DummyTest extends BusinessLogicTestCase {
        public Map<String, String> instanceMap = new HashMap<>();
        public static Map<String, String> staticMap = new HashMap<>();
    }
}
