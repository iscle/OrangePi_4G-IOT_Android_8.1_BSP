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

package com.android.server.wifi.hotspot2;

import static android.net.wifi.WifiManager.ACTION_PASSPOINT_DEAUTH_IMMINENT;
import static android.net.wifi.WifiManager.ACTION_PASSPOINT_ICON;
import static android.net.wifi.WifiManager.ACTION_PASSPOINT_SUBSCRIPTION_REMEDIATION;
import static android.net.wifi.WifiManager.EXTRA_BSSID_LONG;
import static android.net.wifi.WifiManager.EXTRA_DELAY;
import static android.net.wifi.WifiManager.EXTRA_ESS;
import static android.net.wifi.WifiManager.EXTRA_FILENAME;
import static android.net.wifi.WifiManager.EXTRA_ICON;
import static android.net.wifi.WifiManager.EXTRA_SUBSCRIPTION_REMEDIATION_METHOD;
import static android.net.wifi.WifiManager.EXTRA_URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.wifi.EAPConstants;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.UserHandle;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;
import android.util.Pair;

import com.android.server.wifi.Clock;
import com.android.server.wifi.FakeKeys;
import com.android.server.wifi.IMSIParameter;
import com.android.server.wifi.SIMAccessor;
import com.android.server.wifi.WifiConfigManager;
import com.android.server.wifi.WifiConfigStore;
import com.android.server.wifi.WifiKeyStore;
import com.android.server.wifi.WifiMetrics;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.hotspot2.anqp.ANQPElement;
import com.android.server.wifi.hotspot2.anqp.Constants.ANQPElementType;
import com.android.server.wifi.hotspot2.anqp.DomainNameElement;
import com.android.server.wifi.hotspot2.anqp.HSOsuProvidersElement;
import com.android.server.wifi.hotspot2.anqp.I18Name;
import com.android.server.wifi.hotspot2.anqp.OsuProviderInfo;
import com.android.server.wifi.util.ScanResultUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unit tests for {@link com.android.server.wifi.hotspot2.PasspointManager}.
 */
@SmallTest
public class PasspointManagerTest {
    private static final long BSSID = 0x112233445566L;
    private static final String ICON_FILENAME = "test";
    private static final String TEST_FQDN = "test1.test.com";
    private static final String TEST_FRIENDLY_NAME = "friendly name";
    private static final String TEST_REALM = "realm.test.com";
    private static final String TEST_IMSI = "1234*";
    private static final IMSIParameter TEST_IMSI_PARAM = IMSIParameter.build(TEST_IMSI);

    private static final String TEST_SSID = "TestSSID";
    private static final long TEST_BSSID = 0x112233445566L;
    private static final String TEST_BSSID_STRING = "11:22:33:44:55:66";
    private static final long TEST_HESSID = 0x5678L;
    private static final int TEST_ANQP_DOMAIN_ID = 0;
    private static final ANQPNetworkKey TEST_ANQP_KEY = ANQPNetworkKey.buildKey(
            TEST_SSID, TEST_BSSID, TEST_HESSID, TEST_ANQP_DOMAIN_ID);
    private static final int TEST_CREATOR_UID = 1234;

    @Mock Context mContext;
    @Mock WifiNative mWifiNative;
    @Mock WifiKeyStore mWifiKeyStore;
    @Mock Clock mClock;
    @Mock SIMAccessor mSimAccessor;
    @Mock PasspointObjectFactory mObjectFactory;
    @Mock PasspointEventHandler.Callbacks mCallbacks;
    @Mock AnqpCache mAnqpCache;
    @Mock ANQPRequestManager mAnqpRequestManager;
    @Mock CertificateVerifier mCertVerifier;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiConfigStore mWifiConfigStore;
    @Mock PasspointConfigStoreData.DataSource mDataSource;
    @Mock WifiMetrics mWifiMetrics;
    PasspointManager mManager;

