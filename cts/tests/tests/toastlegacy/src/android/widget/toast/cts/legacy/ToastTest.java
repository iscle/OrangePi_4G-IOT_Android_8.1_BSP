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

package android.widget.toast.cts.legacy;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.toast.cts.BaseToastTest;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test whether toasts are properly shown. For apps targeting SDK
 * 25 and below a toast window can be added via the window APIs
 * but it will be removed after a timeout if the UID that added
 * the window is not focused. Also only a single toast window
 * is allowed at a time.
 */
@RunWith(AndroidJUnit4.class)
public class ToastTest extends BaseToastTest {
    @Rule
    public final ActivityTestRule<ToastActivity> mActivityRule =
            new ActivityTestRule<>(ToastActivity.class);

    @Test
    public void testAddSingleNotFocusableToastViaAddingWindowApisWhenUidFocused() throws Exception {
        // Show a toast on top of the focused activity
        showToastsViaAddingWindow(1, false);

        // Wait for the toast to timeout
        waitForToastTimeout();

        // Finish the activity so the UID loses focus
        finishActivity(false);

        // Wait for the toast to timeout
        waitForToastTimeout();

        // Show another toast
        showToastsViaAddingWindow(1, false);
    }

    @Test
    public void testAddSingleFocusableToastViaAddingWindowApisWhenUidFocused() throws Exception {
        // Show a toast on top of our activity
        showToastsViaAddingWindow(1, true);

        // Wait for the toast to timeout
        waitForToastTimeout();

        // Show a toast on top of our activity
        showToastsViaAddingWindow(1, true);
    }

    @Test
    public void testAddSingleToastViaAddingWindowApisWhenUidNotFocused() throws Exception {
        // Finish the activity so the UID loses focus
        finishActivity(false);

        // Show a toast
        showToastsViaAddingWindow(1, true);

        // Wait for the toast to timeout
        waitForToastTimeout();

        // Show a toast on top of our activity
        showToastsViaAddingWindow(1, true);
    }

    @Test
    public void testAddTwoToastsViaToastApisWhenUidFocused() throws Exception {
        // Finish the activity so the UID loses focus
        finishActivity(false);

        // Normal toast windows cannot be obtained vie the accessibility APIs because
        // they are not touchable. In this case not crashing is good enough.
        showToastsViaToastApis(2);

        // Wait for the first one to expire
        waitForToastTimeout();
    }

    @Test
    public void testAddTwoToastsViaToastApisWhenUidNotFocused() throws Exception {
        // Normal toast windows cannot be obtained vie the accessibility APIs because
        // they are not touchable. In this case not crashing is good enough.
        showToastsViaToastApis(2);

        // Wait for the first one to expire
        waitForToastTimeout();
    }

    @Test
    public void testAddTwoToastsViaAddingWindowApisWhenUidNotFocusedQuickly() throws Exception {
        // Finish the activity so the UID loses focus
        finishActivity(false);

        try {
            showToastsViaAddingWindow(2, false);
            Assert.fail("Only one custom toast window at a time should be allowed");
        } catch (WindowManager.BadTokenException e) {
            /* expected */
        } catch (Exception ex) {
            Assert.fail("Unexpected exception when adding second toast window" + ex);
        }
    }

    @Test
    public void testAddTwoToastsViaAddingWindowApisWhenUidFocusedQuickly() throws Exception {
        showToastsViaAddingWindow(2, false);

        // Wait for the toast to timeout
        waitForToastTimeout();
    }

    @Test
    public void testAddTwoToastsViaAddingWindowApisWhenUidFocusedSlowly() throws Exception {
        // Add one window
        showToastsViaAddingWindow(1, true);

        // Wait for the toast to timeout
        waitForToastTimeout();

        // Add another window
        showToastsViaAddingWindow(1, true);
    }

    private void finishActivity(boolean waitForEvent) throws Exception {
        if (waitForEvent) {
            mUiAutomation.executeAndWaitForEvent(
                    () -> mActivityRule.getActivity().finish(),
                    (event) -> event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    , EVENT_TIMEOUT_MILLIS);
        } else {
            mActivityRule.getActivity().finish();
        }
    }
}
