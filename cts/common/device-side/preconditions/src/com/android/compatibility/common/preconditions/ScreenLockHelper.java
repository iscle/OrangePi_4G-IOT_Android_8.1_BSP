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

package com.android.compatibility.common.preconditions;

import android.app.KeyguardManager;
import android.content.Context;

/**
 * ScreenLockHelper is used to check whether the device is protected by a locked screen.
 */
public class ScreenLockHelper {

    /*
     * This helper returns false for the Screen Lock set to 'Swipe' or 'None', as it seems there
     * is no way to programmatically distinguish between the two.
     */
    public static boolean isDeviceSecure(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km.isDeviceSecure();
    }

}
