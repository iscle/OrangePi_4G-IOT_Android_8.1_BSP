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

package com.android.car;

import android.annotation.Nullable;
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothPbapClient;
import android.bluetooth.BluetoothProfile;
import android.car.ICarUserService;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.property.CarPropertyEvent;
import android.car.CarBluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.test.AndroidTestCase;

import org.mockito.Matchers;
import org.mockito.Mockito;

import static org.mockito.Mockito.*;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the {@link BluetoothDeviceConnectionPolicy}.
 * Isolate and test the policy's functionality - test if it finds the right device(s) per profile to
 * connect to when triggered by an appropriate event.
 *
 * The following services are mocked:
 * 1. {@link CarBluetoothUserService} - connect requests to the Bluetooth stack are stubbed out
 * and connection results can be injected (imitating results from the stack)
 * 2. {@link CarCabinService} & {@link CarSensorService} - Fake vehicle events are injected to the
 * policy's Broadcast Receiver.
 */
public class BluetoothAutoConnectPolicyTest extends AndroidTestCase {
    private BluetoothDeviceConnectionPolicy mBluetoothDeviceConnectionPolicyTest;
    private BluetoothAdapter mBluetoothAdapter;
    private BroadcastReceiver mReceiver;
    private BluetoothDeviceConnectionPolicy.CarPropertyListener mCabinEventListener;
    private Handler mMainHandler;
    private Context mockContext;
    // Mock of Services that the policy interacts with
    private CarCabinService mockCarCabinService;
    private CarSensorService mockCarSensorService;
    private CarBluetoothUserService mockBluetoothUserService;
    private PerUserCarServiceHelper mockPerUserCarServiceHelper;
    private ICarUserService mockPerUserCarService;
    private CarBluetoothService mockCarBluetoothService;
    // Timeouts
    private static final int CONNECTION_STATE_CHANGE_TIME = 200; //ms
    private static final int CONNECTION_REQUEST_TIMEOUT = 10000;//ms
    private static final int WAIT_FOR_COMPLETION_TIME = 3000;//ms

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mMainHandler = new Handler(Looper.getMainLooper());
        makeMockServices();
    }

    @Override
    protected void tearDown() throws Exception {
        mBluetoothDeviceConnectionPolicyTest.release();
        super.tearDown();
    }
    /****************************************** Utility methods **********************************/

    /**
     * Pair the given device to the Car on all supported Bluetooth profiles.  This is just adding a
     * (fake) BluetoothDevice to the policy's records, which is what a real Bluetooth pairing would
     * do.
     */
    private void pairDevice(BluetoothDevice device) {
        for (Integer profile : mBluetoothDeviceConnectionPolicyTest.getProfilesToConnect()) {
            pairDeviceOnProfile(device, profile);
        }
    }

    /**
     * Pair a device on the given profile.  Note that this is not real Bluetooth Pairing.  This is
     * just adding a (fake) BluetoothDevice to the policy's records, which is what a real Bluetooth
     * pairing would have done.
     *
     * @param device  - Bluetooth device
     * @param profile - Profile to pair on
     */
    private void pairDeviceOnProfile(BluetoothDevice device, Integer profile) {
        sendFakeConnectionStateChangeOnProfile(device, profile, true);
    }

    /**
     * Connect or Disconnect the list of devices.
     *
     * @param deviceList - list of Bluetooth devices
     * @param connect    - true - connect, false - disconnect
     */
    private void connectDevices(List<BluetoothDevice> deviceList, boolean connect) {
        for (BluetoothDevice device : deviceList) {
            sendFakeConnectionStateChange(device, connect);
        }
    }

    /**
     * Inject a fake connection state changed intent on all profiles to the policy's
     * Broadcast Receiver
     *
     * @param device  - Bluetooth device
     * @param connect - connection success or failure
     */
    private void sendFakeConnectionStateChange(BluetoothDevice device, boolean connect) {
        for (Integer profile : mBluetoothDeviceConnectionPolicyTest.getProfilesToConnect()) {
            sendFakeConnectionStateChangeOnProfile(device, profile, connect);
        }
    }

    /**
     * Inject a fake connection state changed intent to the policy's Broadcast Receiver
     *
     * @param device  - Bluetooth Device
     * @param profile - Bluetooth Profile
     * @param connect - connection Success or Failure
     */
    private void sendFakeConnectionStateChangeOnProfile(BluetoothDevice device, Integer profile,
            boolean connect) {
        assertNotNull(mReceiver);
        Intent connectionIntent = createBluetoothConnectionStateChangedIntent(profile, device,
                connect);
        mReceiver.onReceive(null, connectionIntent);
    }

    /**
     * Utility function to create a Connection State Changed Intent for the given profile and device
     *
     * @param profile - Bluetooth profile
     * @param device  - Bluetooth Device
     * @param connect - Connection Success or Failure
     * @return - Connection State Changed Intent with the filled up EXTRAs
     */
    private Intent createBluetoothConnectionStateChangedIntent(int profile, BluetoothDevice device,
            boolean connect) {
        Intent connectionIntent;
        switch (profile) {
            case BluetoothProfile.A2DP_SINK:
                connectionIntent = new Intent(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED);
                break;
            case BluetoothProfile.HEADSET_CLIENT:
                connectionIntent = new Intent(
                        BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED);
                break;
            case BluetoothProfile.MAP_CLIENT:
                connectionIntent = new Intent(BluetoothMapClient.ACTION_CONNECTION_STATE_CHANGED);
                break;
            case BluetoothProfile.PBAP_CLIENT:
                connectionIntent = new Intent(BluetoothPbapClient.ACTION_CONNECTION_STATE_CHANGED);
                break;
            default:
                return null;
        }
        connectionIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        if (connect) {
            connectionIntent.putExtra(BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_CONNECTED);
        } else {
            connectionIntent.putExtra(BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_DISCONNECTED);
        }
        return connectionIntent;
    }

    /**
     * Trigger a fake vehicle Event by injecting directly into the policy's Event Listener.
     * Note that a Cabin Event (Door unlock) is used here.  The default policy has a Cabin Event
     * listener.
     * The event can be changed to what is appropriate as long as there is a corresponding listener
     * implemented in the policy
     */
    private void triggerFakeVehicleEvent() throws RemoteException {
        assertNotNull(mCabinEventListener);
        CarPropertyValue<Boolean> value = new CarPropertyValue<>(CarCabinManager.ID_DOOR_LOCK,
                false);
        CarPropertyEvent event = new CarPropertyEvent(
                CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, value);
        mCabinEventListener.onEvent(event);
    }

    /**
     * Put all the mock creations in one place.  To be called from setup()
     */
    private void makeMockServices() {
        mockContext = mock(Context.class);
        mockCarCabinService = mock(CarCabinService.class);
        mockCarSensorService = mock(CarSensorService.class);
        mockPerUserCarServiceHelper = mock(PerUserCarServiceHelper.class);
        mockPerUserCarService = mock(ICarUserService.class);
        mockCarBluetoothService = mock(CarBluetoothService.class);
        mockBluetoothUserService = mock(CarBluetoothUserService.class,
                Mockito.withSettings().verboseLogging());
    }

    /**
     * Mock response to a connection request on a specific device.
     *
     * @param device    - Bluetooth device to mock availability
     * @param available - result to return when a connection is requested
     */
    private void mockDeviceAvailability(@Nullable BluetoothDevice device, boolean available)
            throws Exception {
        if (mBluetoothDeviceConnectionPolicyTest != null) {
            for (Integer profile : mBluetoothDeviceConnectionPolicyTest.getProfilesToConnect()) {
                Mockito.doAnswer(createDeviceAvailabilityAnswer(available)).when(
                        mockBluetoothUserService).
                        bluetoothConnectToProfile(profile, device);
            }
        }
    }

    private Answer<Void> createDeviceAvailabilityAnswer(final boolean available) {
        final Answer<Void> answer = new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                if (invocationOnMock.getArguments().length < 2) {
                    return null;
                }
                final int profile = (int) invocationOnMock.getArguments()[0];
                final BluetoothDevice device = (BluetoothDevice) invocationOnMock.getArguments()[1];
                // Sanity check
                if (device == null) {
                    return null;
                }
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mReceiver.onReceive(null,
                                createBluetoothConnectionStateChangedIntent(profile, device,
                                        available));
                    }
                }, CONNECTION_STATE_CHANGE_TIME);
                return null;
            }
        };
        return answer;
    }

    /**
     * Utility method called from the beginning of every test to create and init the policy
     */
    private void createAndSetupBluetoothPolicy() throws Exception {
        // Return the mock Bluetooth User Service when asked for
        when(mockBluetoothUserService.isBluetoothConnectionProxyAvailable(
                Matchers.anyInt())).thenReturn(true);
        when(mockPerUserCarService.getBluetoothUserService()).thenReturn(mockBluetoothUserService);

        mBluetoothDeviceConnectionPolicyTest = BluetoothDeviceConnectionPolicy.create(mockContext,
                mockCarCabinService, mockCarSensorService, mockPerUserCarServiceHelper,
                mockCarBluetoothService);
        mBluetoothDeviceConnectionPolicyTest.setAllowReadWriteToSettings(false);
        mBluetoothDeviceConnectionPolicyTest.init();

        mReceiver = mBluetoothDeviceConnectionPolicyTest.getBluetoothBroadcastReceiver();
        assertNotNull(mReceiver);
        BluetoothDeviceConnectionPolicy.UserServiceConnectionCallback serviceConnectionCallback =
                mBluetoothDeviceConnectionPolicyTest.getServiceCallback();
        assertNotNull(serviceConnectionCallback);
        mCabinEventListener = mBluetoothDeviceConnectionPolicyTest.getCarPropertyListener();
        assertNotNull(mCabinEventListener);

        serviceConnectionCallback.onServiceConnected(mockPerUserCarService);
    }

    /**
     * Utility method called from the end of every test to cleanup and release the policy
     */
    private Intent createBluetoothBondStateChangedIntent(BluetoothDevice device, boolean bonded) {
        // Unbond the device
        Intent bondStateIntent = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (!bonded) {
            bondStateIntent.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
        } else {
            bondStateIntent.putExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_BONDED);
        }
        bondStateIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        return bondStateIntent;
    }

    private void tagDeviceForAllProfiles(BluetoothDevice device, int priority) {
        // tag device as primary in all profiles
        for (Integer profile : mBluetoothDeviceConnectionPolicyTest.getProfilesToConnect()) {
            tagDeviceForProfile(device, profile, priority);
        }
    }

    private void tagDeviceForProfile(BluetoothDevice device, Integer profile, int priority) {
        mBluetoothDeviceConnectionPolicyTest.tagDeviceWithPriority(device, profile, priority);
    }


    /************************************** Test Methods *****************************************/
    /**
     * Basic test -
     * 1. Pair one device to the car on all profiles.
     * 2. Disconnect the device
     * 3. Inject a fake vehicle event
     * 4. Verify that we get connection requests on all the profiles with that paired device
     */
    @Test
    public void testAutoConnectOneDevice() throws Exception {
        createAndSetupBluetoothPolicy();
        // Tell the policy a new device connected - this mimics pairing
        BluetoothDevice device1 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:01");
        mockDeviceAvailability(device1, true);
        // Pair (and Connect) device1 on all the Bluetooth profiles.
        pairDevice(device1);
        // Disconnect so we can test Autoconnect by sending a vehicle event
        sendFakeConnectionStateChange(device1, false);

        // At this point DEADBEEF0001 is paired but disconnected to the vehicle
        // Now, trigger a connection and check if we connected to DEADBEEF0001 on all profiles
        triggerFakeVehicleEvent();
        // Verify that on all profiles, device1 connected
        for (Integer profile : mBluetoothDeviceConnectionPolicyTest.getProfilesToConnect()) {
            verify(mockBluetoothUserService,
                    Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                    profile, device1);
        }

        // Before we cleanup wait for the last Connection Status change from mockDeviceAvailability
        // is broadcast to the policy.
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);
        // Inject an Unbond event to the policy
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device1, false));
    }

    /**
     * Multi device test
     * 1. Pair 4 different devices 2 on HFP and PBAP (since they allow 2 connections) and 1 each on
     * A2DP and MAP
     * 2. Disconnect all devices
     * 3. Inject a fake vehicle event.
     * 4. Verify that the right devices connect on the right profiles ( the snapshot recreated)
     */
    @Test
    public void testAutoConnectMultiDevice() throws Exception {
        createAndSetupBluetoothPolicy();
        BluetoothDevice device1 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:01");
        BluetoothDevice device2 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:02");
        BluetoothDevice device3 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:03");
        BluetoothDevice device4 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:04");
        BluetoothDevice[] testDevices = new BluetoothDevice[]{device4, device3, device2, device1};

        for (BluetoothDevice device : testDevices) {
            mockDeviceAvailability(device, true);
        }
        // Pair 4 different devices on the 4 profiles. HFP and PBAP are connected on the same
        // device(s)
        pairDeviceOnProfile(device1, BluetoothProfile.HEADSET_CLIENT);
        pairDeviceOnProfile(device1, BluetoothProfile.PBAP_CLIENT);
        pairDeviceOnProfile(device2, BluetoothProfile.HEADSET_CLIENT);
        pairDeviceOnProfile(device2, BluetoothProfile.PBAP_CLIENT);
        pairDeviceOnProfile(device3, BluetoothProfile.A2DP_SINK);
        pairDeviceOnProfile(device4, BluetoothProfile.MAP_CLIENT);

        // Disconnect all the 4 devices on the respective connected profiles
        sendFakeConnectionStateChangeOnProfile(device1, BluetoothProfile.HEADSET_CLIENT, false);
        sendFakeConnectionStateChangeOnProfile(device1, BluetoothProfile.PBAP_CLIENT, false);
        sendFakeConnectionStateChangeOnProfile(device2, BluetoothProfile.HEADSET_CLIENT, false);
        sendFakeConnectionStateChangeOnProfile(device2, BluetoothProfile.PBAP_CLIENT, false);
        sendFakeConnectionStateChangeOnProfile(device3, BluetoothProfile.A2DP_SINK, false);
        sendFakeConnectionStateChangeOnProfile(device4, BluetoothProfile.MAP_CLIENT, false);

        triggerFakeVehicleEvent();

        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.HEADSET_CLIENT, device1);

        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.PBAP_CLIENT, device1);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.HEADSET_CLIENT, device2);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.PBAP_CLIENT, device2);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.A2DP_SINK, device3);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.MAP_CLIENT, device4);

        // Before we cleanup wait for the last Connection Status change from is broadcast to the
        // policy.
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);
        // Inject an Unbond event to the policy
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device1, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device2, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device3, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device4, false));
    }

    /**
     * Test setting a device as a primary device.  A primary device, if present, will be the first
     * device that the policy will try to connect regardless of which device connected last.
     * 1. Pair 4 devices.
     * 2. Leave them disconnected.
     * 3. Tag one device as Primary.
     * 4. Send a connection trigger.
     * 5. Verify if the tagged device connected.
     * 6. Disconnect and tag another device.
     * 7. Send a Connection trigger.
     * 8. Verify if the newly tagged device connected.
     */
    @Test
    public void testAutoConnectSetPrimaryPriority() throws Exception {
        createAndSetupBluetoothPolicy();
        BluetoothDevice device1 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:01");
        BluetoothDevice device2 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:02");
        BluetoothDevice device3 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:03");
        BluetoothDevice device4 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:04");
        BluetoothDevice[] testDevices = new BluetoothDevice[]{device4, device3, device2, device1};

        // pair all 4 devices on all 4 profiles
        for (BluetoothDevice device : testDevices) {
            mockDeviceAvailability(device, true);
            pairDevice(device);
            // Disconnect the device.  We want to test auto connection, so the state we want
            // to be at the end of this loop is paired and disconnected
            for (Integer profile : mBluetoothDeviceConnectionPolicyTest.getProfilesToConnect()) {
                sendFakeConnectionStateChangeOnProfile(device, profile, false);
            }
        }
        // Device Order for all profiles will be {device1, device2, device3, device4} in the order
        // of who connected last.
        // Randomly pick device3 as the primary device
        tagDeviceForAllProfiles(device3,
                CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0);
        // Device order should be {device3, device1, device2, device4} now
        // Now when we trigger an auto connect, device 3 should connect on all profiles
        triggerFakeVehicleEvent();
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.HEADSET_CLIENT, device3);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.PBAP_CLIENT, device3);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.A2DP_SINK, device3);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.MAP_CLIENT, device3);
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);

        // Change primary device to device4
        tagDeviceForAllProfiles(device4,
                CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0);
        //Disconnect on all 4 profiles. device3 is connected on all profiles. device1 on HFP & PBAP
        sendFakeConnectionStateChange(device3, false);
        sendFakeConnectionStateChangeOnProfile(device1, BluetoothProfile.HEADSET_CLIENT, false);
        sendFakeConnectionStateChangeOnProfile(device1, BluetoothProfile.PBAP_CLIENT, false);

        // Device Order should be {device4, device3, device1, device2}
        triggerFakeVehicleEvent();

        // Check if device4 connects now
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.HEADSET_CLIENT, device4);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.PBAP_CLIENT, device4);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.A2DP_SINK, device4);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.MAP_CLIENT, device4);

        // Before we cleanup wait for the last Connection Status change is broadcast to the policy.
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);
        // Inject an Unbond event to the policy
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device1, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device2, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device3, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device4, false));
    }

    /**
     * Test setting a device with Secondary priority.
     * Tag a device as primary and another as secondary.
     * Choose a profile that supports only one active connection - A2DP for example.
     * Mock all devices to be available for connection - connection requests are successful
     * Trigger connection.
     * Secondary device should not have connected on A2DP (since Primary is available)
     * Change Primary device to unavailable.
     * Trigger Connection.
     * Secondary device should now be connected on A2DP (since Primary is not available)
     */
    @Test
    public void testAutoConnectSetSecondaryPriority() throws Exception {
        createAndSetupBluetoothPolicy();
        BluetoothDevice device1 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:01");
        BluetoothDevice device2 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:02");
        BluetoothDevice device3 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:03");
        BluetoothDevice device4 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:04");
        BluetoothDevice[] testDevices = new BluetoothDevice[]{device4, device3, device2, device1};

        // Mark all devices to respond successfully for a connection request & Pair them
        for (BluetoothDevice device : testDevices) {
            mockDeviceAvailability(device, true);
            pairDevice(device);
        }
        connectDevices(Arrays.asList(testDevices), false);
        // Device order at this point {device1, device2, device3, device4} in the order of last
        // connected first

        // Tag device3 as Primary device and device4 as Secondary device.
        tagDeviceForAllProfiles(device3,
                CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_0);
        tagDeviceForAllProfiles(device4,
                CarBluetoothManager.BLUETOOTH_DEVICE_CONNECTION_PRIORITY_1);
        // Device order at this point {device3, device4, device1, device2}
        // Test 1:
        //  a) When a connection event triggers, no connection attempt should have been made on
        // device4 for A2DPS_SINK profile because:
        //      1. A2DP_SINK supports only one active connection.
        //      2. Device1 is the first device in the order and it's available for connection
        //  b) Connection attempt should have been made on HFP since HFP supports 2 connections and
        // device4 is the second device.
        triggerFakeVehicleEvent();
        // Mockito.never() doesn't have a timeout option, hence the sleep here.
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);
        verify(mockBluetoothUserService, Mockito.never()).bluetoothConnectToProfile(
                BluetoothProfile.A2DP_SINK, device4);
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.HEADSET_CLIENT, device4);

        // Disconnect devices again
        connectDevices(Arrays.asList(testDevices), false);
        // Mock primaryDevice to be unavailable
        mockDeviceAvailability(device3, false);
        triggerFakeVehicleEvent();
        // Now a connection attempt should be made on device4 for A2DP_SINK for the same reasons as
        // above
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.A2DP_SINK, device4);
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device1, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device2, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device3, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device4, false));
    }

    /**
     * When 2 devices are paired and one is connected, test if the second device when brought in
     * range connects when a vehicle event occurs.
     * 1. Pair 2 devices.
     * 2. Keep one connected.
     * 3. Bring the other device.
     * 4. Send a vehicle event.
     * 5. Test if the second device connects (in addition to the already connected first device)
     * @throws Exception
     */
    @Test
    public void testMultiDeviceConnectWithOneConnected() throws Exception {
        createAndSetupBluetoothPolicy();
        BluetoothDevice device1 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:01");
        BluetoothDevice device2 = mBluetoothAdapter.getRemoteDevice("DE:AD:BE:EF:00:02");
        BluetoothDevice[] testDevices = new BluetoothDevice[]{device2, device1};

        // Pair both devices and leave them disconnected.
        for (BluetoothDevice device : testDevices) {
            mockDeviceAvailability(device, true);
            pairDevice(device);
            sendFakeConnectionStateChange(device, false);
        }

        // Mock the second device to be unavailable for connection.
        mockDeviceAvailability(device2, false);
        triggerFakeVehicleEvent();
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);
        // At this point device1 should have connected and device2 disconnected, since it is mocked
        // to be out of range (unavailable)
        // Now bring device2 in range (mock it to be available)
        mockDeviceAvailability(device2, true);
        triggerFakeVehicleEvent();
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);

        // Now device2 should be connected on the HFP, but not on A2DP (since it supports only
        // 1 connection)
        // There should have been 2 connection attempts on the device2 - the first one unsuccessful
        // due to its unavailability and the second one successful.
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(2)).bluetoothConnectToProfile(
                BluetoothProfile.HEADSET_CLIENT, device2);
        // There should be only 1 connection attempt on device1 - since it is available to connect
        // from the beginning.  The first connection attempt on the first vehicle event should have
        // been successful. For the second vehicle event, we should not have tried to connect on
        // device1 - this tests if we try to connect on already connected devices.
        verify(mockBluetoothUserService,
                Mockito.timeout(CONNECTION_REQUEST_TIMEOUT).times(1)).bluetoothConnectToProfile(
                BluetoothProfile.HEADSET_CLIENT, device1);
        verify(mockBluetoothUserService, Mockito.never()).bluetoothConnectToProfile(
                BluetoothProfile.A2DP_SINK, device2);
        Thread.sleep(WAIT_FOR_COMPLETION_TIME);
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device1, false));
        mReceiver.onReceive(null, createBluetoothBondStateChangedIntent(device2, false));
    }
}
