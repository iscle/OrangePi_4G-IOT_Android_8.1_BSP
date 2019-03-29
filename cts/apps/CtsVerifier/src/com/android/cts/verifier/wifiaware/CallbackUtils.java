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

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareSession;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Blocking callbacks for Wi-Fi Aware and Connectivity Manager.
 */
public class CallbackUtils {
    private static final String TAG = "CallbackUtils";

    public static final int CALLBACK_TIMEOUT_SEC = 15;

    /**
     * Utility AttachCallback - provides mechanism to block execution with the
     * waitForAttach method.
     */
    public static class AttachCb extends AttachCallback {
        public static final int TIMEOUT = -1;
        public static final int ON_ATTACHED = 0;
        public static final int ON_ATTACH_FAILED = 1;

        private CountDownLatch mBlocker = new CountDownLatch(1);
        private int mCallback = TIMEOUT;
        private WifiAwareSession mWifiAwareSession = null;

        @Override
        public void onAttached(WifiAwareSession session) {
            mCallback = ON_ATTACHED;
            mWifiAwareSession = session;
            mBlocker.countDown();
        }

        @Override
        public void onAttachFailed() {
            mCallback = ON_ATTACH_FAILED;
            mBlocker.countDown();
        }

        /**
         * Wait (blocks) for any AttachCallback callback or timeout.
         *
         * @return A pair of values: the callback constant (or TIMEOUT) and the WifiAwareSession
         * created when attach successful - null otherwise (attach failure or timeout).
         */
        public Pair<Integer, WifiAwareSession> waitForAttach() throws InterruptedException {
            if (mBlocker.await(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                return new Pair<>(mCallback, mWifiAwareSession);
            }

            return new Pair<>(TIMEOUT, null);
        }
    }

    /**
     * Utility IdentityChangedListener - provides mechanism to block execution with the
     * waitForIdentity method. Single shot listener - only listens for the first triggered
     * callback.
     */
    public static class IdentityListenerSingleShot extends IdentityChangedListener {
        private CountDownLatch mBlocker = new CountDownLatch(1);
        private byte[] mMac = null;

        @Override
        public void onIdentityChanged(byte[] mac) {
            if (mMac != null) {
                return;
            }

            mMac = mac;
            mBlocker.countDown();
        }

        /**
         * Wait (blocks) for the onIdentityChanged callback or a timeout.
         *
         * @return The MAC address returned by the onIdentityChanged() callback, or null on timeout.
         */
        public byte[] waitForMac() throws InterruptedException {
            if (mBlocker.await(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                return mMac;
            }

            return null;
        }
    }

    /**
     * Utility NetworkCallback - provides mechanism for blocking/serializing access with the
     * waitForNetwork method.
     */
    public static class NetworkCb extends ConnectivityManager.NetworkCallback {
        private CountDownLatch mBlocker = new CountDownLatch(1);
        private boolean mNetworkAvailable = false;

        @Override
        public void onAvailable(Network network) {
            mNetworkAvailable = true;
            mBlocker.countDown();
        }

        @Override
        public void onUnavailable() {
            mNetworkAvailable = false;
            mBlocker.countDown();
        }

        /**
         * Wait (blocks) for Available or Unavailable callbacks - or timesout.
         *
         * @return true if Available, false otherwise (Unavailable or timeout).
         */
        public boolean waitForNetwork() throws InterruptedException {
            if (mBlocker.await(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                return mNetworkAvailable;
            }
            return false;
        }
    }

    /**
     * Utility DiscoverySessionCallback - provides mechanism to block/serialize Aware discovery
     * operations using the waitForCallbacks() method.
     */
    public static class DiscoveryCb extends DiscoverySessionCallback {
        public static final int TIMEOUT = -1;
        public static final int ON_PUBLISH_STARTED = 0x1 << 0;
        public static final int ON_SUBSCRIBE_STARTED = 0x1 << 1;
        public static final int ON_SESSION_CONFIG_UPDATED = 0x1 << 2;
        public static final int ON_SESSION_CONFIG_FAILED = 0x1 << 3;
        public static final int ON_SESSION_TERMINATED = 0x1 << 4;
        public static final int ON_SERVICE_DISCOVERED = 0x1 << 5;
        public static final int ON_MESSAGE_SEND_SUCCEEDED = 0x1 << 6;
        public static final int ON_MESSAGE_SEND_FAILED = 0x1 << 7;
        public static final int ON_MESSAGE_RECEIVED = 0x1 << 8;

        /**
         * Data container for all parameters which can be returned by any DiscoverySessionCallback
         * callback.
         */
        public static class CallbackData {
            public CallbackData(int callback) {
                this.callback = callback;
            }

            public int callback;

            public PublishDiscoverySession publishDiscoverySession;
            public SubscribeDiscoverySession subscribeDiscoverySession;
            public PeerHandle peerHandle;
            public byte[] serviceSpecificInfo;
            public List<byte[]> matchFilter;
            public int messageId;
        }

        private CountDownLatch mBlocker = null;
        private int mWaitForCallbackMask = 0;

