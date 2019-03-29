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
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.util.Log;
import android.util.Pair;

import com.android.cts.verifier.R;
import com.android.cts.verifier.wifiaware.BaseTestCase;
import com.android.cts.verifier.wifiaware.CallbackUtils;

/**
 *Test case for data-path, out-of-band (OOB) test cases:
 * open/passphrase * responder/initiator.
 *
 * OOB assumes that there's an alternative channel over which to communicate the discovery MAC
 * address of the Aware interface. That channel (e.g. bluetooth or a host test device) is not
 * readily available (don't want to have Aware tests dependent on BLE). Instead will fake the OOB
 * channel using Aware itself: will do normal discovery during which the devices will exchange their
 * MAC addresses, then destroy the discovery sessions and use the MAC addresses to perform OOB data-
 * path requests.
 *
 * Responder test sequence:
 * 1. Attach (with identity listener)
 *    wait for results (session)
 *    wait for identity
 * 2. Publish
 *    wait for results (publish session)
 * 3. Wait for rx message (with MAC)
 * 4. Send message with MAC
 *    wait for success
 * 5. Destroy discovery session
 * 6. Request network (as Responder)
 *    wait for network
 *
 * Initiator test sequence:
 * 1. Attach (with identity listener)
 *    wait for results (session)
 *    wait for identity
 * 2. Subscribe
 *    wait for results (subscribe session)
 * 3. Wait for discovery
 * 4. Send message with MAC
 *    wait for success
 * 5. Wait for rx message (with MAC)
 * 6. Destroy discovery session
 * 7. Sleep for 5 seconds to let Responder time to set up
 * 8. Request network (as Initiator)
 *    wait for network
 */
public class DataPathOutOfBandTestCase extends BaseTestCase {
    private static final String TAG = "DataPathOutOfBandTestCase";
    private static final boolean DBG = true;

    private static final int MAC_BYTES_LEN = 6;

    private static final String SERVICE_NAME = "CtsVerifierTestService";
    private static final String PASSPHRASE = "Some super secret password";
    private static final int MESSAGE_ID = 1234;

    private boolean mIsSecurityOpen;
    private boolean mIsResponder;

    private final Object mLock = new Object();

    private String mFailureReason;
    private WifiAwareSession mWifiAwareSession;
    private DiscoverySession mWifiAwareDiscoverySession;
    private byte[] mDiscoveryMac;

    public DataPathOutOfBandTestCase(Context context, boolean isSecurityOpen,
            boolean isResponder) {
        super(context);
        mIsSecurityOpen = isSecurityOpen;
        mIsResponder = isResponder;
    }

    @Override
    protected boolean executeTest() throws InterruptedException {
        if (DBG) {
            Log.d(TAG, "executeTest: mIsSecurityOpen=" + mIsSecurityOpen + ", mIsResponder="
                    + mIsResponder);
        }

        // 1. attach (with identity listener)
        CallbackUtils.AttachCb attachCb = new CallbackUtils.AttachCb();
        CallbackUtils.IdentityListenerSingleShot identityL = new CallbackUtils
                .IdentityListenerSingleShot();
        mWifiAwareManager.attach(attachCb, identityL, mHandler);
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
        mDiscoveryMac = identityL.waitForMac();
        if (mDiscoveryMac == null) {
            setFailureReason(mContext.getString(R.string.aware_status_identity_fail));
            Log.e(TAG, "executeTest: identity callback not triggered");
            return false;
        }
        mListener.onTestMsgReceived(mResources.getString(R.string.aware_status_identity,
                bytesToHex(mDiscoveryMac, ':')));
        if (DBG) {
            Log.d(TAG, "executeTest: identity received: " + bytesToHex(mDiscoveryMac, ':'));
        }

        if (mIsResponder) {
            return executeTestResponder();
        } else {
            return executeTestInitiator();
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

    private boolean executeTestResponder() throws InterruptedException {
        if (DBG) Log.d(TAG, "executeTestResponder");
        CallbackUtils.DiscoveryCb discoveryCb = new CallbackUtils.DiscoveryCb();

        // 2. publish
        PublishConfig publishConfig = new PublishConfig.Builder().setServiceName(
                SERVICE_NAME).build();
        if (DBG) Log.d(TAG, "executeTestResponder: publishConfig=" + publishConfig);
        mWifiAwareSession.publish(publishConfig, discoveryCb, mHandler);

        //    wait for results - publish session
        CallbackUtils.DiscoveryCb.CallbackData callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_PUBLISH_STARTED
                        | CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_publish_timeout));
                Log.e(TAG, "executeTestResponder: publish TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_publish_failed));
                Log.e(TAG, "executeTestResponder: publish ON_SESSION_CONFIG_FAILED");
                return false;
        }
        PublishDiscoverySession discoverySession = callbackData.publishDiscoverySession;
        mWifiAwareDiscoverySession = discoverySession;
        if (discoverySession == null) {
            setFailureReason(mContext.getString(R.string.aware_status_publish_null_session));
            Log.e(TAG, "executeTestResponder: publish succeeded but null session returned");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_publish_started));
        if (DBG) Log.d(TAG, "executeTestResponder: publish succeeded");

