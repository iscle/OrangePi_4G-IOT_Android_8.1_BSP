/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.net.cts.legacy.api22;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.ConditionVariable;
import android.test.AndroidTestCase;
import android.util.Log;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_MOBILE_HIPRI;
import static android.net.ConnectivityManager.TYPE_VPN;
import static android.net.ConnectivityManager.TYPE_WIFI;

public class ConnectivityManagerLegacyTest extends AndroidTestCase {
    private static final String TAG = ConnectivityManagerLegacyTest.class.getSimpleName();
    private static final String FEATURE_ENABLE_HIPRI = "enableHIPRI";
    private static final String HOST_ADDRESS1 = "192.0.2.1";
    private static final String HOST_ADDRESS2 = "192.0.2.2";
    private static final String HOST_ADDRESS3 = "192.0.2.3";

    // These are correct as of API level 22, which is what we target here.
    private static final int APN_REQUEST_FAILED = 3;
    private static final int MAX_NETWORK_TYPE = TYPE_VPN;

    private ConnectivityManager mCm;
    private WifiManager mWifiManager;
    private PackageManager mPackageManager;

    private final List<Integer>mProtectedNetworks = new ArrayList<Integer>();

    protected void setUp() throws Exception {
        super.setUp();
        mCm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        mPackageManager = getContext().getPackageManager();

        // Get com.android.internal.R.array.config_protectedNetworks
        int resId = getContext().getResources().getIdentifier("config_protectedNetworks", "array", "android");
        int[] protectedNetworks = getContext().getResources().getIntArray(resId);
        for (int p : protectedNetworks) {
            mProtectedNetworks.add(p);
        }
    }

    // true if only the system can turn it on
    private boolean isNetworkProtected(int networkType) {
        return mProtectedNetworks.contains(networkType);
    }

    private int ipv4AddrToInt(String addrString) throws Exception {
        byte[] addr = ((Inet4Address) InetAddress.getByName(addrString)).getAddress();
        return ((addr[3] & 0xff) << 24) | ((addr[2] & 0xff) << 16) |
                ((addr[1] & 0xff) << 8) | (addr[0] & 0xff);
    }

    // Returns a list of all the IP addresses for all the networks of a given legacy type. We can't
    // just fetch the IP addresses for that type because there is no public getLinkProperties API
    // that takes a legacy type.
    private List<InetAddress> getIpAddresses(int type) {
        ArrayList<InetAddress> addresses = new ArrayList<>();
        Network[] networks = mCm.getAllNetworks();
        for (int i = 0; i < networks.length; i++) {
            NetworkInfo ni = mCm.getNetworkInfo(networks[i]);
            if (ni != null && ni.getType() == type) {
                // This does not include IP addresses on stacked interfaces (e.g., 464xlat), because
                // there is no public API that will return them.
                LinkProperties lp = mCm.getLinkProperties(networks[i]);
                for (LinkAddress address : lp.getLinkAddresses()) {
                    addresses.add(address.getAddress());
                }
            }
        }
        return addresses;
    }

    private boolean hasIPv4(int type) {
        for (InetAddress address : getIpAddresses(type)) {
            if (address instanceof Inet4Address) {
                return true;
            }
        }
        return false;
    }

    private void checkSourceAddress(String addrString, int type) throws Exception {
        // The public requestRouteToHost API only supports IPv4, but it will not return failure if
        // the network does not have an IPv4 address. So don't check that it's working unless we
        // know that the network has an IPv4 address. Note that it's possible that the network will
        // have an IPv4 address but we don't know about it, because the IPv4 address might be on a
        // stacked interface and we wouldn't be able to see it.
        if (!hasIPv4(type)) {
            Log.d(TAG, "Not checking source address on network type " + type + ", no IPv4 address");
            return;
        }

        DatagramSocket d = new DatagramSocket();
        d.connect(InetAddress.getByName(addrString), 7);
        InetAddress localAddress = d.getLocalAddress();
        String localAddrString = localAddress.getHostAddress();

        Log.d(TAG, "Got source address " + localAddrString + " for destination " + addrString);

        assertTrue(
                "Local address " + localAddress + " not assigned to any network of type " + type,
                getIpAddresses(type).contains(localAddress));

        Log.d(TAG, "Source address " + localAddress + " found on network type " + type);
    }