        private final Object mLock = new Object();
        private ArrayDeque<CallbackData> mCallbackQueue = new ArrayDeque<>();

        private void processCallback(CallbackData callbackData) {
            synchronized (mLock) {
                mCallbackQueue.addLast(callbackData);
                if (mBlocker != null && (mWaitForCallbackMask & callbackData.callback)
                        == callbackData.callback) {
                    mBlocker.countDown();
                }
            }
        }

        private CallbackData getAndRemoveFirst(int callbackMask) {
            synchronized (mLock) {
                for (CallbackData cbd : mCallbackQueue) {
                    if ((cbd.callback & callbackMask) == cbd.callback) {
                        mCallbackQueue.remove(cbd);
                        return cbd;
                    }
                }
            }

            return null;
        }

        private CallbackData waitForCallbacks(int callbackMask, boolean timeout)
                throws InterruptedException {
            synchronized (mLock) {
                CallbackData cbd = getAndRemoveFirst(callbackMask);
                if (cbd != null) {
                    return cbd;
                }

                mWaitForCallbackMask = callbackMask;
                mBlocker = new CountDownLatch(1);
            }

            boolean finishedNormally = true;
            if (timeout) {
                finishedNormally = mBlocker.await(CALLBACK_TIMEOUT_SEC, TimeUnit.SECONDS);
            } else {
                mBlocker.await();
            }
            if (finishedNormally) {
                CallbackData cbd = getAndRemoveFirst(callbackMask);
                if (cbd != null) {
                    return cbd;
                }

                Log.wtf(TAG, "DiscoveryCb.waitForCallback: callbackMask=" + callbackMask
                        + ": did not time-out but doesn't have any of the requested callbacks in "
                        + "the stack!?");
                // falling-through to TIMEOUT
            }

            return new CallbackData(TIMEOUT);
        }

        /**
         * Wait for the specified callbacks - a bitmask of any of the ON_* constants. Returns the
         * CallbackData structure whose CallbackData.callback specifies the callback which was
         * triggered. The callback may be TIMEOUT.
         *
         * Note: other callbacks happening while while waiting for the specified callback(s) will
         * be queued.
         */
        public CallbackData waitForCallbacks(int callbackMask) throws InterruptedException {
            return waitForCallbacks(callbackMask, true);
        }

        /**
         * Wait for the specified callbacks - a bitmask of any of the ON_* constants. Returns the
         * CallbackData structure whose CallbackData.callback specifies the callback which was
         * triggered.
         *
         * This call will not timeout - it can be interrupted though (which results in a thrown
         * exception).
         *
         * Note: other callbacks happening while while waiting for the specified callback(s) will
         * be queued.
         */
        public CallbackData waitForCallbacksNoTimeout(int callbackMask)
                throws InterruptedException {
            return waitForCallbacks(callbackMask, false);
        }

        @Override
        public void onPublishStarted(PublishDiscoverySession session) {
            CallbackData callbackData = new CallbackData(ON_PUBLISH_STARTED);
            callbackData.publishDiscoverySession = session;
            processCallback(callbackData);
        }

        @Override
        public void onSubscribeStarted(SubscribeDiscoverySession session) {
            CallbackData callbackData = new CallbackData(ON_SUBSCRIBE_STARTED);
            callbackData.subscribeDiscoverySession = session;
            processCallback(callbackData);
        }

        @Override
        public void onSessionConfigUpdated() {
            CallbackData callbackData = new CallbackData(ON_SESSION_CONFIG_UPDATED);
            processCallback(callbackData);
        }

        @Override
        public void onSessionConfigFailed() {
            CallbackData callbackData = new CallbackData(ON_SESSION_CONFIG_FAILED);
            processCallback(callbackData);
        }

        @Override
        public void onSessionTerminated() {
            CallbackData callbackData = new CallbackData(ON_SESSION_TERMINATED);
            processCallback(callbackData);
        }

        @Override
        public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
                List<byte[]> matchFilter) {
            CallbackData callbackData = new CallbackData(ON_SERVICE_DISCOVERED);
            callbackData.peerHandle = peerHandle;
            callbackData.serviceSpecificInfo = serviceSpecificInfo;
            callbackData.matchFilter = matchFilter;
            processCallback(callbackData);
        }

        @Override
        public void onMessageSendSucceeded(int messageId) {
            CallbackData callbackData = new CallbackData(ON_MESSAGE_SEND_SUCCEEDED);
            callbackData.messageId = messageId;
            processCallback(callbackData);
        }

        @Override
        public void onMessageSendFailed(int messageId) {
            CallbackData callbackData = new CallbackData(ON_MESSAGE_SEND_FAILED);
            callbackData.messageId = messageId;
            processCallback(callbackData);
        }

        @Override
        public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
            CallbackData callbackData = new CallbackData(ON_MESSAGE_RECEIVED);
            callbackData.peerHandle = peerHandle;
            callbackData.serviceSpecificInfo = message;
            processCallback(callbackData);
        }
    }
}
