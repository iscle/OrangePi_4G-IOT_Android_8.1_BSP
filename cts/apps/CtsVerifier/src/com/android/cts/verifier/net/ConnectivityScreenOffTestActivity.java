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

package com.android.cts.verifier.net;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;

/**
 * A CTS Verifier test case for testing IPv6 network connectivity while the screen is off.
 *
 * This tests that Wi-Fi implementations are compliant with section 7.4.5
 * ("Minimum Network Capability") of the CDD. Specifically, it requires that: "unicast IPv6
 * packets sent to the device MUST NOT be dropped, even when the screen is not in an active
 * state."
 *
 * The verification is attempted as follows:
 *
 *     [1] The device must have Wi-Fi capability.
 *     [2] The device must join an IPv6-capable network (basic IPv6 connectivity to an
 *         Internet resource is tested).
 *     [3] If the device has a battery, the device must be disconnected from any power source.
 *     [4] The screen is put to sleep.
 *     [5] After two minutes, another IPv6 connectivity test is performed.
 */
public class ConnectivityScreenOffTestActivity extends PassFailButtons.Activity {

    private static final String TAG = ConnectivityScreenOffTestActivity.class.getSimpleName();
    private static final String V6CONN_URL = "https://ipv6.google.com/generate_204";
    private static final String V6ADDR_URL = "https://google-ipv6test.appspot.com/ip.js?fmt=text";

    private static final long MIN_SCREEN_OFF_MS = 1000 * (30 + (long) new Random().nextInt(51));
    private static final long MIN_POWER_DISCONNECT_MS = MIN_SCREEN_OFF_MS;

    private final Object mLock;
    private final AppState mState;
    private BackgroundTestingThread mTestingThread;

    private final ScreenAndPlugStateReceiver mReceiver;
    private final IntentFilter mIntentFilter;
    private boolean mWaitForPowerDisconnected;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;
    private ConnectivityManager mCM;
    private NetworkCallback mNetworkCallback;

    private ScrollView mScrollView;
    private TextView mTextView;
    private long mUserActivityTimeout = -1;


    public ConnectivityScreenOffTestActivity() {
        mLock = new Object();
        mState = new AppState();

        mReceiver = new ScreenAndPlugStateReceiver();

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(Intent.ACTION_SCREEN_ON);
        mIntentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        mIntentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureFromSystemServices();
        setupUserInterface();
    }

    @Override
    protected void onDestroy() {
        clearNetworkCallback();
        stopAnyExistingTestingThread();
        unregisterReceiver(mReceiver);
        mWakeLock.release();
        super.onDestroy();
    }

