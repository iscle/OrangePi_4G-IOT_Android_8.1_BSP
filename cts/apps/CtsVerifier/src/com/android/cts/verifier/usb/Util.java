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
 */

package com.android.cts.verifier.usb;

import android.support.annotation.NonNull;
import android.util.Log;

/**
 * Utilities for the USB CTS verifier tests.
 */
public class Util {
    private static final String LOG_TAG = Util.class.getSimpleName();

    /**
     * Run a {@link Invokable} and expect a {@link Throwable}.
     *
     * @param r             The {@link Invokable} to run
     * @param expectedClass The expected {@link Throwable} type
     */
    public static void runAndAssertException(@NonNull Invokable r,
            @NonNull Class<? extends Throwable> expectedClass) throws Throwable {
        try {
            r.run();
        } catch (Throwable e) {
            if (e.getClass().isAssignableFrom(expectedClass)) {
                return;
            } else {
                Log.e(LOG_TAG, "Expected: " + expectedClass.getName() + ", got: "
                        + e.getClass().getName());
                throw e;
            }
        }

        throw new AssertionError("No throwable thrown");
    }


    /**
     * A {@link Runnable} that can throw an {@link Throwable}.
     */
    public interface Invokable {
        /**
         * Run the code that might cause an exception.
         */
        void run() throws Throwable;
    }
}
