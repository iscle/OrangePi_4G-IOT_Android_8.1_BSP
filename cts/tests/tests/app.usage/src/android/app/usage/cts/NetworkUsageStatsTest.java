/**
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package android.app.usage.cts;

import android.app.AppOpsManager;
import android.app.usage.NetworkStatsManager;
import android.app.usage.NetworkStats;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Scanner;
import java.net.HttpURLConnection;

import libcore.io.IoUtils;
import libcore.io.Streams;

public class NetworkUsageStatsTest extends InstrumentationTestCase {
    private static final String LOG_TAG = "NetworkUsageStatsTest";
    private static final String APPOPS_SET_SHELL_COMMAND = "appops set {0} {1} {2}";
    private static final String APPOPS_GET_SHELL_COMMAND = "appops get {0} {1}";

    private static final long MINUTE = 1000 * 60;
    private static final int TIMEOUT_MILLIS = 15000;

    private static final String CHECK_CONNECTIVITY_URL = "http://www.265.com/";
    private static final String CHECK_CALLBACK_URL = CHECK_CONNECTIVITY_URL;

    private static final int NETWORK_TAG = 0xf00d;
    private static final long THRESHOLD_BYTES = 2 * 1024 * 1024;  // 2 MB

    private interface NetworkInterfaceToTest {
        int getNetworkType();
        int getTransportType();
        boolean getMetered();
        void setMetered(boolean metered);
        String getSystemFeature();
        String getErrorMessage();
    }

    private static final NetworkInterfaceToTest[] sNetworkInterfacesToTest =
            new NetworkInterfaceToTest[] {
                    new NetworkInterfaceToTest() {
                        private boolean metered = false;

                        @Override
                        public int getNetworkType() {
                            return ConnectivityManager.TYPE_WIFI;
                        }

                        @Override
                        public int getTransportType() {
                            return NetworkCapabilities.TRANSPORT_WIFI;
                        }

                        @Override
                        public boolean getMetered() {
                            return metered;
                        }

                        @Override
                        public void setMetered(boolean metered) {
                            this.metered = metered;
                        }

                        @Override
                        public String getSystemFeature() {
                            return PackageManager.FEATURE_WIFI;
                        }

                        @Override
                        public String getErrorMessage() {
                            return " Please make sure you are connected to a WiFi access point.";
                        }
                    },
                    new NetworkInterfaceToTest() {
                        private boolean metered = false;
                        @Override
                        public int getNetworkType() {
                            return ConnectivityManager.TYPE_MOBILE;
                        }

                        @Override
                        public int getTransportType() {
                            return NetworkCapabilities.TRANSPORT_CELLULAR;
                        }

                        @Override
                        public boolean getMetered() {
                            return metered;
                        }

                        @Override
                        public void setMetered(boolean metered) {
                            this.metered = metered;
                        }
                        @Override
                        public String getSystemFeature() {
                            return PackageManager.FEATURE_TELEPHONY;
                        }

                        @Override
                        public String getErrorMessage() {
                            return " Please make sure you have added a SIM card with data plan to" +
                                    " your phone, have enabled data over cellular and in case of" +
                                    " dual SIM devices, have selected the right SIM " +
                                    "for data connection.";
                        }
                    }
    };

    private NetworkStatsManager mNsm;
    private ConnectivityManager mCm;
    private PackageManager mPm;
    private long mStartTime;
    private long mEndTime;

    private long mBytesRead;
    private String mWriteSettingsMode;
    private String mUsageStatsMode;

    private void exerciseRemoteHost(Network network, URL url) throws Exception {
        NetworkInfo networkInfo = mCm.getNetworkInfo(network);
        if (networkInfo == null) {
            Log.w(LOG_TAG, "Network info is null");
        } else {
            Log.w(LOG_TAG, "Network: " + networkInfo.toString());
        }
        InputStreamReader in = null;
        HttpURLConnection urlc = null;
        String originalKeepAlive = System.getProperty("http.keepAlive");
        System.setProperty("http.keepAlive", "false");
        try {
            TrafficStats.setThreadStatsTag(NETWORK_TAG);
            urlc = (HttpURLConnection) network.openConnection(url);
            urlc.setConnectTimeout(TIMEOUT_MILLIS);
            urlc.setUseCaches(false);
            urlc.connect();
            boolean ping = urlc.getResponseCode() == 200;
            if (ping) {
                in = new InputStreamReader(
                        (InputStream) urlc.getContent());

                mBytesRead = 0;
                while (in.read() != -1) ++mBytesRead;
            }
        } catch (Exception e) {
            Log.i(LOG_TAG, "Badness during exercising remote server: " + e);
        } finally {
            TrafficStats.clearThreadStatsTag();
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // don't care
                }
            }
            if (urlc != null) {
                urlc.disconnect();
            }
            if (originalKeepAlive == null) {
                System.clearProperty("http.keepAlive");
            } else {
                System.setProperty("http.keepAlive", originalKeepAlive);
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mNsm = (NetworkStatsManager) getInstrumentation().getContext()
                .getSystemService(Context.NETWORK_STATS_SERVICE);

        mCm = (ConnectivityManager) getInstrumentation().getContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        mPm = getInstrumentation().getContext().getPackageManager();

        mWriteSettingsMode = getAppOpsMode(AppOpsManager.OPSTR_WRITE_SETTINGS);
        setAppOpsMode(AppOpsManager.OPSTR_WRITE_SETTINGS, "allow");
        mUsageStatsMode = getAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mWriteSettingsMode != null) {
            setAppOpsMode(AppOpsManager.OPSTR_WRITE_SETTINGS, mWriteSettingsMode);
        }
        if (mUsageStatsMode != null) {
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, mUsageStatsMode);
        }
        super.tearDown();
    }

    private void setAppOpsMode(String appop, String mode) throws Exception {
        final String command = MessageFormat.format(APPOPS_SET_SHELL_COMMAND,
                getInstrumentation().getContext().getPackageName(), appop, mode);
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try {
            Streams.readFully(new FileInputStream(pfd.getFileDescriptor()));
        } finally {
            IoUtils.closeQuietly(pfd.getFileDescriptor());
        }
    }

    private String getAppOpsMode(String appop) throws Exception {
        String result;
        final String command = MessageFormat.format(APPOPS_GET_SHELL_COMMAND,
                getInstrumentation().getContext().getPackageName(), appop);
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        try {
            result = convertStreamToString(new FileInputStream(pfd.getFileDescriptor()));
        } finally {
            IoUtils.closeQuietly(pfd.getFileDescriptor());
        }
        if (result == null) {
            Log.w(LOG_TAG, "App op " + appop + " could not be read.");
        }
        return result;
    }

    private static String convertStreamToString(InputStream is) {
        try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : null;
        }
    }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        private long mTolerance;
        private URL mUrl;
        public boolean success;
        public boolean metered;

        NetworkCallback(long tolerance, URL url) {
            mTolerance = tolerance;
            mUrl = url;
            success = false;
            metered = false;
        }

        @Override
        public void onAvailable(Network network) {
            try {
                mStartTime = System.currentTimeMillis() - mTolerance;
                exerciseRemoteHost(network, mUrl);
                mEndTime = System.currentTimeMillis() + mTolerance;
                success = true;
                metered = !mCm.getNetworkCapabilities(network)
                        .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
                synchronized(NetworkUsageStatsTest.this) {
                    NetworkUsageStatsTest.this.notify();
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, "exercising remote host failed.", e);
                success = false;
            }
        }
    }

    private boolean shouldTestThisNetworkType(int networkTypeIndex, final long tolerance)
            throws Exception {
        boolean hasFeature = mPm.hasSystemFeature(
                sNetworkInterfacesToTest[networkTypeIndex].getSystemFeature());
        if (!hasFeature) {
            return false;
        }
        NetworkCallback callback = new NetworkCallback(tolerance, new URL(CHECK_CONNECTIVITY_URL));
        mCm.requestNetwork(new NetworkRequest.Builder()
                .addTransportType(sNetworkInterfacesToTest[networkTypeIndex].getTransportType())
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(), callback);
        synchronized(this) {
            try {
                wait((int)(TIMEOUT_MILLIS * 1.2));
            } catch (InterruptedException e) {
            }
        }
        if (callback.success) {
            sNetworkInterfacesToTest[networkTypeIndex].setMetered(callback.metered);
            return true;
        }

        // This will always fail at this point as we know 'hasFeature' is true.
        assertFalse (sNetworkInterfacesToTest[networkTypeIndex].getSystemFeature() +
                " is a reported system feature, " +
                "however no corresponding connected network interface was found or the attempt " +
                "to connect has timed out (timeout = " + TIMEOUT_MILLIS + "ms)." +
                sNetworkInterfacesToTest[networkTypeIndex].getErrorMessage(), hasFeature);
        return false;
    }

    private String getSubscriberId(int networkIndex) {
        int networkType = sNetworkInterfacesToTest[networkIndex].getNetworkType();
        if (ConnectivityManager.TYPE_MOBILE == networkType) {
            TelephonyManager tm = (TelephonyManager) getInstrumentation().getContext()
                    .getSystemService(Context.TELEPHONY_SERVICE);
            return tm.getSubscriberId();
        }
        return "";
    }

    public void testDeviceSummary() throws Exception {
        for (int i = 0; i < sNetworkInterfacesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i, MINUTE/2)) {
                continue;
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");
            NetworkStats.Bucket bucket = null;
            try {
                bucket = mNsm.querySummaryForDevice(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
            } catch (RemoteException | SecurityException e) {
                fail("testDeviceSummary fails with exception: " + e.toString());
            }
            assertNotNull(bucket);
            assertTimestamps(bucket);
            assertEquals(bucket.getState(), NetworkStats.Bucket.STATE_ALL);
            assertEquals(bucket.getUid(), NetworkStats.Bucket.UID_ALL);
            assertEquals(bucket.getMetered(), NetworkStats.Bucket.METERED_ALL);
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "deny");
            try {
                bucket = mNsm.querySummaryForDevice(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
                fail("negative testDeviceSummary fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testDeviceSummary fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testUserSummary() throws Exception {
        for (int i = 0; i < sNetworkInterfacesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i, MINUTE/2)) {
                continue;
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");
            NetworkStats.Bucket bucket = null;
            try {
                bucket = mNsm.querySummaryForUser(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
            } catch (RemoteException | SecurityException e) {
                fail("testUserSummary fails with exception: " + e.toString());
            }
            assertNotNull(bucket);
            assertTimestamps(bucket);
            assertEquals(bucket.getState(), NetworkStats.Bucket.STATE_ALL);
            assertEquals(bucket.getUid(), NetworkStats.Bucket.UID_ALL);
            assertEquals(bucket.getMetered(), NetworkStats.Bucket.METERED_ALL);
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "deny");
            try {
                bucket = mNsm.querySummaryForUser(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
                fail("negative testUserSummary fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testUserSummary fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testAppSummary() throws Exception {
        for (int i = 0; i < sNetworkInterfacesToTest.length; ++i) {
            if (!shouldTestThisNetworkType(i, MINUTE/2)) {
                continue;
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");
            NetworkStats result = null;
            try {
                result = mNsm.querySummary(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
                assertTrue(result != null);
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                long totalTxPackets = 0;
                long totalRxPackets = 0;
                long totalTxBytes = 0;
                long totalRxBytes = 0;
                boolean hasCorrectMetering = false;
                int expectedMetering = sNetworkInterfacesToTest[i].getMetered() ?
                        NetworkStats.Bucket.METERED_YES : NetworkStats.Bucket.METERED_NO;
                while (result.hasNextBucket()) {
                    assertTrue(result.getNextBucket(bucket));
                    assertTimestamps(bucket);
                    hasCorrectMetering |= bucket.getMetered() == expectedMetering;
                    if (bucket.getUid() == Process.myUid()) {
                        totalTxPackets += bucket.getTxPackets();
                        totalRxPackets += bucket.getRxPackets();
                        totalTxBytes += bucket.getTxBytes();
                        totalRxBytes += bucket.getRxBytes();
                    }
                }
                assertFalse(result.getNextBucket(bucket));
                assertTrue("Incorrect metering for NetworkType: " +
                        sNetworkInterfacesToTest[i].getNetworkType(), hasCorrectMetering);
                assertTrue("No Rx bytes usage for uid " + Process.myUid(), totalRxBytes > 0);
                assertTrue("No Rx packets usage for uid " + Process.myUid(), totalRxPackets > 0);
                assertTrue("No Tx bytes usage for uid " + Process.myUid(), totalTxBytes > 0);
                assertTrue("No Tx packets usage for uid " + Process.myUid(), totalTxPackets > 0);
            } catch (RemoteException | SecurityException e) {
                fail("testAppSummary fails with exception: " + e.toString());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "deny");
            try {
                result = mNsm.querySummary(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
                fail("negative testAppSummary fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testAppSummary fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testAppDetails() throws Exception {
        for (int i = 0; i < sNetworkInterfacesToTest.length; ++i) {
            // Relatively large tolerance to accommodate for history bucket size.
            if (!shouldTestThisNetworkType(i, MINUTE * 120)) {
                continue;
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");
            NetworkStats result = null;
            try {
                result = mNsm.queryDetails(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
                assertTrue(result != null);
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                long totalTxPackets = 0;
                long totalRxPackets = 0;
                long totalTxBytes = 0;
                long totalRxBytes = 0;
                while (result.hasNextBucket()) {
                    assertTrue(result.getNextBucket(bucket));
                    assertTimestamps(bucket);
                    assertEquals(bucket.getState(), NetworkStats.Bucket.STATE_ALL);
                    assertEquals(bucket.getMetered(), NetworkStats.Bucket.METERED_ALL);
                    if (bucket.getUid() == Process.myUid()) {
                        totalTxPackets += bucket.getTxPackets();
                        totalRxPackets += bucket.getRxPackets();
                        totalTxBytes += bucket.getTxBytes();
                        totalRxBytes += bucket.getRxBytes();
                    }
                }
                assertFalse(result.getNextBucket(bucket));
                assertTrue("No Rx bytes usage for uid " + Process.myUid(), totalRxBytes > 0);
                assertTrue("No Rx packets usage for uid " + Process.myUid(), totalRxPackets > 0);
                assertTrue("No Tx bytes usage for uid " + Process.myUid(), totalTxBytes > 0);
                assertTrue("No Tx packets usage for uid " + Process.myUid(), totalTxPackets > 0);
            } catch (RemoteException | SecurityException e) {
                fail("testAppDetails fails with exception: " + e.toString());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "deny");
            try {
                result = mNsm.queryDetails(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime);
                fail("negative testAppDetails fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testAppDetails fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testUidDetails() throws Exception {
        for (int i = 0; i < sNetworkInterfacesToTest.length; ++i) {
            // Relatively large tolerance to accommodate for history bucket size.
            if (!shouldTestThisNetworkType(i, MINUTE * 120)) {
                continue;
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");
            NetworkStats result = null;
            try {
                result = mNsm.queryDetailsForUid(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime, Process.myUid());
                assertTrue(result != null);
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                long totalTxPackets = 0;
                long totalRxPackets = 0;
                long totalTxBytes = 0;
                long totalRxBytes = 0;
                while (result.hasNextBucket()) {
                    assertTrue(result.getNextBucket(bucket));
                    assertTimestamps(bucket);
                    assertEquals(bucket.getState(), NetworkStats.Bucket.STATE_ALL);
                    assertEquals(bucket.getMetered(), NetworkStats.Bucket.METERED_ALL);
                    assertEquals(bucket.getUid(), Process.myUid());
                    totalTxPackets += bucket.getTxPackets();
                    totalRxPackets += bucket.getRxPackets();
                    totalTxBytes += bucket.getTxBytes();
                    totalRxBytes += bucket.getRxBytes();
                }
                assertFalse(result.getNextBucket(bucket));
                assertTrue("No Rx bytes usage for uid " + Process.myUid(), totalRxBytes > 0);
                assertTrue("No Rx packets usage for uid " + Process.myUid(), totalRxPackets > 0);
                assertTrue("No Tx bytes usage for uid " + Process.myUid(), totalTxBytes > 0);
                assertTrue("No Tx packets usage for uid " + Process.myUid(), totalTxPackets > 0);
            } catch (RemoteException | SecurityException e) {
                fail("testUidDetails fails with exception: " + e.toString());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "deny");
            try {
                result = mNsm.queryDetailsForUid(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime, Process.myUid());
                fail("negative testUidDetails fails: no exception thrown.");
            } catch (RemoteException e) {
                fail("testUidDetails fails with exception: " + e.toString());
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testTagDetails() throws Exception {
        for (int i = 0; i < sNetworkInterfacesToTest.length; ++i) {
            // Relatively large tolerance to accommodate for history bucket size.
            if (!shouldTestThisNetworkType(i, MINUTE * 120)) {
                continue;
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");
            NetworkStats result = null;
            try {
                result = mNsm.queryDetailsForUidTag(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime, Process.myUid(), NETWORK_TAG);
                assertTrue(result != null);
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                long totalTxPackets = 0;
                long totalRxPackets = 0;
                long totalTxBytes = 0;
                long totalRxBytes = 0;
                while (result.hasNextBucket()) {
                    assertTrue(result.getNextBucket(bucket));
                    assertTimestamps(bucket);
                    assertEquals(bucket.getState(), NetworkStats.Bucket.STATE_ALL);
                    assertEquals(bucket.getMetered(), NetworkStats.Bucket.METERED_ALL);
                    assertEquals(bucket.getUid(), Process.myUid());
                    if (bucket.getTag() == NETWORK_TAG) {
                        totalTxPackets += bucket.getTxPackets();
                        totalRxPackets += bucket.getRxPackets();
                        totalTxBytes += bucket.getTxBytes();
                        totalRxBytes += bucket.getRxBytes();
                    }
                }
                assertTrue("No Rx bytes tagged with 0x" + Integer.toHexString(NETWORK_TAG)
                        + " for uid " + Process.myUid(), totalRxBytes > 0);
                assertTrue("No Rx packets tagged with " + Integer.toHexString(NETWORK_TAG)
                        + " for uid " + Process.myUid(), totalRxPackets > 0);
                assertTrue("No Tx bytes tagged with 0x" + Integer.toHexString(NETWORK_TAG)
                        + " for uid " + Process.myUid(), totalTxBytes > 0);
                assertTrue("No Tx packets tagged with 0x" + Integer.toHexString(NETWORK_TAG)
                        + " for uid " + Process.myUid(), totalTxPackets > 0);
            } catch (SecurityException e) {
                fail("testUidDetails fails with exception: " + e.toString());
            } finally {
                if (result != null) {
                    result.close();
                }
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "deny");
            try {
                result = mNsm.queryDetailsForUidTag(
                        sNetworkInterfacesToTest[i].getNetworkType(), getSubscriberId(i),
                        mStartTime, mEndTime, Process.myUid(), NETWORK_TAG);
                fail("negative testUidDetails fails: no exception thrown.");
            } catch (SecurityException e) {
                // expected outcome
            }
        }
    }

    public void testCallback() throws Exception {
        for (int i = 0; i < sNetworkInterfacesToTest.length; ++i) {
            // Relatively large tolerance to accommodate for history bucket size.
            if (!shouldTestThisNetworkType(i, MINUTE/2)) {
                continue;
            }
            setAppOpsMode(AppOpsManager.OPSTR_GET_USAGE_STATS, "allow");

            TestUsageCallback usageCallback = new TestUsageCallback();
            HandlerThread thread = new HandlerThread("callback-thread");
            thread.start();
            Handler handler = new Handler(thread.getLooper());
            mNsm.registerUsageCallback(sNetworkInterfacesToTest[i].getNetworkType(),
                    getSubscriberId(i), THRESHOLD_BYTES, usageCallback, handler);

            // TODO: Force traffic and check whether the callback is invoked.
            // Right now the test only covers whether the callback can be registered, but not
            // whether it is invoked upon data usage since we don't have a scalable way of
            // storing files of >2MB in CTS.

            mNsm.unregisterUsageCallback(usageCallback);
        }
    }

    private void assertTimestamps(final NetworkStats.Bucket bucket) {
        assertTrue("Start timestamp " + bucket.getStartTimeStamp() + " is less than " +
                mStartTime, bucket.getStartTimeStamp() >= mStartTime);
        assertTrue("End timestamp " + bucket.getEndTimeStamp() + " is greater than " +
                mEndTime, bucket.getEndTimeStamp() <= mEndTime);
    }

    private static class TestUsageCallback extends NetworkStatsManager.UsageCallback {
        @Override
        public void onThresholdReached(int networkType, String subscriberId) {
            Log.v(LOG_TAG, "Called onThresholdReached for networkType=" + networkType
                    + " subscriberId=" + subscriberId);
        }
    }
}
