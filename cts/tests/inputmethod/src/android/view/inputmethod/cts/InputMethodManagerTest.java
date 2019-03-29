/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.inputmethod.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.Window;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.R;
import android.widget.EditText;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class InputMethodManagerTest {
    private Instrumentation mInstrumentation;
    private InputMethodCtsActivity mActivity;

    @Rule
    public ActivityTestRule<InputMethodCtsActivity> mActivityRule =
            new ActivityTestRule<>(InputMethodCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void teardown() {
        mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }

    @Test
    public void testInputMethodManager() throws Throwable {
        if (!mActivity.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS)) {
            return;
        }

        Window window = mActivity.getWindow();
        final EditText view = (EditText) window.findViewById(R.id.entry);

        PollingCheck.waitFor(1000, view::hasWindowFocus);

        mActivityRule.runOnUiThread(view::requestFocus);
        mInstrumentation.waitForIdleSync();
        assertTrue(view.isFocused());

        BaseInputConnection connection = new BaseInputConnection(view, false);
        Context context = mInstrumentation.getTargetContext();
        final InputMethodManager imManager = (InputMethodManager) context
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        PollingCheck.waitFor(imManager::isActive);

        assertTrue(imManager.isAcceptingText());
        assertTrue(imManager.isActive(view));

        assertFalse(imManager.isFullscreenMode());
        connection.reportFullscreenMode(true);
        // Only IMEs are allowed to report full-screen mode.  Calling this method from the
        // application should have no effect.
        assertFalse(imManager.isFullscreenMode());

        mActivityRule.runOnUiThread(() -> {
            IBinder token = view.getWindowToken();

            // Show and hide input method.
            assertTrue(imManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT));
            assertTrue(imManager.hideSoftInputFromWindow(token, 0));

            Handler handler = new Handler();
            ResultReceiver receiver = new ResultReceiver(handler);
            assertTrue(imManager.showSoftInput(view, 0, receiver));
            receiver = new ResultReceiver(handler);
            assertTrue(imManager.hideSoftInputFromWindow(token, 0, receiver));

            imManager.showSoftInputFromInputMethod(token, InputMethodManager.SHOW_FORCED);
            imManager.hideSoftInputFromInputMethod(token, InputMethodManager.HIDE_NOT_ALWAYS);

            // status: hide to show to hide
            imManager.toggleSoftInputFromWindow(token, 0, InputMethodManager.HIDE_NOT_ALWAYS);
            imManager.toggleSoftInputFromWindow(token, 0, InputMethodManager.HIDE_NOT_ALWAYS);

            List<InputMethodInfo> enabledImList = imManager.getEnabledInputMethodList();
            if (enabledImList != null && enabledImList.size() > 0) {
                imManager.setInputMethod(token, enabledImList.get(0).getId());
                // cannot test whether setting was successful
            }

            List<InputMethodInfo> imList = imManager.getInputMethodList();
            if (imList != null && enabledImList != null) {
                assertTrue(imList.size() >= enabledImList.size());
            }
        });
        mInstrumentation.waitForIdleSync();
    }
}
