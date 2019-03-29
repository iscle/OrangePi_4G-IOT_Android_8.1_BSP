/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.jobscheduler.cts;


import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that have network connectivity
 * constraints.
 * Requires manipulating the {@link android.net.wifi.WifiManager} to ensure an unmetered network.
 * Similarly, requires that the phone be connected to a wifi hotspot, or else the test will fail.
 */
@TargetApi(21)
public class ConnectivityConstraintTest extends ConstraintTest {
    private static final String TAG = "ConnectivityConstraintTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int CONNECTIVITY_JOB_ID = ConnectivityConstraintTest.class.hashCode();

    private WifiManager mWifiManager;
    private ConnectivityManager mCm;

    /** Whether the device running these tests supports WiFi. */
    private boolean mHasWifi;
    /** Whether the device running these tests supports telephony. */
    private boolean mHasTelephony;
    /** Track whether WiFi was enabled in case we turn it off. */
    private boolean mInitialWiFiState;

    private JobInfo.Builder mBuilder;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mWifiManager = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        mCm =
                (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        PackageManager packageManager = mContext.getPackageManager();
        mHasWifi = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI);
        mHasTelephony = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        mBuilder =
                new JobInfo.Builder(CONNECTIVITY_JOB_ID, kJobServiceComponent);

        mInitialWiFiState = mWifiManager.isWifiEnabled();
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(CONNECTIVITY_JOB_ID);

        // Ensure that we leave WiFi in its previous state.
        if (mWifiManager.isWifiEnabled() == mInitialWiFiState) {
            return;
        }
        NetworkInfo.State expectedState = mInitialWiFiState ?
                NetworkInfo.State.CONNECTED : NetworkInfo.State.DISCONNECTED;
        ConnectivityActionReceiver receiver =
                new ConnectivityActionReceiver(ConnectivityManager.TYPE_WIFI,
                        expectedState);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(receiver, filter);

        assertTrue(mWifiManager.setWifiEnabled(mInitialWiFiState));
        receiver.waitForStateChange();
        assertTrue("Failure to restore previous WiFi state",
                mWifiManager.isWifiEnabled() == mInitialWiFiState);

