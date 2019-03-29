/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.admin.cts;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserManager;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.Suppress;
import android.util.Log;

import java.util.List;

/**
 * TODO: Make sure DO APIs are not called by PO.
 * Test that exercises {@link DevicePolicyManager}. The test requires that the
 * CtsDeviceAdminReceiver be installed via the CtsDeviceAdmin.apk and be
 * activated via "Settings > Location & security > Select device administrators".
 */
public class DevicePolicyManagerTest extends AndroidTestCase {

    private static final String TAG = DevicePolicyManagerTest.class.getSimpleName();

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mComponent;
    private boolean mDeviceAdmin;
    private boolean mManagedProfiles;
    private PackageManager mPackageManager;

    private static final String TEST_CA_STRING1 =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICVzCCAgGgAwIBAgIJAMvnLHnnfO/IMA0GCSqGSIb3DQEBBQUAMIGGMQswCQYD\n" +
            "VQQGEwJJTjELMAkGA1UECAwCQVAxDDAKBgNVBAcMA0hZRDEVMBMGA1UECgwMSU1G\n" +
            "TCBQVlQgTFREMRAwDgYDVQQLDAdJTUZMIE9VMRIwEAYDVQQDDAlJTUZMLklORk8x\n" +
            "HzAdBgkqhkiG9w0BCQEWEHJhbWVzaEBpbWZsLmluZm8wHhcNMTMwODI4MDk0NDA5\n" +
            "WhcNMjMwODI2MDk0NDA5WjCBhjELMAkGA1UEBhMCSU4xCzAJBgNVBAgMAkFQMQww\n" +
            "CgYDVQQHDANIWUQxFTATBgNVBAoMDElNRkwgUFZUIExURDEQMA4GA1UECwwHSU1G\n" +
            "TCBPVTESMBAGA1UEAwwJSU1GTC5JTkZPMR8wHQYJKoZIhvcNAQkBFhByYW1lc2hA\n" +
            "aW1mbC5pbmZvMFwwDQYJKoZIhvcNAQEBBQADSwAwSAJBAJ738cbTQlNIO7O6nV/f\n" +
            "DJTMvWbPkyHYX8CQ7yXiAzEiZ5bzKJjDJmpRAkUrVinljKns2l6C4++l/5A7pFOO\n" +
            "33kCAwEAAaNQME4wHQYDVR0OBBYEFOdbZP7LaMbgeZYPuds2CeSonmYxMB8GA1Ud\n" +
            "IwQYMBaAFOdbZP7LaMbgeZYPuds2CeSonmYxMAwGA1UdEwQFMAMBAf8wDQYJKoZI\n" +
            "hvcNAQEFBQADQQBdrk6J9koyylMtl/zRfiMAc2zgeC825fgP6421NTxs1rjLs1HG\n" +
            "VcUyQ1/e7WQgOaBHi9TefUJi+4PSVSluOXon\n" +
            "-----END CERTIFICATE-----";

