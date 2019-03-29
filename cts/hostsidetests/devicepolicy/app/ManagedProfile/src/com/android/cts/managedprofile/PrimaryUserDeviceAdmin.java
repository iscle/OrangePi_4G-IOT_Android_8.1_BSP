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
package com.android.cts.managedprofile;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;

/**
 * A device admin class running in the primary user. Currently used by delegated cert installer
 * test to set a lockscreen password which is prerequisite of installKeyPair().
 */
public class PrimaryUserDeviceAdmin extends DeviceAdminReceiver {
    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            PrimaryUserDeviceAdmin.class.getPackage().getName(),
            PrimaryUserDeviceAdmin.class.getName());
}