        mContext.unregisterReceiver(receiver);
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires a WiFi connection, and assert that it executes when the device
     * is connected to WiFi. This will fail if a wifi connection is unavailable.
     */
    public void testUnmeteredConstraintExecutes_withWifi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        connectToWiFi();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());

        sendExpediteStableChargingBroadcast();

        assertTrue("Job with unmetered constraint did not fire on WiFi.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a connectivity constraint, and ensure that it executes on WiFi.
     */
    public void testConnectivityConstraintExecutes_withWifi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        connectToWiFi();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        sendExpediteStableChargingBroadcast();

        assertTrue("Job with connectivity constraint did not fire on WiFi.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a generic connectivity constraint, and ensure that it executes
     * on a cellular data connection.
     */
    public void testConnectivityConstraintExecutes_withMobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());

        sendExpediteStableChargingBroadcast();

        assertTrue("Job with connectivity constraint did not fire on mobile.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job with a metered connectivity constraint, and ensure that it executes
     * on a mobile data connection.
     */
    public void testConnectivityConstraintExecutes_metered() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED)
                        .build());

        sendExpediteStableChargingBroadcast();
        assertTrue("Job with metered connectivity constraint did not fire on mobile.",
                kTestEnvironment.awaitExecution());
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires a WiFi connection, and assert that it fails when the device is
     * connected to a cellular provider.
     * This test assumes that if the device supports a mobile data connection, then this connection
     * will be available.
     */
    public void testUnmeteredConstraintFails_withMobile() throws Exception {
        if (!checkDeviceSupportsMobileData()) {
            return;
        }
        disconnectWifiToConnectToMobile();

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                        .build());
        sendExpediteStableChargingBroadcast();

        assertTrue("Job requiring unmetered connectivity still executed on mobile.",
                kTestEnvironment.awaitTimeout());
    }

    /**
     * Schedule a job that requires a metered connection, and verify that it does not run when
     * the device is connected to a WiFi provider.
     * This test assumes that if the device supports a mobile data connection, then this connection
     * will be available.
     */
    public void testMeteredConstraintFails_withWiFi() throws Exception {
        if (!mHasWifi) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }
        if (!checkDeviceSupportsMobileData()) {
            Log.d(TAG, "Skipping test that requires the device be mobile data enabled.");
            return;
        }
        connectToWiFi();

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(
                mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_METERED)
                        .build());
        sendExpediteStableChargingBroadcast();

        assertTrue("Job requiring metered connectivity still executed on WiFi.",
                kTestEnvironment.awaitTimeout());
    }

    // --------------------------------------------------------------------------------------------
    // Utility methods
    // --------------------------------------------------------------------------------------------

    /**
     * Determine whether the device running these CTS tests should be subject to tests involving
     * mobile data.
     * @return True if this device will support a mobile data connection.
     */
    private boolean checkDeviceSupportsMobileData() {
        if (!mHasTelephony) {
            Log.d(TAG, "Skipping test that requires telephony features, not supported by this" +
                    " device");
            return false;
        }
        if (mCm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE) == null) {
            Log.d(TAG, "Skipping test that requires ConnectivityManager.TYPE_MOBILE");
            return false;
        }
        return true;
    }

    /**
     * Ensure WiFi is enabled, and block until we've verified that we are in fact connected.
     * Taken from {@link android.net.http.cts.ApacheHttpClientTest}.
     */
    private void connectToWiFi() throws InterruptedException {
        if (!mWifiManager.isWifiEnabled()) {
            ConnectivityActionReceiver receiver =
                    new ConnectivityActionReceiver(ConnectivityManager.TYPE_WIFI,
                            NetworkInfo.State.CONNECTED);
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(receiver, filter);

            assertTrue(mWifiManager.setWifiEnabled(true));
            assertTrue("Wifi must be configured to connect to an access point for this test.",
                    receiver.waitForStateChange());

            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Disconnect from WiFi in an attempt to connect to cellular data. Worth noting that this is
     * best effort - there are no public APIs to force connecting to cell data. We disable WiFi
     * and wait for a broadcast that we're connected to cell.
     * We will not call into this function if the device doesn't support telephony.
     * @see #mHasTelephony
     * @see #checkDeviceSupportsMobileData()
     */
    private void disconnectWifiToConnectToMobile() throws InterruptedException {
        if (mHasWifi && mWifiManager.isWifiEnabled()) {
            ConnectivityActionReceiver connectMobileReceiver =
                    new ConnectivityActionReceiver(ConnectivityManager.TYPE_MOBILE,
                            NetworkInfo.State.CONNECTED);
            ConnectivityActionReceiver disconnectWifiReceiver =
                    new ConnectivityActionReceiver(ConnectivityManager.TYPE_WIFI,
                            NetworkInfo.State.DISCONNECTED);
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(connectMobileReceiver, filter);
            mContext.registerReceiver(disconnectWifiReceiver, filter);

            assertTrue(mWifiManager.setWifiEnabled(false));
            assertTrue("Failure disconnecting from WiFi.",
                    disconnectWifiReceiver.waitForStateChange());
            assertTrue("Device must have access to a metered network for this test.",
                    connectMobileReceiver.waitForStateChange());

            mContext.unregisterReceiver(connectMobileReceiver);
            mContext.unregisterReceiver(disconnectWifiReceiver);
        }
    }

    /** Capture the last connectivity change's network type and state. */
    private class ConnectivityActionReceiver extends BroadcastReceiver {

        private final CountDownLatch mReceiveLatch = new CountDownLatch(1);

        private final int mNetworkType;

        private final NetworkInfo.State mExpectedState;

        ConnectivityActionReceiver(int networkType, NetworkInfo.State expectedState) {
            mNetworkType = networkType;
            mExpectedState = expectedState;
        }

        public void onReceive(Context context, Intent intent) {
            // Dealing with a connectivity changed event for this network type.
            final int networkTypeChanged =
                    intent.getIntExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, -1);
            if (networkTypeChanged == -1) {
                Log.e(TAG, "No network type provided in intent");
                return;
            }

            if (networkTypeChanged != mNetworkType) {
                // Only track changes for the connectivity event that we are interested in.
                return;
            }
            // Pull out the NetworkState object that we're interested in. Necessary because
            // the ConnectivityManager will filter on uid for background connectivity.
            NetworkInfo[] allNetworkInfo = mCm.getAllNetworkInfo();
            NetworkInfo networkInfo = null;
            for (int i=0; i<allNetworkInfo.length; i++) {
                NetworkInfo ni = allNetworkInfo[i];
                if (ni.getType() == mNetworkType) {
                    networkInfo =  ni;
                    break;
                }
            }
            if (networkInfo == null) {
                Log.e(TAG, "Could not find correct network type.");
                return;
            }

            NetworkInfo.State networkState = networkInfo.getState();
            Log.i(TAG, "Network type: " + mNetworkType + " State: " + networkState);
            if (networkState == mExpectedState) {
                mReceiveLatch.countDown();
            }
        }

        public boolean waitForStateChange() throws InterruptedException {
            return mReceiveLatch.await(30, TimeUnit.SECONDS) || hasExpectedState();
        }

        private boolean hasExpectedState() {
            return mExpectedState == mCm.getNetworkInfo(mNetworkType).getState();
        }
    }

}