    private static final String MANAGED_PROVISIONING_PKG = "com.android.managedprovisioning";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mComponent = DeviceAdminInfoTest.getReceiverComponent();
        mPackageManager = mContext.getPackageManager();
        mDeviceAdmin = mPackageManager.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
        mManagedProfiles = mDeviceAdmin
                && mPackageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS);
    }

    public void testGetActiveAdmins() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetActiveAdmins");
            return;
        }
        List<ComponentName> activeAdmins = mDevicePolicyManager.getActiveAdmins();
        assertFalse(activeAdmins.isEmpty());
        assertTrue(activeAdmins.contains(mComponent));
        assertTrue(mDevicePolicyManager.isAdminActive(mComponent));
    }

    public void testKeyguardDisabledFeatures() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testKeyguardDisabledFeatures");
            return;
        }
        int originalValue = mDevicePolicyManager.getKeyguardDisabledFeatures(mComponent);
        try {
            for (int which = DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE;
                    which < 2 * DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT; ++which) {
                mDevicePolicyManager.setKeyguardDisabledFeatures(mComponent, which);
                assertEquals(which, mDevicePolicyManager.getKeyguardDisabledFeatures(mComponent));
            }
        } finally {
            mDevicePolicyManager.setKeyguardDisabledFeatures(mComponent, originalValue);
        }
    }

    public void testRequestRemoteBugreport_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRequestRemoteBugreport_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.requestBugreport(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetSecurityLoggingEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSecurityLoggingEnabled_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setSecurityLoggingEnabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testIsSecurityLoggingEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsSecurityLoggingEnabled_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.isSecurityLoggingEnabled(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testRetrieveSecurityLogs_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRetrieveSecurityLogs_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.retrieveSecurityLogs(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testRetrievePreRebootSecurityLogs_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRetrievePreRebootSecurityLogs_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.retrievePreRebootSecurityLogs(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetNetworkLoggingEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetNetworkLoggingEnabled_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setNetworkLoggingEnabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testIsNetworkLoggingEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsNetworkLoggingEnabled_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.isNetworkLoggingEnabled(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerOrManageUsersMessage(e.getMessage());
        }
    }

    public void testRetrieveNetworkLogs_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRetrieveNetworkLogs_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.retrieveNetworkLogs(mComponent, /* batchToken */ 0);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }
    public void testRemoveUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testRemoveUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.removeUser(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetApplicationHidden_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetApplicationHidden_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setApplicationHidden(mComponent, "com.google.anything", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsApplicationHidden_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsApplicationHidden_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isApplicationHidden(mComponent, "com.google.anything");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetGlobalSetting_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetGlobalSetting_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setGlobalSetting(mComponent,
                    Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, "1");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetSecureSetting_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSecureSetting_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setSecureSetting(mComponent,
                    Settings.Secure.SKIP_FIRST_USE_HINTS, "1");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetSecureSetting_failForInstallNonMarketApps() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSecureSetting_failForInstallNonMarketApps");
            return;
        }
        ComponentName profileOwner = DeviceAdminInfoTest.getProfileOwnerComponent();
        try {
            mDevicePolicyManager.setSecureSetting(profileOwner,
                    Settings.Secure.INSTALL_NON_MARKET_APPS, "0");
            fail("did not throw UnsupportedOperationException");
        } catch (UnsupportedOperationException exc) {
            // Supposed to throw. Pass.
        }
    }

    public void testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setMasterVolumeMuted(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsMasterVolumeMuted_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetMasterVolumeMuted_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.isMasterVolumeMuted(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetRecommendedGlobalProxy_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetRecommendedGlobalProxy_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setRecommendedGlobalProxy(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetLockTaskPackages_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetLockTaskPackages_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setLockTaskPackages(mComponent, new String[] {"package"});
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
        }
    }

    // This test registers itself as DO, so this is no longer testable.  We do a positive test
    // for clearDeviceOwnerApp()
    @Suppress
    public void testClearDeviceOwnerApp_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testClearDeviceOwnerApp_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearDeviceOwnerApp("android.admin.app");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSwitchUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSwitchUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.switchUser(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testCreateAndManageUser_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testCreateAndManageUser_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.createAndManageUser(mComponent, "name", mComponent, null, 0);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testInstallCaCert_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testInstallCaCert_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.installCaCert(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testInstallCaCert_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testInstallCaCert_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.installCaCert(null, TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testUninstallCaCert_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallCaCert_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.uninstallCaCert(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testUninstallCaCert_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallCaCert_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.uninstallCaCert(null, TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testGetInstalledCaCerts_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetInstalledCaCerts_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.getInstalledCaCerts(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testGetInstalledCaCerts_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetInstalledCaCerts_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.getInstalledCaCerts(null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testHasCaCertInstalled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testHasCaCertInstalled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.hasCaCertInstalled(mComponent,
                    TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testHasCaCertInstalled_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testHasCaCertInstalled_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.hasCaCertInstalled(null, TEST_CA_STRING1.getBytes());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testUninstallAllUserCaCerts_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallAllUserCaCerts_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.uninstallAllUserCaCerts(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testUninstallAllUserCaCerts_failIfNotCertInstaller() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testUninstallAllUserCaCerts_failIfNotCertInstaller");
            return;
        }
        try {
            // Delegated cert installer is identified by using null as the first argument.
            mDevicePolicyManager.uninstallAllUserCaCerts(null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testSetScreenCaptureDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetScreenCaptureDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setScreenCaptureDisabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetAutoTimeRequired_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetAutoTimeRequired_failIfNotDeviceOrProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setAutoTimeRequired(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testAddPersistentPreferredActivity_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testAddPersistentPreferredActivity_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.addPersistentPreferredActivity(mComponent,
                    new IntentFilter(Intent.ACTION_MAIN),
                    new ComponentName("android.admin.cts", "dummy"));
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testClearPackagePersistentPreferredActivities_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testClearPackagePersistentPreferredActivities_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearPackagePersistentPreferredActivities(mComponent,
                    "android.admin.cts");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetApplicationRestrictions_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetApplicationRestrictions_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setApplicationRestrictions(mComponent,
                    "android.admin.cts", null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testAddUserRestriction_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testAddUserRestriction_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.addUserRestriction(mComponent,
                    UserManager.DISALLOW_SMS);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetAccountManagementDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetAccountManagementDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setAccountManagementDisabled(mComponent,
                    "dummy", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetRestrictionsProvider_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetRestrictionsProvider_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setRestrictionsProvider(mComponent,
                    new ComponentName("android.admin.cts", "dummy"));
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetUninstallBlocked_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetUninstallBlocked_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setUninstallBlocked(mComponent,
                    "android.admin.cts", true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetPermittedAccessibilityServices_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetPermittedAccessibilityServices_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setPermittedAccessibilityServices(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetCrossProfileContactsSearchDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetCrossProfileContactsSearchDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setCrossProfileContactsSearchDisabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetBluetoothContactSharingDisabled_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetBluetoothContactSharingDisabled_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setBluetoothContactSharingDisabled(mComponent, true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetPermittedInputMethods_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetPermittedInputMethods_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setPermittedInputMethods(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    /**
     * Test whether the version of the pre-installed launcher is at least L. This is needed for
     * managed profile support.
     */
    public void testLauncherVersionAtLeastL() throws Exception {
        if (!mManagedProfiles) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(intent,
                0 /* default flags */);
        assertFalse("No launcher present", resolveInfos.isEmpty());

        for (ResolveInfo resolveInfo : resolveInfos) {
            ApplicationInfo launcherAppInfo = mPackageManager.getApplicationInfo(
                    resolveInfo.activityInfo.packageName, 0 /* default flags */);
            if ((launcherAppInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    launcherAppInfo.targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP) {
                return;
            }
        }
        fail("No system launcher with version L+ present present on device.");
    }

    /**
     * Test that managed provisioning is pre-installed if the device declares the device admin
     * feature.
     */
    public void testManagedProvisioningPreInstalled() throws Exception {
        if (mDeviceAdmin) {
            assertTrue(isPackageInstalledOnSystemImage(MANAGED_PROVISIONING_PKG));
        }
    }

    private void assertDeviceOwnerMessage(String message) {
        assertTrue("message is: "+ message, message.contains("does not own the device")
                || message.contains("can only be called by the device owner"));
    }

    private void assertDeviceOwnerOrManageUsersMessage(String message) {
        assertTrue("message is: "+ message, message.contains("does not own the device")
                || message.contains("can only be called by the device owner")
                || (message.startsWith("Neither user ") && message.endsWith(
                        " nor current process has android.permission.MANAGE_USERS.")));
    }

    private void assertProfileOwnerMessage(String message) {
        assertTrue("message is: "+ message,
                message.contains("does not own the profile"));
    }

    public void testSetDelegatedCertInstaller_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetDelegatedCertInstaller_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.setCertInstallerPackage(mComponent, "com.test.package");
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testGetDelegatedCertInstaller_failIfNotProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testGetDelegatedCertInstaller_failIfNotProfileOwner");
            return;
        }
        try {
            mDevicePolicyManager.getCertInstallerPackage(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testSetSystemUpdatePolicy_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetSystemUpdatePolicy_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setSystemUpdatePolicy(mComponent, null);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    private boolean isPackageInstalledOnSystemImage(String packagename) {
        try {
            ApplicationInfo info = mPackageManager.getApplicationInfo(packagename,
                    0 /* default flags */);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public void testReboot_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testReboot_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.reboot(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testSetBackupServiceEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetBackupServiceEnabled");
            return;
        }
        try {
            mDevicePolicyManager.setBackupServiceEnabled(mComponent, false);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testIsBackupServiceEnabled_failIfNotDeviceOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsBackupServiceEnabled");
            return;
        }
        try {
            mDevicePolicyManager.isBackupServiceEnabled(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertDeviceOwnerMessage(e.getMessage());
        }
    }

    public void testCreateAdminSupportIntent_returnNullIfRestrictionIsNotSet() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testCreateAdminSupportIntent");
            return;
        }
        Intent intent = mDevicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_CAMERA);
        assertNull(intent);
        intent = mDevicePolicyManager.createAdminSupportIntent(UserManager.DISALLOW_ADJUST_VOLUME);
        assertNull(intent);
    }

    public void testSetResetPasswordToken_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testSetResetPasswordToken_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.setResetPasswordToken(mComponent, new byte[32]);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testClearResetPasswordToken_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testClearResetPasswordToken_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.clearResetPasswordToken(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testIsResetPasswordTokenActive_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testIsResetPasswordTokenActive_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.isResetPasswordTokenActive(mComponent);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }

    public void testResetPasswordWithToken_failIfNotDeviceOrProfileOwner() {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testResetPasswordWithToken_failIfNotDeviceOwner");
            return;
        }
        try {
            mDevicePolicyManager.resetPasswordWithToken(mComponent, "1234", new byte[32], 0);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertProfileOwnerMessage(e.getMessage());
        }
    }
}
