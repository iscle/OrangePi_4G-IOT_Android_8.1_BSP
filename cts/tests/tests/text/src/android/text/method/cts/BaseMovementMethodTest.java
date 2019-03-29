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

package android.text.method.cts;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.TextView.BufferType.EDITABLE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Spannable;
import android.text.method.BaseMovementMethod;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerProperties;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link BaseMovementMethod}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class BaseMovementMethodTest {
    private Instrumentation mInstrumentation;
    private BaseMovementMethod mMovementMethod;
    private TextView mTextView;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mMovementMethod = new BaseMovementMethod();
    }

    @Test
    public void testOnGenericMotionEvent_horizontalScroll() throws Throwable {
        final String testLine = "some text some text";
        final String testString = testLine + "\n" + testLine;

        mActivityRule.runOnUiThread(() -> mTextView = createTextView());
        // limit lines for horizontal scroll
        mTextView.setSingleLine();
        mTextView.setText(testString, EDITABLE);

        // limit width for horizontal scroll

        setContentView(mTextView, (int) mTextView.getPaint().measureText(testLine) / 3);
        // assert the default scroll position
        assertEquals(0, mTextView.getScrollX());

        final Spannable text = (Spannable) mTextView.getText();
        final double lineSpacing = Math.ceil(mTextView.getPaint().getFontSpacing());

        // scroll right
        MotionEvent event = createScrollEvent(1, 0);
        assertTrue(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView, null);
        assertEquals(lineSpacing, mTextView.getScrollX(), lineSpacing / 4);
        event.recycle();

        // scroll left
        event = createScrollEvent(-1, 0);
        assertTrue(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView, null);
        assertEquals(0, mTextView.getScrollX());
        event.recycle();

        // cannot scroll to left
        event = createScrollEvent(-1, 0);
        assertFalse(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        event.recycle();

        // cannot scroll to right
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView,
                () -> mTextView.scrollTo((int) mTextView.getLayout().getLineWidth(0), 0));
        event = createScrollEvent(1, 0);
        assertFalse(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        event.recycle();

        // meta shift on
        // reset scroll
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView,
                () -> mTextView.scrollTo(0, 0));

        // scroll top becomes scroll right
        event = createScrollEvent(0, 1, KeyEvent.META_SHIFT_ON);
        assertTrue(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView, null);
        assertEquals(lineSpacing, mTextView.getScrollX(), lineSpacing / 4);
        event.recycle();

        // scroll down becomes scroll left
        event = createScrollEvent(0, -1, KeyEvent.META_SHIFT_ON);
        assertTrue(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView, null);
        assertEquals(0, mTextView.getScrollX());
        event.recycle();
    }

    @Test
    public void testOnGenericMotionEvent_verticalScroll() throws Throwable {
        final String testLine = "some text some text";
        final String testString = testLine + "\n" + testLine;

        mActivityRule.runOnUiThread(() -> mTextView = createTextView());
        // limit lines for vertical scroll
        mTextView.setMaxLines(1);
        mTextView.setText(testString, EDITABLE);
        setContentView(mTextView, WRAP_CONTENT);
        // assert the default scroll positions
        assertEquals(0, mTextView.getScrollY());

        final Spannable text = (Spannable) mTextView.getText();
        final int lineHeight = mTextView.getLineHeight();

        // scroll down
        MotionEvent event = createScrollEvent(0, -1);
        assertTrue(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView, null);
        assertEquals(lineHeight, mTextView.getScrollY(), lineHeight / 4);
        event.recycle();

        // scroll up
        event = createScrollEvent(0, 1);
        assertTrue(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView, null);
        assertEquals(0, mTextView.getScrollY());
        event.recycle();

        // cannot scroll up
        event = createScrollEvent(0, 1);
        assertFalse(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        event.recycle();

        // cannot scroll down
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mTextView,
                () -> mTextView.scrollTo(0, mTextView.getLayout().getHeight()));
        event = createScrollEvent(0, -1);
        assertFalse(mMovementMethod.onGenericMotionEvent(mTextView, text, event));
        event.recycle();
    }

    private TextView createTextView() {
        final TextView textView = new TextViewNoIme(mActivityRule.getActivity());
        textView.setFocusable(true);
        textView.setMovementMethod(mMovementMethod);
        textView.setTextDirection(View.TEXT_DIRECTION_LTR);
        return textView;
    }

    private void setContentView(@NonNull TextView textView, int textWidth) throws Throwable {
        final Activity activity = mActivityRule.getActivity();
        final FrameLayout layout = new FrameLayout(activity);
        layout.addView(textView, new ViewGroup.LayoutParams(textWidth, WRAP_CONTENT));

        mActivityRule.runOnUiThread(() -> {
            activity.setContentView(layout, new ViewGroup.LayoutParams(MATCH_PARENT,
                    MATCH_PARENT));
            textView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(textView.isFocused());
    }

    private static MotionEvent createScrollEvent(int horizontal, int vertical) {
        return createScrollEvent(horizontal, vertical, 0);
    }

    private static MotionEvent createScrollEvent(int horizontal, int vertical, int meta) {
        final PointerProperties[] pointerProperties = new PointerProperties[1];
        pointerProperties[0] = new PointerProperties();
        pointerProperties[0].id = 0;
        final MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].setAxisValue(MotionEvent.AXIS_VSCROLL, vertical);
        coords[0].setAxisValue(MotionEvent.AXIS_HSCROLL, horizontal);
        final long time = SystemClock.uptimeMillis();
        return MotionEvent.obtain(time, time, MotionEvent.ACTION_SCROLL, 1,
                pointerProperties, coords, meta, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_CLASS_POINTER, 0);
    }
}
