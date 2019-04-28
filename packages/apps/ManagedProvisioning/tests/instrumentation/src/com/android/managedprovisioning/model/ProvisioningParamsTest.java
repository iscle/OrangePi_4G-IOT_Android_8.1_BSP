/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.model;

import static com.android.managedprovisioning.TestUtils.createTestAdminExtras;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;
import java.io.File;
import java.util.Locale;

/** Tests for {@link ProvisioningParams} */
public class ProvisioningParamsTest extends AndroidTestCase {
    private static final String TEST_PROVISIONING_ACTION =
            DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final Integer TEST_MAIN_COLOR = 65280;
    private static final boolean TEST_STARTED_BY_TRUSTED_SOURCE = true;
    private static final boolean TEST_IS_NFC = true;
    private static final boolean TEST_LEAVE_ALL_SYSTEM_APP_ENABLED = true;
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final boolean TEST_SKIP_USER_SETUP = true;
    private static final boolean TEST_SKIP_USER_CONSENT = true;
    private static final Account TEST_ACCOUNT_TO_MIGRATE =
            new Account("user@gmail.com", "com.google");

    // Wifi info
    private static final String TEST_SSID = "TestWifi";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_SECURITY_TYPE = "WPA2";
    private static final String TEST_PASSWORD = "GoogleRock";
    private static final String TEST_PROXY_HOST = "testhost.com";
    private static final int TEST_PROXY_PORT = 7689;
    private static final String TEST_PROXY_BYPASS_HOSTS = "http://host1.com;https://host2.com";
    private static final String TEST_PAC_URL = "pac.test.com";
    private static final WifiInfo TEST_WIFI_INFO = WifiInfo.Builder.builder()
            .setSsid(TEST_SSID)
            .setHidden(TEST_HIDDEN)
            .setSecurityType(TEST_SECURITY_TYPE)
            .setPassword(TEST_PASSWORD)
            .setProxyHost(TEST_PROXY_HOST)
            .setProxyPort(TEST_PROXY_PORT)
            .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
            .setPacUrl(TEST_PAC_URL)
            .build();

    // Device admin package download info
    private static final String TEST_DOWNLOAD_LOCATION =
            "http://example/dpc.apk";
    private static final String TEST_COOKIE_HEADER =
            "Set-Cookie: sessionToken=foobar; Expires=Thu, 18 Feb 2016 23:59:59 GMT";
    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[] { '1', '2', '3', '4', '5' };
    private static final byte[] TEST_SIGNATURE_CHECKSUM = new byte[] { '5', '4', '3', '2', '1' };
    private static final int TEST_MIN_SUPPORT_VERSION = 17689;
    private static final PackageDownloadInfo TEST_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setCookieHeader(TEST_COOKIE_HEADER)
                    .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                    .build();

    @SmallTest
    public void testFailToConstructProvisioningParamsWithoutPackageNameOrComponentName() {
        // WHEN the ProvisioningParams is constructed by with neither a package name nor a component
        // name
        try {
            ProvisioningParams provisioningParams = ProvisioningParams.Builder.builder()
                    .setProvisioningAction(TEST_PROVISIONING_ACTION)
                    .build();
            fail("Package name or component name is mandatory.");
        } catch (IllegalArgumentException e) {
            // THEN the ProvisioningParams fails to construct.
        }
    }

    @SmallTest
    public void testFailToConstructProvisioningParamsWithoutProvisioningAction() {
        // WHEN the ProvisioningParams is constructed by without a provisioning action.
        try {
            ProvisioningParams provisioningParams = ProvisioningParams.Builder.builder()
                    .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                    .build();
            fail("Provisioning action is mandatory");
        } catch (NullPointerException e) {
            // THEN the ProvisioningParams fails to construct.
        }
    }

    @SmallTest
    public void testEquals() {
        // GIVEN 2 ProvisioningParams objects created by the same set of parameters
        ProvisioningParams provisioningParams1 = getCompleteProvisioningParams();
        ProvisioningParams provisioningParams2 = getCompleteProvisioningParams();

        // WHEN these two objects compare.
        // THEN they are the same.
        assertEquals(provisioningParams1, provisioningParams2);
    }

