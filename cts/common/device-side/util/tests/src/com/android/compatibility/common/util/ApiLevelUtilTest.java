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
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.os.Build;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@line ApiLevelUtil}.
 */
@RunWith(AndroidJUnit4.class)
public class ApiLevelUtilTest {

    @Test
    public void testComparisonByInt() throws Exception {
        int version = Build.VERSION.SDK_INT;

        assertFalse(ApiLevelUtil.isBefore(version - 1));
        assertFalse(ApiLevelUtil.isBefore(version));
        assertTrue(ApiLevelUtil.isBefore(version + 1));

        assertTrue(ApiLevelUtil.isAfter(version - 1));
        assertFalse(ApiLevelUtil.isAfter(version));
        assertFalse(ApiLevelUtil.isAfter(version + 1));

        assertTrue(ApiLevelUtil.isAtLeast(version - 1));
        assertTrue(ApiLevelUtil.isAtLeast(version));
        assertFalse(ApiLevelUtil.isAtLeast(version + 1));

        assertFalse(ApiLevelUtil.isAtMost(version - 1));
        assertTrue(ApiLevelUtil.isAtMost(version));
        assertTrue(ApiLevelUtil.isAtMost(version + 1));
    }

    @Test
    public void testComparisonByString() throws Exception {
        // test should pass as long as device SDK version is at least 12
        assertTrue(ApiLevelUtil.isAtLeast("HONEYCOMB_MR1"));
        assertTrue(ApiLevelUtil.isAtLeast("12"));
    }

    @Test
    public void testResolveVersionString() throws Exception {
        // can only test versions known to the device build
        assertEquals(ApiLevelUtil.resolveVersionString("GINGERBREAD_MR1"), 10);
        assertEquals(ApiLevelUtil.resolveVersionString("10"), 10);
        assertEquals(ApiLevelUtil.resolveVersionString("HONEYCOMB"), 11);
        assertEquals(ApiLevelUtil.resolveVersionString("11"), 11);
        assertEquals(ApiLevelUtil.resolveVersionString("honeycomb_mr1"), 12);
        assertEquals(ApiLevelUtil.resolveVersionString("12"), 12);
    }

    @Test(expected = RuntimeException.class)
    public void testResolveMisspelledVersionString() throws Exception {
        ApiLevelUtil.resolveVersionString("GINGERBEARD");
    }
}
