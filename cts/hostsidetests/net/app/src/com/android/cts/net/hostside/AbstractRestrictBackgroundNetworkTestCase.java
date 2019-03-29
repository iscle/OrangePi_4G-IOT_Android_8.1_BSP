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

package com.android.cts.net.hostside;

import static android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import com.android.cts.net.hostside.INetworkStateObserver;

/**
 * Superclass for tests related to background network restrictions.
 */
abstract class AbstractRestrictBackgroundNetworkTestCase extends InstrumentationTestCase {
    protected static final String TAG = "RestrictBackgroundNetworkTests";

    protected static final String TEST_PKG = "com.android.cts.net.hostside";
    protected static final String TEST_APP2_PKG = "com.android.cts.net.hostside.app2";

    private static final String TEST_APP2_ACTIVITY_CLASS = TEST_APP2_PKG + ".MyActivity";
    private static final String TEST_APP2_SERVICE_CLASS = TEST_APP2_PKG + ".MyForegroundService";

    private static final int SLEEP_TIME_SEC = 1;
    private static final boolean DEBUG = true;

    // Constants below must match values defined on app2's Common.java
    private static final String MANIFEST_RECEIVER = "ManifestReceiver";
    private static final String DYNAMIC_RECEIVER = "DynamicReceiver";

    private static final String ACTION_RECEIVER_READY =
            "com.android.cts.net.hostside.app2.action.RECEIVER_READY";
    static final String ACTION_SHOW_TOAST =
            "com.android.cts.net.hostside.app2.action.SHOW_TOAST";

    protected static final String NOTIFICATION_TYPE_CONTENT = "CONTENT";
    protected static final String NOTIFICATION_TYPE_DELETE = "DELETE";
    protected static final String NOTIFICATION_TYPE_FULL_SCREEN = "FULL_SCREEN";
    protected static final String NOTIFICATION_TYPE_BUNDLE = "BUNDLE";
    protected static final String NOTIFICATION_TYPE_ACTION = "ACTION";
    protected static final String NOTIFICATION_TYPE_ACTION_BUNDLE = "ACTION_BUNDLE";
    protected static final String NOTIFICATION_TYPE_ACTION_REMOTE_INPUT = "ACTION_REMOTE_INPUT";


    private static final String NETWORK_STATUS_SEPARATOR = "\\|";
    private static final int SECOND_IN_MS = 1000;
    static final int NETWORK_TIMEOUT_MS = 15 * SECOND_IN_MS;
    private static final int PROCESS_STATE_FOREGROUND_SERVICE = 4;
    private static final int PROCESS_STATE_TOP = 2;

    private static final String KEY_NETWORK_STATE_OBSERVER = TEST_PKG + ".observer";

    protected static final int TYPE_COMPONENT_ACTIVTIY = 0;
    protected static final int TYPE_COMPONENT_FOREGROUND_SERVICE = 1;

    private static final int BATTERY_STATE_TIMEOUT_MS = 5000;
    private static final int BATTERY_STATE_CHECK_INTERVAL_MS = 500;

    private static final int FOREGROUND_PROC_NETWORK_TIMEOUT_MS = 6000;

    // Must be higher than NETWORK_TIMEOUT_MS
    private static final int ORDERED_BROADCAST_TIMEOUT_MS = NETWORK_TIMEOUT_MS * 4;

    private static final IntentFilter BATTERY_CHANGED_FILTER =
            new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

    private static final String APP_NOT_FOREGROUND_ERROR = "app_not_fg";

    protected Context mContext;
    protected Instrumentation mInstrumentation;
    protected ConnectivityManager mCm;
    protected WifiManager mWfm;
    protected int mUid;
    private int mMyUid;
    private String mMeteredWifi;
    private MyServiceClient mServiceClient;
    private String mDeviceIdleConstantsSetting;
    private boolean mSupported;
    private boolean mIsLocationOn;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWfm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mUid = getUid(TEST_APP2_PKG);
        mMyUid = getUid(mContext.getPackageName());
        mServiceClient = new MyServiceClient(mContext);
        mServiceClient.bind();
        mDeviceIdleConstantsSetting = "device_idle_constants";
        mIsLocationOn = isLocationOn();
        if (!mIsLocationOn) {
            enableLocation();
        }
        mSupported = setUpActiveNetworkMeteringState();

