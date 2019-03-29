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

package com.android.cts.verifier.wifiaware.testcase;

import static com.android.cts.verifier.wifiaware.CallbackUtils.CALLBACK_TIMEOUT_SEC;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareSession;
import android.util.Log;
import android.util.Pair;

import com.android.cts.verifier.R;
import com.android.cts.verifier.wifiaware.BaseTestCase;
import com.android.cts.verifier.wifiaware.CallbackUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test case for data-path, in-band test cases:
 * open/passphrase * solicited/unsolicited * publish/subscribe.
 *
 * Subscribe test sequence:
 * 1. Attach
 *    wait for results (session)
 * 2. Subscribe
 *    wait for results (subscribe session)
 * 3. Wait for discovery
 * 4. Send message
 *    Wait for success
 * 5. Wait for rx message
 * 6. Request network
 *    Wait for network
 * 7. Destroy session
 *
 * Publish test sequence:
 * 1. Attach
 *    wait for results (session)
 * 2. Publish
 *    wait for results (publish session)
 * 3. Wait for rx message
 * 4. Request network
 * 5. Send message
 *    Wait for success
 * 6. Wait for network
 * 7. Destroy session
 */
public class DataPathInBandTestCase extends BaseTestCase {
    private static final String TAG = "DataPathInBandTestCase";
    private static final boolean DBG = true;

    private static final String SERVICE_NAME = "CtsVerifierTestService";
    private static final byte[] MATCH_FILTER_BYTES = "bytes used for matching".getBytes();
    private static final byte[] PUB_SSI = "Extra bytes in the publisher discovery".getBytes();
    private static final byte[] SUB_SSI = "Arbitrary bytes for the subscribe discovery".getBytes();
    private static final byte[] MSG_SUB_TO_PUB = "Let's talk".getBytes();
    private static final byte[] MSG_PUB_TO_SUB = "Ready".getBytes();
    private static final String PASSPHRASE = "Some super secret password";
    private static final int MESSAGE_ID = 1234;

    private boolean mIsSecurityOpen;
    private boolean mIsPublish;
    private boolean mIsUnsolicited;

    private final Object mLock = new Object();

    private String mFailureReason;
    private WifiAwareSession mWifiAwareSession;
    private DiscoverySession mWifiAwareDiscoverySession;

    public DataPathInBandTestCase(Context context, boolean isSecurityOpen, boolean isPublish,
            boolean isUnsolicited) {
        super(context);
        mIsSecurityOpen = isSecurityOpen;
        mIsPublish = isPublish;
        mIsUnsolicited = isUnsolicited;
    }

