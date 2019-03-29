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
 *
 */

package libcore.java.util.tests;

import com.google.common.collect.testing.TestsForMapsInJavaUtil;
import com.google.common.collect.testing.testers.CollectionAddAllTester;
import com.google.common.collect.testing.testers.CollectionAddTester;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * Guava-testlib tests for {@link MapsToTest} that were specified as a
 * constructor argument.
 */
public class AndroidTestsForMapsInJavaUtil extends TestsForMapsInJavaUtil {
    public enum MapsToTest {
        /** All Maps other than those below. */
        OTHER,
        /** TreeMao with natural ordering. */
        TREE_MAP_NATURAL,
        /** TreeMap with a Comparator. */
        TREE_MAP_WITH_COMPARATOR,
        /** ConcurrentSKipListMap with natural ordering. */
        CONCURRENT_SKIP_LIST_MAP_NATURAL,
        /** ConcurrentSKipListMap with a Comparator. */
        CONCURRENT_SKIP_LIST_MAP_WITH_COMPARATOR
    }

    private final MapsToTest mapsToTest;

    public AndroidTestsForMapsInJavaUtil(MapsToTest mapsToTest) {
        this.mapsToTest = Objects.requireNonNull(mapsToTest);
    }

    /**
     * Returns the tests for the {@link MapsToTest} from {@code java.util}.
     */
    @Override
    public final Test allTests() {
        TestSuite suite = new TestSuite("java.util Maps: " + mapsToTest);
        switch (mapsToTest) {
            case OTHER:
                suite.addTest(testsForCheckedMap());
                suite.addTest(testsForCheckedSortedMap());
                suite.addTest(testsForEmptyMap());
                suite.addTest(testsForSingletonMap());
                suite.addTest(testsForHashMap());
                suite.addTest(testsForLinkedHashMap());
                suite.addTest(testsForEnumMap());
                suite.addTest(testsForConcurrentHashMap());
                break;
            case TREE_MAP_NATURAL:
                suite.addTest(testsForTreeMapNatural());
                break;
            case TREE_MAP_WITH_COMPARATOR:
                suite.addTest(testsForTreeMapWithComparator());
                break;
            case CONCURRENT_SKIP_LIST_MAP_NATURAL:
                suite.addTest(testsForConcurrentSkipListMapNatural());
                break;
            case CONCURRENT_SKIP_LIST_MAP_WITH_COMPARATOR:
                suite.addTest(testsForConcurrentSkipListMapWithComparator());
                break;
            default:
                throw new IllegalArgumentException("Unknown part: " + mapsToTest);
        }
        return suite;
    }

    @Override
    protected final Collection<Method> suppressForConcurrentHashMap() {
        // http://b/30853241
        return Arrays.asList(
                CollectionAddAllTester.getAddAllUnsupportedNonePresentMethod(),
                CollectionAddAllTester.getAddAllUnsupportedSomePresentMethod(),
                CollectionAddTester.getAddUnsupportedNotPresentMethod());
    }
}
