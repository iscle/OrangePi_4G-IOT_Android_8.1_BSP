/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListActivity;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.TestResult;
import com.android.cts.verifier.location.LocationListenerActivity;

/**
 * CTS verifier test for BYOD managed provisioning flow
 *
 * This activity is responsible for starting the managed provisioning flow and verify the outcome of
 * provisioning. It performs the following verifications:
 *   Full disk encryption is enabled.
 *   Profile owner is correctly installed.
 *   Profile owner shows up in the Settings app.
 *   Badged work apps show up in launcher.
 * The first two verifications are performed automatically, by interacting with profile owner using
 * cross-profile intents, while the last two are carried out manually by the user.
 */
public class ByodFlowTestActivity extends DialogTestListActivity {

    private static final String TAG = "ByodFlowTestActivity";
    private static ConnectivityManager mCm;
    private static final int REQUEST_MANAGED_PROVISIONING = 0;
    private static final int REQUEST_PROFILE_OWNER_STATUS = 1;
    private static final int REQUEST_INTENT_FILTERS_STATUS = 2;
    private static final int REQUEST_CHECK_DISK_ENCRYPTION = 3;
    private static final int REQUEST_SET_LOCK_FOR_ENCRYPTION = 4;

    private ComponentName mAdminReceiverComponent;
    private KeyguardManager mKeyguardManager;
    private ByodFlowTestHelper mByodFlowTestHelper;

    private DialogTestListItem mProfileOwnerInstalled;
    private DialogTestListItem mDiskEncryptionTest;
    private DialogTestListItem mProfileAccountVisibleTest;
    private DialogTestListItem mDeviceAdminVisibleTest;
    private DialogTestListItem mWorkAppVisibleTest;
    private DialogTestListItem mCrossProfileIntentFiltersTestFromPersonal;
    private DialogTestListItem mCrossProfileIntentFiltersTestFromWork;
    private DialogTestListItem mAppLinkingTest;
    private DialogTestListItem mDisableNonMarketTest;
    private DialogTestListItem mEnableNonMarketTest;
    private DialogTestListItem mWorkNotificationBadgedTest;
    private DialogTestListItem mWorkStatusBarIconTest;
    private DialogTestListItem mWorkStatusBarToastTest;
    private DialogTestListItem mAppSettingsVisibleTest;
    private DialogTestListItem mLocationSettingsVisibleTest;
    private DialogTestListItem mWiFiDataUsageSettingsVisibleTest;
    private DialogTestListItem mCellularDataUsageSettingsVisibleTest;
    private DialogTestListItem mCredSettingsVisibleTest;
    private DialogTestListItem mPrintSettingsVisibleTest;
    private DialogTestListItem mIntentFiltersTest;
    private DialogTestListItem mPermissionLockdownTest;
    private DialogTestListItem mCrossProfileImageCaptureSupportTest;
    private DialogTestListItem mCrossProfileVideoCaptureWithExtraOutputSupportTest;
    private DialogTestListItem mCrossProfileVideoCaptureWithoutExtraOutputSupportTest;
    private DialogTestListItem mCrossProfileAudioCaptureSupportTest;
    private TestListItem mKeyguardDisabledFeaturesTest;
    private DialogTestListItem mDisableNfcBeamTest;
    private TestListItem mAuthenticationBoundKeyTest;
    private DialogTestListItem mEnableLocationModeTest;
    private DialogTestListItem mDisableLocationModeThroughMainSwitchTest;
    private DialogTestListItem mDisableLocationModeThroughWorkSwitchTest;
    private DialogTestListItem mPrimaryLocationWhenWorkDisabledTest;
    private DialogTestListItem mSelectWorkChallenge;
    private DialogTestListItem mConfirmWorkCredentials;
    private DialogTestListItem mParentProfilePassword;
    private TestListItem mVpnTest;
    private TestListItem mAlwaysOnVpnSettingsTest;
    private TestListItem mRecentsTest;
    private TestListItem mDisallowAppsControlTest;
    private TestListItem mOrganizationInfoTest;
    private TestListItem mPolicyTransparencyTest;
    private TestListItem mTurnOffWorkFeaturesTest;

