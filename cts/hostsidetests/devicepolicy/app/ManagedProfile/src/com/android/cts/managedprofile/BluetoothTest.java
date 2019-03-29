/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.managedprofile;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.test.AndroidTestCase;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Test that the basic bluetooth API is callable in managed profiles.
 * These tests should only be executed if the device supports bluetooth,
 * i.e. if it has the {@link android.content.pm.PackageManager#FEATURE_BLUETOOTH} feature.
 *
 * This includes tests for the {@link BluetoothAdapter}.
 * The corresponding CTS tests in the primary profile are in
 * {@link android.bluetooth.cts.BasicAdapterTest}.
 * TODO: Merge the primary and managed profile tests into one.
 */
public class BluetoothTest extends AndroidTestCase {
    private static final int DISABLE_TIMEOUT_MS = 8000;
    private static final int ENABLE_TIMEOUT_MS = 10000;
    private static final int POLL_TIME_MS = 400;
    private static final int CHECK_WAIT_TIME_MS = 1000;

    private BluetoothAdapter mAdapter;
    private boolean mBtWasEnabled;

    public void setUp() throws Exception {
        super.setUp();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertNotNull(mAdapter);
        mBtWasEnabled = mAdapter.isEnabled();
    }

    public void tearDown() throws Exception {
        if (mBtWasEnabled != mAdapter.isEnabled()) {
            if (mBtWasEnabled) {
                enable();
            } else {
                disable();
            }
        }
        super.tearDown();
    }

    /**
     * Checks enable(), disable(), getState(), isEnabled()
     */
    public void testEnableDisable() {
        disable();
        enable();
    }

    /**
     * Test the getAddress() function.
     */
    public void testGetAddress() {
        assertTrue(BluetoothAdapter.checkBluetoothAddress(mAdapter.getAddress()));
    }

    /**
     * Tests the listenUsingRfcommWithServiceRecord function.
     */
    public void testListenUsingRfcommWithServiceRecord() throws IOException {
        enable();
        BluetoothServerSocket socket = mAdapter.listenUsingRfcommWithServiceRecord(
                "test", UUID.randomUUID());
        assertNotNull(socket);
        socket.close();
    }

    /**
     * Test the getRemoteDevice() function.
     */
    public void testGetRemoteDevice() {
        // getRemoteDevice() should work even with Bluetooth disabled
        disable();

        // test bad addresses
        try {
            mAdapter.getRemoteDevice((String)null);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
        try {
            mAdapter.getRemoteDevice("00:00:00:00:00:00:00:00");
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
        try {
            mAdapter.getRemoteDevice((byte[])null);
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }
        try {
            mAdapter.getRemoteDevice(new byte[] {0x00, 0x00, 0x00, 0x00, 0x00});
            fail("IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
        }

        // test success
        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertNotNull(device);
        assertEquals("00:11:22:AA:BB:CC", device.getAddress());
        device = mAdapter.getRemoteDevice(
                new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06});
        assertNotNull(device);
        assertEquals("01:02:03:04:05:06", device.getAddress());
    }

    /**
     * Helper to turn BT off.
     * This method will either fail on an assert, or return with BT turned off.
     * Behavior of getState() and isEnabled() are validated along the way.
     */
    private void disable() {
        sleep(CHECK_WAIT_TIME_MS);
        if (mAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            assertFalse(mAdapter.isEnabled());
            return;
        }

        assertEquals(BluetoothAdapter.STATE_ON, mAdapter.getState());
        assertTrue(mAdapter.isEnabled());
        assertTrue(mAdapter.disable());
        boolean turnOff = false;
        for (int i=0; i<DISABLE_TIMEOUT_MS/POLL_TIME_MS; i++) {
            sleep(POLL_TIME_MS);
            int state = mAdapter.getState();
            switch (state) {
            case BluetoothAdapter.STATE_OFF:
                assertFalse(mAdapter.isEnabled());
                return;
            default:
                if (state != BluetoothAdapter.STATE_ON || turnOff) {
                    assertEquals(BluetoothAdapter.STATE_TURNING_OFF, state);
                    turnOff = true;
                }
                break;
            }
        }
        fail("disable() timeout");
    }

    /**
     * Helper to turn BT on.
     * This method will either fail on an assert, or return with BT turned on.
     * Behavior of getState() and isEnabled() are validated along the way.
     */
    private void enable() {
        sleep(CHECK_WAIT_TIME_MS);
        if (mAdapter.getState() == BluetoothAdapter.STATE_ON) {
            assertTrue(mAdapter.isEnabled());
            return;
        }

        assertEquals(BluetoothAdapter.STATE_OFF, mAdapter.getState());
        assertFalse(mAdapter.isEnabled());
        assertTrue(mAdapter.enable());
        boolean turnOn = false;
        for (int i=0; i<ENABLE_TIMEOUT_MS/POLL_TIME_MS; i++) {
            sleep(POLL_TIME_MS);
            int state = mAdapter.getState();
            switch (state) {
            case BluetoothAdapter.STATE_ON:
                assertTrue(mAdapter.isEnabled());
                return;
            default:
                if (state != BluetoothAdapter.STATE_OFF || turnOn) {
                    assertEquals(BluetoothAdapter.STATE_TURNING_ON, state);
                    turnOn = true;
                }
                break;
            }
        }
        fail("enable() timeout");
    }

    private void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {}
    }
}
