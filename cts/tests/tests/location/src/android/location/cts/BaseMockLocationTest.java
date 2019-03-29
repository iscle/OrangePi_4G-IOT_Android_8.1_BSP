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
 *
 */

package android.location.cts;

import com.android.compatibility.common.util.LocationUtils;

import android.test.InstrumentationTestCase;

/**
 * Base class for instrumentations tests that use mock location.
 */
public abstract class BaseMockLocationTest extends InstrumentationTestCase {
    private static final String LOG_TAG = "BaseMockLocationTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        LocationUtils.registerMockLocationProvider(getInstrumentation(), true);
    }

    @Override
    protected void tearDown() throws Exception {
        LocationUtils.registerMockLocationProvider(getInstrumentation(), false);
        super.tearDown();
    }
}
