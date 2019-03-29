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

import com.google.common.collect.testing.TestsForListsInJavaUtil;
import com.google.common.collect.testing.testers.CollectionToArrayTester;

import junit.framework.Test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

/**
 * Guava-testlib tests for {@code List} implementations from {@code java.util}.
 */
public class AndroidTestsForListsInJavaUtil extends TestsForListsInJavaUtil {
    @Override
    protected Collection<Method> suppressForArraysAsList() {
        return Collections.singleton(
                // http://b/30829421
                CollectionToArrayTester.getToArrayIsPlainObjectArrayMethod());
    }
}