    /** Test that hipri can be brought up when Wifi is enabled. */
    public void testStartUsingNetworkFeature_enableHipri() throws Exception {
        if (!mPackageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || !mPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            // This test requires a mobile data connection and WiFi.
            return;
        }

        // Make sure WiFi is connected to an access point.
        boolean isWifiEnabled = isWifiConnected();
        try {
            if (!isWifiEnabled) {
                connectToWifi();
            }

            expectNetworkBroadcast(TYPE_MOBILE_HIPRI, NetworkInfo.State.CONNECTED, new Runnable() {
                public void run() {
                    int ret = mCm.startUsingNetworkFeature(TYPE_MOBILE, FEATURE_ENABLE_HIPRI);
                    assertTrue("Couldn't start using the HIPRI feature.", ret != -1);
                }
            });

            assertTrue("Couldn't requestRouteToHost using HIPRI.",
                    mCm.requestRouteToHost(TYPE_MOBILE_HIPRI, ipv4AddrToInt(HOST_ADDRESS1)));

            checkSourceAddress(HOST_ADDRESS1, TYPE_MOBILE);
            checkSourceAddress(HOST_ADDRESS2, TYPE_WIFI);

            // TODO check dns selection

            expectNetworkBroadcast(TYPE_MOBILE_HIPRI, NetworkInfo.State.DISCONNECTED, new Runnable() {
                public void run() {
                    int ret = mCm.stopUsingNetworkFeature(TYPE_MOBILE, FEATURE_ENABLE_HIPRI);
                    assertTrue("Couldn't stop using the HIPRI feature.", ret != -1);
                }
            });

            // TODO check dns selection
        } finally {
            if (!isWifiEnabled && isWifiConnected()) {
                disconnectFromWifi();
            }
        }
    }

    public void testStartUsingNetworkFeature() {

        final String invalidFeature = "invalidFeature";
        final String mmsFeature = "enableMMS";
        final int failureCode = -1;
        final int wifiOnlyStartFailureCode = APN_REQUEST_FAILED;
        final int wifiOnlyStopFailureCode = -1;

        NetworkInfo ni = mCm.getNetworkInfo(TYPE_MOBILE);
        if (ni != null) {
            assertEquals(APN_REQUEST_FAILED,
                    mCm.startUsingNetworkFeature(TYPE_MOBILE, invalidFeature));
            assertEquals(failureCode, mCm.stopUsingNetworkFeature(TYPE_MOBILE, invalidFeature));
        } else {
            assertEquals(wifiOnlyStartFailureCode, mCm.startUsingNetworkFeature(TYPE_MOBILE,
                    invalidFeature));
            assertEquals(wifiOnlyStopFailureCode, mCm.stopUsingNetworkFeature(TYPE_MOBILE,
                    invalidFeature));
        }

        ni = mCm.getNetworkInfo(TYPE_WIFI);
        if (ni != null) {
            // Should return failure because MMS is not supported on WIFI.
            assertEquals(APN_REQUEST_FAILED, mCm.startUsingNetworkFeature(TYPE_WIFI,
                    mmsFeature));
            assertEquals(failureCode, mCm.stopUsingNetworkFeature(TYPE_WIFI,
                    mmsFeature));
        }
    }

    private void expectNetworkBroadcast(final int type, final NetworkInfo.State state,
            Runnable afterWhat) {
        final int TIMEOUT_MS = 30 * 1000;
        final ConditionVariable var = new ConditionVariable();

        Log.d(TAG, "Waiting for " + state + " broadcast for type " + type);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                NetworkInfo ni = intent.getExtras()
                        .getParcelable(ConnectivityManager.EXTRA_NETWORK_INFO);
                assertNotNull("CONNECTIVITY_ACTION with null EXTRA_NETWORK_INFO", ni);
                if (ni.getType() == type && ni.getState().equals(state)) {
                    Log.d(TAG, "Received expected " + state + " broadcast for type " + type);
                    var.open();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        try {
            afterWhat.run();
            final String msg = "Did not receive expected " + state + " broadcast for type " + type +
                    " after " + TIMEOUT_MS + " ms";
            assertTrue(msg, var.block(TIMEOUT_MS));
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    private boolean isWifiConnected() {
        NetworkInfo ni = mCm.getNetworkInfo(TYPE_WIFI);
        return ni != null && ni.isConnected();
    }

    private void setWifiState(final boolean enabled) {
        if (enabled != isWifiConnected()) {
            final NetworkInfo.State desiredState = enabled ?
                    NetworkInfo.State.CONNECTED :
                    NetworkInfo.State.DISCONNECTED;
            expectNetworkBroadcast(TYPE_WIFI, desiredState, new Runnable() {
                public void run() {
                    mWifiManager.setWifiEnabled(enabled);
                }
            });
        }
    }

    private void connectToWifi() {
        setWifiState(true);
    }

    private void disconnectFromWifi() {
        setWifiState(false);
    }

    private boolean isNetworkSupported(int networkType) {
        return mCm.getNetworkInfo(networkType) != null;
    }

    public void testRequestRouteToHost() throws Exception {
        for (int type = -1 ; type <= MAX_NETWORK_TYPE; type++) {
            NetworkInfo ni = mCm.getNetworkInfo(type);
            boolean expectToWork = isNetworkSupported(type) && !isNetworkProtected(type) &&
                    ni != null && ni.isConnected();

            try {
                assertTrue("Network type " + type,
                        mCm.requestRouteToHost(type, ipv4AddrToInt(HOST_ADDRESS3)) == expectToWork);
            } catch (Exception e) {
                Log.d(TAG, "got exception in requestRouteToHost for type " + type);
                assertFalse("Exception received for type " + type, expectToWork);
            }

            //TODO verify route table
        }

        assertFalse(mCm.requestRouteToHost(-1, ipv4AddrToInt(HOST_ADDRESS1)));
    }
}
