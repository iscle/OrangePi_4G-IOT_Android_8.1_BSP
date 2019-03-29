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

package com.android.cts.verifier.managedprovisioning;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;

import java.util.List;

public class KeyguardDisabledFeaturesActivity extends DialogTestListActivity {

    protected DevicePolicyManager mDpm;

    public KeyguardDisabledFeaturesActivity() {
        super(R.layout.provisioning_byod,
                R.string.provisioning_byod_keyguard_disabled_features,
                R.string.provisioning_byod_keyguard_disabled_features_info,
                R.string.provisioning_byod_keyguard_disabled_features_instruction);
    }

    protected int getKeyguardDisabledFeatures() {
        return DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS
                | DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT
                | DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        mPrepareTestButton.setText(
                R.string.provisioning_byod_keyguard_disabled_features_prepare_button);
        mPrepareTestButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mDpm.isAdminActive(getAdminComponent())) {
                        Toast.makeText(KeyguardDisabledFeaturesActivity.this,
                                R.string.provisioning_byod_keyguard_disabled_features_not_admin,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setKeyguardDisabledFeatures();
                }
            });
    }

    protected ComponentName getAdminComponent() {
        return DeviceAdminTestReceiver.getReceiverComponentName();
    }

    protected String getTestIdPrefix() {
        return "BYOD_";
    }

    @Override
    public void finish() {
        // Pass and fail buttons are known to call finish() when clicked, and this is when we want to
        // clear the password.
        final ComponentName adminComponent = getAdminComponent();
        if (mDpm.isAdminActive(adminComponent)) {
            mDpm.removeActiveAdmin(adminComponent);
        }
        super.finish();
    }

    protected void setKeyguardDisabledFeatures() {
        int flags = getKeyguardDisabledFeatures();
        Intent setKeyguardDisabledFeaturesIntent = new Intent(
                ByodHelperActivity.ACTION_KEYGUARD_DISABLED_FEATURES)
                        .putExtra(ByodHelperActivity.EXTRA_PARAMETER_1, flags);
        startActivity(setKeyguardDisabledFeaturesIntent);
    }

    protected void setupDisableTrustAgentsTest(ArrayTestListAdapter adapter) {
        adapter.add(new DialogTestListItem(this, R.string.provisioning_byod_disable_trust_agents,
                getTestIdPrefix() + "DisableTrustAgentsTest",
                R.string.provisioning_byod_disable_trust_agents_instruction,
                new Intent(Settings.ACTION_SECURITY_SETTINGS)));
    }

    protected void setupDisableUnredactedWorkNotification(ArrayTestListAdapter adapter) {
        adapter.add(new DialogTestListItemWithIcon(this,
                R.string.provisioning_byod_disable_unredacted_notifications,
                getTestIdPrefix() + "DisableUnredactedNotifications",
                R.string.provisioning_byod_disable_unredacted_notifications_instruction,
                new Intent(ByodHelperActivity.ACTION_NOTIFICATION_ON_LOCKSCREEN),
                R.drawable.ic_corp_icon));
    }

    protected void setupFingerprintTests(ArrayTestListAdapter adapter) {
        FingerprintManager fpm = (FingerprintManager) getSystemService(Context.FINGERPRINT_SERVICE);
        if (fpm != null && fpm.isHardwareDetected()) {
            adapter.add(new DialogTestListItem(this,
                    R.string.provisioning_byod_fingerprint_disabled_in_settings,
                    getTestIdPrefix() + "FingerprintDisabledInSettings",
                    R.string.provisioning_byod_fingerprint_disabled_in_settings_instruction,
                    new Intent(Settings.ACTION_SECURITY_SETTINGS)));
            adapter.add(new DialogTestListItem(this, R.string.provisioning_byod_disable_fingerprint,
                    getTestIdPrefix() + "DisableFingerprint",
                    R.string.provisioning_byod_disable_fingerprint_instruction,
                    ByodHelperActivity.createLockIntent()));
        }
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        if (hasTrustAgents()) {
            setupDisableTrustAgentsTest(adapter);
        }
        setupDisableUnredactedWorkNotification(adapter);
        setupFingerprintTests(adapter);
    }

    private boolean hasTrustAgents() {
        PackageManager packageManager = getPackageManager();
        Intent intent = new Intent("android.service.trust.TrustAgentService");
        List<ResolveInfo> resolveInfos = packageManager.queryIntentServices(intent, 0);
        return resolveInfos.size() > 0;
    }

    @Override
    protected void clearRemainingState(final DialogTestListItem test) {
        super.clearRemainingState(test);
        if (ByodHelperActivity.ACTION_NOTIFICATION_ON_LOCKSCREEN.equals(
                test.getManualTestIntent().getAction())) {
            try {
                startActivity(new Intent(
                        ByodHelperActivity.ACTION_CLEAR_NOTIFICATION));
            } catch (ActivityNotFoundException e) {
                // User shouldn't run this test before work profile is set up.
            }
        }
    }
}
