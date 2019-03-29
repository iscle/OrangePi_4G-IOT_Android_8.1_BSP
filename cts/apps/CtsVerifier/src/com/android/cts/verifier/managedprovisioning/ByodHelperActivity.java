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

import static android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserManager;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v4.util.Pair;
import android.util.Log;

import com.android.cts.verifier.R;
import com.android.cts.verifier.location.LocationListenerActivity;
import com.android.cts.verifier.managedprovisioning.ByodPresentMediaDialog.DialogCallback;

import java.io.File;
import java.util.ArrayList;

/**
 * A helper activity from the managed profile side that responds to requests from CTS verifier in
 * primary user. Profile owner APIs are accessible inside this activity (given this activity is
 * started within the work profile). Its current functionalities include making sure the profile
 * owner is setup correctly, removing the work profile upon request, and verifying the image and
 * video capture functionality.
 *
 * Note: We have to use a dummy activity because cross-profile intents only work for activities.
 */
public class ByodHelperActivity extends LocationListenerActivity
        implements DialogCallback {
    static final String TAG = "ByodHelperActivity";

    // Primary -> managed intent: query if the profile owner has been set up.
    public static final String ACTION_QUERY_PROFILE_OWNER = "com.android.cts.verifier.managedprovisioning.BYOD_QUERY";
    // Managed -> primary intent: update profile owner test status in primary's CtsVerifer
    public static final String ACTION_PROFILE_OWNER_STATUS = "com.android.cts.verifier.managedprovisioning.BYOD_STATUS";
    // Primary -> managed intent: request to delete the current profile
    public static final String ACTION_REMOVE_MANAGED_PROFILE = "com.android.cts.verifier.managedprovisioning.BYOD_REMOVE";
    // Managed -> managed intent: provisioning completed successfully
    public static final String ACTION_PROFILE_PROVISIONED = "com.android.cts.verifier.managedprovisioning.BYOD_PROVISIONED";
    // Primary -> managed intent: request to capture and check an image
    public static final String ACTION_CAPTURE_AND_CHECK_IMAGE = "com.android.cts.verifier.managedprovisioning.BYOD_CAPTURE_AND_CHECK_IMAGE";
    // Primary -> managed intent: request to capture and check a video with custom output path
    public static final String ACTION_CAPTURE_AND_CHECK_VIDEO_WITH_EXTRA_OUTPUT = "com.android.cts.verifier.managedprovisioning.BYOD_CAPTURE_AND_CHECK_VIDEO_WITH_EXTRA_OUTPUT";
    // Primary -> managed intent: request to capture and check a video without custom output path
    public static final String ACTION_CAPTURE_AND_CHECK_VIDEO_WITHOUT_EXTRA_OUTPUT = "com.android.cts.verifier.managedprovisioning.BYOD_CAPTURE_AND_CHECK_VIDEO_WITHOUT_EXTRA_OUTPUT";
    // Primary -> managed intent: request to capture and check an audio recording
    public static final String ACTION_CAPTURE_AND_CHECK_AUDIO = "com.android.cts.verifier.managedprovisioning.BYOD_CAPTURE_AND_CHECK_AUDIO";
    public static final String ACTION_KEYGUARD_DISABLED_FEATURES =
            "com.android.cts.verifier.managedprovisioning.BYOD_KEYGUARD_DISABLED_FEATURES";
    public static final String ACTION_LOCKNOW =
            "com.android.cts.verifier.managedprovisioning.BYOD_LOCKNOW";
    public static final String ACTION_TEST_NFC_BEAM = "com.android.cts.verifier.managedprovisioning.TEST_NFC_BEAM";

    public static final String EXTRA_PROVISIONED = "extra_provisioned";
    public static final String EXTRA_PARAMETER_1 = "extra_parameter_1";

    // Primary -> managed intent: check if the disk of the device is encrypted
    public static final String ACTION_CHECK_DISK_ENCRYPTION =
            "com.android.cts.verifier.managedprovisioning.action.BYOD_CHECK_DISK_ENCRYPTION";
    // Managed -> primary intent: update disk encryption status in primary's CtsVerifier
    public static final String ACTION_DISK_ENCRYPTION_STATUS =
            "com.android.cts.verifier.managedprovisioning.action.BYOD_DISK_ENCRYPTION_STATUS";
    // Int extra field indicating the encryption status of the device storage
    public static final String EXTRA_ENCRYPTION_STATUS = "extra_encryption_status";

    // Primary -> managed intent: set unknown sources restriction and install package
    public static final String ACTION_INSTALL_APK = "com.android.cts.verifier.managedprovisioning.BYOD_INSTALL_APK";
    public static final String EXTRA_ALLOW_NON_MARKET_APPS = "allow_non_market_apps";

    // Primary -> managed intent: check if the required cross profile intent filters are set.
    public static final String ACTION_CHECK_INTENT_FILTERS =
            "com.android.cts.verifier.managedprovisioning.action.CHECK_INTENT_FILTERS";

    // Primary -> managed intent: will send a cross profile intent and check if the user sees an
    // intent picker dialog and can open the apps.
    public static final String ACTION_TEST_CROSS_PROFILE_INTENTS_DIALOG =
            "com.android.cts.verifier.managedprovisioning.action.TEST_CROSS_PROFILE_INTENTS_DIALOG";

    // Primary -> managed intent: will send an app link intent and check if the user sees a
    // dialog and can open the apps. This test is extremely similar to
    // ACTION_TEST_CROSS_PROFILE_INTENTS_DIALOG, but the intent used is a web intent, and there is
    // some behavior which is specific to web intents.
    public static final String ACTION_TEST_APP_LINKING_DIALOG =
            "com.android.cts.verifier.managedprovisioning.action.TEST_APP_LINKING_DIALOG";

    // Primary -> managed intent: request to goto the location settings page and listen to updates.
    public static final String ACTION_BYOD_SET_LOCATION_AND_CHECK_UPDATES =
            "com.android.cts.verifier.managedprovisioning.BYOD_SET_LOCATION_AND_CHECK";
    public static final String ACTION_NOTIFICATION =
            "com.android.cts.verifier.managedprovisioning.NOTIFICATION";
    public static final String ACTION_NOTIFICATION_ON_LOCKSCREEN =
            "com.android.cts.verifier.managedprovisioning.LOCKSCREEN_NOTIFICATION";
    public static final String ACTION_CLEAR_NOTIFICATION =
            "com.android.cts.verifier.managedprovisioning.CLEAR_NOTIFICATION";

    // Primary -> managed intent: set a user restriction
    public static final String ACTION_SET_USER_RESTRICTION =
            "com.android.cts.verifier.managedprovisioning.BYOD_SET_USER_RESTRICTION";

    // Primary -> managed intent: reset a user restriction
    public static final String ACTION_CLEAR_USER_RESTRICTION =
            "com.android.cts.verifier.managedprovisioning.BYOD_CLEAR_USER_RESTRICTION";

    // Primary -> managed intent: Start the selection of a work challenge
    public static final String ACTION_TEST_SELECT_WORK_CHALLENGE =
            "com.android.cts.verifier.managedprovisioning.TEST_SELECT_WORK_CHALLENGE";

    // Primary -> managed intent: Start the selection of a parent profile password.
    public static final String ACTION_TEST_PARENT_PROFILE_PASSWORD =
            "com.android.cts.verifier.managedprovisioning.TEST_PARENT_PROFILE_PASSWORD";

    // Primary -> managed intent: Start the confirm credentials screen for the managed profile
    public static final String ACTION_LAUNCH_CONFIRM_WORK_CREDENTIALS =
            "com.android.cts.verifier.managedprovisioning.LAUNCH_CONFIRM_WORK_CREDENTIALS";

    public static final String ACTION_SET_ORGANIZATION_INFO =
            "com.android.cts.verifier.managedprovisioning.TEST_ORGANIZATION_INFO";

    public static final int RESULT_FAILED = RESULT_FIRST_USER;

    private static final int REQUEST_INSTALL_PACKAGE = 2;
    private static final int REQUEST_IMAGE_CAPTURE = 3;
    private static final int REQUEST_VIDEO_CAPTURE_WITH_EXTRA_OUTPUT = 4;
    private static final int REQUEST_VIDEO_CAPTURE_WITHOUT_EXTRA_OUTPUT = 5;
    private static final int REQUEST_AUDIO_CAPTURE = 6;
    private static final int REQUEST_LOCATION_UPDATE = 7;

    private static final String ORIGINAL_RESTRICTIONS_NAME = "original restrictions";

    private static final int NOTIFICATION_ID = 7;
    private static final String NOTIFICATION_CHANNEL_ID = TAG;

    private NotificationManager mNotificationManager;
    private Bundle mOriginalRestrictions;

    private ComponentName mAdminReceiverComponent;
    private DevicePolicyManager mDevicePolicyManager;

    private Uri mImageUri;
    private Uri mVideoUri;
    private File mImageFile;

    private ArrayList<File> mTempFiles = new ArrayList<File>();

    private Handler mMainThreadHandler;

    private void showNotification(int visibility) {
        final Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.provisioning_byod_notification_title))
                .setContentText(getString(R.string.provisioning_byod_notification_title))
                .setVisibility(visibility)
                .setAutoCancel(true)
                .setPublicVersion(createPublicVersionNotification())
                .build();
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private Notification createPublicVersionNotification() {
        return new Notification.Builder(this)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.provisioning_byod_notification_public_title))
                .setAutoCancel(true)
                .build();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainThreadHandler = new Handler(getMainLooper());
        if (savedInstanceState != null) {
            Log.w(TAG, "Restored state");
            mOriginalRestrictions = savedInstanceState.getBundle(ORIGINAL_RESTRICTIONS_NAME);
        } else {
            mOriginalRestrictions = new Bundle();
        }

        mAdminReceiverComponent = new ComponentName(this, DeviceAdminTestReceiver.class.getName());
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = getIntent();
        String action = intent.getAction();
        Log.d(TAG, "ByodHelperActivity.onCreate: " + action);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));

        // we are explicitly started by {@link DeviceAdminTestReceiver} after a successful provisioning.
        if (action.equals(ACTION_PROFILE_PROVISIONED)) {
            // Jump back to CTS verifier with result.
            Intent response = new Intent(ACTION_PROFILE_OWNER_STATUS);
            response.putExtra(EXTRA_PROVISIONED, isProfileOwner());
            startActivityInPrimary(response);
            // Queried by CtsVerifier in the primary side using startActivityForResult.
        } else if (action.equals(ACTION_QUERY_PROFILE_OWNER)) {
            Intent response = new Intent();
            response.putExtra(EXTRA_PROVISIONED, isProfileOwner());
            setResult(RESULT_OK, response);
            // Request to delete work profile.
        } else if (action.equals(ACTION_REMOVE_MANAGED_PROFILE)) {
            if (isProfileOwner()) {
                Log.d(TAG, "Clearing cross profile intents");
                mDevicePolicyManager.clearCrossProfileIntentFilters(mAdminReceiverComponent);
                mDevicePolicyManager.wipeData(0);
                showToast(R.string.provisioning_byod_profile_deleted);
            }
        } else if (action.equals(ACTION_CHECK_DISK_ENCRYPTION)) {
            final int status = mDevicePolicyManager.getStorageEncryptionStatus();
            final Intent response = new Intent(ACTION_DISK_ENCRYPTION_STATUS)
                    .putExtra(EXTRA_ENCRYPTION_STATUS, status);
            setResult(RESULT_OK, response);
        } else if (action.equals(ACTION_INSTALL_APK)) {
            boolean allowNonMarket = intent.getBooleanExtra(EXTRA_ALLOW_NON_MARKET_APPS, false);
            boolean wasAllowed = !isUnknownSourcesRestrictionSet();

            if (wasAllowed != allowNonMarket) {
                setUnknownSourcesRestriction(!allowNonMarket);
                mOriginalRestrictions.putBoolean(DISALLOW_INSTALL_UNKNOWN_SOURCES, !wasAllowed);
            }
            // Start the installer activity until this activity is rendered to workaround a glitch.
            mMainThreadHandler.post(() -> {
                // Request to install a non-market application- easiest way is to reinstall ourself
                final Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                        .setData(Uri.parse("package:" + getPackageName()))
                        .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                        .putExtra(Intent.EXTRA_RETURN_RESULT, true);
                startActivityForResult(installIntent, REQUEST_INSTALL_PACKAGE);

            });
            // Not yet ready to finish- wait until the result comes back
            return;
            // Queried by CtsVerifier in the primary side using startActivityForResult.
        } else if (action.equals(ACTION_CHECK_INTENT_FILTERS)) {
            final boolean intentFiltersSetForManagedIntents =
                    new IntentFiltersTestHelper(this).checkCrossProfileIntentFilters(
                            IntentFiltersTestHelper.FLAG_INTENTS_FROM_MANAGED);
            setResult(intentFiltersSetForManagedIntents? RESULT_OK : RESULT_FAILED, null);
        } else if (action.equals(ACTION_CAPTURE_AND_CHECK_IMAGE)) {
            // We need the camera permission to send the image capture intent.
            grantCameraPermissionToSelf();
            Intent captureImageIntent = getCaptureImageIntent();
            Pair<File, Uri> pair = getTempUri("image.jpg");
            mImageFile = pair.first;
            mImageUri = pair.second;
            captureImageIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
            if (captureImageIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(captureImageIntent, REQUEST_IMAGE_CAPTURE);
            } else {
                Log.e(TAG, "Capture image intent could not be resolved in managed profile.");
                showToast(R.string.provisioning_byod_capture_media_error);
                finish();
            }
            return;
        } else if (action.equals(ACTION_CAPTURE_AND_CHECK_VIDEO_WITH_EXTRA_OUTPUT) ||
                action.equals(ACTION_CAPTURE_AND_CHECK_VIDEO_WITHOUT_EXTRA_OUTPUT)) {
            // We need the camera permission to send the video capture intent.
            grantCameraPermissionToSelf();
            Intent captureVideoIntent = getCaptureVideoIntent();
            int videoCaptureRequestId;
            if (action.equals(ACTION_CAPTURE_AND_CHECK_VIDEO_WITH_EXTRA_OUTPUT)) {
                mVideoUri = getTempUri("video.mp4").second;
                captureVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, mVideoUri);
                videoCaptureRequestId = REQUEST_VIDEO_CAPTURE_WITH_EXTRA_OUTPUT;
            } else {
                videoCaptureRequestId = REQUEST_VIDEO_CAPTURE_WITHOUT_EXTRA_OUTPUT;
            }
            if (captureVideoIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(captureVideoIntent, videoCaptureRequestId);
            } else {
                Log.e(TAG, "Capture video intent could not be resolved in managed profile.");
                showToast(R.string.provisioning_byod_capture_media_error);
                finish();
            }
            return;
        } else if (action.equals(ACTION_CAPTURE_AND_CHECK_AUDIO)) {
            Intent captureAudioIntent = getCaptureAudioIntent();
            if (captureAudioIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(captureAudioIntent, REQUEST_AUDIO_CAPTURE);
            } else {
                Log.e(TAG, "Capture audio intent could not be resolved in managed profile.");
                showToast(R.string.provisioning_byod_capture_media_error);
                finish();
            }
            return;
        } else if (ACTION_KEYGUARD_DISABLED_FEATURES.equals(action)) {
            final int value = intent.getIntExtra(EXTRA_PARAMETER_1,
                    DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE);
            mDevicePolicyManager.setKeyguardDisabledFeatures(mAdminReceiverComponent, value);
        } else if (ACTION_LOCKNOW.equals(action)) {
            mDevicePolicyManager.lockNow();
            setResult(RESULT_OK);
        } else if (action.equals(ACTION_TEST_NFC_BEAM)) {
            Intent testNfcBeamIntent = new Intent(this, NfcTestActivity.class);
            testNfcBeamIntent.putExtras(intent);
            startActivity(testNfcBeamIntent);
            finish();
            return;
        } else if (action.equals(ACTION_TEST_CROSS_PROFILE_INTENTS_DIALOG)) {
            sendIntentInsideChooser(new Intent(
                    CrossProfileTestActivity.ACTION_CROSS_PROFILE_TO_PERSONAL));
        } else if (action.equals(ACTION_TEST_APP_LINKING_DIALOG)) {
            mDevicePolicyManager.addUserRestriction(
                    DeviceAdminTestReceiver.getReceiverComponentName(),
                    UserManager.ALLOW_PARENT_PROFILE_APP_LINKING);
            Intent toSend = new Intent(Intent.ACTION_VIEW);
            toSend.setData(Uri.parse("http://com.android.cts.verifier"));
            sendIntentInsideChooser(toSend);
        } else if (action.equals(ACTION_SET_USER_RESTRICTION)) {
            final String restriction = intent.getStringExtra(EXTRA_PARAMETER_1);
            if (restriction != null) {
                mDevicePolicyManager.addUserRestriction(
                        DeviceAdminTestReceiver.getReceiverComponentName(), restriction);
            }
        } else if (action.equals(ACTION_CLEAR_USER_RESTRICTION)) {
            final String restriction = intent.getStringExtra(EXTRA_PARAMETER_1);
            if (restriction != null) {
                mDevicePolicyManager.clearUserRestriction(
                        DeviceAdminTestReceiver.getReceiverComponentName(), restriction);
            }
        } else if (action.equals(ACTION_BYOD_SET_LOCATION_AND_CHECK_UPDATES)) {
            handleLocationAction();
            return;
        } else if (action.equals(ACTION_NOTIFICATION)) {
            showNotification(Notification.VISIBILITY_PUBLIC);
        } else if (ACTION_NOTIFICATION_ON_LOCKSCREEN.equals(action)) {
            mDevicePolicyManager.lockNow();
            showNotification(Notification.VISIBILITY_PRIVATE);
        } else if (ACTION_CLEAR_NOTIFICATION.equals(action)) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        } else if (ACTION_TEST_SELECT_WORK_CHALLENGE.equals(action)) {
            mDevicePolicyManager.setOrganizationColor(mAdminReceiverComponent, Color.BLUE);
            mDevicePolicyManager.setOrganizationName(mAdminReceiverComponent, getResources()
                    .getString(R.string.provisioning_byod_confirm_work_credentials_header));
            startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD));
        } else if (ACTION_LAUNCH_CONFIRM_WORK_CREDENTIALS.equals(action)) {
            KeyguardManager keyguardManager =
                    (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            Intent launchIntent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                showToast(R.string.provisioning_byod_no_secure_lockscreen);
            }
        } else if (ACTION_SET_ORGANIZATION_INFO.equals(action)) {
            if(intent.hasExtra(OrganizationInfoTestActivity.EXTRA_ORGANIZATION_NAME)) {
                final String organizationName = intent
                        .getStringExtra(OrganizationInfoTestActivity.EXTRA_ORGANIZATION_NAME);
                mDevicePolicyManager.setOrganizationName(mAdminReceiverComponent, organizationName);
            }
            final int organizationColor = intent.getIntExtra(
                    OrganizationInfoTestActivity.EXTRA_ORGANIZATION_COLOR,
                    mDevicePolicyManager.getOrganizationColor(mAdminReceiverComponent));
            mDevicePolicyManager.setOrganizationColor(mAdminReceiverComponent, organizationColor);
        } else if (ACTION_TEST_PARENT_PROFILE_PASSWORD.equals(action)) {
            startActivity(new Intent(DevicePolicyManager.ACTION_SET_NEW_PARENT_PROFILE_PASSWORD));
        }
        // This activity has no UI and is only used to respond to CtsVerifier in the primary side.
        finish();
    }

    @Override
    protected void onSaveInstanceState(final Bundle savedState) {
        super.onSaveInstanceState(savedState);

        savedState.putBundle(ORIGINAL_RESTRICTIONS_NAME, mOriginalRestrictions);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_INSTALL_PACKAGE: {
                Log.w(TAG, "Received REQUEST_INSTALL_PACKAGE, resultCode = " + resultCode);
                if (mOriginalRestrictions.containsKey(DISALLOW_INSTALL_UNKNOWN_SOURCES)) {
                    // Restore original setting
                    setUnknownSourcesRestriction(
                            mOriginalRestrictions.getBoolean(DISALLOW_INSTALL_UNKNOWN_SOURCES));
                    mOriginalRestrictions.remove(DISALLOW_INSTALL_UNKNOWN_SOURCES);
                }
                finish();
                break;
            }
            case REQUEST_IMAGE_CAPTURE: {
                if (resultCode == RESULT_OK) {
                    ByodPresentMediaDialog.newImageInstance(mImageFile)
                            .show(getFragmentManager(), "ViewImageDialogFragment");
                } else {
                    // Failed capturing image.
                    finish();
                }
                break;
            }
            case REQUEST_VIDEO_CAPTURE_WITH_EXTRA_OUTPUT: {
                if (resultCode == RESULT_OK) {
                    ByodPresentMediaDialog.newVideoInstance(mVideoUri)
                            .show(getFragmentManager(), "PlayVideoDialogFragment");
                } else {
                    // Failed capturing video.
                    finish();
                }
                break;
            }
            case REQUEST_VIDEO_CAPTURE_WITHOUT_EXTRA_OUTPUT: {
                if (resultCode == RESULT_OK) {
                    ByodPresentMediaDialog.newVideoInstance(data.getData())
                            .show(getFragmentManager(), "PlayVideoDialogFragment");
                } else {
                    // Failed capturing video.
                    finish();
                }
                break;
            }
            case REQUEST_AUDIO_CAPTURE: {
                if (resultCode == RESULT_OK) {
                    ByodPresentMediaDialog.newAudioInstance(data.getData())
                            .show(getFragmentManager(), "PlayAudioDialogFragment");
                } else {
                    // Failed capturing audio.
                    finish();
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        cleanUpTempUris();
        super.onDestroy();
    }

    public static Intent getCaptureImageIntent() {
        return new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    }

    public static Intent getCaptureVideoIntent() {
        return new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
    }

    public static Intent getCaptureAudioIntent() {
        return new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
    }

    public static Intent createLockIntent() {
        return new Intent(ACTION_LOCKNOW);
    }

    private Pair<File, Uri> getTempUri(String fileName) {
        final File file = new File(getFilesDir() + File.separator + "images"
                + File.separator + fileName);
        file.getParentFile().mkdirs(); //if the folder doesn't exists it is created
        mTempFiles.add(file);
        return new Pair<>(file, FileProvider.getUriForFile(this,
                    "com.android.cts.verifier.managedprovisioning.fileprovider", file));
    }

    private void cleanUpTempUris() {
        for (File file : mTempFiles) {
            file.delete();
        }
    }

    private boolean isProfileOwner() {
        return mDevicePolicyManager.isAdminActive(mAdminReceiverComponent) &&
                mDevicePolicyManager.isProfileOwnerApp(mAdminReceiverComponent.getPackageName());
    }

    private boolean isUnknownSourcesRestrictionSet() {
        // We only care about restrictions set by Cts Verifier. In other cases, we cannot modify
        // it and the test will fail anyway.
        Bundle restrictions = mDevicePolicyManager.getUserRestrictions(mAdminReceiverComponent);
        return restrictions.getBoolean(DISALLOW_INSTALL_UNKNOWN_SOURCES, false);
    }

    private void setUnknownSourcesRestriction(boolean enabled) {
        if (enabled) {
            mDevicePolicyManager.addUserRestriction(mAdminReceiverComponent,
                    DISALLOW_INSTALL_UNKNOWN_SOURCES);
        } else {
            mDevicePolicyManager.clearUserRestriction(mAdminReceiverComponent,
                    DISALLOW_INSTALL_UNKNOWN_SOURCES);
        }
    }

    private void startActivityInPrimary(Intent intent) {
        // Disable app components in the current profile, so only the counterpart in the other
        // profile can respond (via cross-profile intent filter)
        getPackageManager().setComponentEnabledSetting(new ComponentName(
                this, ByodFlowTestActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        startActivity(intent);
    }

    private void grantCameraPermissionToSelf() {
        mDevicePolicyManager.setPermissionGrantState(mAdminReceiverComponent, getPackageName(),
                android.Manifest.permission.CAMERA,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
    }

    private void sendIntentInsideChooser(Intent toSend) {
        toSend.putExtra(CrossProfileTestActivity.EXTRA_STARTED_FROM_WORK, true);
        Intent chooser = Intent.createChooser(toSend,
                getResources().getString(R.string.provisioning_cross_profile_chooser));
        startActivity(chooser);
    }

    @Override
    protected void handleLocationAction() {
        // Grant the locaiton permission to the provile owner on cts-verifier.
        // The permission state does not have to be reverted at the end since the profile onwer
        // is going to be deleted when BYOD tests ends.
        grantLocationPermissionToSelf();
        super.handleLocationAction();
    }

    private void grantLocationPermissionToSelf() {
        mDevicePolicyManager.setPermissionGrantState(mAdminReceiverComponent, getPackageName(),
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
    }

    @Override
    public void onDialogClose() {
        finish();
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}
