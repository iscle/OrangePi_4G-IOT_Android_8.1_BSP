/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.task.CrossProfileIntentFilter.Direction;

import org.junit.Test;

/**
 * Unit tests for {@link CrossProfileIntentFilter}.
 */
@SmallTest
public class CrossProfileIntentFilterTest {

    private static final int TEST_FLAGS = PackageManager.SKIP_CURRENT_PROFILE;
    private static final int TEST_DIRECTION = Direction.TO_PARENT;
    private static final String ACTION_1 = "action1";
    private static final String ACTION_2 = "action2";
    private static final String CATEGORY_1 = "category1";
    private static final String CATEGORY_2 = "category2";
    private static final String SCHEME_1 = "scheme1";
    private static final String SCHEME_2 = "scheme2";
    private static final String TYPE_1 = "*/*";
    private static final String TYPE_2 = "com.test/*";

    @Test
    public void testBuilder() {
        CrossProfileIntentFilter filter =
                new CrossProfileIntentFilter.Builder(TEST_DIRECTION, TEST_FLAGS)
                        .addAction(ACTION_1)
                        .addAction(ACTION_2)
                        .addCategory(CATEGORY_1)
                        .addCategory(CATEGORY_2)
                        .addDataScheme(SCHEME_1)
                        .addDataScheme(SCHEME_2)
                        .addDataType(TYPE_1)
                        .addDataType(TYPE_2)
                        .build();

        assertEquals(TEST_DIRECTION, filter.direction);
        assertEquals(TEST_FLAGS, filter.flags);

        assertEquals(ACTION_1, filter.filter.getAction(0));
        assertEquals(ACTION_2, filter.filter.getAction(1));
        assertEquals(CATEGORY_1, filter.filter.getCategory(0));
        assertEquals(CATEGORY_2, filter.filter.getCategory(1));
        assertEquals(SCHEME_1, filter.filter.getDataScheme(0));
        assertEquals(SCHEME_2, filter.filter.getDataScheme(1));
        assertTrue(filter.filter.hasDataType(TYPE_1));
        assertTrue(filter.filter.hasDataType(TYPE_2));
    }
}