    @Override
    protected boolean executeTest() throws InterruptedException {
        if (DBG) {
            Log.d(TAG,
                    "executeTest: mIsSecurityOpen=" + mIsSecurityOpen + ", mIsPublish=" + mIsPublish
                            + ", mIsUnsolicited=" + mIsUnsolicited);
        }

        // 1. attach
        CallbackUtils.AttachCb attachCb = new CallbackUtils.AttachCb();
        mWifiAwareManager.attach(attachCb, mHandler);
        Pair<Integer, WifiAwareSession> results = attachCb.waitForAttach();
        switch (results.first) {
            case CallbackUtils.AttachCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_attach_timeout));
                Log.e(TAG, "executeTest: attach TIMEOUT");
                return false;
            case CallbackUtils.AttachCb.ON_ATTACH_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_attach_fail));
                Log.e(TAG, "executeTest: attach ON_ATTACH_FAILED");
                return false;
        }
        mWifiAwareSession = results.second;
        if (mWifiAwareSession == null) {
            setFailureReason(mContext.getString(R.string.aware_status_attach_fail));
            Log.e(TAG, "executeTest: attach callback succeeded but null session returned!?");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_attached));
        if (DBG) {
            Log.d(TAG, "executeTest: attach succeeded");
        }

        if (mIsPublish) {
            return executeTestPublisher();
        } else {
            return executeTestSubscriber();
        }
    }

    private void setFailureReason(String reason) {
        synchronized (mLock) {
            mFailureReason = reason;
        }
    }

    @Override
    protected String getFailureReason() {
        synchronized (mLock) {
            return mFailureReason;
        }
    }

    @Override
    protected void tearDown() {
        if (mWifiAwareDiscoverySession != null) {
            mWifiAwareDiscoverySession.close();
            mWifiAwareDiscoverySession = null;
        }
        if (mWifiAwareSession != null) {
            mWifiAwareSession.close();
            mWifiAwareSession = null;
        }
        super.tearDown();
    }

    private boolean executeTestSubscriber() throws InterruptedException {
        if (DBG) Log.d(TAG, "executeTestSubscriber");
        CallbackUtils.DiscoveryCb discoveryCb = new CallbackUtils.DiscoveryCb();

        // 2. subscribe
        List<byte[]> matchFilter = new ArrayList<>();
        matchFilter.add(MATCH_FILTER_BYTES);
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(
                SERVICE_NAME).setServiceSpecificInfo(SUB_SSI).setMatchFilter(
                matchFilter).setSubscribeType(
                mIsUnsolicited ? SubscribeConfig.SUBSCRIBE_TYPE_PASSIVE
                        : SubscribeConfig.SUBSCRIBE_TYPE_ACTIVE).setTerminateNotificationEnabled(
                true).build();
        if (DBG) Log.d(TAG, "executeTestSubscriber: subscribeConfig=" + subscribeConfig);
        mWifiAwareSession.subscribe(subscribeConfig, discoveryCb, mHandler);

        //    wait for results - subscribe session
        CallbackUtils.DiscoveryCb.CallbackData callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_SUBSCRIBE_STARTED
                        | CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_subscribe_timeout));
                Log.e(TAG, "executeTestSubscriber: subscribe TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_subscribe_failed));
                Log.e(TAG, "executeTestSubscriber: subscribe ON_SESSION_CONFIG_FAILED");
                return false;
        }
        SubscribeDiscoverySession discoverySession = callbackData.subscribeDiscoverySession;
        mWifiAwareDiscoverySession = discoverySession;
        if (discoverySession == null) {
            setFailureReason(mContext.getString(R.string.aware_status_subscribe_null_session));
            Log.e(TAG, "executeTestSubscriber: subscribe succeeded but null session returned");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_subscribe_started));
        if (DBG) Log.d(TAG, "executeTestSubscriber: subscribe succeeded");

        // 3. wait for discovery
        callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_SERVICE_DISCOVERED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_discovery_timeout));
                Log.e(TAG, "executeTestSubscriber: waiting for discovery TIMEOUT");
                return false;
        }
        PeerHandle peerHandle = callbackData.peerHandle;
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_discovery));
        if (DBG) Log.d(TAG, "executeTestSubscriber: discovery");

        //    validate discovery parameters match
        if (!Arrays.equals(PUB_SSI, callbackData.serviceSpecificInfo)) {
            setFailureReason(mContext.getString(R.string.aware_status_discovery_fail));
            Log.e(TAG, "executeTestSubscriber: discovery but SSI mismatch: rx='" + new String(
                    callbackData.serviceSpecificInfo) + "'");
            return false;
        }
        if (callbackData.matchFilter.size() != 1 || !Arrays.equals(MATCH_FILTER_BYTES,
                callbackData.matchFilter.get(0))) {
            setFailureReason(mContext.getString(R.string.aware_status_discovery_fail));
            StringBuffer sb = new StringBuffer();
            sb.append("size=").append(callbackData.matchFilter.size());
            for (byte[] mf: callbackData.matchFilter) {
                sb.append(", e='").append(new String(mf)).append("'");
            }
            Log.e(TAG, "executeTestSubscriber: discovery but matchFilter mismatch: "
                    + sb.toString());
            return false;
        }
        if (peerHandle == null) {
            setFailureReason(mContext.getString(R.string.aware_status_discovery_fail));
            Log.e(TAG, "executeTestSubscriber: discovery but null peerHandle");
            return false;
        }

        // 4. send message & wait for send status
        discoverySession.sendMessage(peerHandle, MESSAGE_ID, MSG_SUB_TO_PUB);
        callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_SUCCEEDED
                        | CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_send_timeout));
                Log.e(TAG, "executeTestSubscriber: send message TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_send_failed));
                Log.e(TAG, "executeTestSubscriber: send message ON_MESSAGE_SEND_FAILED");
                return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_send_success));
        if (DBG) Log.d(TAG, "executeTestSubscriber: send message succeeded");

        if (callbackData.messageId != MESSAGE_ID) {
            setFailureReason(mContext.getString(R.string.aware_status_send_fail_parameter));
            Log.e(TAG, "executeTestSubscriber: send message message ID mismatch: "
                    + callbackData.messageId);
            return false;
        }

        // 5. wait to receive message
        callbackData = discoveryCb.waitForCallbacks(CallbackUtils.DiscoveryCb.ON_MESSAGE_RECEIVED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_receive_timeout));
                Log.e(TAG, "executeTestSubscriber: receive message TIMEOUT");
                return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_received));
        if (DBG) Log.d(TAG, "executeTestSubscriber: received message");

        //    validate that received the expected message
        if (!Arrays.equals(MSG_PUB_TO_SUB, callbackData.serviceSpecificInfo)) {
            setFailureReason(mContext.getString(R.string.aware_status_receive_failure));
            Log.e(TAG, "executeTestSubscriber: receive message message content mismatch: rx='"
                    + new String(callbackData.serviceSpecificInfo) + "'");
            return false;
        }

        // 6. request network
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(
                mIsSecurityOpen ? discoverySession.createNetworkSpecifierOpen(peerHandle)
                        : discoverySession.createNetworkSpecifierPassphrase(peerHandle,
                                PASSPHRASE)).build();
        CallbackUtils.NetworkCb networkCb = new CallbackUtils.NetworkCb();
        cm.requestNetwork(nr, networkCb, CALLBACK_TIMEOUT_SEC * 1000);
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_requested));
        if (DBG) Log.d(TAG, "executeTestSubscriber: requested network");
        boolean networkAvailable = networkCb.waitForNetwork();
        cm.unregisterNetworkCallback(networkCb);
        if (!networkAvailable) {
            setFailureReason(mContext.getString(R.string.aware_status_network_failed));
            Log.e(TAG, "executeTestSubscriber: network request rejected - ON_UNAVAILABLE");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_success));
        if (DBG) Log.d(TAG, "executeTestSubscriber: network request granted - AVAILABLE");

        // 7. destroy session
        discoverySession.close();
        mWifiAwareDiscoverySession = null;

        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_lifecycle_ok));
        return true;
    }

    private boolean executeTestPublisher() throws InterruptedException {
        if (DBG) Log.d(TAG, "executeTestPublisher");
        CallbackUtils.DiscoveryCb discoveryCb = new CallbackUtils.DiscoveryCb();

        // 2. publish
        List<byte[]> matchFilter = new ArrayList<>();
        matchFilter.add(MATCH_FILTER_BYTES);
        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                SERVICE_NAME).setServiceSpecificInfo(PUB_SSI).setMatchFilter(
                matchFilter).setPublishType(mIsUnsolicited ? PublishConfig.PUBLISH_TYPE_UNSOLICITED
                : PublishConfig.PUBLISH_TYPE_SOLICITED).setTerminateNotificationEnabled(
                true).build();
        if (DBG) Log.d(TAG, "executeTestPublisher: publishConfig=" + publishConfig);
        mWifiAwareSession.publish(publishConfig, discoveryCb, mHandler);

        //    wait for results - publish session
        CallbackUtils.DiscoveryCb.CallbackData callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_PUBLISH_STARTED
                        | CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_publish_timeout));
                Log.e(TAG, "executeTestPublisher: publish TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_publish_failed));
                Log.e(TAG, "executeTestPublisher: publish ON_SESSION_CONFIG_FAILED");
                return false;
        }
        PublishDiscoverySession discoverySession = callbackData.publishDiscoverySession;
        mWifiAwareDiscoverySession = discoverySession;
        if (discoverySession == null) {
            setFailureReason(mContext.getString(R.string.aware_status_publish_null_session));
            Log.e(TAG, "executeTestPublisher: publish succeeded but null session returned");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_publish_started));
        if (DBG) Log.d(TAG, "executeTestPublisher: publish succeeded");

        // 3. wait to receive message: no timeout since this depends on (human) operator starting
        //    the test on the subscriber device.
        callbackData = discoveryCb.waitForCallbacksNoTimeout(
                CallbackUtils.DiscoveryCb.ON_MESSAGE_RECEIVED);
        PeerHandle peerHandle = callbackData.peerHandle;
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_received));
        if (DBG) Log.d(TAG, "executeTestPublisher: received message");

        //    validate that received the expected message
        if (!Arrays.equals(MSG_SUB_TO_PUB, callbackData.serviceSpecificInfo)) {
            setFailureReason(mContext.getString(R.string.aware_status_receive_failure));
            Log.e(TAG, "executeTestPublisher: receive message message content mismatch: rx='"
                    + new String(callbackData.serviceSpecificInfo) + "'");
            return false;
        }
        if (peerHandle == null) {
            setFailureReason(mContext.getString(R.string.aware_status_receive_failure));
            Log.e(TAG, "executeTestPublisher: received message but peerHandle is null!?");
            return false;
        }

        // 4. Request network
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(
                mIsSecurityOpen ? discoverySession.createNetworkSpecifierOpen(peerHandle)
                        : discoverySession.createNetworkSpecifierPassphrase(peerHandle,
                                PASSPHRASE)).build();
        CallbackUtils.NetworkCb networkCb = new CallbackUtils.NetworkCb();
        cm.requestNetwork(nr, networkCb, CALLBACK_TIMEOUT_SEC * 1000);
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_requested));
        if (DBG) Log.d(TAG, "executeTestPublisher: requested network");

        // 5. send message & wait for send status
        discoverySession.sendMessage(peerHandle, MESSAGE_ID, MSG_PUB_TO_SUB);
        callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_SUCCEEDED
                        | CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_send_timeout));
                Log.e(TAG, "executeTestPublisher: send message TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_send_failed));
                Log.e(TAG, "executeTestPublisher: send message ON_MESSAGE_SEND_FAILED");
                return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_send_success));
        if (DBG) Log.d(TAG, "executeTestPublisher: send message succeeded");

        if (callbackData.messageId != MESSAGE_ID) {
            setFailureReason(mContext.getString(R.string.aware_status_send_fail_parameter));
            Log.e(TAG, "executeTestPublisher: send message succeeded but message ID mismatch : "
                    + callbackData.messageId);
            return false;
        }

        // 6. wait for network
        boolean networkAvailable = networkCb.waitForNetwork();
        cm.unregisterNetworkCallback(networkCb);
        if (!networkAvailable) {
            setFailureReason(mContext.getString(R.string.aware_status_network_failed));
            Log.e(TAG, "executeTestPublisher: request network rejected - ON_UNAVAILABLE");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_success));
        if (DBG) Log.d(TAG, "executeTestPublisher: network request granted - AVAILABLE");

        // 7. destroy session
        discoverySession.close();
        mWifiAwareDiscoverySession = null;

        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_lifecycle_ok));
        return true;

    }
}