    public ByodFlowTestActivity() {
        super(R.layout.provisioning_byod,
                R.string.provisioning_byod, R.string.provisioning_byod_info,
                R.string.provisioning_byod_instructions);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mByodFlowTestHelper = new ByodFlowTestHelper(this);
        mAdminReceiverComponent = new ComponentName(this, DeviceAdminTestReceiver.class.getName());
        mKeyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

        mByodFlowTestHelper.setup();

        mPrepareTestButton.setText(R.string.provisioning_byod_start);
        mPrepareTestButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Utils.provisionManagedProfile(ByodFlowTestActivity.this, mAdminReceiverComponent,
                        REQUEST_MANAGED_PROVISIONING);
            }
        });

        // If we are started by managed provisioning (fresh managed provisioning after encryption
        // reboot), redirect the user back to the main test list. This is because the test result
        // is only saved by the parent TestListActivity, and if we did allow the user to proceed
        // here, the test result would be lost when this activity finishes.
        if (ByodHelperActivity.ACTION_PROFILE_OWNER_STATUS.equals(getIntent().getAction())) {
            startActivity(new Intent(this, TestListActivity.class));
            // Calling super.finish() because we delete managed profile in our overridden of finish(),
            // which is not what we want to do here.
            super.finish();
        } else {
            queryProfileOwner(false);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // This is called when managed provisioning completes successfully without reboot.
        super.onNewIntent(intent);
        if (ByodHelperActivity.ACTION_PROFILE_OWNER_STATUS.equals(intent.getAction())) {
            handleStatusUpdate(RESULT_OK, intent);
        }
    }

    @Override
    protected void handleActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_MANAGED_PROVISIONING:
                return;
            case REQUEST_PROFILE_OWNER_STATUS:
                // Called after queryProfileOwner()
                handleStatusUpdate(resultCode, data);
                break;
            case REQUEST_CHECK_DISK_ENCRYPTION:
                // Called after checkDiskEncryption()
                handleDiskEncryptionStatus(resultCode, data);
                break;
            case REQUEST_SET_LOCK_FOR_ENCRYPTION:
                // Called after handleDiskEncryptionStatus() to set screen lock if necessary
                handleSetLockForEncryption();
                break;
            case REQUEST_INTENT_FILTERS_STATUS:
                // Called after checkIntentFilters()
                handleIntentFiltersStatus(resultCode);
                break;
            default:
                super.handleActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleStatusUpdate(int resultCode, Intent data) {
        boolean provisioned = data != null &&
                data.getBooleanExtra(ByodHelperActivity.EXTRA_PROVISIONED, false);
        setTestResult(mProfileOwnerInstalled, (provisioned && resultCode == RESULT_OK) ?
                TestResult.TEST_RESULT_PASSED : TestResult.TEST_RESULT_FAILED);
    }

    @Override
    public void finish() {
        // Pass and fail buttons are known to call finish() when clicked, and this is when we want to
        // clean up the provisioned profile.
        mByodFlowTestHelper.tearDown();
        super.finish();
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        mProfileOwnerInstalled = new DialogTestListItem(this,
                R.string.provisioning_byod_profileowner,
                "BYOD_ProfileOwnerInstalled") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                queryProfileOwner(true);
            }
        };

        mDiskEncryptionTest = new DialogTestListItem(this,
                R.string.provisioning_byod_disk_encryption,
                "BYOD_DiskEncryptionTest") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                checkDiskEncryption();
            }
        };

        /*
         * To keep the image in this test up to date, use the instructions in
         * {@link ByodIconSamplerActivity}.
         */

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            mWorkAppVisibleTest = new DialogTestListItemWithIcon(this,
                    R.string.provisioning_byod_workapps_visible,
                    "BYOD_WorkAppVisibleTest",
                    R.string.provisioning_byod_workapps_visible_instruction,
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    R.drawable.badged_icon);

            mConfirmWorkCredentials = new DialogTestListItem(this,
                    R.string.provisioning_byod_confirm_work_credentials,
                    "BYOD_ConfirmWorkCredentials",
                    R.string.provisioning_byod_confirm_work_credentials_description,
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));

            mWiFiDataUsageSettingsVisibleTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_wifi_data_usage_settings,
                    "BYOD_WiFiDataUsageSettingsVisibleTest",
                    R.string.provisioning_byod_wifi_data_usage_settings_instruction,
                    new Intent(Settings.ACTION_SETTINGS));
        }

        mWorkNotificationBadgedTest = new DialogTestListItemWithIcon(this,
                R.string.provisioning_byod_work_notification,
                "BYOD_WorkNotificationBadgedTest",
                R.string.provisioning_byod_work_notification_instruction,
                new Intent(ByodHelperActivity.ACTION_NOTIFICATION),
                R.drawable.ic_corp_icon);

        Intent workStatusIcon = new Intent(WorkStatusTestActivity.ACTION_WORK_STATUS_ICON);
        workStatusIcon.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mWorkStatusBarIconTest = new DialogTestListItemWithIcon(this,
                R.string.provisioning_byod_work_status_icon,
                "BYOD_WorkStatusBarIconTest",
                R.string.provisioning_byod_work_status_icon_instruction,
                workStatusIcon,
                R.drawable.stat_sys_managed_profile_status);

        Intent workStatusToast = new Intent(WorkStatusTestActivity.ACTION_WORK_STATUS_TOAST);
        workStatusToast.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mWorkStatusBarToastTest = new DialogTestListItem(this,
                R.string.provisioning_byod_work_status_toast,
                "BYOD_WorkStatusBarToastTest",
                R.string.provisioning_byod_work_status_toast_instruction,
                workStatusToast);

        mDisableNonMarketTest = new DialogTestListItem(this,
                R.string.provisioning_byod_nonmarket_deny,
                "BYOD_DisableNonMarketTest",
                R.string.provisioning_byod_nonmarket_deny_info,
                new Intent(ByodHelperActivity.ACTION_INSTALL_APK)
                        .putExtra(ByodHelperActivity.EXTRA_ALLOW_NON_MARKET_APPS, false));

        mEnableNonMarketTest = new DialogTestListItem(this,
                R.string.provisioning_byod_nonmarket_allow,
                "BYOD_EnableNonMarketTest",
                R.string.provisioning_byod_nonmarket_allow_info,
                new Intent(ByodHelperActivity.ACTION_INSTALL_APK)
                        .putExtra(ByodHelperActivity.EXTRA_ALLOW_NON_MARKET_APPS, true));

        mProfileAccountVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_profile_visible,
                "BYOD_ProfileAccountVisibleTest",
                R.string.provisioning_byod_profile_visible_instruction,
                new Intent(Settings.ACTION_SETTINGS));

        mAppSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_app_settings,
                "BYOD_AppSettingsVisibleTest",
                R.string.provisioning_byod_app_settings_instruction,
                new Intent(Settings.ACTION_APPLICATION_SETTINGS));

        mDeviceAdminVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_admin_visible,
                "BYOD_DeviceAdminVisibleTest",
                R.string.provisioning_byod_admin_visible_instruction,
                new Intent(Settings.ACTION_SECURITY_SETTINGS));

        mCredSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_cred_settings,
                "BYOD_CredSettingsVisibleTest",
                R.string.provisioning_byod_cred_settings_instruction,
                new Intent(Settings.ACTION_SECURITY_SETTINGS));

        mLocationSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_location_settings,
                "BYOD_LocationSettingsVisibleTest",
                R.string.provisioning_byod_location_settings_instruction,
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));

        mCellularDataUsageSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_cellular_data_usage_settings,
                "BYOD_CellularDataUsageSettingsVisibleTest",
                R.string.provisioning_byod_cellular_data_usage_settings_instruction,
                new Intent(Settings.ACTION_SETTINGS));

        mPrintSettingsVisibleTest = new DialogTestListItem(this,
                R.string.provisioning_byod_print_settings,
                "BYOD_PrintSettingsVisibleTest",
                R.string.provisioning_byod_print_settings_instruction,
                new Intent(Settings.ACTION_PRINT_SETTINGS));

        Intent intent = new Intent(CrossProfileTestActivity.ACTION_CROSS_PROFILE_TO_WORK);
        intent.putExtra(CrossProfileTestActivity.EXTRA_STARTED_FROM_WORK, false);
        Intent chooser = Intent.createChooser(intent,
                getResources().getString(R.string.provisioning_cross_profile_chooser));
        mCrossProfileIntentFiltersTestFromPersonal = new DialogTestListItem(this,
                R.string.provisioning_byod_cross_profile_from_personal,
                "BYOD_CrossProfileIntentFiltersTestFromPersonal",
                R.string.provisioning_byod_cross_profile_from_personal_instruction,
                chooser);

        mCrossProfileIntentFiltersTestFromWork = new DialogTestListItem(this,
                R.string.provisioning_byod_cross_profile_from_work,
                "BYOD_CrossProfileIntentFiltersTestFromWork",
                R.string.provisioning_byod_cross_profile_from_work_instruction,
                new Intent(ByodHelperActivity.ACTION_TEST_CROSS_PROFILE_INTENTS_DIALOG));

        /* Disable due to b/33571176
        mAppLinkingTest = new DialogTestListItem(this,
                R.string.provisioning_app_linking,
                "BYOD_AppLinking",
                R.string.provisioning_byod_app_linking_instruction,
                new Intent(ByodHelperActivity.ACTION_TEST_APP_LINKING_DIALOG));
        */

        mKeyguardDisabledFeaturesTest = TestListItem.newTest(this,
                R.string.provisioning_byod_keyguard_disabled_features,
                KeyguardDisabledFeaturesActivity.class.getName(),
                new Intent(this, KeyguardDisabledFeaturesActivity.class), null);

        mAuthenticationBoundKeyTest = TestListItem.newTest(this,
                R.string.provisioning_byod_auth_bound_key,
                AuthenticationBoundKeyTestActivity.class.getName(),
                new Intent(AuthenticationBoundKeyTestActivity.ACTION_AUTH_BOUND_KEY_TEST),
                null);

        mVpnTest = TestListItem.newTest(this,
                R.string.provisioning_byod_vpn,
                VpnTestActivity.class.getName(),
                new Intent(VpnTestActivity.ACTION_VPN),
                null);

        mAlwaysOnVpnSettingsTest = TestListItem.newTest(this,
                R.string.provisioning_byod_always_on_vpn,
                AlwaysOnVpnSettingsTestActivity.class.getName(),
                new Intent(AlwaysOnVpnSettingsTestActivity.ACTION_ALWAYS_ON_VPN_SETTINGS_TEST),
                null);

        mDisallowAppsControlTest = TestListItem.newTest(this,
                R.string.provisioning_byod_disallow_apps_control,
                DisallowAppsControlActivity.class.getName(),
                new Intent(this, DisallowAppsControlActivity.class), null);

        // Test for checking if the required intent filters are set during managed provisioning.
        mIntentFiltersTest = new DialogTestListItem(this,
                R.string.provisioning_byod_cross_profile_intent_filters,
                "BYOD_IntentFiltersTest") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                checkIntentFilters();
            }
        };

        mTurnOffWorkFeaturesTest = TestListItem.newTest(this,
                R.string.provisioning_byod_turn_off_work,
                TurnOffWorkActivity.class.getName(),
                new Intent(this, TurnOffWorkActivity.class), null);

        Intent permissionCheckIntent = new Intent(
                PermissionLockdownTestActivity.ACTION_MANAGED_PROFILE_CHECK_PERMISSION_LOCKDOWN);
        mPermissionLockdownTest = new DialogTestListItem(this,
                R.string.device_profile_owner_permission_lockdown_test,
                "BYOD_PermissionLockdownTest",
                R.string.profile_owner_permission_lockdown_test_info,
                permissionCheckIntent);

        mSelectWorkChallenge = new DialogTestListItem(this,
                R.string.provisioning_byod_select_work_challenge,
                "BYOD_SelectWorkChallenge",
                R.string.provisioning_byod_select_work_challenge_description,
                new Intent(ByodHelperActivity.ACTION_TEST_SELECT_WORK_CHALLENGE));

        mRecentsTest = TestListItem.newTest(this,
                R.string.provisioning_byod_recents,
                RecentsRedactionActivity.class.getName(),
                new Intent(RecentsRedactionActivity.ACTION_RECENTS),
                null);

        mOrganizationInfoTest = TestListItem.newTest(this,
                R.string.provisioning_byod_organization_info,
                OrganizationInfoTestActivity.class.getName(),
                new Intent(this, OrganizationInfoTestActivity.class),
                null);

        mParentProfilePassword = new DialogTestListItem(this,
                R.string.provisioning_byod_parent_profile_password,
                "BYOD_ParentProfilePasswordTest",
                R.string.provisioning_byod_parent_profile_password_description,
                new Intent(ByodHelperActivity.ACTION_TEST_PARENT_PROFILE_PASSWORD));

        final Intent policyTransparencyTestIntent = new Intent(this,
                PolicyTransparencyTestListActivity.class);
        policyTransparencyTestIntent.putExtra(
                PolicyTransparencyTestListActivity.EXTRA_MODE,
                PolicyTransparencyTestListActivity.MODE_PROFILE_OWNER);
        policyTransparencyTestIntent.putExtra(
                PolicyTransparencyTestActivity.EXTRA_TEST_ID, "BYOD_PolicyTransparency");
        mPolicyTransparencyTest = TestListItem.newTest(this,
                R.string.device_profile_owner_policy_transparency_test,
                "BYOD_PolicyTransparency",
                policyTransparencyTestIntent, null);

        adapter.add(mProfileOwnerInstalled);
        adapter.add(mDiskEncryptionTest);

        // Badge related tests
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            adapter.add(mWorkAppVisibleTest);
        }

        adapter.add(mWorkNotificationBadgedTest);
        adapter.add(mWorkStatusBarIconTest);
        adapter.add(mWorkStatusBarToastTest);

        // Settings related tests.
        adapter.add(mProfileAccountVisibleTest);
        adapter.add(mDeviceAdminVisibleTest);
        adapter.add(mCredSettingsVisibleTest);
        adapter.add(mAppSettingsVisibleTest);
        adapter.add(mLocationSettingsVisibleTest);
        adapter.add(mPrintSettingsVisibleTest);

        adapter.add(mCrossProfileIntentFiltersTestFromPersonal);
        adapter.add(mCrossProfileIntentFiltersTestFromWork);
        /* Disable due to b/33571176
        adapter.add(mAppLinkingTest);
        */
        adapter.add(mDisableNonMarketTest);
        adapter.add(mEnableNonMarketTest);
        adapter.add(mIntentFiltersTest);
        adapter.add(mPermissionLockdownTest);
        adapter.add(mKeyguardDisabledFeaturesTest);
        adapter.add(mAuthenticationBoundKeyTest);
        adapter.add(mVpnTest);
        adapter.add(mAlwaysOnVpnSettingsTest);
        adapter.add(mTurnOffWorkFeaturesTest);
        adapter.add(mSelectWorkChallenge);
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            adapter.add(mConfirmWorkCredentials);
        }
        adapter.add(mRecentsTest);
        adapter.add(mOrganizationInfoTest);
        adapter.add(mParentProfilePassword);
        adapter.add(mPolicyTransparencyTest);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
                adapter.add(mWiFiDataUsageSettingsVisibleTest);
            }
        }

        mCm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if(mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) != null) {
            adapter.add(mCellularDataUsageSettingsVisibleTest);
        }

        if (canResolveIntent(new Intent(Settings.ACTION_APPLICATION_SETTINGS))) {
            adapter.add(mDisallowAppsControlTest);
        }

        /* If there is an application that handles ACTION_IMAGE_CAPTURE, test that it handles it
         * well.
         */
        if (canResolveIntent(ByodHelperActivity.getCaptureImageIntent())) {
            // Capture image intent can be resolved in primary profile, so test.
            mCrossProfileImageCaptureSupportTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_capture_image_support,
                    "BYOD_CrossProfileImageCaptureSupportTest",
                    R.string.provisioning_byod_capture_image_support_info,
                    new Intent(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_IMAGE));
            adapter.add(mCrossProfileImageCaptureSupportTest);
        } else {
            // Capture image intent cannot be resolved in primary profile, so skip test.
            Toast.makeText(ByodFlowTestActivity.this,
                    R.string.provisioning_byod_no_image_capture_resolver, Toast.LENGTH_SHORT)
                    .show();
        }

        /* If there is an application that handles ACTION_VIDEO_CAPTURE, test that it handles it
         * well.
         */
        if (canResolveIntent(ByodHelperActivity.getCaptureVideoIntent())) {
            // Capture video intent can be resolved in primary profile, so test.
            mCrossProfileVideoCaptureWithExtraOutputSupportTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_capture_video_support_with_extra_output,
                    "BYOD_CrossProfileVideoCaptureWithExtraOutputSupportTest",
                    R.string.provisioning_byod_capture_video_support_info,
                    new Intent(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_VIDEO_WITH_EXTRA_OUTPUT));
            adapter.add(mCrossProfileVideoCaptureWithExtraOutputSupportTest);
            mCrossProfileVideoCaptureWithoutExtraOutputSupportTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_capture_video_support_without_extra_output,
                    "BYOD_CrossProfileVideoCaptureWithoutExtraOutputSupportTest",
                    R.string.provisioning_byod_capture_video_support_info,
                    new Intent(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_VIDEO_WITHOUT_EXTRA_OUTPUT));
            adapter.add(mCrossProfileVideoCaptureWithoutExtraOutputSupportTest);
        } else {
            // Capture video intent cannot be resolved in primary profile, so skip test.
            Toast.makeText(ByodFlowTestActivity.this,
                    R.string.provisioning_byod_no_video_capture_resolver, Toast.LENGTH_SHORT)
                    .show();
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
            mDisableNfcBeamTest = new DialogTestListItem(this, R.string.provisioning_byod_nfc_beam,
                    "BYOD_DisableNfcBeamTest",
                    R.string.provisioning_byod_nfc_beam_allowed_instruction,
                    new Intent(ByodHelperActivity.ACTION_TEST_NFC_BEAM)) {
                @Override
                public void performTest(final DialogTestListActivity activity) {
                    activity.showManualTestDialog(mDisableNfcBeamTest,
                            new DefaultTestCallback(mDisableNfcBeamTest) {
                        @Override
                        public void onPass() {
                            // Start a second test with beam disallowed by policy.
                            Intent testNfcBeamIntent = new Intent(
                                    ByodHelperActivity.ACTION_TEST_NFC_BEAM);
                            testNfcBeamIntent.putExtra(NfcTestActivity.EXTRA_DISALLOW_BY_POLICY,
                                    true);
                            DialogTestListItem disableNfcBeamTest2 =
                                    new DialogTestListItem(activity,
                                    R.string.provisioning_byod_nfc_beam,
                                    "BYOD_DisableNfcBeamTest",
                                    R.string.provisioning_byod_nfc_beam_disallowed_instruction,
                                    testNfcBeamIntent);
                            // The result should be reflected on the original test.
                            activity.showManualTestDialog(disableNfcBeamTest2,
                                    new DefaultTestCallback(mDisableNfcBeamTest));
                        }
                    });
                }
            };
            adapter.add(mDisableNfcBeamTest);
        }

        /* If there is an application that handles RECORD_SOUND_ACTION, test that it handles it
         * well.
         */
        if (canResolveIntent(ByodHelperActivity.getCaptureAudioIntent())) {
            // Capture audio intent can be resolved in primary profile, so test.
            mCrossProfileAudioCaptureSupportTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_capture_audio_support,
                    "BYOD_CrossProfileAudioCaptureSupportTest",
                    R.string.provisioning_byod_capture_audio_support_info,
                    new Intent(ByodHelperActivity.ACTION_CAPTURE_AND_CHECK_AUDIO));
            adapter.add(mCrossProfileAudioCaptureSupportTest);
        } else {
            // Capture audio intent cannot be resolved in primary profile, so skip test.
            Toast.makeText(ByodFlowTestActivity.this,
                    R.string.provisioning_byod_no_audio_capture_resolver, Toast.LENGTH_SHORT)
                    .show();
        }

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
            mEnableLocationModeTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_location_mode_enable,
                    "BYOD_LocationModeEnableTest",
                    R.string.provisioning_byod_location_mode_enable_instruction,
                    new Intent(ByodHelperActivity.ACTION_BYOD_SET_LOCATION_AND_CHECK_UPDATES));
            mDisableLocationModeThroughMainSwitchTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_location_mode_disable,
                    "BYOD_LocationModeDisableMainTest",
                    R.string.provisioning_byod_location_mode_disable_instruction,
                    new Intent(ByodHelperActivity.ACTION_BYOD_SET_LOCATION_AND_CHECK_UPDATES));
            mDisableLocationModeThroughWorkSwitchTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_work_location_mode_disable,
                    "BYOD_LocationModeDisableWorkTest",
                    R.string.provisioning_byod_work_location_mode_disable_instruction,
                    new Intent(ByodHelperActivity.ACTION_BYOD_SET_LOCATION_AND_CHECK_UPDATES));
            mPrimaryLocationWhenWorkDisabledTest = new DialogTestListItem(this,
                    R.string.provisioning_byod_primary_location_when_work_disabled,
                    "BYOD_PrimaryLocationWhenWorkDisabled",
                    R.string.provisioning_byod_primary_location_when_work_disabled_instruction,
                    new Intent(LocationListenerActivity.ACTION_SET_LOCATION_AND_CHECK_UPDATES));
            adapter.add(mEnableLocationModeTest);
            adapter.add(mDisableLocationModeThroughMainSwitchTest);
            adapter.add(mDisableLocationModeThroughWorkSwitchTest);
            adapter.add(mPrimaryLocationWhenWorkDisabledTest);
        } else {
            // The system does not support GPS feature, so skip test.
            Toast.makeText(ByodFlowTestActivity.this,
                    R.string.provisioning_byod_no_gps_location_feature, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    // Return whether the intent can be resolved in the current profile
    private boolean canResolveIntent(Intent intent) {
        return intent.resolveActivity(getPackageManager()) != null;
    }

    @Override
    protected void clearRemainingState(final DialogTestListItem test) {
        super.clearRemainingState(test);
        if (ByodHelperActivity.ACTION_NOTIFICATION.equals(
                test.getManualTestIntent().getAction())) {
            try {
                startActivity(new Intent(
                        ByodHelperActivity.ACTION_CLEAR_NOTIFICATION));
            } catch (ActivityNotFoundException e) {
                // User shouldn't run this test before work profile is set up.
            }
        }
    }

    private void queryProfileOwner(boolean showToast) {
        try {
            Intent intent = new Intent(ByodHelperActivity.ACTION_QUERY_PROFILE_OWNER);
            startActivityForResult(intent, REQUEST_PROFILE_OWNER_STATUS);
        }
        catch (ActivityNotFoundException e) {
            Log.d(TAG, "queryProfileOwner: ActivityNotFoundException", e);
            setTestResult(mProfileOwnerInstalled, TestResult.TEST_RESULT_FAILED);
            if (showToast) {
                Utils.showToast(this, R.string.provisioning_byod_no_activity);
            }
        }
    }

    private void checkDiskEncryption() {
        try {
            Intent intent = new Intent(ByodHelperActivity.ACTION_CHECK_DISK_ENCRYPTION);
            startActivityForResult(intent, REQUEST_CHECK_DISK_ENCRYPTION);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "checkDiskEncryption: ActivityNotFoundException", e);
            setTestResult(mDiskEncryptionTest, TestResult.TEST_RESULT_FAILED);
            Utils.showToast(this, R.string.provisioning_byod_no_activity);
        }
    }

    private void handleDiskEncryptionStatus(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null) {
            Log.e(TAG, "Failed to get result for disk encryption, result code: " + resultCode);
            setTestResult(mDiskEncryptionTest, TestResult.TEST_RESULT_FAILED);
            return;
        }

        final int status = data.getIntExtra(ByodHelperActivity.EXTRA_ENCRYPTION_STATUS,
                DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED);
        switch (status) {
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE:
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER:
                setTestResult(mDiskEncryptionTest, TestResult.TEST_RESULT_PASSED);
                break;
            case DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY:
                if (!mKeyguardManager.isDeviceSecure()) {
                    Utils.setScreenLock(this, REQUEST_SET_LOCK_FOR_ENCRYPTION);
                    return;
                }
                Log.e(TAG, "Disk encryption key is not entangled with lock screen credentials");
                Toast.makeText(this, R.string.provisioning_byod_disk_encryption_default_key_toast,
                        Toast.LENGTH_LONG).show();
                // fall through
            default:
                setTestResult(mDiskEncryptionTest, TestResult.TEST_RESULT_FAILED);
        }

        if (mKeyguardManager.isDeviceSecure()) {
            Utils.removeScreenLock(this);
        }
    }

    private void handleSetLockForEncryption() {
        if (mKeyguardManager.isDeviceSecure()) {
            checkDiskEncryption();
        } else {
            setTestResult(mDiskEncryptionTest, TestResult.TEST_RESULT_FAILED);
            Toast.makeText(this, R.string.provisioning_byod_disk_encryption_no_pin_toast,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void checkIntentFilters() {
        try {
            // Enable component HandleIntentActivity before intent filters are checked.
            setHandleIntentActivityEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            // We disable the ByodHelperActivity in the primary profile. So, this intent
            // will be handled by the ByodHelperActivity in the managed profile.
            Intent intent = new Intent(ByodHelperActivity.ACTION_CHECK_INTENT_FILTERS);
            startActivityForResult(intent, REQUEST_INTENT_FILTERS_STATUS);
        } catch (ActivityNotFoundException e) {
            // Disable component HandleIntentActivity if intent filters check fails.
            setHandleIntentActivityEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            Log.d(TAG, "checkIntentFilters: ActivityNotFoundException", e);
            setTestResult(mIntentFiltersTest, TestResult.TEST_RESULT_FAILED);
            Utils.showToast(this, R.string.provisioning_byod_no_activity);
        }
    }

    private void handleIntentFiltersStatus(int resultCode) {
        // Disable component HandleIntentActivity after intent filters are checked.
        setHandleIntentActivityEnabledSetting(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        // we use the resultCode from ByodHelperActivity in the managed profile to know if certain
        // intents fired from the managed profile are forwarded.
        final boolean intentFiltersSetForManagedIntents = (resultCode == RESULT_OK);
        // Since the ByodFlowTestActivity is running in the primary profile, we directly use
        // the IntentFiltersTestHelper to know if certain intents fired from the primary profile
        // are forwarded.
        final boolean intentFiltersSetForPrimaryIntents =
                new IntentFiltersTestHelper(this).checkCrossProfileIntentFilters(
                        IntentFiltersTestHelper.FLAG_INTENTS_FROM_PRIMARY);
        final boolean intentFiltersSet =
                intentFiltersSetForPrimaryIntents & intentFiltersSetForManagedIntents;
        setTestResult(mIntentFiltersTest,
                intentFiltersSet ? TestResult.TEST_RESULT_PASSED : TestResult.TEST_RESULT_FAILED);
    }

    private void setHandleIntentActivityEnabledSetting(final int enableState) {
        getPackageManager().setComponentEnabledSetting(
            new ComponentName(ByodFlowTestActivity.this, HandleIntentActivity.class.getName()),
            enableState, PackageManager.DONT_KILL_APP);
    }

}
