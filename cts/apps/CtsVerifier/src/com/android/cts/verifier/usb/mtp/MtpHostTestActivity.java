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

package com.android.cts.verifier.usb.mtp;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.mtp.MtpConstants;
import android.mtp.MtpDevice;
import android.mtp.MtpDeviceInfo;
import android.mtp.MtpEvent;
import android.mtp.MtpObjectInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.MutableInt;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import junit.framework.AssertionFailedError;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MtpHostTestActivity extends PassFailButtons.Activity implements Handler.Callback {
    private static final int MESSAGE_PASS = 0;
    private static final int MESSAGE_FAIL = 1;
    private static final int MESSAGE_RUN = 2;

    private static final int ITEM_STATE_PASS = 0;
    private static final int ITEM_STATE_FAIL = 1;
    private static final int ITEM_STATE_INDETERMINATE = 2;

    /**
     * Subclass for PTP.
     */
    private static final int SUBCLASS_STILL_IMAGE_CAPTURE = 1;

    /**
     * Subclass for Android style MTP.
     */
    private static final int SUBCLASS_MTP = 0xff;

    /**
     * Protocol for Picture Transfer Protocol (PIMA 15470).
     */
    private static final int PROTOCOL_PICTURE_TRANSFER = 1;

    /**
     * Protocol for Android style MTP.
     */
    private static final int PROTOCOL_MTP = 0;

    private static final int RETRY_DELAY_MS = 1000;

    private static final String ACTION_PERMISSION_GRANTED =
            "com.android.cts.verifier.usb.ACTION_PERMISSION_GRANTED";

    private static final String TEST_FILE_NAME = "CtsVerifierTest_testfile.txt";
    private static final byte[] TEST_FILE_CONTENTS =
            "This is a test file created by CTS verifier test.".getBytes(StandardCharsets.US_ASCII);

    private final Handler mHandler = new Handler(this);
    private int mStep;
    private final ArrayList<TestItem> mItems = new ArrayList<>();

    private UsbManager mUsbManager;
    private BroadcastReceiver mReceiver;
    private UsbDevice mUsbDevice;
    private MtpDevice mMtpDevice;
    private ExecutorService mExecutor;
    private TextView mErrorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mtp_host_activity);
        setInfoResources(R.string.mtp_host_test, R.string.mtp_host_test_info, -1);
        setPassFailButtonClickListeners();

        final LayoutInflater inflater = getLayoutInflater();
        final LinearLayout itemsView = (LinearLayout) findViewById(R.id.mtp_host_list);

        mErrorText = (TextView) findViewById(R.id.error_text);

        // Don't allow a test pass until all steps are passed.
        getPassButton().setEnabled(false);

        // Build test items.
        mItems.add(new TestItem(
                inflater,
                R.string.mtp_host_device_lookup_message,
                new int[] { R.id.next_item_button }));
        mItems.add(new TestItem(
                inflater,
                R.string.mtp_host_test_file_browse_message,
                new int[] { R.id.settings_button, R.id.pass_item_button, R.id.fail_item_button }));
        mItems.add(new TestItem(
                inflater,
                R.string.mtp_host_grant_permission_message,
                null));
        mItems.add(new TestItem(
                inflater,
                R.string.mtp_host_test_read_event_message,
                null));
        mItems.add(new TestItem(
                inflater,
                R.string.mtp_host_test_send_object_message,
                null));
        for (final TestItem item : mItems) {
            itemsView.addView(item.view);
        }

        mExecutor = Executors.newSingleThreadExecutor();
        mUsbManager = getSystemService(UsbManager.class);

        mStep = 0;
        mHandler.sendEmptyMessage(MESSAGE_RUN);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        final TestItem item = mStep < mItems.size() ? mItems.get(mStep) : null;

        switch (msg.what) {
            case MESSAGE_RUN:
                if (item == null) {
                    getPassButton().setEnabled(true);
                    return true;
                }
                item.setEnabled(true);
                mExecutor.execute(new Runnable() {
                    private final int mCurrentStep = mStep;

                    @Override
                    public void run() {
                        try {
                            switch (mCurrentStep) {
                                case 0:
                                    stepFindMtpDevice();
                                    break;
                                case 1:
                                    stepTestFileBrowse();
                                    break;
                                case 2:
                                    stepGrantPermission();
                                    break;
                                case 3:
                                    stepTestReadEvent();
                                    break;
                                case 4:
                                    stepTestSendObject();
                                    break;
                            }
                            mHandler.sendEmptyMessage(MESSAGE_PASS);
                        } catch (Exception | AssertionFailedError exception) {
                            mHandler.sendMessage(mHandler.obtainMessage(MESSAGE_FAIL, exception));
                        }
                    }
                });
                break;

            case MESSAGE_PASS:
                item.setState(ITEM_STATE_PASS);
                item.setEnabled(false);
                mStep++;
                mHandler.sendEmptyMessage(MESSAGE_RUN);
                break;

            case MESSAGE_FAIL:
                item.setState(ITEM_STATE_FAIL);
                item.setEnabled(false);
                final StringWriter writer = new StringWriter();
                final Throwable throwable = (Throwable) msg.obj;
                throwable.printStackTrace(new PrintWriter(writer));
                mErrorText.setText(writer.toString());
                break;
        }

        return true;
    }

    private void stepFindMtpDevice() throws InterruptedException {
        assertEquals(R.id.next_item_button, waitForButtonClick());

        UsbDevice device = null;
        for (final UsbDevice candidate : mUsbManager.getDeviceList().values()) {
            if (isMtpDevice(candidate)) {
                device = candidate;
                break;
            }
        }
        assertNotNull(device);
        mUsbDevice = device;
    }

    private void stepGrantPermission() throws InterruptedException {
        if (!mUsbManager.hasPermission(mUsbDevice)) {
            final CountDownLatch latch = new CountDownLatch(1);
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    unregisterReceiver(this);
                    mReceiver = null;
                    latch.countDown();
                }
            };
            registerReceiver(mReceiver, new IntentFilter(ACTION_PERMISSION_GRANTED));
            mUsbManager.requestPermission(
                    mUsbDevice,
                    PendingIntent.getBroadcast(
                            MtpHostTestActivity.this, 0, new Intent(ACTION_PERMISSION_GRANTED), 0));

            latch.await();
            assertTrue(mUsbManager.hasPermission(mUsbDevice));
        }

        final UsbDeviceConnection connection = mUsbManager.openDevice(mUsbDevice);
        assertNotNull(connection);

        // Try to rob device ownership from other applications.
        for (int i = 0; i < mUsbDevice.getInterfaceCount(); i++) {
            connection.claimInterface(mUsbDevice.getInterface(i), true);
            connection.releaseInterface(mUsbDevice.getInterface(i));
        }
        mMtpDevice = new MtpDevice(mUsbDevice);
        assertTrue(mMtpDevice.open(connection));
        assertTrue(mMtpDevice.getStorageIds().length > 0);
    }

    private void stepTestReadEvent() {
        assertNotNull(mMtpDevice.getDeviceInfo().getEventsSupported());
        assertTrue(mMtpDevice.getDeviceInfo().isEventSupported(MtpEvent.EVENT_OBJECT_ADDED));

        mMtpDevice.getObjectHandles(0xFFFFFFFF, 0x0, 0x0);
        while (true) {
            MtpEvent event;
            try {
                event = mMtpDevice.readEvent(null);
            } catch (IOException e) {
                fail();
                return;
            }
            if (event.getEventCode() == MtpEvent.EVENT_OBJECT_ADDED) {
                break;
            }
            SystemClock.sleep(RETRY_DELAY_MS);
        }
    }

    private void stepTestSendObject() throws IOException {
        final MtpDeviceInfo deviceInfo = mMtpDevice.getDeviceInfo();
        assertNotNull(deviceInfo.getOperationsSupported());
        assertTrue(deviceInfo.isOperationSupported(MtpConstants.OPERATION_SEND_OBJECT_INFO));
        assertTrue(deviceInfo.isOperationSupported(MtpConstants.OPERATION_SEND_OBJECT));

        // Delete an existing test file that may be created by the test previously.
        final int storageId = mMtpDevice.getStorageIds()[0];
        for (final int objectHandle : mMtpDevice.getObjectHandles(
                storageId, /* all format */ 0, /* Just under the root */ -1)) {
            final MtpObjectInfo info = mMtpDevice.getObjectInfo(objectHandle);
            if (TEST_FILE_NAME.equals(info.getName())) {
                assertTrue(mMtpDevice.deleteObject(objectHandle));
            }
        }

        final MtpObjectInfo info = new MtpObjectInfo.Builder()
                .setStorageId(storageId)
                .setName(TEST_FILE_NAME)
                .setCompressedSize(TEST_FILE_CONTENTS.length)
                .setFormat(MtpConstants.FORMAT_TEXT)
                .setParent(-1)
                .build();
        final MtpObjectInfo newInfo = mMtpDevice.sendObjectInfo(info);
        assertNotNull(newInfo);
        assertTrue(newInfo.getObjectHandle() != -1);

        final ParcelFileDescriptor[] pipes = ParcelFileDescriptor.createPipe();
        try {
            try (final ParcelFileDescriptor.AutoCloseOutputStream stream =
                    new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])) {
                stream.write(TEST_FILE_CONTENTS);
            }
            assertTrue(mMtpDevice.sendObject(
                    newInfo.getObjectHandle(),
                    newInfo.getCompressedSizeLong(),
                    pipes[0]));
        } finally {
            pipes[0].close();
        }
    }

    private void stepTestFileBrowse() throws InterruptedException {
        while (true) {
            final int id = waitForButtonClick();
            if (id == R.id.settings_button) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
                continue;
            }
            assertEquals(R.id.pass_item_button, waitForButtonClick());
            break;
        }
    }

    private int waitForButtonClick() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final MutableInt result = new MutableInt(-1);
        mItems.get(mStep).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                result.value = v.getId();
                latch.countDown();
            }
        });
        latch.await();
        return result.value;
    }

    private static boolean isMtpDevice(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            final UsbInterface usbInterface = device.getInterface(i);
            if ((usbInterface.getInterfaceClass() == UsbConstants.USB_CLASS_STILL_IMAGE &&
                    usbInterface.getInterfaceSubclass() == SUBCLASS_STILL_IMAGE_CAPTURE &&
                    usbInterface.getInterfaceProtocol() == PROTOCOL_PICTURE_TRANSFER)) {
                return true;
            }
            if (usbInterface.getInterfaceClass() == UsbConstants.USB_SUBCLASS_VENDOR_SPEC &&
                    usbInterface.getInterfaceSubclass() == SUBCLASS_MTP &&
                    usbInterface.getInterfaceProtocol() == PROTOCOL_MTP &&
                    "MTP".equals(usbInterface.getName())) {
                return true;
            }
        }
        return false;
    }

    private static class TestItem {
        private final View view;
        private final int[] buttons;

        TestItem(LayoutInflater inflater,
                 int messageText,
                 int[] buttons) {
            this.view = inflater.inflate(R.layout.mtp_host_item, null, false);

            final TextView textView = (TextView) view.findViewById(R.id.instructions);
            textView.setText(messageText);

            this.buttons = buttons != null ? buttons : new int[0];
            for (final int id : this.buttons) {
                final Button button = (Button) view.findViewById(id);
                button.setVisibility(View.VISIBLE);
                button.setEnabled(false);
            }
        }

        void setOnClickListener(OnClickListener listener) {
            for (final int id : buttons) {
                final Button button = (Button) view.findViewById(id);
                button.setOnClickListener(listener);
            }
        }

        void setEnabled(boolean value) {
            for (final int id : buttons) {
                final Button button = (Button) view.findViewById(id);
                button.setEnabled(value);
            }
        }

        Button getButton(int id) {
            return (Button) view.findViewById(id);
        }

        void setState(int state) {
            final ImageView imageView = (ImageView) view.findViewById(R.id.status);
            switch (state) {
                case ITEM_STATE_PASS:
                    imageView.setImageResource(R.drawable.fs_good);
                    break;
                case ITEM_STATE_FAIL:
                    imageView.setImageResource(R.drawable.fs_error);
                    break;
                case ITEM_STATE_INDETERMINATE:
                    imageView.setImageResource(R.drawable.fs_indeterminate);
                    break;
            }
        }
    }
}
