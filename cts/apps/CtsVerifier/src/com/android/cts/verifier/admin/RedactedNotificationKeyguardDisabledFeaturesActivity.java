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
package com.android.cts.verifier.admin;

import android.app.admin.DevicePolicyManager;

import android.content.Intent;


import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.R;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.managedprovisioning.ByodHelperActivity;

/**
 * Tests for Device Admin keyguard redacted notification feature. This test is taken out from
 * DeviceAdminKeyguardDisabledFeaturesActivity class, because KEYGUARD_DISABLE_SECURE_NOTIFICATIONS
 * would mask KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS.
 *  */

public class RedactedNotificationKeyguardDisabledFeaturesActivity
    extends DeviceAdminKeyguardDisabledFeaturesActivity {
  @Override
  protected int getKeyguardDisabledFeatures() {
    return DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;
  }

  @Override
  protected void setupTests(ArrayTestListAdapter adapter) {
    adapter.add(new DialogTestListItem(this, R.string.device_admin_disable_unredacted_notifications,
        "DeviceAdmin_DisableUnredactedNotifications",
        R.string.device_admin_disable_unredacted_notifications_instruction,
        new Intent(ByodHelperActivity.ACTION_NOTIFICATION_ON_LOCKSCREEN)));
  }
}
