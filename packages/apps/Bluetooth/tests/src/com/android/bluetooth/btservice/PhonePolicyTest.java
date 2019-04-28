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

package com.android.bluetooth.btservice;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.lang.Thread;

import android.test.AndroidTestCase;

import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.PhonePolicy;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.Utils;

import static org.mockito.Mockito.*;

public class PhonePolicyTest extends AndroidTestCase {
    private static final String TAG = "PhonePolicyTest";
    private static final int ASYNC_CALL_TIMEOUT = 2000; // 2s
    private static final int RETRY_TIMEOUT = 10000; // 10s

    private HandlerThread mHandlerThread;
    private BluetoothAdapter mAdapter;

    @Override
    protected void setUp() {
        mHandlerThread = new HandlerThread("PhonePolicyTest");
        mHandlerThread.start();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    protected void tearDown() {
        mHandlerThread.quit();
    }

    // Test that when new UUIDs are refreshed for a device then we set the priorities for various
    // profiles accurately. The following profiles should have ON priorities:
    // A2DP, HFP, HID and PAN
    public void testProcessInitProfilePriorities() {
        BluetoothAdapter inst = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = inst.getRemoteDevice("00:01:02:03:04:05");

        // Create the mock objects required
        AdapterService mockAdapterService = mock(AdapterService.class);
        ServiceFactory mockServiceFactory = mock(ServiceFactory.class);
        HeadsetService mockHeadsetService = mock(HeadsetService.class);
        A2dpService mockA2dpService = mock(A2dpService.class);

        // Mock the HeadsetService
        when(mockServiceFactory.getHeadsetService()).thenReturn(mockHeadsetService);
        when(mockHeadsetService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_UNDEFINED);

        // Mock the A2DP service
        when(mockServiceFactory.getA2dpService()).thenReturn(mockA2dpService);
        when(mockA2dpService.getPriority(device)).thenReturn(BluetoothProfile.PRIORITY_UNDEFINED);

        // Mock the looper
        when(mockAdapterService.getMainLooper()).thenReturn(mHandlerThread.getLooper());

        // Tell the AdapterService that it is a mock (see isMock documentation)
        when(mockAdapterService.isMock()).thenReturn(true);

        PhonePolicy phPol = new PhonePolicy(mockAdapterService, mockServiceFactory);

        // Get the broadcast receiver to inject events.
        BroadcastReceiver injector = phPol.getBroadcastReceiver();

        // Inject an event for UUIDs updated for a remote device with only HFP enabled
        Intent intent = new Intent(BluetoothDevice.ACTION_UUID);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        ParcelUuid[] uuids = new ParcelUuid[2];
        uuids[0] = BluetoothUuid.Handsfree;
        uuids[1] = BluetoothUuid.AudioSink;

        intent.putExtra(BluetoothDevice.EXTRA_UUID, uuids);
        injector.onReceive(null /* context */, intent);

        // Check that the priorities of the devices for preferred profiles are set to ON
        verify(mockHeadsetService, timeout(ASYNC_CALL_TIMEOUT).times(1))
                .setPriority(eq(device), eq(BluetoothProfile.PRIORITY_ON));
        verify(mockA2dpService, timeout(ASYNC_CALL_TIMEOUT).times(1))
                .setPriority(eq(device), eq(BluetoothProfile.PRIORITY_ON));
    }

    // Test that when the adapter is turned ON then we call autoconnect on devices that have HFP and
    // A2DP enabled. NOTE that the assumption is that we have already done the pairing previously
    // and hence the priorities for the device is already set to AUTO_CONNECT over HFP and A2DP (as
    // part of post pairing process).
    public void testAdapterOnAutoConnect() {
        BluetoothAdapter inst = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = inst.getRemoteDevice("00:01:02:03:04:05");

        // Create the mock objects required
        AdapterService mockAdapterService = mock(AdapterService.class);
        ServiceFactory mockServiceFactory = mock(ServiceFactory.class);
        HeadsetService mockHeadsetService = mock(HeadsetService.class);
        A2dpService mockA2dpService = mock(A2dpService.class);

        // Return desired values from the mocked object(s)
        when(mockAdapterService.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        when(mockAdapterService.isQuietModeEnabled()).thenReturn(false);
        when(mockServiceFactory.getHeadsetService()).thenReturn(mockHeadsetService);
        when(mockServiceFactory.getA2dpService()).thenReturn(mockA2dpService);

        // Return a list of bonded devices (just one)
        BluetoothDevice[] bondedDevices = new BluetoothDevice[1];
        bondedDevices[0] = device;
        when(mockAdapterService.getBondedDevices()).thenReturn(bondedDevices);

        // Return PRIORITY_AUTO_CONNECT over HFP and A2DP
        when(mockHeadsetService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_AUTO_CONNECT);
        when(mockA2dpService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_AUTO_CONNECT);

        // Mock the looper
        when(mockAdapterService.getMainLooper()).thenReturn(mHandlerThread.getLooper());

        // Tell the AdapterService that it is a mock (see isMock documentation)
        when(mockAdapterService.isMock()).thenReturn(true);

        PhonePolicy phPol = new PhonePolicy(mockAdapterService, mockServiceFactory);

        // Get the broadcast receiver to inject events
        BroadcastReceiver injector = phPol.getBroadcastReceiver();

        // Inject an event that the adapter is turned on.
        Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
        injector.onReceive(null /* context */, intent);

        // Check that we got a request to connect over HFP and A2DP
        verify(mockHeadsetService, timeout(ASYNC_CALL_TIMEOUT).times(1)).connect(eq(device));
        verify(mockA2dpService, timeout(ASYNC_CALL_TIMEOUT).times(1)).connect(eq(device));
    }

    // Test that we will try to re-connect to a profile on a device if an attempt failed previously.
    // This is to add robustness to the connection mechanism
    public void testReconnectOnPartialConnect() {
        BluetoothAdapter inst = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = inst.getRemoteDevice("00:01:02:03:04:05");

        // Create the mock objects required
        AdapterService mockAdapterService = mock(AdapterService.class);
        ServiceFactory mockServiceFactory = mock(ServiceFactory.class);
        HeadsetService mockHeadsetService = mock(HeadsetService.class);
        A2dpService mockA2dpService = mock(A2dpService.class);

        // Setup the mocked factory to return mocked services
        when(mockServiceFactory.getHeadsetService()).thenReturn(mockHeadsetService);
        when(mockServiceFactory.getA2dpService()).thenReturn(mockA2dpService);

        // Return a list of bonded devices (just one)
        BluetoothDevice[] bondedDevices = new BluetoothDevice[1];
        bondedDevices[0] = device;
        when(mockAdapterService.getBondedDevices()).thenReturn(bondedDevices);

        // Return PRIORITY_AUTO_CONNECT over HFP and A2DP. This would imply that the profiles are
        // auto-connectable.
        when(mockHeadsetService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_AUTO_CONNECT);
        when(mockA2dpService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_AUTO_CONNECT);

        when(mockAdapterService.getState()).thenReturn(BluetoothAdapter.STATE_ON);

        // Mock the looper
        when(mockAdapterService.getMainLooper()).thenReturn(mHandlerThread.getLooper());

        // Tell the AdapterService that it is a mock (see isMock documentation)
        when(mockAdapterService.isMock()).thenReturn(true);

        PhonePolicy phPol = new PhonePolicy(mockAdapterService, mockServiceFactory);

        // Get the broadcast receiver to inject events
        BroadcastReceiver injector = phPol.getBroadcastReceiver();

        // We send a connection successful for one profile since the re-connect *only* works if we
        // have already connected successfully over one of the profiles
        Intent intent = new Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_DISCONNECTED);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);
        intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        injector.onReceive(null /* context */, intent);

        // We should see (in CONNECT_OTHER_PROFILES_TIMEOUT) a call to connect A2DP
        // To enable that we need to make sure that HeadsetService returns the device as list of
        // connected devices
        ArrayList<BluetoothDevice> hsConnectedDevices = new ArrayList<>();
        hsConnectedDevices.add(device);
        when(mockHeadsetService.getConnectedDevices()).thenReturn(hsConnectedDevices);
        // Also the A2DP should say that its not connected for same device
        when(mockA2dpService.getConnectionState(device))
                .thenReturn(BluetoothProfile.STATE_DISCONNECTED);

        // Check that we get a call to A2DP connect
        verify(mockA2dpService, timeout(RETRY_TIMEOUT).times(1)).connect(eq(device));
    }