    @SmallTest
    public void testNotEquals() {
        // GIVEN 2 ProvisioningParams objects created by different sets of parameters
        ProvisioningParams provisioningParams1 = ProvisioningParams.Builder.builder()
                .setProvisioningAction(TEST_PROVISIONING_ACTION)
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                .setLocalTime(TEST_LOCAL_TIME)
                .setLocale(TEST_LOCALE)
                .setTimeZone(TEST_TIME_ZONE)
                .setMainColor(TEST_MAIN_COLOR)
                .setStartedByTrustedSource(TEST_STARTED_BY_TRUSTED_SOURCE)
                .setLeaveAllSystemAppsEnabled(TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                .setSkipUserSetup(TEST_SKIP_USER_SETUP)
                .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                .setWifiInfo(TEST_WIFI_INFO)
                .setAdminExtrasBundle(createTestAdminExtras())
                .build();
        ProvisioningParams provisioningParams2 = ProvisioningParams.Builder.builder()
                .setProvisioningAction("different.action")
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                .setLocalTime(TEST_LOCAL_TIME)
                .setLocale(TEST_LOCALE)
                .setTimeZone(TEST_TIME_ZONE)
                .setMainColor(TEST_MAIN_COLOR)
                .setStartedByTrustedSource(TEST_STARTED_BY_TRUSTED_SOURCE)
                .setLeaveAllSystemAppsEnabled(TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                .setSkipUserSetup(TEST_SKIP_USER_SETUP)
                .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                .setWifiInfo(TEST_WIFI_INFO)
                .setAdminExtrasBundle(createTestAdminExtras())
                .build();

        // WHEN these two objects compare.
        // THEN they are not the same.
        MoreAsserts.assertNotEqual(provisioningParams1, provisioningParams2);
    }

    @SmallTest
    public void testSaveAndRestoreComplete() throws Exception {
        testSaveAndRestore(getCompleteProvisioningParams());
    }

    // Testing with a minimum set of parameters to cover all null use cases.
    @SmallTest
    public void testSaveAndRestoreMinimalist() throws Exception {
        testSaveAndRestore(ProvisioningParams.Builder.builder()
                .setProvisioningAction(TEST_PROVISIONING_ACTION)
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .build());
    }

    private void testSaveAndRestore(ProvisioningParams original) {
        // GIVEN a ProvisioningParams object
        // WHEN the ProvisioningParams is written to xml and then read back
        File file = new File(mContext.getFilesDir(), "test_store.xml");
        original.save(file);
        ProvisioningParams copy = ProvisioningParams.load(file);
        // THEN the same ProvisioningParams is obtained
        assertEquals(original, copy);
    }

    @SmallTest
    public void testParceable() {
        // GIVEN a ProvisioningParams object.
        ProvisioningParams expectedProvisioningParams = getCompleteProvisioningParams();

        // WHEN the ProvisioningParams is written to parcel and then read back.
        Parcel parcel = Parcel.obtain();
        expectedProvisioningParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ProvisioningParams actualProvisioningParams =
                ProvisioningParams.CREATOR.createFromParcel(parcel);

        // THEN the same ProvisioningParams is obtained.
        assertEquals(expectedProvisioningParams, actualProvisioningParams);
    }

    private ProvisioningParams getCompleteProvisioningParams() {
        return ProvisioningParams.Builder.builder()
                .setProvisioningAction(TEST_PROVISIONING_ACTION)
                .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                .setLocalTime(TEST_LOCAL_TIME)
                .setLocale(TEST_LOCALE)
                .setTimeZone(TEST_TIME_ZONE)
                .setMainColor(TEST_MAIN_COLOR)
                .setStartedByTrustedSource(TEST_STARTED_BY_TRUSTED_SOURCE)
                .setIsNfc(TEST_IS_NFC)
                .setLeaveAllSystemAppsEnabled(TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                .setSkipUserSetup(TEST_SKIP_USER_SETUP)
                .setSkipUserConsent(TEST_SKIP_USER_CONSENT)
                .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                .setWifiInfo(TEST_WIFI_INFO)
                .setAdminExtrasBundle(createTestAdminExtras())
                .build();
    }
}