    /** Sets up test. */
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        when(mObjectFactory.makeAnqpCache(mClock)).thenReturn(mAnqpCache);
        when(mObjectFactory.makeANQPRequestManager(any(), eq(mClock)))
                .thenReturn(mAnqpRequestManager);
        when(mObjectFactory.makeCertificateVerifier()).thenReturn(mCertVerifier);
        mManager = new PasspointManager(mContext, mWifiNative, mWifiKeyStore, mClock,
                mSimAccessor, mObjectFactory, mWifiConfigManager, mWifiConfigStore, mWifiMetrics);
        ArgumentCaptor<PasspointEventHandler.Callbacks> callbacks =
                ArgumentCaptor.forClass(PasspointEventHandler.Callbacks.class);
        verify(mObjectFactory).makePasspointEventHandler(any(WifiNative.class),
                                                         callbacks.capture());
        ArgumentCaptor<PasspointConfigStoreData.DataSource> dataSource =
                ArgumentCaptor.forClass(PasspointConfigStoreData.DataSource.class);
        verify(mObjectFactory).makePasspointConfigStoreData(
                any(WifiKeyStore.class), any(SIMAccessor.class), dataSource.capture());
        mCallbacks = callbacks.getValue();
        mDataSource = dataSource.getValue();
    }

    /**
     * Verify {@link WifiManager#ACTION_PASSPOINT_ICON} broadcast intent.
     * @param bssid BSSID of the AP
     * @param fileName Name of the icon file
     * @param data icon data byte array
     */
    private void verifyIconIntent(long bssid, String fileName, byte[] data) {
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(intent.capture(), eq(UserHandle.ALL),
                eq(android.Manifest.permission.ACCESS_WIFI_STATE));
        assertEquals(ACTION_PASSPOINT_ICON, intent.getValue().getAction());
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_BSSID_LONG));
        assertEquals(bssid, intent.getValue().getExtras().getLong(EXTRA_BSSID_LONG));
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_FILENAME));
        assertEquals(fileName, intent.getValue().getExtras().getString(EXTRA_FILENAME));
        if (data != null) {
            assertTrue(intent.getValue().getExtras().containsKey(EXTRA_ICON));
            Icon icon = (Icon) intent.getValue().getExtras().getParcelable(EXTRA_ICON);
            assertTrue(Arrays.equals(data, icon.getDataBytes()));
        } else {
            assertFalse(intent.getValue().getExtras().containsKey(EXTRA_ICON));
        }
    }

    /**
     * Verify that the given Passpoint configuration matches the one that's added to
     * the PasspointManager.
     *
     * @param expectedConfig The expected installed Passpoint configuration
     */
    private void verifyInstalledConfig(PasspointConfiguration expectedConfig) {
        List<PasspointConfiguration> installedConfigs = mManager.getProviderConfigs();
        assertEquals(1, installedConfigs.size());
        assertEquals(expectedConfig, installedConfigs.get(0));
    }

    /**
     * Create a mock PasspointProvider with default expectations.
     *
     * @param config The configuration associated with the provider
     * @return {@link com.android.server.wifi.hotspot2.PasspointProvider}
     */
    private PasspointProvider createMockProvider(PasspointConfiguration config) {
        PasspointProvider provider = mock(PasspointProvider.class);
        when(provider.installCertsAndKeys()).thenReturn(true);
        when(provider.getConfig()).thenReturn(config);
        return provider;
    }

    /**
     * Helper function for creating a test configuration with user credential.
     *
     * @return {@link PasspointConfiguration}
     */
    private PasspointConfiguration createTestConfigWithUserCredential() {
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(TEST_FQDN);
        homeSp.setFriendlyName(TEST_FRIENDLY_NAME);
        config.setHomeSp(homeSp);
        Credential credential = new Credential();
        credential.setRealm(TEST_REALM);
        credential.setCaCertificate(FakeKeys.CA_CERT0);
        Credential.UserCredential userCredential = new Credential.UserCredential();
        userCredential.setUsername("username");
        userCredential.setPassword("password");
        userCredential.setEapType(EAPConstants.EAP_TTLS);
        userCredential.setNonEapInnerMethod(Credential.UserCredential.AUTH_METHOD_MSCHAP);
        credential.setUserCredential(userCredential);
        config.setCredential(credential);
        return config;
    }

    /**
     * Helper function for creating a test configuration with SIM credential.
     *
     * @return {@link PasspointConfiguration}
     */
    private PasspointConfiguration createTestConfigWithSimCredential() {
        PasspointConfiguration config = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(TEST_FQDN);
        homeSp.setFriendlyName(TEST_FRIENDLY_NAME);
        config.setHomeSp(homeSp);
        Credential credential = new Credential();
        credential.setRealm(TEST_REALM);
        Credential.SimCredential simCredential = new Credential.SimCredential();
        simCredential.setImsi(TEST_IMSI);
        simCredential.setEapType(EAPConstants.EAP_SIM);
        credential.setSimCredential(simCredential);
        config.setCredential(credential);
        return config;
    }

    /**
     * Helper function for adding a test provider to the manager.  Return the mock
     * provider that's added to the manager.
     *
     * @return {@link PasspointProvider}
     */
    private PasspointProvider addTestProvider() {
        PasspointConfiguration config = createTestConfigWithUserCredential();
        PasspointProvider provider = createMockProvider(config);
        when(mObjectFactory.makePasspointProvider(eq(config), eq(mWifiKeyStore),
                eq(mSimAccessor), anyLong(), eq(TEST_CREATOR_UID))).thenReturn(provider);
        assertTrue(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));

        return provider;
    }

    /**
     * Helper function for creating a ScanResult for testing.
     *
     * @return {@link ScanResult}
     */
    private ScanResult createTestScanResult() {
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = TEST_SSID;
        scanResult.BSSID = TEST_BSSID_STRING;
        scanResult.hessid = TEST_HESSID;
        scanResult.flags = ScanResult.FLAG_PASSPOINT_NETWORK;
        return scanResult;
    }

    /**
     * Verify that the ANQP elements will be added to the ANQP cache on receiving a successful
     * response.
     *
     * @throws Exception
     */
    @Test
    public void anqpResponseSuccess() throws Exception {
        Map<ANQPElementType, ANQPElement> anqpElementMap = new HashMap<>();
        anqpElementMap.put(ANQPElementType.ANQPDomName,
                new DomainNameElement(Arrays.asList(new String[] {"test.com"})));

        when(mAnqpRequestManager.onRequestCompleted(TEST_BSSID, true)).thenReturn(TEST_ANQP_KEY);
        mCallbacks.onANQPResponse(TEST_BSSID, anqpElementMap);
        verify(mAnqpCache).addEntry(TEST_ANQP_KEY, anqpElementMap);
        verify(mContext, never()).sendBroadcastAsUser(any(Intent.class), any(UserHandle.class),
                any(String.class));
    }

    /**
     * Verify that no ANQP elements will be added to the ANQP cache on receiving a successful
     * response for a request that's not sent by us.
     *
     * @throws Exception
     */
    @Test
    public void anqpResponseSuccessWithUnknownRequest() throws Exception {
        Map<ANQPElementType, ANQPElement> anqpElementMap = new HashMap<>();
        anqpElementMap.put(ANQPElementType.ANQPDomName,
                new DomainNameElement(Arrays.asList(new String[] {"test.com"})));

        when(mAnqpRequestManager.onRequestCompleted(TEST_BSSID, true)).thenReturn(null);
        mCallbacks.onANQPResponse(TEST_BSSID, anqpElementMap);
        verify(mAnqpCache, never()).addEntry(any(ANQPNetworkKey.class), anyMap());
    }

    /**
     * Verify that no ANQP elements will be added to the ANQP cache on receiving a failure response.
     *
     * @throws Exception
     */
    @Test
    public void anqpResponseFailure() throws Exception {
        when(mAnqpRequestManager.onRequestCompleted(TEST_BSSID, false)).thenReturn(TEST_ANQP_KEY);
        mCallbacks.onANQPResponse(TEST_BSSID, null);
        verify(mAnqpCache, never()).addEntry(any(ANQPNetworkKey.class), anyMap());

    }

    /**
     * Validate the broadcast intent when icon file retrieval succeeded.
     *
     * @throws Exception
     */
    @Test
    public void iconResponseSuccess() throws Exception {
        byte[] iconData = new byte[] {0x00, 0x11};
        mCallbacks.onIconResponse(BSSID, ICON_FILENAME, iconData);
        verifyIconIntent(BSSID, ICON_FILENAME, iconData);
    }

    /**
     * Validate the broadcast intent when icon file retrieval failed.
     *
     * @throws Exception
     */
    @Test
    public void iconResponseFailure() throws Exception {
        mCallbacks.onIconResponse(BSSID, ICON_FILENAME, null);
        verifyIconIntent(BSSID, ICON_FILENAME, null);
    }

    /**
     * Validate the broadcast intent {@link WifiManager#ACTION_PASSPOINT_DEAUTH_IMMINENT} when
     * Deauth Imminent WNM frame is received.
     *
     * @throws Exception
     */
    @Test
    public void onDeauthImminentReceived() throws Exception {
        String reasonUrl = "test.com";
        int delay = 123;
        boolean ess = true;

        mCallbacks.onWnmFrameReceived(new WnmData(BSSID, reasonUrl, ess, delay));
        // Verify the broadcast intent.
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(intent.capture(), eq(UserHandle.ALL),
                eq(android.Manifest.permission.ACCESS_WIFI_STATE));
        assertEquals(ACTION_PASSPOINT_DEAUTH_IMMINENT, intent.getValue().getAction());
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_BSSID_LONG));
        assertEquals(BSSID, intent.getValue().getExtras().getLong(EXTRA_BSSID_LONG));
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_ESS));
        assertEquals(ess, intent.getValue().getExtras().getBoolean(EXTRA_ESS));
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_DELAY));
        assertEquals(delay, intent.getValue().getExtras().getInt(EXTRA_DELAY));
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_URL));
        assertEquals(reasonUrl, intent.getValue().getExtras().getString(EXTRA_URL));
    }

    /**
     * Validate the broadcast intent {@link WifiManager#ACTION_PASSPOINT_SUBSCRIPTION_REMEDIATION}
     * when Subscription Remediation WNM frame is received.
     *
     * @throws Exception
     */
    @Test
    public void onSubscriptionRemediationReceived() throws Exception {
        int serverMethod = 1;
        String serverUrl = "testUrl";

        mCallbacks.onWnmFrameReceived(new WnmData(BSSID, serverUrl, serverMethod));
        // Verify the broadcast intent.
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcastAsUser(intent.capture(), eq(UserHandle.ALL),
                eq(android.Manifest.permission.ACCESS_WIFI_STATE));
        assertEquals(ACTION_PASSPOINT_SUBSCRIPTION_REMEDIATION, intent.getValue().getAction());
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_BSSID_LONG));
        assertEquals(BSSID, intent.getValue().getExtras().getLong(EXTRA_BSSID_LONG));
        assertTrue(intent.getValue().getExtras().containsKey(
                EXTRA_SUBSCRIPTION_REMEDIATION_METHOD));
        assertEquals(serverMethod, intent.getValue().getExtras().getInt(
                EXTRA_SUBSCRIPTION_REMEDIATION_METHOD));
        assertTrue(intent.getValue().getExtras().containsKey(EXTRA_URL));
        assertEquals(serverUrl, intent.getValue().getExtras().getString(EXTRA_URL));
    }

    /**
     * Verify that adding a provider with a null configuration will fail.
     *
     * @throws Exception
     */
    @Test
    public void addProviderWithNullConfig() throws Exception {
        assertFalse(mManager.addOrUpdateProvider(null, TEST_CREATOR_UID));
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics, never()).incrementNumPasspointProviderInstallSuccess();
    }

    /**
     * Verify that adding a provider with a empty configuration will fail.
     *
     * @throws Exception
     */
    @Test
    public void addProviderWithEmptyConfig() throws Exception {
        assertFalse(mManager.addOrUpdateProvider(new PasspointConfiguration(), TEST_CREATOR_UID));
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics, never()).incrementNumPasspointProviderInstallSuccess();
    }

    /**
     * Verify taht adding a provider with an invalid credential will fail (using EAP-TLS
     * for user credential).
     *
     * @throws Exception
     */
    @Test
    public void addProviderWithInvalidCredential() throws Exception {
        PasspointConfiguration config = createTestConfigWithUserCredential();
        // EAP-TLS not allowed for user credential.
        config.getCredential().getUserCredential().setEapType(EAPConstants.EAP_TLS);
        assertFalse(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics, never()).incrementNumPasspointProviderInstallSuccess();
    }

    /**
     * Verify that adding a provider with a valid configuration and user credential will succeed.
     *
     * @throws Exception
     */
    @Test
    public void addRemoveProviderWithValidUserCredential() throws Exception {
        PasspointConfiguration config = createTestConfigWithUserCredential();
        PasspointProvider provider = createMockProvider(config);
        when(mObjectFactory.makePasspointProvider(eq(config), eq(mWifiKeyStore),
                eq(mSimAccessor), anyLong(), eq(TEST_CREATOR_UID))).thenReturn(provider);
        assertTrue(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));
        verifyInstalledConfig(config);
        verify(mWifiConfigManager).saveToStore(true);
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics).incrementNumPasspointProviderInstallSuccess();
        reset(mWifiMetrics);
        reset(mWifiConfigManager);

        // Verify content in the data source.
        List<PasspointProvider> providers = mDataSource.getProviders();
        assertEquals(1, providers.size());
        assertEquals(config, providers.get(0).getConfig());
        // Provider index start with 0, should be 1 after adding a provider.
        assertEquals(1, mDataSource.getProviderIndex());

        // Remove the provider.
        assertTrue(mManager.removeProvider(TEST_FQDN));
        verify(provider).uninstallCertsAndKeys();
        verify(mWifiConfigManager).saveToStore(true);
        verify(mWifiMetrics).incrementNumPasspointProviderUninstallation();
        verify(mWifiMetrics).incrementNumPasspointProviderUninstallSuccess();
        assertTrue(mManager.getProviderConfigs().isEmpty());

        // Verify content in the data source.
        assertTrue(mDataSource.getProviders().isEmpty());
        // Removing a provider should not change the provider index.
        assertEquals(1, mDataSource.getProviderIndex());
    }

    /**
     * Verify that adding a provider with a valid configuration and SIM credential will succeed.
     *
     * @throws Exception
     */
    @Test
    public void addRemoveProviderWithValidSimCredential() throws Exception {
        PasspointConfiguration config = createTestConfigWithSimCredential();
        PasspointProvider provider = createMockProvider(config);
        when(mObjectFactory.makePasspointProvider(eq(config), eq(mWifiKeyStore),
                eq(mSimAccessor), anyLong(), eq(TEST_CREATOR_UID))).thenReturn(provider);
        assertTrue(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));
        verifyInstalledConfig(config);
        verify(mWifiConfigManager).saveToStore(true);
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics).incrementNumPasspointProviderInstallSuccess();
        reset(mWifiMetrics);
        reset(mWifiConfigManager);

        // Verify content in the data source.
        List<PasspointProvider> providers = mDataSource.getProviders();
        assertEquals(1, providers.size());
        assertEquals(config, providers.get(0).getConfig());
        // Provider index start with 0, should be 1 after adding a provider.
        assertEquals(1, mDataSource.getProviderIndex());

        // Remove the provider.
        assertTrue(mManager.removeProvider(TEST_FQDN));
        verify(provider).uninstallCertsAndKeys();
        verify(mWifiConfigManager).saveToStore(true);
        verify(mWifiMetrics).incrementNumPasspointProviderUninstallation();
        verify(mWifiMetrics).incrementNumPasspointProviderUninstallSuccess();
        assertTrue(mManager.getProviderConfigs().isEmpty());

        // Verify content in the data source.
        assertTrue(mDataSource.getProviders().isEmpty());
        // Removing a provider should not change the provider index.
        assertEquals(1, mDataSource.getProviderIndex());
    }

    /**
     * Verify that adding a provider with the same base domain as the existing provider will
     * succeed, and verify that the existing provider is replaced by the new provider with
     * the new configuration.
     *
     * @throws Exception
     */
    @Test
    public void addProviderWithExistingConfig() throws Exception {
        // Add a provider with the original configuration.
        PasspointConfiguration origConfig = createTestConfigWithSimCredential();
        PasspointProvider origProvider = createMockProvider(origConfig);
        when(mObjectFactory.makePasspointProvider(eq(origConfig), eq(mWifiKeyStore),
                eq(mSimAccessor), anyLong(), eq(TEST_CREATOR_UID))).thenReturn(origProvider);
        assertTrue(mManager.addOrUpdateProvider(origConfig, TEST_CREATOR_UID));
        verifyInstalledConfig(origConfig);
        verify(mWifiConfigManager).saveToStore(true);
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics).incrementNumPasspointProviderInstallSuccess();
        reset(mWifiMetrics);
        reset(mWifiConfigManager);

        // Verify data source content.
        List<PasspointProvider> origProviders = mDataSource.getProviders();
        assertEquals(1, origProviders.size());
        assertEquals(origConfig, origProviders.get(0).getConfig());
        assertEquals(1, mDataSource.getProviderIndex());

        // Add another provider with the same base domain as the existing provider.
        // This should replace the existing provider with the new configuration.
        PasspointConfiguration newConfig = createTestConfigWithUserCredential();
        PasspointProvider newProvider = createMockProvider(newConfig);
        when(mObjectFactory.makePasspointProvider(eq(newConfig), eq(mWifiKeyStore),
                eq(mSimAccessor), anyLong(), eq(TEST_CREATOR_UID))).thenReturn(newProvider);
        assertTrue(mManager.addOrUpdateProvider(newConfig, TEST_CREATOR_UID));
        verifyInstalledConfig(newConfig);
        verify(mWifiConfigManager).saveToStore(true);
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics).incrementNumPasspointProviderInstallSuccess();

        // Verify data source content.
        List<PasspointProvider> newProviders = mDataSource.getProviders();
        assertEquals(1, newProviders.size());
        assertEquals(newConfig, newProviders.get(0).getConfig());
        assertEquals(2, mDataSource.getProviderIndex());
    }

    /**
     * Verify that adding a provider will fail when failing to install certificates and
     * key to the keystore.
     *
     * @throws Exception
     */
    @Test
    public void addProviderOnKeyInstallationFailiure() throws Exception {
        PasspointConfiguration config = createTestConfigWithUserCredential();
        PasspointProvider provider = mock(PasspointProvider.class);
        when(provider.installCertsAndKeys()).thenReturn(false);
        when(mObjectFactory.makePasspointProvider(eq(config), eq(mWifiKeyStore),
                eq(mSimAccessor), anyLong(), eq(TEST_CREATOR_UID))).thenReturn(provider);
        assertFalse(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics, never()).incrementNumPasspointProviderInstallSuccess();
    }

    /**
     * Verify that adding a provider with an invalid CA certificate will fail.
     *
     * @throws Exception
     */
    @Test
    public void addProviderWithInvalidCaCert() throws Exception {
        PasspointConfiguration config = createTestConfigWithUserCredential();
        doThrow(new GeneralSecurityException())
                .when(mCertVerifier).verifyCaCert(any(X509Certificate.class));
        assertFalse(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics, never()).incrementNumPasspointProviderInstallSuccess();
    }

    /**
     * Verify that adding a provider with R2 configuration will not perform CA certificate
     * verification.
     *
     * @throws Exception
     */
    @Test
    public void addProviderWithR2Config() throws Exception {
        PasspointConfiguration config = createTestConfigWithUserCredential();
        config.setUpdateIdentifier(1);
        PasspointProvider provider = createMockProvider(config);
        when(mObjectFactory.makePasspointProvider(eq(config), eq(mWifiKeyStore),
                eq(mSimAccessor), anyLong(), eq(TEST_CREATOR_UID))).thenReturn(provider);
        assertTrue(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));
        verify(mCertVerifier, never()).verifyCaCert(any(X509Certificate.class));
        verifyInstalledConfig(config);
        verify(mWifiMetrics).incrementNumPasspointProviderInstallation();
        verify(mWifiMetrics).incrementNumPasspointProviderInstallSuccess();
    }

    /**
     * Verify that removing a non-existing provider will fail.
     *
     * @throws Exception
     */
    @Test
    public void removeNonExistingProvider() throws Exception {
        assertFalse(mManager.removeProvider(TEST_FQDN));
        verify(mWifiMetrics).incrementNumPasspointProviderUninstallation();
        verify(mWifiMetrics, never()).incrementNumPasspointProviderUninstallSuccess();
    }

    /**
     * Verify that a {code null} will be returned when no providers are installed.
     *
     * @throws Exception
     */
    @Test
    public void matchProviderWithNoProvidersInstalled() throws Exception {
        assertNull(mManager.matchProvider(createTestScanResult()));
    }

    /**
     * Verify that a {code null} be returned when ANQP entry doesn't exist in the cache.
     *
     * @throws Exception
     */
    @Test
    public void matchProviderWithAnqpCacheMissed() throws Exception {
        addTestProvider();

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(null);
        assertNull(mManager.matchProvider(createTestScanResult()));
        // Verify that a request for ANQP elements is initiated.
        verify(mAnqpRequestManager).requestANQPElements(eq(TEST_BSSID), any(ANQPNetworkKey.class),
                anyBoolean(), anyBoolean());
    }

    /**
     * Verify that the expected provider will be returned when a HomeProvider is matched.
     *
     * @throws Exception
     */
    @Test
    public void matchProviderAsHomeProvider() throws Exception {
        PasspointProvider provider = addTestProvider();
        ANQPData entry = new ANQPData(mClock, null);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        when(provider.match(anyMap())).thenReturn(PasspointMatch.HomeProvider);
        Pair<PasspointProvider, PasspointMatch> result =
                mManager.matchProvider(createTestScanResult());
        assertEquals(PasspointMatch.HomeProvider, result.second);
        assertEquals(TEST_FQDN, result.first.getConfig().getHomeSp().getFqdn());
    }

    /**
     * Verify that the expected provider will be returned when a RoamingProvider is matched.
     *
     * @throws Exception
     */
    @Test
    public void matchProviderAsRoamingProvider() throws Exception {
        PasspointProvider provider = addTestProvider();
        ANQPData entry = new ANQPData(mClock, null);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        when(provider.match(anyMap())).thenReturn(PasspointMatch.RoamingProvider);
        Pair<PasspointProvider, PasspointMatch> result =
                mManager.matchProvider(createTestScanResult());
        assertEquals(PasspointMatch.RoamingProvider, result.second);
        assertEquals(TEST_FQDN, result.first.getConfig().getHomeSp().getFqdn());
    }

    /**
     * Verify that a {code null} will be returned when there is no matching provider.
     *
     * @throws Exception
     */
    @Test
    public void matchProviderWithNoMatch() throws Exception {
        PasspointProvider provider = addTestProvider();
        ANQPData entry = new ANQPData(mClock, null);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        when(provider.match(anyMap())).thenReturn(PasspointMatch.None);
        assertNull(mManager.matchProvider(createTestScanResult()));
    }

    /**
     * Verify the expectations for sweepCache.
     *
     * @throws Exception
     */
    @Test
    public void sweepCache() throws Exception {
        mManager.sweepCache();
        verify(mAnqpCache).sweep();
    }

    /**
     * Verify that an empty map will be returned if ANQP elements are not cached for the given AP.
     *
     * @throws Exception
     */
    @Test
    public void getANQPElementsWithNoMatchFound() throws Exception {
        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(null);
        assertTrue(mManager.getANQPElements(createTestScanResult()).isEmpty());
    }

    /**
     * Verify that an expected ANQP elements will be returned if ANQP elements are cached for the
     * given AP.
     *
     * @throws Exception
     */
    @Test
    public void getANQPElementsWithMatchFound() throws Exception {
        Map<ANQPElementType, ANQPElement> anqpElementMap = new HashMap<>();
        anqpElementMap.put(ANQPElementType.ANQPDomName,
                new DomainNameElement(Arrays.asList(new String[] {"test.com"})));
        ANQPData entry = new ANQPData(mClock, anqpElementMap);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        assertEquals(anqpElementMap, mManager.getANQPElements(createTestScanResult()));
    }

    /**
     * Verify that an expected {@link WifiConfiguration} will be returned when a {@link ScanResult}
     * is matched to a home provider.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingWifiConfigForHomeProviderAP() throws Exception {
        PasspointProvider provider = addTestProvider();
        ANQPData entry = new ANQPData(mClock, null);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        when(provider.match(anyMap())).thenReturn(PasspointMatch.HomeProvider);
        when(provider.getWifiConfig()).thenReturn(new WifiConfiguration());
        WifiConfiguration config = mManager.getMatchingWifiConfig(createTestScanResult());
        assertEquals(ScanResultUtil.createQuotedSSID(TEST_SSID), config.SSID);
        assertTrue(config.isHomeProviderNetwork);
    }

    /**
     * Verify that an expected {@link WifiConfiguration} will be returned when a {@link ScanResult}
     * is matched to a roaming provider.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingWifiConfigForRoamingProviderAP() throws Exception {
        PasspointProvider provider = addTestProvider();
        ANQPData entry = new ANQPData(mClock, null);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        when(provider.match(anyMap())).thenReturn(PasspointMatch.RoamingProvider);
        when(provider.getWifiConfig()).thenReturn(new WifiConfiguration());
        WifiConfiguration config = mManager.getMatchingWifiConfig(createTestScanResult());
        assertEquals(ScanResultUtil.createQuotedSSID(TEST_SSID), config.SSID);
        assertFalse(config.isHomeProviderNetwork);
    }

    /**
     * Verify that a {code null} will be returned when a {@link ScanResult} doesn't match any
     * provider.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingWifiConfigWithNoMatchingProvider() throws Exception {
        PasspointProvider provider = addTestProvider();
        ANQPData entry = new ANQPData(mClock, null);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        when(provider.match(anyMap())).thenReturn(PasspointMatch.None);
        assertNull(mManager.getMatchingWifiConfig(createTestScanResult()));
        verify(provider, never()).getWifiConfig();
    }

    /**
     * Verify that a {@code null} will be returned when trying to get a matching
     * {@link WifiConfiguration} for a {@code null} {@link ScanResult}.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingWifiConfigWithNullScanResult() throws Exception {
        assertNull(mManager.getMatchingWifiConfig(null));
    }

    /**
     * Verify that a {@code null} will be returned when trying to get a matching
     * {@link WifiConfiguration} for a {@link ScanResult} with a {@code null} BSSID.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingWifiConfigWithNullBSSID() throws Exception {
        ScanResult scanResult = createTestScanResult();
        scanResult.BSSID = null;
        assertNull(mManager.getMatchingWifiConfig(scanResult));
    }

    /**
     * Verify that a {@code null} will be returned when trying to get a matching
     * {@link WifiConfiguration} for a {@link ScanResult} with an invalid BSSID.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingWifiConfigWithInvalidBSSID() throws Exception {
        ScanResult scanResult = createTestScanResult();
        scanResult.BSSID = "asdfdasfas";
        assertNull(mManager.getMatchingWifiConfig(scanResult));
    }

    /**
     * Verify that a {@code null} will be returned when trying to get a matching
     * {@link WifiConfiguration} for a non-Passpoint AP.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingWifiConfigForNonPasspointAP() throws Exception {
        ScanResult scanResult = createTestScanResult();
        scanResult.flags = 0;
        assertNull(mManager.getMatchingWifiConfig(scanResult));
    }

    /**
     * Verify that an empty list will be returned when retrieving OSU providers for an AP with
     * null scan result.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingOsuProvidersForNullScanResult() throws Exception {
        assertTrue(mManager.getMatchingOsuProviders(null).isEmpty());
    }

    /**
     * Verify that an empty list will be returned when retrieving OSU providers for an AP with
     * invalid BSSID.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingOsuProvidersForInvalidBSSID() throws Exception {
        ScanResult scanResult = createTestScanResult();
        scanResult.BSSID = "asdfdasfas";
        assertTrue(mManager.getMatchingOsuProviders(scanResult).isEmpty());
    }

    /**
     * Verify that an empty list will be returned when retrieving OSU providers for a
     * non-Passpoint AP.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingOsuProvidersForNonPasspointAP() throws Exception {
        ScanResult scanResult = createTestScanResult();
        scanResult.flags = 0;
        assertTrue(mManager.getMatchingOsuProviders(scanResult).isEmpty());
    }

    /**
     * Verify that an empty list will be returned when no match is found from the ANQP cache.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingOsuProviderWithNoMatch() throws Exception {
        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(null);
        assertTrue(mManager.getMatchingOsuProviders(createTestScanResult()).isEmpty());
    }

    /**
     * Verify that an expected provider list will be returned when a match is found from
     * the ANQP cache.
     *
     * @throws Exception
     */
    @Test
    public void getMatchingOsuProvidersWithMatch() throws Exception {
        // Test data.
        WifiSsid osuSsid = WifiSsid.createFromAsciiEncoded("Test SSID");
        String friendlyName = "Test Provider";
        String serviceDescription = "Dummy Service";
        Uri serverUri = Uri.parse("https://test.com");
        String nai = "access.test.com";
        List<Integer> methodList = Arrays.asList(1);
        List<I18Name> friendlyNames = Arrays.asList(
                new I18Name(Locale.ENGLISH.getLanguage(), Locale.ENGLISH, friendlyName));
        List<I18Name> serviceDescriptions = Arrays.asList(
                new I18Name(Locale.ENGLISH.getLanguage(), Locale.ENGLISH, serviceDescription));

        // Setup OSU providers ANQP element.
        List<OsuProviderInfo> providerInfoList = new ArrayList<>();
        providerInfoList.add(new OsuProviderInfo(
                friendlyNames, serverUri, methodList, null, nai, serviceDescriptions));
        Map<ANQPElementType, ANQPElement> anqpElementMap = new HashMap<>();
        anqpElementMap.put(ANQPElementType.HSOSUProviders,
                new HSOsuProvidersElement(osuSsid, providerInfoList));
        ANQPData entry = new ANQPData(mClock, anqpElementMap);

        // Setup expectation.
        OsuProvider provider = new OsuProvider(
                osuSsid, friendlyName, serviceDescription, serverUri, nai, methodList, null);
        List<OsuProvider> expectedList = new ArrayList<>();
        expectedList.add(provider);

        when(mAnqpCache.getEntry(TEST_ANQP_KEY)).thenReturn(entry);
        assertEquals(expectedList, mManager.getMatchingOsuProviders(createTestScanResult()));
    }

    /**
     * Verify that the provider list maintained by the PasspointManager after the list is updated
     * in the data source.
     *
     * @throws Exception
     */
    @Test
    public void verifyProvidersAfterDataSourceUpdate() throws Exception {
        // Update the provider list in the data source.
        PasspointConfiguration config = createTestConfigWithUserCredential();
        PasspointProvider provider = createMockProvider(config);
        List<PasspointProvider> providers = new ArrayList<>();
        providers.add(provider);
        mDataSource.setProviders(providers);

        // Verify the providers maintained by PasspointManager.
        assertEquals(1, mManager.getProviderConfigs().size());
        assertEquals(config, mManager.getProviderConfigs().get(0));
    }

    /**
     * Verify that the provider index used by PasspointManager is updated after it is updated in
     * the data source.
     *
     * @throws Exception
     */
    @Test
    public void verifyProviderIndexAfterDataSourceUpdate() throws Exception {
        long providerIndex = 9;
        mDataSource.setProviderIndex(providerIndex);
        assertEquals(providerIndex, mDataSource.getProviderIndex());

        // Add a provider.
        PasspointConfiguration config = createTestConfigWithUserCredential();
        PasspointProvider provider = createMockProvider(config);
        // Verify the provider ID used to create the new provider.
        when(mObjectFactory.makePasspointProvider(eq(config), eq(mWifiKeyStore),
                eq(mSimAccessor), eq(providerIndex), eq(TEST_CREATOR_UID))).thenReturn(provider);
        assertTrue(mManager.addOrUpdateProvider(config, TEST_CREATOR_UID));
        verifyInstalledConfig(config);
        verify(mWifiConfigManager).saveToStore(true);
        reset(mWifiConfigManager);
    }

    /**
     * Verify that a PasspointProvider with expected PasspointConfiguration will be installed when
     * adding a legacy Passpoint configuration containing a valid user credential.
     *
     * @throws Exception
     */
    @Test
    public void addLegacyPasspointConfigWithUserCredential() throws Exception {
        // Test data.
        String fqdn = "test.com";
        String friendlyName = "Friendly Name";
        long[] rcOIs = new long[] {0x1234L, 0x2345L};
        String realm = "realm.com";
        String username = "username";
        String password = "password";
        byte[] base64EncodedPw =
                Base64.encode(password.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        String encodedPasswordStr = new String(base64EncodedPw, StandardCharsets.UTF_8);
        String caCertificateAlias = "CaCert";

        // Setup WifiConfiguration for legacy Passpoint configuraiton.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = fqdn;
        wifiConfig.providerFriendlyName = friendlyName;
        wifiConfig.roamingConsortiumIds = rcOIs;
        wifiConfig.enterpriseConfig.setIdentity(username);
        wifiConfig.enterpriseConfig.setPassword(password);
        wifiConfig.enterpriseConfig.setRealm(realm);
        wifiConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TTLS);
        wifiConfig.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.PAP);
        wifiConfig.enterpriseConfig.setCaCertificateAlias(caCertificateAlias);

        // Setup expected {@link PasspointConfiguration}
        PasspointConfiguration passpointConfig = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        homeSp.setFriendlyName(friendlyName);
        homeSp.setRoamingConsortiumOis(rcOIs);
        passpointConfig.setHomeSp(homeSp);
        Credential credential = new Credential();
        Credential.UserCredential userCredential = new Credential.UserCredential();
        userCredential.setUsername(username);
        userCredential.setPassword(encodedPasswordStr);
        userCredential.setEapType(EAPConstants.EAP_TTLS);
        userCredential.setNonEapInnerMethod("PAP");
        credential.setUserCredential(userCredential);
        credential.setRealm(realm);
        passpointConfig.setCredential(credential);

        assertTrue(PasspointManager.addLegacyPasspointConfig(wifiConfig));
        verifyInstalledConfig(passpointConfig);
    }

    /**
     * Verify that adding a legacy Passpoint configuration containing user credential will
     * fail when client certificate is not provided.
     *
     * @throws Exception
     */
    @Test
    public void addLegacyPasspointConfigWithUserCredentialWithoutCaCert() throws Exception {
        // Test data.
        String fqdn = "test.com";
        String friendlyName = "Friendly Name";
        long[] rcOIs = new long[] {0x1234L, 0x2345L};
        String realm = "realm.com";
        String username = "username";
        String password = "password";
        byte[] base64EncodedPw =
                Base64.encode(password.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        String encodedPasswordStr = new String(base64EncodedPw, StandardCharsets.UTF_8);

        // Setup WifiConfiguration for legacy Passpoint configuraiton.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = fqdn;
        wifiConfig.providerFriendlyName = friendlyName;
        wifiConfig.roamingConsortiumIds = rcOIs;
        wifiConfig.enterpriseConfig.setIdentity(username);
        wifiConfig.enterpriseConfig.setPassword(password);
        wifiConfig.enterpriseConfig.setRealm(realm);
        wifiConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TTLS);
        wifiConfig.enterpriseConfig.setPhase2Method(WifiEnterpriseConfig.Phase2.PAP);

        assertFalse(PasspointManager.addLegacyPasspointConfig(wifiConfig));
    }

    /**
     * Verify that a PasspointProvider with expected PasspointConfiguration will be installed when
     * adding a legacy Passpoint configuration containing a valid SIM credential.
     *
     * @throws Exception
     */
    @Test
    public void addLegacyPasspointConfigWithSimCredential() throws Exception {
        // Test data.
        String fqdn = "test.com";
        String friendlyName = "Friendly Name";
        long[] rcOIs = new long[] {0x1234L, 0x2345L};
        String realm = "realm.com";
        String imsi = "1234";

        // Setup WifiConfiguration for legacy Passpoint configuraiton.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = fqdn;
        wifiConfig.providerFriendlyName = friendlyName;
        wifiConfig.roamingConsortiumIds = rcOIs;
        wifiConfig.enterpriseConfig.setRealm(realm);
        wifiConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.SIM);
        wifiConfig.enterpriseConfig.setPlmn(imsi);

        // Setup expected {@link PasspointConfiguration}
        PasspointConfiguration passpointConfig = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        homeSp.setFriendlyName(friendlyName);
        homeSp.setRoamingConsortiumOis(rcOIs);
        passpointConfig.setHomeSp(homeSp);
        Credential credential = new Credential();
        Credential.SimCredential simCredential = new Credential.SimCredential();
        simCredential.setEapType(EAPConstants.EAP_SIM);
        simCredential.setImsi(imsi);
        credential.setSimCredential(simCredential);
        credential.setRealm(realm);
        passpointConfig.setCredential(credential);

        assertTrue(PasspointManager.addLegacyPasspointConfig(wifiConfig));
        verifyInstalledConfig(passpointConfig);
    }

    /**
     * Verify that a PasspointProvider with expected PasspointConfiguration will be installed when
     * adding a legacy Passpoint configuration containing a valid certificate credential.
     *
     * @throws Exception
     */
    @Test
    public void addLegacyPasspointConfigWithCertCredential() throws Exception {
        // Test data.
        String fqdn = "test.com";
        String friendlyName = "Friendly Name";
        long[] rcOIs = new long[] {0x1234L, 0x2345L};
        String realm = "realm.com";
        String caCertificateAlias = "CaCert";
        String clientCertificateAlias = "ClientCert";

        // Setup WifiConfiguration for legacy Passpoint configuraiton.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = fqdn;
        wifiConfig.providerFriendlyName = friendlyName;
        wifiConfig.roamingConsortiumIds = rcOIs;
        wifiConfig.enterpriseConfig.setRealm(realm);
        wifiConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        wifiConfig.enterpriseConfig.setCaCertificateAlias(caCertificateAlias);
        wifiConfig.enterpriseConfig.setClientCertificateAlias(clientCertificateAlias);

        // Setup expected {@link PasspointConfiguration}
        PasspointConfiguration passpointConfig = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(fqdn);
        homeSp.setFriendlyName(friendlyName);
        homeSp.setRoamingConsortiumOis(rcOIs);
        passpointConfig.setHomeSp(homeSp);
        Credential credential = new Credential();
        Credential.CertificateCredential certCredential = new Credential.CertificateCredential();
        certCredential.setCertType(Credential.CertificateCredential.CERT_TYPE_X509V3);
        credential.setCertCredential(certCredential);
        credential.setRealm(realm);
        passpointConfig.setCredential(credential);

        assertTrue(PasspointManager.addLegacyPasspointConfig(wifiConfig));
        verifyInstalledConfig(passpointConfig);
    }

    /**
     * Verify that adding a legacy Passpoint configuration containing certificate credential will
     * fail when CA certificate is not provided.
     *
     * @throws Exception
     */
    @Test
    public void addLegacyPasspointConfigWithCertCredentialWithoutCaCert() throws Exception {
        // Test data.
        String fqdn = "test.com";
        String friendlyName = "Friendly Name";
        long[] rcOIs = new long[] {0x1234L, 0x2345L};
        String realm = "realm.com";
        String clientCertificateAlias = "ClientCert";

        // Setup WifiConfiguration for legacy Passpoint configuraiton.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = fqdn;
        wifiConfig.providerFriendlyName = friendlyName;
        wifiConfig.roamingConsortiumIds = rcOIs;
        wifiConfig.enterpriseConfig.setRealm(realm);
        wifiConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        wifiConfig.enterpriseConfig.setClientCertificateAlias(clientCertificateAlias);

        assertFalse(PasspointManager.addLegacyPasspointConfig(wifiConfig));
    }

    /**
     * Verify that adding a legacy Passpoint configuration containing certificate credential will
     * fail when client certificate is not provided.
     *
     * @throws Exception
     */
    @Test
    public void addLegacyPasspointConfigWithCertCredentialWithoutClientCert() throws Exception {
        // Test data.
        String fqdn = "test.com";
        String friendlyName = "Friendly Name";
        long[] rcOIs = new long[] {0x1234L, 0x2345L};
        String realm = "realm.com";
        String caCertificateAlias = "CaCert";

        // Setup WifiConfiguration for legacy Passpoint configuraiton.
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.FQDN = fqdn;
        wifiConfig.providerFriendlyName = friendlyName;
        wifiConfig.roamingConsortiumIds = rcOIs;
        wifiConfig.enterpriseConfig.setRealm(realm);
        wifiConfig.enterpriseConfig.setEapMethod(WifiEnterpriseConfig.Eap.TLS);
        wifiConfig.enterpriseConfig.setCaCertificateAlias(caCertificateAlias);

        assertFalse(PasspointManager.addLegacyPasspointConfig(wifiConfig));
    }

    /**
     * Verify that the provider's "hasEverConnected" flag will be set to true and the associated
     * metric is updated after the provider was used to successfully connect to a Passpoint
     * network for the first time.
     *
     * @throws Exception
     */
    @Test
    public void providerNetworkConnectedFirstTime() throws Exception {
        PasspointProvider provider = addTestProvider();
        when(provider.getHasEverConnected()).thenReturn(false);
        mManager.onPasspointNetworkConnected(TEST_FQDN);
        verify(provider).setHasEverConnected(eq(true));
    }

    /**
     * Verify that the provider's "hasEverConnected" flag the associated metric is not updated
     * after the provider was used to successfully connect to a Passpoint network for non-first
     * time.
     *
     * @throws Exception
     */
    @Test
    public void providerNetworkConnectedNotFirstTime() throws Exception {
        PasspointProvider provider = addTestProvider();
        when(provider.getHasEverConnected()).thenReturn(true);
        mManager.onPasspointNetworkConnected(TEST_FQDN);
        verify(provider, never()).setHasEverConnected(anyBoolean());
    }

    /**
     * Verify that the expected Passpoint metrics are updated when
     * {@link PasspointManager#updateMetrics} is invoked.
     *
     * @throws Exception
     */
    @Test
    public void updateMetrics() throws Exception {
        PasspointProvider provider = addTestProvider();

        // Provider have not provided a successful network connection.
        int expectedInstalledProviders = 1;
        int expectedConnectedProviders = 0;
        when(provider.getHasEverConnected()).thenReturn(false);
        mManager.updateMetrics();
        verify(mWifiMetrics).updateSavedPasspointProfiles(
                eq(expectedInstalledProviders), eq(expectedConnectedProviders));
        reset(provider);
        reset(mWifiMetrics);

        // Provider have provided a successful network connection.
        expectedConnectedProviders = 1;
        when(provider.getHasEverConnected()).thenReturn(true);
        mManager.updateMetrics();
        verify(mWifiMetrics).updateSavedPasspointProfiles(
                eq(expectedInstalledProviders), eq(expectedConnectedProviders));
    }
}