    // Test that we will not try to reconnect on a profile if all the connections failed
    public void testNoReconnectOnNoConnect() {
        BluetoothAdapter inst = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = inst.getRemoteDevice("00:01:02:03:04:05");

        // Create the mock objects required
        AdapterService mockAdapterService = mock(AdapterService.class);
        ServiceFactory mockServiceFactory = mock(ServiceFactory.class);
        HeadsetService mockHeadsetService = mock(HeadsetService.class);
        A2dpService mockA2dpService = mock(A2dpService.class);

        // Setup the mocked factory to return mocked services
        when(mockServiceFactory.getHeadsetService()).thenReturn(mockHeadsetService);
        when(mockServiceFactory.getA2dpService()).thenReturn(mockA2dpService);

        // Return a list of bonded devices (just one)
        BluetoothDevice[] bondedDevices = new BluetoothDevice[1];
        bondedDevices[0] = device;
        when(mockAdapterService.getBondedDevices()).thenReturn(bondedDevices);

        // Return PRIORITY_AUTO_CONNECT over HFP and A2DP. This would imply that the profiles are
        // auto-connectable.
        when(mockHeadsetService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_AUTO_CONNECT);
        when(mockA2dpService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_AUTO_CONNECT);

        when(mockAdapterService.getState()).thenReturn(BluetoothAdapter.STATE_ON);

        // Mock the looper
        when(mockAdapterService.getMainLooper()).thenReturn(mHandlerThread.getLooper());

        // Tell the AdapterService that it is a mock (see isMock documentation)
        when(mockAdapterService.isMock()).thenReturn(true);

        PhonePolicy phPol = new PhonePolicy(mockAdapterService, mockServiceFactory);

        // Get the broadcast receiver to inject events
        BroadcastReceiver injector = phPol.getBroadcastReceiver();

        // We send a connection successful for one profile since the re-connect *only* works if we
        // have already connected successfully over one of the profiles
        Intent intent = new Intent(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, BluetoothProfile.STATE_DISCONNECTED);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_CONNECTED);
        intent.addFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        injector.onReceive(null /* context */, intent);

