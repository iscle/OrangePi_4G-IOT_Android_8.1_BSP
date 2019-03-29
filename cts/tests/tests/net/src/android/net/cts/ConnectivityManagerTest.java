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

package android.net.cts;

import static android.net.NetworkCapabilities.NET_CAPABILITY_IMS;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkConfig;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.Looper;
import android.os.SystemProperties;
import android.system.Os;
import android.system.OsConstants;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.internal.telephony.PhoneConstants;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConnectivityManagerTest extends AndroidTestCase {

    private static final String TAG = ConnectivityManagerTest.class.getSimpleName();

    private static final String FEATURE_ENABLE_HIPRI = "enableHIPRI";

    public static final int TYPE_MOBILE = ConnectivityManager.TYPE_MOBILE;
    public static final int TYPE_WIFI = ConnectivityManager.TYPE_WIFI;

    private static final int HOST_ADDRESS = 0x7f000001;// represent ip 127.0.0.1
    private static final String TEST_HOST = "connectivitycheck.gstatic.com";
    private static final int SOCKET_TIMEOUT_MS = 2000;
    private static final int SEND_BROADCAST_TIMEOUT = 30000;
    private static final int HTTP_PORT = 80;
    private static final String HTTP_REQUEST =
            "GET /generate_204 HTTP/1.0\r\n" +
            "Host: " + TEST_HOST + "\r\n" +
            "Connection: keep-alive\r\n\r\n";

    // Base path for IPv6 sysctls
    private static final String IPV6_SYSCTL_DIR = "/proc/sys/net/ipv6/conf";

    // Expected values for MIN|MAX_PLEN.
    private static final int IPV6_WIFI_ACCEPT_RA_RT_INFO_MIN_PLEN = 48;
    private static final int IPV6_WIFI_ACCEPT_RA_RT_INFO_MAX_PLEN = 64;

    // Expected values for RFC 7559 router soliciations.
    // Maximum number of router solicitations to send. -1 means no limit.
    private static final int IPV6_WIFI_ROUTER_SOLICITATIONS = -1;

    // Action sent to ConnectivityActionReceiver when a network callback is sent via PendingIntent.
    private static final String NETWORK_CALLBACK_ACTION =
            "ConnectivityManagerTest.NetworkCallbackAction";

    // Intent string to get the number of wifi CONNECTIVITY_ACTION callbacks the test app has seen
    public static final String GET_WIFI_CONNECTIVITY_ACTION_COUNT =
            "android.net.cts.appForApi23.getWifiConnectivityActionCount";

    // device could have only one interface: data, wifi.
    private static final int MIN_NUM_NETWORK_TYPES = 1;

    private Context mContext;
    private ConnectivityManager mCm;
    private WifiManager mWifiManager;
    private PackageManager mPackageManager;
    private final HashMap<Integer, NetworkConfig> mNetworks =
            new HashMap<Integer, NetworkConfig>();
    boolean mWifiConnectAttempted;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Looper.prepare();
        mContext = getContext();
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mPackageManager = mContext.getPackageManager();
        mWifiConnectAttempted = false;

        // Get com.android.internal.R.array.networkAttributes
        int resId = mContext.getResources().getIdentifier("networkAttributes", "array", "android");
        String[] naStrings = mContext.getResources().getStringArray(resId);
        //TODO: What is the "correct" way to determine if this is a wifi only device?
        boolean wifiOnly = SystemProperties.getBoolean("ro.radio.noril", false);
        for (String naString : naStrings) {
            try {
                NetworkConfig n = new NetworkConfig(naString);
                if (wifiOnly && ConnectivityManager.isNetworkTypeMobile(n.type)) {
                    continue;
                }
                mNetworks.put(n.type, n);
            } catch (Exception e) {}
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // Return WiFi to its original disabled state after tests that explicitly connect.
        if (mWifiConnectAttempted) {
            disconnectFromWifi(null);
        }
    }

    /**
     * Make sure WiFi is connected to an access point if it is not already. If
     * WiFi is enabled as a result of this function, it will be disabled
     * automatically in tearDown().
     */
    private Network ensureWifiConnected() {
        if (mWifiManager.isWifiEnabled()) {
            return getWifiNetwork();
        }
        mWifiConnectAttempted = true;
        return connectToWifi();
    }

    public void testIsNetworkTypeValid() {
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_WIFI));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_MMS));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_SUPL));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_DUN));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_HIPRI));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_WIMAX));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_BLUETOOTH));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_DUMMY));
        assertTrue(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.TYPE_ETHERNET));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_FOTA));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_IMS));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_CBS));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_WIFI_P2P));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.TYPE_MOBILE_IA));
        assertFalse(mCm.isNetworkTypeValid(-1));
        assertTrue(mCm.isNetworkTypeValid(0));
        assertTrue(mCm.isNetworkTypeValid(ConnectivityManager.MAX_NETWORK_TYPE));
        assertFalse(ConnectivityManager.isNetworkTypeValid(ConnectivityManager.MAX_NETWORK_TYPE+1));

        NetworkInfo[] ni = mCm.getAllNetworkInfo();

        for (NetworkInfo n: ni) {
            assertTrue(ConnectivityManager.isNetworkTypeValid(n.getType()));
        }

    }

    public void testSetNetworkPreference() {
        // getNetworkPreference() and setNetworkPreference() are both deprecated so they do
        // not preform any action.  Verify they are at least still callable.
        mCm.setNetworkPreference(mCm.getNetworkPreference());
    }

    public void testGetActiveNetworkInfo() {
        NetworkInfo ni = mCm.getActiveNetworkInfo();

        assertNotNull("You must have an active network connection to complete CTS", ni);
        assertTrue(ConnectivityManager.isNetworkTypeValid(ni.getType()));
        assertTrue(ni.getState() == State.CONNECTED);
    }

    public void testGetActiveNetwork() {
        Network network = mCm.getActiveNetwork();
        assertNotNull("You must have an active network connection to complete CTS", network);

        NetworkInfo ni = mCm.getNetworkInfo(network);
        assertNotNull("Network returned from getActiveNetwork was invalid", ni);

        // Similar to testGetActiveNetworkInfo above.
        assertTrue(ConnectivityManager.isNetworkTypeValid(ni.getType()));
        assertTrue(ni.getState() == State.CONNECTED);
    }

    public void testGetNetworkInfo() {
        for (int type = -1; type <= ConnectivityManager.MAX_NETWORK_TYPE+1; type++) {
            if (isSupported(type)) {
                NetworkInfo ni = mCm.getNetworkInfo(type);
                assertTrue("Info shouldn't be null for " + type, ni != null);
                State state = ni.getState();
                assertTrue("Bad state for " + type, State.UNKNOWN.ordinal() >= state.ordinal()
                           && state.ordinal() >= State.CONNECTING.ordinal());
                DetailedState ds = ni.getDetailedState();
                assertTrue("Bad detailed state for " + type,
                           DetailedState.FAILED.ordinal() >= ds.ordinal()
                           && ds.ordinal() >= DetailedState.IDLE.ordinal());
            } else {
                assertNull("Info should be null for " + type, mCm.getNetworkInfo(type));
            }
        }
    }

    public void testGetAllNetworkInfo() {
        NetworkInfo[] ni = mCm.getAllNetworkInfo();
        assertTrue(ni.length >= MIN_NUM_NETWORK_TYPES);
        for (int type = 0; type <= ConnectivityManager.MAX_NETWORK_TYPE; type++) {
            int desiredFoundCount = (isSupported(type) ? 1 : 0);
            int foundCount = 0;
            for (NetworkInfo i : ni) {
                if (i.getType() == type) foundCount++;
            }
            if (foundCount != desiredFoundCount) {
                Log.e(TAG, "failure in testGetAllNetworkInfo.  Dump of returned NetworkInfos:");
                for (NetworkInfo networkInfo : ni) Log.e(TAG, "  " + networkInfo);
            }
            assertTrue("Unexpected foundCount of " + foundCount + " for type " + type,
                    foundCount == desiredFoundCount);
        }
    }

    private void assertStartUsingNetworkFeatureUnsupported(int networkType, String feature) {
        try {
            mCm.startUsingNetworkFeature(networkType, feature);
            fail("startUsingNetworkFeature is no longer supported in the current API version");
        } catch (UnsupportedOperationException expected) {}
    }

    private void assertStopUsingNetworkFeatureUnsupported(int networkType, String feature) {
        try {
            mCm.startUsingNetworkFeature(networkType, feature);
            fail("stopUsingNetworkFeature is no longer supported in the current API version");
        } catch (UnsupportedOperationException expected) {}
    }

    private void assertRequestRouteToHostUnsupported(int networkType, int hostAddress) {
        try {
            mCm.requestRouteToHost(networkType, hostAddress);
            fail("requestRouteToHost is no longer supported in the current API version");
        } catch (UnsupportedOperationException expected) {}
    }

    public void testStartUsingNetworkFeature() {

        final String invalidateFeature = "invalidateFeature";
        final String mmsFeature = "enableMMS";
        final int failureCode = -1;
        final int wifiOnlyStartFailureCode = PhoneConstants.APN_REQUEST_FAILED;
        final int wifiOnlyStopFailureCode = -1;

        assertStartUsingNetworkFeatureUnsupported(TYPE_MOBILE, invalidateFeature);
        assertStopUsingNetworkFeatureUnsupported(TYPE_MOBILE, invalidateFeature);
        assertStartUsingNetworkFeatureUnsupported(TYPE_WIFI, mmsFeature);
    }

    private boolean isSupported(int networkType) {
        return mNetworks.containsKey(networkType) ||
               (networkType == ConnectivityManager.TYPE_VPN) ||
               (networkType == ConnectivityManager.TYPE_ETHERNET &&
                       mContext.getSystemService(Context.ETHERNET_SERVICE) != null);
    }

    public void testIsNetworkSupported() {
        for (int type = -1; type <= ConnectivityManager.MAX_NETWORK_TYPE; type++) {
            boolean supported = mCm.isNetworkSupported(type);
            if (isSupported(type)) {
                assertTrue(supported);
            } else {
                assertFalse(supported);
            }
        }
    }

    public void testRequestRouteToHost() {
        for (int type = -1 ; type <= ConnectivityManager.MAX_NETWORK_TYPE; type++) {
            assertRequestRouteToHostUnsupported(type, HOST_ADDRESS);
        }
    }

    public void testTest() {
        mCm.getBackgroundDataSetting();
    }

    private NetworkRequest makeWifiNetworkRequest() {
        return new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
    }

    /**
     * Exercises both registerNetworkCallback and unregisterNetworkCallback. This checks to
     * see if we get a callback for the TRANSPORT_WIFI transport type being available.
     *
     * <p>In order to test that a NetworkCallback occurs, we need some change in the network
     * state (either a transport or capability is now available). The most straightforward is
     * WiFi. We could add a version that uses the telephony data connection but it's not clear
     * that it would increase test coverage by much (how many devices have 3G radio but not Wifi?).
     */
    public void testRegisterNetworkCallback() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testRegisterNetworkCallback cannot execute unless device supports WiFi");
            return;
        }

        // We will register for a WIFI network being available or lost.
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), callback);

        final TestNetworkCallback defaultTrackingCallback = new TestNetworkCallback();
        mCm.registerDefaultNetworkCallback(defaultTrackingCallback);

        Network wifiNetwork = null;

        try {
            ensureWifiConnected();

            // Now we should expect to get a network callback about availability of the wifi
            // network even if it was already connected as a state-based action when the callback
            // is registered.
            wifiNetwork = callback.waitForAvailable();
            assertNotNull("Did not receive NetworkCallback.onAvailable for TRANSPORT_WIFI",
                    wifiNetwork);

            assertNotNull("Did not receive NetworkCallback.onAvailable for any default network",
                    defaultTrackingCallback.waitForAvailable());
        } catch (InterruptedException e) {
            fail("Broadcast receiver or NetworkCallback wait was interrupted.");
        } finally {
            mCm.unregisterNetworkCallback(callback);
            mCm.unregisterNetworkCallback(defaultTrackingCallback);
        }
    }

    /**
     * Tests both registerNetworkCallback and unregisterNetworkCallback similarly to
     * {@link #testRegisterNetworkCallback} except that a {@code PendingIntent} is used instead
     * of a {@code NetworkCallback}.
     */
    public void testRegisterNetworkCallback_withPendingIntent() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testRegisterNetworkCallback cannot execute unless device supports WiFi");
            return;
        }

        // Create a ConnectivityActionReceiver that has an IntentFilter for our locally defined
        // action, NETWORK_CALLBACK_ACTION.
        IntentFilter filter = new IntentFilter();
        filter.addAction(NETWORK_CALLBACK_ACTION);

        ConnectivityActionReceiver receiver = new ConnectivityActionReceiver(
                ConnectivityManager.TYPE_WIFI, NetworkInfo.State.CONNECTED);
        mContext.registerReceiver(receiver, filter);

        // Create a broadcast PendingIntent for NETWORK_CALLBACK_ACTION.
        Intent intent = new Intent(NETWORK_CALLBACK_ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // We will register for a WIFI network being available or lost.
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), pendingIntent);

        try {
            ensureWifiConnected();

            // Now we expect to get the Intent delivered notifying of the availability of the wifi
            // network even if it was already connected as a state-based action when the callback
            // is registered.
            assertTrue("Did not receive expected Intent " + intent + " for TRANSPORT_WIFI",
                    receiver.waitForState());
        } catch (InterruptedException e) {
            fail("Broadcast receiver or NetworkCallback wait was interrupted.");
        } finally {
            mCm.unregisterNetworkCallback(pendingIntent);
            pendingIntent.cancel();
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Exercises the requestNetwork with NetworkCallback API. This checks to
     * see if we get a callback for an INTERNET request.
     */
    public void testRequestNetworkCallback() {
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.requestNetwork(new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), callback);

        try {
            // Wait to get callback for availability of internet
            Network internetNetwork = callback.waitForAvailable();
            assertNotNull("Did not receive NetworkCallback#onAvailable for INTERNET",
                    internetNetwork);
        } catch (InterruptedException e) {
            fail("NetworkCallback wait was interrupted.");
        } finally {
            mCm.unregisterNetworkCallback(callback);
        }
    }

    /**
     * Exercises the requestNetwork with NetworkCallback API with timeout - expected to
     * fail. Use WIFI and switch Wi-Fi off.
     */
    public void testRequestNetworkCallback_onUnavailable() {
        final boolean previousWifiEnabledState = mWifiManager.isWifiEnabled();
        if (previousWifiEnabledState) {
            disconnectFromWifi(null);
        }

        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(TRANSPORT_WIFI)
                .build(), callback, 100);

        try {
            // Wait to get callback for unavailability of requested network
            assertTrue("Did not receive NetworkCallback#onUnavailable",
                    callback.waitForUnavailable());
        } catch (InterruptedException e) {
            fail("NetworkCallback wait was interrupted.");
        } finally {
            mCm.unregisterNetworkCallback(callback);
            if (previousWifiEnabledState) {
                connectToWifi();
            }
        }
    }

    /**
     * Tests reporting of connectivity changed.
     */
    public void testConnectivityChanged_manifestRequestOnly_shouldNotReceiveIntent() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testConnectivityChanged_manifestRequestOnly_shouldNotReceiveIntent cannot execute unless device supports WiFi");
            return;
        }
        ConnectivityReceiver.prepare();

        toggleWifi();

        // The connectivity broadcast has been sent; push through a terminal broadcast
        // to wait for in the receive to confirm it didn't see the connectivity change.
        Intent finalIntent = new Intent(ConnectivityReceiver.FINAL_ACTION);
        finalIntent.setClass(mContext, ConnectivityReceiver.class);
        mContext.sendBroadcast(finalIntent);
        assertFalse(ConnectivityReceiver.waitForBroadcast());
    }

    public void testConnectivityChanged_whenRegistered_shouldReceiveIntent() {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testConnectivityChanged_whenRegistered_shouldReceiveIntent cannot execute unless device supports WiFi");
            return;
        }
        ConnectivityReceiver.prepare();
        ConnectivityReceiver receiver = new ConnectivityReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        toggleWifi();
        Intent finalIntent = new Intent(ConnectivityReceiver.FINAL_ACTION);
        finalIntent.setClass(mContext, ConnectivityReceiver.class);
        mContext.sendBroadcast(finalIntent);

        assertTrue(ConnectivityReceiver.waitForBroadcast());
    }

    public void testConnectivityChanged_manifestRequestOnlyPreN_shouldReceiveIntent()
            throws InterruptedException {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testConnectivityChanged_manifestRequestOnlyPreN_shouldReceiveIntent cannot execute unless device supports WiFi");
            return;
        }
        Intent startIntent = new Intent();
        startIntent.setComponent(new ComponentName("android.net.cts.appForApi23",
                "android.net.cts.appForApi23.ConnectivityListeningActivity"));
        mContext.startActivity(startIntent);

        toggleWifi();

        Intent getConnectivityCount = new Intent(GET_WIFI_CONNECTIVITY_ACTION_COUNT);
        assertEquals(2, sendOrderedBroadcastAndReturnResultCode(
                getConnectivityCount, SEND_BROADCAST_TIMEOUT));
    }

    private int sendOrderedBroadcastAndReturnResultCode(
            Intent intent, int timeoutMs) throws InterruptedException {
        final LinkedBlockingQueue<Integer> result = new LinkedBlockingQueue<>(1);
        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result.offer(getResultCode());
            }
        }, null, 0, null, null);

        Integer resultCode = result.poll(timeoutMs, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out (more than " + timeoutMs +
                " milliseconds) waiting for result code for broadcast", resultCode);
        return resultCode;
    }

    // Toggle WiFi twice, leaving it in the state it started in
    private void toggleWifi() {
        if (mWifiManager.isWifiEnabled()) {
            Network wifiNetwork = getWifiNetwork();
            disconnectFromWifi(wifiNetwork);
            connectToWifi();
        } else {
            connectToWifi();
            Network wifiNetwork = getWifiNetwork();
            disconnectFromWifi(wifiNetwork);
        }
    }

    /** Enable WiFi and wait for it to become connected to a network. */
    private Network connectToWifi() {
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), callback);
        Network wifiNetwork = null;

        ConnectivityActionReceiver receiver = new ConnectivityActionReceiver(
                ConnectivityManager.TYPE_WIFI, NetworkInfo.State.CONNECTED);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        boolean connected = false;
        try {
            assertTrue(mWifiManager.setWifiEnabled(true));
            // Ensure we get both an onAvailable callback and a CONNECTIVITY_ACTION.
            wifiNetwork = callback.waitForAvailable();
            assertNotNull(wifiNetwork);
            connected = receiver.waitForState();
        } catch (InterruptedException ex) {
            fail("connectToWifi was interrupted");
        } finally {
            mCm.unregisterNetworkCallback(callback);
            mContext.unregisterReceiver(receiver);
        }

        assertTrue("Wifi must be configured to connect to an access point for this test.",
                connected);
        return wifiNetwork;
    }

    private Socket getBoundSocket(Network network, String host, int port) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(host, port);
        Socket s = network.getSocketFactory().createSocket();
        try {
            s.setSoTimeout(SOCKET_TIMEOUT_MS);
            s.connect(addr, SOCKET_TIMEOUT_MS);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        return s;
    }

    private void testHttpRequest(Socket s) throws IOException {
        OutputStream out = s.getOutputStream();
        InputStream in = s.getInputStream();

        final byte[] requestBytes = HTTP_REQUEST.getBytes("UTF-8");
        byte[] responseBytes = new byte[4096];
        out.write(requestBytes);
        in.read(responseBytes);
        assertTrue(new String(responseBytes, "UTF-8").startsWith("HTTP/1.0 204 No Content\r\n"));
    }

    /** Disable WiFi and wait for it to become disconnected from the network. */
    private void disconnectFromWifi(Network wifiNetworkToCheck) {
        final TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), callback);
        Network lostWifiNetwork = null;

        ConnectivityActionReceiver receiver = new ConnectivityActionReceiver(
                ConnectivityManager.TYPE_WIFI, NetworkInfo.State.DISCONNECTED);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        // Assert that we can establish a TCP connection on wifi.
        Socket wifiBoundSocket = null;
        if (wifiNetworkToCheck != null) {
            try {
                wifiBoundSocket = getBoundSocket(wifiNetworkToCheck, TEST_HOST, HTTP_PORT);
                testHttpRequest(wifiBoundSocket);
            } catch (IOException e) {
                fail("HTTP request before wifi disconnected failed with: " + e);
            }
        }

        boolean disconnected = false;
        try {
            assertTrue(mWifiManager.setWifiEnabled(false));
            // Ensure we get both an onLost callback and a CONNECTIVITY_ACTION.
            lostWifiNetwork = callback.waitForLost();
            assertNotNull(lostWifiNetwork);
            disconnected = receiver.waitForState();
        } catch (InterruptedException ex) {
            fail("disconnectFromWifi was interrupted");
        } finally {
            mCm.unregisterNetworkCallback(callback);
            mContext.unregisterReceiver(receiver);
        }

        assertTrue("Wifi failed to reach DISCONNECTED state.", disconnected);

        // Check that the socket is closed when wifi disconnects.
        if (wifiBoundSocket != null) {
            try {
                testHttpRequest(wifiBoundSocket);
                fail("HTTP request should not succeed after wifi disconnects");
            } catch (IOException expected) {
                assertEquals(Os.strerror(OsConstants.ECONNABORTED), expected.getMessage());
            }
        }
    }

    /**
     * Receiver that captures the last connectivity change's network type and state. Recognizes
     * both {@code CONNECTIVITY_ACTION} and {@code NETWORK_CALLBACK_ACTION} intents.
     */
    private class ConnectivityActionReceiver extends BroadcastReceiver {

        private final CountDownLatch mReceiveLatch = new CountDownLatch(1);

        private final int mNetworkType;
        private final NetworkInfo.State mNetState;

        ConnectivityActionReceiver(int networkType, NetworkInfo.State netState) {
            mNetworkType = networkType;
            mNetState = netState;
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            NetworkInfo networkInfo = null;

            // When receiving ConnectivityManager.CONNECTIVITY_ACTION, the NetworkInfo parcelable
            // is stored in EXTRA_NETWORK_INFO. With a NETWORK_CALLBACK_ACTION, the Network is
            // sent in EXTRA_NETWORK and we need to ask the ConnectivityManager for the NetworkInfo.
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                networkInfo = intent.getExtras()
                        .getParcelable(ConnectivityManager.EXTRA_NETWORK_INFO);
                assertNotNull("ConnectivityActionReceiver expected EXTRA_NETWORK_INFO", networkInfo);
            } else if (NETWORK_CALLBACK_ACTION.equals(action)) {
                Network network = intent.getExtras()
                        .getParcelable(ConnectivityManager.EXTRA_NETWORK);
                assertNotNull("ConnectivityActionReceiver expected EXTRA_NETWORK", network);
                networkInfo = mCm.getNetworkInfo(network);
                if (networkInfo == null) {
                    // When disconnecting, it seems like we get an intent sent with an invalid
                    // Network; that is, by the time we call ConnectivityManager.getNetworkInfo(),
                    // it is invalid. Ignore these.
                    Log.i(TAG, "ConnectivityActionReceiver NETWORK_CALLBACK_ACTION ignoring "
                            + "invalid network");
                    return;
                }
            } else {
                fail("ConnectivityActionReceiver received unxpected intent action: " + action);
            }

            assertNotNull("ConnectivityActionReceiver didn't find NetworkInfo", networkInfo);
            int networkType = networkInfo.getType();
            State networkState = networkInfo.getState();
            Log.i(TAG, "Network type: " + networkType + " state: " + networkState);
            if (networkType == mNetworkType && networkInfo.getState() == mNetState) {
                mReceiveLatch.countDown();
            }
        }

        public boolean waitForState() throws InterruptedException {
            return mReceiveLatch.await(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Callback used in testRegisterNetworkCallback that allows caller to block on
     * {@code onAvailable}.
     */
    private static class TestNetworkCallback extends ConnectivityManager.NetworkCallback {
        private final CountDownLatch mAvailableLatch = new CountDownLatch(1);
        private final CountDownLatch mLostLatch = new CountDownLatch(1);
        private final CountDownLatch mUnavailableLatch = new CountDownLatch(1);

        public Network currentNetwork;
        public Network lastLostNetwork;

        public Network waitForAvailable() throws InterruptedException {
            return mAvailableLatch.await(30, TimeUnit.SECONDS) ? currentNetwork : null;
        }

        public Network waitForLost() throws InterruptedException {
            return mLostLatch.await(30, TimeUnit.SECONDS) ? lastLostNetwork : null;
        }

        public boolean waitForUnavailable() throws InterruptedException {
            return mUnavailableLatch.await(2, TimeUnit.SECONDS);
        }


        @Override
        public void onAvailable(Network network) {
            currentNetwork = network;
            mAvailableLatch.countDown();
        }

        @Override
        public void onLost(Network network) {
            lastLostNetwork = network;
            if (network.equals(currentNetwork)) {
                currentNetwork = null;
            }
            mLostLatch.countDown();
        }

        @Override
        public void onUnavailable() {
            mUnavailableLatch.countDown();
        }
    }

    private Network getWifiNetwork() {
        TestNetworkCallback callback = new TestNetworkCallback();
        mCm.registerNetworkCallback(makeWifiNetworkRequest(), callback);
        Network network = null;
        try {
            network = callback.waitForAvailable();
        } catch (InterruptedException e) {
            fail("NetworkCallback wait was interrupted.");
        } finally {
            mCm.unregisterNetworkCallback(callback);
        }
        assertNotNull("Cannot find Network for wifi. Is wifi connected?", network);
        return network;
    }

    /** Verify restricted networks cannot be requested. */
    public void testRestrictedNetworks() {
        // Verify we can request unrestricted networks:
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NET_CAPABILITY_INTERNET).build();
        NetworkCallback callback = new NetworkCallback();
        mCm.requestNetwork(request, callback);
        mCm.unregisterNetworkCallback(callback);
        // Verify we cannot request restricted networks:
        request = new NetworkRequest.Builder().addCapability(NET_CAPABILITY_IMS).build();
        callback = new NetworkCallback();
        try {
            mCm.requestNetwork(request, callback);
            fail("No exception thrown when restricted network requested.");
        } catch (SecurityException expected) {}
    }

    private Scanner makeWifiSysctlScanner(String key) throws FileNotFoundException {
        Network network = ensureWifiConnected();
        String iface = mCm.getLinkProperties(network).getInterfaceName();
        String path = IPV6_SYSCTL_DIR + "/" + iface + "/" + key;
        return new Scanner(new File(path));
    }

    /** Verify that accept_ra_rt_info_min_plen exists and is set to the expected value */
    public void testAcceptRaRtInfoMinPlen() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testConnectivityChanged_manifestRequestOnlyPreN_shouldReceiveIntent cannot execute unless device supports WiFi");
            return;
        }
        Scanner s = makeWifiSysctlScanner("accept_ra_rt_info_min_plen");
        assertEquals(IPV6_WIFI_ACCEPT_RA_RT_INFO_MIN_PLEN, s.nextInt());
    }

    /** Verify that accept_ra_rt_info_max_plen exists and is set to the expected value */
    public void testAcceptRaRtInfoMaxPlen() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testConnectivityChanged_manifestRequestOnlyPreN_shouldReceiveIntent cannot execute unless device supports WiFi");
            return;
        }
        Scanner s = makeWifiSysctlScanner("accept_ra_rt_info_max_plen");
        assertEquals(IPV6_WIFI_ACCEPT_RA_RT_INFO_MAX_PLEN, s.nextInt());
    }

    /** Verify that router_solicitations exists and is set to the expected value */
    public void testRouterSolicitations() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testConnectivityChanged_manifestRequestOnlyPreN_shouldReceiveIntent cannot execute unless device supports WiFi");
            return;
        }
        Scanner s = makeWifiSysctlScanner("router_solicitations");
        assertEquals(IPV6_WIFI_ROUTER_SOLICITATIONS, s.nextInt());
    }

    /** Verify that router_solicitation_max_interval exists and is in an acceptable interval */
    public void testRouterSolicitationMaxInterval() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.i(TAG, "testConnectivityChanged_manifestRequestOnlyPreN_shouldReceiveIntent cannot execute unless device supports WiFi");
            return;
        }
        Scanner s = makeWifiSysctlScanner("router_solicitation_max_interval");
        int interval = s.nextInt();
        // Verify we're in the interval [15 minutes, 60 minutes]. Lower values may adversely
        // impact battery life and higher values can decrease the probability of detecting
        // network changes.
        final int lowerBoundSec = 15 * 60;
        final int upperBoundSec = 60 * 60;
        assertTrue(lowerBoundSec <= interval);
        assertTrue(interval <= upperBoundSec);
    }
}
