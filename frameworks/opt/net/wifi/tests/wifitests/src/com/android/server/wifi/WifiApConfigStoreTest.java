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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Random;

/**
 * Unit tests for {@link com.android.server.wifi.WifiApConfigStore}.
 */
@SmallTest
public class WifiApConfigStoreTest {

    private static final String TAG = "WifiApConfigStoreTest";

    private static final String TEST_AP_CONFIG_FILE_PREFIX = "APConfig_";
    private static final String TEST_DEFAULT_2G_CHANNEL_LIST = "1,2,3,4,5,6";
    private static final String TEST_DEFAULT_AP_SSID = "TestAP";
    private static final String TEST_CONFIGURED_AP_SSID = "ConfiguredAP";
    private static final String TEST_DEFAULT_HOTSPOT_SSID = "TestShare";
    private static final String TEST_DEFAULT_HOTSPOT_PSK = "TestPassword";
    private static final int RAND_SSID_INT_MIN = 1000;
    private static final int RAND_SSID_INT_MAX = 9999;
    private static final String TEST_CHAR_SET_AS_STRING = "abcdefghijklmnopqrstuvwxyz0123456789";

    @Mock Context mContext;
    @Mock BackupManagerProxy mBackupManagerProxy;
    File mApConfigFile;
    Random mRandom;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        /* Create a temporary file for AP config file storage. */
        mApConfigFile = File.createTempFile(TEST_AP_CONFIG_FILE_PREFIX, "");

        /* Setup expectations for Resources to return some default settings. */
        MockResources resources = new MockResources();
        resources.setString(R.string.config_wifi_framework_sap_2G_channel_list,
                            TEST_DEFAULT_2G_CHANNEL_LIST);
        resources.setString(R.string.wifi_tether_configure_ssid_default,
                            TEST_DEFAULT_AP_SSID);
        resources.setString(R.string.wifi_localhotspot_configure_ssid_default,
                            TEST_DEFAULT_HOTSPOT_SSID);
        when(mContext.getResources()).thenReturn(resources);

