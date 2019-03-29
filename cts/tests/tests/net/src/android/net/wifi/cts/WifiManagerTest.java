/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.net.wifi.cts;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.Status;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.TxPacketCountListener;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.Credential;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.WifiConfigCreator;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WifiManagerTest extends AndroidTestCase {
    private static class MySync {
        int expectedState = STATE_NULL;
    }

    private WifiManager mWifiManager;
    private WifiLock mWifiLock;
    private static MySync mMySync;
    private List<ScanResult> mScanResults = null;
    private NetworkInfo mNetworkInfo;
    private Object mLOHSLock = new Object();

    // Please refer to WifiManager
    private static final int MIN_RSSI = -100;
    private static final int MAX_RSSI = -55;

    private static final int STATE_NULL = 0;
    private static final int STATE_WIFI_CHANGING = 1;
    private static final int STATE_WIFI_ENABLED = 2;
    private static final int STATE_WIFI_DISABLED = 3;
    private static final int STATE_SCANNING = 4;
    private static final int STATE_SCAN_RESULTS_AVAILABLE = 5;

    private static final String TAG = "WifiManagerTest";
    private static final String SSID1 = "\"WifiManagerTest\"";
    private static final String SSID2 = "\"WifiManagerTestModified\"";
    private static final String PROXY_TEST_SSID = "SomeProxyAp";
    private static final String ADD_NETWORK_EXCEPTION_SUBSTR = "addNetwork";
    // A full single scan duration is about 6-7 seconds if country code is set
    // to US. If country code is set to world mode (00), we would expect a scan
    // duration of roughly 8 seconds. So we set scan timeout as 9 seconds here.
    private static final int SCAN_TIMEOUT_MSEC = 9000;
    private static final int TIMEOUT_MSEC = 6000;
    private static final int WAIT_MSEC = 60;
    private static final int DURATION = 10000;
    private static final int WIFI_SCAN_TEST_INTERVAL_MILLIS = 60 * 1000;
    private static final int WIFI_SCAN_TEST_CACHE_DELAY_MILLIS = 3 * 60 * 1000;
    private static final int WIFI_SCAN_TEST_ITERATIONS = 5;

    private static final String TEST_PAC_URL = "http://www.example.com/proxy.pac";

    private IntentFilter mIntentFilter;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                synchronized (mMySync) {
                    if (mWifiManager.getScanResults() != null) {
                        mScanResults = mWifiManager.getScanResults();
                        mMySync.expectedState = STATE_SCAN_RESULTS_AVAILABLE;
                        mScanResults = mWifiManager.getScanResults();
                        mMySync.notifyAll();
                    }
                }
            } else if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int newState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                        WifiManager.WIFI_STATE_UNKNOWN);
                synchronized (mMySync) {
                    if (newState == WifiManager.WIFI_STATE_ENABLED) {
                        Log.d(TAG, "*** New WiFi state is ENABLED ***");
                        mMySync.expectedState = STATE_WIFI_ENABLED;
                        mMySync.notifyAll();
                    } else if (newState == WifiManager.WIFI_STATE_DISABLED) {
                        Log.d(TAG, "*** New WiFi state is DISABLED ***");
                        mMySync.expectedState = STATE_WIFI_DISABLED;
                        mMySync.notifyAll();
                    }
                }
            } else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                synchronized (mMySync) {
                    mNetworkInfo =
                            (NetworkInfo) intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                    if (mNetworkInfo.getState() == NetworkInfo.State.CONNECTED)
                        mMySync.notifyAll();
                }
            }
        }
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        mMySync = new MySync();
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mIntentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        mIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.NETWORK_IDS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.ACTION_PICK_WIFI_NETWORK);

        mContext.registerReceiver(mReceiver, mIntentFilter);
        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        assertNotNull(mWifiManager);
        mWifiLock = mWifiManager.createWifiLock(TAG);
        mWifiLock.acquire();
        if (!mWifiManager.isWifiEnabled())
            setWifiEnabled(true);
        Thread.sleep(DURATION);
        assertTrue(mWifiManager.isWifiEnabled());
        synchronized (mMySync) {
            mMySync.expectedState = STATE_NULL;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            super.tearDown();
            return;
        }
        if (!mWifiManager.isWifiEnabled())
            setWifiEnabled(true);
        mWifiLock.release();
        mContext.unregisterReceiver(mReceiver);
        Thread.sleep(DURATION);
        super.tearDown();
    }

    private void setWifiEnabled(boolean enable) throws Exception {
        synchronized (mMySync) {
            if (mWifiManager.isWifiEnabled() != enable) {
                // the new state is different, we expect it to change
                mMySync.expectedState = STATE_WIFI_CHANGING;
            } else {
                mMySync.expectedState = (enable ? STATE_WIFI_ENABLED : STATE_WIFI_DISABLED);
            }
            // now trigger the change
            assertTrue(mWifiManager.setWifiEnabled(enable));
            waitForExpectedWifiState(enable);
        }
    }

    private void waitForExpectedWifiState(boolean enabled) throws InterruptedException {
        synchronized (mMySync) {
            long timeout = System.currentTimeMillis() + TIMEOUT_MSEC;
            int expected = (enabled ? STATE_WIFI_ENABLED : STATE_WIFI_DISABLED);
            while (System.currentTimeMillis() < timeout
                    && mMySync.expectedState != expected) {
                mMySync.wait(WAIT_MSEC);
            }
        }
    }

    private void startScan() throws Exception {
        synchronized (mMySync) {
            mMySync.expectedState = STATE_SCANNING;
            mScanResults = null;
            assertTrue(mWifiManager.startScan());
            long timeout = System.currentTimeMillis() + SCAN_TIMEOUT_MSEC;
            while (System.currentTimeMillis() < timeout && mMySync.expectedState == STATE_SCANNING)
                mMySync.wait(WAIT_MSEC);
        }
    }

    private void connectWifi() throws Exception {
        synchronized (mMySync) {
            if (mNetworkInfo.getState() == NetworkInfo.State.CONNECTED) return;
            assertTrue(mWifiManager.reconnect());
            long timeout = System.currentTimeMillis() + TIMEOUT_MSEC;
            while (System.currentTimeMillis() < timeout
                    && mNetworkInfo.getState() != NetworkInfo.State.CONNECTED)
                mMySync.wait(WAIT_MSEC);
            assertTrue(mNetworkInfo.getState() == NetworkInfo.State.CONNECTED);
        }
    }

    private boolean existSSID(String ssid) {
        for (final WifiConfiguration w : mWifiManager.getConfiguredNetworks()) {
            if (w.SSID.equals(ssid))
                return true;
        }
        return false;
    }

    private int findConfiguredNetworks(String SSID, List<WifiConfiguration> networks) {
        for (final WifiConfiguration w : networks) {
            if (w.SSID.equals(SSID))
                return networks.indexOf(w);
        }
        return -1;
    }

    /**
     * test point of wifiManager actions:
     * 1.reconnect
     * 2.reassociate
     * 3.disconnect
     * 4.pingSupplicant
     * 5.satrtScan
     */
    public void testWifiManagerActions() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        assertTrue(mWifiManager.reconnect());
        assertTrue(mWifiManager.reassociate());
        assertTrue(mWifiManager.disconnect());
        setWifiEnabled(false);
        startScan();
        Thread.sleep(DURATION);
        if (mWifiManager.isScanAlwaysAvailable()) {
            // Make sure at least one AP is found.
            assertNotNull("mScanResult should not be null!", mScanResults);
            assertFalse("empty scan results!", mScanResults.isEmpty());
        } else {
            // Make sure no scan results are available.
            assertNull("mScanResult should be null!", mScanResults);
        }
        final String TAG = "Test";
        assertNotNull(mWifiManager.createWifiLock(TAG));
        assertNotNull(mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG));
    }

    /**
     * test point of wifiManager properties:
     * 1.enable properties
     * 2.DhcpInfo properties
     * 3.wifi state
     * 4.ConnectionInfo
     */
    public void testWifiManagerProperties() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        setWifiEnabled(true);
        assertTrue(mWifiManager.isWifiEnabled());
        assertNotNull(mWifiManager.getDhcpInfo());
        assertEquals(WifiManager.WIFI_STATE_ENABLED, mWifiManager.getWifiState());
        mWifiManager.getConnectionInfo();
        setWifiEnabled(false);
        assertFalse(mWifiManager.isWifiEnabled());
    }

    /**
     * Test WiFi scan timestamp - fails when WiFi scan timestamps are inconsistent with
     * {@link SystemClock#elapsedRealtime()} on device.<p>
     * To run this test in cts-tradefed:
     * run cts --class android.net.wifi.cts.WifiManagerTest --method testWifiScanTimestamp
     */
    public void testWifiScanTimestamp() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            Log.d(TAG, "Skipping test as WiFi is not supported");
            return;
        }
        if (!hasLocationFeature()) {
            Log.d(TAG, "Skipping test as location is not supported");
            return;
        }
        if (!isLocationEnabled()) {
            fail("Please enable location for this test - since Marshmallow WiFi scan results are"
                    + " empty when location is disabled!");
        }
        if (!mWifiManager.isWifiEnabled()) {
            setWifiEnabled(true);
        }
        // Scan multiple times to make sure scan timestamps increase with device timestamp.
        for (int i = 0; i < WIFI_SCAN_TEST_ITERATIONS; ++i) {
            startScan();
            // Make sure at least one AP is found.
            assertTrue("mScanResult should not be null. This may be due to a scan timeout",
                       mScanResults != null);
            assertFalse("empty scan results!", mScanResults.isEmpty());
            long nowMillis = SystemClock.elapsedRealtime();
            // Keep track of how many APs are fresh in one scan.
            int numFreshAps = 0;
            for (ScanResult result : mScanResults) {
                long scanTimeMillis = TimeUnit.MICROSECONDS.toMillis(result.timestamp);
                if (Math.abs(nowMillis - scanTimeMillis)  < WIFI_SCAN_TEST_CACHE_DELAY_MILLIS) {
                    numFreshAps++;
                }
            }
            // At least half of the APs in the scan should be fresh.
            int numTotalAps = mScanResults.size();
            String msg = "Stale AP count: " + (numTotalAps - numFreshAps) + ", fresh AP count: "
                    + numFreshAps;
            assertTrue(msg, numFreshAps * 2 >= mScanResults.size());
            if (i < WIFI_SCAN_TEST_ITERATIONS - 1) {
                // Wait before running next iteration.
                Thread.sleep(WIFI_SCAN_TEST_INTERVAL_MILLIS);
            }
        }
    }

    // Return true if location is enabled.
    private boolean isLocationEnabled() {
        return Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) !=
                Settings.Secure.LOCATION_MODE_OFF;
    }

    // Returns true if the device has location feature.
    private boolean hasLocationFeature() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION);
    }

    /**
     * test point of wifiManager NetWork:
     * 1.add NetWork
     * 2.update NetWork
     * 3.remove NetWork
     * 4.enable NetWork
     * 5.disable NetWork
     * 6.configured Networks
     * 7.save configure;
     */
    public void testWifiManagerNetWork() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }

        // store the list of enabled networks, so they can be re-enabled after test completes
        Set<String> enabledSsids = getEnabledNetworks(mWifiManager.getConfiguredNetworks());
        try {
            WifiConfiguration wifiConfiguration;
            // add a WifiConfig
            final int notExist = -1;
            List<WifiConfiguration> wifiConfiguredNetworks = mWifiManager.getConfiguredNetworks();
            int pos = findConfiguredNetworks(SSID1, wifiConfiguredNetworks);
            if (notExist != pos) {
                wifiConfiguration = wifiConfiguredNetworks.get(pos);
                mWifiManager.removeNetwork(wifiConfiguration.networkId);
            }
            pos = findConfiguredNetworks(SSID1, wifiConfiguredNetworks);
            assertEquals(notExist, pos);
            final int size = wifiConfiguredNetworks.size();

            wifiConfiguration = new WifiConfiguration();
            wifiConfiguration.SSID = SSID1;
            wifiConfiguration.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            int netId = mWifiManager.addNetwork(wifiConfiguration);
            assertTrue(existSSID(SSID1));

            wifiConfiguredNetworks = mWifiManager.getConfiguredNetworks();
            assertEquals(size + 1, wifiConfiguredNetworks.size());
            pos = findConfiguredNetworks(SSID1, wifiConfiguredNetworks);
            assertTrue(notExist != pos);

            // Enable & disable network
            boolean disableOthers = true;
            assertTrue(mWifiManager.enableNetwork(netId, disableOthers));
            wifiConfiguration = mWifiManager.getConfiguredNetworks().get(pos);
            assertEquals(Status.ENABLED, wifiConfiguration.status);

            assertTrue(mWifiManager.disableNetwork(netId));
            wifiConfiguration = mWifiManager.getConfiguredNetworks().get(pos);
            assertEquals(Status.DISABLED, wifiConfiguration.status);

            // Update a WifiConfig
            wifiConfiguration = wifiConfiguredNetworks.get(pos);
            wifiConfiguration.SSID = SSID2;
            netId = mWifiManager.updateNetwork(wifiConfiguration);
            assertFalse(existSSID(SSID1));
            assertTrue(existSSID(SSID2));

            // Remove a WifiConfig
            assertTrue(mWifiManager.removeNetwork(netId));
            assertFalse(mWifiManager.removeNetwork(notExist));
            assertFalse(existSSID(SSID1));
            assertFalse(existSSID(SSID2));

            assertTrue(mWifiManager.saveConfiguration());
        } finally {
            reEnableNetworks(enabledSsids, mWifiManager.getConfiguredNetworks());
            mWifiManager.saveConfiguration();
        }
    }

    /**
     * Verifies that addNetwork() fails for WifiConfigurations containing a non-null http proxy when
     * the caller doesn't have OVERRIDE_WIFI_CONFIG permission, DeviceOwner or ProfileOwner device
     * management policies
     */
    public void testSetHttpProxy_PermissionFail() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        WifiConfigCreator configCreator = new WifiConfigCreator(getContext());
        boolean exceptionThrown = false;
        try {
            configCreator.addHttpProxyNetworkVerifyAndRemove(
                    PROXY_TEST_SSID, TEST_PAC_URL);
        } catch (IllegalStateException e) {
            // addHttpProxyNetworkVerifyAndRemove throws three IllegalStateException,
            // expect it to throw for the addNetwork operation
            if (e.getMessage().contains(ADD_NETWORK_EXCEPTION_SUBSTR)) {
                exceptionThrown = true;
            }
        }
        assertTrue(exceptionThrown);
    }

    private Set<String> getEnabledNetworks(List<WifiConfiguration> configuredNetworks) {
        Set<String> ssids = new HashSet<String>();
        for (WifiConfiguration wifiConfig : configuredNetworks) {
            if (Status.ENABLED == wifiConfig.status || Status.CURRENT == wifiConfig.status) {
                ssids.add(wifiConfig.SSID);
                Log.i(TAG, String.format("remembering enabled network %s", wifiConfig.SSID));
            }
        }
        return ssids;
    }

    private void reEnableNetworks(Set<String> enabledSsids,
            List<WifiConfiguration> configuredNetworks) {
        for (WifiConfiguration wifiConfig : configuredNetworks) {
            if (enabledSsids.contains(wifiConfig.SSID)) {
                mWifiManager.enableNetwork(wifiConfig.networkId, false);
                Log.i(TAG, String.format("re-enabling network %s", wifiConfig.SSID));
            }
        }
    }

    public void testSignal() {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        final int numLevels = 9;
        int expectLevel = 0;
        assertEquals(expectLevel, WifiManager.calculateSignalLevel(MIN_RSSI, numLevels));
        assertEquals(numLevels - 1, WifiManager.calculateSignalLevel(MAX_RSSI, numLevels));
        expectLevel = 4;
        assertEquals(expectLevel, WifiManager.calculateSignalLevel((MIN_RSSI + MAX_RSSI) / 2,
                numLevels));
        int rssiA = 4;
        int rssiB = 5;
        assertTrue(WifiManager.compareSignalLevel(rssiA, rssiB) < 0);
        rssiB = 4;
        assertTrue(WifiManager.compareSignalLevel(rssiA, rssiB) == 0);
        rssiA = 5;
        rssiB = 4;
        assertTrue(WifiManager.compareSignalLevel(rssiA, rssiB) > 0);
    }

    private int getTxPacketCount() throws Exception {
        final AtomicInteger ret = new AtomicInteger(-1);

        mWifiManager.getTxPacketCount(new TxPacketCountListener() {
            @Override
            public void onSuccess(int count) {
                ret.set(count);
            }
            @Override
            public void onFailure(int reason) {
                ret.set(0);
            }
        });

        long timeout = System.currentTimeMillis() + TIMEOUT_MSEC;
        while (ret.get() < 0 && System.currentTimeMillis() < timeout)
            Thread.sleep(WAIT_MSEC);
        assertTrue(ret.get() >= 0);
        return ret.get();
    }

    /**
     * The new WiFi watchdog requires kernel/driver to export some packet loss
     * counters. This CTS tests whether those counters are correctly exported.
     * To pass this CTS test, a connected WiFi link is required.
     */
    public void testWifiWatchdog() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        // Make sure WiFi is enabled
        if (!mWifiManager.isWifiEnabled()) {
            setWifiEnabled(true);
            Thread.sleep(DURATION);
        }
        assertTrue(mWifiManager.isWifiEnabled());

        // give the test a chance to autoconnect
        Thread.sleep(DURATION);
        if (mNetworkInfo.getState() != NetworkInfo.State.CONNECTED) {
            // this test requires a connectable network be configured
            fail("This test requires a wifi network connection.");
        }

        // This will generate a distinct stack trace if the initial connection fails.
        connectWifi();

        int i = 0;
        for (; i < 15; i++) {
            // Wait for a WiFi connection
            connectWifi();

            // Read TX packet counter
            int txcount1 = getTxPacketCount();

            // Do some network operations
            HttpURLConnection connection = null;
            try {
                URL url = new URL("http://www.google.com/");
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(TIMEOUT_MSEC);
                connection.setReadTimeout(TIMEOUT_MSEC);
                connection.setUseCaches(false);
                connection.getInputStream();
            } catch (Exception e) {
                // ignore
            } finally {
                if (connection != null) connection.disconnect();
            }

            // Read TX packet counter again and make sure it increases
            int txcount2 = getTxPacketCount();

            if (txcount2 > txcount1) {
                break;
            } else {
                Thread.sleep(DURATION);
            }
        }
        assertTrue(i < 15);
    }

    /**
     * Verify Passpoint configuration management APIs (add, remove, get) for a Passpoint
     * configuration with an user credential.
     *
     * @throws Exception
     */
    public void testAddPasspointConfigWithUserCredential() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        testAddPasspointConfig(generatePasspointConfig(generateUserCredential()));
    }

    /**
     * Verify Passpoint configuration management APIs (add, remove, get) for a Passpoint
     * configuration with a certificate credential.
     *
     * @throws Exception
     */
    public void testAddPasspointConfigWithCertCredential() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        testAddPasspointConfig(generatePasspointConfig(generateCertCredential()));
    }

    /**
     * Verify Passpoint configuration management APIs (add, remove, get) for a Passpoint
     * configuration with a SIm credential.
     *
     * @throws Exception
     */
    public void testAddPasspointConfigWithSimCredential() throws Exception {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        testAddPasspointConfig(generatePasspointConfig(generateSimCredential()));
    }

    /**
     * Helper function for generating a {@link PasspointConfiguration} for testing.
     *
     * @return {@link PasspointConfiguration}
     */
    private PasspointConfiguration generatePasspointConfig(Credential credential) {
        PasspointConfiguration config = new PasspointConfiguration();
        config.setCredential(credential);

        // Setup HomeSp.
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn("Test.com");
        homeSp.setFriendlyName("Test Provider");
        homeSp.setRoamingConsortiumOis(new long[] {0x11223344});
        config.setHomeSp(homeSp);

        return config;
    }

    /**
     * Helper function for generating an user credential for testing.
     *
     * @return {@link Credential}
     */
    private Credential generateUserCredential() {
        Credential credential = new Credential();
        credential.setRealm("test.net");
        Credential.UserCredential userCred = new Credential.UserCredential();
        userCred.setEapType(21 /* EAP_TTLS */);
        userCred.setUsername("username");
        userCred.setPassword("password");
        userCred.setNonEapInnerMethod("PAP");
        credential.setUserCredential(userCred);
        credential.setCaCertificate(FakeKeys.CA_PUBLIC_CERT);
        return credential;
    }

    /**
     * Helper function for generating a certificate credential for testing.
     *
     * @return {@link Credential}
     */
    private Credential generateCertCredential() throws Exception {
        Credential credential = new Credential();
        credential.setRealm("test.net");
        Credential.CertificateCredential certCredential = new Credential.CertificateCredential();
        certCredential.setCertType("x509v3");
        certCredential.setCertSha256Fingerprint(
                MessageDigest.getInstance("SHA-256").digest(FakeKeys.CLIENT_CERT.getEncoded()));
        credential.setCertCredential(certCredential);
        credential.setCaCertificate(FakeKeys.CA_PUBLIC_CERT);
        credential.setClientCertificateChain(new X509Certificate[] {FakeKeys.CLIENT_CERT});
        credential.setClientPrivateKey(FakeKeys.RSA_KEY1);
        return credential;
    }

    /**
     * Helper function for generating a SIM credential for testing.
     *
     * @return {@link Credential}
     */
    private Credential generateSimCredential() throws Exception {
        Credential credential = new Credential();
        credential.setRealm("test.net");
        Credential.SimCredential simCredential = new Credential.SimCredential();
        simCredential.setImsi("1234*");
        simCredential.setEapType(18 /* EAP_SIM */);
        credential.setSimCredential(simCredential);
        return credential;
    }

    /**
     * Helper function verifying Passpoint configuration management APIs (add, remove, get) for
     * a given configuration.
     *
     * @param config The configuration to test with
     */
    private void testAddPasspointConfig(PasspointConfiguration config) throws Exception {
        try {
            mWifiManager.addOrUpdatePasspointConfiguration(config);

            // Certificates and keys will be set to null after it is installed to the KeyStore by
            // WifiManager.  Reset them in the expected config so that it can be used to compare
            // against the retrieved config.
            config.getCredential().setCaCertificate(null);
            config.getCredential().setClientCertificateChain(null);
            config.getCredential().setClientPrivateKey(null);

            // Retrieve the configuration and verify it.
            List<PasspointConfiguration> configList = mWifiManager.getPasspointConfigurations();
            assertEquals(1, configList.size());
            assertEquals(config, configList.get(0));

            // Remove the configuration and verify no installed configuration.
            mWifiManager.removePasspointConfiguration(config.getHomeSp().getFqdn());
            assertTrue(mWifiManager.getPasspointConfigurations().isEmpty());
        } catch (UnsupportedOperationException e) {
            // Passpoint build config |config_wifi_hotspot2_enabled| is disabled, so noop.
        }
    }

    public class TestLocalOnlyHotspotCallback extends WifiManager.LocalOnlyHotspotCallback {
        Object hotspotLock;
        WifiManager.LocalOnlyHotspotReservation reservation = null;
        boolean onStartedCalled = false;
        boolean onStoppedCalled = false;
        boolean onFailedCalled = false;
        int failureReason = -1;

        TestLocalOnlyHotspotCallback(Object lock) {
            hotspotLock = lock;
        }

        @Override
        public void onStarted(WifiManager.LocalOnlyHotspotReservation r) {
            synchronized (hotspotLock) {
                reservation = r;
                onStartedCalled = true;
                hotspotLock.notify();
            }
        }

        @Override
        public void onStopped() {
            synchronized (hotspotLock) {
                onStoppedCalled = true;
                hotspotLock.notify();
            }
        }

        @Override
        public void onFailed(int reason) {
            synchronized (hotspotLock) {
                onFailedCalled = true;
                failureReason = reason;
                hotspotLock.notify();
            }
        }
    }

    private TestLocalOnlyHotspotCallback startLocalOnlyHotspot() {
        // Location mode must be enabled for this test
        if (!isLocationEnabled()) {
            fail("Please enable location for this test");
        }

        TestLocalOnlyHotspotCallback callback = new TestLocalOnlyHotspotCallback(mLOHSLock);
        synchronized (mLOHSLock) {
            try {
                mWifiManager.startLocalOnlyHotspot(callback, null);
                // now wait for callback
                mLOHSLock.wait(DURATION);
            } catch (InterruptedException e) {
            }
            // check if we got the callback
            assertTrue(callback.onStartedCalled);
            assertNotNull(callback.reservation.getWifiConfiguration());
            assertFalse(callback.onFailedCalled);
            assertFalse(callback.onStoppedCalled);
        }
        return callback;
    }

    private void stopLocalOnlyHotspot(TestLocalOnlyHotspotCallback callback, boolean wifiEnabled) {
       synchronized (mMySync) {
           // we are expecting a new state
           mMySync.expectedState = STATE_WIFI_CHANGING;

           // now shut down LocalOnlyHotspot
           callback.reservation.close();

           try {
               waitForExpectedWifiState(wifiEnabled);
           } catch (InterruptedException e) {}
        }
    }

    /**
     * Verify that calls to startLocalOnlyHotspot succeed with proper permissions.
     *
     * Note: Location mode must be enabled for this test.
     */
    public void testStartLocalOnlyHotspotSuccess() {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        // check that softap mode is supported by the device
        if (!mWifiManager.isPortableHotspotSupported()) {
            return;
        }

        boolean wifiEnabled = mWifiManager.isWifiEnabled();

        TestLocalOnlyHotspotCallback callback = startLocalOnlyHotspot();

        // at this point, wifi should be off
        assertFalse(mWifiManager.isWifiEnabled());

        stopLocalOnlyHotspot(callback, wifiEnabled);
        assertEquals(wifiEnabled, mWifiManager.isWifiEnabled());
    }

    /**
     * Verify calls to setWifiEnabled from a non-settings app while softap mode is active do not
     * exit softap mode.
     *
     * This test uses the LocalOnlyHotspot API to enter softap mode.  This should also be true when
     * tethering is started.
     * Note: Location mode must be enabled for this test.
     */
    public void testSetWifiEnabledByAppDoesNotStopHotspot() {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        // check that softap mode is supported by the device
        if (!mWifiManager.isPortableHotspotSupported()) {
            return;
        }

        boolean wifiEnabled = mWifiManager.isWifiEnabled();

        TestLocalOnlyHotspotCallback callback = startLocalOnlyHotspot();
        // at this point, wifi should be off
        assertFalse(mWifiManager.isWifiEnabled());

        // now we should fail to turn on wifi
        assertFalse(mWifiManager.setWifiEnabled(true));

        stopLocalOnlyHotspot(callback, wifiEnabled);
        assertEquals(wifiEnabled, mWifiManager.isWifiEnabled());
    }

    /**
     * Verify that applications can only have one registered LocalOnlyHotspot request at a time.
     *
     * Note: Location mode must be enabled for this test.
     */
    public void testStartLocalOnlyHotspotSingleRequestByApps() {
        if (!WifiFeature.isWifiSupported(getContext())) {
            // skip the test if WiFi is not supported
            return;
        }
        // check that softap mode is supported by the device
        if (!mWifiManager.isPortableHotspotSupported()) {
            return;
        }

        boolean caughtException = false;

        boolean wifiEnabled = mWifiManager.isWifiEnabled();

        TestLocalOnlyHotspotCallback callback = startLocalOnlyHotspot();

        // at this point, wifi should be off
        assertFalse(mWifiManager.isWifiEnabled());

        // now make a second request - this should fail.
        TestLocalOnlyHotspotCallback callback2 = new TestLocalOnlyHotspotCallback(mLOHSLock);
        try {
            mWifiManager.startLocalOnlyHotspot(callback2, null);
        } catch (IllegalStateException e) {
            Log.d(TAG, "Caught the IllegalStateException we expected: called startLOHS twice");
            caughtException = true;
        }
        assertTrue(caughtException);

        stopLocalOnlyHotspot(callback, wifiEnabled);
        assertEquals(wifiEnabled, mWifiManager.isWifiEnabled());
    }
}
