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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.IWificond;
import android.net.wifi.RttManager;
import android.net.wifi.RttManager.ParcelableRttParams;
import android.net.wifi.RttManager.ResponderConfig;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.util.test.BidirectionalAsyncChannel;
import com.android.server.wifi.util.WifiPermissionsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit test for {@link com.android.server.wifi.RttService}
 */
@SmallTest
public class RttServiceTest {

    // Some constants for running Rtt tests.
    private static final String MAC = "12:34:56:78:9A:BC";
    private static final int CLIENT_KEY1 = 1;
    private static final int CLIENT_KEY2 = 2;

    @Mock
    Context mContext;
    @Mock
    WifiNative mWifiNative;
    TestLooper mLooper;
    @Mock
    WifiInjector mWifiInjector;
    @Mock
    IWificond mWificond;
    @Mock
    WifiPermissionsUtil mWifiPermissionsUtil;

    RttService.RttServiceImpl mRttServiceImpl;
    ArgumentCaptor<BroadcastReceiver> mBroadcastReceiverCaptor = ArgumentCaptor
            .forClass(BroadcastReceiver.class);

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();
        when(mWifiInjector.makeWificond()).thenReturn(mWificond);
        when(mWifiInjector.getWifiNative()).thenReturn(mWifiNative);
        when(mWifiInjector.getWifiPermissionsUtil()).thenReturn(mWifiPermissionsUtil);
        when(mWifiPermissionsUtil.checkCallersLocationPermission(any(), anyInt()))
                .thenReturn(true);
        mRttServiceImpl = new RttService.RttServiceImpl(mContext, mLooper.getLooper(),
                mWifiInjector);
        mRttServiceImpl.startService();
    }

    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    private void startWifi() {
        verify(mContext).registerReceiver(mBroadcastReceiverCaptor.capture(),
                any(IntentFilter.class));
        TestUtil.sendWifiScanAvailable(mBroadcastReceiverCaptor.getValue(),
                mContext, WifiManager.WIFI_STATE_ENABLED);
    }

    // Create and connect a bi-directional async channel.
    private BidirectionalAsyncChannel connectChannel(Handler handler) {
        BidirectionalAsyncChannel channel = new BidirectionalAsyncChannel();
        channel.connect(mLooper.getLooper(), mRttServiceImpl.getMessenger(null, new int[1]),
                handler);
        mLooper.dispatchAll();
        channel.assertConnected();
        return channel;
    }

    private void sendRangingRequestFailed(BidirectionalAsyncChannel channel, Handler handler,
            int clientKey, ParcelableRttParams params) {
        Message message = sendRangingRequest(channel, handler, clientKey, params);
        assertEquals("ranging request did not fail",
                RttManager.CMD_OP_FAILED, message.what);
        verifyNoMoreInteractions(mWifiNative);
    }

    // Send rtt ranging request message and verify failure.
    private Message sendRangingRequest(BidirectionalAsyncChannel channel, Handler handler,
            int clientKey, ParcelableRttParams params) {
        Message message = new Message();
        message.what = RttManager.CMD_OP_START_RANGING;
        message.arg2 = clientKey;
        message.obj = params;
        channel.sendMessage(message);
        mLooper.dispatchAll();
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(handler, atLeastOnce()).handleMessage(messageCaptor.capture());
        return messageCaptor.getValue();
    }

    // Send enable responder message and verify success.
    private void sendEnableResponderSucceed(BidirectionalAsyncChannel channel,
            Handler handler, int clientKey) {
        Message message = sendEnableResponder(channel, handler, clientKey,
                createResponderConfig());
        verify(mWifiNative).enableRttResponder(anyInt());
        assertEquals("reponse status is not success",
                RttManager.CMD_OP_ENALBE_RESPONDER_SUCCEEDED, message.what);
        String actualMac = ((ResponderConfig) message.obj).macAddress;
        assertEquals("mac address mismatch", MAC, actualMac);
    }

    // Send enable responder message and verify failure.
    private void sendEnableResponderFailed(BidirectionalAsyncChannel channel,
            Handler handler, int clientKey, int reason) {
        Message message = sendEnableResponder(channel, handler, clientKey, null);
        assertEquals("reponse status is not failure",
                RttManager.CMD_OP_ENALBE_RESPONDER_FAILED, message.what);
        assertEquals("failure reason is not " + reason,
                reason, message.arg1);
    }

    private Message sendEnableResponder(BidirectionalAsyncChannel channel, Handler handler,
            int clientKey, ResponderConfig config) {
        when(mWifiNative.enableRttResponder(anyInt())).thenReturn(config);
        when(mWifiNative.getMacAddress()).thenReturn(MAC);
        Message message = new Message();
        message.what = RttManager.CMD_OP_ENABLE_RESPONDER;
        message.arg2 = clientKey;
        channel.sendMessage(message);
        mLooper.dispatchAll();
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(handler, atLeastOnce()).handleMessage(messageCaptor.capture());
        return messageCaptor.getValue();
    }

    private void sendDisableResponder(BidirectionalAsyncChannel channel, int key, boolean success) {
        when(mWifiNative.disableRttResponder()).thenReturn(success);
        Message message = new Message();
        message.what = RttManager.CMD_OP_DISABLE_RESPONDER;
        message.arg2 = key;
        channel.sendMessage(message);
        mLooper.dispatchAll();
    }

    private ResponderConfig createResponderConfig() {
        ResponderConfig config = new ResponderConfig();
        config.macAddress = MAC;
        return config;
    }

    @Test
    public void testEnableResponderSuccess() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        // Successfully enabled responder.
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY1);
    }

    @Test
    public void testEnableResponderSecondTime() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY1);
        // Calling enabling responder with the same key should succeed.
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY1);
    }

    @Test
    public void testEnableResponderMultiClient() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY1);
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY2);
        // Native method should be called only once when multiple clients enabled responder.
        verify(mWifiNative, times(1)).enableRttResponder(anyInt());
    }

    @Test
    public void testDisableResponderSuccess() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY1);
        // Disable after successfully enabling.
        sendDisableResponder(channel, CLIENT_KEY1, false);
        verify(mWifiNative).disableRttResponder();
    }

    /**
     * Enable responder failed because of internal error.
     */
    @Test
    public void testEnableResponderFailure() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        when(mWifiNative.enableRttResponder(anyInt())).thenReturn(null);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        // Disable failed.
        sendEnableResponderFailed(channel, handler, CLIENT_KEY1, RttManager.REASON_UNSPECIFIED);
    }

    @Test
    public void testDisableResponderNotStarted() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        // Disable without enabling should fail.
        sendDisableResponder(channel, CLIENT_KEY1, false);
        // Native disable method shouldn't be called.
        verifyNoMoreInteractions(mWifiNative);
    }

    @Test
    public void testDisableResponderMultiClient() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY1);
        sendEnableResponderSucceed(channel, handler, CLIENT_KEY2);
        // Two clients enabled, one client disabled.
        sendDisableResponder(channel, CLIENT_KEY1, true);
        verify(mWifiNative, times(1)).getMacAddress();
        verifyNoMoreInteractions(mWifiNative);
    }

    /**
     * Enable responder failed because wifi is not enabled.
     */
    @Test
    public void testEnableResponderFailedWifiDisabled() throws Exception {
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        // Wifi is disabled as startWifi() is not invoked.
        sendEnableResponderFailed(channel, handler, CLIENT_KEY1, RttManager.REASON_NOT_AVAILABLE);
        verifyNoMoreInteractions(mWifiNative);
    }

    /**
     * Test RTT fails without proper location permission
     */
    @Test
    public void testEnableResponderFailureNoPermission() throws Exception {
        when(mWifiPermissionsUtil.checkCallersLocationPermission(any(), anyInt()))
                .thenReturn(false);
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        Message message = sendEnableResponder(channel, handler, CLIENT_KEY1, null);
        // RTT operations failed without proper permission.
        assertEquals("expected permission denied, but got " + message.what,
                RttManager.REASON_PERMISSION_DENIED, message.arg1);
    }

    /**
     * Test RTT ranging with empty RttParams.
     */
    @Test
    public void testInitiatorEmptyParams() throws Exception {
        startWifi();
        Handler handler = mock(Handler.class);
        BidirectionalAsyncChannel channel = connectChannel(handler);
        sendRangingRequestFailed(channel, handler, CLIENT_KEY1, new ParcelableRttParams(null));
    }
}
