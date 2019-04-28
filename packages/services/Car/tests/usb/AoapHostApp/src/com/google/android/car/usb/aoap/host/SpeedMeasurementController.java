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
package com.google.android.car.usb.aoap.host;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Controller that measures USB AOAP transfer speed. */
class SpeedMeasurementController extends Thread {
    private static final String TAG = SpeedMeasurementController.class.getSimpleName();

    interface SpeedMeasurementControllerCallback {
        void testStarted(int mode, int bufferSize);
        void testFinished(int mode, int bufferSize);
        void testSuiteFinished();
        void testResult(int mode, String update);
    }

    public static final int TEST_MODE_SYNC = 1;
    public static final int TEST_MODE_ASYNC = 2;

    private static final int TEST_DATA_SIZE = 100 * 1024 * 1024; // 100MB
    private static final int TEST_DATA_1_BATCH_SIZE = 15000;
    private static final int TEST_DATA_2_BATCH_SIZE = 1500;
    private static final int USB_TIMEOUT_MS = 1000; // 1s
    private static final int TEST_MAX_TIME_MS = 200000; // 200s
    private static final int ASYNC_MAX_OUTSTANDING_REQUESTS = 5;

    private static final ByteOrder ORDER = ByteOrder.BIG_ENDIAN;

    private final UsbDevice mDevice;
    private final UsbDeviceConnection mUsbConnection;
    private final SpeedMeasurementControllerCallback mCallback;
    private final Context mContext;

    public static Random sRandom = new Random(SystemClock.uptimeMillis());

    SpeedMeasurementController(Context context,
            UsbDevice device, UsbDeviceConnection conn,
            SpeedMeasurementControllerCallback callback) {
        if (TEST_DATA_SIZE % TEST_DATA_1_BATCH_SIZE == 0) {
            throw new AssertionError("TEST_DATA_SIZE mod TEST_DATA_BIG_BATCH_SIZE must not be 0");
        }
        if (TEST_DATA_SIZE % TEST_DATA_2_BATCH_SIZE == 0) {
            throw new AssertionError("TEST_DATA_SIZE mod TEST_DATA_SMALL_BATCH_SIZE must not be 0");
        }
        mContext = context;
        mDevice = device;
        mUsbConnection = conn;
        mCallback = callback;
    }

    protected void release() {
        if (mUsbConnection != null) {
            mUsbConnection.close();
        }
    }

    /**
     * {@inheritDoc}
     *
     * Test runs two type of USB host->phone write tests:
     * <ul>
     * <li> Synchronous write with usage of UsbDeviceConnection.bulkTransfer. </li>
     * <li> Aynchronous write with usage of UsbRequest and UsbDeviceConnection.requestWait. </li>
     * </ul>
     * Each test scenario also runs with different buffer size.
     */
    @Override
    public void run() {
        Log.v(TAG, "Running sync test with buffer size #1");
        runSyncTest(TEST_DATA_1_BATCH_SIZE);
        Log.v(TAG, "Running sync test with buffer size #2");
        runSyncTest(TEST_DATA_2_BATCH_SIZE);
        Log.v(TAG, "Running async test with buffer size #1");
        runAsyncTest(TEST_DATA_1_BATCH_SIZE);
        Log.v(TAG, "Running async test with buffer size #2");
        runAsyncTest(TEST_DATA_2_BATCH_SIZE);
        Log.v(TAG, "Done running tests");
        release();
        mCallback.testSuiteFinished();
    }

    private void runTest(BaseWriterThread writer, ReaderThread reader, int mode, int bufferSize) {
        mCallback.testStarted(mode, bufferSize);
        writer.start();
        reader.start();
        try {
            writer.join(TEST_MAX_TIME_MS);
        } catch (InterruptedException e) {}
        try {
            reader.join(TEST_MAX_TIME_MS);
        } catch (InterruptedException e) {}
        if (reader.isAlive()) {
            reader.requestToQuit();
            try {
                reader.join(USB_TIMEOUT_MS);
            } catch (InterruptedException e) {}
            if (reader.isAlive()) {
                throw new RuntimeException("ReaderSyncThread still alive");
            }
        }
        if (writer.isAlive()) {
            writer.requestToQuit();
            try {
                writer.join(USB_TIMEOUT_MS);
            } catch (InterruptedException e) {}
            if (writer.isAlive()) {
                throw new RuntimeException("WriterSyncThread still alive");
            }
        }
        mCallback.testFinished(mode, bufferSize);
        mCallback.testResult(
                mode,
                "Buffer size: " + bufferSize + " bytes. Speed " + writer.getSpeed());
    }

