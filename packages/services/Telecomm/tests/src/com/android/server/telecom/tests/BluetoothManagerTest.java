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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.BluetoothAdapterProxy;
import com.android.server.telecom.BluetoothHeadsetProxy;
import com.android.server.telecom.BluetoothManager;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BluetoothManagerTest extends TelecomTestCase {
    @Mock BluetoothManager.BluetoothStateListener mListener;
    @Mock BluetoothHeadsetProxy mHeadsetProxy;
    @Mock BluetoothAdapterProxy mAdapterProxy;

    BluetoothManager mBluetoothManager;
    BluetoothProfile.ServiceListener serviceListenerUnderTest;
    BroadcastReceiver receiverUnderTest;

    private BluetoothDevice device1;

    public void setUp() throws Exception {
        super.setUp();
        initializeDevice();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mBluetoothManager = new BluetoothManager(mContext, mAdapterProxy);

        ArgumentCaptor<BluetoothProfile.ServiceListener> serviceCaptor =
                ArgumentCaptor.forClass(BluetoothProfile.ServiceListener.class);
        verify(mAdapterProxy).getProfileProxy(eq(mContext),
                serviceCaptor.capture(), eq(BluetoothProfile.HEADSET));
        serviceListenerUnderTest = serviceCaptor.getValue();

        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), any(IntentFilter.class));
        receiverUnderTest = receiverCaptor.getValue();

        mBluetoothManager.setBluetoothStateListener(mListener);
        mBluetoothManager.setBluetoothHeadsetForTesting(mHeadsetProxy);
    }

    @SmallTest
    public void testIsBluetoothAvailableWithNoDevices() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.<BluetoothDevice>emptyList());
        assertFalse(mBluetoothManager.isBluetoothAvailable());
    }

    @SmallTest
    public void testIsBluetoothAvailable() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        assertTrue(mBluetoothManager.isBluetoothAvailable());
    }

    @SmallTest
    public void testIsAudioConnectedWithNoDevices() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.<BluetoothDevice>emptyList());
        assertFalse(mBluetoothManager.isBluetoothAudioConnected());
    }

    @SmallTest
    public void testIsAudioConnectedWhenAudioNotOn() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(false);
        assertFalse(mBluetoothManager.isBluetoothAudioConnected());
    }

    @SmallTest
    public void testIsAudioConnectedWhenAudioOn() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(true);
        assertTrue(mBluetoothManager.isBluetoothAudioConnected());
    }

    @SmallTest
    public void testShouldBePendingAfterConnectAudio() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(false);
        mBluetoothManager.connectBluetoothAudio();
        verify(mHeadsetProxy).connectAudio();
        assertFalse(mBluetoothManager.isBluetoothAudioConnected());
        assertTrue(mBluetoothManager.isBluetoothAudioConnectedOrPending());
    }

    @SmallTest
    public void testDisconnectAudioWhenHeadsetServiceConnected() {
        mBluetoothManager.disconnectBluetoothAudio();
        verify(mHeadsetProxy).disconnectAudio();
    }

    @SmallTest
    public void testDisconnectAudioWithNoHeadsetService() {
        mBluetoothManager.setBluetoothHeadsetForTesting(null);
        mBluetoothManager.disconnectBluetoothAudio();
        verify(mHeadsetProxy, never()).disconnectAudio();
    }

    @SmallTest
    public void testConnectServiceWhenUninitialized1() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.<BluetoothDevice>emptyList());
        serviceListenerUnderTest.onServiceConnected(BluetoothProfile.HEALTH, null);
        verify(mListener).onBluetoothStateChange(BluetoothManager.BLUETOOTH_UNINITIALIZED,
                BluetoothManager.BLUETOOTH_DISCONNECTED);
    }

    @SmallTest
    public void testConnectServiceWhenUninitialized2() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(false);
        serviceListenerUnderTest.onServiceConnected(BluetoothProfile.HEALTH, null);
        verify(mListener).onBluetoothStateChange(BluetoothManager.BLUETOOTH_UNINITIALIZED,
                BluetoothManager.BLUETOOTH_DEVICE_CONNECTED);
    }

    @SmallTest
    public void testConnectServiceWhenUninitialized3() {
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(true);
        serviceListenerUnderTest.onServiceConnected(BluetoothProfile.HEALTH, null);
        verify(mListener).onBluetoothStateChange(BluetoothManager.BLUETOOTH_UNINITIALIZED,
                BluetoothManager.BLUETOOTH_AUDIO_CONNECTED);
    }

    @SmallTest
    public void testReceiveAudioDisconnectWhenConnected() {
        mBluetoothManager.setInternalBluetoothState(BluetoothManager.BLUETOOTH_AUDIO_CONNECTED);
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(false);
        receiverUnderTest.onReceive(mContext,
                buildAudioActionIntent(BluetoothHeadset.STATE_AUDIO_DISCONNECTED));
        verify(mListener).onBluetoothStateChange(BluetoothManager.BLUETOOTH_AUDIO_CONNECTED,
                BluetoothManager.BLUETOOTH_DEVICE_CONNECTED);
    }

    @SmallTest
    public void testReceiveAudioConnectWhenDisconnected() {
        mBluetoothManager.setInternalBluetoothState(BluetoothManager.BLUETOOTH_DEVICE_CONNECTED);
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(true);
        receiverUnderTest.onReceive(mContext,
                buildAudioActionIntent(BluetoothHeadset.STATE_AUDIO_CONNECTED));
        verify(mListener).onBluetoothStateChange(BluetoothManager.BLUETOOTH_DEVICE_CONNECTED,
                BluetoothManager.BLUETOOTH_AUDIO_CONNECTED);
    }

    @SmallTest
    public void testReceiveAudioConnectWhenPending() {
        mBluetoothManager.setInternalBluetoothState(BluetoothManager.BLUETOOTH_AUDIO_PENDING);
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(true);
        receiverUnderTest.onReceive(mContext,
                buildAudioActionIntent(BluetoothHeadset.STATE_AUDIO_CONNECTED));
        verify(mListener).onBluetoothStateChange(BluetoothManager.BLUETOOTH_AUDIO_PENDING,
                BluetoothManager.BLUETOOTH_AUDIO_CONNECTED);
    }

    @SmallTest
    public void testReceiveAudioDisconnectWhenPending() {
        mBluetoothManager.setInternalBluetoothState(BluetoothManager.BLUETOOTH_AUDIO_PENDING);
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(false);
        receiverUnderTest.onReceive(mContext,
                buildAudioActionIntent(BluetoothHeadset.STATE_AUDIO_DISCONNECTED));
        verify(mListener).onBluetoothStateChange(BluetoothManager.BLUETOOTH_AUDIO_PENDING,
                BluetoothManager.BLUETOOTH_DEVICE_CONNECTED);
    }

    @SmallTest
    public void testReceiveAudioConnectingWhenPending() {
        mBluetoothManager.setInternalBluetoothState(BluetoothManager.BLUETOOTH_AUDIO_PENDING);
        when(mHeadsetProxy.getConnectedDevices()).thenReturn(
                Collections.singletonList(device1));
        when(mHeadsetProxy.isAudioConnected(eq(device1))).thenReturn(false);
        receiverUnderTest.onReceive(mContext,
                buildAudioActionIntent(BluetoothHeadset.STATE_AUDIO_CONNECTING));
        verify(mListener, never()).onBluetoothStateChange(anyInt(), anyInt());
    }

    private Intent buildAudioActionIntent(int state) {
        Intent i = new Intent(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
        i.putExtra(BluetoothHeadset.EXTRA_STATE, state);
        return i;
    }

    private void initializeDevice() {
        Parcel p1 = Parcel.obtain();
        p1.writeString("00:01:02:03:04:05");
        p1.setDataPosition(0);
        device1 = BluetoothDevice.CREATOR.createFromParcel(p1);
        p1.recycle();
    }
}
