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
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

public class BluetoothDeviceManagerTest extends TelecomTestCase {
    @Mock BluetoothRouteManager mRouteManager;
    @Mock BluetoothHeadsetProxy mHeadsetProxy;
    @Mock BluetoothAdapterProxy mAdapterProxy;

    BluetoothDeviceManager mBluetoothDeviceManager;
    BluetoothProfile.ServiceListener serviceListenerUnderTest;
    BroadcastReceiver receiverUnderTest;

    private BluetoothDevice device1;
    private BluetoothDevice device2;
    private BluetoothDevice device3;

    public void setUp() throws Exception {
        super.setUp();
        device1 = makeBluetoothDevice("00:00:00:00:00:01");
        device2 = makeBluetoothDevice("00:00:00:00:00:02");
        device3 = makeBluetoothDevice("00:00:00:00:00:03");

        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mBluetoothDeviceManager = new BluetoothDeviceManager(mContext, mAdapterProxy,
                new TelecomSystem.SyncRoot() { });
        mBluetoothDeviceManager.setBluetoothRouteManager(mRouteManager);

        ArgumentCaptor<BluetoothProfile.ServiceListener> serviceCaptor =
                ArgumentCaptor.forClass(BluetoothProfile.ServiceListener.class);
        verify(mAdapterProxy).getProfileProxy(eq(mContext),
                serviceCaptor.capture(), eq(BluetoothProfile.HEADSET));
        serviceListenerUnderTest = serviceCaptor.getValue();

        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<IntentFilter> intentFilterCaptor =
                ArgumentCaptor.forClass(IntentFilter.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), intentFilterCaptor.capture());
        assertTrue(intentFilterCaptor.getValue().hasAction(
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));
        receiverUnderTest = receiverCaptor.getValue();

        mBluetoothDeviceManager.setHeadsetServiceForTesting(mHeadsetProxy);
    }

    @SmallTest
    public void testSingleDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(device1.getAddress(),
                mBluetoothDeviceManager.getMostRecentlyConnectedDevice(null));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1));
        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
        assertNull(mBluetoothDeviceManager.getMostRecentlyConnectedDevice(null));
    }

    @SmallTest
    public void testMultiDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2));
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(device3.getAddress(),
                mBluetoothDeviceManager.getMostRecentlyConnectedDevice(null));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device3));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(device2.getAddress(),
                mBluetoothDeviceManager.getMostRecentlyConnectedDevice(null));
    }

    @SmallTest
    public void testExclusionaryGetRecentDevices() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2));
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(device2.getAddress(),
                mBluetoothDeviceManager.getMostRecentlyConnectedDevice(device3.getAddress()));
    }

    @SmallTest
    public void testHeadsetServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2));
        serviceListenerUnderTest.onServiceDisconnected(0);

        verify(mRouteManager).onDeviceLost(device1);
        verify(mRouteManager).onDeviceLost(device2);
        assertNull(mBluetoothDeviceManager.getHeadsetService());
        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    private Intent buildConnectionActionIntent(int state, BluetoothDevice device) {
        Intent i = new Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        i.putExtra(BluetoothHeadset.EXTRA_STATE, state);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        return i;
    }

    private BluetoothDevice makeBluetoothDevice(String address) {
        Parcel p1 = Parcel.obtain();
        p1.writeString(address);
        p1.setDataPosition(0);
        BluetoothDevice device = BluetoothDevice.CREATOR.createFromParcel(p1);
        p1.recycle();
        return device;
    }
}
