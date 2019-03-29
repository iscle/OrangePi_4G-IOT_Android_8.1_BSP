/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.android.cts.verifier.usb.device;

import static com.android.cts.verifier.usb.Util.runAndAssertException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import org.junit.AssumptionViolatedException;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class UsbDeviceTestActivity extends PassFailButtons.Activity {
    private static final String ACTION_USB_PERMISSION =
            "com.android.cts.verifier.usb.device.USB_PERMISSION";
    private static final String LOG_TAG = UsbDeviceTestActivity.class.getSimpleName();
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int MAX_BUFFER_SIZE = 16384;
    private static final int OVERSIZED_BUFFER_SIZE = MAX_BUFFER_SIZE + 100;

    private UsbManager mUsbManager;
    private BroadcastReceiver mUsbDeviceConnectionReceiver;
    private Thread mTestThread;
    private TextView mStatus;
    private ProgressBar mProgress;

    /**
     * Some N and older accessories do not send a zero sized package after a request that is a
     * multiple of the maximum package size.
     */
    private boolean mDoesCompanionZeroTerminate;

    private static long now() {
        return System.nanoTime() / 1000000;
    }

    /**
     * Check if we should expect a zero sized transfer after a certain sized transfer
     *
     * @param transferSize The size of the previous transfer
     *
     * @return {@code true} if a zero sized transfer is expected
     */
    private boolean isZeroTransferExpected(int transferSize) {
        if (mDoesCompanionZeroTerminate) {
            if (transferSize % 1024 == 0) {
                if (transferSize % 8 != 0) {
                    throw new IllegalArgumentException("As the transfer speed is unknown the code "
                            + "has to work for all speeds");
                }

                return true;
            }
        }

        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.usb_main);
        setInfoResources(R.string.usb_device_test, R.string.usb_device_test_info, -1);

        mStatus = (TextView) findViewById(R.id.status);
        mProgress = (ProgressBar) findViewById(R.id.progress_bar);

        mUsbManager = getSystemService(UsbManager.class);

        getPassButton().setEnabled(false);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);

        mStatus.setText(R.string.usb_device_test_step1);

        mUsbDeviceConnectionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (UsbDeviceTestActivity.this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    switch (intent.getAction()) {
                        case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                            if (!AoapInterface.isDeviceInAoapMode(device)) {
                                mStatus.setText(R.string.usb_device_test_step2);
                            }

                            mUsbManager.requestPermission(device,
                                    PendingIntent.getBroadcast(UsbDeviceTestActivity.this, 0,
                                            new Intent(ACTION_USB_PERMISSION), 0));
                            break;
                        case ACTION_USB_PERMISSION:
                            boolean granted = intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED, false);

                            if (granted) {
                                if (!AoapInterface.isDeviceInAoapMode(device)) {
                                    mStatus.setText(R.string.usb_device_test_step3);

                                    UsbDeviceConnection connection = mUsbManager.openDevice(device);
                                    try {
                                        makeThisDeviceAnAccessory(connection);
                                    } finally {
                                        connection.close();
                                    }
                                } else {
                                    mStatus.setText(R.string.usb_device_test_step4);
                                    mProgress.setIndeterminate(true);
                                    mProgress.setVisibility(View.VISIBLE);

                                    unregisterReceiver(mUsbDeviceConnectionReceiver);
                                    mUsbDeviceConnectionReceiver = null;

                                    // Do not run test on main thread
                                    mTestThread = new Thread() {
                                        @Override
                                        public void run() {
                                            runTests(device);
                                        }
                                    };

                                    mTestThread.start();
                                }
                            } else {
                                fail("Permission to connect to " + device.getProductName()
                                        + " not granted", null);
                            }
                            break;
                    }
                }
            }
        };

        registerReceiver(mUsbDeviceConnectionReceiver, filter);
    }

    /**
     * Indicate that the test failed.
     */
    private void fail(@Nullable String s, @Nullable Throwable e) {
        Log.e(LOG_TAG, s, e);
        setTestResultAndFinish(false);
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
                "Android device under CTS test");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_DESCRIPTION,
                "Android device running CTS verifier");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_VERSION, "2");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_URI,
                "https://source.android.com/compatibility/cts/verifier.html");
        AoapInterface.sendString(connection, AoapInterface.ACCESSORY_STRING_SERIAL, "0");
        AoapInterface.sendAoapStart(connection);
    }

    /**
     * Switch to next test.
     *
     * @param connection   Connection to the USB device
     * @param in           The in endpoint
     * @param out          The out endpoint
     * @param nextTestName The name of the new test
     */
    private void nextTest(@NonNull UsbDeviceConnection connection, @NonNull UsbEndpoint in,
            @NonNull UsbEndpoint out, @NonNull CharSequence nextTestName) {
        Log.v(LOG_TAG, "Finishing previous test");

        // Make sure name length is not a multiple of 8 to avoid zero-termination issues
        StringBuilder safeNextTestName = new StringBuilder(nextTestName);
        if (nextTestName.length() % 8 == 0) {
            safeNextTestName.append(' ');
        }

        // Send name of next test
        assertTrue(safeNextTestName.length() <= Byte.MAX_VALUE);
        ByteBuffer nextTestNameBuffer = Charset.forName("UTF-8")
                .encode(CharBuffer.wrap(safeNextTestName));
        byte[] sizeBuffer = { (byte) nextTestNameBuffer.limit() };
        int numSent = connection.bulkTransfer(out, sizeBuffer, 1, 0);
        assertEquals(1, numSent);

        numSent = connection.bulkTransfer(out, nextTestNameBuffer.array(),
                nextTestNameBuffer.limit(), 0);
        assertEquals(nextTestNameBuffer.limit(), numSent);

        // Receive result of last test
        byte[] lastTestResultBytes = new byte[1];
        int numReceived = connection.bulkTransfer(in, lastTestResultBytes,
                lastTestResultBytes.length, TIMEOUT_MILLIS);
        assertEquals(1, numReceived);
        assertEquals(1, lastTestResultBytes[0]);

        // Send ready signal
        sizeBuffer[0] = 42;
        numSent = connection.bulkTransfer(out, sizeBuffer, 1, 0);
        assertEquals(1, numSent);

        Log.i(LOG_TAG, "Running test \"" + safeNextTestName + "\"");
    }

    /**
     * Receive a transfer that has size zero using bulk-transfer.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     */
    private void receiveZeroSizedTransfer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in) {
        byte[] buffer = new byte[1];
        int numReceived = connection.bulkTransfer(in, buffer, 1, TIMEOUT_MILLIS);
        assertEquals(0, numReceived);
    }

    /**
     * Send some data and expect it to be echoed back.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param out        The out endpoint
     * @param size       The number of bytes to send
     */
    private void echoBulkTransfer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out, int size) {
        byte[] sentBuffer = new byte[size];
        Random r = new Random();
        r.nextBytes(sentBuffer);

        int numSent = connection.bulkTransfer(out, sentBuffer, sentBuffer.length, 0);
        assertEquals(size, numSent);

        byte[] receivedBuffer = new byte[size];
        int numReceived = connection.bulkTransfer(in, receivedBuffer, receivedBuffer.length,
                TIMEOUT_MILLIS);
        assertEquals(size, numReceived);

        assertArrayEquals(sentBuffer, receivedBuffer);

        if (isZeroTransferExpected(size)) {
            receiveZeroSizedTransfer(connection, in);
        }
    }

    /**
     * Send some data and expect it to be echoed back (but have an offset in the send buffer).
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param out        The out endpoint
     * @param size       The number of bytes to send
     */
    private void echoBulkTransferOffset(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out, int offset, int size) {
        byte[] sentBuffer = new byte[offset + size];
        Random r = new Random();
        r.nextBytes(sentBuffer);

        int numSent = connection.bulkTransfer(out, sentBuffer, offset, size, 0);
        assertEquals(size, numSent);

        byte[] receivedBuffer = new byte[offset + size];
        int numReceived = connection.bulkTransfer(in, receivedBuffer, offset, size, TIMEOUT_MILLIS);
        assertEquals(size, numReceived);

        for (int i = 0; i < offset + size; i++) {
            if (i < offset) {
                assertEquals(0, receivedBuffer[i]);
            } else {
                assertEquals(sentBuffer[i], receivedBuffer[i]);
            }
        }

        if (isZeroTransferExpected(size)) {
            receiveZeroSizedTransfer(connection, in);
        }
    }

    /**
     * Send a transfer that is larger than MAX_BUFFER_SIZE.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param out        The out endpoint
     */
    private void echoOversizedBulkTransfer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out) {
        int totalSize = OVERSIZED_BUFFER_SIZE;
        byte[] sentBuffer = new byte[totalSize];
        Random r = new Random();
        r.nextBytes(sentBuffer);

        int numSent = connection.bulkTransfer(out, sentBuffer, sentBuffer.length, 0);

        // Buffer will only be partially transferred
        assertEquals(MAX_BUFFER_SIZE, numSent);

        byte[] receivedBuffer = new byte[totalSize];
        int numReceived = connection.bulkTransfer(in, receivedBuffer, receivedBuffer.length,
                TIMEOUT_MILLIS);

        // All beyond MAX_BUFFER_SIZE was not send, hence it will not be echoed back
        assertEquals(MAX_BUFFER_SIZE, numReceived);

        for (int i = 0; i < totalSize; i++) {
            if (i < MAX_BUFFER_SIZE) {
                assertEquals(sentBuffer[i], receivedBuffer[i]);
            } else {
                assertEquals(0, receivedBuffer[i]);
            }
        }

        if (mDoesCompanionZeroTerminate) {
            receiveZeroSizedTransfer(connection, in);
        }
    }

    /**
     * Receive a transfer that is larger than MAX_BUFFER_SIZE
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     */
    private void receiveOversizedBulkTransfer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in) {
        // Buffer will be received as two transfers
        byte[] receivedBuffer1 = new byte[OVERSIZED_BUFFER_SIZE];
        int numReceived = connection.bulkTransfer(in, receivedBuffer1, receivedBuffer1.length,
                TIMEOUT_MILLIS);
        assertEquals(MAX_BUFFER_SIZE, numReceived);

        byte[] receivedBuffer2 = new byte[OVERSIZED_BUFFER_SIZE - MAX_BUFFER_SIZE];
        numReceived = connection.bulkTransfer(in, receivedBuffer2, receivedBuffer2.length,
                TIMEOUT_MILLIS);
        assertEquals(OVERSIZED_BUFFER_SIZE - MAX_BUFFER_SIZE, numReceived);

        assertEquals(1, receivedBuffer1[0]);
        assertEquals(2, receivedBuffer1[MAX_BUFFER_SIZE - 1]);
        assertEquals(3, receivedBuffer2[0]);
        assertEquals(4, receivedBuffer2[OVERSIZED_BUFFER_SIZE - MAX_BUFFER_SIZE - 1]);
    }

    /**
     * Receive data but supply an empty buffer. This causes the thread to block until any data is
     * sent. The zero-sized receive-transfer just returns without data and the next transfer can
     * actually read the data.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     * @param buffer     The buffer to use
     * @param offset     The offset into the buffer
     * @param length     The lenght of data to receive
     */
    private void receiveWithEmptyBuffer(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @Nullable byte[] buffer, int offset, int length) {
        long startTime = now();
        int numReceived;
        if (offset == 0) {
            numReceived = connection.bulkTransfer(in, buffer, length, 0);
        } else {
            numReceived = connection.bulkTransfer(in, buffer, offset, length, 0);
        }
        long endTime = now();
        assertEquals(-1, numReceived);

        // The transfer should block
        assertTrue(endTime - startTime > 100);

        numReceived = connection.bulkTransfer(in, new byte[1], 1, 0);
        assertEquals(1, numReceived);
    }

    /**
     * Tests {@link UsbDeviceConnection#controlTransfer}.
     *
     * <p>Note: We cannot send ctrl data to the device as it thinks it talks to an accessory, hence
     * the testing is currently limited.</p>
     *
     * @param connection The connection to use for testing
     *
     * @throws Throwable
     */
    private void ctrlTransferTests(@NonNull UsbDeviceConnection connection) throws Throwable {
        runAndAssertException(() -> connection.controlTransfer(0, 0, 0, 0, null, 1, 0),
                IllegalArgumentException.class);

        runAndAssertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], -1, 0),
                IllegalArgumentException.class);

        runAndAssertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], 2, 0),
                IllegalArgumentException.class);

        runAndAssertException(() -> connection.controlTransfer(0, 0, 0, 0, null, 0, 1, 0),
                IllegalArgumentException.class);

        runAndAssertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], 0, -1, 0),
                IllegalArgumentException.class);

        runAndAssertException(() -> connection.controlTransfer(0, 0, 0, 0, new byte[1], 1, 1, 0),
                IllegalArgumentException.class);
    }

    /**
     * Search an {@link UsbInterface} for an {@link UsbEndpoint endpoint} of a certain direction.
     *
     * @param iface     The interface to search
     * @param direction The direction the endpoint is for.
     *
     * @return The first endpoint found or {@link null}.
     */
    private @NonNull UsbEndpoint getEndpoint(@NonNull UsbInterface iface, int direction) {
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
     * Receive a transfer that has size zero using deprecated usb-request methods.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     */
    private void receiveZeroSizeRequestLegacy(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in) {
        UsbRequest receiveZero = new UsbRequest();
        boolean isInited = receiveZero.initialize(connection, in);
        assertTrue(isInited);
        ByteBuffer zeroBuffer = ByteBuffer.allocate(1);
        receiveZero.queue(zeroBuffer, 1);

        UsbRequest finished = connection.requestWait();
        assertEquals(receiveZero, finished);
        assertEquals(0, zeroBuffer.position());
    }

    /**
     * Send a USB request using the {@link UsbRequest#queue legacy path} and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     * @param size            The size of the request to send
     * @param originalSize    The size of the original buffer
     * @param sliceStart      The start of the final buffer in the original buffer
     * @param sliceEnd        The end of the final buffer in the original buffer
     * @param positionInSlice The position parameter in the final buffer
     * @param limitInSlice    The limited parameter in the final buffer
     * @param useDirectBuffer If the buffer to be used should be a direct buffer
     */
    private void echoUsbRequestLegacy(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out, int size, int originalSize,
            int sliceStart, int sliceEnd, int positionInSlice, int limitInSlice,
            boolean useDirectBuffer) {
        Random random = new Random();

        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);
        Object sentClientData = new Object();
        sent.setClientData(sentClientData);

        UsbRequest receive = new UsbRequest();
        isInited = receive.initialize(connection, in);
        assertTrue(isInited);
        Object receiveClientData = new Object();
        receive.setClientData(receiveClientData);

        ByteBuffer bufferSent;
        if (useDirectBuffer) {
            bufferSent = ByteBuffer.allocateDirect(originalSize);
        } else {
            bufferSent = ByteBuffer.allocate(originalSize);
        }
        for (int i = 0; i < originalSize; i++) {
            bufferSent.put((byte) random.nextInt());
        }
        bufferSent.position(sliceStart);
        bufferSent.limit(sliceEnd);
        ByteBuffer bufferSentSliced = bufferSent.slice();
        bufferSentSliced.position(positionInSlice);
        bufferSentSliced.limit(limitInSlice);

        bufferSent.position(0);
        bufferSent.limit(originalSize);

        ByteBuffer bufferReceived;
        if (useDirectBuffer) {
            bufferReceived = ByteBuffer.allocateDirect(originalSize);
        } else {
            bufferReceived = ByteBuffer.allocate(originalSize);
        }
        bufferReceived.position(sliceStart);
        bufferReceived.limit(sliceEnd);
        ByteBuffer bufferReceivedSliced = bufferReceived.slice();
        bufferReceivedSliced.position(positionInSlice);
        bufferReceivedSliced.limit(limitInSlice);

        bufferReceived.position(0);
        bufferReceived.limit(originalSize);

        boolean wasQueued = receive.queue(bufferReceivedSliced, size);
        assertTrue(wasQueued);
        wasQueued = sent.queue(bufferSentSliced, size);
        assertTrue(wasQueued);

        for (int reqRun = 0; reqRun < 2; reqRun++) {
            UsbRequest finished;

            try {
                finished = connection.requestWait();
            } catch (BufferOverflowException e) {
                if (size > bufferSentSliced.limit() || size > bufferReceivedSliced.limit()) {
                    Log.e(LOG_TAG, "Expected failure", e);
                    continue;
                } else {
                    throw e;
                }
            }

            // Should we have gotten a failure?
            if (finished == receive) {
                // We should have gotten an exception if size > limit
                assumeTrue(bufferReceivedSliced.limit() >= size);

                assertEquals(size, bufferReceivedSliced.position());

                for (int i = 0; i < size; i++) {
                    if (i < size) {
                        assertEquals(bufferSent.get(i), bufferReceived.get(i));
                    } else {
                        assertEquals(0, bufferReceived.get(i));
                    }
                }

                assertSame(receiveClientData, finished.getClientData());
                assertSame(in, finished.getEndpoint());
            } else {
                assertEquals(size, bufferSentSliced.position());

                // We should have gotten an exception if size > limit
                assumeTrue(bufferSentSliced.limit() >= size);
                assertSame(sent, finished);
                assertSame(sentClientData, finished.getClientData());
                assertSame(out, finished.getEndpoint());
            }
            finished.close();
        }

        if (isZeroTransferExpected(size)) {
            receiveZeroSizeRequestLegacy(connection, in);
        }
    }

    /**
     * Receive a transfer that has size zero using current usb-request methods.
     *
     * @param connection Connection to the USB device
     * @param in         The in endpoint
     */
    private void receiveZeroSizeRequest(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in) {
        UsbRequest receiveZero = new UsbRequest();
        boolean isInited = receiveZero.initialize(connection, in);
        assertTrue(isInited);
        ByteBuffer zeroBuffer = ByteBuffer.allocate(1);
        receiveZero.queue(zeroBuffer);

        UsbRequest finished = connection.requestWait();
        assertEquals(receiveZero, finished);
        assertEquals(0, zeroBuffer.position());
    }

    /**
     * Send a USB request and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     * @param originalSize    The size of the original buffer
     * @param sliceStart      The start of the final buffer in the original buffer
     * @param sliceEnd        The end of the final buffer in the original buffer
     * @param positionInSlice The position parameter in the final buffer
     * @param limitInSlice    The limited parameter in the final buffer
     * @param useDirectBuffer If the buffer to be used should be a direct buffer
     */
    private void echoUsbRequest(@NonNull UsbDeviceConnection connection, @NonNull UsbEndpoint in,
            @NonNull UsbEndpoint out, int originalSize, int sliceStart, int sliceEnd,
            int positionInSlice, int limitInSlice, boolean useDirectBuffer,
            boolean makeSendBufferReadOnly) {
        Random random = new Random();

        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);
        Object sentClientData = new Object();
        sent.setClientData(sentClientData);

        UsbRequest receive = new UsbRequest();
        isInited = receive.initialize(connection, in);
        assertTrue(isInited);
        Object receiveClientData = new Object();
        receive.setClientData(receiveClientData);

        ByteBuffer bufferSent;
        if (useDirectBuffer) {
            bufferSent = ByteBuffer.allocateDirect(originalSize);
        } else {
            bufferSent = ByteBuffer.allocate(originalSize);
        }
        for (int i = 0; i < originalSize; i++) {
            bufferSent.put((byte) random.nextInt());
        }
        if (makeSendBufferReadOnly) {
            bufferSent = bufferSent.asReadOnlyBuffer();
        }
        bufferSent.position(sliceStart);
        bufferSent.limit(sliceEnd);
        ByteBuffer bufferSentSliced = bufferSent.slice();
        bufferSentSliced.position(positionInSlice);
        bufferSentSliced.limit(limitInSlice);

        bufferSent.position(0);
        bufferSent.limit(originalSize);

        ByteBuffer bufferReceived;
        if (useDirectBuffer) {
            bufferReceived = ByteBuffer.allocateDirect(originalSize);
        } else {
            bufferReceived = ByteBuffer.allocate(originalSize);
        }
        bufferReceived.position(sliceStart);
        bufferReceived.limit(sliceEnd);
        ByteBuffer bufferReceivedSliced = bufferReceived.slice();
        bufferReceivedSliced.position(positionInSlice);
        bufferReceivedSliced.limit(limitInSlice);

        bufferReceived.position(0);
        bufferReceived.limit(originalSize);

        boolean wasQueued = receive.queue(bufferReceivedSliced);
        assertTrue(wasQueued);
        wasQueued = sent.queue(bufferSentSliced);
        assertTrue(wasQueued);

        for (int reqRun = 0; reqRun < 2; reqRun++) {
            UsbRequest finished = connection.requestWait();

            if (finished == receive) {
                assertEquals(limitInSlice, bufferReceivedSliced.limit());
                assertEquals(limitInSlice, bufferReceivedSliced.position());

                for (int i = 0; i < originalSize; i++) {
                    if (i >= sliceStart + positionInSlice && i < sliceStart + limitInSlice) {
                        assertEquals(bufferSent.get(i), bufferReceived.get(i));
                    } else {
                        assertEquals(0, bufferReceived.get(i));
                    }
                }

                assertSame(receiveClientData, finished.getClientData());
                assertSame(in, finished.getEndpoint());
            } else {
                assertEquals(limitInSlice, bufferSentSliced.limit());
                assertEquals(limitInSlice, bufferSentSliced.position());

                assertSame(sent, finished);
                assertSame(sentClientData, finished.getClientData());
                assertSame(out, finished.getEndpoint());
            }
            finished.close();
        }

        if (isZeroTransferExpected(sliceStart + limitInSlice - (sliceStart + positionInSlice))) {
            receiveZeroSizeRequest(connection, in);
        }
    }

    /**
     * Send a USB request using the {@link UsbRequest#queue legacy path} and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     * @param size            The size of the request to send
     * @param useDirectBuffer If the buffer to be used should be a direct buffer
     */
    private void echoUsbRequestLegacy(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out, int size, boolean useDirectBuffer) {
        echoUsbRequestLegacy(connection, in, out, size, size, 0, size, 0, size, useDirectBuffer);
    }

    /**
     * Send a USB request and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     * @param size            The size of the request to send
     * @param useDirectBuffer If the buffer to be used should be a direct buffer
     */
    private void echoUsbRequest(@NonNull UsbDeviceConnection connection, @NonNull UsbEndpoint in,
            @NonNull UsbEndpoint out, int size, boolean useDirectBuffer) {
        echoUsbRequest(connection, in, out, size, 0, size, 0, size, useDirectBuffer, false);
    }

    /**
     * Send a USB request which more than the allowed size and receive it back.
     *
     * @param connection      The connection to use
     * @param in              The endpoint to receive requests from
     * @param out             The endpoint to send requests to
     */
    private void echoOversizedUsbRequestLegacy(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, @NonNull UsbEndpoint out) {
        Random random = new Random();
        int totalSize = OVERSIZED_BUFFER_SIZE;

        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);

        UsbRequest receive = new UsbRequest();
        isInited = receive.initialize(connection, in);
        assertTrue(isInited);

        byte[] sentBytes = new byte[totalSize];
        random.nextBytes(sentBytes);
        ByteBuffer bufferSent = ByteBuffer.wrap(sentBytes);

        byte[] receivedBytes = new byte[totalSize];
        ByteBuffer bufferReceived = ByteBuffer.wrap(receivedBytes);

        boolean wasQueued = receive.queue(bufferReceived, totalSize);
        assertTrue(wasQueued);
        wasQueued = sent.queue(bufferSent, totalSize);
        assertTrue(wasQueued);

        for (int requestNum = 0; requestNum < 2; requestNum++) {
            UsbRequest finished = connection.requestWait();
            if (finished == receive) {
                // size beyond MAX_BUFFER_SIZE is ignored
                for (int i = 0; i < totalSize; i++) {
                    if (i < MAX_BUFFER_SIZE) {
                        assertEquals(sentBytes[i], receivedBytes[i]);
                    } else {
                        assertEquals(0, receivedBytes[i]);
                    }
                }
            } else {
                assertSame(sent, finished);
            }
            finished.close();
        }

        if (mDoesCompanionZeroTerminate) {
            receiveZeroSizeRequestLegacy(connection, in);
        }
    }

    /**
     * Time out while waiting for USB requests.
     *
     * @param connection The connection to use
     */
    private void timeoutWhileWaitingForUsbRequest(@NonNull UsbDeviceConnection connection)
            throws Throwable {
        runAndAssertException(() -> connection.requestWait(-1), IllegalArgumentException.class);

        long startTime = now();
        runAndAssertException(() -> connection.requestWait(100), TimeoutException.class);
        assertTrue(now() - startTime >= 100);
        assertTrue(now() - startTime < 400);

        startTime = now();
        runAndAssertException(() -> connection.requestWait(0), TimeoutException.class);
        assertTrue(now() - startTime < 400);
    }

    /**
     * Receive a USB request before a timeout triggers
     *
     * @param connection The connection to use
     * @param in         The endpoint to receive requests from
     */
    private void receiveAfterTimeout(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, long timeout) throws InterruptedException, TimeoutException {
        UsbRequest reqQueued = new UsbRequest();
        ByteBuffer buffer = ByteBuffer.allocate(1);

        reqQueued.initialize(connection, in);
        reqQueued.queue(buffer);

        // Let the kernel receive and process the request
        Thread.sleep(50);

        long startTime = now();
        UsbRequest reqFinished = connection.requestWait(timeout);
        assertTrue(now() - startTime < timeout + 50);
        assertSame(reqQueued, reqFinished);
        reqFinished.close();
    }

    /**
     * Send a USB request with size 0 using the {@link UsbRequest#queue legacy path}.
     *
     * @param connection      The connection to use
     * @param out             The endpoint to send requests to
     * @param useDirectBuffer Send data from a direct buffer
     */
    private void sendZeroLengthRequestLegacy(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint out, boolean useDirectBuffer) {
        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);

        ByteBuffer buffer;
        if (useDirectBuffer) {
            buffer = ByteBuffer.allocateDirect(0);
        } else {
            buffer = ByteBuffer.allocate(0);
        }

        boolean isQueued = sent.queue(buffer, 0);
        assumeTrue(isQueued);
        UsbRequest finished = connection.requestWait();
        assertSame(finished, sent);
        finished.close();
    }

    /**
     * Send a USB request with size 0.
     *
     * @param connection      The connection to use
     * @param out             The endpoint to send requests to
     * @param useDirectBuffer Send data from a direct buffer
     */
    private void sendZeroLengthRequest(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint out, boolean useDirectBuffer) {
        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);

        ByteBuffer buffer;
        if (useDirectBuffer) {
            buffer = ByteBuffer.allocateDirect(0);
        } else {
            buffer = ByteBuffer.allocate(0);
        }

        boolean isQueued = sent.queue(buffer);
        assumeTrue(isQueued);
        UsbRequest finished = connection.requestWait();
        assertSame(finished, sent);
        finished.close();
    }

    /**
     * Send a USB request with a null buffer.
     *
     * @param connection      The connection to use
     * @param out             The endpoint to send requests to
     */
    private void sendNullRequest(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint out) {
        UsbRequest sent = new UsbRequest();
        boolean isInited = sent.initialize(connection, out);
        assertTrue(isInited);

        boolean isQueued = sent.queue(null);
        assumeTrue(isQueued);
        UsbRequest finished = connection.requestWait();
        assertSame(finished, sent);
        finished.close();
    }

    /**
     * Receive a USB request with size 0.
     *
     * @param connection      The connection to use
     * @param in             The endpoint to recevie requests from
     */
    private void receiveZeroLengthRequestLegacy(@NonNull UsbDeviceConnection connection,
            @NonNull UsbEndpoint in, boolean useDirectBuffer) {
        UsbRequest zeroReceived = new UsbRequest();
        boolean isInited = zeroReceived.initialize(connection, in);
        assertTrue(isInited);

        UsbRequest oneReceived = new UsbRequest();
        isInited = oneReceived.initialize(connection, in);
        assertTrue(isInited);

        ByteBuffer buffer;
        if (useDirectBuffer) {
            buffer = ByteBuffer.allocateDirect(0);
        } else {
            buffer = ByteBuffer.allocate(0);
        }

        ByteBuffer buffer1;
        if (useDirectBuffer) {
            buffer1 = ByteBuffer.allocateDirect(1);
        } else {
            buffer1 = ByteBuffer.allocate(1);
        }

        boolean isQueued = zeroReceived.queue(buffer);
        assumeTrue(isQueued);
        isQueued = oneReceived.queue(buffer1);
        assumeTrue(isQueued);

        // We expect both to be returned after some time
        ArrayList<UsbRequest> finished = new ArrayList<>(2);

        // We expect both request to come back after the delay, but then quickly
        long startTime = now();
        finished.add(connection.requestWait());
        long firstReturned = now();
        finished.add(connection.requestWait());
        long secondReturned = now();

        assumeTrue(firstReturned - startTime > 100);
        assumeTrue(secondReturned - firstReturned < 100);

        assertTrue(finished.contains(zeroReceived));
        assertTrue(finished.contains(oneReceived));
    }

    /**
     * Tests the {@link UsbRequest#queue legacy implementaion} of {@link UsbRequest} and
     * {@link UsbDeviceConnection#requestWait()}.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     * @throws Throwable
     */
    private void usbRequestLegacyTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // Find bulk in and out endpoints
        assumeTrue(iface.getEndpointCount() == 2);
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        // Single threaded send and receive
        nextTest(connection, in, out, "Echo 1 byte");
        echoUsbRequestLegacy(connection, in, out, 1, true);

        nextTest(connection, in, out, "Echo 1 byte");
        echoUsbRequestLegacy(connection, in, out, 1, false);

        nextTest(connection, in, out, "Echo max bytes");
        echoUsbRequestLegacy(connection, in, out, MAX_BUFFER_SIZE, true);

        nextTest(connection, in, out, "Echo max bytes");
        echoUsbRequestLegacy(connection, in, out, MAX_BUFFER_SIZE, false);

        nextTest(connection, in, out, "Echo oversized buffer");
        echoOversizedUsbRequestLegacy(connection, in, out);

        // Send empty requests
        sendZeroLengthRequestLegacy(connection, out, true);
        sendZeroLengthRequestLegacy(connection, out, false);

        // waitRequest with timeout
        timeoutWhileWaitingForUsbRequest(connection);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveAfterTimeout(connection, in, 400);

        nextTest(connection, in, out, "Receive byte immediately");
        // Make sure the data is received before we queue the request for it
        Thread.sleep(50);
        receiveAfterTimeout(connection, in, 0);

        /* TODO: Unreliable

        // Zero length means waiting for the next data and then return
        nextTest(connection, in, out, "Receive byte after some time");
        receiveZeroLengthRequestLegacy(connection, in, true);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveZeroLengthRequestLegacy(connection, in, true);

        */

        // UsbRequest.queue ignores position, limit, arrayOffset, and capacity
        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 42, 0, 42, 5, 42, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 42, 0, 42, 0, 36, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 42, 5, 42, 0, 36, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 42, 0, 36, 0, 31, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 47, 0, 47, 0, 47, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 47, 5, 47, 0, 42, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 47, 0, 42, 0, 42, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 47, 0, 47, 5, 47, false);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoUsbRequestLegacy(connection, in, out, 42, 47, 5, 47, 5, 36, false);

        // Illegal arguments
        final UsbRequest req1 = new UsbRequest();
        runAndAssertException(() -> req1.initialize(null, in), NullPointerException.class);
        runAndAssertException(() -> req1.initialize(connection, null), NullPointerException.class);
        boolean isInited = req1.initialize(connection, in);
        assertTrue(isInited);
        runAndAssertException(() -> req1.queue(null, 0), NullPointerException.class);
        runAndAssertException(() -> req1.queue(ByteBuffer.allocate(1).asReadOnlyBuffer(), 1),
                IllegalArgumentException.class);
        req1.close();

        // Cannot queue closed request
        runAndAssertException(() -> req1.queue(ByteBuffer.allocate(1), 1),
                NullPointerException.class);
        runAndAssertException(() -> req1.queue(ByteBuffer.allocateDirect(1), 1),
                NullPointerException.class);
    }

    /**
     * Repeat c n times
     *
     * @param c The character to repeat
     * @param n The number of times to repeat
     *
     * @return c repeated n times
     */
    public static String repeat(char c, int n) {
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (c != ' ' && i % 10 == 0) {
                result.append(i / 10);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Tests {@link UsbRequest} and {@link UsbDeviceConnection#requestWait()}.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     * @throws Throwable
     */
    private void usbRequestTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // Find bulk in and out endpoints
        assumeTrue(iface.getEndpointCount() == 2);
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        // Single threaded send and receive
        nextTest(connection, in, out, "Echo 1 byte");
        echoUsbRequest(connection, in, out, 1, true);

        nextTest(connection, in, out, "Echo 1 byte");
        echoUsbRequest(connection, in, out, 1, false);

        nextTest(connection, in, out, "Echo max bytes");
        echoUsbRequest(connection, in, out, MAX_BUFFER_SIZE, true);

        nextTest(connection, in, out, "Echo max bytes");
        echoUsbRequest(connection, in, out, MAX_BUFFER_SIZE, false);

        // Send empty requests
        sendZeroLengthRequest(connection, out, true);
        sendZeroLengthRequest(connection, out, false);
        sendNullRequest(connection, out);

        /* TODO: Unreliable

        // Zero length means waiting for the next data and then return
        nextTest(connection, in, out, "Receive byte after some time");
        receiveZeroLengthRequest(connection, in, true);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveZeroLengthRequest(connection, in, true);

        */

        for (int startOfSlice : new int[]{0, 1}) {
            for (int endOffsetOfSlice : new int[]{0, 2}) {
                for (int positionInSlice : new int[]{0, 5}) {
                    for (int limitOffsetInSlice : new int[]{0, 11}) {
                        for (boolean useDirectBuffer : new boolean[]{true, false}) {
                            for (boolean makeSendBufferReadOnly : new boolean[]{true, false}) {
                                int sliceSize = 42 + positionInSlice + limitOffsetInSlice;
                                int originalSize = sliceSize + startOfSlice + endOffsetOfSlice;

                                nextTest(connection, in, out, "Echo 42 bytes");

                                // Log buffer, slice, and data offsets
                                Log.i(LOG_TAG,
                                        "buffer" + (makeSendBufferReadOnly ? "(ro): [" : ":     [")
                                                + repeat('.', originalSize) + "]");
                                Log.i(LOG_TAG,
                                        "slice:     " + repeat(' ', startOfSlice) + " [" + repeat(
                                                '.', sliceSize) + "]");
                                Log.i(LOG_TAG,
                                        "data:      " + repeat(' ', startOfSlice + positionInSlice)
                                                + " [" + repeat('.', 42) + "]");

                                echoUsbRequest(connection, in, out, originalSize, startOfSlice,
                                        originalSize - endOffsetOfSlice, positionInSlice,
                                        sliceSize - limitOffsetInSlice, useDirectBuffer,
                                        makeSendBufferReadOnly);
                            }
                        }
                    }
                }
            }
        }

        // Illegal arguments
        final UsbRequest req1 = new UsbRequest();
        runAndAssertException(() -> req1.initialize(null, in), NullPointerException.class);
        runAndAssertException(() -> req1.initialize(connection, null), NullPointerException.class);
        boolean isInited = req1.initialize(connection, in);
        assertTrue(isInited);
        runAndAssertException(() -> req1.queue(ByteBuffer.allocate(16384 + 1).asReadOnlyBuffer()),
                IllegalArgumentException.class);
        runAndAssertException(() -> req1.queue(ByteBuffer.allocate(1).asReadOnlyBuffer()),
                IllegalArgumentException.class);
        req1.close();

        // Cannot queue closed request
        runAndAssertException(() -> req1.queue(ByteBuffer.allocate(1)),
                IllegalStateException.class);
        runAndAssertException(() -> req1.queue(ByteBuffer.allocateDirect(1)),
                IllegalStateException.class);

        // Initialize
        UsbRequest req2 = new UsbRequest();
        isInited = req2.initialize(connection, in);
        assertTrue(isInited);
        isInited = req2.initialize(connection, out);
        assertTrue(isInited);
        req2.close();

        // Close
        req2 = new UsbRequest();
        req2.close();

        req2.initialize(connection, in);
        req2.close();
        req2.close();
    }

    /** State of a {@link UsbRequest} in flight */
    private static class RequestState {
        final ByteBuffer buffer;
        final Object clientData;

        private RequestState(ByteBuffer buffer, Object clientData) {
            this.buffer = buffer;
            this.clientData = clientData;
        }
    }

    /** Recycles elements that might be expensive to create */
    private abstract class Recycler<T> {
        private final Random mRandom;
        private final LinkedList<T> mData;

        protected Recycler() {
            mData = new LinkedList<>();
            mRandom = new Random();
        }

        /**
         * Add a new element to be recycled.
         *
         * @param newElement The element that is not used anymore and can be used by someone else.
         */
        private void recycle(@NonNull T newElement) {
            synchronized (mData) {
                if (mRandom.nextBoolean()) {
                    mData.addLast(newElement);
                } else {
                    mData.addFirst(newElement);
                }
            }
        }

        /**
         * Get a recycled element or create a new one if needed.
         *
         * @return An element that can be used (maybe recycled)
         */
        private @NonNull T get() {
            T recycledElement;

            try {
                synchronized (mData) {
                    recycledElement = mData.pop();
                }
            } catch (NoSuchElementException ignored) {
                recycledElement = create();
            }

            reset(recycledElement);

            return recycledElement;
        }

        /** Reset internal state of {@code recycledElement} */
        protected abstract void reset(@NonNull T recycledElement);

        /** Create a new element */
        protected abstract @NonNull T create();

        /** Get all elements that are currently recycled and waiting to be used again */
        public @NonNull LinkedList<T> getAll() {
            return mData;
        }
    }

    /**
     * Common code between {@link QueuerThread} and {@link ReceiverThread}.
     */
    private class TestThread extends Thread {
        /** State copied from the main thread (see runTest()) */
        protected final UsbDeviceConnection mConnection;
        protected final Recycler<UsbRequest> mInRequestRecycler;
        protected final Recycler<UsbRequest> mOutRequestRecycler;
        protected final Recycler<ByteBuffer> mBufferRecycler;
        protected final HashMap<UsbRequest, RequestState> mRequestsInFlight;
        protected final HashMap<Integer, Integer> mData;
        protected final ArrayList<Throwable> mErrors;

        protected volatile boolean mShouldStop;

        TestThread(@NonNull UsbDeviceConnection connection,
                @NonNull Recycler<UsbRequest> inRequestRecycler,
                @NonNull Recycler<UsbRequest> outRequestRecycler,
                @NonNull Recycler<ByteBuffer> bufferRecycler,
                @NonNull HashMap<UsbRequest, RequestState> requestsInFlight,
                @NonNull HashMap<Integer, Integer> data,
                @NonNull ArrayList<Throwable> errors) {
            super();

            mShouldStop = false;
            mConnection = connection;
            mBufferRecycler = bufferRecycler;
            mInRequestRecycler = inRequestRecycler;
            mOutRequestRecycler = outRequestRecycler;
            mRequestsInFlight = requestsInFlight;
            mData = data;
            mErrors = errors;
        }

        /**
         * Stop thread
         */
        void abort() {
            mShouldStop = true;
            interrupt();
        }
    }

    /**
     * A thread that queues matching write and read {@link UsbRequest requests}. We expect the
     * writes to be echoed back and return in unchanged in the read requests.
     * <p> This thread just issues the requests and does not care about them anymore after the
     * system took them. The {@link ReceiverThread} handles the result of both write and read
     * requests.</p>
     */
    private class QueuerThread extends TestThread {
        private static final int MAX_IN_FLIGHT = 64;
        private static final long RUN_TIME = 10 * 1000;

        private final AtomicInteger mCounter;

        /**
         * Create a new thread that queues matching write and read UsbRequests.
         *
         * @param connection Connection to communicate with
         * @param inRequestRecycler Pool of in-requests that can be reused
         * @param outRequestRecycler Pool of out-requests that can be reused
         * @param bufferRecycler Pool of byte buffers that can be reused
         * @param requestsInFlight State of the requests currently in flight
         * @param data Mapping counter -> data
         * @param counter An atomic counter
         * @param errors Pool of throwables created by threads like this
         */
        QueuerThread(@NonNull UsbDeviceConnection connection,
                @NonNull Recycler<UsbRequest> inRequestRecycler,
                @NonNull Recycler<UsbRequest> outRequestRecycler,
                @NonNull Recycler<ByteBuffer> bufferRecycler,
                @NonNull HashMap<UsbRequest, RequestState> requestsInFlight,
                @NonNull HashMap<Integer, Integer> data,
                @NonNull AtomicInteger counter,
                @NonNull ArrayList<Throwable> errors) {
            super(connection, inRequestRecycler, outRequestRecycler, bufferRecycler,
                    requestsInFlight, data, errors);

            mCounter = counter;
        }

        @Override
        public void run() {
            Random random = new Random();

            long endTime = now() + RUN_TIME;

            while (now() < endTime && !mShouldStop) {
                try {
                    int counter = mCounter.getAndIncrement();

                    if (counter % 1024 == 0) {
                        Log.i(LOG_TAG, "Counter is " + counter);
                    }

                    // Write [1:counter:data]
                    UsbRequest writeRequest = mOutRequestRecycler.get();
                    ByteBuffer writeBuffer = mBufferRecycler.get();
                    int data = random.nextInt();
                    writeBuffer.put((byte)1).putInt(counter).putInt(data);
                    writeBuffer.flip();

                    // Send read that will receive the data back from the write as the other side
                    // will echo all requests.
                    UsbRequest readRequest = mInRequestRecycler.get();
                    ByteBuffer readBuffer = mBufferRecycler.get();

                    // Register requests
                    synchronized (mRequestsInFlight) {
                        // Wait until previous requests were processed
                        while (mRequestsInFlight.size() > MAX_IN_FLIGHT) {
                            try {
                                mRequestsInFlight.wait();
                            } catch (InterruptedException e) {
                                break;
                            }
                        }

                        if (mShouldStop) {
                            break;
                        } else {
                            mRequestsInFlight.put(writeRequest, new RequestState(writeBuffer,
                                    writeRequest.getClientData()));
                            mRequestsInFlight.put(readRequest, new RequestState(readBuffer,
                                    readRequest.getClientData()));
                            mRequestsInFlight.notifyAll();
                        }
                    }

                    // Store which data was written for the counter
                    synchronized (mData) {
                        mData.put(counter, data);
                    }

                    // Send both requests to the system. Once they finish the ReceiverThread will
                    // be notified
                    boolean isQueued = writeRequest.queue(writeBuffer);
                    assertTrue(isQueued);

                    isQueued = readRequest.queue(readBuffer, 9);
                    assertTrue(isQueued);
                } catch (Throwable t) {
                    synchronized (mErrors) {
                        mErrors.add(t);
                        mErrors.notify();
                    }
                    break;
                }
            }
        }
    }

    /**
     * A thread that receives processed UsbRequests and compares the expected result. The requests
     * can be both read and write requests. The requests were created and given to the system by
     * the {@link QueuerThread}.
     */
    private class ReceiverThread extends TestThread {
        private final UsbEndpoint mOut;

        /**
         * Create a thread that receives processed UsbRequests and compares the expected result.
         *
         * @param connection Connection to communicate with
         * @param out Endpoint to queue write requests on
         * @param inRequestRecycler Pool of in-requests that can be reused
         * @param outRequestRecycler Pool of out-requests that can be reused
         * @param bufferRecycler Pool of byte buffers that can be reused
         * @param requestsInFlight State of the requests currently in flight
         * @param data Mapping counter -> data
         * @param errors Pool of throwables created by threads like this
         */
        ReceiverThread(@NonNull UsbDeviceConnection connection, @NonNull UsbEndpoint out,
                @NonNull Recycler<UsbRequest> inRequestRecycler,
                @NonNull Recycler<UsbRequest> outRequestRecycler,
                @NonNull Recycler<ByteBuffer> bufferRecycler,
                @NonNull HashMap<UsbRequest, RequestState> requestsInFlight,
                @NonNull HashMap<Integer, Integer> data, @NonNull ArrayList<Throwable> errors) {
            super(connection, inRequestRecycler, outRequestRecycler, bufferRecycler,
                    requestsInFlight, data, errors);

            mOut = out;
        }

        @Override
        public void run() {
            while (!mShouldStop) {
                try {
                    // Wait until a request is queued as mConnection.requestWait() cannot be
                    // interrupted.
                    synchronized (mRequestsInFlight) {
                        while (mRequestsInFlight.isEmpty()) {
                            try {
                                mRequestsInFlight.wait();
                            } catch (InterruptedException e) {
                                break;
                            }
                        }

                        if (mShouldStop) {
                            break;
                        }
                    }

                    // Receive request
                    UsbRequest request = mConnection.requestWait();
                    assertNotNull(request);

                    // Find the state the request should have
                    RequestState state;
                    synchronized (mRequestsInFlight) {
                        state = mRequestsInFlight.remove(request);
                        mRequestsInFlight.notifyAll();
                    }

                    // Compare client data
                    assertSame(state.clientData, request.getClientData());

                    // There is nothing more to check about write requests, but for read requests
                    // (the ones going to an out endpoint) we know that it just an echoed back write
                    // request.
                    if (!request.getEndpoint().equals(mOut)) {
                        state.buffer.flip();

                        // Read request buffer, check that data is correct
                        byte alive = state.buffer.get();
                        int counter = state.buffer.getInt();
                        int receivedData = state.buffer.getInt();

                        // We stored which data-combinations were written
                        int expectedData;
                        synchronized(mData) {
                            expectedData = mData.remove(counter);
                        }

                        // Make sure read request matches a write request we sent before
                        assertEquals(1, alive);
                        assertEquals(expectedData, receivedData);
                    }

                    // Recycle buffers and requests so they can be reused later.
                    mBufferRecycler.recycle(state.buffer);

                    if (request.getEndpoint().equals(mOut)) {
                        mOutRequestRecycler.recycle(request);
                    } else {
                        mInRequestRecycler.recycle(request);
                    }
                } catch (Throwable t) {
                    synchronized (mErrors) {
                        mErrors.add(t);
                        mErrors.notify();
                    }
                    break;
                }
            }
        }
    }

    /**
     * Tests parallel issuance and receiving of {@link UsbRequest usb requests}.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     */
    private void parallelUsbRequestsTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) {
        // Find bulk in and out endpoints
        assumeTrue(iface.getEndpointCount() == 2);
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        // Recycler for requests for the in-endpoint
        Recycler<UsbRequest> inRequestRecycler = new Recycler<UsbRequest>() {
            @Override
            protected void reset(@NonNull UsbRequest recycledElement) {
                recycledElement.setClientData(new Object());
            }

            @Override
            protected @NonNull UsbRequest create() {
                UsbRequest request = new UsbRequest();
                request.initialize(connection, in);

                return request;
            }
        };

        // Recycler for requests for the in-endpoint
        Recycler<UsbRequest> outRequestRecycler = new Recycler<UsbRequest>() {
            @Override
            protected void reset(@NonNull UsbRequest recycledElement) {
                recycledElement.setClientData(new Object());
            }

            @Override
            protected @NonNull UsbRequest create() {
                UsbRequest request = new UsbRequest();
                request.initialize(connection, out);

                return request;
            }
        };

        // Recycler for requests for read and write buffers
        Recycler<ByteBuffer> bufferRecycler = new Recycler<ByteBuffer>() {
            @Override
            protected void reset(@NonNull ByteBuffer recycledElement) {
                recycledElement.rewind();
            }

            @Override
            protected @NonNull ByteBuffer create() {
                return ByteBuffer.allocateDirect(9);
            }
        };

        HashMap<UsbRequest, RequestState> requestsInFlight = new HashMap<>();

        // Data in the requests
        HashMap<Integer, Integer> data = new HashMap<>();
        AtomicInteger counter = new AtomicInteger(0);

        // Errors created in the threads
        ArrayList<Throwable> errors = new ArrayList<>();

        // Create two threads that queue read and write requests
        QueuerThread queuer1 = new QueuerThread(connection, inRequestRecycler,
                outRequestRecycler, bufferRecycler, requestsInFlight, data, counter, errors);
        QueuerThread queuer2 = new QueuerThread(connection, inRequestRecycler,
                outRequestRecycler, bufferRecycler, requestsInFlight, data, counter, errors);

        // Create a thread that receives the requests after they are processed.
        ReceiverThread receiver = new ReceiverThread(connection, out, inRequestRecycler,
                outRequestRecycler, bufferRecycler, requestsInFlight, data, errors);

        nextTest(connection, in, out, "Echo until stop signal");

        queuer1.start();
        queuer2.start();
        receiver.start();

        Log.i(LOG_TAG, "Waiting for queuers to stop");

        try {
            queuer1.join();
            queuer2.join();
        } catch (InterruptedException e) {
            synchronized(errors) {
                errors.add(e);
            }
        }

        if (errors.isEmpty()) {
            Log.i(LOG_TAG, "Wait for all requests to finish");
            synchronized (requestsInFlight) {
                while (!requestsInFlight.isEmpty()) {
                    try {
                        requestsInFlight.wait();
                    } catch (InterruptedException e) {
                        synchronized(errors) {
                            errors.add(e);
                        }
                        break;
                    }
                }
            }

            receiver.abort();

            try {
                receiver.join();
            } catch (InterruptedException e) {
                synchronized(errors) {
                    errors.add(e);
                }
            }

            // Close all requests that are currently recycled
            inRequestRecycler.getAll().forEach(UsbRequest::close);
            outRequestRecycler.getAll().forEach(UsbRequest::close);
        } else {
            receiver.abort();
        }

        for (Throwable t : errors) {
            Log.e(LOG_TAG, "Error during test", t);
        }

        byte[] stopBytes = new byte[9];
        connection.bulkTransfer(out, stopBytes, 9, 0);

        // If we had any error make the test fail
        assertEquals(0, errors.size());
    }

    /**
     * Tests {@link UsbDeviceConnection#bulkTransfer}.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     * @throws Throwable
     */
    private void bulkTransferTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // Find bulk in and out endpoints
        assumeTrue(iface.getEndpointCount() == 2);
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        // Transmission tests
        nextTest(connection, in, out, "Echo 1 byte");
        echoBulkTransfer(connection, in, out, 1);

        nextTest(connection, in, out, "Echo 42 bytes");
        echoBulkTransferOffset(connection, in, out, 23, 42);

        nextTest(connection, in, out, "Echo max bytes");
        echoBulkTransfer(connection, in, out, MAX_BUFFER_SIZE);

        nextTest(connection, in, out, "Echo oversized buffer");
        echoOversizedBulkTransfer(connection, in, out);

        nextTest(connection, in, out, "Receive oversized buffer");
        receiveOversizedBulkTransfer(connection, in);

        // Illegal arguments
        runAndAssertException(() -> connection.bulkTransfer(out, new byte[1], 2, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(in, new byte[1], 2, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(out, new byte[2], 1, 2, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(in, new byte[2], 1, 2, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(out, new byte[1], -1, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(in, new byte[1], -1, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(out, new byte[1], 1, -1, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(in, new byte[1], 1, -1, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(out, new byte[1], -1, -1, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(in, new byte[1], -1, -1, 0),
                IllegalArgumentException.class);
        runAndAssertException(() -> connection.bulkTransfer(null, new byte[1], 1, 0),
                NullPointerException.class);

        // Transmissions that do nothing
        int numSent = connection.bulkTransfer(out, null, 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, null, 0, 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, new byte[0], 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, new byte[0], 0, 0, 0);
        assertEquals(0, numSent);

        numSent = connection.bulkTransfer(out, new byte[2], 2, 0, 0);
        assertEquals(0, numSent);

        /* TODO: These tests are flaky as they appear to be affected by previous tests

        // Transmissions that do not transfer data:
        // - first transfer blocks until data is received, but does not return the data.
        // - The data is read in the second transfer
        nextTest(connection, in, out, "Receive byte after some time");
        receiveWithEmptyBuffer(connection, in, null, 0, 0);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveWithEmptyBuffer(connection, in, new byte[0], 0, 0);

        nextTest(connection, in, out, "Receive byte after some time");
        receiveWithEmptyBuffer(connection, in, new byte[2], 2, 0);

        */

        // Timeouts
        int numReceived = connection.bulkTransfer(in, new byte[1], 1, 100);
        assertEquals(-1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, 10000);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, 0);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[1], 1, -1);
        assertEquals(1, numReceived);

        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, 100);
        assertEquals(-1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, 0);
        assertEquals(1, numReceived);

        nextTest(connection, in, out, "Receive byte after some time");
        numReceived = connection.bulkTransfer(in, new byte[2], 1, 1, -1);
        assertEquals(1, numReceived);
    }

    /**
     * Test if the companion device zero-terminates their requests that are multiples of the
     * maximum package size. Then sets {@link #mDoesCompanionZeroTerminate} if the companion
     * zero terminates
     *
     * @param connection Connection to the USB device
     * @param iface      The interface to use
     */
    private void testIfCompanionZeroTerminates(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) {
        assumeTrue(iface.getEndpointCount() == 2);
        final UsbEndpoint in = getEndpoint(iface, UsbConstants.USB_DIR_IN);
        final UsbEndpoint out = getEndpoint(iface, UsbConstants.USB_DIR_OUT);
        assertNotNull(in);
        assertNotNull(out);

        nextTest(connection, in, out, "does companion zero terminate");

        // The other size sends:
        // - 1024 bytes
        // - maybe a zero sized package
        // - 1 byte

        byte[] buffer = new byte[1024];
        int numTransferred = connection.bulkTransfer(in, buffer, 1024, 0);
        assertEquals(1024, numTransferred);

        numTransferred = connection.bulkTransfer(in, buffer, 1, 0);
        if (numTransferred == 0) {
            assertEquals(0, numTransferred);

            numTransferred = connection.bulkTransfer(in, buffer, 1, 0);
            assertEquals(1, numTransferred);

            mDoesCompanionZeroTerminate = true;
            Log.i(LOG_TAG, "Companion zero terminates");
        } else {
            assertEquals(1, numTransferred);
            Log.i(LOG_TAG, "Companion does not zero terminate - an older device");
        }
    }

    /**
     * Send signal to the remove device that testing is finished.
     *
     * @param connection The connection to use for testing
     * @param iface      The interface of the android accessory interface of the device
     */
    private void endTesting(@NonNull UsbDeviceConnection connection, @NonNull UsbInterface iface) {
        // "done" signals that testing is over
        nextTest(connection, getEndpoint(iface, UsbConstants.USB_DIR_IN),
                getEndpoint(iface, UsbConstants.USB_DIR_OUT), "done");
    }

    /**
     * Test the behavior of {@link UsbDeviceConnection#claimInterface} and
     * {@link UsbDeviceConnection#releaseInterface}.
     *
     * <p>Note: The interface under test is <u>not</u> claimed by a kernel driver, hence there is
     * no difference in behavior between force and non-force versions of
     * {@link UsbDeviceConnection#claimInterface}</p>
     *
     * @param connection The connection to use
     * @param iface The interface to claim and release
     *
     * @throws Throwable
     */
    private void claimInterfaceTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        // The interface is not claimed by the kernel driver, so not forcing it should work
        boolean claimed = connection.claimInterface(iface, false);
        assertTrue(claimed);
        boolean released = connection.releaseInterface(iface);
        assertTrue(released);

        // Forcing if it is not necessary does no harm
        claimed = connection.claimInterface(iface, true);
        assertTrue(claimed);

        // Re-claiming does nothing
        claimed = connection.claimInterface(iface, true);
        assertTrue(claimed);

        released = connection.releaseInterface(iface);
        assertTrue(released);

        // Re-releasing is not allowed
        released = connection.releaseInterface(iface);
        assertFalse(released);

        // Using an unclaimed interface claims it automatically
        int numSent = connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_OUT), null, 0,
                0);
        assertEquals(0, numSent);

        released = connection.releaseInterface(iface);
        assertTrue(released);

        runAndAssertException(() -> connection.claimInterface(null, true),
                NullPointerException.class);
        runAndAssertException(() -> connection.claimInterface(null, false),
                NullPointerException.class);
        runAndAssertException(() -> connection.releaseInterface(null), NullPointerException.class);
    }

    /**
     * Test all input parameters to {@link UsbDeviceConnection#setConfiguration} .
     *
     * <p>Note:
     * <ul>
     *     <li>The device under test only supports one configuration, hence changing configuration
     * is not tested.</li>
     *     <li>This test sets the current configuration again. This resets the device.</li>
     * </ul></p>
     *
     * @param device the device under test
     * @param connection The connection to use
     * @param iface An interface of the device
     *
     * @throws Throwable
     */
    private void setConfigurationTests(@NonNull UsbDevice device,
            @NonNull UsbDeviceConnection connection, @NonNull UsbInterface iface) throws Throwable {
        assumeTrue(device.getConfigurationCount() == 1);
        boolean wasSet = connection.setConfiguration(device.getConfiguration(0));
        assertTrue(wasSet);

        // Cannot set configuration for a device with a claimed interface
        boolean claimed = connection.claimInterface(iface, false);
        assertTrue(claimed);
        wasSet = connection.setConfiguration(device.getConfiguration(0));
        assertFalse(wasSet);
        boolean released = connection.releaseInterface(iface);
        assertTrue(released);

        runAndAssertException(() -> connection.setConfiguration(null), NullPointerException.class);
    }

    /**
     * Test all input parameters to {@link UsbDeviceConnection#setConfiguration} .
     *
     * <p>Note: The interface under test only supports one settings, hence changing the setting can
     * not be tested.</p>
     *
     * @param connection The connection to use
     * @param iface The interface to test
     *
     * @throws Throwable
     */
    private void setInterfaceTests(@NonNull UsbDeviceConnection connection,
            @NonNull UsbInterface iface) throws Throwable {
        boolean claimed = connection.claimInterface(iface, false);
        assertTrue(claimed);
        boolean wasSet = connection.setInterface(iface);
        assertTrue(wasSet);
        boolean released = connection.releaseInterface(iface);
        assertTrue(released);

        // Setting the interface for an unclaimed interface automatically claims it
        wasSet = connection.setInterface(iface);
        assertTrue(wasSet);
        released = connection.releaseInterface(iface);
        assertTrue(released);

        runAndAssertException(() -> connection.setInterface(null), NullPointerException.class);
    }

    /**
     * Enumerate all known devices and check basic relationship between the properties.
     */
    private void enumerateDevices() throws Exception {
        Set<Integer> knownDeviceIds = new ArraySet<>();

        for (Map.Entry<String, UsbDevice> entry : mUsbManager.getDeviceList().entrySet()) {
            UsbDevice device = entry.getValue();

            assertEquals(entry.getKey(), device.getDeviceName());
            assertNotNull(device.getDeviceName());

            // Device ID should be unique
            assertFalse(knownDeviceIds.contains(device.getDeviceId()));
            knownDeviceIds.add(device.getDeviceId());

            assertEquals(device.getDeviceName(), UsbDevice.getDeviceName(device.getDeviceId()));

            // Properties without constraints
            device.getManufacturerName();
            device.getProductName();
            device.getVersion();
            device.getSerialNumber();
            device.getVendorId();
            device.getProductId();
            device.getDeviceClass();
            device.getDeviceSubclass();
            device.getDeviceProtocol();

            Set<UsbInterface> interfacesFromAllConfigs = new ArraySet<>();
            Set<Pair<Integer, Integer>> knownInterfaceIds = new ArraySet<>();
            Set<Integer> knownConfigurationIds = new ArraySet<>();
            int numConfigurations = device.getConfigurationCount();
            for (int configNum = 0; configNum < numConfigurations; configNum++) {
                UsbConfiguration config = device.getConfiguration(configNum);

                // Configuration ID should be unique
                assertFalse(knownConfigurationIds.contains(config.getId()));
                knownConfigurationIds.add(config.getId());

                assertTrue(config.getMaxPower() >= 0);

                // Properties without constraints
                config.getName();
                config.isSelfPowered();
                config.isRemoteWakeup();

                int numInterfaces = config.getInterfaceCount();
                for (int interfaceNum = 0; interfaceNum < numInterfaces; interfaceNum++) {
                    UsbInterface iface = config.getInterface(interfaceNum);
                    interfacesFromAllConfigs.add(iface);

                    Pair<Integer, Integer> ifaceId = new Pair<>(iface.getId(),
                            iface.getAlternateSetting());
                    assertFalse(knownInterfaceIds.contains(ifaceId));
                    knownInterfaceIds.add(ifaceId);

                    // Properties without constraints
                    iface.getName();
                    iface.getInterfaceClass();
                    iface.getInterfaceSubclass();
                    iface.getInterfaceProtocol();

                    int numEndpoints = iface.getEndpointCount();
                    for (int endpointNum = 0; endpointNum < numEndpoints; endpointNum++) {
                        UsbEndpoint endpoint = iface.getEndpoint(endpointNum);

                        assertEquals(endpoint.getAddress(),
                                endpoint.getEndpointNumber() | endpoint.getDirection());

                        assertTrue(endpoint.getDirection() == UsbConstants.USB_DIR_OUT ||
                                endpoint.getDirection() == UsbConstants.USB_DIR_IN);

                        assertTrue(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_CONTROL ||
                                endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                                endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                                endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT);

                        assertTrue(endpoint.getMaxPacketSize() >= 0);
                        assertTrue(endpoint.getInterval() >= 0);

                        // Properties without constraints
                        endpoint.getAttributes();
                    }
                }
            }

            int numInterfaces = device.getInterfaceCount();
            for (int interfaceNum = 0; interfaceNum < numInterfaces; interfaceNum++) {
                assertTrue(interfacesFromAllConfigs.contains(device.getInterface(interfaceNum)));
            }
        }
    }

    /**
     * Run tests.
     *
     * @param device The device to run the test against. This device is running
     *               com.android.cts.verifierusbcompanion.DeviceTestCompanion
     */
    private void runTests(@NonNull UsbDevice device) {
        try {
            // Find the AOAP interface
            UsbInterface iface = null;
            for (int i = 0; i < device.getConfigurationCount(); i++) {
                if (device.getInterface(i).getName().equals("Android Accessory Interface")) {
                    iface = device.getInterface(i);
                    break;
                }
            }
            assumeNotNull(iface);

            enumerateDevices();

            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            assertNotNull(connection);

            claimInterfaceTests(connection, iface);

            boolean claimed = connection.claimInterface(iface, false);
            assertTrue(claimed);

            testIfCompanionZeroTerminates(connection, iface);

            usbRequestLegacyTests(connection, iface);
            usbRequestTests(connection, iface);
            parallelUsbRequestsTests(connection, iface);
            ctrlTransferTests(connection);
            bulkTransferTests(connection, iface);

            // Signal to the DeviceTestCompanion that there are no more transfer test
            endTesting(connection, iface);
            boolean released = connection.releaseInterface(iface);
            assertTrue(released);

            setInterfaceTests(connection, iface);
            setConfigurationTests(device, connection, iface);

            assertFalse(connection.getFileDescriptor() == -1);
            assertNotNull(connection.getRawDescriptors());
            assertFalse(connection.getRawDescriptors().length == 0);
            assertEquals(device.getSerialNumber(), connection.getSerial());

            connection.close();

            // We should not be able to communicate with the device anymore
            assertFalse(connection.claimInterface(iface, true));
            assertFalse(connection.releaseInterface(iface));
            assertFalse(connection.setConfiguration(device.getConfiguration(0)));
            assertFalse(connection.setInterface(iface));
            assertTrue(connection.getFileDescriptor() == -1);
            assertNull(connection.getRawDescriptors());
            assertNull(connection.getSerial());
            assertEquals(-1, connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_OUT),
                    new byte[1], 1, 0));
            assertEquals(-1, connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_OUT),
                    null, 0, 0));
            assertEquals(-1, connection.bulkTransfer(getEndpoint(iface, UsbConstants.USB_DIR_IN),
                    null, 0, 0));
            assertFalse((new UsbRequest()).initialize(connection, getEndpoint(iface,
                    UsbConstants.USB_DIR_IN)));

            // Double close should do no harm
            connection.close();

            setTestResultAndFinish(true);
        } catch (AssumptionViolatedException e) {
            // Assumptions failing means that somehow the device/connection is set up incorrectly
            Toast.makeText(this, getString(R.string.usb_device_unexpected, e.getLocalizedMessage()),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            fail(null, e);
        }
    }

    @Override
    protected void onDestroy() {
        if (mUsbDeviceConnectionReceiver != null) {
            unregisterReceiver(mUsbDeviceConnectionReceiver);
        }

        super.onDestroy();
    }
}
