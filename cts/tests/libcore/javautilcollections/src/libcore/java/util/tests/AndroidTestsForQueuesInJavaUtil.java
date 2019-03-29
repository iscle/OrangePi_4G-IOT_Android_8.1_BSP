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

import com.google.common.collect.testing.MinimalCollection;
import com.google.common.collect.testing.QueueTestSuiteBuilder;
import com.google.common.collect.testing.TestStringQueueGenerator;
import com.google.common.collect.testing.TestsForListsInJavaUtil;
import com.google.common.collect.testing.TestsForQueuesInJavaUtil;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;

import java.util.LinkedList;
import java.util.Queue;
import junit.framework.Test;

/**
 * Guava-testlib tests for {@code Queue} implementations from {@code java.util}.
 */
public class AndroidTestsForQueuesInJavaUtil extends TestsForQueuesInJavaUtil {

    /**
     * Override and copy the super class's implementation in order to change the name to ensure
     * that created tests are unique and do not clash with those created by
     * {@link TestsForListsInJavaUtil#testsForLinkedList()}, see bug 62438629.
     */
    @Override
    public Test testsForLinkedList() {
        return QueueTestSuiteBuilder.using(
                new TestStringQueueGenerator() {
                    @Override
                    public Queue<String> create(String[] elements) {
                        return new LinkedList<String>(MinimalCollection.of(elements));
                    }
                })
                .named("LinkedList as Queue")
                .withFeatures(
                        CollectionFeature.GENERAL_PURPOSE,
                        CollectionFeature.ALLOWS_NULL_VALUES,
                        CollectionFeature.KNOWN_ORDER,
                        CollectionSize.ANY)
                .skipCollectionTests() // already covered in TestsForListsInJavaUtil
                .suppressing(suppressForLinkedList())
                .createTestSuite();
    }
}
