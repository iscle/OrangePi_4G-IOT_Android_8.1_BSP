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

package com.android.cts.verifierusbcompanion;

import static org.junit.Assert.assertEquals;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.support.annotation.NonNull;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Companion code for com.android.cts.verifier.usb.device.UsbAccessoryTestActivity
 */
class AccessoryTestCompanion extends TestCompanion {
    private static final int TIMEOUT_MILLIS = 500;
    private static final int MAX_BUFFER_SIZE = 16384;
    private static final int TEST_DATA_SIZE_THRESHOLD = 100 * 1024 * 1024; // 100MB

    private static final String ACTION_USB_PERMISSION =
            "com.android.cts.verifierusbcompanion.USB_PERMISSION";

    private UsbManager mUsbManager;
    private BroadcastReceiver mUsbDeviceConnectionReceiver;
    private UsbDevice mDevice;

    AccessoryTestCompanion(@NonNull Context context, @NonNull TestObserver observer) {
        super(context, observer);
    }

    /**
     * @throws Throwable
     */
    @Override
    protected void runTest() throws Throwable {
        updateStatus("Waiting for device under test to connect");

        mUsbManager = getContext().getSystemService(UsbManager.class);

        mUsbDeviceConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (AccessoryTestCompanion.this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    switch (intent.getAction()) {
                        case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                            if (mUsbManager.hasPermission(device)) {
                                onDeviceAccessPermitted(device);
                            } else {
                                mUsbManager.requestPermission(device,
                                        PendingIntent.getBroadcast(getContext(), 0,
                                                new Intent(ACTION_USB_PERMISSION), 0));
                            }
                            break;
                        case ACTION_USB_PERMISSION:
                            boolean granted = intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED, false);

                            if (granted) {
                                onDeviceAccessPermitted(device);
                            } else {
                                fail("Permission to connect to " + device.getProductName()
                                        + " not granted");
                            }
                            break;
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        getContext().registerReceiver(mUsbDeviceConnectionReceiver, filter);

        synchronized (this) {
            while (mDevice == null) {
                wait();
            }
        }

        UsbInterface iface = null;
        for (int i = 0; i < mDevice.getConfigurationCount(); i++) {
            if (mDevice.getInterface(i).getName().equals("Android Accessory Interface")) {
                iface = mDevice.getInterface(i);
                break;
            }
        }

        UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);

        UsbDeviceConnection connection = mUsbManager.openDevice(mDevice);

        try {
            String testName;
            do {
                testName = nextTest(connection, in, out, true);

                updateStatus("Running test \"" + testName + "\"");

                switch (testName) {
                    case "echo 32 bytes": {
                        byte[] buffer = new byte[32];

                        int numTransferred = connection.bulkTransfer(in, buffer, 32, 0);
                        assertEquals(32, numTransferred);

                        numTransferred = connection.bulkTransfer(out, buffer, 32, 0);
                        assertEquals(32, numTransferred);
                    }
                    break;

                    case "echo two 16 byte transfers as one": {
                        byte[] buffer = new byte[48];

                        // We receive the individual transfers even if we wait for more data
                        int numTransferred = connection.bulkTransfer(in, buffer, 32, 0);
                        assertEquals(16, numTransferred);
                        numTransferred = connection.bulkTransfer(in, buffer, 16, 32, 0);
                        assertEquals(16, numTransferred);

                        numTransferred = connection.bulkTransfer(out, buffer, 32, 0);
                        assertEquals(32, numTransferred);
                    }
                    break;

                    case "echo 32 bytes as two 16 byte transfers": {
                        byte[] buffer = new byte[32];

                        int numTransferred = connection.bulkTransfer(in, buffer, 32, 0);
                        assertEquals(32, numTransferred);

                        numTransferred = connection.bulkTransfer(out, buffer, 16, 0);
                        assertEquals(16, numTransferred);
                        numTransferred = connection.bulkTransfer(out, buffer, 16, 16, 0);
                        assertEquals(16, numTransferred);
                    }
                    break;

                    case "measure out transfer speed": {
                        byte[] buffer = new byte[MAX_BUFFER_SIZE];

                        long bytesRead = 0;
                        while (bytesRead < TEST_DATA_SIZE_THRESHOLD) {
                            int numTransferred = connection.bulkTransfer(
                                    in, buffer, MAX_BUFFER_SIZE, 0);
                            bytesRead += numTransferred;
                        }

                        // MAX_BUFFER_SIZE is a multiple of the package size, hence we get a zero
                        // sized package after. Some older devices do not send these packages, but
                        // this is not compliant anymore.
                        int numTransferred = connection.bulkTransfer(in, buffer, 1, TIMEOUT_MILLIS);
                        assertEquals(0, numTransferred);

                        byte[] confirm = new byte[] {1};
                        numTransferred = connection.bulkTransfer(out, confirm, 1, 0);
                        assertEquals(1, numTransferred);
                    }
                    break;

                    case "measure in transfer speed": {
                        byte[] buffer = new byte[MAX_BUFFER_SIZE];

                        long bytesWritten = 0;
                        int numTransferred = 0;
                        while (bytesWritten < TEST_DATA_SIZE_THRESHOLD) {
                            numTransferred =
                                    connection.bulkTransfer(out, buffer, MAX_BUFFER_SIZE, 0);
                            assertEquals(MAX_BUFFER_SIZE, numTransferred);
                            bytesWritten += numTransferred;
                        }

                        byte[] confirm = new byte[] {1};
                        numTransferred = connection.bulkTransfer(out, confirm, 1, 0);
                        assertEquals(1, numTransferred);
                    }
                    break;

                    case "echo max bytes": {
                        byte[] buffer = new byte[MAX_BUFFER_SIZE];

                        int numTransferred = connection.bulkTransfer(in, buffer, MAX_BUFFER_SIZE,
                                0);
                        assertEquals(MAX_BUFFER_SIZE, numTransferred);

                        // MAX_BUFFER_SIZE is a multiple of the package size, hence we get a zero
                        // sized package after. Some older devices do not send these packages, but
                        // this is not compliant anymore.
                        numTransferred = connection.bulkTransfer(in, buffer, 1, TIMEOUT_MILLIS);
                        assertEquals(0, numTransferred);

                        numTransferred = connection.bulkTransfer(out, buffer, MAX_BUFFER_SIZE, 0);
                        assertEquals(MAX_BUFFER_SIZE, numTransferred);
                    }
                    break;

                    case "echo max*2 bytes": {
                        byte[] buffer = new byte[MAX_BUFFER_SIZE * 2];

                        int numTransferred = connection.bulkTransfer(in, buffer, MAX_BUFFER_SIZE,
                                0);
                        assertEquals(MAX_BUFFER_SIZE, numTransferred);

                        // Oversized transfers get split into two
                        numTransferred = connection.bulkTransfer(in, buffer, MAX_BUFFER_SIZE,
                                MAX_BUFFER_SIZE, 0);
                        assertEquals(MAX_BUFFER_SIZE, numTransferred);

                        // MAX_BUFFER_SIZE is a multiple of the package size, hence we get a zero
                        // sized package after. Some older devices do not send these packages, but
                        // this is not compliant anymore.
                        numTransferred = connection.bulkTransfer(in, buffer, 1, TIMEOUT_MILLIS);
                        assertEquals(0, numTransferred);

                        numTransferred = connection.bulkTransfer(out, buffer, MAX_BUFFER_SIZE, 100);
                        assertEquals(MAX_BUFFER_SIZE, numTransferred);

                        numTransferred = connection.bulkTransfer(out, buffer, MAX_BUFFER_SIZE,
                                MAX_BUFFER_SIZE, 0);
                        assertEquals(MAX_BUFFER_SIZE, numTransferred);
                    }
                    break;

                    default:
                        break;
                }
            } while (!testName.equals("done"));
        } finally {
            connection.close();
        }
    }

