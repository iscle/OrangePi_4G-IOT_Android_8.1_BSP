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
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.Touch;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TouchTest {
    private static final String LONG_TEXT = "Scrolls the specified widget to the specified " +
            "coordinates, except constrains the X scrolling position to the horizontal regions " +
            "of the text that will be visible after scrolling to the specified Y position." +
            "This is the description of the test." +
            "Scrolls the specified widget to the specified " +
            "coordinates, except constrains the X scrolling position to the horizontal regions " +
            "of the text that will be visible after scrolling to the specified Y position." +
            "This is the description of the test.";

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private boolean mReturnFromTouchEvent;
    private TextView mTextView;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @UiThreadTest
    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mTextView = new TextViewNoIme(mActivity);
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
    }

    @Test
    public void testScrollTo() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final float pixelPerSp =
                mActivity.getResources().getDisplayMetrics().scaledDensity;
            // Explicitly set the width so that |LONG_TEXT| causes horizontal scroll.
            mActivity.setContentView(mTextView, new ViewGroup.LayoutParams(
                (int)(100 * pixelPerSp), ViewGroup.LayoutParams.MATCH_PARENT));
            mTextView.setSingleLine(true);
            mTextView.setLines(2);
        });
        mInstrumentation.waitForIdleSync();
        TextPaint paint = mTextView.getPaint();
        final Layout layout = mTextView.getLayout();

        mActivityRule.runOnUiThread(() -> mTextView.setText(LONG_TEXT));
        mInstrumentation.waitForIdleSync();

        // get the total length of string
        final int width = getTextWidth(LONG_TEXT, paint);

        mActivityRule.runOnUiThread(
                () -> Touch.scrollTo(mTextView, layout, width - mTextView.getWidth() - 1, 0));
        mInstrumentation.waitForIdleSync();
        assertEquals(width - mTextView.getWidth() - 1, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());

        // the X to which scroll is greater than the total length of string.
        mActivityRule.runOnUiThread(() -> Touch.scrollTo(mTextView, layout, width + 100, 5));
        mInstrumentation.waitForIdleSync();
        assertEquals(width - mTextView.getWidth(), mTextView.getScrollX(), 1.0f);
        assertEquals(5, mTextView.getScrollY());

        mActivityRule.runOnUiThread(() -> Touch.scrollTo(mTextView, layout, width - 10, 5));
        mInstrumentation.waitForIdleSync();
        assertEquals(width - mTextView.getWidth(), mTextView.getScrollX(), 1.0f);
        assertEquals(5, mTextView.getScrollY());
    }

    @Test
    public void testOnTouchEvent() throws Throwable {
        // Create a string that is wider than the screen.
        DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        TextPaint paint = mTextView.getPaint();
        String text = LONG_TEXT;
        int textWidth = Math.round(paint.measureText(text));
        while (textWidth < screenWidth) {
            text += LONG_TEXT;
            textWidth = Math.round(paint.measureText(text));
        }

        // Drag the difference between the text width and the screen width.
        int dragAmount = Math.min(screenWidth, textWidth - screenWidth);
        assertTrue(dragAmount > 0);
        final String finalText = text;
        final SpannableString spannable = new SpannableString(finalText);
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(mTextView);
            mTextView.setSingleLine(true);
            mTextView.setText(finalText);
        });
        mInstrumentation.waitForIdleSync();

        long downTime = SystemClock.uptimeMillis();
        long eventTime = SystemClock.uptimeMillis();
        final MotionEvent event1 = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_DOWN, dragAmount, 0, 0);
        final MotionEvent event2 = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_MOVE, 0, 0, 0);
        final MotionEvent event3 = MotionEvent.obtain(downTime, eventTime,
                MotionEvent.ACTION_UP, 0, 0, 0);
        assertEquals(0, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());
        mReturnFromTouchEvent = false;
        mActivityRule.runOnUiThread(
                () -> mReturnFromTouchEvent = Touch.onTouchEvent(mTextView, spannable, event1));
        mInstrumentation.waitForIdleSync();
        assertTrue(mReturnFromTouchEvent);
        // TextView has not been scrolled.
        assertEquals(0, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());
        assertEquals(0, Touch.getInitialScrollX(mTextView, spannable));
        assertEquals(0, Touch.getInitialScrollY(mTextView, spannable));

        mReturnFromTouchEvent = false;
        mActivityRule.runOnUiThread(
                () -> mReturnFromTouchEvent = Touch.onTouchEvent(mTextView, spannable, event2));
        mInstrumentation.waitForIdleSync();
        assertTrue(mReturnFromTouchEvent);
        // TextView has been scrolled.
        assertEquals(dragAmount, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());
        assertEquals(0, Touch.getInitialScrollX(mTextView, spannable));
        assertEquals(0, Touch.getInitialScrollY(mTextView, spannable));

        mReturnFromTouchEvent = false;
        mActivityRule.runOnUiThread(
                () -> mReturnFromTouchEvent = Touch.onTouchEvent(mTextView, spannable, event3));
        mInstrumentation.waitForIdleSync();
        assertTrue(mReturnFromTouchEvent);
        // TextView has not been scrolled.
        assertEquals(dragAmount, mTextView.getScrollX());
        assertEquals(0, mTextView.getScrollY());
        assertEquals(-1, Touch.getInitialScrollX(mTextView, spannable));
        assertEquals(-1, Touch.getInitialScrollY(mTextView, spannable));
    }

    private int getTextWidth(String str, TextPaint paint) {
        float totalWidth = 0f;
        float[] widths = new float[str.length()];
        paint.getTextWidths(str, widths);
        for (float f : widths) {
            totalWidth += f;
        }
        return (int) totalWidth;
    }
}
