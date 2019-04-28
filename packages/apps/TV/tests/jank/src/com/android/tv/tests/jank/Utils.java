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
package com.android.tv.tests.jank;

import com.android.tv.testing.uihelper.UiDeviceUtils;

import android.support.test.uiautomator.UiDevice;

public final class Utils {
    /** Live TV process name */
    public static final String LIVE_CHANNELS_PROCESS_NAME = "com.android.tv";

    private Utils() { }

    /**
     * Presses channel number to tune to {@code channel}.
     */
    public static void pressKeysForChannelNumber(String channel, UiDevice uiDevice) {
        UiDeviceUtils.pressKeys(uiDevice, channel);
        uiDevice.pressDPadCenter();
    }
}