    private void runSyncTest(int bufferSize) {
        ReaderThread readerSync = new ReaderThread(mDevice, mUsbConnection, TEST_MODE_SYNC);
        WriterSyncThread writerSync = new WriterSyncThread(mDevice, mUsbConnection, bufferSize);
        runTest(writerSync, readerSync, TEST_MODE_SYNC, bufferSize);
    }

    private void runAsyncTest(int bufferSize) {
        ReaderThread readerAsync = new ReaderThread(mDevice, mUsbConnection, TEST_MODE_ASYNC);
        WriterAsyncThread writerAsync = new WriterAsyncThread(mDevice, mUsbConnection, bufferSize);
        runTest(writerAsync, readerAsync, TEST_MODE_ASYNC, bufferSize);
    }

    private class ReaderThread extends Thread {
        private boolean mShouldQuit = false;
        private final UsbDevice mDevice;
        private final UsbDeviceConnection mUsbConnection;
        private final int mMode;
        private final UsbEndpoint mBulkIn;
        private final byte[] mBuffer = new byte[16384];

        private ReaderThread(UsbDevice device, UsbDeviceConnection conn, int testMode) {
            super("AOAP reader");
            mDevice = device;
            mUsbConnection = conn;
            mMode = testMode;
            UsbInterface iface = mDevice.getInterface(0);
            // Setup bulk endpoints.
            UsbEndpoint bulkIn = null;
            UsbEndpoint bulkOut = null;
            for (int i = 0; i < iface.getEndpointCount(); i++) {
                UsbEndpoint ep = iface.getEndpoint(i);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    if (bulkIn == null) {
                        bulkIn = ep;
                    }
                } else {
                    if (bulkOut == null) {
                        bulkOut = ep;
                    }
                }
            }
            if (bulkIn == null || bulkOut == null) {
                throw new IllegalStateException("Unable to find bulk endpoints");
            }
            mBulkIn = bulkIn;
        }

        public synchronized void requestToQuit() {
            mShouldQuit = true;
        }

        private synchronized boolean shouldQuit() {
            return mShouldQuit;
        }

