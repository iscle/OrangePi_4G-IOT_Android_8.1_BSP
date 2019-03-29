/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.verifier.wifiaware;

import android.content.Context;
import android.content.res.Resources;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Handler;
import android.os.HandlerThread;

import com.android.cts.verifier.R;

/**
 * Base class for all Aware test cases.
 */
public abstract class BaseTestCase {
    protected Context mContext;
    protected Resources mResources;
    protected Listener mListener;

    private Thread mThread;
    private HandlerThread mHandlerThread;
    protected Handler mHandler;

    protected WifiAwareManager mWifiAwareManager;

    public BaseTestCase(Context context) {
        mContext = context;
        mResources = mContext.getResources();
    }

    /**
     * Set up the test case. Executed once before test starts.
     */
    protected void setUp() {
        mWifiAwareManager = (WifiAwareManager) mContext.getSystemService(
                Context.WIFI_AWARE_SERVICE);
    }

    /**
     * Tear down the test case. Executed after test finishes - whether on success or failure.
     */
    protected void tearDown() {
        mWifiAwareManager = null;
    }

    /**
     * Execute test case.
     *
     * @return true on success, false on failure. In case of failure
     */
    protected abstract boolean executeTest() throws InterruptedException;

    /**
     * Returns a String describing the failure reason of the most recent test failure (not valid
     * in other scenarios). Override to customize the failure string.
     */
    protected String getFailureReason() {
        return mContext.getString(R.string.aware_unexpected_error);
    }

    /**
     * Start running the test case.
     *
     * Test case is executed in another thread.
     */
    public void start(Listener listener) {
        mListener = listener;

        stop();
        mHandlerThread = new HandlerThread("CtsVerifier-Aware");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mThread = new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        mListener.onTestStarted();
                        try {
                            setUp();
                        } catch (Exception e) {
                            mListener.onTestFailed(mContext.getString(R.string.aware_setup_error));
                            return;
                        }

                        try {
                            if (executeTest()) {
                                mListener.onTestSuccess();
                            } else {
                                mListener.onTestFailed(getFailureReason());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            mListener.onTestFailed(
                                    mContext.getString(R.string.aware_unexpected_error));
                        } finally {
                            tearDown();
                        }
                    }
                });
        mThread.start();
    }

    /**
     * Stop the currently running test case.
     */
    public void stop() {
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
            mHandler = null;
        }
    }

    /**
     * Listener interface used to communicate the state and status of the test case. It should
     * be implemented by any activity encompassing a test case.
     */
    public interface Listener {
        /**
         * This function is invoked when the test case starts.
         */
        void onTestStarted();

        /**
         * This function is invoked by the test to send a message to listener.
         */
        void onTestMsgReceived(String msg);

        /**
         * This function is invoked when the test finished successfully.
         */
        void onTestSuccess();

        /**
         * This function is invoked when the test failed (test is done).
         */
        void onTestFailed(String reason);
    }

    /**
     * Convert byte array to hex string representation utility.
     */
    public static String bytesToHex(byte[] bytes, Character separator) {
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        boolean useSeparator = separator != null;
        char sep = 0;
        if (useSeparator) {
            sep = separator;
        }
        char[] hexChars = new char[bytes.length * 2 + (useSeparator ? bytes.length - 1 : 0)];
        int base = 0;
        for (int j = 0; j < bytes.length; j++) {
            if (useSeparator && j != 0) {
                hexChars[base++] = sep;
            }
            int v = bytes[j] & 0xFF;
            hexChars[base++] = hexArray[v >> 4];
            hexChars[base++] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
