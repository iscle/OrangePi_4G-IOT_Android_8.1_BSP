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
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.CODE_MANAGED_USERS_NOT_SUPPORTED;
import static android.app.admin.DevicePolicyManager.CODE_OK;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;

import static com.android.managedprovisioning.common.Globals.ACTION_RESUME_PROVISIONING;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import static java.util.Collections.emptyList;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.VectorDrawable;
import android.os.UserManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.test.AndroidTestCase;
import android.text.TextUtils;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;
import com.android.managedprovisioning.parser.MessageParser;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class PreProvisioningControllerTest extends AndroidTestCase {
    private static final String TEST_MDM_PACKAGE = "com.test.mdm";
    private static final String TEST_MDM_PACKAGE_LABEL = "Test MDM";
    private static final ComponentName TEST_MDM_COMPONENT_NAME = new ComponentName(TEST_MDM_PACKAGE,
            "com.test.mdm.DeviceAdmin");
    private static final String TEST_BOGUS_PACKAGE = "com.test.bogus";
    private static final String TEST_WIFI_SSID = "TestNet";
    private static final String MP_PACKAGE_NAME = "com.android.managedprovisioning";
    private static final int TEST_USER_ID = 10;

    @Mock
    private Context mContext;
    @Mock
    Resources mResources;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private UserManager mUserManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private PersistentDataBlockManager mPdbManager;
    @Mock
    private PreProvisioningController.Ui mUi;
    @Mock
    private MessageParser mMessageParser;
    @Mock
    private Utils mUtils;
    @Mock
    private SettingsFacade mSettingsFacade;
    @Mock
    private Intent mIntent;
    @Mock
    private EncryptionController mEncryptionController;
    @Mock
    private TimeLogger mTimeLogger;

    private ProvisioningParams mParams;

    private PreProvisioningController mController;

    @Override
    public void setUp() throws PackageManager.NameNotFoundException {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(mUserManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(mActivityManager);
        when(mContext.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(mKeyguardManager);
        when(mContext.getSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE))
                .thenReturn(mPdbManager);
        when(mContext.getPackageName()).thenReturn(MP_PACKAGE_NAME);
        when(mContext.getResources()).thenReturn(
                InstrumentationRegistry.getTargetContext().getResources());

        when(mUserManager.getUserHandle()).thenReturn(TEST_USER_ID);

        when(mUtils.isSplitSystemUser()).thenReturn(false);
        when(mUtils.isEncryptionRequired()).thenReturn(false);
        when(mUtils.currentLauncherSupportsManagedProfiles(mContext)).thenReturn(true);
        when(mUtils.alreadyHasManagedProfile(mContext)).thenReturn(-1);

        when(mPackageManager.getApplicationIcon(anyString())).thenReturn(new VectorDrawable());
        when(mPackageManager.getApplicationLabel(any())).thenReturn(TEST_MDM_PACKAGE_LABEL);

        when(mKeyguardManager.inKeyguardRestrictedInputMode()).thenReturn(false);
        when(mDevicePolicyManager.getStorageEncryptionStatus())
                .thenReturn(DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE);
        mController = new PreProvisioningController(mContext, mUi, mTimeLogger, mMessageParser,
                mUtils, mSettingsFacade, mEncryptionController);
    }

    public void testManagedProfile() throws Exception {
        // GIVEN an intent to provision a managed profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start profile provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_provisioningNotAllowed() throws Exception {
        // GIVEN an intent to provision a managed profile, but provisioning mode is not allowed
        prepareMocksForManagedProfileIntent(false);
        when(mDevicePolicyManager.checkProvisioningPreCondition(
                ACTION_PROVISION_MANAGED_PROFILE, TEST_MDM_PACKAGE))
                .thenReturn(CODE_MANAGED_USERS_NOT_SUPPORTED);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN show an error dialog
        verify(mUi).showErrorAndClose(eq(R.string.cant_add_work_profile),
                eq(R.string.work_profile_cant_be_added_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_nullCallingPackage() throws Exception {
        // GIVEN a device that is not currently encrypted
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // THEN error is shown
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.contact_your_admin_for_help), any(String.class));
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_invalidCallingPackage() throws Exception {
        // GIVEN a device that is not currently encrypted
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, "com.android.invalid.dpc");
        // THEN error is shown
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.contact_your_admin_for_help), any(String.class));
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_withEncryption() throws Exception {
        // GIVEN a device that is not currently encrypted
        prepareMocksForManagedProfileIntent(false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating managed profile provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // THEN show encryption screen
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_afterEncryption() throws Exception {
        // GIVEN managed profile provisioning continues after successful encryption. In this case
        // we don't set the startedByTrustedSource flag.
        prepareMocksForAfterEncryption(ACTION_PROVISION_MANAGED_PROFILE, false);
        // WHEN initiating with a continuation intent
        mController.initiateProvisioning(mIntent, null, MP_PACKAGE_NAME);
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start profile provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_withExistingProfile() throws Exception {
        // GIVEN a managed profile currently exist on the device
        prepareMocksForManagedProfileIntent(false);
        when(mUtils.alreadyHasManagedProfile(mContext)).thenReturn(TEST_USER_ID);
        // WHEN initiating managed profile provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly and a dialog to remove the existing
        // profile should be shown
        verifyInitiateProfileOwnerUi();
        verify(mUi).showDeleteManagedProfileDialog(any(), any(), eq(TEST_USER_ID));
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start profile provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_badLauncher() throws Exception {
        // GIVEN that the current launcher does not support managed profiles
        prepareMocksForManagedProfileIntent(false);
        when(mUtils.currentLauncherSupportsManagedProfiles(mContext)).thenReturn(false);
        // WHEN initiating managed profile provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN show a dialog indicating that the current launcher is invalid
        verify(mUi).showCurrentLauncherInvalid();
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_wrongPackage() throws Exception {
        // GIVEN that the provisioning intent tries to set a package different from the caller
        // as owner of the profile
        prepareMocksForManagedProfileIntent(false);
        // WHEN initiating managed profile provisioning
        mController.initiateProvisioning(mIntent, null, TEST_BOGUS_PACKAGE);
        // THEN show an error dialog and do not continue
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.contact_your_admin_for_help), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_frp() throws Exception {
        // GIVEN managed profile provisioning is invoked from SUW with FRP active
        prepareMocksForManagedProfileIntent(false);
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(false);
        // setting the data block size to any number greater than 0 should invoke FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // WHEN initiating managed profile provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN show an error dialog and do not continue
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_has_reset_protection_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testCheckFactoryResetProtection_skipFrp() throws Exception {
        // GIVEN managed profile provisioning is invoked from SUW with FRP active
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(false);
        // setting the data block size to any number greater than 0 to simulate FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // GIVEN there is a persistent data package.
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getString(anyInt())).thenReturn("test.persistent.data");
        // GIVEN the persistent data package is a system app.
        PackageInfo packageInfo = new PackageInfo();
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        packageInfo.applicationInfo = applicationInfo;
        when(mPackageManager.getPackageInfo(eq("test.persistent.data"), anyInt()))
                .thenReturn(packageInfo);

        // WHEN factory reset protection is checked for trusted source device provisioning.
        ProvisioningParams provisioningParams = createParams(true, false, null,
                ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE, TEST_MDM_PACKAGE);
        boolean result = mController.checkFactoryResetProtection(
                provisioningParams, "test.persistent.data");

        // THEN the check is successful despite the FRP data presence.
        assertTrue(result);
    }

    public void testManagedProfile_skipEncryption() throws Exception {
        // GIVEN an intent to provision a managed profile with skip encryption
        prepareMocksForManagedProfileIntent(true);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start profile provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mUi, never()).requestEncryption(any(ProvisioningParams.class));
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testManagedProfile_encryptionNotSupported() throws Exception {
        // GIVEN an intent to provision a managed profile on an unencrypted device that does not
        // support encryption
        prepareMocksForManagedProfileIntent(false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        when(mDevicePolicyManager.getStorageEncryptionStatus())
                .thenReturn(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN the UI elements should be updated accordingly
        verifyInitiateProfileOwnerUi();
        // THEN show an error indicating that this device does not support encryption
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_doesnt_allow_encryption_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testNfc() throws Exception {
        // GIVEN provisioning was started via an NFC tap and device is already encrypted
        prepareMocksForNfcIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        // WHEN initiating NFC provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testNfc_skipEncryption() throws Exception {
        // GIVEN provisioning was started via an NFC tap with encryption skipped
        prepareMocksForNfcIntent(ACTION_PROVISION_MANAGED_DEVICE, true);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating NFC provisioning

        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mUi, never()).requestEncryption(any(ProvisioningParams.class));
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testNfc_withEncryption() throws Exception {
        // GIVEN provisioning was started via an NFC tap with encryption necessary
        prepareMocksForNfcIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating NFC provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN show encryption screen
        verifyInitiateDeviceOwnerUi();
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

    public void testNfc_afterEncryption() throws Exception {
        // GIVEN provisioning was started via an NFC tap and we have gone through encryption
        // in this case the device gets resumed with the DO intent and startedByTrustedSource flag
        // set
        prepareMocksForAfterEncryption(ACTION_PROVISION_MANAGED_DEVICE, true);
        // WHEN continuing NFC provisioning after encryption
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verifyNoMoreInteractions(mUi);
    }

    public void testNfc_frp() throws Exception {
        // GIVEN provisioning was started via an NFC tap, but the device is locked with FRP
        prepareMocksForNfcIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        // setting the data block size to any number greater than 0 should invoke FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // WHEN initiating NFC provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // THEN show an error dialog
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_has_reset_protection_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testNfc_encryptionNotSupported() throws Exception {
        // GIVEN provisioning was started via an NFC tap, the device is not encrypted and encryption
        // is not supported on the device
        prepareMocksForNfcIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        when(mDevicePolicyManager.getStorageEncryptionStatus())
                .thenReturn(DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED);
        // WHEN initiating NFC provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN show an error dialog
        verifyInitiateDeviceOwnerUi();
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_doesnt_allow_encryption_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testQr() throws Exception {
        // GIVEN provisioning was started via a QR code and device is already encrypted
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verifyNoMoreInteractions(mUi);
    }

    public void testQr_skipEncryption() throws Exception {
        // GIVEN provisioning was started via a QR code with encryption skipped
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, true);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verifyInitiateDeviceOwnerUi();
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mUi, never()).requestEncryption(any());
        verifyNoMoreInteractions(mUi);
    }

    public void testQr_withEncryption() throws Exception {
        // GIVEN provisioning was started via a QR code with encryption necessary
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN show encryption screen
        verifyInitiateDeviceOwnerUi();
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

    public void testQr_frp() throws Exception {
        // GIVEN provisioning was started via a QR code, but the device is locked with FRP
        prepareMocksForQrIntent(ACTION_PROVISION_MANAGED_DEVICE, false);
        // setting the data block size to any number greater than 0 should invoke FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // WHEN initiating QR provisioning
        mController.initiateProvisioning(mIntent, null, null);
        // THEN show an error dialog
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_has_reset_protection_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testDeviceOwner() throws Exception {
        // GIVEN device owner provisioning was started and device is already encrypted
        prepareMocksForDoIntent(true);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly
        verifyInitiateDeviceOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testDeviceOwner_skipEncryption() throws Exception {
        // GIVEN device owner provisioning was started with skip encryption flag
        prepareMocksForDoIntent(true);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN the UI elements should be updated accordingly
        verifyInitiateDeviceOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mUi, never()).requestEncryption(any());
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    // TODO: There is a difference in behaviour here between the managed profile and the device
    // owner case: In managed profile case, we invoke encryption after user clicks next, but in
    // device owner mode we invoke it straight away. Also in theory no need to update
    // the UI elements if we're moving away from this activity straight away.
    public void testDeviceOwner_withEncryption() throws Exception {
        // GIVEN device owner provisioning is started with encryption needed
        prepareMocksForDoIntent(false);
        when(mUtils.isEncryptionRequired()).thenReturn(true);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN update the UI elements and show encryption screen
        verifyInitiateDeviceOwnerUi();
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

    public void testDeviceOwner_afterEncryption() throws Exception {
        // GIVEN device owner provisioning is continued after encryption. In this case we do not set
        // the startedByTrustedSource flag.
        prepareMocksForAfterEncryption(ACTION_PROVISION_MANAGED_DEVICE, false);
        // WHEN provisioning is continued
        mController.initiateProvisioning(mIntent, null, null);
        // THEN the UI elements should be updated accordingly
        verifyInitiateDeviceOwnerUi();
        // WHEN the user consents
        mController.continueProvisioningAfterUserConsent();
        // THEN start device owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
        verify(mEncryptionController).cancelEncryptionReminder();
        verifyNoMoreInteractions(mUi);
    }

    public void testNullParams() throws Exception {
        // THEN verifying params is null initially
        assertNull(mController.getParams());
    }

    public void testDeviceOwner_frp() throws Exception {
        // GIVEN device owner provisioning is invoked with FRP active
        prepareMocksForDoIntent(false);
        // setting the data block size to any number greater than 0 should invoke FRP.
        when(mPdbManager.getDataBlockSize()).thenReturn(4);
        // WHEN initiating provisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN show an error dialog
        verify(mUi).showErrorAndClose(eq(R.string.cant_set_up_device),
                eq(R.string.device_has_reset_protection_contact_admin), any());
        verifyNoMoreInteractions(mUi);
    }

    public void testMaybeStartProfileOwnerProvisioningIfSkipUserConsent_continueProvisioning()
            throws Exception {
        // GIVEN skipping user consent and encryption
        prepareMocksForMaybeStartProvisioning(true, true, false);
        // WHEN calling initiateProvisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN start profile owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
    }

    public void testMaybeStartProfileOwnerProvisioningIfSkipUserConsent_notSkipUserConsent()
            throws Exception {
        // GIVEN not skipping user consent
        prepareMocksForMaybeStartProvisioning(false, true, false);
        // WHEN calling initiateProvisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN not starting profile owner provisioning
        verify(mUi, never()).startProvisioning(mUserManager.getUserHandle(), mParams);
    }

    public void testMaybeStartProfileOwnerProvisioningIfSkipUserConsent_requireEncryption()
            throws Exception {
        // GIVEN skipping user consent and encryption
        prepareMocksForMaybeStartProvisioning(true, false, false);
        // WHEN calling initiateProvisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN not starting profile owner provisioning
        verify(mUi, never()).startProvisioning(anyInt(), any());
        // THEN show encryption ui
        verify(mUi).requestEncryption(mParams);
        verifyNoMoreInteractions(mUi);
    }

    public void testMaybeStartProfileOwnerProvisioningIfSkipUserConsent_managedProfileExists()
            throws Exception {
        // GIVEN skipping user consent and encryption, but current managed profile exists
        prepareMocksForMaybeStartProvisioning(true, true, true);
        // WHEN calling initiateProvisioning
        mController.initiateProvisioning(mIntent, null, TEST_MDM_PACKAGE);
        // THEN not starting profile owner provisioning
        verify(mUi, never()).startProvisioning(mUserManager.getUserHandle(), mParams);
        // THEN show UI to delete user
        verify(mUi).showDeleteManagedProfileDialog(any(), any(), anyInt());
        // WHEN user agrees to remove the current profile and continue provisioning
        mController.continueProvisioningAfterUserConsent();
        // THEN start profile owner provisioning
        verify(mUi).startProvisioning(mUserManager.getUserHandle(), mParams);
    }

    private void prepareMocksForMaybeStartProvisioning(
            boolean skipUserConsent, boolean skipEncryption, boolean managedProfileExists)
            throws IllegalProvisioningArgumentException {
        String action = ACTION_PROVISION_MANAGED_PROFILE;
        when(mDevicePolicyManager.checkProvisioningPreCondition(action, TEST_MDM_PACKAGE))
                .thenReturn(CODE_OK);
        mParams = ProvisioningParams.Builder.builder()
                .setProvisioningAction(action)
                .setDeviceAdminComponentName(TEST_MDM_COMPONENT_NAME)
                .setSkipUserConsent(skipUserConsent)
                .build();

        when(mUtils.alreadyHasManagedProfile(mContext)).thenReturn(
                managedProfileExists ? 10 : -1);
        when(mUtils.isEncryptionRequired()).thenReturn(!skipEncryption);


        when(mMessageParser.parse(mIntent)).thenReturn(mParams);
    }

    private void prepareMocksForManagedProfileIntent(boolean skipEncryption) throws Exception {
        final String action = ACTION_PROVISION_MANAGED_PROFILE;
        when(mIntent.getAction()).thenReturn(action);
        when(mUtils.findDeviceAdmin(TEST_MDM_PACKAGE, null, mContext))
                .thenReturn(TEST_MDM_COMPONENT_NAME);
        when(mSettingsFacade.isDeviceProvisioned(mContext)).thenReturn(true);
        when(mDevicePolicyManager.checkProvisioningPreCondition(action, TEST_MDM_PACKAGE))
                .thenReturn(CODE_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(false, skipEncryption, null, action, TEST_MDM_PACKAGE));
    }

    private void prepareMocksForNfcIntent(String action, boolean skipEncryption) throws Exception {
        when(mIntent.getAction()).thenReturn(ACTION_NDEF_DISCOVERED);
        when(mIntent.getComponent()).thenReturn(ComponentName.createRelative(MP_PACKAGE_NAME,
                ".PreProvisioningActivityViaNfc"));
        when(mDevicePolicyManager.checkProvisioningPreCondition(action, TEST_MDM_PACKAGE))
                .thenReturn(CODE_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(true, skipEncryption, TEST_WIFI_SSID, action, TEST_MDM_PACKAGE));
    }

    private void prepareMocksForQrIntent(String action, boolean skipEncryption) throws Exception {
        when(mIntent.getAction())
                .thenReturn(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE);
        when(mIntent.getComponent()).thenReturn(ComponentName.createRelative(MP_PACKAGE_NAME,
                ".PreProvisioningActivityViaTrustedApp"));
        when(mDevicePolicyManager.checkProvisioningPreCondition(action, TEST_MDM_PACKAGE))
                .thenReturn(CODE_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(true, skipEncryption, TEST_WIFI_SSID, action, TEST_MDM_PACKAGE));
    }

    private void prepareMocksForDoIntent(boolean skipEncryption) throws Exception {
        final String action = ACTION_PROVISION_MANAGED_DEVICE;
        when(mIntent.getAction()).thenReturn(action);
        when(mDevicePolicyManager.checkProvisioningPreCondition(action, TEST_MDM_PACKAGE))
                .thenReturn(CODE_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(false, skipEncryption, TEST_WIFI_SSID, action, TEST_MDM_PACKAGE));
    }

    private void prepareMocksForAfterEncryption(String action, boolean startedByTrustedSource)
            throws Exception {
        when(mIntent.getAction()).thenReturn(ACTION_RESUME_PROVISIONING);
        when(mIntent.getComponent()).thenReturn(ComponentName.createRelative(MP_PACKAGE_NAME,
                ".PreProvisioningActivityAfterEncryption"));
        when(mDevicePolicyManager.checkProvisioningPreCondition(action, TEST_MDM_PACKAGE))
                .thenReturn(CODE_OK);
        when(mMessageParser.parse(mIntent)).thenReturn(
                createParams(
                        startedByTrustedSource, false, TEST_WIFI_SSID, action, TEST_MDM_PACKAGE));
    }

    private ProvisioningParams createParams(boolean startedByTrustedSource, boolean skipEncryption,
            String wifiSsid, String action, String packageName) {
        ProvisioningParams.Builder builder = ProvisioningParams.Builder.builder()
                .setStartedByTrustedSource(startedByTrustedSource)
                .setSkipEncryption(skipEncryption)
                .setProvisioningAction(action)
                .setDeviceAdminPackageName(packageName);
        if (!TextUtils.isEmpty(wifiSsid)) {
            builder.setWifiInfo(WifiInfo.Builder.builder().setSsid(wifiSsid).build());
        }
        return mParams = builder.build();
    }

    private void verifyInitiateProfileOwnerUi() {
        verify(mUi).initiateUi(eq(R.layout.intro_profile_owner),
                eq(R.string.setup_profile), any(), any(), eq(true),
                eq(false), eq(emptyList()), any());
    }

    private void verifyInitiateDeviceOwnerUi() {
        verify(mUi).initiateUi(eq(R.layout.intro_device_owner),
                eq(R.string.setup_device), eq(TEST_MDM_PACKAGE_LABEL), any(), eq(false),
                eq(false), eq(emptyList()), any());
    }
}