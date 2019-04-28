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
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_HIDDEN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PAC_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_HOST;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;
import static org.mockito.Mockito.when;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Properties;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link PropertiesProvisioningDataParser} */
@SmallTest
public class PropertiesProvisioningDataParserTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final Integer TEST_MAIN_COLOR = 65280;
    private static final boolean TEST_STARTED_BY_TRUSTED_SOURCE = true;
    private static final boolean TEST_LEAVE_ALL_SYSTEM_APP_ENABLED = true;
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final boolean TEST_SKIP_USER_SETUP = true;
    private static final long TEST_PROVISIONING_ID = 2000L;
    private static final Account TEST_ACCOUNT_TO_MIGRATE =
            new Account("user@gmail.com", "com.google");
    private static final String INVALID_MIME_TYPE = "invalid-mime-type";

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

    @Mock
    private Context mContext;

    @Mock
    private ManagedProvisioningSharedPreferences mSharedPreferences;

    private PropertiesProvisioningDataParser mPropertiesProvisioningDataParser;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mSharedPreferences.incrementAndGetProvisioningId()).thenReturn(TEST_PROVISIONING_ID);
        mPropertiesProvisioningDataParser = new PropertiesProvisioningDataParser(mContext,
                new Utils(), mSharedPreferences);
    }

    public void testParse_nfcProvisioningIntent() throws Exception {
        // GIVEN a NFC provisioning intent with all supported data.
        Properties props = new Properties();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        props.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME);
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                TEST_COMPONENT_NAME.flattenToString());
        setTestWifiInfo(props);
        setTestTimeTimeZoneAndLocale(props);
        setTestDeviceAdminDownload(props);
        props.setProperty(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, getTestAdminExtrasString());
        props.setProperty(
                EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                Boolean.toString(TEST_SKIP_ENCRYPTION));
        // GIVEN main color is supplied even though it is not supported in NFC provisioning.
        props.setProperty(EXTRA_PROVISIONING_MAIN_COLOR, Integer.toString(TEST_MAIN_COLOR));

        props.store(stream, "NFC provisioning intent" /* data description */);

        NdefRecord record = NdefRecord.createMime(
                DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC,
                stream.toByteArray());
        NdefMessage ndfMsg = new NdefMessage(new NdefRecord[]{record});

        Intent intent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
                .setType(MIME_TYPE_PROVISIONING_NFC)
                .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{ndfMsg});

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mPropertiesProvisioningDataParser.parse(intent);


        // THEN ProvisionParams is constructed as expected.
        assertEquals(
                ProvisioningParams.Builder.builder()
                        // THEN NFC provisioning is translated to ACTION_PROVISION_MANAGED_DEVICE
                        // action.
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        .setDeviceAdminDownloadInfo(
                                PackageDownloadInfo.Builder.builder()
                                        .setLocation(TEST_DOWNLOAD_LOCATION)
                                        .setCookieHeader(TEST_COOKIE_HEADER)
                                        .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                                        .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                                        .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                                        // THEN it supports SHA1 package checksum.
                                        .setPackageChecksumSupportsSha1(true)
                                        .build())
                        .setLocalTime(TEST_LOCAL_TIME)
                        .setLocale(TEST_LOCALE)
                        .setTimeZone(TEST_TIME_ZONE)
                        // THEN main color is not supported in NFC intent.
                        .setMainColor(null)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setWifiInfo(TEST_WIFI_INFO)
                        .setAdminExtrasBundle(getTestAdminExtrasPersistableBundle())
                        .setStartedByTrustedSource(true)
                        .setIsNfc(true)
                        .build(),
                params);
    }

    public void testParse_OtherIntentsThrowsException() {
        // GIVEN a managed device provisioning intent and some extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        try {
            // WHEN the intent is parsed by the parser.
            ProvisioningParams params = mPropertiesProvisioningDataParser.parse(intent);
            fail("PropertiesProvisioningDataParser doesn't support intent other than NFC. "
                    + "IllegalProvisioningArgumentException should be thrown");
        } catch (IllegalProvisioningArgumentException e) {
            // THEN IllegalProvisioningArgumentException is thrown.
        }
    }

    public void testGetFirstNdefRecord_nullNdefMessages() {
        // GIVEN nfc intent with no ndef messages
        Intent nfcIntent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
        // WHEN getting first ndef record
        // THEN it should return null
        assertNull(PropertiesProvisioningDataParser.getFirstNdefRecord(nfcIntent));
    }

    public void testGetFirstNdefRecord_noNfcMimeType() {
        // GIVEN nfc intent with no ndf message with a record with a valid mime type.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        NdefRecord record = NdefRecord.createMime(INVALID_MIME_TYPE, stream.toByteArray());
        NdefMessage ndfMsg = new NdefMessage(new NdefRecord[]{record});
        Intent nfcIntent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
                .setType(MIME_TYPE_PROVISIONING_NFC)
                .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{ndfMsg});
        // WHEN getting first ndef record
        // THEN it should return null
        assertNull(PropertiesProvisioningDataParser.getFirstNdefRecord(nfcIntent));
    }

    public void testGetFirstNdefRecord() {
        // GIVEN nfc intent with valid ndf message with a record with mime type nfc.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        NdefRecord record = NdefRecord.createMime(
                DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC,
                stream.toByteArray());
        NdefMessage ndfMsg = new NdefMessage(new NdefRecord[]{record});
        Intent nfcIntent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
                .setType(MIME_TYPE_PROVISIONING_NFC)
                .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{ndfMsg});
        // WHEN getting first ndef record
        // THEN valid record should be returned
        assertEquals(PropertiesProvisioningDataParser.getFirstNdefRecord(nfcIntent), record);
    }

    private static Properties setTestWifiInfo(Properties props) {
        props.setProperty(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        props.setProperty(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_PORT, Integer.toString(TEST_PROXY_PORT));
        props.setProperty(EXTRA_PROVISIONING_WIFI_HIDDEN, Boolean.toString(TEST_HIDDEN));
        return props;
    }

    private static Properties setTestTimeTimeZoneAndLocale(Properties props) {
        props.setProperty(EXTRA_PROVISIONING_LOCAL_TIME, Long.toString(TEST_LOCAL_TIME));
        props.setProperty(EXTRA_PROVISIONING_TIME_ZONE, TEST_TIME_ZONE);
        props.setProperty(EXTRA_PROVISIONING_LOCALE, StoreUtils.localeToString(TEST_LOCALE));
        return props;
    }

    private static Properties setTestDeviceAdminDownload(Properties props) {
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                Integer.toString(TEST_MIN_SUPPORT_VERSION));
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION,
                TEST_DOWNLOAD_LOCATION);
        props.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                TEST_COOKIE_HEADER);
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM,
                Base64.encodeToString(TEST_PACKAGE_CHECKSUM,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP));
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                Base64.encodeToString(TEST_SIGNATURE_CHECKSUM,
                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP));
        return props;
    }

    private static String getTestAdminExtrasString() throws Exception {
        Properties props = new Properties();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        PersistableBundle bundle = getTestAdminExtrasPersistableBundle();
        for (String key : bundle.keySet()) {
            props.setProperty(key, bundle.getString(key));
        }
        props.store(stream, "ADMIN_EXTRAS_BUNDLE" /* data description */);

        return stream.toString();
    }

    private static PersistableBundle getTestAdminExtrasPersistableBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("key1", "val1");
        bundle.putString("key2", "val2");
        bundle.putString("key3", "val3");
        return bundle;
    }
}
