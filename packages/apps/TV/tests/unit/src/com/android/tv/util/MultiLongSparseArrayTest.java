/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.support.test.filters.SmallTest;
import android.test.MoreAsserts;

import org.junit.Test;

import java.util.Collections;

/**
 * Tests for {@link MultiLongSparseArray}.
 */
@SmallTest
public class MultiLongSparseArrayTest {
    @Test
    public void testEmpty() {
        MultiLongSparseArray<String> sparseArray = new MultiLongSparseArray<>();
        assertSame(Collections.EMPTY_SET, sparseArray.get(0));
    }

    @Test
    public void testOneElement() {
        MultiLongSparseArray<String> sparseArray = new MultiLongSparseArray<>();
        sparseArray.put(0, "foo");
        MoreAsserts.assertContentsInAnyOrder(sparseArray.get(0), "foo");
    }

    @Test
    public void testTwoElements() {
        MultiLongSparseArray<String> sparseArray = new MultiLongSparseArray<>();
        sparseArray.put(0, "foo");
        sparseArray.put(0, "bar");
        MoreAsserts.assertContentsInAnyOrder(sparseArray.get(0), "foo", "bar");
    }


    @Test
    public void testClearEmptyCache() {
        MultiLongSparseArray<String> sparseArray = new MultiLongSparseArray<>();
        sparseArray.clearEmptyCache();
        assertEquals(0, sparseArray.getEmptyCacheSize());
        sparseArray.put(0, "foo");
        sparseArray.remove(0, "foo");
        assertEquals(1, sparseArray.getEmptyCacheSize());
        sparseArray.clearEmptyCache();
        assertEquals(0, sparseArray.getEmptyCacheSize());
    }

    @Test
    public void testMaxEmptyCacheSize() {
        MultiLongSparseArray<String> sparseArray = new MultiLongSparseArray<>();
        sparseArray.clearEmptyCache();
        assertEquals(0, sparseArray.getEmptyCacheSize());
        for (int i = 0; i <= MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT + 2; i++) {
            sparseArray.put(i, "foo");
        }
        for (int i = 0; i <= MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT + 2; i++) {
            sparseArray.remove(i, "foo");
        }
        assertEquals(MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT,
                sparseArray.getEmptyCacheSize());
        sparseArray.clearEmptyCache();
        assertEquals(0, sparseArray.getEmptyCacheSize());
    }

    @Test
    public void testReuseEmptySets() {
        MultiLongSparseArray<String> sparseArray = new MultiLongSparseArray<>();
        sparseArray.clearEmptyCache();
        assertEquals(0, sparseArray.getEmptyCacheSize());
        // create a bunch of sets
        for (int i = 0; i <= MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT + 2; i++) {
            sparseArray.put(i, "foo");
        }
        // remove them so they are all put in the cache.
        for (int i = 0; i <= MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT + 2; i++) {
            sparseArray.remove(i, "foo");
        }
        assertEquals(MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT,
                sparseArray.getEmptyCacheSize());

        // now create elements, that use the cached empty sets.
        for (int i = 0; i < MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT; i++) {
            sparseArray.put(10 + i, "bar");
            assertEquals(MultiLongSparseArray.DEFAULT_MAX_EMPTIES_KEPT - i - 1,
                    sparseArray.getEmptyCacheSize());
        }
    }
}
