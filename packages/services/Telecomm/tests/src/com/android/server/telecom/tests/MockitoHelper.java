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

package com.android.server.telecom.tests;

import android.content.Context;
import android.telecom.Log;

/**
 * Helper for Mockito-based test cases.
 */
public final class MockitoHelper {
    private static final String DEXCACHE = "dexmaker.dexcache";

    /**
     * Creates a new helper, which in turn will set the context classloader so
     * it can load Mockito resources.
     *
     * @param packageClass test case class
     */
    public void setUp(Context context, Class<?> packageClass) throws Exception {
        String dexCache = context.getCacheDir().toString();
        Log.v(this, "Setting property %s to %s", DEXCACHE, dexCache);
        System.setProperty(DEXCACHE, dexCache);
    }

    /**
     * Restores the context classloader to the previous value.
     */
    public void tearDown() throws Exception {
        Log.v(this, "Restoring context classloaders");
        Log.v(this, "Clearing property %s", DEXCACHE);
        System.clearProperty(DEXCACHE);
    }
}