        @Override
        public void run() {
            while (!shouldQuit()) {
                int read = mUsbConnection.bulkTransfer(
                        mBulkIn, mBuffer, mBuffer.length, USB_TIMEOUT_MS);
                if (read > 0) {
                    Log.v(TAG, "Read " + read + " bytes");
                    break;
                }
            }
        }
    }

    private abstract class BaseWriterThread extends Thread {
        protected boolean mShouldQuit = false;
        protected long mSpeed;
        protected final UsbDevice mDevice;
        protected final int mBufferSize;
        protected final UsbDeviceConnection mUsbConnection;
        protected final UsbEndpoint mBulkOut;

        private BaseWriterThread(UsbDevice device, UsbDeviceConnection conn, int bufferSize) {
            super("AOAP writer");
            mDevice = device;
            mUsbConnection = conn;
            mBufferSize = bufferSize;
            UsbInterface iface = mDevice.getInterface(0);
            // Setup bulk endpoints.
            UsbEndpoint bulkIn = null;
            UsbEndpoint bulkOut = null;
            for (int i = 0; i < iface.getEndpointCount(); i++) {
                UsbEndpoint ep = iface.getEndpoint(i);
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    if (bulkIn == null) {
                        bulkIn = ep;
                    }
                } else {
                    if (bulkOut == null) {
                        bulkOut = ep;
                    }
                }
            }
            if (bulkIn == null || bulkOut == null) {
                throw new IllegalStateException("Unable to find bulk endpoints");
            }
            mBulkOut = bulkOut;
        }

        public synchronized void requestToQuit() {
            mShouldQuit = true;
        }

        protected synchronized boolean shouldQuit() {
            return mShouldQuit;
        }

        public synchronized String getSpeed() {
            return Formatter.formatFileSize(mContext, mSpeed) + "/s";
        }

        protected synchronized void setSpeed(long speed) {
            // Speed is set in bytes/ms. Convert it to bytes/s.
            mSpeed = speed * 1000;
        }

        protected byte[] intToByte(int value) {
            return ByteBuffer.allocate(4).order(ORDER).putInt(value).array();
        }

    }

    private class WriterSyncThread extends BaseWriterThread {
        private WriterSyncThread(UsbDevice device, UsbDeviceConnection conn, int bufferSize) {
            super(device, conn, bufferSize);
        }

        private boolean writeBufferSize() {
            byte[] bufferSizeArray = intToByte(mBufferSize);
            int sentBytes = mUsbConnection.bulkTransfer(
                    mBulkOut,
                    bufferSizeArray,
                    bufferSizeArray.length,
                    USB_TIMEOUT_MS);
            if (sentBytes < 0) {
                Log.e(TAG, "Failed to write data");
                return false;
            }
            return true;
        }

        @Override
        public void run() {
            int bytesToSend = TEST_DATA_SIZE;
            if (!writeBufferSize()) {
                return;
            }
            byte[] buffer = new byte[mBufferSize];
            sRandom.nextBytes(buffer);

            long timeStart = System.currentTimeMillis();
            while (bytesToSend > 0 && !shouldQuit()) {
                int sentBytes = mUsbConnection.bulkTransfer(
                        mBulkOut,
                        buffer,
                        (bytesToSend > buffer.length ? buffer.length : bytesToSend),
                        USB_TIMEOUT_MS);
                if (sentBytes < 0) {
                    Log.e(TAG, "Failed to write data/");
                    return;
                } else {
                    bytesToSend -= sentBytes;
                }
            }
            setSpeed(TEST_DATA_SIZE / (System.currentTimeMillis() - timeStart));
        }
    }

    private class WriterAsyncThread extends BaseWriterThread {
        private WriterAsyncThread(UsbDevice device, UsbDeviceConnection conn, int bufferSize) {
            super(device, conn, bufferSize);
        }

        private boolean drainRequests(int numRequests) {
            while (numRequests > 0) {
                UsbRequest req = mUsbConnection.requestWait();
                if (req == null) {
                    Log.e(TAG, "Error while requestWait");
                    return false;
                }
                req.close();
                numRequests--;
            }
            return true;
        }

        private boolean writeBufferSize() {
            byte[] bufferSizeArray = intToByte(mBufferSize);
            UsbRequest sendRequest = getNewRequest();
            if (sendRequest == null) {
                return false;
            }

            ByteBuffer bufferToSend = ByteBuffer.wrap(bufferSizeArray, 0, bufferSizeArray.length);
            boolean queued = sendRequest.queue(bufferToSend, bufferSizeArray.length);

            if (!queued) {
                Log.e(TAG, "Failed to queue request");
                return false;
            }

            UsbRequest req = mUsbConnection.requestWait();
            if (req == null) {
                Log.e(TAG, "Error while waiting for request to complete.");
                return false;
            }
            req.close();
            return true;
        }

        private UsbRequest getNewRequest() {
            UsbRequest request = new UsbRequest();
            if (!request.initialize(mUsbConnection, mBulkOut)) {
                Log.e(TAG, "Failed to init");
                return null;
            }
            return request;
        }

        @Override
        public void run() {
            int bytesToSend = TEST_DATA_SIZE;
            if (!writeBufferSize()) {
                return;
            }
            int numRequests = 0;
            byte[] buffer = new byte[mBufferSize];
            sRandom.nextBytes(buffer);

            long timeStart = System.currentTimeMillis();
            while (bytesToSend > 0 && !shouldQuit()) {
                numRequests++;
                UsbRequest sendRequest = getNewRequest();
                if (sendRequest == null) {
                    return;
                }

                int bufferSize = (bytesToSend > buffer.length ? buffer.length : bytesToSend);
                ByteBuffer bufferToSend = ByteBuffer.wrap(buffer, 0, bufferSize);
                boolean queued = sendRequest.queue(bufferToSend, bufferSize);
                if (queued) {
                    bytesToSend -= buffer.length;
                } else {
                    Log.e(TAG, "Failed to queue more data");
                    return;
                }

                if (numRequests == ASYNC_MAX_OUTSTANDING_REQUESTS) {
                    UsbRequest req = mUsbConnection.requestWait();
                    if (req == null) {
                        Log.e(TAG, "Error while waiting for request to complete.");
                        return;
                    }
                    req.close();
                    numRequests--;
                }
            }

            if (!drainRequests(numRequests)) {
                return;
            }
            setSpeed(TEST_DATA_SIZE / (System.currentTimeMillis() - timeStart));
            Log.d(TAG, "Wrote all the data. Exiting thread");
        }
    }
}
