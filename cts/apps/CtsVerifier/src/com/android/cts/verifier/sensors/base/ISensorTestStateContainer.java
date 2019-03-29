/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.sensors.base;

import android.content.ContentResolver;
import android.content.Intent;

/**
 * An interface that defines a facade for {@link BaseSensorTestActivity}, so it can be consumed by
 * other CtsVerifier Sensor Test Framework helper components.
 */
public interface ISensorTestStateContainer {

    /**
     * @return The current logger.
     */
    BaseSensorTestActivity.SensorTestLogger getTestLogger();

    /**
     * Waits for the operator to acknowledge to continue execution.
     */
    void waitForUserToContinue() throws InterruptedException;

    /**
     * @param resId The resource Id to extract.
     * @return The extracted string.
     */
    String getString(int resId);

    /**
     * @param resId The resource Id to extract.
     * @param params The parameters to format the string represented by the resource contents.
     * @return The formatted extracted string.
     */
    String getString(int resId, Object ... params);

    /**
     * Starts an Activity and blocks until it completes, then it returns its result back to the
     * client.
     *
     * @param action The action to start the Activity.
     * @return The Activity's result code.
     */
    int executeActivity(String action) throws InterruptedException;

    /**
     * Starts an Activity and blocks until it completes, then it returns its result back to the
     * client.
     *
     * @param intent The intent to start the Activity.
     * @return The Activity's result code.
     */
    int executeActivity(Intent intent) throws InterruptedException;

    /**
     * @return The {@link ContentResolver} associated with the test.
     */
    ContentResolver getContentResolver();

    /**
     * @param feature the feature being tested
     * @return true if the specified feature is implemented; false otherwise.
     */
    boolean hasSystemFeature(String feature);

    /**
     * @param action setting in the form of action name
     * @return true if corresponding setting activity exists; false otherwise.
     */
    boolean hasActivity(String action);
}
