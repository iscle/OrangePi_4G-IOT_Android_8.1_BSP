/**
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.AccessibilityService.SoftKeyboardController;
import android.app.Activity;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import android.accessibilityservice.cts.R;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Test cases for testing the accessibility APIs for interacting with the soft keyboard show mode.
 */
public class AccessibilitySoftKeyboardModesTest extends ActivityInstrumentationTestCase2
        <AccessibilitySoftKeyboardModesTest.SoftKeyboardModesActivity> {

    private static final long TIMEOUT_PROPAGATE_SETTING = 5000;

    /**
     * Timeout required for pending Binder calls or event processing to
     * complete.
     */
    private static final long TIMEOUT_ASYNC_PROCESSING = 5000;

    /**
     * The timeout since the last accessibility event to consider the device idle.
     */
    private static final long TIMEOUT_ACCESSIBILITY_STATE_IDLE = 500;

    /**
     * The timeout since {@link InputMethodManager#showSoftInput(View, int, ResultReceiver)}
     * is called to {@link ResultReceiver#onReceiveResult(int, Bundle)} is called back.
     */
    private static final int TIMEOUT_SHOW_SOFTINPUT_RESULT = 2000;

    private static final int SHOW_MODE_AUTO = 0;
    private static final int SHOW_MODE_HIDDEN = 1;

    private int mLastCallbackValue;

    private InstrumentedAccessibilityService mService;
    private SoftKeyboardController mKeyboardController;
    private UiAutomation mUiAutomation;

    private Object mLock = new Object();

    public AccessibilitySoftKeyboardModesTest() {
        super(SoftKeyboardModesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // If we don't call getActivity(), we get an empty list when requesting the number of
        // windows on screen.
        getActivity();

        mService = InstrumentedAccessibilityService.enableService(
                getInstrumentation(), InstrumentedAccessibilityService.class);
        mKeyboardController = mService.getSoftKeyboardController();
        mUiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info = mUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        mUiAutomation.setServiceInfo(info);
    }

    @Override
    public void tearDown() throws Exception {
        mKeyboardController.setShowMode(SHOW_MODE_AUTO);
        mService.runOnServiceSync(() -> mService.disableSelf());
        Activity activity = getActivity();
        activity.getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
    }

    public void testApiReturnValues_shouldChangeValueOnRequestAndSendCallback() throws Exception {
        mLastCallbackValue = -1;

        final SoftKeyboardController.OnShowModeChangedListener listener =
                new SoftKeyboardController.OnShowModeChangedListener() {
                    @Override
                    public void onShowModeChanged(SoftKeyboardController controller, int showMode) {
                        synchronized (mLock) {
                            mLastCallbackValue = showMode;
                            mLock.notifyAll();
                        }
                    }
                };
        mKeyboardController.addOnShowModeChangedListener(listener);

        // The soft keyboard should be in its default mode.
        assertEquals(SHOW_MODE_AUTO, mKeyboardController.getShowMode());

        // Set the show mode to SHOW_MODE_HIDDEN.
        assertTrue(mKeyboardController.setShowMode(SHOW_MODE_HIDDEN));

        // Make sure the mode was changed.
        assertEquals(SHOW_MODE_HIDDEN, mKeyboardController.getShowMode());

        // Make sure we're getting the callback with the proper value.
        waitForCallbackValueWithLock(SHOW_MODE_HIDDEN);

        // Make sure we can set the value back to the default.
        assertTrue(mKeyboardController.setShowMode(SHOW_MODE_AUTO));
        assertEquals(SHOW_MODE_AUTO, mKeyboardController.getShowMode());
        waitForCallbackValueWithLock(SHOW_MODE_AUTO);

        // Make sure we can remove our listener.
        assertTrue(mKeyboardController.removeOnShowModeChangedListener(listener));
    }

    public void testHideSoftKeyboard_shouldHideKeyboardOnRequest() throws Exception {
        // The soft keyboard should be in its default mode.
        assertEquals(SHOW_MODE_AUTO, mKeyboardController.getShowMode());

        if (!tryShowSoftInput()) {
            // If the current (default) IME declined to show its window, then there is nothing we
            // can test here.
            // TODO: Create a mock IME so that we can test only the framework behavior.
            return;
        }

        waitForImePresentToBe(true);
        // Request the keyboard be hidden.
        assertTrue(mKeyboardController.setShowMode(SHOW_MODE_HIDDEN));

        waitForImePresentToBe(false);

        assertTrue(mKeyboardController.setShowMode(SHOW_MODE_AUTO));
    }

    private void waitForCallbackValueWithLock(int expectedValue) throws Exception {
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_PROPAGATE_SETTING;

        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized(mLock) {
                if (mLastCallbackValue == expectedValue) {
                    return;
                }
                try {
                    mLock.wait(timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Wait until timeout.
                }
            }
        }

        throw new IllegalStateException("last callback value <" + mLastCallbackValue
                + "> does not match expected value < " + expectedValue + ">");
    }

    private void waitForWindowChanges() {
        try {
            mUiAutomation.executeAndWaitForEvent(new Runnable() {
                @Override
                public void run() {
                    // Do nothing.
                }
            },
            new UiAutomation.AccessibilityEventFilter() {
                @Override
                public boolean accept (AccessibilityEvent event) {
                    return event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
                }
            },
            TIMEOUT_PROPAGATE_SETTING);
        } catch (TimeoutException ignored) {
            // Ignore since the event could have occurred before this method was called. There
            // should be a check after this method returns to catch incorrect values.
        }
    }

    private boolean isImeWindowPresent() {
        List<AccessibilityWindowInfo> windows = mUiAutomation.getWindows();
        for (int i = 0; i < windows.size(); i++) {
            if (windows.get(i).getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                return true;
            }
        }
        return false;
    }

    private void waitForImePresentToBe(boolean imeShown) {
        long timeOutTime = System.currentTimeMillis() + TIMEOUT_ASYNC_PROCESSING;
        while (isImeWindowPresent() != imeShown) {
            assertTrue(System.currentTimeMillis() < timeOutTime);
            waitForWindowChanges();
        }
    }

    /**
     * Tries to call {@link InputMethodManager#hideSoftInputFromWindow(IBinder, int)} to see if
     * software keyboard is shown as a result or not.
     * @return {@code true} if the current input method reported that it is currently shown
     * @throws Exception when the result is unknown, including the system did not return the result
     *                   within {@link #TIMEOUT_SHOW_SOFTINPUT_RESULT}
     */
    private boolean tryShowSoftInput() throws Exception {
        final BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);

        getInstrumentation().runOnMainSync(() -> {
            Activity activity = getActivity();
            ResultReceiver resultReceiver =
                    new ResultReceiver(new Handler(activity.getMainLooper())) {
                            @Override
                            protected void onReceiveResult(int resultCode, Bundle resultData) {
                                queue.add(resultCode);
                            }
                    };
            View editText = activity.findViewById(R.id.edit_text);
            activity.getSystemService(InputMethodManager.class)
                    .showSoftInput(editText, InputMethodManager.SHOW_FORCED, resultReceiver);
        });

        Integer result;
        try {
            result = queue.poll(TIMEOUT_SHOW_SOFTINPUT_RESULT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new Exception("Failed to get the result of showSoftInput().", e);
        }
        if (result == null) {
            throw new Exception("Failed to get the result of showSoftInput() within timeout.");
        }
        switch (result) {
            case InputMethodManager.RESULT_SHOWN:
            case InputMethodManager.RESULT_UNCHANGED_SHOWN:
                return true;
            default:
                return false;
        }
    }

    /**
     * Activity for testing the AccessibilityService API for hiding and showring the soft keyboard.
     */
    public static class SoftKeyboardModesActivity extends AccessibilityTestActivity {
        public SoftKeyboardModesActivity() {
            super();
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.accessibility_soft_keyboard_modes_test);
        }
    }
}