    private void setupUserInterface() {
        setContentView(R.layout.network_screen_off);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(
                R.string.network_screen_off_test,
                R.string.network_screen_off_test_instructions,
                -1);

        mScrollView = (ScrollView) findViewById(R.id.scroll);
        mTextView = (TextView) findViewById(R.id.text);
        mTextView.setTypeface(Typeface.MONOSPACE);
        mTextView.setTextSize(14.0f);

        // Get the start button and attach the listener.
        getStartButton().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getStartButton().setEnabled(false);
                startTest();
            }
        });
    }

    private void configureFromSystemServices() {
        final Intent batteryInfo = registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // Whether or not this device (currently) has a battery.
        mWaitForPowerDisconnected =
                batteryInfo.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) && !isLeanback();

        // Check if the device is already on battery power.
        if (mWaitForPowerDisconnected) {
            BatteryManager battMgr = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (!battMgr.isCharging()) {
                mState.setPowerDisconnected();
            }
        }

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        registerReceiver(mReceiver, mIntentFilter);

        mCM = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    private void clearNetworkCallback() {
        if (mNetworkCallback != null) {
            mCM.unregisterNetworkCallback(mNetworkCallback);
            mNetworkCallback = null;
        }
    }

    private void stopAnyExistingTestingThread() {
        synchronized (mLock) {
            if (mTestingThread != null) {
                // The testing thread will observe this and exit on its own (eventually).
                mTestingThread.setStopped();
            }
        }
    }

    private void setTestPassing() {
        logAndUpdate("Test PASSED!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getPassButton().setEnabled(true);
            }
        });
    }

    private void logAndUpdate(final String msg) {
        Log.d(TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.append(msg);
                mTextView.append("\n");
                mScrollView.fullScroll(View.FOCUS_DOWN);  // Scroll to bottom
            }
        });
    }

    private Button getStartButton() {
        return (Button) findViewById(R.id.start_btn);
    }

    private void setUserActivityTimeout(long timeout) {
        final LayoutParams params = getWindow().getAttributes();

        try {
            final Field field = params.getClass().getField("userActivityTimeout");
            // Save the original value.
            if (mUserActivityTimeout < 0) {
                mUserActivityTimeout = field.getLong(params);
                Log.d(TAG, "saving userActivityTimeout: " + mUserActivityTimeout);
            }
            field.setLong(params, 1);
        } catch (NoSuchFieldException e) {
            Log.d(TAG, "No luck with userActivityTimeout: ", e);
            return;
        } catch (IllegalAccessException e) {
            Log.d(TAG, "No luck with userActivityTimeout: ", e);
            return;
        }

        getWindow().setAttributes(params);
    }

    private void tryScreenOff() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setUserActivityTimeout(1);
            }
        });
    }

    private void tryScreenOn() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                PowerManager.WakeLock screenOnLock = mPowerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK
                        | PowerManager.ACQUIRE_CAUSES_WAKEUP
                        | PowerManager.ON_AFTER_RELEASE, TAG + ":screenOn");
                screenOnLock.acquire();
                setUserActivityTimeout((mUserActivityTimeout > 0)
                        ? mUserActivityTimeout
                        : 30);  // No good value to restore, use 30 seconds.
                screenOnLock.release();
            }
        });
    }

    private void startTest() {
        clearNetworkCallback();
        stopAnyExistingTestingThread();
        mTextView.setText("");
        logAndUpdate("Starting test...");

        mCM.registerNetworkCallback(
                new NetworkRequest.Builder()
                        .addTransportType(TRANSPORT_WIFI)
                        .addCapability(NET_CAPABILITY_INTERNET)
                        .build(),
                createNetworkCallback());

        new BackgroundTestingThread().start();
    }

    /**
     * TODO(ek): Evaluate reworking the code roughly as follows:
     *     - Move all the shared state here, including mWaitForPowerDisconnected
     *       (and mTestingThread).
     *     - Move from synchronizing on mLock to synchronizing on this since the
     *       AppState object is final, and delete mLock.
     *     - Synchronize the methods below, and add some required new methods.
     *     - Remove copying entire state into the BackgroundTestingThread.
     */
    class AppState {
        Network mNetwork;
        LinkProperties mLinkProperties;
        long mScreenOffTime;
        long mPowerDisconnectTime;
        boolean mPassedInitialIPv6Check;

        void setNetwork(Network network) {
            mNetwork = network;
            mLinkProperties = null;
            mPassedInitialIPv6Check = false;
        }

        void setScreenOn() { mScreenOffTime = 0; }
        void setScreenOff() { mScreenOffTime = SystemClock.elapsedRealtime(); }
        boolean validScreenStateForTesting() { return (mScreenOffTime > 0); }

        void setPowerConnected() { mPowerDisconnectTime = 0; }
        void setPowerDisconnected() { mPowerDisconnectTime = SystemClock.elapsedRealtime(); }
        boolean validPowerStateForTesting() {
            return !mWaitForPowerDisconnected || (mPowerDisconnectTime > 0);
        }
    }

    class ScreenAndPlugStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.d(TAG, "got ACTION_SCREEN_ON");
                synchronized (mLock) {
                    mState.setScreenOn();
                    mLock.notify();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.d(TAG, "got ACTION_SCREEN_OFF");
                synchronized (mLock) {
                    mState.setScreenOff();
                    mLock.notify();
                }
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                Log.d(TAG, "got ACTION_POWER_CONNECTED");
                synchronized (mLock) {
                    mState.setPowerConnected();
                    mLock.notify();
                }
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                Log.d(TAG, "got ACTION_POWER_DISCONNECTED");
                synchronized (mLock) {
                    mState.setPowerDisconnected();
                    mLock.notify();
                }
            }
        }
    }

    private NetworkCallback createNetworkCallback() {
        return new NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                synchronized (mLock) {
                    mState.setNetwork(network);
                    mLock.notify();
                }
            }

            @Override
            public void onLost(Network network) {
                synchronized (mLock) {
                    if (network.equals(mState.mNetwork)) {
                        mState.setNetwork(null);
                        mLock.notify();
                    }
                }
            }

            @Override
            public void onLinkPropertiesChanged(Network network, LinkProperties newLp) {
                synchronized (mLock) {
                    if (network.equals(mState.mNetwork)) {
                        mState.mLinkProperties = newLp;
                        mLock.notify();
                    }
                }
            }
        };
    }

    private class BackgroundTestingThread extends Thread {
        final int POLLING_INTERVAL_MS = 5000;
        final int CONNECTIVITY_CHECKING_INTERVAL_MS = 1000 + 100 * (new Random().nextInt(20));
        final int MAX_CONNECTIVITY_CHECKS = 3;
        final AppState localState = new AppState();
        final AtomicBoolean isRunning = new AtomicBoolean(false);
        int numConnectivityChecks = 0;
        int numConnectivityChecksPassing = 0;

        @Override
        public void run() {
            Log.d(TAG, getId() + " started");

            maybeWaitForPreviousThread();

            try {
                mainLoop();
            } finally {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getStartButton().setEnabled(true);
                    }
                });
                tryScreenOn();
            }

            synchronized (mLock) { mTestingThread = null; }

            Log.d(TAG, getId() + " exiting");
        }

        private void mainLoop() {
            int nextSleepDurationMs = 0;

            while (stillRunning()) {
                awaitNotification(nextSleepDurationMs);
                if (!stillRunning()) { break; }
                nextSleepDurationMs = POLLING_INTERVAL_MS;

                if (localState.mNetwork == null) {
                    logAndUpdate("waiting for available network");
                    continue;
                }

                if (localState.mLinkProperties == null) {
                    synchronized (mLock) {
                        mState.mLinkProperties = mCM.getLinkProperties(mState.mNetwork);
                        dupStateLocked();
                    }
                }

                if (!localState.mPassedInitialIPv6Check) {
                    if (!hasBasicIPv6Connectivity()) {
                        logAndUpdate("waiting for basic IPv6 connectivity");
                        continue;
                    }
                    synchronized (mLock) {
                        mState.mPassedInitialIPv6Check = true;
                    }
                }

                if (!localState.validPowerStateForTesting()) {
                    resetConnectivityCheckStatistics();
                    logAndUpdate("waiting for ACTION_POWER_DISCONNECTED");
                    continue;
                }

                if (!localState.validScreenStateForTesting()) {
                    resetConnectivityCheckStatistics();
                    tryScreenOff();
                    logAndUpdate("waiting for ACTION_SCREEN_OFF");
                    continue;
                }

                if (mWaitForPowerDisconnected) {
                    final long delta = SystemClock.elapsedRealtime() - localState.mPowerDisconnectTime;
                    if (delta < MIN_POWER_DISCONNECT_MS) {
                        nextSleepDurationMs = (int) (MIN_POWER_DISCONNECT_MS - delta);
                        // Not a lot of point in going to sleep for fewer than 500ms.
                        if (nextSleepDurationMs > 500) {
                            Log.d(TAG, "waiting for power to be disconnected for at least "
                                    + MIN_POWER_DISCONNECT_MS + "ms, "
                                    + nextSleepDurationMs + "ms left.");
                            continue;
                        }
                    }
                }

                final long delta = SystemClock.elapsedRealtime() - localState.mScreenOffTime;
                if (delta < MIN_SCREEN_OFF_MS) {
                    nextSleepDurationMs = (int) (MIN_SCREEN_OFF_MS - delta);
                    // Not a lot of point in going to sleep for fewer than 500ms.
                    if (nextSleepDurationMs > 500) {
                        Log.d(TAG, "waiting for screen to be off for at least "
                                + MIN_SCREEN_OFF_MS + "ms, "
                                + nextSleepDurationMs + "ms left.");
                        continue;
                    }
                }

                numConnectivityChecksPassing += hasGlobalIPv6Connectivity() ? 1 : 0;
                numConnectivityChecks++;
                if (numConnectivityChecks >= MAX_CONNECTIVITY_CHECKS) {
                    break;
                }
                nextSleepDurationMs = CONNECTIVITY_CHECKING_INTERVAL_MS;
            }

            if (!stillRunning()) { return; }

            // We require that 100% of IPv6 HTTPS queries succeed.
            if (numConnectivityChecksPassing == MAX_CONNECTIVITY_CHECKS) {
                setTestPassing();
            } else {
                logAndUpdate("Test FAILED with score: "
                        + numConnectivityChecksPassing + "/" + MAX_CONNECTIVITY_CHECKS);
            }
        }

        private boolean stillRunning() {
            return isRunning.get();
        }

        public void setStopped() {
            isRunning.set(false);
        }

        private void maybeWaitForPreviousThread() {
            BackgroundTestingThread previousThread;
            synchronized (mLock) {
                previousThread = mTestingThread;
            }

            if (previousThread != null) {
                previousThread.setStopped();
                try {
                    previousThread.join();
                } catch (InterruptedException ignored) {}
            }

            synchronized (mLock) {
                if (mTestingThread == null || mTestingThread == previousThread) {
                    mTestingThread = this;
                    isRunning.set(true);
                }
            }
        }

        private void dupStateLocked() {
            localState.mNetwork = mState.mNetwork;
            localState.mLinkProperties = mState.mLinkProperties;
            localState.mScreenOffTime = mState.mScreenOffTime;
            localState.mPowerDisconnectTime = mState.mPowerDisconnectTime;
            localState.mPassedInitialIPv6Check = mState.mPassedInitialIPv6Check;
        }

        private void awaitNotification(int timeoutMs) {
            synchronized (mLock) {
                if (timeoutMs > 0) {
                    try {
                        mLock.wait(timeoutMs);
                    } catch (InterruptedException e) {}
                }
                dupStateLocked();
            }
        }

        private void resetConnectivityCheckStatistics() {
            numConnectivityChecks = 0;
            numConnectivityChecksPassing = 0;
        }

        boolean hasBasicIPv6Connectivity() {
            final HttpResult result = getHttpResource(localState.mNetwork, V6CONN_URL, true);
            if (result.rcode != 204) {
                if (result.msg != null && !result.msg.isEmpty()) {
                    logAndUpdate(result.msg);
                }
                return false;
            }
            return true;
        }

        boolean hasGlobalIPv6Connectivity() {
            final boolean doClose = ((numConnectivityChecks % 2) == 0);
            final HttpResult result = getHttpResource(localState.mNetwork, V6ADDR_URL, doClose);
            if (result.rcode != 200) {
                if (result.msg != null && !result.msg.isEmpty()) {
                    logAndUpdate(result.msg);
                }
                return false;
            }

            InetAddress reflectedIp;
            try {
                // TODO: replace with Os.inet_pton().
                reflectedIp = InetAddress.getByName(result.msg);
            } catch (UnknownHostException e) {
                logAndUpdate("Failed to parse '" + result.msg + "' as an IP address");
                return false;
            }
            if (!(reflectedIp instanceof Inet6Address)) {
                logAndUpdate(reflectedIp.getHostAddress() + " is not a valid IPv6 address");
                return false;
            }

            for (LinkAddress linkAddr : localState.mLinkProperties.getLinkAddresses()) {
                if (linkAddr.getAddress().equals(reflectedIp)) {
                    logAndUpdate("Found reflected IP " + linkAddr.getAddress().getHostAddress());
                    return true;
                }
            }

            logAndUpdate("Link IP addresses do not include: " + reflectedIp.getHostAddress());
            return false;
        }
    }

    private static class HttpResult {
        public final int rcode;
        public final String msg;

        public HttpResult(int rcode, String msg) {
            this.rcode = rcode;
            this.msg = msg;
        }
    }

    private static HttpResult getHttpResource(
            final Network network, final String url, boolean doClose) {
        int rcode = -1;
        String msg = null;

        try {
            final HttpURLConnection conn =
                    (HttpURLConnection) network.openConnection(new URL(url));
            conn.setConnectTimeout(10 * 1000);
            conn.setReadTimeout(10 * 1000);
            if (doClose) { conn.setRequestProperty("connection", "close"); }
            rcode = conn.getResponseCode();
            if (rcode >= 200 && rcode <= 299) {
                msg = new BufferedReader(new InputStreamReader(conn.getInputStream())).readLine();
            }
            if (doClose) { conn.disconnect(); }  // try not to have reusable sessions
        } catch (IOException e) {
            msg = "HTTP GET of '" + url + "' encountered " + e;
        }

        return new HttpResult(rcode, msg);
    }

    private boolean isLeanback() {
        final PackageManager pm = this.getPackageManager();
        return (pm != null && pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK));
    }
}