    /**
     * If access to a device was permitted either make the device an accessory if it already is,
     * start the test.
     *
     * @param device The device access was permitted to
     */
    private void onDeviceAccessPermitted(@NonNull UsbDevice device) {
        if (!AoapInterface.isDeviceInAoapMode(device)) {
            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            try {
                makeThisDeviceAnAccessory(connection);
            } finally {
                connection.close();
            }
        } else {
            getContext().unregisterReceiver(mUsbDeviceConnectionReceiver);
            mUsbDeviceConnectionReceiver = null;

            synchronized (AccessoryTestCompanion.this) {
                mDevice = device;

                AccessoryTestCompanion.this.notifyAll();
            }
        }
    }

    @NonNull private String nextTest(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out, boolean isSuccess) {
        byte[] sizeBuffer = new byte[1];

        updateStatus("Waiting for next test");

        int numTransferred = connection.bulkTransfer(in, sizeBuffer, 1, 0);
        assertEquals(1, numTransferred);

        int nameSize = sizeBuffer[0];

        byte[] nameBuffer = new byte[nameSize];
        numTransferred = connection.bulkTransfer(in, nameBuffer, nameSize, 0);
        assertEquals(nameSize, numTransferred);

        numTransferred = connection.bulkTransfer(out, new byte[]{(byte) (isSuccess ? 1 : 0)}, 1, 0);
        assertEquals(1, numTransferred);

        numTransferred = connection.bulkTransfer(in, new byte[1], 1, 0);
        assertEquals(1, numTransferred);

        String name = Charset.forName("UTF-8").decode(ByteBuffer.wrap(nameBuffer)).toString();

        updateStatus("Next test is " + name);

        return name;
    }

    /**
     * Search an {@link UsbInterface} for an {@link UsbEndpoint endpoint} of a certain direction.
     *
     * @param iface     The interface to search
     * @param direction The direction the endpoint is for.
     *
     * @return The first endpoint found or {@link null}.
     */
    @NonNull private UsbEndpoint getEndpoint(@NonNull UsbInterface iface, int direction) {
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint ep = iface.getEndpoint(i);
            if (ep.getDirection() == direction) {
                return ep;
            }
        }

        throw new IllegalStateException("Could not find " + direction + " endpoint in "
                + iface.getName());
    }

    /**
     * Converts the device under test into an Android accessory. Accessories are USB hosts that are
     * detected on the device side via {@link UsbManager#getAccessoryList()}.
     *
     * @param connection The connection to the USB device
     */
    private void makeThisDeviceAnAccessory(@NonNull UsbDeviceConnection connection) {
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_MANUFACTURER,
                "Android CTS");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_MODEL,
                "Android CTS test companion device");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_DESCRIPTION,
                "Android device running CTS verifier");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_VERSION, "2");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_URI,
                "https://source.android.com/compatibility/cts/verifier.html");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_SERIAL, "0");
        AoapInterface.sendAoapStart(connection);
    }
}
