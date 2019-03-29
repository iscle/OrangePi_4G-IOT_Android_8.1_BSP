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

import static com.android.compatibility.common.util.CtsMouseUtil.clearHoverListener;
import static com.android.compatibility.common.util.CtsMouseUtil.installHoverListener;
import static com.android.compatibility.common.util.CtsMouseUtil.obtainMouseEvent;
import static com.android.compatibility.common.util.CtsMouseUtil.verifyEnterMove;
import static com.android.compatibility.common.util.CtsMouseUtil.verifyEnterMoveExit;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.android.compatibility.common.util.CtsMouseUtil.ActionMatcher;
import com.android.compatibility.common.util.CtsMouseUtil.PositionMatcher;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

/**
 * Test hover events.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HoverTest {
    private static final String LOG_TAG = "HoverTest";

    private Instrumentation mInstrumentation;
    private Activity mActivity;

    private View mOuter;
    private View mMiddle1;
    private View mMiddle2;
    private View mInner11;
    private View mInner12;
    private View mInner21;
    private View mInner22;

    private View mOverlapping;
    private View mLayer1;
    private View mLayer2;
    private View mLayer3;
    private View mLayer4Left;
    private View mLayer4Right;

    @Rule
    public ActivityTestRule<HoverCtsActivity> mActivityRule =
            new ActivityTestRule<>(HoverCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();

        mOuter = mActivity.findViewById(R.id.outer);
        mMiddle1 = mActivity.findViewById(R.id.middle1);
        mMiddle2 = mActivity.findViewById(R.id.middle2);
        mInner11 = mActivity.findViewById(R.id.inner11);
        mInner12 = mActivity.findViewById(R.id.inner12);
        mInner21 = mActivity.findViewById(R.id.inner21);
        mInner22 = mActivity.findViewById(R.id.inner22);

        mOverlapping = mActivity.findViewById(R.id.overlapping);
        mLayer1 = mActivity.findViewById(R.id.layer1);
        mLayer2 = mActivity.findViewById(R.id.layer2);
        mLayer3 = mActivity.findViewById(R.id.layer3);
        mLayer4Left = mActivity.findViewById(R.id.layer4_left);
        mLayer4Right = mActivity.findViewById(R.id.layer4_right);
    }

    private void injectHoverMove(View view) {
        injectHoverMove(view, 0, 0);
    }

    private void injectHoverMove(View view, int offsetX, int offsetY) {
        mActivity.getWindow().injectInputEvent(
                obtainMouseEvent(MotionEvent.ACTION_HOVER_MOVE, view, offsetX, offsetY));
        mInstrumentation.waitForIdleSync();
    }

    private void remove(View view) throws Throwable {
        mActivityRule.runOnUiThread(() -> ((ViewGroup)view.getParent()).removeView(view));
    }

    @Test
    public void testHoverMove() throws Throwable {
        View.OnHoverListener listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);

        clearHoverListener(mInner11);

        verifyEnterMove(listener, mInner11, 1);
    }

    @Test
    public void testHoverMoveMultiple() throws Throwable {
        View.OnHoverListener listener = installHoverListener(mInner11);

        injectHoverMove(mInner11, 1, 2);
        injectHoverMove(mInner11, 3, 4);
        injectHoverMove(mInner11, 5, 6);

        clearHoverListener(mInner11);

        InOrder inOrder = inOrder(listener);

        inOrder.verify(listener, times(1)).onHover(eq(mInner11),
                argThat(new ActionMatcher(MotionEvent.ACTION_HOVER_ENTER)));
        inOrder.verify(listener, times(1)).onHover(eq(mInner11),
                argThat(new PositionMatcher(MotionEvent.ACTION_HOVER_MOVE, 1, 2)));
        inOrder.verify(listener, times(1)).onHover(eq(mInner11),
                argThat(new PositionMatcher(MotionEvent.ACTION_HOVER_MOVE, 3, 4)));
        inOrder.verify(listener, times(1)).onHover(eq(mInner11),
                argThat(new PositionMatcher(MotionEvent.ACTION_HOVER_MOVE, 5, 6)));

        verifyNoMoreInteractions(listener);
    }

    @Test
    public void testHoverMoveAndExit() throws Throwable {
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);
        View.OnHoverListener inner12Listener = installHoverListener(mInner12);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);

        clearHoverListener(mInner11);
        clearHoverListener(mInner12);

        verifyEnterMoveExit(inner11Listener, mInner11, 2);
        verifyEnterMove(inner12Listener, mInner12, 1);
    }

    @Test
    public void testRemoveBeforeExit() throws Throwable {
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);
        remove(mInner11);

        clearHoverListener(mMiddle1);
        clearHoverListener(mInner11);

        verifyNoMoreInteractions(middle1Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 1);
    }

    @Test
    public void testRemoveParentBeforeExit() throws Throwable {
        View.OnHoverListener outerListener = installHoverListener(mOuter);
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);
        remove(mMiddle1);

        clearHoverListener(mOuter);
        clearHoverListener(mMiddle1);
        clearHoverListener(mInner11);

        verifyNoMoreInteractions(outerListener);
        verifyNoMoreInteractions(middle1Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 1);
    }

    @Test
    public void testRemoveAfterExit() throws Throwable {
        View.OnHoverListener listener = installHoverListener(mInner11);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);
        remove(mInner11);

        clearHoverListener(mInner11);

        verifyEnterMoveExit(listener, mInner11, 2);
    }

    @Test
    public void testNoParentInteraction() throws Throwable {
        View.OnHoverListener outerListener = installHoverListener(mOuter);
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener middle2Listener = installHoverListener(mMiddle2);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11);
        View.OnHoverListener inner12Listener = installHoverListener(mInner12);
        View.OnHoverListener inner21Listener = installHoverListener(mInner21);
        View.OnHoverListener inner22Listener = installHoverListener(mInner22);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);
        injectHoverMove(mInner21);
        injectHoverMove(mInner22);

        clearHoverListener(mOuter);
        clearHoverListener(mMiddle1);
        clearHoverListener(mMiddle2);
        clearHoverListener(mInner11);
        clearHoverListener(mInner21);

        verifyNoMoreInteractions(outerListener);
        verifyNoMoreInteractions(middle1Listener);
        verifyNoMoreInteractions(middle2Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 2);
        verifyEnterMoveExit(inner12Listener, mInner12, 2);
        verifyEnterMoveExit(inner21Listener, mInner21, 2);
        verifyEnterMove(inner22Listener, mInner22, 1);
    }

    @Test
    public void testParentInteraction() throws Throwable {
        View.OnHoverListener outerListener = installHoverListener(mOuter);
        View.OnHoverListener middle1Listener = installHoverListener(mMiddle1);
        View.OnHoverListener middle2Listener = installHoverListener(mMiddle2);
        View.OnHoverListener inner11Listener = installHoverListener(mInner11, false);
        View.OnHoverListener inner12Listener = installHoverListener(mInner12, false);
        View.OnHoverListener inner21Listener = installHoverListener(mInner21);
        View.OnHoverListener inner22Listener = installHoverListener(mInner22);

        injectHoverMove(mInner11);
        injectHoverMove(mInner12);
        injectHoverMove(mInner21);
        injectHoverMove(mInner22);

        clearHoverListener(mOuter);
        clearHoverListener(mMiddle1);
        clearHoverListener(mMiddle2);
        clearHoverListener(mInner11);
        clearHoverListener(mInner12);
        clearHoverListener(mInner21);

        verifyNoMoreInteractions(outerListener);
        verifyEnterMoveExit(middle1Listener, mMiddle1, 3);
        verifyNoMoreInteractions(middle2Listener);
        verifyEnterMoveExit(inner11Listener, mInner11, 2);
        verifyEnterMoveExit(inner12Listener, mInner12, 2);
        verifyEnterMoveExit(inner21Listener, mInner21, 2);
        verifyEnterMove(inner22Listener, mInner22, 1);
    }

    @Test
    public void testOverlappingHoverTargets() throws Throwable {
        View.OnHoverListener overlapping = installHoverListener(mOverlapping);
        View.OnHoverListener listener1 = installHoverListener(mLayer1);
        View.OnHoverListener listener2 = installHoverListener(mLayer2);
        View.OnHoverListener listener3 = installHoverListener(mLayer3, false);
        View.OnHoverListener listener4_left = installHoverListener(mLayer4Left, false);
        View.OnHoverListener listener4_right = installHoverListener(mLayer4Right, false);

        injectHoverMove(mLayer4Left);
        injectHoverMove(mLayer4Left, 1, 1);
        injectHoverMove(mLayer4Right);
        injectHoverMove(mMiddle1);

        clearHoverListener(mLayer1);
        clearHoverListener(mLayer2);
        clearHoverListener(mLayer3);
        clearHoverListener(mLayer4Left);
        clearHoverListener(mLayer4Right);

        verifyNoMoreInteractions(overlapping);
        verifyNoMoreInteractions(listener1);
        verifyEnterMoveExit(listener2, mLayer2, 4);
        verifyEnterMoveExit(listener3, mLayer3, 4);
        verifyEnterMoveExit(listener4_left, mLayer4Left, 3);
        verifyEnterMoveExit(listener4_right, mLayer4Right, 2);
   }
}