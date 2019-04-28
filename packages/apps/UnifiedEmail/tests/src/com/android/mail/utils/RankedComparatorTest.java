/**
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.google.android.mail.common.base.Function;

@SmallTest
public class RankedComparatorTest extends AndroidTestCase {

    private static final String RANK1 = "rank1";
    private static final String RANK2 = "rank2";
    private static final String[] RANKS = new String[]{RANK1, RANK2};
    private static final String UNKNOWN_RANK1 = "unknown_rank_1";
    private static final String UNKNOWN_RANK2 = "unknown_rank_2";
    private static final String NULL_RANK = null;

    private RankedComparator<DummyObject, String> comparator;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        comparator =
                new RankedComparator<DummyObject, String>(RANKS, DUMMY_OBJECT_TO_RANK_FUNCTION);
    }

    public void testSimple() {
        DummyObject rank1_1 = new DummyObject(RANK1);
        DummyObject rank1_2 = new DummyObject(RANK1);
        DummyObject rank2 = new DummyObject(RANK2);

        assertTrue("Same object should be equal to itself.",
                comparator.compare(rank1_1, rank1_1) == 0);
        assertTrue("Different objects with same rank should be equal.",
                comparator.compare(rank1_1, rank1_2) == 0);

        // Testing different ranks and with different order of the parameters
        assertTrue(comparator.compare(rank1_1, rank2) < 0);
        assertTrue(comparator.compare(rank2, rank1_1) > 0);
    }

    public void testUnknownRank() {
        DummyObject knownRank = new DummyObject(RANK1);
        DummyObject unknownRank1 = new DummyObject(UNKNOWN_RANK1);
        DummyObject unknownRank2 = new DummyObject(UNKNOWN_RANK2);

        assertTrue("Known rank should be smaller than unknown rank.",
                comparator.compare(knownRank, unknownRank1) < 0);
        assertTrue("Unknown rank should be larger than known rank.",
                comparator.compare(unknownRank1, knownRank) > 0);
        assertTrue("Two different unknown ranks should be equal.",
                comparator.compare(unknownRank1, unknownRank2) == 0);
    }

    public void testNullRank() {
        DummyObject knownRank = new DummyObject(RANK1);
        DummyObject unknownRank = new DummyObject(UNKNOWN_RANK1);
        DummyObject nullRank = new DummyObject(NULL_RANK);

        assertTrue("Known rank should be smaller than null rank.",
                comparator.compare(knownRank, nullRank) < 0);
        assertTrue("null rank should be larger than known rank.",
                comparator.compare(nullRank, knownRank) > 0);
        assertTrue("Unknown and null rank should be equal.",
                comparator.compare(unknownRank, nullRank) == 0);
    }

    private static final Function<DummyObject, String> DUMMY_OBJECT_TO_RANK_FUNCTION =
            new Function<DummyObject, String>() {
                @Override
                public String apply(DummyObject dummyObject) {
                    return dummyObject.rank;
                }
            };

    private class DummyObject {
        private final String rank;

        private DummyObject(String rank) {
            this.rank = rank;
        }
    }
}