        mRandom = new Random();
    }

    @After
    public void cleanUp() {
        /* Remove the temporary AP config file. */
        mApConfigFile.delete();
    }

    /**
     * Generate a WifiConfiguration based on the specified parameters.
     */
    private WifiConfiguration setupApConfig(
            String ssid, String preSharedKey, int keyManagement, int band, int channel) {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = ssid;
        config.preSharedKey = preSharedKey;
        config.allowedKeyManagement.set(keyManagement);
        config.apBand = band;
        config.apChannel = channel;
        return config;
    }

    private void writeApConfigFile(WifiConfiguration config) throws Exception {
        Method m = WifiApConfigStore.class.getDeclaredMethod(
                "writeApConfiguration", String.class, WifiConfiguration.class);
        m.setAccessible(true);
        m.invoke(null, mApConfigFile.getPath(), config);
    }

    private void verifyApConfig(WifiConfiguration config1, WifiConfiguration config2) {
        assertEquals(config1.SSID, config2.SSID);
        assertEquals(config1.preSharedKey, config2.preSharedKey);
        assertEquals(config1.getAuthType(), config2.getAuthType());
        assertEquals(config1.apBand, config2.apBand);
        assertEquals(config1.apChannel, config2.apChannel);
    }

    private void verifyDefaultApConfig(WifiConfiguration config, String expectedSsid) {
        String[] splitSsid = config.SSID.split("_");
        assertEquals(2, splitSsid.length);
        assertEquals(expectedSsid, splitSsid[0]);
        int randomPortion = Integer.parseInt(splitSsid[1]);
        assertTrue(randomPortion >= RAND_SSID_INT_MIN && randomPortion <= RAND_SSID_INT_MAX);
        assertTrue(config.allowedKeyManagement.get(KeyMgmt.WPA2_PSK));
    }

    /**
     * AP Configuration is not specified in the config file,
     * WifiApConfigStore should fallback to use the default configuration.
     */
    @Test
    public void initWithDefaultConfiguration() throws Exception {
        WifiApConfigStore store = new WifiApConfigStore(
                mContext, mBackupManagerProxy, mApConfigFile.getPath());
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
    }

    /**
     * Verify WifiApConfigStore can correctly load the existing configuration
     * from the config file.
     */
    @Test
    public void initWithExistingConfiguration() throws Exception {
        WifiConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",    /* SSID */
                "randomKey",       /* preshared key */
                KeyMgmt.WPA_EAP,   /* key management */
                1,                 /* AP band (5GHz) */
                40                 /* AP channel */);
        writeApConfigFile(expectedConfig);
        WifiApConfigStore store = new WifiApConfigStore(
                mContext, mBackupManagerProxy, mApConfigFile.getPath());
        verifyApConfig(expectedConfig, store.getApConfiguration());
    }

    /**
     * Verify the handling of setting a null ap configuration.
     * WifiApConfigStore should fallback to the default configuration when
     * null ap configuration is provided.
     */
    @Test
    public void setNullApConfiguration() throws Exception {
        /* Initialize WifiApConfigStore with existing configuration. */
        WifiConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",    /* SSID */
                "randomKey",       /* preshared key */
                KeyMgmt.WPA_EAP,   /* key management */
                1,                 /* AP band (5GHz) */
                40                 /* AP channel */);
        writeApConfigFile(expectedConfig);
        WifiApConfigStore store = new WifiApConfigStore(
                mContext, mBackupManagerProxy, mApConfigFile.getPath());
        verifyApConfig(expectedConfig, store.getApConfiguration());

        store.setApConfiguration(null);
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);
        verify(mBackupManagerProxy).notifyDataChanged();
    }

    /**
     * Verify AP configuration is correctly updated via setApConfiguration call.
     */
    @Test
    public void updateApConfiguration() throws Exception {
        /* Initialize WifiApConfigStore with default configuration. */
        WifiApConfigStore store = new WifiApConfigStore(
                mContext, mBackupManagerProxy, mApConfigFile.getPath());
        verifyDefaultApConfig(store.getApConfiguration(), TEST_DEFAULT_AP_SSID);

        /* Update with a valid configuration. */
        WifiConfiguration expectedConfig = setupApConfig(
                "ConfiguredAP",    /* SSID */
                "randomKey",       /* preshared key */
                KeyMgmt.WPA_EAP,   /* key management */
                1,                 /* AP band (5GHz) */
                40                 /* AP channel */);
        store.setApConfiguration(expectedConfig);
        verifyApConfig(expectedConfig, store.getApConfiguration());
        verify(mBackupManagerProxy).notifyDataChanged();
    }

    /**
     * Verify a proper WifiConfiguration is generate by getDefaultApConfiguration().
     */
    @Test
    public void getDefaultApConfigurationIsValid() {
        WifiApConfigStore store = new WifiApConfigStore(
                mContext, mBackupManagerProxy, mApConfigFile.getPath());
        WifiConfiguration config = store.getApConfiguration();
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));
    }

    /**
     * Verify a proper local only hotspot config is generated when called properly with the valid
     * context.
     */
    @Test
    public void generateLocalOnlyHotspotConfigIsValid() {
        WifiConfiguration config = WifiApConfigStore.generateLocalOnlyHotspotConfig(mContext);
        verifyDefaultApConfig(config, TEST_DEFAULT_HOTSPOT_SSID);
        // The LOHS config should also have a specific network id set - check that as well.
        assertEquals(WifiConfiguration.LOCAL_ONLY_NETWORK_ID, config.networkId);

        // verify that the config passes the validateApWifiConfiguration check
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));
    }

    /**
     * Helper method to generate random SSIDs.
     *
     * Note: this method has limited use as a random SSID generator.  The characters used in this
     * method do no not cover all valid inputs.
     * @param length number of characters to generate for the name
     * @return String generated string of random characters
     */
    private String generateRandomString(int length) {

        StringBuilder stringBuilder = new StringBuilder(length);
        int index = -1;
        while (stringBuilder.length() < length) {
            index = mRandom.nextInt(TEST_CHAR_SET_AS_STRING.length());
            stringBuilder.append(TEST_CHAR_SET_AS_STRING.charAt(index));
        }
        return stringBuilder.toString();
    }

    /**
     * Verify the SSID checks in validateApWifiConfiguration.
     *
     * Cases to check and verify they trigger failed verification:
     * null WifiConfiguration.SSID
     * empty WifiConfiguration.SSID
     * invalid WifiConfiguaration.SSID length
     *
     * Additionally check a valid SSID with a random (within valid ranges) length.
     */
    @Test
    public void testSsidVerificationInValidateApWifiConfigurationCheck() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = null;
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));
        config.SSID = "";
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));
        // check a string that is too large
        config.SSID = generateRandomString(WifiApConfigStore.SSID_MAX_LEN + 1);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));

        // now check a valid SSID with a random length
        int validLength = WifiApConfigStore.SSID_MAX_LEN - WifiApConfigStore.SSID_MIN_LEN;
        config.SSID = generateRandomString(
                mRandom.nextInt(validLength) + WifiApConfigStore.SSID_MIN_LEN);
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));
    }

    /**
     * Verify the Open network checks in validateApWifiConfiguration.
     *
     * If the configured network is open, it should not have a password set.
     *
     * Additionally verify a valid open network passes verification.
     */
    @Test
    public void testOpenNetworkConfigInValidateApWifiConfigurationCheck() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_DEFAULT_HOTSPOT_SSID;

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.preSharedKey = TEST_DEFAULT_HOTSPOT_PSK;
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));

        // open networks should not have a password set
        config.preSharedKey = null;
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));
        config.preSharedKey = "";
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));
    }

    /**
     * Verify the WPA2_PSK network checks in validateApWifiConfiguration.
     *
     * If the configured network is configured with a preSharedKey, verify that the passwork is set
     * and it meets length requirements.
     */
    @Test
    public void testWpa2PskNetworkConfigInValidateApWifiConfigurationCheck() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_DEFAULT_HOTSPOT_SSID;

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        config.preSharedKey = null;
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));
        config.preSharedKey = "";
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));

        // test too short
        config.preSharedKey =
                generateRandomString(WifiApConfigStore.PSK_MIN_LEN - 1);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));

        // test too long
        config.preSharedKey =
                generateRandomString(WifiApConfigStore.PSK_MAX_LEN + 1);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));

        // explicitly test min length
        config.preSharedKey =
            generateRandomString(WifiApConfigStore.PSK_MIN_LEN);
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));

        // explicitly test max length
        config.preSharedKey =
                generateRandomString(WifiApConfigStore.PSK_MAX_LEN);
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));

        // test random (valid length)
        int maxLen = WifiApConfigStore.PSK_MAX_LEN;
        int minLen = WifiApConfigStore.PSK_MIN_LEN;
        config.preSharedKey =
                generateRandomString(mRandom.nextInt(maxLen - minLen) + minLen);
        assertTrue(WifiApConfigStore.validateApWifiConfiguration(config));
    }

    /**
     * Verify an invalid AuthType setting (that would trigger an IllegalStateException)
     * returns false when triggered in the validateApWifiConfiguration.
     */
    @Test
    public void testInvalidAuthTypeInValidateApWifiConfigurationCheck() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_DEFAULT_HOTSPOT_SSID;

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));
    }

    /**
     * Verify an unsupported authType returns false for validateApWifiConfigurationCheck.
     */
    @Test
    public void testUnsupportedAuthTypeInValidateApWifiConfigurationCheck() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TEST_DEFAULT_HOTSPOT_SSID;

        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        assertFalse(WifiApConfigStore.validateApWifiConfiguration(config));
    }
}
