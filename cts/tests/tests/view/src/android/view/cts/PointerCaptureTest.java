/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.compatibility.common.util.CtsMouseUtil.PositionMatcher;
import static com.android.compatibility.common.util.CtsMouseUtil.clearHoverListener;
import static com.android.compatibility.common.util.CtsMouseUtil.installHoverListener;
import static com.android.compatibility.common.util.CtsMouseUtil.obtainMouseEvent;
import static com.android.compatibility.common.util.CtsMouseUtil.verifyEnterMove;
import static com.android.compatibility.common.util.CtsMouseUtil.verifyEnterMoveExit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.compatibility.common.util.CtsMouseUtil.ActionMatcher;
import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

/**
 * Test {@link View}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PointerCaptureTest {
    private static final long TIMEOUT_DELTA = 10000;

    private Instrumentation mInstrumentation;
    private PointerCaptureCtsActivity mActivity;

    private PointerCaptureGroup mOuter;
    private PointerCaptureGroup mInner;
    private PointerCaptureView mTarget;
    private PointerCaptureGroup mTarget2;

    @Rule
    public ActivityTestRule<PointerCaptureCtsActivity> mActivityRule =
            new ActivityTestRule<>(PointerCaptureCtsActivity.class);

    @Rule
    public ActivityTestRule<CtsActivity> mCtsActivityRule =
            new ActivityTestRule<>(CtsActivity.class, false, false);

    private void requestFocusSync(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.requestFocus();
        });
        PollingCheck.waitFor(TIMEOUT_DELTA, view::hasFocus);
    }

    private void requestCaptureSync(View view) throws Throwable {
        mActivityRule.runOnUiThread(view::requestPointerCapture);
        PollingCheck.waitFor(TIMEOUT_DELTA,
                () -> view.hasPointerCapture() && mActivity.hasPointerCapture());
    }

    private void requestCaptureSync() throws Throwable {
        requestCaptureSync(mOuter);
    }

    private void releaseCaptureSync(View view) throws Throwable {
        mActivityRule.runOnUiThread(view::releasePointerCapture);
        PollingCheck.waitFor(TIMEOUT_DELTA,
                () -> !view.hasPointerCapture() && !mActivity.hasPointerCapture());
    }

    private void releaseCaptureSync() throws Throwable {
        releaseCaptureSync(mOuter);
    }

    public static View.OnCapturedPointerListener installCapturedPointerListener(View view) {
        final View.OnCapturedPointerListener mockListener =
                mock(View.OnCapturedPointerListener.class);
        view.setOnCapturedPointerListener((v, event) -> {
            // Clone the event to work around event instance reuse in the framework.
            mockListener.onCapturedPointer(v, MotionEvent.obtain(event));
            return true;
        });
        return mockListener;
    }

    public static void clearCapturedPointerListener(View view) {
        view.setOnCapturedPointerListener(null);
    }

    private void injectMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_CLASS_POINTER) != 0) {
            // Regular mouse event.
            mInstrumentation.sendPointerSync(event);
        } else {
            // Relative mouse event belongs to SOURCE_CLASS_TRACKBALL.
            mInstrumentation.sendTrackballEventSync(event);
        }
    }

    private void injectRelativeMouseEvent(int action, int x, int y) {
        injectMotionEvent(obtainRelativeMouseEvent(action, x, y));
    }

    private static MotionEvent obtainRelativeMouseEvent(int action, int x, int y) {
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(eventTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_MOUSE_RELATIVE);
        return event;
    }

    private static void verifyRelativeMouseEvent(InOrder inOrder,
                View.OnCapturedPointerListener listener, View view, int action, int x, int y) {
        inOrder.verify(listener, times(1)).onCapturedPointer(
                eq(view), argThat(new PositionMatcher(action, x, y)));
    }

    private void verifyHoverDispatch() {
        View.OnHoverListener listenerOuter = installHoverListener(mOuter);
        View.OnHoverListener listenerInner = installHoverListener(mInner);
        View.OnHoverListener listenerTarget = installHoverListener(mTarget);
        View.OnHoverListener listenerTarget2 = installHoverListener(mTarget2);

        injectMotionEvent(obtainMouseEvent(MotionEvent.ACTION_HOVER_MOVE, mInner, 0, 0));
        injectMotionEvent(obtainMouseEvent(MotionEvent.ACTION_HOVER_MOVE, mTarget, 0, 0));
        injectMotionEvent(obtainMouseEvent(MotionEvent.ACTION_HOVER_MOVE, mTarget2, 0, 0));

        clearHoverListener(mOuter);
        clearHoverListener(mInner);
        clearHoverListener(mTarget);
        clearHoverListener(mTarget2);

        verifyEnterMoveExit(listenerInner, mInner, 2);
        verifyEnterMoveExit(listenerTarget, mTarget, 2);
        verifyEnterMove(listenerTarget2, mTarget2, 1);

        verifyNoMoreInteractions(listenerOuter);
        verifyNoMoreInteractions(listenerInner);
        verifyNoMoreInteractions(listenerTarget);
        verifyNoMoreInteractions(listenerTarget2);
    }

    private void assertPointerCapture(boolean enabled) {
        assertEquals(enabled, mOuter.hasPointerCapture());
        assertEquals(enabled, mInner.hasPointerCapture());
        assertEquals(enabled, mTarget.hasPointerCapture());
        assertEquals(enabled, mTarget2.hasPointerCapture());
        assertEquals(enabled, mActivity.hasPointerCapture());
    }

    private void resetViews() {
        mOuter.reset();
        mInner.reset();
        mTarget.reset();
        mTarget2.reset();

        mOuter.setOnCapturedPointerListener(null);
        mInner.setOnCapturedPointerListener(null);
        mTarget.setOnCapturedPointerListener(null);
        mTarget2.setOnCapturedPointerListener(null);
    }

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();

        mOuter = (PointerCaptureGroup) mActivity.findViewById(R.id.outer);
        mInner = (PointerCaptureGroup) mActivity.findViewById(R.id.inner);
        mTarget = (PointerCaptureView) mActivity.findViewById(R.id.target);
        mTarget2 = (PointerCaptureGroup) mActivity.findViewById(R.id.target2);

        PollingCheck.waitFor(TIMEOUT_DELTA, mActivity::hasWindowFocus);
    }

    @Test
    public void testRequestAndReleaseWorkOnAnyView() throws Throwable {
        requestCaptureSync(mOuter);
        assertPointerCapture(true);

        releaseCaptureSync(mOuter);
        assertPointerCapture(false);

        requestCaptureSync(mInner);
        assertPointerCapture(true);

        releaseCaptureSync(mTarget);
        assertPointerCapture(false);
    }

    @Test
    public void testWindowFocusChangeEndsCapture() throws Throwable {
        requestCaptureSync();
        assertPointerCapture(true);

        // Show a context menu on a widget.
        mActivity.registerForContextMenu(mTarget);
        // TODO(kaznacheev) replace the below line with a call to showContextMenu once b/65487689
        // is fixed. Meanwhile, emulate a long press which takes long enough time to avoid the race
        // condition.
        CtsTouchUtils.emulateLongPressOnView(mInstrumentation, mTarget, 0, 0);
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !mOuter.hasWindowFocus());
        assertPointerCapture(false);

        mInstrumentation.sendCharacterSync(KeyEvent.KEYCODE_BACK);
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> mOuter.hasWindowFocus());
        assertFalse(mTarget.hasPointerCapture());
        assertFalse(mActivity.hasPointerCapture());
    }

    @Test
    public void testActivityFocusChangeEndsCapture() throws Throwable {
        requestCaptureSync();
        assertPointerCapture(true);

        // Launch another activity.
        CtsActivity activity = mCtsActivityRule.launchActivity(null);
        PollingCheck.waitFor(TIMEOUT_DELTA, () -> !mActivity.hasWindowFocus());
        assertPointerCapture(false);

        activity.finish();
    }

    @Test
    public void testEventDispatch() throws Throwable {
        verifyHoverDispatch();

        View.OnCapturedPointerListener listenerInner = installCapturedPointerListener(mInner);
        View.OnCapturedPointerListener listenerTarget = installCapturedPointerListener(mTarget);
        View.OnCapturedPointerListener listenerTarget2 = installCapturedPointerListener(mTarget2);
        View.OnHoverListener hoverListenerTarget2 = installHoverListener(mTarget2);

        requestCaptureSync();

        requestFocusSync(mInner);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 1, 2);
        injectRelativeMouseEvent(MotionEvent.ACTION_DOWN, 1, 2);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 3, 4);
        injectRelativeMouseEvent(MotionEvent.ACTION_UP, 3, 4);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 1, 2);

        requestFocusSync(mTarget);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 5, 6);

        requestFocusSync(mTarget2);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 7, 8);

        requestFocusSync(mInner);
        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 9, 10);

        releaseCaptureSync();

        injectRelativeMouseEvent(MotionEvent.ACTION_MOVE, 11, 12);  // Should be ignored.

        clearCapturedPointerListener(mInner);
        clearCapturedPointerListener(mTarget);
        clearCapturedPointerListener(mTarget2);
        clearHoverListener(mTarget2);

        InOrder inOrder = inOrder(
                listenerInner, listenerTarget, listenerTarget2, hoverListenerTarget2);

        // mTarget2 is left hovered after the call to verifyHoverDispatch.
        inOrder.verify(hoverListenerTarget2, times(1)).onHover(
                eq(mTarget2), argThat(new ActionMatcher(MotionEvent.ACTION_HOVER_EXIT)));

        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 1, 2);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_DOWN, 1, 2);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 3, 4);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_UP, 3, 4);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 1, 2);

        verifyRelativeMouseEvent(inOrder, listenerTarget, mTarget, MotionEvent.ACTION_MOVE, 5, 6);
        verifyRelativeMouseEvent(inOrder, listenerTarget2, mTarget2, MotionEvent.ACTION_MOVE, 7, 8);
        verifyRelativeMouseEvent(inOrder, listenerInner, mInner, MotionEvent.ACTION_MOVE, 9, 10);

        inOrder.verifyNoMoreInteractions();

        // Check the regular dispatch again.
        verifyHoverDispatch();
    }

    @Test
    public void testPointerCaptureChangeDispatch() throws Throwable {
        // Normal dispatch should reach every view in the hierarchy.
        requestCaptureSync();

        assertTrue(mOuter.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mOuter.hasCalledOnPointerCaptureChange());
        assertTrue(mInner.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mInner.hasCalledOnPointerCaptureChange());
        assertTrue(mTarget.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mTarget.hasCalledOnPointerCaptureChange());
        assertTrue(mTarget2.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mTarget2.hasCalledOnPointerCaptureChange());

        resetViews();

        releaseCaptureSync();

        assertTrue(mOuter.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mOuter.hasCalledOnPointerCaptureChange());
        assertTrue(mInner.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mInner.hasCalledOnPointerCaptureChange());
        assertTrue(mTarget.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mTarget.hasCalledOnPointerCaptureChange());
        assertTrue(mTarget2.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mTarget2.hasCalledOnPointerCaptureChange());

        resetViews();

        // Manual dispatch should only reach the recipient and its descendants.
        mInner.dispatchPointerCaptureChanged(true);
        assertFalse(mOuter.hasCalledDispatchPointerCaptureChanged());
        assertFalse(mOuter.hasCalledOnPointerCaptureChange());
        assertTrue(mInner.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mInner.hasCalledOnPointerCaptureChange());
        assertTrue(mTarget.hasCalledDispatchPointerCaptureChanged());
        assertTrue(mTarget.hasCalledOnPointerCaptureChange());
        assertFalse(mTarget2.hasCalledDispatchPointerCaptureChanged());
        assertFalse(mTarget2.hasCalledOnPointerCaptureChange());
    }

    @Test
    public void testOnCapturedPointerEvent() throws Throwable {
        final MotionEvent event = obtainRelativeMouseEvent(MotionEvent.ACTION_MOVE, 1, 1);

        // No focus, no capture. Dispatch does reach descendants, no handlers called.
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mOuter.hasCalledOnCapturedPointerEvent());
        assertFalse(mInner.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mInner.hasCalledOnCapturedPointerEvent());
        assertFalse(mTarget.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mTarget.hasCalledOnCapturedPointerEvent());
        resetViews();

        requestCaptureSync();
        // Same with capture but no focus
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mOuter.hasCalledOnCapturedPointerEvent());
        assertFalse(mInner.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mInner.hasCalledOnCapturedPointerEvent());
        assertFalse(mTarget.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mTarget.hasCalledOnCapturedPointerEvent());
        resetViews();

        releaseCaptureSync();
        requestFocusSync(mOuter);

        // Same with focus but no capture.
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mOuter.hasCalledOnCapturedPointerEvent());
        assertFalse(mInner.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mInner.hasCalledOnCapturedPointerEvent());
        assertFalse(mTarget.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mTarget.hasCalledOnCapturedPointerEvent());
        resetViews();

        requestCaptureSync();

        // Have both focus and capture, both dispatch and handler called on the focused view,
        // Nothing called for descendants.
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertTrue(mOuter.hasCalledOnCapturedPointerEvent());
        assertFalse(mInner.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mInner.hasCalledOnCapturedPointerEvent());
        assertFalse(mTarget.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mTarget.hasCalledOnCapturedPointerEvent());
        resetViews();

        // Listener returning false does not block the callback.
        mOuter.setOnCapturedPointerListener((v, e) -> false);
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertTrue(mOuter.hasCalledOnCapturedPointerEvent());
        resetViews();

        // Listener returning true blocks the callback.
        mOuter.setOnCapturedPointerListener((v, e) -> true);
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mOuter.hasCalledOnCapturedPointerEvent());
        resetViews();

        requestFocusSync(mTarget);

        // Dispatch reaches the focused view and all intermediate parents but not siblings.
        // Handler only called on the focused view.
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mOuter.hasCalledOnCapturedPointerEvent());
        assertTrue(mInner.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mInner.hasCalledOnCapturedPointerEvent());
        assertTrue(mTarget.hasCalledDispatchCapturedPointerEvent());
        assertTrue(mTarget.hasCalledOnCapturedPointerEvent());
        assertFalse(mTarget2.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mTarget2.hasCalledOnCapturedPointerEvent());
        resetViews();

        // Unfocused parent with a listener does not interfere with dispatch.
        mInner.setOnCapturedPointerListener((v, e) -> true);
        mOuter.dispatchCapturedPointerEvent(event);
        assertTrue(mOuter.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mOuter.hasCalledOnCapturedPointerEvent());
        assertTrue(mInner.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mInner.hasCalledOnCapturedPointerEvent());
        assertTrue(mTarget.hasCalledDispatchCapturedPointerEvent());
        assertTrue(mTarget.hasCalledOnCapturedPointerEvent());
        assertFalse(mTarget2.hasCalledDispatchCapturedPointerEvent());
        assertFalse(mTarget2.hasCalledOnCapturedPointerEvent());
        resetViews();
    }
}