        // 3. Wait for rx message (with MAC)
        callbackData = discoveryCb.waitForCallbacks(CallbackUtils.DiscoveryCb.ON_MESSAGE_RECEIVED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_receive_timeout));
                Log.e(TAG, "executeTestResponder: receive message TIMEOUT");
                return false;
        }

        if (callbackData.serviceSpecificInfo == null
                || callbackData.serviceSpecificInfo.length != MAC_BYTES_LEN) {
            setFailureReason(mContext.getString(R.string.aware_status_receive_failure));
            Log.e(TAG, "executeTestResponder: receive message message content mismatch: "
                    + bytesToHex(callbackData.serviceSpecificInfo, ':'));
            return false;
        }

        PeerHandle peerHandle = callbackData.peerHandle;
        byte[] peerMac = callbackData.serviceSpecificInfo;

        mListener.onTestMsgReceived(mResources.getString(R.string.aware_status_received_mac,
                bytesToHex(peerMac, ':')));
        if (DBG) {
            Log.d(TAG, "executeTestResponder: received MAC address: " + bytesToHex(peerMac, ':'));
        }

        // 4. Send message with MAC and wait for success
        discoverySession.sendMessage(peerHandle, MESSAGE_ID, mDiscoveryMac);
        callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_SUCCEEDED
                        | CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_send_timeout));
                Log.e(TAG, "executeTestResponder: send message TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_send_failed));
                Log.e(TAG, "executeTestResponder: send message ON_MESSAGE_SEND_FAILED");
                return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_send_success));
        if (DBG) Log.d(TAG, "executeTestResponder: send message succeeded");

        if (callbackData.messageId != MESSAGE_ID) {
            setFailureReason(mContext.getString(R.string.aware_status_send_fail_parameter));
            Log.e(TAG, "executeTestResponder: send message message ID mismatch: "
                    + callbackData.messageId);
            return false;
        }

        // 5. Destroy discovery session
        discoverySession.close();
        mWifiAwareDiscoverySession = null;

        // 6. Request network (as Responder) and wait for network
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(
                mIsSecurityOpen ? mWifiAwareSession.createNetworkSpecifierOpen(
                        WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER, peerMac)
                        : mWifiAwareSession.createNetworkSpecifierPassphrase(
                                WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_RESPONDER, peerMac,
                                PASSPHRASE)).build();
        CallbackUtils.NetworkCb networkCb = new CallbackUtils.NetworkCb();
        cm.requestNetwork(nr, networkCb, CALLBACK_TIMEOUT_SEC * 1000);
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_requested));
        if (DBG) Log.d(TAG, "executeTestResponder: requested network");
        boolean networkAvailable = networkCb.waitForNetwork();
        cm.unregisterNetworkCallback(networkCb);
        if (!networkAvailable) {
            setFailureReason(mContext.getString(R.string.aware_status_network_failed));
            Log.e(TAG, "executeTestResponder: network request rejected - ON_UNAVAILABLE");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_success));
        if (DBG) Log.d(TAG, "executeTestResponder: network request granted - AVAILABLE");

        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_lifecycle_ok));
        return true;
    }

    private boolean executeTestInitiator() throws InterruptedException {
        if (DBG) Log.d(TAG, "executeTestInitiator");
        CallbackUtils.DiscoveryCb discoveryCb = new CallbackUtils.DiscoveryCb();

        // 2. Subscribe
        SubscribeConfig subscribeConfig = new SubscribeConfig.Builder().setServiceName(
                SERVICE_NAME).build();
        if (DBG) Log.d(TAG, "executeTestInitiator: subscribeConfig=" + subscribeConfig);
        mWifiAwareSession.subscribe(subscribeConfig, discoveryCb, mHandler);

        //    wait for results - subscribe session
        CallbackUtils.DiscoveryCb.CallbackData callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_SUBSCRIBE_STARTED
                        | CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_subscribe_timeout));
                Log.e(TAG, "executeTestInitiator: subscribe TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_SESSION_CONFIG_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_subscribe_failed));
                Log.e(TAG, "executeTestInitiator: subscribe ON_SESSION_CONFIG_FAILED");
                return false;
        }
        SubscribeDiscoverySession discoverySession = callbackData.subscribeDiscoverySession;
        mWifiAwareDiscoverySession = discoverySession;
        if (discoverySession == null) {
            setFailureReason(mContext.getString(R.string.aware_status_subscribe_null_session));
            Log.e(TAG, "executeTestInitiator: subscribe succeeded but null session returned");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_subscribe_started));
        if (DBG) Log.d(TAG, "executeTestInitiator: subscribe succeeded");

        // 3. Wait for discovery
        callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_SERVICE_DISCOVERED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_discovery_timeout));
                Log.e(TAG, "executeTestInitiator: waiting for discovery TIMEOUT");
                return false;
        }
        PeerHandle peerHandle = callbackData.peerHandle;
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_discovery));
        if (DBG) Log.d(TAG, "executeTestInitiator: discovery");

        // 4. Send message with MAC and wait for success
        discoverySession.sendMessage(peerHandle, MESSAGE_ID, mDiscoveryMac);
        callbackData = discoveryCb.waitForCallbacks(
                CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_SUCCEEDED
                        | CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_send_timeout));
                Log.e(TAG, "executeTestInitiator: send message TIMEOUT");
                return false;
            case CallbackUtils.DiscoveryCb.ON_MESSAGE_SEND_FAILED:
                setFailureReason(mContext.getString(R.string.aware_status_send_failed));
                Log.e(TAG, "executeTestInitiator: send message ON_MESSAGE_SEND_FAILED");
                return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_send_success));
        if (DBG) Log.d(TAG, "executeTestInitiator: send message succeeded");

        if (callbackData.messageId != MESSAGE_ID) {
            setFailureReason(mContext.getString(R.string.aware_status_send_fail_parameter));
            Log.e(TAG, "executeTestInitiator: send message message ID mismatch: "
                    + callbackData.messageId);
            return false;
        }

        // 5. Wait for rx message (with MAC)
        callbackData = discoveryCb.waitForCallbacks(CallbackUtils.DiscoveryCb.ON_MESSAGE_RECEIVED);
        switch (callbackData.callback) {
            case CallbackUtils.DiscoveryCb.TIMEOUT:
                setFailureReason(mContext.getString(R.string.aware_status_receive_timeout));
                Log.e(TAG, "executeTestInitiator: receive message TIMEOUT");
                return false;
        }

        if (callbackData.serviceSpecificInfo == null
                || callbackData.serviceSpecificInfo.length != MAC_BYTES_LEN) {
            setFailureReason(mContext.getString(R.string.aware_status_receive_failure));
            Log.e(TAG, "executeTestInitiator: receive message message content mismatch: "
                    + bytesToHex(callbackData.serviceSpecificInfo, ':'));
            return false;
        }

        byte[] peerMac = callbackData.serviceSpecificInfo;

        mListener.onTestMsgReceived(mResources.getString(R.string.aware_status_received_mac,
                bytesToHex(peerMac, ':')));
        if (DBG) {
            Log.d(TAG, "executeTestInitiator: received MAC address: " + bytesToHex(peerMac, ':'));
        }

        // 6. Destroy discovery session
        discoverySession.close();
        mWifiAwareDiscoverySession = null;

        // 7. Sleep for 5 seconds to let Responder time to set up
        mListener.onTestMsgReceived(
                mContext.getString(R.string.aware_status_sleeping_wait_for_responder));
        Thread.sleep(5000);

        // 8. Request network (as Initiator) and wait for network
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkRequest nr = new NetworkRequest.Builder().addTransportType(
                NetworkCapabilities.TRANSPORT_WIFI_AWARE).setNetworkSpecifier(
                mIsSecurityOpen ? mWifiAwareSession.createNetworkSpecifierOpen(
                        WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerMac)
                        : mWifiAwareSession.createNetworkSpecifierPassphrase(
                                WifiAwareManager.WIFI_AWARE_DATA_PATH_ROLE_INITIATOR, peerMac,
                                PASSPHRASE)).build();
        CallbackUtils.NetworkCb networkCb = new CallbackUtils.NetworkCb();
        cm.requestNetwork(nr, networkCb, CALLBACK_TIMEOUT_SEC * 1000);
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_requested));
        if (DBG) Log.d(TAG, "executeTestInitiator: requested network");
        boolean networkAvailable = networkCb.waitForNetwork();
        cm.unregisterNetworkCallback(networkCb);
        if (!networkAvailable) {
            setFailureReason(mContext.getString(R.string.aware_status_network_failed));
            Log.e(TAG, "executeTestInitiator: network request rejected - ON_UNAVAILABLE");
            return false;
        }
        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_network_success));
        if (DBG) Log.d(TAG, "executeTestInitiator: network request granted - AVAILABLE");

        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_lifecycle_ok));
        return true;
    }
}
