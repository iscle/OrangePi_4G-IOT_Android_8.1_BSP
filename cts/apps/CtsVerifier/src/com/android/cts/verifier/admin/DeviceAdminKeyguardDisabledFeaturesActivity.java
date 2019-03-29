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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.fingerprint.FingerprintManager;
import android.provider.Settings;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.R;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.managedprovisioning.ByodHelperActivity;
import com.android.cts.verifier.managedprovisioning.DeviceAdminTestReceiver;
import com.android.cts.verifier.managedprovisioning.KeyguardDisabledFeaturesActivity;

import java.util.List;

/**
 * Tests for Device Admin keyguard disabled features.
 */
public class DeviceAdminKeyguardDisabledFeaturesActivity extends KeyguardDisabledFeaturesActivity {
    @Override
    protected int getKeyguardDisabledFeatures() {
        return DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL;
    }

    @Override
    protected void setKeyguardDisabledFeatures() {
        int flags = getKeyguardDisabledFeatures();
        mDpm.setKeyguardDisabledFeatures(getAdminComponent(), flags);
    }

    @Override
    protected String getTestIdPrefix() {
        return "DeviceAdmin_";
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        setupFingerprintTests(adapter);
        if (hasTrustAgents()) {
            setupDisableTrustAgentsTest(adapter);
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            adapter.add(new DialogTestListItem(this, R.string.device_admin_keyguard_disable_camera,
                    getTestIdPrefix()+"KeyguardDisableCamera",
                    R.string.device_admin_keyguard_disable_camera_instruction,
                    new Intent(ByodHelperActivity.ACTION_LOCKNOW)));
        }

        adapter.add(new DialogTestListItem(this, R.string.device_admin_disable_notifications,
                "DeviceAdmin_DisableNotifications",
                R.string.device_admin_disable_notifications_instruction,
                new Intent(ByodHelperActivity.ACTION_NOTIFICATION_ON_LOCKSCREEN)));
    }

    private boolean hasTrustAgents() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent("android.service.trust.TrustAgentService");
        List<ResolveInfo> resolveInfos = packageManager.queryIntentServices(intent, 0);
       return resolveInfos.size() > 0;
    }
}
