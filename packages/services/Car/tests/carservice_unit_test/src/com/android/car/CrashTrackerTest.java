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
package com.android.car;


import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.MutableBoolean;

import com.android.car.CarService.CrashTracker;

@SmallTest
public class CrashTrackerTest extends AndroidTestCase {

    public void testCrashingTooManyTimes() throws InterruptedException {
        final int SLIDING_WINDOW = 100;
        final MutableBoolean callbackTriggered = new MutableBoolean(false);

        CarService.CrashTracker crashTracker = new CrashTracker(3, SLIDING_WINDOW,
                () -> callbackTriggered.value = true);


        crashTracker.crashDetected();
        crashTracker.crashDetected();
        Thread.sleep(SLIDING_WINDOW + 1);
        crashTracker.crashDetected();

        assertFalse(callbackTriggered.value);

        crashTracker.crashDetected();
        assertFalse(callbackTriggered.value);
        crashTracker.crashDetected();
        assertTrue(callbackTriggered.value);  // Last 3 crashes should be within SLIDING_WINDOW
                                              // time frame.

    }
}
