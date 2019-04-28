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
 * limitations under the License.
 */
package android.platform.longevity;

import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;

import org.junit.Test;
import org.junit.internal.builders.AllDefaultPossibilitiesBuilder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;
import org.junit.runners.Suite.SuiteClasses;

/**
 * Unit tests for the {@link LongevitySuite} runner.
 */
@RunWith(JUnit4.class)
public class LongevitySuiteTest {
    /**
     * Unit test that the {@link SuiteClasses} annotation is required.
     */
    @Test
    @SmallTest
    public void testAnnotationRequired() {
        try {
            new LongevitySuite(NoSuiteClassesSuite.class, new AllDefaultPossibilitiesBuilder(true));
            fail("This suite should not be possible to construct.");
        } catch (InitializationError e) {
            // ignore and pass.
        }
    }

    @RunWith(LongevitySuite.class)
    private static class NoSuiteClassesSuite { }
}
