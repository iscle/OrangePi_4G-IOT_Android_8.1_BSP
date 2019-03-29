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

package android.text.method.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.MovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ScrollingMovementMethod}. The class is an implementation of interface
 * {@link MovementMethod}. The typical usage of {@link MovementMethod} is tested in
 * {@link android.widget.cts.TextViewTest} and this test case is only focused on the
 * implementation of the methods.
 *
 * @see android.widget.cts.TextViewTest
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ScrollingMovementMethodTest {
    private static final int LITTLE_SPACE = 20;

    private static final String THREE_LINES_TEXT = "first line\nsecond line\nlast line";

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private TextView mTextView;
    private Spannable mSpannable;
    private int mScaledTouchSlop;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @UiThreadTest
    @Before
    public void setup() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mTextView = new TextViewNoIme(mActivity);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTextView.setText(THREE_LINES_TEXT, BufferType.EDITABLE);
        mSpannable = (Spannable) mTextView.getText();
        mScaledTouchSlop = ViewConfiguration.get(mActivity).getScaledTouchSlop();
    }

    @Test
    public void testConstructor() {
        new ScrollingMovementMethod();
    }

    @Test
    public void testGetInstance() {
        final MovementMethod method0 = ScrollingMovementMethod.getInstance();
        assertTrue(method0 instanceof ScrollingMovementMethod);

        final MovementMethod method1 = ScrollingMovementMethod.getInstance();
        assertSame(method0, method1);
    }

    @Test
    public void testOnTouchEventHorizontalMotion() throws Throwable {
        final ScrollingMovementMethod method = new ScrollingMovementMethod();
        runActionOnUiThread(() -> {
            mTextView.setText("hello world", BufferType.SPANNABLE);
            mTextView.setSingleLine();
            mSpannable = (Spannable) mTextView.getText();
            final int width = WidgetTestUtils.convertDipToPixels(mActivity, LITTLE_SPACE);
            mActivity.setContentView(mTextView,
                    new LayoutParams(width, LayoutParams.WRAP_CONTENT));
        });
        assertNotNull(mTextView.getLayout());

        final float rightMost = mTextView.getLayout().getLineRight(0) - mTextView.getWidth()
                + mTextView.getTotalPaddingLeft() + mTextView.getTotalPaddingRight();
        final int leftMost = mTextView.getScrollX();

        final long now = SystemClock.uptimeMillis();
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // press
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0));
            }
        }));

        final int tinyDist = -(mScaledTouchSlop - 1);
        int previousScrollX = mTextView.getScrollX();
        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move for short distance
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, tinyDist, 0, 0));
            }
        }));
        assertEquals(previousScrollX, mTextView.getScrollX());

        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // release
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, tinyDist, 0, 0));
            }
        }));

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // press
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0));
            }
        }));

        final int distFar = -mScaledTouchSlop;
        previousScrollX = mTextView.getScrollX();
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move for enough distance
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, distFar, 0, 0));
            }
        }));
        assertTrue(mTextView.getScrollX() > previousScrollX);
        assertTrue(mTextView.getScrollX() < rightMost);

        previousScrollX = mTextView.getScrollX();
        final int distTooFar = (int) (-rightMost * 10);
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move for long distance
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, distTooFar, 0, 0));
            }
        }));
        assertTrue(mTextView.getScrollX() > previousScrollX);
        assertEquals(rightMost, mTextView.getScrollX(), 1.0f);

        previousScrollX = mTextView.getScrollX();
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move back
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, 0, 0, 0));
            }
        }));
        assertTrue(mTextView.getScrollX() < previousScrollX);
        assertEquals(leftMost, mTextView.getScrollX());

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // release
                mResult = method.onTouchEvent(mTextView, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 0, 0, 0));
            }
        }));
    }

    @Test
    public void testOnTouchEventVerticalMotion() throws Throwable {
        final ScrollingMovementMethod method = new ScrollingMovementMethod();
        runActionOnUiThread(() -> {
            mTextView.setLines(1);
            mActivity.setContentView(mTextView,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        });
        assertNotNull(mTextView.getLayout());

        final float bottom = mTextView.getLayout().getHeight() - mTextView.getHeight()
                + mTextView.getTotalPaddingTop() + mTextView.getTotalPaddingBottom();
        final int top = mTextView.getScrollY();

        final long now = SystemClock.uptimeMillis();
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // press
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_DOWN, 0, 0, 0));
            }
        }));

        final int tinyDist = -(mScaledTouchSlop - 1);
        int previousScrollY = mTextView.getScrollY();
        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move for short distance
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_MOVE, 0, tinyDist, 0));
            }
        }));
        assertEquals(previousScrollY, mTextView.getScrollY());

        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // release
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_UP, 0, tinyDist, 0));
            }
        }));

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // press
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_DOWN, 0, 0, 0));
            }
        }));

        final int distFar = -mScaledTouchSlop;
        previousScrollY = mTextView.getScrollY();
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move for enough distance
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_MOVE, 0, distFar, 0));
            }
        }));
        assertTrue(mTextView.getScrollY() > previousScrollY);
        assertTrue(mTextView.getScrollX() < bottom);

        previousScrollY = mTextView.getScrollY();
        final int distTooFar = (int) (-bottom * 10);
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move for long distance
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_MOVE, 0, distTooFar, 0));
            }
        }));
        assertTrue(mTextView.getScrollY() > previousScrollY);
        assertEquals(bottom, mTextView.getScrollY(), 0f);

        previousScrollY = mTextView.getScrollY();
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // move back
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_MOVE, 0, 0, 0));
            }
        }));
        assertTrue(mTextView.getScrollY() < previousScrollY);
        assertEquals(top, mTextView.getScrollX());

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                // release
                mResult = method.onTouchEvent(mTextView, mSpannable, MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_UP, 0, 0, 0));
            }
        }));
    }

    @Test
    public void testOnTouchEventExceptional() throws Throwable {
        runActionOnUiThread(() -> {
            final int width = WidgetTestUtils.convertDipToPixels(mActivity, LITTLE_SPACE);
            mActivity.setContentView(mTextView,
                    new LayoutParams(width, LayoutParams.WRAP_CONTENT));
        });
        assertNotNull(mTextView.getLayout());

        runActionOnUiThread(() -> {
            try {
                new ScrollingMovementMethod().onTouchEvent(mTextView, mSpannable, null);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            long now = SystemClock.uptimeMillis();
            try {
                new ScrollingMovementMethod().onTouchEvent(mTextView, null,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                new ScrollingMovementMethod().onTouchEvent(null, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            new ScrollingMovementMethod().onTouchEvent(mTextView, mSpannable,
                    MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0));
            try {
                new ScrollingMovementMethod().onTouchEvent(null, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, -10000, 0, 0));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                new ScrollingMovementMethod().onTouchEvent(mTextView, null,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, -10000, 0, 0));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                new ScrollingMovementMethod().onTouchEvent(null, mSpannable,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, -10000, 0, 0));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                new ScrollingMovementMethod().onTouchEvent(mTextView, null,
                        MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, -10000, 0, 0));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }
        });
    }

    @Test
    public void testCanSelectArbitrarily() {
        assertFalse(new ScrollingMovementMethod().canSelectArbitrarily());
    }

    @Test
    public void testOnKeyDownVerticalMovement() throws Throwable {
        runActionOnUiThread(() -> mActivity.setContentView(mTextView));
        assertNotNull(mTextView.getLayout());

        verifyVisibleLineInTextView(0);
        final MyScrollingMovementMethod method = new MyScrollingMovementMethod();
        runActionOnUiThread(() -> method.onKeyDown(mTextView, null, KeyEvent.KEYCODE_DPAD_DOWN,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
        verifyVisibleLineInTextView(1);

        runActionOnUiThread(() -> method.onKeyDown(mTextView, null, KeyEvent.KEYCODE_DPAD_UP,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)));
        verifyVisibleLineInTextView(0);
    }

    @Test
    public void testOnKeyDownHorizontalMovement() throws Throwable {
        runActionOnUiThread(() -> {
            mTextView.setText("short");
            mTextView.setSingleLine();
            final int width = WidgetTestUtils.convertDipToPixels(mActivity, LITTLE_SPACE);
            mActivity.setContentView(mTextView,
                    new LayoutParams(width, LayoutParams.WRAP_CONTENT));
        });
        assertNotNull(mTextView.getLayout());

        final MyScrollingMovementMethod method = new MyScrollingMovementMethod();
        int previousScrollX = mTextView.getScrollX();
        runActionOnUiThread(() -> method.onKeyDown(mTextView, (Spannable) mTextView.getText(),
                KeyEvent.KEYCODE_DPAD_RIGHT, new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertTrue(mTextView.getScrollX() > previousScrollX);

        previousScrollX = mTextView.getScrollX();
        runActionOnUiThread(() -> method.onKeyDown(mTextView, (Spannable) mTextView.getText(),
                KeyEvent.KEYCODE_DPAD_LEFT, new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT)));
        assertTrue(mTextView.getScrollX() < previousScrollX);

        previousScrollX = mTextView.getScrollX();
        verifyVisibleLineInTextView(0);
        runActionOnUiThread(() -> assertFalse(method.onKeyDown(mTextView, mSpannable, 0,
                new KeyEvent(KeyEvent.ACTION_DOWN, 0))));
        assertEquals(previousScrollX, mTextView.getScrollX());
        verifyVisibleLineInTextView(0);
    }

    @Test
    public void testOnKeyDownExceptions() throws Throwable {
        runActionOnUiThread(() -> mActivity.setContentView(mTextView));
        assertNotNull(mTextView.getLayout());

        final MyScrollingMovementMethod method = new MyScrollingMovementMethod();
        runActionOnUiThread(() -> {
            try {
                method.onKeyDown(null, mSpannable, KeyEvent.KEYCODE_DPAD_RIGHT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                method.onKeyDown(mTextView, null, KeyEvent.KEYCODE_DPAD_RIGHT,
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP));
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                method.onKeyDown(mTextView, mSpannable, KeyEvent.KEYCODE_DPAD_RIGHT, null);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }
        });
    }

    @Test
    public void testVerticalMovement() throws Throwable {
        final MyScrollingMovementMethod method = new MyScrollingMovementMethod();
        runActionOnUiThread(() -> {
            mTextView.setLines(1);
            mActivity.setContentView(mTextView,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        });
        assertNotNull(mTextView.getLayout());

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.down(mTextView, mSpannable);
            }
        }));
        verifyVisibleLineInTextView(1);

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.down(mTextView, mSpannable);
            }
        }));
        verifyVisibleLineInTextView(2);

        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.down(mTextView, mSpannable);
            }
        }));
        verifyVisibleLineInTextView(2);

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.up(mTextView, mSpannable);
            }
        }));
        verifyVisibleLineInTextView(1);

        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.up(mTextView, mSpannable);
            }
        }));
        verifyVisibleLineInTextView(0);

        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.up(mTextView, mSpannable);
            }
        }));
        verifyVisibleLineInTextView(0);

        runActionOnUiThread(() -> {
            try {
                method.up(null, mSpannable);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                method.up(mTextView, null);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                method.down(null, mSpannable);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                method.down(mTextView, null);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }
        });
    }

    @Test
    public void testMovementWithNullLayout() {
        assertNull(mTextView.getLayout());
        try {
            new MyScrollingMovementMethod().down(mTextView, mSpannable);
        } catch (NullPointerException e) {
            // NPE is acceptable
        }

        try {
            new MyScrollingMovementMethod().up(mTextView, mSpannable);
        } catch (NullPointerException e) {
            // NPE is acceptable
        }

        try {
            new MyScrollingMovementMethod().left(mTextView, mSpannable);
        } catch (NullPointerException e) {
            // NPE is acceptable
        }

        try {
            new MyScrollingMovementMethod().right(mTextView, mSpannable);
        } catch (NullPointerException e) {
            // NPE is acceptable
        }

        final long now = SystemClock.uptimeMillis();
        try {
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT);
            new ScrollingMovementMethod().onKeyDown(mTextView, mSpannable,
                    KeyEvent.KEYCODE_DPAD_RIGHT, event);
        } catch (NullPointerException e) {
            // NPE is acceptable
        }

        new ScrollingMovementMethod().onTouchEvent(mTextView, mSpannable,
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0, 0, 0));
        try {
            new ScrollingMovementMethod().onTouchEvent(mTextView, mSpannable,
                    MotionEvent.obtain(now, now, MotionEvent.ACTION_MOVE, - 10000, 0, 0));
        } catch (NullPointerException e) {
            // NPE is acceptable
        }
    }

    @Test
    public void testInitialize() {
        new ScrollingMovementMethod().initialize(null, null);
    }

    @Test
    public void testOnTrackballEvent() {
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now, 0, 2, -2, 0);
        final MyScrollingMovementMethod mockMethod = new MyScrollingMovementMethod();

        assertFalse(mockMethod.onTrackballEvent(mTextView, mSpannable, event));
        assertFalse(mockMethod.onTrackballEvent(null, mSpannable, event));
        assertFalse(mockMethod.onTrackballEvent(mTextView, mSpannable, null));
        assertFalse(mockMethod.onTrackballEvent(mTextView, null, event));
    }

    @UiThreadTest
    @Test
    public void testOnKeyUp() {
        final ScrollingMovementMethod method = new ScrollingMovementMethod();
        final SpannableString spannable = new SpannableString("Test Content");
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        final TextView view = new TextViewNoIme(mActivity);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        assertFalse(method.onKeyUp(view, spannable, KeyEvent.KEYCODE_0, event));
        assertFalse(method.onKeyUp(null, null, 0, null));
        assertFalse(method.onKeyUp(null, spannable, KeyEvent.KEYCODE_0, event));
        assertFalse(method.onKeyUp(view, null, KeyEvent.KEYCODE_0, event));
        assertFalse(method.onKeyUp(view, spannable, 0, event));
        assertFalse(method.onKeyUp(view, spannable, KeyEvent.KEYCODE_0, null));
    }

    @Test
    public void testOnTakeFocus() throws Throwable {
        final ScrollingMovementMethod method = new ScrollingMovementMethod();
        // wait until the text view gets layout
        assertNull(mTextView.getLayout());
        try {
            method.onTakeFocus(mTextView, mSpannable, View.FOCUS_BACKWARD);
        } catch (NullPointerException e) {
            // NPE is acceptable
        }

        runActionOnUiThread(() -> {
            final int height = WidgetTestUtils.convertDipToPixels(mActivity, LITTLE_SPACE);
            mActivity.setContentView(mTextView,
                    new LayoutParams(LayoutParams.MATCH_PARENT,
                            height));
        });
        final Layout layout = mTextView.getLayout();
        assertNotNull(layout);

        int previousScrollY = mTextView.getScrollY();
        runActionOnUiThread(() -> method.onTakeFocus(mTextView, mSpannable, View.FOCUS_BACKWARD));
        assertTrue(mTextView.getScrollY() >= previousScrollY);
        verifyVisibleLineInTextView(2);

        previousScrollY = mTextView.getScrollY();
        runActionOnUiThread(() -> method.onTakeFocus(mTextView, mSpannable, View.FOCUS_FORWARD));
        assertTrue(mTextView.getScrollY() <= previousScrollY);
        verifyVisibleLineInTextView(0);

        runActionOnUiThread(() -> {
            try {
                method.onTakeFocus(null, mSpannable, View.FOCUS_FORWARD);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }

            try {
                method.onTakeFocus(mTextView, null, View.FOCUS_FORWARD);
            } catch (NullPointerException e) {
                // NPE is acceptable
            }
        });
    }

    @Test
    public void testHorizontalMovement() throws Throwable {
        final MyScrollingMovementMethod method = new MyScrollingMovementMethod();
        runActionOnUiThread(() -> {
            mTextView.setText("short");
            mTextView.setSingleLine();
            final DisplayMetrics dm = mActivity.getResources().getDisplayMetrics();
            final int width = (int) (LITTLE_SPACE * dm.scaledDensity);
            mActivity.setContentView(mTextView,
                    new LayoutParams(width, LayoutParams.WRAP_CONTENT));
        });
        assertNotNull(mTextView.getLayout());

        int previousScrollX = mTextView.getScrollX();
        assertTrue(getActionResult(new ActionRunnerWithResult() {

            public void run() {
                mResult = method.right(mTextView, mSpannable);
            }
        }));
        assertTrue(mTextView.getScrollX() > previousScrollX);

        previousScrollX = mTextView.getScrollX();
        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.right(mTextView, mSpannable);
            }
        }));
        assertEquals(previousScrollX, mTextView.getScrollX());

        previousScrollX = mTextView.getScrollX();
        assertTrue(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.left(mTextView, mSpannable);
            }
        }));
        assertTrue(mTextView.getScrollX() < previousScrollX);

        previousScrollX = mTextView.getScrollX();
        assertFalse(getActionResult(new ActionRunnerWithResult() {
            public void run() {
                mResult = method.left(mTextView, mSpannable);
            }
        }));
        assertEquals(previousScrollX, mTextView.getScrollX());
    }

    @Test
    public void testOnKeyOther() throws Throwable {
        runActionOnUiThread(() -> mActivity.setContentView(mTextView));
        assertNotNull(mTextView.getLayout());

        verifyVisibleLineInTextView(0);
        final MyScrollingMovementMethod method = new MyScrollingMovementMethod();
        runActionOnUiThread(() -> method.onKeyOther(mTextView, null,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE,
                        KeyEvent.KEYCODE_DPAD_DOWN, 2)));
        verifyVisibleLineInTextView(1);

        runActionOnUiThread(() -> method.onKeyOther(mTextView, null,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE,
                        KeyEvent.KEYCODE_DPAD_UP, 2)));
        verifyVisibleLineInTextView(0);
    }

    private void verifyVisibleLineInTextView(int line) {
        final Layout layout = mTextView.getLayout();
        final int scrollY = mTextView.getScrollY();
        final int padding = mTextView.getTotalPaddingTop() + mTextView.getTotalPaddingBottom();
        assertTrue(layout.getLineForVertical(scrollY) <= line);
        assertTrue(layout.getLineForVertical(scrollY + mTextView.getHeight() - padding) >= line);
    }

    private boolean getActionResult(ActionRunnerWithResult actionRunner) throws Throwable {
        runActionOnUiThread(actionRunner);
        return actionRunner.getResult();
    }

    private void runActionOnUiThread(Runnable actionRunner) throws Throwable {
        mActivityRule.runOnUiThread(actionRunner);
        mInstrumentation.waitForIdleSync();
    }

    private static abstract class ActionRunnerWithResult implements Runnable {
        protected boolean mResult = false;

        public boolean getResult() {
            return mResult;
        }
    }

    private static class MyScrollingMovementMethod extends ScrollingMovementMethod {
        @Override
        protected boolean down(TextView widget, Spannable buffer) {
            return super.down(widget, buffer);
        }

        @Override
        protected boolean left(TextView widget, Spannable buffer) {
            return super.left(widget, buffer);
        }

        @Override
        protected boolean right(TextView widget, Spannable buffer) {
            return super.right(widget, buffer);
        }

        @Override
        protected boolean up(TextView widget, Spannable buffer) {
            return super.up(widget, buffer);
        }
    }
}
