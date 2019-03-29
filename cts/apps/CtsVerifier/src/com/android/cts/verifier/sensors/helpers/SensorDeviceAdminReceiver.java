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

package com.android.cts.verifier.sensors.helpers;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;

/**
 * Receiver to be used with {@link SensorTestScreenManipulator}.
 */
public class SensorDeviceAdminReceiver extends DeviceAdminReceiver {

    /**
     * Gets the associated {@link ComponentName} of the current receiver.
     */
    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, SensorDeviceAdminReceiver.class);
    }
}
