/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.media.cts;

import java.io.IOException;


import android.content.Context;
import android.content.pm.PackageManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDevice.MidiConnection;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiDeviceStatus;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;
import android.media.midi.MidiSender;
import android.os.Handler;
import android.os.Looper;

import com.android.compatibility.common.util.CtsAndroidTestCase;

/**
 * Test MIDI when there may be no MIDI devices available. There is not much we
 * can test without a device.
 */
public class MidiSoloTest extends CtsAndroidTestCase {
    private static final String TAG = "MidiSoloTest";
    private final static int LOCAL_STORAGE_SIZE = 256;

    // Store received data so we can check it later.
    class MyMidiReceiver extends MidiReceiver {
        public int byteCount;
        public byte[] data = new byte[LOCAL_STORAGE_SIZE];

        public MyMidiReceiver(int maxMessageSize) {
            super(maxMessageSize);
        }

        @Override
        // Abstract method declared in MidiReceiver
        public void onSend(byte[] msg, int offset, int count, long timestamp)
                throws IOException {
            assertTrue("Message too large.", (count <= getMaxMessageSize()));
            try {
                System.arraycopy(msg, offset, data, byteCount, count);
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new IOException("Exceeded local storage.", e);
            }
            byteCount += count;
        }

        @Override
        public void onFlush() {
            byteCount = 0;
        }
    }

    @Override
    protected void setUp() throws Exception {
        // setup for each test case.
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        // Test case clean up.
        super.tearDown();
    }

    public void testMidiManager() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiManager midiManager = (MidiManager) getContext().getSystemService(
                Context.MIDI_SERVICE);
        assertTrue("MidiManager not supported.", midiManager != null);

        MidiDeviceInfo[] infos = midiManager.getDevices();
        assertTrue("Device list was null.", infos != null);

        MidiManager.DeviceCallback callback = new MidiManager.DeviceCallback();

        // These should not crash.
        midiManager.unregisterDeviceCallback(callback);
        midiManager.registerDeviceCallback(callback, null);
        midiManager.unregisterDeviceCallback(callback);
        midiManager.registerDeviceCallback(callback, new Handler(Looper.getMainLooper()));
        midiManager.registerDeviceCallback(callback, new Handler(Looper.getMainLooper()));
        midiManager.unregisterDeviceCallback(callback);
        midiManager.unregisterDeviceCallback(callback);
        midiManager.unregisterDeviceCallback(callback);
    }

    public void testMidiReceiver() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiReceiver receiver = new MidiReceiver() {
                @Override
            public void onSend(byte[] msg, int offset, int count,
                    long timestamp) throws IOException {
            }
        };
        assertEquals("MidiReceiver default size wrong.", Integer.MAX_VALUE,
                receiver.getMaxMessageSize());

        int maxSize = 11;
        MyMidiReceiver myReceiver = new MyMidiReceiver(maxSize);
        assertEquals("MidiReceiver set size wrong.", maxSize,
                myReceiver.getMaxMessageSize());

        // Fill array with predictable data.
        byte[] bar = new byte[200];
        for (int i = 0; i < bar.length; i++) {
            bar[i] = (byte) (i ^ 15);
        }
        // Small message with no offset.
        int offset = 0;
        int count = 3;
        checkReceivedData(myReceiver, bar, offset, count);

        // Small with an offset.
        offset = 50;
        count = 3;
        checkReceivedData(myReceiver, bar, offset, count);

        // Entire array.
        offset = 0;
        count = bar.length;
        checkReceivedData(myReceiver, bar, offset, count);

        offset = 20;
        count = 100;
        checkReceivedData(myReceiver, bar, offset, count);
    }

    public void testMidiReceiverException() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        int maxSize = 11;
        MyMidiReceiver myReceiver = new MyMidiReceiver(maxSize);
        assertEquals("MidiReceiver set size wrong.", maxSize,
                myReceiver.getMaxMessageSize());

        // Fill array with predictable data.
        byte[] bar = new byte[200];
        int offset = 0;
        int count = bar.length;
        myReceiver.flush(); // reset byte counter
        IOException exception = null;
        // Send too much data and intentionally cause an IOException.
        try {
            int sent = 0;
            while (sent < LOCAL_STORAGE_SIZE) {
                myReceiver.send(bar, offset, count);
                sent += count;
            }
        } catch (IOException e) {
            exception = e;
        }
        assertTrue("We should have caught an IOException", exception != null);
    }

    // Does the data we sent match the data received by the MidiReceiver?
    private void checkReceivedData(MyMidiReceiver myReceiver, byte[] bar,
            int offset, int count) throws IOException {
        myReceiver.flush(); // reset byte counter
        assertEquals("MidiReceiver flush ", 0, myReceiver.byteCount);
        myReceiver.send(bar, offset, count);
        // Did we get all the data
        assertEquals("MidiReceiver count ", count, myReceiver.byteCount);
        for (int i = 0; i < count; i++) {
            assertEquals("MidiReceiver byte " + i + " + " + offset,
                    bar[i + offset], myReceiver.data[i]);
        }
    }
}
