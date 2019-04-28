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
package com.android.managedprovisioning.preprovisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class EncryptionControllerTest extends AndroidTestCase {
    private static final int TEST_USER_ID = 10;
    private static final String MP_PACKAGE_NAME = "com.android.managedprovisioning";
    private static final ComponentName TEST_HOME_RECEIVER = new ComponentName(MP_PACKAGE_NAME,
            ".HomeReceiverActivity");
    private static final String TEST_MDM_PACKAGE = "com.admin.test";
    private static final int RESUME_PROVISIONING_TIMEOUT_MS = 1000;

    @Mock private Context mContext;
    @Mock private Utils mUtils;
    @Mock private SettingsFacade mSettingsFacade;
    @Mock private Resources mResources;
    @Mock private PackageManager mPackageManager;
    @Mock private EncryptionController.ResumeNotificationHelper mResumeNotificationHelper;

    private EncryptionController mController;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mUtils.isPhysicalDeviceEncrypted()).thenReturn(true);
        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getFilesDir()).thenReturn(getContext().getFilesDir());

        mController = createEncryptionController();
        mController.getProvisioningParamsFile(mContext).delete();
    }

    public void testDeviceOwner() throws Exception {
        // GIVEN we've set a provisioning reminder for device owner provisioning.
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);
        ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_DEVICE);
        setReminder(params);
        verify(mUtils).enableComponent(TEST_HOME_RECEIVER, TEST_USER_ID);
        verify(mPackageManager).flushPackageRestrictionsAsUser(TEST_USER_ID);
        // WHEN resuming the provisioning
        runResumeProvisioningOnUiThread();
        // THEN the pre provisioning activity is started
        verifyStartPreProvisioningActivity(params);
    }

    public void testProfileOwnerAfterSuw() throws Exception {
        // GIVEN we set a provisioning reminder for managed profile provisioning after SUW
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(true);
        ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_PROFILE);
        setReminder(params);
        // WHEN resuming the provisioning
        runResumeProvisioningOnUiThread();
        // THEN we show a notification
        verifyShowResumeNotification(params);
    }

    public void testProfileOwnerDuringSuw() throws Exception {
        // GIVEN we set a provisioning reminder for managed profile provisioning during SUW
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);
        ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_PROFILE);
        setReminder(params);
        verify(mUtils).enableComponent(TEST_HOME_RECEIVER, TEST_USER_ID);
        verify(mPackageManager).flushPackageRestrictionsAsUser(TEST_USER_ID);
        // WHEN resuming the provisioning
        runResumeProvisioningOnUiThread();
        // THEN we start the pre provisioning activity
        verifyStartPreProvisioningActivity(params);
    }

    public void testDeviceNotEncrypted() throws Exception {
        // GIVEN an intent was stored to resume device owner provisioning, but the device
        // is not encrypted
        ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_DEVICE);
        setReminder(params);
        when(mUtils.isPhysicalDeviceEncrypted()).thenReturn(false);
        // WHEN resuming provisioning
        runResumeProvisioningOnUiThread();
        // THEN nothing should happen
        verifyNothingStarted();
    }

    public void testResumeProvisioningNoIntent() throws Exception {
        // GIVEN no reminder is set
        // WHEN resuming the provisioning
        runResumeProvisioningOnUiThread();
        // THEN nothing should happen
        verifyNothingStarted();
    }

    public void testCancelProvisioningReminder() throws Exception {
        // WHEN we've set a provisioning reminder
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(true);
        ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_PROFILE);
        setReminder(params);
        // WHEN canceling the reminder and then resuming the provisioning
        mController.cancelEncryptionReminder();
        verify(mUtils).disableComponent(TEST_HOME_RECEIVER, TEST_USER_ID);
        runResumeProvisioningOnUiThread();
        // THEN nothing should start
        verifyNothingStarted();
    }

    private ProvisioningParams createProvisioningParams(String action) {
        return new ProvisioningParams.Builder()
                .setProvisioningAction(action)
                .setDeviceAdminPackageName(TEST_MDM_PACKAGE)
                .build();
    }

    private void runResumeProvisioningOnUiThread() throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // In a real case, the device may have rebooted between the moment when the
                // reminder was set and the moment we resume the provisioning. Recreate the
                // encryption controller to simulate this.
                createEncryptionController().resumeProvisioning();
                semaphore.release();
            }
        });
        assertTrue("Timeout trying to resume provisioning",
                semaphore.tryAcquire(RESUME_PROVISIONING_TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private EncryptionController createEncryptionController() {
        return new EncryptionController(mContext, mUtils, mSettingsFacade, TEST_HOME_RECEIVER,
                mResumeNotificationHelper, TEST_USER_ID);
    }

    private void setReminder(ProvisioningParams params) {
        mController.setEncryptionReminder(params);
    }

    private void verifyStartPreProvisioningActivity(ProvisioningParams params) throws Exception {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivity(intentCaptor.capture());
        assertEquals(params, intentCaptor.getValue().getParcelableExtra(
                ProvisioningParams.EXTRA_PROVISIONING_PARAMS));
        verify(mResumeNotificationHelper, never()).showResumeNotification(any(Intent.class));
    }

    private void verifyShowResumeNotification(ProvisioningParams params) throws Exception {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mResumeNotificationHelper).showResumeNotification(intentCaptor.capture());
        assertEquals(params, intentCaptor.getValue().getParcelableExtra(
                ProvisioningParams.EXTRA_PROVISIONING_PARAMS));
        verify(mContext, never()).startActivity(any(Intent.class));
    }

    private void verifyNothingStarted() throws Exception {
        verify(mContext, never()).startActivity(any(Intent.class));
        verify(mResumeNotificationHelper, never()).showResumeNotification(any(Intent.class));
    }
}