        Log.i(TAG, "Apps status on " + getName() + ":\n"
                + "\ttest app: uid=" + mMyUid + ", state=" + getProcessStateByUid(mMyUid) + "\n"
                + "\tapp2: uid=" + mUid + ", state=" + getProcessStateByUid(mUid));
        executeShellCommand("settings get global app_idle_constants");
   }

    @Override
    protected void tearDown() throws Exception {
        if (!mIsLocationOn) {
            disableLocation();
        }
        mServiceClient.unbind();

        super.tearDown();
    }

    private void enableLocation() throws Exception {
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_SENSORS_ONLY);
        assertEquals(Settings.Secure.LOCATION_MODE_SENSORS_ONLY,
                Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.LOCATION_MODE));
    }

    private void disableLocation() throws Exception {
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF);
        assertEquals(Settings.Secure.LOCATION_MODE_OFF,
                Settings.Secure.getInt(mContext.getContentResolver(),
                        Settings.Secure.LOCATION_MODE));
    }

    private boolean isLocationOn() throws Exception {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.LOCATION_MODE) != Settings.Secure.LOCATION_MODE_OFF;
    }

    protected int getUid(String packageName) throws Exception {
        return mContext.getPackageManager().getPackageUid(packageName, 0);
    }

    protected void assertRestrictBackgroundChangedReceived(int expectedCount) throws Exception {
        assertRestrictBackgroundChangedReceived(DYNAMIC_RECEIVER, expectedCount);
        assertRestrictBackgroundChangedReceived(MANIFEST_RECEIVER, 0);
    }

    protected void assertRestrictBackgroundChangedReceived(String receiverName, int expectedCount)
            throws Exception {
        int attempts = 0;
        int count = 0;
        final int maxAttempts = 5;
        do {
            attempts++;
            count = getNumberBroadcastsReceived(receiverName, ACTION_RESTRICT_BACKGROUND_CHANGED);
            if (count == expectedCount) {
                break;
            }
            Log.d(TAG, "Expecting count " + expectedCount + " but actual is " + count + " after "
                    + attempts + " attempts; sleeping "
                    + SLEEP_TIME_SEC + " seconds before trying again");
            SystemClock.sleep(SLEEP_TIME_SEC * SECOND_IN_MS);
        } while (attempts <= maxAttempts);
        assertEquals("Number of expected broadcasts for " + receiverName + " not reached after "
                + maxAttempts * SLEEP_TIME_SEC + " seconds", expectedCount, count);
    }

    protected String sendOrderedBroadcast(Intent intent) throws Exception {
        return sendOrderedBroadcast(intent, ORDERED_BROADCAST_TIMEOUT_MS);
    }

    protected String sendOrderedBroadcast(Intent intent, int timeoutMs) throws Exception {
        final LinkedBlockingQueue<String> result = new LinkedBlockingQueue<>(1);
        Log.d(TAG, "Sending ordered broadcast: " + intent);
        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String resultData = getResultData();
                if (resultData == null) {
                    Log.e(TAG, "Received null data from ordered intent");
                    return;
                }
                result.offer(resultData);
            }
        }, null, 0, null, null);

        final String resultData = result.poll(timeoutMs, TimeUnit.MILLISECONDS);
        Log.d(TAG, "Ordered broadcast response after " + timeoutMs + "ms: " + resultData );
        return resultData;
    }

    protected int getNumberBroadcastsReceived(String receiverName, String action) throws Exception {
        return mServiceClient.getCounters(receiverName, action);
    }

    protected void assertRestrictBackgroundStatus(int expectedStatus) throws Exception {
        final String status = mServiceClient.getRestrictBackgroundStatus();
        assertNotNull("didn't get API status from app2", status);
        final String actualStatus = toString(Integer.parseInt(status));
        assertEquals("wrong status", toString(expectedStatus), actualStatus);
    }

    protected void assertMyRestrictBackgroundStatus(int expectedStatus) throws Exception {
        final int actualStatus = mCm.getRestrictBackgroundStatus();
        assertEquals("Wrong status", toString(expectedStatus), toString(actualStatus));
    }

    protected boolean isMyRestrictBackgroundStatus(int expectedStatus) throws Exception {
        final int actualStatus = mCm.getRestrictBackgroundStatus();
        if (expectedStatus != actualStatus) {
            Log.d(TAG, "Expected: " + toString(expectedStatus)
                    + " but actual: " + toString(actualStatus));
            return false;
        }
        return true;
    }

    protected void assertBackgroundNetworkAccess(boolean expectAllowed) throws Exception {
        assertBackgroundState(); // Sanity check.
        assertNetworkAccess(expectAllowed);
    }

    protected void assertForegroundNetworkAccess() throws Exception {
        assertForegroundState(); // Sanity check.
        assertNetworkAccess(true);
    }

    protected void assertForegroundServiceNetworkAccess() throws Exception {
        assertForegroundServiceState(); // Sanity check.
        assertNetworkAccess(true);
    }

    /**
     * Whether this device suport this type of test.
     *
     * <p>Should be overridden when necessary (but always calling
     * {@code super.isSupported()} first), and explicitly used before each test
     * Example:
     *
     * <pre><code>
     * public void testSomething() {
     *    if (!isSupported()) return;
     * </code></pre>
     *
     * @return {@code true} by default.
     */
    protected boolean isSupported() throws Exception {
        return mSupported;
    }

    /**
     * Asserts that an app always have access while on foreground or running a foreground service.
     *
     * <p>This method will launch an activity and a foreground service to make the assertion, but
     * will finish the activity / stop the service afterwards.
     */
    protected void assertsForegroundAlwaysHasNetworkAccess() throws Exception{
        // Checks foreground first.
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_ACTIVTIY);
        finishActivity();

        // Then foreground service
        launchComponentAndAssertNetworkAccess(TYPE_COMPONENT_FOREGROUND_SERVICE);
        stopForegroundService();
    }

    protected final void assertBackgroundState() throws Exception {
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            state = getProcessStateByUid(mUid);
            Log.v(TAG, "assertBackgroundState(): status for app2 (" + mUid + ") on attempt #" + i
                    + ": " + state);
            if (isBackground(state.state)) {
                return;
            }
            Log.d(TAG, "App not on background state (" + state + ") on attempt #" + i
                    + "; sleeping 1s before trying again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("App2 is not on background state after " + maxTries + " attempts: " + state );
    }

    protected final void assertForegroundState() throws Exception {
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            state = getProcessStateByUid(mUid);
            Log.v(TAG, "assertForegroundState(): status for app2 (" + mUid + ") on attempt #" + i
                    + ": " + state);
            if (!isBackground(state.state)) {
                return;
            }
            Log.d(TAG, "App not on foreground state on attempt #" + i
                    + "; sleeping 1s before trying again");
            turnScreenOn();
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("App2 is not on foreground state after " + maxTries + " attempts: " + state );
    }

    protected final void assertForegroundServiceState() throws Exception {
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            state = getProcessStateByUid(mUid);
            Log.v(TAG, "assertForegroundServiceState(): status for app2 (" + mUid + ") on attempt #"
                    + i + ": " + state);
            if (state.state == PROCESS_STATE_FOREGROUND_SERVICE) {
                return;
            }
            Log.d(TAG, "App not on foreground service state on attempt #" + i
                    + "; sleeping 1s before trying again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("App2 is not on foreground service state after " + maxTries + " attempts: " + state );
    }

    /**
     * Returns whether an app state should be considered "background" for restriction purposes.
     */
    protected boolean isBackground(int state) {
        return state > PROCESS_STATE_FOREGROUND_SERVICE;
    }

    /**
     * Asserts whether the active network is available or not.
     */
    private void assertNetworkAccess(boolean expectAvailable) throws Exception {
        final int maxTries = 5;
        String error = null;
        int timeoutMs = 500;

        for (int i = 1; i <= maxTries; i++) {
            error = checkNetworkAccess(expectAvailable);

            if (error.isEmpty()) return;

            // TODO: ideally, it should retry only when it cannot connect to an external site,
            // or no retry at all! But, currently, the initial change fails almost always on
            // battery saver tests because the netd changes are made asynchronously.
            // Once b/27803922 is fixed, this retry mechanism should be revisited.

            Log.w(TAG, "Network status didn't match for expectAvailable=" + expectAvailable
                    + " on attempt #" + i + ": " + error + "\n"
                    + "Sleeping " + timeoutMs + "ms before trying again");
            // No sleep after the last turn
            if (i < maxTries) {
                SystemClock.sleep(timeoutMs);
            }
            // Exponential back-off.
            timeoutMs = Math.min(timeoutMs*2, NETWORK_TIMEOUT_MS);
        }
        dumpOnFailure();
        fail("Invalid state for expectAvailable=" + expectAvailable + " after " + maxTries
                + " attempts.\nLast error: " + error);
    }

    private void dumpOnFailure() throws Exception {
        dumpAllNetworkRules();
        Log.d(TAG, "Usagestats dump: " + getUsageStatsDump());
        executeShellCommand("settings get global app_idle_constants");
    }

    private void dumpAllNetworkRules() throws Exception {
        final String networkManagementDump = runShellCommand(mInstrumentation,
                "dumpsys network_management").trim();
        final String networkPolicyDump = runShellCommand(mInstrumentation,
                "dumpsys netpolicy").trim();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter('\n');
        splitter.setString(networkManagementDump);
        String next;
        Log.d(TAG, ">>> Begin network_management dump");
        while (splitter.hasNext()) {
            next = splitter.next();
            Log.d(TAG, next);
        }
        Log.d(TAG, "<<< End network_management dump");
        splitter.setString(networkPolicyDump);
        Log.d(TAG, ">>> Begin netpolicy dump");
        while (splitter.hasNext()) {
            next = splitter.next();
            Log.d(TAG, next);
        }
        Log.d(TAG, "<<< End netpolicy dump");
    }

    /**
     * Checks whether the network is available as expected.
     *
     * @return error message with the mismatch (or empty if assertion passed).
     */
    private String checkNetworkAccess(boolean expectAvailable) throws Exception {
        final String resultData = mServiceClient.checkNetworkStatus();
        return checkForAvailabilityInResultData(resultData, expectAvailable);
    }

    private String checkForAvailabilityInResultData(String resultData, boolean expectAvailable) {
        if (resultData == null) {
            assertNotNull("Network status from app2 is null", resultData);
        }
        // Network status format is described on MyBroadcastReceiver.checkNetworkStatus()
        final String[] parts = resultData.split(NETWORK_STATUS_SEPARATOR);
        assertEquals("Wrong network status: " + resultData, 5, parts.length); // Sanity check
        final State state = parts[0].equals("null") ? null : State.valueOf(parts[0]);
        final DetailedState detailedState = parts[1].equals("null")
                ? null : DetailedState.valueOf(parts[1]);
        final boolean connected = Boolean.valueOf(parts[2]);
        final String connectionCheckDetails = parts[3];
        final String networkInfo = parts[4];

        final StringBuilder errors = new StringBuilder();
        final State expectedState;
        final DetailedState expectedDetailedState;
        if (expectAvailable) {
            expectedState = State.CONNECTED;
            expectedDetailedState = DetailedState.CONNECTED;
        } else {
            expectedState = State.DISCONNECTED;
            expectedDetailedState = DetailedState.BLOCKED;
        }

        if (expectAvailable != connected) {
            errors.append(String.format("External site connection failed: expected %s, got %s\n",
                    expectAvailable, connected));
        }
        if (expectedState != state || expectedDetailedState != detailedState) {
            errors.append(String.format("Connection state mismatch: expected %s/%s, got %s/%s\n",
                    expectedState, expectedDetailedState, state, detailedState));
        }

        if (errors.length() > 0) {
            errors.append("\tnetworkInfo: " + networkInfo + "\n");
            errors.append("\tconnectionCheckDetails: " + connectionCheckDetails + "\n");
        }
        return errors.toString();
    }

    protected String executeShellCommand(String command) throws Exception {
        final String result = runShellCommand(mInstrumentation, command).trim();
        if (DEBUG) Log.d(TAG, "Command '" + command + "' returned '" + result + "'");
        return result;
    }

    /**
     * Runs a Shell command which is not expected to generate output.
     */
    protected void executeSilentShellCommand(String command) throws Exception {
        final String result = executeShellCommand(command);
        assertTrue("Command '" + command + "' failed: " + result, result.trim().isEmpty());
    }

    /**
     * Asserts the result of a command, wait and re-running it a couple times if necessary.
     */
    protected void assertDelayedShellCommand(String command, final String expectedResult)
            throws Exception {
        assertDelayedShellCommand(command, 5, 1, expectedResult);
    }

    protected void assertDelayedShellCommand(String command, int maxTries, int napTimeSeconds,
            final String expectedResult) throws Exception {
        assertDelayedShellCommand(command, maxTries, napTimeSeconds, new ExpectResultChecker() {

            @Override
            public boolean isExpected(String result) {
                return expectedResult.equals(result);
            }

            @Override
            public String getExpected() {
                return expectedResult;
            }
        });
    }

    protected void assertDelayedShellCommand(String command, ExpectResultChecker checker)
            throws Exception {
        assertDelayedShellCommand(command, 5, 1, checker);
    }
    protected void assertDelayedShellCommand(String command, int maxTries, int napTimeSeconds,
            ExpectResultChecker checker) throws Exception {
        String result = "";
        for (int i = 1; i <= maxTries; i++) {
            result = executeShellCommand(command).trim();
            if (checker.isExpected(result)) return;
            Log.v(TAG, "Command '" + command + "' returned '" + result + " instead of '"
                    + checker.getExpected() + "' on attempt #" + i
                    + "; sleeping " + napTimeSeconds + "s before trying again");
            SystemClock.sleep(napTimeSeconds * SECOND_IN_MS);
        }
        fail("Command '" + command + "' did not return '" + checker.getExpected() + "' after "
                + maxTries
                + " attempts. Last result: '" + result + "'");
    }

    /**
     * Sets the initial metering state for the active network.
     *
     * <p>It's called on setup and by default does nothing - it's up to the
     * subclasses to override.
     *
     * @return whether the tests in the subclass are supported on this device.
     */
    protected boolean setUpActiveNetworkMeteringState() throws Exception {
        return true;
    }

    /**
     * Makes sure the active network is not metered.
     *
     * <p>If the device does not supoprt un-metered networks (for example if it
     * only has cellular data but not wi-fi), it should return {@code false};
     * otherwise, it should return {@code true} (or fail if the un-metered
     * network could not be set).
     *
     * @return {@code true} if the network is now unmetered.
     */
    protected boolean setUnmeteredNetwork() throws Exception {
        final NetworkInfo info = mCm.getActiveNetworkInfo();
        assertNotNull("Could not get active network", info);
        if (!mCm.isActiveNetworkMetered()) {
            Log.d(TAG, "Active network is not metered: " + info);
        } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
            Log.i(TAG, "Setting active WI-FI network as not metered: " + info );
            setWifiMeteredStatus(false);
        } else {
            Log.d(TAG, "Active network cannot be set to un-metered: " + info);
            return false;
        }
        assertActiveNetworkMetered(false); // Sanity check.
        return true;
    }

    /**
     * Enables metering on the active network if supported.
     *
     * <p>If the device does not support metered networks it should return
     * {@code false}; otherwise, it should return {@code true} (or fail if the
     * metered network could not be set).
     *
     * @return {@code true} if the network is now metered.
     */
    protected boolean setMeteredNetwork() throws Exception {
        final NetworkInfo info = mCm.getActiveNetworkInfo();
        final boolean metered = mCm.isActiveNetworkMetered();
        if (metered) {
            Log.d(TAG, "Active network already metered: " + info);
            return true;
        } else if (info.getType() != ConnectivityManager.TYPE_WIFI) {
            Log.w(TAG, "Active network does not support metering: " + info);
            return false;
        } else {
            Log.w(TAG, "Active network not metered: " + info);
        }
        final String netId = setWifiMeteredStatus(true);

        // Set flag so status is reverted on resetMeteredNetwork();
        mMeteredWifi = netId;
        // Sanity check.
        assertWifiMeteredStatus(netId, true);
        assertActiveNetworkMetered(true);
        return true;
    }

    /**
     * Resets the device metering state to what it was before the test started.
     *
     * <p>This reverts any metering changes made by {@code setMeteredNetwork}.
     */
    protected void resetMeteredNetwork() throws Exception {
        if (mMeteredWifi != null) {
            Log.i(TAG, "resetMeteredNetwork(): SID '" + mMeteredWifi
                    + "' was set as metered by test case; resetting it");
            setWifiMeteredStatus(mMeteredWifi, false);
            assertActiveNetworkMetered(false); // Sanity check.
        }
    }

    private void assertActiveNetworkMetered(boolean expected) throws Exception {
        final int maxTries = 5;
        NetworkInfo info = null;
        for (int i = 1; i <= maxTries; i++) {
            info = mCm.getActiveNetworkInfo();
            if (info == null) {
                Log.v(TAG, "No active network info on attempt #" + i
                        + "; sleeping 1s before polling again");
            } else if (mCm.isActiveNetworkMetered() != expected) {
                Log.v(TAG, "Wrong metered status for active network " + info + "; expected="
                        + expected + "; sleeping 1s before polling again");
            } else {
                break;
            }
            Thread.sleep(SECOND_IN_MS);
        }
        assertNotNull("No active network after " + maxTries + " attempts", info);
        assertEquals("Wrong metered status for active network " + info, expected,
                mCm.isActiveNetworkMetered());
    }

    private String setWifiMeteredStatus(boolean metered) throws Exception {
        // We could call setWifiEnabled() here, but it might take sometime to be in a consistent
        // state (for example, if one of the saved network is not properly authenticated), so it's
        // better to let the hostside test take care of that.
        assertTrue("wi-fi is disabled", mWfm.isWifiEnabled());
        // TODO: if it's not guaranteed the device has wi-fi, we need to change the tests
        // to make the actual verification of restrictions optional.
        final String ssid = mWfm.getConnectionInfo().getSSID();
        return setWifiMeteredStatus(ssid, metered);
    }

    private String setWifiMeteredStatus(String ssid, boolean metered) throws Exception {
        assertNotNull("null SSID", ssid);
        final String netId = ssid.trim().replaceAll("\"", ""); // remove quotes, if any.
        assertFalse("empty SSID", ssid.isEmpty());

        Log.i(TAG, "Setting wi-fi network " + netId + " metered status to " + metered);
        final String setCommand = "cmd netpolicy set metered-network " + netId + " " + metered;
        assertDelayedShellCommand(setCommand, "");

        return netId;
    }

    private void assertWifiMeteredStatus(String netId, boolean status) throws Exception {
        final String command = "cmd netpolicy list wifi-networks";
        final String expectedLine = netId + ";" + status;
        assertDelayedShellCommand(command, new ExpectResultChecker() {

            @Override
            public boolean isExpected(String result) {
                return result.contains(expectedLine);
            }

            @Override
            public String getExpected() {
                return "line containing " + expectedLine;
            }
        });
    }

    protected void setRestrictBackground(boolean enabled) throws Exception {
        executeShellCommand("cmd netpolicy set restrict-background " + enabled);
        final String output = executeShellCommand("cmd netpolicy get restrict-background ");
        final String expectedSuffix = enabled ? "enabled" : "disabled";
        // TODO: use MoreAsserts?
        assertTrue("output '" + output + "' should end with '" + expectedSuffix + "'",
                output.endsWith(expectedSuffix));
      }

    protected void addRestrictBackgroundWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy add restrict-background-whitelist " + uid);
        assertRestrictBackgroundWhitelist(uid, true);
        // UID policies live by the Highlander rule: "There can be only one".
        // Hence, if app is whitelisted, it should not be blacklisted.
        assertRestrictBackgroundBlacklist(uid, false);
    }

    protected void removeRestrictBackgroundWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy remove restrict-background-whitelist " + uid);
        assertRestrictBackgroundWhitelist(uid, false);
    }

    protected void assertRestrictBackgroundWhitelist(int uid, boolean expected) throws Exception {
        assertRestrictBackground("restrict-background-whitelist", uid, expected);
    }

    protected void addRestrictBackgroundBlacklist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy add restrict-background-blacklist " + uid);
        assertRestrictBackgroundBlacklist(uid, true);
        // UID policies live by the Highlander rule: "There can be only one".
        // Hence, if app is blacklisted, it should not be whitelisted.
        assertRestrictBackgroundWhitelist(uid, false);
    }

    protected void removeRestrictBackgroundBlacklist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy remove restrict-background-blacklist " + uid);
        assertRestrictBackgroundBlacklist(uid, false);
    }

    protected void assertRestrictBackgroundBlacklist(int uid, boolean expected) throws Exception {
        assertRestrictBackground("restrict-background-blacklist", uid, expected);
    }

    private void assertRestrictBackground(String list, int uid, boolean expected) throws Exception {
        final int maxTries = 5;
        boolean actual = false;
        final String expectedUid = Integer.toString(uid);
        String uids = "";
        for (int i = 1; i <= maxTries; i++) {
            final String output =
                    executeShellCommand("cmd netpolicy list " + list);
            uids = output.split(":")[1];
            for (String candidate : uids.split(" ")) {
                actual = candidate.trim().equals(expectedUid);
                if (expected == actual) {
                    return;
                }
            }
            Log.v(TAG, list + " check for uid " + uid + " doesn't match yet (expected "
                    + expected + ", got " + actual + "); sleeping 1s before polling again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail(list + " check for uid " + uid + " failed: expected " + expected + ", got " + actual
                + ". Full list: " + uids);
    }

    protected void assertPowerSaveModeWhitelist(String packageName, boolean expected)
            throws Exception {
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        assertDelayedShellCommand("dumpsys deviceidle whitelist =" + packageName,
                Boolean.toString(expected));
    }

    protected void addPowerSaveModeWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Adding package " + packageName + " to power-save-mode whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle whitelist +" + packageName);
        assertPowerSaveModeWhitelist(packageName, true); // Sanity check
    }

    protected void removePowerSaveModeWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Removing package " + packageName + " from power-save-mode whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle whitelist -" + packageName);
        assertPowerSaveModeWhitelist(packageName, false); // Sanity check
    }

    protected void assertPowerSaveModeExceptIdleWhitelist(String packageName, boolean expected)
            throws Exception {
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        assertDelayedShellCommand("dumpsys deviceidle except-idle-whitelist =" + packageName,
                Boolean.toString(expected));
    }

    protected void addPowerSaveModeExceptIdleWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Adding package " + packageName + " to power-save-mode-except-idle whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle except-idle-whitelist +" + packageName);
        assertPowerSaveModeExceptIdleWhitelist(packageName, true); // Sanity check
    }

    protected void removePowerSaveModeExceptIdleWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Removing package " + packageName
                + " from power-save-mode-except-idle whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle except-idle-whitelist reset");
        assertPowerSaveModeExceptIdleWhitelist(packageName, false); // Sanity check
    }

    protected void turnBatteryOff() throws Exception {
        executeSilentShellCommand("cmd battery unplug");
        assertBatteryState(false);
    }

    protected void turnBatteryOn() throws Exception {
        executeSilentShellCommand("cmd battery reset");
        assertBatteryState(true);

    }

    private void assertBatteryState(boolean pluggedIn) throws Exception {
        final long endTime = SystemClock.elapsedRealtime() + BATTERY_STATE_TIMEOUT_MS;
        while (isDevicePluggedIn() != pluggedIn && SystemClock.elapsedRealtime() <= endTime) {
            Thread.sleep(BATTERY_STATE_CHECK_INTERVAL_MS);
        }
        if (isDevicePluggedIn() != pluggedIn) {
            fail("Timed out waiting for the plugged-in state to change,"
                    + " expected pluggedIn: " + pluggedIn);
        }
    }

    private boolean isDevicePluggedIn() {
        final Intent batteryIntent = mContext.registerReceiver(null, BATTERY_CHANGED_FILTER);
        return batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) > 0;
    }

    protected void turnScreenOff() throws Exception {
        executeSilentShellCommand("input keyevent KEYCODE_SLEEP");
    }

    protected void turnScreenOn() throws Exception {
        executeSilentShellCommand("input keyevent KEYCODE_WAKEUP");
        executeSilentShellCommand("wm dismiss-keyguard");
    }

    protected void setBatterySaverMode(boolean enabled) throws Exception {
        Log.i(TAG, "Setting Battery Saver Mode to " + enabled);
        if (enabled) {
            turnBatteryOff();
            executeSilentShellCommand("cmd power set-mode 1");
        } else {
            executeSilentShellCommand("cmd power set-mode 0");
            turnBatteryOn();
        }
    }

    protected void setDozeMode(boolean enabled) throws Exception {
        // Sanity check, since tests should check beforehand....
        assertTrue("Device does not support Doze Mode", isDozeModeEnabled());

        Log.i(TAG, "Setting Doze Mode to " + enabled);
        if (enabled) {
            turnBatteryOff();
            turnScreenOff();
            executeShellCommand("dumpsys deviceidle force-idle deep");
        } else {
            turnScreenOn();
            turnBatteryOn();
            executeShellCommand("dumpsys deviceidle unforce");
        }
        // Sanity check.
        assertDozeMode(enabled);
    }

    protected void assertDozeMode(boolean enabled) throws Exception {
        assertDelayedShellCommand("dumpsys deviceidle get deep", enabled ? "IDLE" : "ACTIVE");
    }

    protected boolean isDozeModeEnabled() throws Exception {
        final String result = executeShellCommand("cmd deviceidle enabled deep").trim();
        return result.equals("1");
    }

    protected void setAppIdle(boolean enabled) throws Exception {
        Log.i(TAG, "Setting app idle to " + enabled);
        executeSilentShellCommand("am set-inactive " + TEST_APP2_PKG + " " + enabled );
        assertAppIdle(enabled); // Sanity check
    }

    private String getUsageStatsDump() throws Exception {
        final String output = runShellCommand(mInstrumentation, "dumpsys usagestats").trim();
        final StringBuilder sb = new StringBuilder();
        final TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter('\n');
        splitter.setString(output);
        String str;
        while (splitter.hasNext()) {
            str = splitter.next();
            if (str.contains("package=")
                    && !str.contains(TEST_PKG) && !str.contains(TEST_APP2_PKG)) {
                continue;
            }
            if (str.trim().startsWith("config=") || str.trim().startsWith("time=")) {
                continue;
            }
            sb.append(str).append('\n');
        }
        return sb.toString();
    }

    protected void assertAppIdle(boolean enabled) throws Exception {
        try {
            assertDelayedShellCommand("am get-inactive " + TEST_APP2_PKG, 15, 2, "Idle=" + enabled);
        } catch (Throwable e) {
            Log.d(TAG, "UsageStats dump:\n" + getUsageStatsDump());
            executeShellCommand("settings get global app_idle_constants");
            throw e;
        }
    }

    /**
     * Starts a service that will register a broadcast receiver to receive
     * {@code RESTRICT_BACKGROUND_CHANGE} intents.
     * <p>
     * The service must run in a separate app because otherwise it would be killed every time
     * {@link #runDeviceTests(String, String)} is executed.
     */
    protected void registerBroadcastReceiver() throws Exception {
        mServiceClient.registerBroadcastReceiver();

        final Intent intent = new Intent(ACTION_RECEIVER_READY)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        // Wait until receiver is ready.
        final int maxTries = 10;
        for (int i = 1; i <= maxTries; i++) {
            final String message = sendOrderedBroadcast(intent, SECOND_IN_MS * 4);
            Log.d(TAG, "app2 receiver acked: " + message);
            if (message != null) {
                return;
            }
            Log.v(TAG, "app2 receiver is not ready yet; sleeping 1s before polling again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("app2 receiver is not ready");
    }

    /**
     * Registers a {@link NotificationListenerService} implementation that will execute the
     * notification actions right after the notification is sent.
     */
    protected void registerNotificationListenerService() throws Exception {
        executeShellCommand("cmd notification allow_listener "
                + MyNotificationListenerService.getId());
        final NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        final ComponentName listenerComponent = MyNotificationListenerService.getComponentName();
        assertTrue(listenerComponent + " has not been granted access",
                nm.isNotificationListenerAccessGranted(listenerComponent));
    }

    protected void setPendingIntentWhitelistDuration(int durationMs) throws Exception {
        executeSilentShellCommand(String.format(
                "settings put global %s %s=%d", mDeviceIdleConstantsSetting,
                "notification_whitelist_duration", durationMs));
    }

    protected void resetDeviceIdleSettings() throws Exception {
        executeShellCommand(String.format("settings delete global %s",
                mDeviceIdleConstantsSetting));
    }

    protected void launchComponentAndAssertNetworkAccess(int type) throws Exception {
        if (type == TYPE_COMPONENT_FOREGROUND_SERVICE) {
            startForegroundService();
            assertForegroundServiceNetworkAccess();
            return;
        } else if (type == TYPE_COMPONENT_ACTIVTIY) {
            turnScreenOn();
            // Wait for screen-on state to propagate through the system.
            SystemClock.sleep(2000);
            final CountDownLatch latch = new CountDownLatch(1);
            final Intent launchIntent = getIntentForComponent(type);
            final Bundle extras = new Bundle();
            final String[] errors = new String[]{null};
            extras.putBinder(KEY_NETWORK_STATE_OBSERVER, getNewNetworkStateObserver(latch, errors));
            launchIntent.putExtras(extras);
            mContext.startActivity(launchIntent);
            if (latch.await(FOREGROUND_PROC_NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                if (!errors[0].isEmpty()) {
                    if (errors[0] == APP_NOT_FOREGROUND_ERROR) {
                        // App didn't come to foreground when the activity is started, so try again.
                        assertForegroundNetworkAccess();
                    } else {
                        dumpOnFailure();
                        fail("Network is not available for app2 (" + mUid + "): " + errors[0]);
                    }
                }
            } else {
                dumpOnFailure();
                fail("Timed out waiting for network availability status from app2 (" + mUid + ")");
            }
        } else {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private void startForegroundService() throws Exception {
        final Intent launchIntent = getIntentForComponent(TYPE_COMPONENT_FOREGROUND_SERVICE);
        mContext.startForegroundService(launchIntent);
        assertForegroundServiceState();
    }

    private Intent getIntentForComponent(int type) {
        final Intent intent = new Intent();
        if (type == TYPE_COMPONENT_ACTIVTIY) {
            intent.setComponent(new ComponentName(TEST_APP2_PKG, TEST_APP2_ACTIVITY_CLASS));
        } else if (type == TYPE_COMPONENT_FOREGROUND_SERVICE) {
            intent.setComponent(new ComponentName(TEST_APP2_PKG, TEST_APP2_SERVICE_CLASS))
                    .setFlags(1);
        } else {
            fail("Unknown type: " + type);
        }
        return intent;
    }

    protected void stopForegroundService() throws Exception {
        executeShellCommand(String.format("am startservice -f 2 %s/%s",
                TEST_APP2_PKG, TEST_APP2_SERVICE_CLASS));
        // NOTE: cannot assert state because it depends on whether activity was on top before.
    }

    private Binder getNewNetworkStateObserver(final CountDownLatch latch,
            final String[] errors) {
        return new INetworkStateObserver.Stub() {
            @Override
            public boolean isForeground() {
                try {
                    final ProcessState state = getProcessStateByUid(mUid);
                    return !isBackground(state.state);
                } catch (Exception e) {
                    Log.d(TAG, "Error while reading the proc state for " + mUid + ": " + e);
                    return false;
                }
            }

            @Override
            public void onNetworkStateChecked(String resultData) {
                errors[0] = resultData == null
                        ? APP_NOT_FOREGROUND_ERROR
                        : checkForAvailabilityInResultData(resultData, true);
                latch.countDown();
            }
        };
    }

    /**
     * Finishes an activity on app2 so its process is demoted fromforeground status.
     */
    protected void finishActivity() throws Exception {
        executeShellCommand("am broadcast -a "
                + " com.android.cts.net.hostside.app2.action.FINISH_ACTIVITY "
                + "--receiver-foreground --receiver-registered-only");
    }

    protected void sendNotification(int notificationId, String notificationType) throws Exception {
        Log.d(TAG, "Sending notification broadcast (id=" + notificationId
                + ", type=" + notificationType);
        mServiceClient.sendNotification(notificationId, notificationType);
    }

    protected String showToast() {
        final Intent intent = new Intent(ACTION_SHOW_TOAST);
        intent.setPackage(TEST_APP2_PKG);
        Log.d(TAG, "Sending request to show toast");
        try {
            return sendOrderedBroadcast(intent, 3 * SECOND_IN_MS);
        } catch (Exception e) {
            return "";
        }
    }

    private String toString(int status) {
        switch (status) {
            case RESTRICT_BACKGROUND_STATUS_DISABLED:
                return "DISABLED";
            case RESTRICT_BACKGROUND_STATUS_WHITELISTED:
                return "WHITELISTED";
            case RESTRICT_BACKGROUND_STATUS_ENABLED:
                return "ENABLED";
            default:
                return "UNKNOWN_STATUS_" + status;
        }
    }

    private ProcessState getProcessStateByUid(int uid) throws Exception {
        return new ProcessState(executeShellCommand("cmd activity get-uid-state " + uid));
    }

    private static class ProcessState {
        private final String fullState;
        final int state;

        ProcessState(String fullState) {
            this.fullState = fullState;
            try {
                this.state = Integer.parseInt(fullState.split(" ")[0]);
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not parse " + fullState);
            }
        }

        @Override
        public String toString() {
            return fullState;
        }
    }

    /**
     * Helper class used to assert the result of a Shell command.
     */
    protected static interface ExpectResultChecker {
        /**
         * Checkes whether the result of the command matched the expectation.
         */
        boolean isExpected(String result);
        /**
         * Gets the expected result so it's displayed on log and failure messages.
         */
        String getExpected();
    }
}
