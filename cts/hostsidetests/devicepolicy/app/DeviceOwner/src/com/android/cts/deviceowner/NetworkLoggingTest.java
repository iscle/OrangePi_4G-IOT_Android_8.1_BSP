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
package com.android.cts.deviceowner;

import android.app.admin.ConnectEvent;
import android.app.admin.DnsEvent;
import android.app.admin.NetworkEvent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class NetworkLoggingTest extends BaseDeviceOwnerTest {

    private static final String TAG = "NetworkLoggingTest";
    private static final int FAKE_BATCH_TOKEN = -666; // real batch tokens are always non-negative
    private static final int FULL_LOG_BATCH_SIZE = 1200;
    private static final String CTS_APP_PACKAGE_NAME = "com.android.cts.deviceowner";
    private static final int MAX_IP_ADDRESSES_LOGGED = 10;

    private static final String[] NOT_LOGGED_URLS_LIST = {
            "wikipedia.org",
            "google.pl"
    };

    private static final int MAX_VISITING_WEBPAGES_ITERATIONS = 100; // >1500 events
    // note: make sure URL_LIST has at least 10 urls in it to generate enough network traffic
    private static final String[] LOGGED_URLS_LIST = {
            "example.com",
            "example.net",
            "example.org",
            "example.edu",
            "ipv6.google.com",
            "google.co.jp",
            "google.fr",
            "google.com.br",
            "google.com.tr",
            "google.co.uk",
            "google.de"
    };

    private final BroadcastReceiver mNetworkLogsReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BaseDeviceOwnerTest.ACTION_NETWORK_LOGS_AVAILABLE.equals(intent.getAction())) {
                mGenerateNetworkTraffic = false;
                mCurrentBatchToken = intent.getLongExtra(
                        BaseDeviceOwnerTest.EXTRA_NETWORK_LOGS_BATCH_TOKEN, FAKE_BATCH_TOKEN);
                if (mCountDownLatch != null) {
                    mCountDownLatch.countDown();
                }
            }
        }
    };

    private CountDownLatch mCountDownLatch;
    private long mCurrentBatchToken;
    private volatile boolean mGenerateNetworkTraffic;

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), false);
        assertFalse(mDevicePolicyManager.isNetworkLoggingEnabled(getWho()));

        super.tearDown();
    }

    /**
     * Test: retrieving network logs can only be done if there's one user on the device or all
     * secondary users / profiles are affiliated.
     */
    public void testRetrievingNetworkLogsThrowsSecurityException() {
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isNetworkLoggingEnabled(getWho()));
        try {
            mDevicePolicyManager.retrieveNetworkLogs(getWho(), FAKE_BATCH_TOKEN);
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Test: when a wrong batch token id (not a token of the current batch) is provided, null should
     * be returned.
     */
    public void testProvidingWrongBatchTokenReturnsNull() {
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isNetworkLoggingEnabled(getWho()));
        assertNull(mDevicePolicyManager.retrieveNetworkLogs(getWho(), FAKE_BATCH_TOKEN));
    }

    /**
     * Test: test that the actual logging happens when the network logging is enabled and doesn't
     * happen before it's enabled; for this test to work we need to generate enough internet
     * traffic, so that the batch of logs is created
     */
    public void testNetworkLoggingAndRetrieval() throws Exception {
        mCountDownLatch = new CountDownLatch(1);
        mCurrentBatchToken = FAKE_BATCH_TOKEN;
        mGenerateNetworkTraffic = true;
        // register a receiver that listens for DeviceAdminReceiver#onNetworkLogsAvailable()
        final IntentFilter filterNetworkLogsAvailable = new IntentFilter(
                BaseDeviceOwnerTest.ACTION_NETWORK_LOGS_AVAILABLE);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mNetworkLogsReceiver,
                filterNetworkLogsAvailable);

        // visit websites that shouldn't be logged as network logging isn't enabled yet
        for (final String url : NOT_LOGGED_URLS_LIST) {
            connectToWebsite(url);
        }

        // enable network logging and start the logging scenario
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isNetworkLoggingEnabled(getWho()));

        // TODO: here test that facts about logging are shown in the UI

        // visit websites in a loop to generate enough network traffic
        int iterationsDone = 0;
        while (mGenerateNetworkTraffic && iterationsDone < MAX_VISITING_WEBPAGES_ITERATIONS) {
            for (final String url : LOGGED_URLS_LIST) {
                connectToWebsite(url);
            }
            iterationsDone++;
        }

        // if DeviceAdminReceiver#onNetworkLogsAvailable() hasn't been triggered yet, wait for up to
        // 3 minutes just in case
        mCountDownLatch.await(3, TimeUnit.MINUTES);
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mNetworkLogsReceiver);
        if (mGenerateNetworkTraffic) {
            fail("Carried out 100 iterations and waited for 3 minutes, but still didn't get"
                    + " DeviceAdminReceiver#onNetworkLogsAvailable() callback");
        }

        // retrieve and verify network logs
        final List<NetworkEvent> networkEvents = mDevicePolicyManager.retrieveNetworkLogs(getWho(),
                mCurrentBatchToken);
        if (networkEvents == null) {
            fail("Failed to retrieve batch of network logs with batch token " + mCurrentBatchToken);
            return;
        }
        verifyNetworkLogs(networkEvents, iterationsDone);
    }

    private void verifyNetworkLogs(List<NetworkEvent> networkEvents, int iterationsDone) {
        assertTrue(networkEvents.size() == FULL_LOG_BATCH_SIZE);
        int ctsPackageNameCounter = 0;
        // allow a small down margin for verification, to avoid flakyness
        final int iterationsDoneWithMargin = iterationsDone - 5;
        final int[] visitedFrequencies = new int[LOGGED_URLS_LIST.length];

        for (int i = 0; i < networkEvents.size(); i++) {
            final NetworkEvent currentEvent = networkEvents.get(i);
            // verify that the events are in chronological order
            if (i > 0) {
                assertTrue(currentEvent.getTimestamp() >= networkEvents.get(i - 1).getTimestamp());
            }
            // count how many events come from the CTS app
            if (CTS_APP_PACKAGE_NAME.equals(currentEvent.getPackageName())) {
                ctsPackageNameCounter++;
                if (currentEvent instanceof DnsEvent) {
                    final DnsEvent dnsEvent = (DnsEvent) currentEvent;
                    // verify that we didn't log a hostname lookup when network logging was disabled
                    if (dnsEvent.getHostname().contains(NOT_LOGGED_URLS_LIST[0])
                            || dnsEvent.getHostname().contains(NOT_LOGGED_URLS_LIST[1])) {
                        fail("A hostname that was looked-up when network logging was disabled"
                                + " was logged.");
                    }
                    // count the frequencies of LOGGED_URLS_LIST's hostnames that were looked up
                    for (int j = 0; j < LOGGED_URLS_LIST.length; j++) {
                        if (dnsEvent.getHostname().contains(LOGGED_URLS_LIST[j])) {
                            visitedFrequencies[j]++;
                            break;
                        }
                    }
                    // verify that as many IP addresses were logged as were reported (max 10)
                    final List<InetAddress> ips = dnsEvent.getInetAddresses();
                    assertTrue(ips.size() <= MAX_IP_ADDRESSES_LOGGED);
                    final int expectedAddressCount = Math.min(MAX_IP_ADDRESSES_LOGGED,
                            dnsEvent.getTotalResolvedAddressCount());
                    assertEquals(expectedAddressCount, ips.size());
                    // verify the IP addresses are valid IPv4 or IPv6 addresses
                    for (final InetAddress ipAddress : ips) {
                        assertTrue(isIpv4OrIpv6Address(ipAddress));
                    }
                } else if (currentEvent instanceof ConnectEvent) {
                    final ConnectEvent connectEvent = (ConnectEvent) currentEvent;
                    // verify the IP address is a valid IPv4 or IPv6 address
                    assertTrue(isIpv4OrIpv6Address(connectEvent.getInetAddress()));
                    // verify that the port is a valid port
                    assertTrue(connectEvent.getPort() >= 0 && connectEvent.getPort() <= 65535);
                } else {
                    fail("An unknown NetworkEvent type logged: "
                            + currentEvent.getClass().getName());
                }
            }
        }

        // verify that each hostname from LOGGED_URLS_LIST was looked-up enough times
        for (int i = 0; i < 10; i++) {
            // to avoid flakyness account for DNS caching and connection errors
            assertTrue(visitedFrequencies[i] >= (iterationsDoneWithMargin / 2));
        }
        // verify that sufficient iterations done by the CTS app were logged
        assertTrue(ctsPackageNameCounter >= LOGGED_URLS_LIST.length * iterationsDoneWithMargin);
    }

    private void connectToWebsite(String urlString) {
        HttpURLConnection urlConnection = null;
        try {
            final URL url = new URL("http://" + urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(2000);
            urlConnection.setReadTimeout(2000);
            urlConnection.getResponseCode();
        } catch (IOException e) {
            Log.w(TAG, "Failed to connect to " + urlString, e);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private boolean isIpv4OrIpv6Address(InetAddress addr) {
        return ((addr instanceof Inet4Address) || (addr instanceof Inet6Address));
    }
}
