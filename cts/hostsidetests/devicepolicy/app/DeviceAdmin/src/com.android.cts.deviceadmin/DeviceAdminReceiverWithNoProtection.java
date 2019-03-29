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
package com.android.cts.deviceadmin;

import android.app.admin.DeviceAdminReceiver;

/**
 * Active admin with no android:permission="android.permission.BIND_DEVICE_ADMIN".  It can still
 * be activated if the target API level <= 23, but not >= 24.
 */
public class DeviceAdminReceiverWithNoProtection extends DeviceAdminReceiver {
}
