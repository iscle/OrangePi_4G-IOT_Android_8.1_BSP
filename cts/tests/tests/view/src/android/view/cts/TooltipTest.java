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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link View}.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class TooltipTest {
    private static final String LOG_TAG = "TooltipTest";

    private static final long TIMEOUT_DELTA = 10000;
    private static final long WAIT_MARGIN = 100;

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private ViewGroup mTopmostView;
    private ViewGroup mGroupView;
    private View mNoTooltipView;
    private View mTooltipView;
    private View mNoTooltipView2;
    private View mEmptyGroup;

    @Rule
    public ActivityTestRule<TooltipActivity> mActivityRule =
            new ActivityTestRule<>(TooltipActivity.class);

    @Rule
    public ActivityTestRule<CtsActivity> mCtsActivityRule =
            new ActivityTestRule<>(CtsActivity.class, false, false);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mTopmostView = (ViewGroup) mActivity.findViewById(R.id.tooltip_layout);
        mGroupView = (ViewGroup) mActivity.findViewById(R.id.tooltip_group);
        mNoTooltipView = mActivity.findViewById(R.id.no_tooltip);
        mTooltipView = mActivity.findViewById(R.id.has_tooltip);
        mNoTooltipView2 = mActivity.findViewById(R.id.no_tooltip2);
        mEmptyGroup = mActivity.findViewById(R.id.empty_group);

        PollingCheck.waitFor(TIMEOUT_DELTA, mActivity::hasWindowFocus);
    }

    private void waitOut(long msDelay) {
        try {
            Thread.sleep(msDelay + WAIT_MARGIN);
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Wait interrupted. Test may fail!", e);
        }
    }

    private void setTooltipText(View view, CharSequence tooltipText) throws Throwable {
        mActivityRule.runOnUiThread(() -> view.setTooltipText(tooltipText));
    }

    private boolean hasTooltip(View view) {
        final View tooltipView = view.getTooltipView();
        return tooltipView != null && tooltipView.getParent() != null;
    }


    private void addView(ViewGroup parent, View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> parent.addView(view));
        mInstrumentation.waitForIdleSync();
    }

    private void removeView(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> ((ViewGroup) (view.getParent())).removeView(view));
        mInstrumentation.waitForIdleSync();
    }

    private void setVisibility(View view, int visibility) throws Throwable {
        mActivityRule.runOnUiThread(() -> view.setVisibility(visibility));
    }

    private void setClickable(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> view.setClickable(true));
    }

    private void setLongClickable(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> view.setLongClickable(true));
    }

    private void setContextClickable(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> view.setContextClickable(true));
    }

    private void callPerformLongClick(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> view.performLongClick(0, 0));
    }

    private void requestLowProfileSystemUi() throws Throwable {
        final int flag = View.SYSTEM_UI_FLAG_LOW_PROFILE;
        mActivityRule.runOnUiThread(() -> mTooltipView.setSystemUiVisibility(flag));
        PollingCheck.waitFor(TIMEOUT_DELTA,
                () -> (mTooltipView.getWindowSystemUiVisibility() & flag) == flag);
    }

    private void injectKeyPress(View target, int keyCode, int duration) throws Throwable {
        if (target != null) {
            mActivityRule.runOnUiThread(() -> {
                target.setFocusableInTouchMode(true);
                target.requestFocus();
            });
            mInstrumentation.waitForIdleSync();
            assertTrue(target.isFocused());
        }
        mInstrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        waitOut(duration);
        mInstrumentation.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private void injectArbitraryShortKeyPress() throws Throwable {
        injectKeyPress(null, KeyEvent.KEYCODE_0, 0);
    }

    private void injectLongKeyPress(View target, int keyCode) throws Throwable {
        injectKeyPress(target, keyCode, ViewConfiguration.getLongPressTimeout());
    }

    private void injectLongEnter(View target) throws Throwable {
        injectLongKeyPress(target, KeyEvent.KEYCODE_ENTER);
    }

    private void injectShortClick(View target) {
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, target);
    }

    private void injectLongClick(View target) {
        CtsTouchUtils.emulateLongPressOnView(mInstrumentation, target,
                target.getWidth() / 2, target.getHeight() / 2);
    }

    private void injectMotionEvent(MotionEvent event) {
        mInstrumentation.sendPointerSync(event);
    }

    private void injectHoverMove(View target, int offsetX, int offsetY) {
        injectMotionEvent(obtainMouseEvent(
                target, MotionEvent.ACTION_HOVER_MOVE, offsetX,  offsetY));
    }

    private void injectHoverMove(View target) {
        injectHoverMove(target, 0, 0);
    }

    private void injectLongHoverMove(View target) {
        injectHoverMove(target);
        waitOut(ViewConfiguration.getHoverTooltipShowTimeout());
    }

    private static MotionEvent obtainMouseEvent(View target, int action, int offsetX, int offsetY) {
        final long eventTime = SystemClock.uptimeMillis();
        final int[] xy = new int[2];
        target.getLocationOnScreen(xy);
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime, action,
                xy[0] + target.getWidth() / 2 + offsetX, xy[1] + target.getHeight() / 2 + offsetY,
                0);
        event.setSource(InputDevice.SOURCE_MOUSE);
        return event;
    }

    @Test
    public void testGetSetTooltip() throws Throwable {
        // No tooltip set in resource
        assertEquals(null, mNoTooltipView.getTooltipText());

        // Set the tooltip, read it back
        final String tooltipText1 = "new tooltip";
        setTooltipText(mNoTooltipView, tooltipText1);
        assertEquals(tooltipText1, mNoTooltipView.getTooltipText());

        // Clear the tooltip.
        setTooltipText(mNoTooltipView, null);
        assertEquals(null, mNoTooltipView.getTooltipText());

        // Check the tooltip set in resource
        assertEquals("tooltip text", mTooltipView.getTooltipText());

        // Clear the tooltip set in resource
        setTooltipText(mTooltipView, null);
        assertEquals(null, mTooltipView.getTooltipText());

        // Set the tooltip again, read it back
        final String tooltipText2 = "new tooltip 2";
        setTooltipText(mTooltipView, tooltipText2);
        assertEquals(tooltipText2, mTooltipView.getTooltipText());
    }

    @Test
    public void testNoTooltipWhenNotSet() throws Throwable {
        callPerformLongClick(mNoTooltipView);
        assertFalse(hasTooltip(mNoTooltipView));

        injectLongClick(mNoTooltipView);
        assertFalse(hasTooltip(mNoTooltipView));

        injectLongEnter(mNoTooltipView);
        assertFalse(hasTooltip(mNoTooltipView));

        injectLongHoverMove(mNoTooltipView);
        assertFalse(hasTooltip(mNoTooltipView));
    }

    @Test
    public void testNoTooltipOnDisabledView() throws Throwable {
        mActivityRule.runOnUiThread(() -> mTooltipView.setEnabled(false));

        injectLongClick(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));

        injectLongEnter(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));

        injectLongHoverMove(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testUpdateOpenTooltip() throws Throwable {
        callPerformLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        setTooltipText(mTooltipView, "updated tooltip");
        assertTrue(hasTooltip(mTooltipView));

        setTooltipText(mTooltipView, null);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testTooltipHidesOnActivityFocusChange() throws Throwable {
        callPerformLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        CtsActivity activity = mCtsActivityRule.launchActivity(null);
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !mActivity.hasWindowFocus());
        assertFalse(hasTooltip(mTooltipView));
        activity.finish();
    }

    @Test
    public void testTooltipHidesOnWindowFocusChange() throws Throwable {
        callPerformLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Show a context menu on another widget.
        mActivity.registerForContextMenu(mNoTooltipView);
        mActivityRule.runOnUiThread(() -> mNoTooltipView.showContextMenu(0, 0));

        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !mTooltipView.hasWindowFocus());
        mInstrumentation.waitForIdleSync();
        assertFalse(hasTooltip(mTooltipView));
    }

    // Tests for tooltips triggered by long click.

    @Test
    public void testShortClickDoesNotShowTooltip() throws Throwable {
        injectShortClick(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testPerformLongClickShowsTooltipImmediately() throws Throwable {
        callPerformLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipBlockedByLongClickListener() throws Throwable {
        mTooltipView.setOnLongClickListener(v -> true);
        injectLongClick(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipBlockedByContextMenu() throws Throwable {
        mActivity.registerForContextMenu(mTooltipView);
        injectLongClick(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipOnNonClickableView() throws Throwable {
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipOnClickableView() throws Throwable {
        setClickable(mTooltipView);
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipOnLongClickableView() throws Throwable {
        setLongClickable(mTooltipView);
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipOnContextClickableView() throws Throwable {
        setContextClickable(mTooltipView);
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipStaysOnMouseMove() throws Throwable {
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Tooltip stays while the mouse moves over the widget.
        injectHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Long-click-triggered tooltip stays while the mouse to another widget.
        injectHoverMove(mNoTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipHidesAfterUp() throws Throwable {
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Long-click-triggered tooltip hides after ACTION_UP (with a delay).
        waitOut(ViewConfiguration.getLongPressTooltipHideTimeout());
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipHidesOnClick() throws Throwable {
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectShortClick(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipHidesOnClickElsewhere() throws Throwable {
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectShortClick(mNoTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongClickTooltipHidesOnKey() throws Throwable {
        injectLongClick(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectArbitraryShortKeyPress();
        assertFalse(hasTooltip(mTooltipView));
    }

    // Tests for tooltips triggered by long key press.

    @Test
    public void testShortKeyPressDoesNotShowTooltip() throws Throwable {
        injectKeyPress(null, KeyEvent.KEYCODE_ENTER, 0);
        assertFalse(hasTooltip(mTooltipView));

        injectKeyPress(mTooltipView, KeyEvent.KEYCODE_ENTER, 0);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongArbitraryKeyPressDoesNotShowTooltip() throws Throwable {
        injectLongKeyPress(mTooltipView, KeyEvent.KEYCODE_0);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressWithoutFocusDoesNotShowTooltip() throws Throwable {
        injectLongEnter(null);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressOnAnotherViewDoesNotShowTooltip() throws Throwable {
        injectLongEnter(mNoTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipOnNonClickableView() throws Throwable {
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipOnClickableView() throws Throwable {
        setClickable(mTooltipView);
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipOnLongClickableView() throws Throwable {
        setLongClickable(mTooltipView);
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipOnContextClickableView() throws Throwable {
        setContextClickable(mTooltipView);
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipStaysOnMouseMove() throws Throwable {
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Tooltip stays while the mouse moves over the widget.
        injectHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Long-keypress-triggered tooltip stays while the mouse to another widget.
        injectHoverMove(mNoTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipHidesAfterUp() throws Throwable {
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Long-keypress-triggered tooltip hides after ACTION_UP (with a delay).
        waitOut(ViewConfiguration.getLongPressTooltipHideTimeout());
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipHidesOnClick() throws Throwable {
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectShortClick(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipHidesOnClickElsewhere() throws Throwable {
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectShortClick(mNoTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testLongKeyPressTooltipHidesOnKey() throws Throwable {
        injectLongEnter(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectArbitraryShortKeyPress();
        assertFalse(hasTooltip(mTooltipView));
    }

    // Tests for tooltips triggered by mouse hover.

    @Test
    public void testMouseClickDoesNotShowTooltip() throws Throwable {
        injectMotionEvent(obtainMouseEvent(mTooltipView, MotionEvent.ACTION_DOWN, 0, 0));
        injectMotionEvent(obtainMouseEvent(mTooltipView, MotionEvent.ACTION_BUTTON_PRESS, 0, 0));
        injectMotionEvent(obtainMouseEvent(mTooltipView, MotionEvent.ACTION_BUTTON_RELEASE, 0, 0));
        injectMotionEvent(obtainMouseEvent(mTooltipView, MotionEvent.ACTION_UP, 0, 0));
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverDoesNotShowTooltipImmediately() throws Throwable {
        injectHoverMove(mTooltipView, 0, 0);
        assertFalse(hasTooltip(mTooltipView));

        injectHoverMove(mTooltipView, 1, 1);
        assertFalse(hasTooltip(mTooltipView));

        injectHoverMove(mTooltipView, 2, 2);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverExitCancelsPendingTooltip() throws Throwable {
        injectHoverMove(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));

        injectLongHoverMove(mNoTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipOnClickableView() throws Throwable {
        setClickable(mTooltipView);
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipOnLongClickableView() throws Throwable {
        setLongClickable(mTooltipView);
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipOnContextClickableView() throws Throwable {
        setContextClickable(mTooltipView);
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipStaysOnMouseMove() throws Throwable {
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Tooltip stays while the mouse moves over the widget.
        injectHoverMove(mTooltipView, 1, 1);
        assertTrue(hasTooltip(mTooltipView));

        injectHoverMove(mTooltipView, 2, 2);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipHidesOnExit() throws Throwable {
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        // Tooltip hides once the mouse moves out of the widget.
        injectHoverMove(mNoTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipHidesOnClick() throws Throwable {
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectShortClick(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipHidesOnClickOnElsewhere() throws Throwable {
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectShortClick(mNoTooltipView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipHidesOnKey() throws Throwable {
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        injectArbitraryShortKeyPress();
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipHidesOnTimeout() throws Throwable {
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        waitOut(ViewConfiguration.getHoverTooltipHideTimeout());
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipHidesOnShortTimeout() throws Throwable {
        requestLowProfileSystemUi();

        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));

        waitOut(ViewConfiguration.getHoverTooltipHideShortTimeout());
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipWithHoverListener() throws Throwable {
        mTooltipView.setOnHoverListener((v, event) -> true);
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipUnsetWhileHovering() throws Throwable {
        injectHoverMove(mTooltipView);
        setTooltipText(mTooltipView, null);
        waitOut(ViewConfiguration.getHoverTooltipShowTimeout());
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipDisableWhileHovering() throws Throwable {
        injectHoverMove(mTooltipView);
        mActivityRule.runOnUiThread(() -> mTooltipView.setEnabled(false));
        waitOut(ViewConfiguration.getHoverTooltipShowTimeout());
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipFromParent() throws Throwable {
        // Hover listeners should not interfere with tooltip dispatch.
        mNoTooltipView.setOnHoverListener((v, event) -> true);
        mTooltipView.setOnHoverListener((v, event) -> true);

        setTooltipText(mTopmostView, "tooltip");

        // Hover over a child with a tooltip works normally.
        injectLongHoverMove(mTooltipView);
        assertFalse(hasTooltip(mTopmostView));
        assertTrue(hasTooltip(mTooltipView));
        injectShortClick(mTopmostView);
        assertFalse(hasTooltip(mTooltipView));

        // Hover over a child with no tooltip triggers a tooltip on its parent.
        injectLongHoverMove(mNoTooltipView2);
        assertFalse(hasTooltip(mNoTooltipView2));
        assertTrue(hasTooltip(mTopmostView));
        injectShortClick(mTopmostView);
        assertFalse(hasTooltip(mTopmostView));

        // Same but the child is and empty view group.
        injectLongHoverMove(mEmptyGroup);
        assertFalse(hasTooltip(mEmptyGroup));
        assertTrue(hasTooltip(mTopmostView));
        injectShortClick(mTopmostView);
        assertFalse(hasTooltip(mTopmostView));

        // Hover over a grandchild with no tooltip triggers a tooltip on its grandparent.
        injectLongHoverMove(mNoTooltipView);
        assertFalse(hasTooltip(mNoTooltipView));
        assertTrue(hasTooltip(mTopmostView));
        // Move to another child one level up, the tooltip stays.
        injectHoverMove(mNoTooltipView2);
        assertTrue(hasTooltip(mTopmostView));
        injectShortClick(mTopmostView);
        assertFalse(hasTooltip(mTopmostView));

        // Set a tooltip on the intermediate parent, now it is showing tooltips.
        setTooltipText(mGroupView, "tooltip");
        injectLongHoverMove(mNoTooltipView);
        assertFalse(hasTooltip(mNoTooltipView));
        assertFalse(hasTooltip(mTopmostView));
        assertTrue(hasTooltip(mGroupView));

        // Move out of this group, the tooltip is now back on the grandparent.
        injectLongHoverMove(mNoTooltipView2);
        assertFalse(hasTooltip(mGroupView));
        assertTrue(hasTooltip(mTopmostView));
        injectShortClick(mTopmostView);
        assertFalse(hasTooltip(mTopmostView));
    }

    @Test
    public void testMouseHoverTooltipRemoveWhileWaiting() throws Throwable {
        // Remove the view while hovering.
        injectHoverMove(mTooltipView);
        removeView(mTooltipView);
        waitOut(ViewConfiguration.getHoverTooltipShowTimeout());
        assertFalse(hasTooltip(mTooltipView));
        addView(mGroupView, mTooltipView);

        // Remove and re-add the view while hovering.
        injectHoverMove(mTooltipView);
        removeView(mTooltipView);
        addView(mGroupView, mTooltipView);
        waitOut(ViewConfiguration.getHoverTooltipShowTimeout());
        assertFalse(hasTooltip(mTooltipView));

        // Remove the view's parent while hovering.
        injectHoverMove(mTooltipView);
        removeView(mGroupView);
        waitOut(ViewConfiguration.getHoverTooltipShowTimeout());
        assertFalse(hasTooltip(mTooltipView));
        addView(mTopmostView, mGroupView);

        // Remove and re-add view's parent while hovering.
        injectHoverMove(mTooltipView);
        removeView(mGroupView);
        addView(mTopmostView, mGroupView);
        waitOut(ViewConfiguration.getHoverTooltipShowTimeout());
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverTooltipRemoveWhileShowing() throws Throwable {
        // Remove the view while showing the tooltip.
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
        removeView(mTooltipView);
        assertFalse(hasTooltip(mTooltipView));
        addView(mGroupView, mTooltipView);
        assertFalse(hasTooltip(mTooltipView));

        // Remove the view's parent while showing the tooltip.
        injectLongHoverMove(mTooltipView);
        assertTrue(hasTooltip(mTooltipView));
        removeView(mGroupView);
        assertFalse(hasTooltip(mTooltipView));
        addView(mTopmostView, mGroupView);
        assertFalse(hasTooltip(mTooltipView));
    }

    @Test
    public void testMouseHoverOverlap() throws Throwable {
        final View parent = mActivity.findViewById(R.id.overlap_group);
        final View child1 = mActivity.findViewById(R.id.overlap1);
        final View child2 = mActivity.findViewById(R.id.overlap2);
        final View child3 = mActivity.findViewById(R.id.overlap3);

        injectLongHoverMove(parent);
        assertTrue(hasTooltip(child3));

        setVisibility(child3, View.GONE);
        injectLongHoverMove(parent);
        assertTrue(hasTooltip(child2));

        setTooltipText(child2, null);
        injectLongHoverMove(parent);
        assertTrue(hasTooltip(child1));

        setVisibility(child1, View.INVISIBLE);
        injectLongHoverMove(parent);
        assertTrue(hasTooltip(parent));
    }

    @Test
    public void testTooltipInPopup() throws Throwable {
        TextView popupContent = new TextView(mActivity);

        mActivityRule.runOnUiThread(() -> {
            popupContent.setText("Popup view");
            popupContent.setTooltipText("Tooltip");

            PopupWindow popup = new PopupWindow(popupContent,
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            popup.showAtLocation(mGroupView, Gravity.CENTER, 0, 0);
        });
        mInstrumentation.waitForIdleSync();

        injectLongClick(popupContent);
        assertTrue(hasTooltip(popupContent));
    }
}
