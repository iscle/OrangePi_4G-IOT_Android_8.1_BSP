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

package android.view.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ContentPaneFocusTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<ContentPaneCtsActivity> mActivityRule =
            new ActivityTestRule<>(ContentPaneCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testAccessActionBar() throws Throwable {
        final View v1 = mActivity.findViewById(R.id.view1);
        mActivityRule.runOnUiThread(v1::requestFocus);

        mInstrumentation.waitForIdleSync();
        sendMetaHotkey(KeyEvent.KEYCODE_TAB);
        mInstrumentation.waitForIdleSync();

        ActionBar action = mActivity.getActionBar();
        if (action == null || !action.isShowing()) {
            // No action bar, so we only needed to make sure that the shortcut didn't cause
            // the framework to crash.
            return;
        }

        final View actionBar = getActionBarView();
        // Should jump to the action bar after meta+tab
        mActivityRule.runOnUiThread(() -> {
            assertFalse(v1.hasFocus());
            assertTrue(actionBar.hasFocus());
        });

        boolean isTouchScreen = mActivity.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        if (isTouchScreen) {
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
            mInstrumentation.waitForIdleSync();

            // Shouldn't leave actionbar with normal keyboard navigation on touchscreens.
            mActivityRule.runOnUiThread(() -> assertTrue(actionBar.hasFocus()));
        }

        sendMetaHotkey(KeyEvent.KEYCODE_TAB);
        mInstrumentation.waitForIdleSync();

        // Should jump to the first view again.
        mActivityRule.runOnUiThread(() -> assertTrue(v1.hasFocus()));

        if (isTouchScreen) {
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP);
            mInstrumentation.waitForIdleSync();
            // Now it shouldn't go up to action bar -- it doesn't allow taking focus once left
            // but only for touch screens.
            mActivityRule.runOnUiThread(() -> assertTrue(v1.hasFocus()));
        }
    }

    @Test
    public void testNoFocusablesInContent() throws Throwable {
        ViewGroup top = mActivity.findViewById(R.id.linearlayout);
        top.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mActivityRule.runOnUiThread(top::clearFocus);
        mInstrumentation.waitForIdleSync();
        top.clearFocus();
        final View content = mActivity.findViewById(android.R.id.content);
        assertTrue(content.findFocus() == null);
        sendMetaHotkey(KeyEvent.KEYCODE_TAB);
        mInstrumentation.waitForIdleSync();

        ActionBar action = mActivity.getActionBar();
        if (action == null || !action.isShowing()) {
            // No action bar, so we only needed to make sure that the shortcut didn't cause
            // the framework to crash.
            return;
        }

        assertTrue(getActionBarView().hasFocus());
    }

    private View getActionBarView() {
        final View content = mActivity.findViewById(android.R.id.content);
        assertNotNull(content);
        final ViewParent viewParent = content.getParent();
        assertNotNull(viewParent);
        assertTrue(viewParent instanceof ViewGroup);
        ViewGroup parent = (ViewGroup) viewParent;
        View actionBarView = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if ("android:action_bar".equals(child.getTransitionName())) {
                actionBarView = child;
                break;
            }
        }
        assertNotNull(actionBarView);
        return actionBarView;
    }

    private void sendMetaHotkey(int keyCode) throws Throwable {
        sendMetaKey(KeyEvent.ACTION_DOWN);
        long time = SystemClock.uptimeMillis();
        KeyEvent metaHotkey = new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode,
                0, KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON);
        mInstrumentation.sendKeySync(metaHotkey);
        time = SystemClock.uptimeMillis();
        metaHotkey = new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode,
                0, KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON);
        mInstrumentation.sendKeySync(metaHotkey);
        Thread.sleep(2);
        sendMetaKey(KeyEvent.ACTION_UP);
    }

    private void sendMetaKey(int action) throws Throwable {
        long time = SystemClock.uptimeMillis();
        KeyEvent keyEvent = new KeyEvent(time, time, action, KeyEvent.KEYCODE_META_LEFT, 0,
                KeyEvent.META_META_LEFT_ON | KeyEvent.META_META_ON);
        mInstrumentation.sendKeySync(keyEvent);
        Thread.sleep(2);
    }
}
