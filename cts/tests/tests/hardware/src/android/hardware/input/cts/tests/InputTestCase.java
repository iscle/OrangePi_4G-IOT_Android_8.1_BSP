/*
 * Copyright 2015 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.hardware.input.cts.InputCallback;
import android.hardware.input.cts.InputCtsActivity;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import libcore.io.IoUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

public class InputTestCase {
    // hid executable expects "-" argument to read from stdin instead of a file
    private static final String HID_COMMAND = "hid -";
    private static final String[] KEY_ACTIONS = {"DOWN", "UP", "MULTIPLE"};

    private OutputStream mOutputStream;

    private final BlockingQueue<KeyEvent> mKeys;
    private final BlockingQueue<MotionEvent> mMotions;
    private InputListener mInputListener;

    private Instrumentation mInstrumentation;

    private volatile CountDownLatch mDeviceAddedSignal; // to wait for onInputDeviceAdded signal

    public InputTestCase() {
        mKeys = new LinkedBlockingQueue<KeyEvent>();
        mMotions = new LinkedBlockingQueue<MotionEvent>();
        mInputListener = new InputListener();
    }

    @Rule
    public ActivityTestRule<InputCtsActivity> mActivityRule =
        new ActivityTestRule<>(InputCtsActivity.class);

    @Before
    public void setUp() throws Exception {
        clearKeys();
        clearMotions();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivityRule.getActivity().setInputCallback(mInputListener);
        setupPipes();
    }

    @After
    public void tearDown() throws Exception {
        IoUtils.closeQuietly(mOutputStream);
    }

    /**
     * Register an input device. May cause a failure if the device added notification
     * is not received within the timeout period
     *
     * @param resourceId The resource id from which to send the register command.
     */
    public void registerInputDevice(int resourceId) {
        mDeviceAddedSignal = new CountDownLatch(1);
        sendHidCommands(resourceId);
        try {
            // Found that in kernel 3.10, the device registration takes a very long time
            // The wait can be decreased to 2 seconds after kernel 3.10 is no longer supported
            mDeviceAddedSignal.await(20L, TimeUnit.SECONDS);
            if (mDeviceAddedSignal.getCount() != 0) {
                fail("Device added notification was not received in time.");
            }
        } catch (InterruptedException ex) {
            fail("Unexpectedly interrupted while waiting for device added notification.");
        }
    }

    /**
     * Sends the HID commands designated by the given resource id.
     * The commands must be in the format expected by the `hid` shell command.
     *
     * @param id The resource id from which to load the HID commands. This must be a "raw"
     * resource.
     */
    public void sendHidCommands(int id) {
        try {
            mOutputStream.write(getEvents(id).getBytes());
            mOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Asserts that the application received a {@link android.view.KeyEvent} with the given action
     * and keycode.
     *
     * If other KeyEvents are received by the application prior to the expected KeyEvent, or no
     * KeyEvents are received within a reasonable amount of time, then this will throw an
     * AssertionFailedError.
     *
     * @param action The action to expect on the next KeyEvent
     * (e.g. {@link android.view.KeyEvent#ACTION_DOWN}).
     * @param keyCode The expected key code of the next KeyEvent.
     */
    public void assertReceivedKeyEvent(int action, int keyCode) {
        KeyEvent k = waitForKey();
        if (k == null) {
            fail("Timed out waiting for " + KeyEvent.keyCodeToString(keyCode)
                    + " with action " + KEY_ACTIONS[action]);
            return;
        }
        assertEquals(action, k.getAction());
        assertEquals(keyCode, k.getKeyCode());
    }

    /**
     * Asserts that no more events have been received by the application.
     *
     * If any more events have been received by the application, this throws an
     * AssertionFailedError.
     */
    public void assertNoMoreEvents() {
        KeyEvent key;
        MotionEvent motion;
        if ((key = mKeys.poll()) != null) {
            fail("Extraneous key events generated: " + key);
        }
        if ((motion = mMotions.poll()) != null) {
            fail("Extraneous motion events generated: " + motion);
        }
    }

    private KeyEvent waitForKey() {
        try {
            return mKeys.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return null;
        }
    }

    private void clearKeys() {
        mKeys.clear();
    }

    private void clearMotions() {
        mMotions.clear();
    }

    private void setupPipes() throws IOException {
        UiAutomation ui = mInstrumentation.getUiAutomation();
        ParcelFileDescriptor[] pipes = ui.executeShellCommandRw(HID_COMMAND);

        mOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]);
        IoUtils.closeQuietly(pipes[0]); // hid command is write-only
    }

    private String getEvents(int id) throws IOException {
        InputStream is =
            mInstrumentation.getTargetContext().getResources().openRawResource(id);
        return readFully(is);
    }

    private static String readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read = 0;
        byte[] buffer = new byte[1024];
        while ((read = is.read(buffer)) >= 0) {
            baos.write(buffer, 0, read);
        }
        return baos.toString();
    }

    private class InputListener implements InputCallback {
        @Override
        public void onKeyEvent(KeyEvent ev) {
            boolean done = false;
            do {
                try {
                    mKeys.put(new KeyEvent(ev));
                    done = true;
                } catch (InterruptedException ignore) { }
            } while (!done);
        }

        @Override
        public void onMotionEvent(MotionEvent ev) {
            boolean done = false;
            do {
                try {
                    mMotions.put(MotionEvent.obtain(ev));
                    done = true;
                } catch (InterruptedException ignore) { }
            } while (!done);
        }

        @Override
        public void onInputDeviceAdded(int deviceId) {
            mDeviceAddedSignal.countDown();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
        }
    }


}