        // Return an empty list simulating that the above connection successful was nullified
        ArrayList<BluetoothDevice> hsConnectedDevices = new ArrayList<>();
        when(mockHeadsetService.getConnectedDevices()).thenReturn(hsConnectedDevices);

        // Also the A2DP should say that its not connected for same device
        when(mockA2dpService.getConnectionState(device))
                .thenReturn(BluetoothProfile.STATE_DISCONNECTED);

        // To check that we have processed all the messages we need to have a hard sleep here. The
        // reason being mockito can only verify synchronous calls, asynchronous calls are hidden
        // from its mocking framework. Also, Looper does not provide a way to wait until all future
        // messages are proceed.
        try {
            Thread.sleep(RETRY_TIMEOUT);
        } catch (Exception ex) {
        }

        // Check that we don't get any calls to reconnect
        verify(mockA2dpService, never()).connect(eq(device));
        verify(mockHeadsetService, never()).connect(eq(device));
    }

    // Test that a device with no supported uuids is initialized properly and does not crash the
    // stack
    public void testNoSupportedUuids() {
        BluetoothAdapter inst = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = inst.getRemoteDevice("00:01:02:03:04:05");

        // Create the mock objects required
        AdapterService mockAdapterService = mock(AdapterService.class);
        ServiceFactory mockServiceFactory = mock(ServiceFactory.class);
        HeadsetService mockHeadsetService = mock(HeadsetService.class);
        A2dpService mockA2dpService = mock(A2dpService.class);

        // Mock the HeadsetService
        when(mockServiceFactory.getHeadsetService()).thenReturn(mockHeadsetService);
        when(mockHeadsetService.getPriority(device))
                .thenReturn(BluetoothProfile.PRIORITY_UNDEFINED);

        // Mock the A2DP service
        when(mockServiceFactory.getA2dpService()).thenReturn(mockA2dpService);
        when(mockA2dpService.getPriority(device)).thenReturn(BluetoothProfile.PRIORITY_UNDEFINED);

        // Mock the looper
        when(mockAdapterService.getMainLooper()).thenReturn(mHandlerThread.getLooper());

        PhonePolicy phPol = new PhonePolicy(mockAdapterService, mockServiceFactory);

        // Get the broadcast receiver to inject events.
        BroadcastReceiver injector = phPol.getBroadcastReceiver();

        // Inject an event for UUIDs updated for a remote device with only HFP enabled
        Intent intent = new Intent(BluetoothDevice.ACTION_UUID);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);

        // Put no UUIDs
        injector.onReceive(null /* context */, intent);

        // To check that we have processed all the messages we need to have a hard sleep here. The
        // reason being mockito can only verify synchronous calls, asynchronous calls are hidden
        // from its mocking framework. Also, Looper does not provide a way to wait until all future
        // messages are proceed.
        try {
            Thread.sleep(RETRY_TIMEOUT);
        } catch (Exception ex) {
        }

        // Check that we do not crash and not call any setPriority methods
        verify(mockHeadsetService, never())
                .setPriority(eq(device), eq(BluetoothProfile.PRIORITY_ON));
        verify(mockA2dpService, never()).setPriority(eq(device), eq(BluetoothProfile.PRIORITY_ON));
    }
}
