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
 * limitations under the License.
 */

package com.android.tv.testing;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Tester for {@link java.lang.Comparable}s.
 *
 * <p>
 * To use, create a new {@link ComparableTester} and add comparable groups
 * where each group contains objects that are
 * {@link java.util.Comparator#compare(Object, Object)} == 0 to each other.
 * Groups are added in order asserting that all earlier groups have compare < 0
 * for all later groups.
 *
 * <pre>{@code
 * new ComparableTester<String>()
 *     .addEquivalentGroup("Hello", "HELLO")
 *     .addEquivalentGroup("World", "wORLD")
 *     .addEquivalentGroup("ZEBRA")
 *     .test();
 * }
 * </pre>
 *
 * @param <T> the type of objects to compare.
 */
public class ComparableTester<T extends Comparable<T>> {

    private final List<List<T>> listOfGroups = new ArrayList<>();

    @SafeVarargs
    public final ComparableTester<T> addEquivalentGroup(T... items) {
        listOfGroups.add(Arrays.asList(items));
        return this;
    }

    public void test() {
        for (int i = 0; i < listOfGroups.size(); i++) {
            List<T> currentGroup = listOfGroups.get(i);
            for (int j = 0; j < i; j++) {
                List<T> lhs = listOfGroups.get(j);
                assertOrder(i, j, lhs, currentGroup);
            }
            assertZero(currentGroup);
            for (int j = i + 1; j < listOfGroups.size(); j++) {
                List<T> rhs = listOfGroups.get(j);
                assertOrder(i, j, currentGroup, rhs);
            }
        }
    }

    private void assertOrder(int less, int more, List<T> lessGroup, List<T> moreGroup) {
        assertLess(less, more, lessGroup, moreGroup);
        assertMore(more, less, moreGroup, lessGroup);
    }

    private void assertLess(int left, int right, Collection<T> leftGroup,
            Collection<T> rightGroup) {
        int leftSub = 0;
        for (T leftItem : leftGroup) {
            int rightSub = 0;
            String leftName = "Item[" + left + "," + (leftSub++) + "]";
            for (T rightItem : rightGroup) {
                String rightName = "Item[" + right + "," + (rightSub++) + "]";
                Assert.assertEquals(
                        leftName + " " + leftItem + " compareTo  " + rightName + " " + rightItem
                                + " is <0", true, leftItem.compareTo(rightItem) < 0);
            }
        }
    }

    private void assertMore(int left, int right, Collection<T> leftGroup,
            Collection<T> rightGroup) {
        int leftSub = 0;
        for (T leftItem : leftGroup) {
            int rightSub = 0;
            String leftName = "Item[" + left + "," + (leftSub++) + "]";
            for (T rightItem : rightGroup) {
                String rightName = "Item[" + right + "," + (rightSub++) + "]";
                Assert.assertEquals(
                        leftName + " " + leftItem + " compareTo  " + rightName + " " + rightItem
                                + " is >0", true, leftItem.compareTo(rightItem) > 0);
            }
        }
    }

    private void assertZero(Collection<T> group) {
        // Test everything against everything in both directions, including against itself.
        for (T lhs : group) {
            for (T rhs : group) {
                Assert.assertEquals(lhs + " compareTo " + rhs, 0, lhs.compareTo(rhs));
            }
        }
    }
}
