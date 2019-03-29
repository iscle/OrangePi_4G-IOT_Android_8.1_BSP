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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link LinkMovementMethod}. The class is an implementation of interface
 * {@link MovementMethod}. The typical usage of {@link MovementMethod} is tested in
 * {@link android.widget.cts.TextViewTest} and this test case is only focused on the
 * implementation of the methods.
 *
 * @see android.widget.cts.TextViewTest
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class LinkMovementMethodTest {
    private static final String CONTENT = "clickable\nunclickable\nclickable";

    private Activity mActivity;
    private LinkMovementMethod mMethod;
    private TextView mView;
    private Spannable mSpannable;
    private ClickableSpan mClickable0;
    private ClickableSpan mClickable1;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        mMethod = new LinkMovementMethod();

        // Set the content view with a text view which contains 3 lines,
        mActivityRule.runOnUiThread(() -> mView = new TextViewNoIme(mActivity));
        mView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        mView.setText(CONTENT, BufferType.SPANNABLE);

        mActivityRule.runOnUiThread(() -> mActivity.setContentView(mView));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        mSpannable = (Spannable) mView.getText();
        // make first line clickable
        mClickable0 = markClickable(0, CONTENT.indexOf('\n'));
        // make last line clickable
        mClickable1 = markClickable(CONTENT.lastIndexOf('\n'), CONTENT.length());
    }

    @Test
    public void testConstructor() {
        new LinkMovementMethod();
    }

    @Test
    public void testGetInstance() {
        MovementMethod method0 = LinkMovementMethod.getInstance();
        assertTrue(method0 instanceof LinkMovementMethod);

        MovementMethod method1 = LinkMovementMethod.getInstance();
        assertNotNull(method1);
        assertSame(method0, method1);
    }

    @Test
    public void testOnTakeFocus() {
        LinkMovementMethod method = new LinkMovementMethod();
        Spannable spannable = new SpannableString("test sequence");
        Selection.setSelection(spannable, 0, spannable.length());

        assertSelection(spannable, 0, spannable.length());
        assertTrue("Expected at least 2 spans",
                2 <= spannable.getSpans(0, spannable.length(), Object.class).length);
        method.onTakeFocus(null, spannable, View.FOCUS_UP);
        assertSelection(spannable, -1);
        assertEquals(1, spannable.getSpans(0, spannable.length(), Object.class).length);
        Object span = spannable.getSpans(0, spannable.length(), Object.class)[0];
        assertEquals(0, spannable.getSpanStart(span));
        assertEquals(0, spannable.getSpanEnd(span));
        assertEquals(Spanned.SPAN_POINT_POINT, spannable.getSpanFlags(span));

        // focus forwards
        Selection.setSelection(spannable, 0, spannable.length());
        assertSelection(spannable, 0, spannable.length());
        assertTrue("Expected at least 3 spans",
                3 <= spannable.getSpans(0, spannable.length(), Object.class).length);
        method.onTakeFocus(null, spannable, View.FOCUS_RIGHT);
        assertSelection(spannable, -1);
        assertEquals(0, spannable.getSpans(0, spannable.length(), Object.class).length);

        // force adding span while focus backward
        method.onTakeFocus(null, spannable, View.FOCUS_UP);
        // param direction is unknown(0)
        Selection.setSelection(spannable, 0, spannable.length());
        assertSelection(spannable, 0, spannable.length());
        assertTrue("Expected at least 3 spans",
                3 <= spannable.getSpans(0, spannable.length(), Object.class).length);
        method.onTakeFocus(null, spannable, 0);
        assertSelection(spannable, -1);
        assertEquals(0, spannable.getSpans(0, spannable.length(), Object.class).length);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTakeFocusNullSpannable() {
        LinkMovementMethod method = new LinkMovementMethod();
        method.onTakeFocus(new TextViewNoIme(mActivity), null, View.FOCUS_RIGHT);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDown() {
        // no selection
        assertSelection(mSpannable, -1);
        reset(mClickable0);
        reset(mClickable1);
        assertFalse(mMethod.onKeyDown(mView, mSpannable, KeyEvent.KEYCODE_ENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        verify(mClickable0, never()).onClick(any());
        verify(mClickable1, never()).onClick(any());

        // select clickable0
        Selection.setSelection(mSpannable, mSpannable.getSpanStart(mClickable0),
                mSpannable.getSpanEnd(mClickable0));
        reset(mClickable0);
        reset(mClickable1);
        assertFalse(mMethod.onKeyDown(mView, mSpannable, KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
        verify(mClickable0, times(1)).onClick(any());
        verify(mClickable1, never()).onClick(any());

        // select unclickable
        Selection.setSelection(mSpannable, mSpannable.getSpanEnd(mClickable0),
                mSpannable.getSpanStart(mClickable1));
        reset(mClickable0);
        reset(mClickable1);
        assertFalse(mMethod.onKeyDown(mView, mSpannable, KeyEvent.KEYCODE_ENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)));
        verify(mClickable0, never()).onClick(any());
        verify(mClickable1, never()).onClick(any());

        // select all clickables(more than one)
        Selection.selectAll(mSpannable);
        reset(mClickable0);
        reset(mClickable1);
        assertFalse(mMethod.onKeyDown(mView, mSpannable, KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
        verify(mClickable0, never()).onClick(any());
        verify(mClickable1, never()).onClick(any());

        // part of selection is clickable
        Selection.setSelection(mSpannable, mSpannable.getSpanEnd(mClickable0),
                mSpannable.getSpanEnd(mClickable1));
        reset(mClickable0);
        reset(mClickable1);
        assertFalse(mMethod.onKeyDown(mView, mSpannable, KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)));
        verify(mClickable0, never()).onClick(any());
        verify(mClickable1, times(1)).onClick(any());

        // selection contains only clickable1 and repeat count of the event is not 0
        Selection.setSelection(mSpannable, mSpannable.getSpanEnd(mClickable0),
        mSpannable.getSpanEnd(mClickable1));
        long now = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER, 1);

        reset(mClickable0);
        reset(mClickable1);
        assertFalse(mMethod.onKeyDown(mView, mSpannable, KeyEvent.KEYCODE_DPAD_CENTER, event));
        verify(mClickable0, never()).onClick(any());
        verify(mClickable1, never()).onClick(any());
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnKeyDown_nullViewParam() {
        mMethod.onKeyDown(null, mSpannable, KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnKeyDown_nullSpannableParam() {
        mMethod.onKeyDown(mView, null, KeyEvent.KEYCODE_DPAD_CENTER,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnKeyDown_nullKeyEventParam() {
        mMethod.onKeyDown(mView, mSpannable, KeyEvent.KEYCODE_DPAD_CENTER, null);
    }

    @UiThreadTest
    @Test
    public void testOnKeyUp() {
        LinkMovementMethod method = new LinkMovementMethod();
        // always returns false
        assertFalse(method.onKeyUp(null, null, 0, null));
        assertFalse(method.onKeyUp(new TextViewNoIme(mActivity), null, 0, null));
        assertFalse(method.onKeyUp(null, new SpannableString("blahblah"), 0, null));
        assertFalse(method.onKeyUp(null, null, KeyEvent.KEYCODE_0,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0)));
    }

    @UiThreadTest
    @Test
    public void testOnTouchEvent() {
        assertSelection(mSpannable, -1);

        // press on first line (Clickable)
        assertTrue(pressOnLine(0));
        assertSelectClickableLeftToRight(mSpannable, mClickable0);

        // release on first line
        verify(mClickable0, never()).onClick(any());
        assertTrue(releaseOnLine(0));
        verify(mClickable0, times(1)).onClick(any());

        // press on second line (unclickable)
        assertSelectClickableLeftToRight(mSpannable, mClickable0);
        // just clear selection
        pressOnLine(1);
        assertSelection(mSpannable, -1);

        // press on last line  (Clickable)
        assertTrue(pressOnLine(2));
        assertSelectClickableLeftToRight(mSpannable, mClickable1);

        // release on last line
        verify(mClickable1, never()).onClick(any());
        assertTrue(releaseOnLine(2));
        verify(mClickable1, times(1)).onClick(any());

        // release on second line (unclickable)
        assertSelectClickableLeftToRight(mSpannable, mClickable1);
        // just clear selection
        releaseOnLine(1);
        assertSelection(mSpannable, -1);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTouchEvent_nullViewParam() {
        long now = SystemClock.uptimeMillis();
        int y = (mView.getLayout().getLineTop(1) + mView.getLayout().getLineBottom(1)) / 2;
        mMethod.onTouchEvent(null, mSpannable,
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 5, y, 0));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTouchEvent_nullSpannableParam() {
        long now = SystemClock.uptimeMillis();
        int y = (mView.getLayout().getLineTop(1) + mView.getLayout().getLineBottom(1)) / 2;
        mMethod.onTouchEvent(mView, null,
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 5, y, 0));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTouchEvent_nullKeyEventParam() {
        mMethod.onTouchEvent(mView, mSpannable, null);
    }

    @UiThreadTest
    @Test
    public void testUp() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        assertSelection(mSpannable, -1);

        assertTrue(method.up(mView, mSpannable));
        assertSelectClickableRightToLeft(mSpannable, mClickable1);

        assertTrue(method.up(mView, mSpannable));
        assertSelectClickableRightToLeft(mSpannable, mClickable0);

        assertFalse(method.up(mView, mSpannable));
        assertSelectClickableRightToLeft(mSpannable, mClickable0);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testUp_nullViewParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.up(null, mSpannable);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testUp_nullSpannableParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.up(mView, null);
    }

    @UiThreadTest
    @Test
    public void testDown() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        assertSelection(mSpannable, -1);

        assertTrue(method.down(mView, mSpannable));
        assertSelectClickableLeftToRight(mSpannable, mClickable0);

        assertTrue(method.down(mView, mSpannable));
        assertSelectClickableLeftToRight(mSpannable, mClickable1);

        assertFalse(method.down(mView, mSpannable));
        assertSelectClickableLeftToRight(mSpannable, mClickable1);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testDown_nullViewParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.down(null, mSpannable);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testDown_nullSpannableParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.down(mView, null);
    }

    @UiThreadTest
    @Test
    public void testLeft() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        assertSelection(mSpannable, -1);

        assertTrue(method.left(mView, mSpannable));
        assertSelectClickableRightToLeft(mSpannable, mClickable1);

        assertTrue(method.left(mView, mSpannable));
        assertSelectClickableRightToLeft(mSpannable, mClickable0);

        assertFalse(method.left(mView, mSpannable));
        assertSelectClickableRightToLeft(mSpannable, mClickable0);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testLeft_nullViewParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.left(null, mSpannable);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testLeft_nullSpannableParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.left(mView, null);
    }

    @UiThreadTest
    @Test
    public void testRight() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        assertSelection(mSpannable, -1);

        assertTrue(method.right(mView, mSpannable));
        assertSelectClickableLeftToRight(mSpannable, mClickable0);

        assertTrue(method.right(mView, mSpannable));
        assertSelectClickableLeftToRight(mSpannable, mClickable1);

        assertFalse(method.right(mView, mSpannable));
        assertSelectClickableLeftToRight(mSpannable, mClickable1);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testRight_nullViewParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.right(null, mSpannable);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testRight_nullSpannableParam() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        method.right(mView, null);
    }

    @UiThreadTest
    @Test
    public void testMoveAroundUnclickable() {
        final MyLinkMovementMethod method = new MyLinkMovementMethod();
        mSpannable.removeSpan(mClickable0);
        mSpannable.removeSpan(mClickable1);
        assertSelection(mSpannable, -1);

        assertFalse(method.up(mView, mSpannable));
        assertSelection(mSpannable, -1);

        assertFalse(method.down(mView, mSpannable));
        assertSelection(mSpannable, -1);

        assertFalse(method.left(mView, mSpannable));
        assertSelection(mSpannable, -1);

        assertFalse(method.right(mView, mSpannable));
        assertSelection(mSpannable, -1);
    }

    @Test
    public void testInitialize() {
        LinkMovementMethod method = new LinkMovementMethod();
        Spannable spannable = new SpannableString("test sequence");
        method.onTakeFocus(null, spannable, View.FOCUS_UP);
        Selection.setSelection(spannable, 0, spannable.length());

        assertSelection(spannable, 0, spannable.length());
        assertTrue("Expected at least 3 spans", 3 <= spannable.getSpans(0, spannable.length(), Object.class).length);
        method.initialize(null, spannable);
        assertSelection(spannable, -1);
        assertEquals(0, spannable.getSpans(0, spannable.length(), Object.class).length);

    }

    @Test(expected=NullPointerException.class)
    public void testInitialize_nullViewParam() {
        final LinkMovementMethod method = new LinkMovementMethod();
        method.initialize(mView, null);
    }

    private ClickableSpan markClickable(final int start, final int end) throws Throwable {
        final ClickableSpan clickableSpan = spy(new MockClickableSpan());
        mActivityRule.runOnUiThread(() -> mSpannable.setSpan(clickableSpan, start, end,
                Spanned.SPAN_MARK_MARK));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return clickableSpan;
    }

    private boolean performMotionOnLine(int line, int action) {
        int x = (mView.getLayout().getLineStart(line) + mView.getLayout().getLineEnd(line)) / 2;
        int y = (mView.getLayout().getLineTop(line) + mView.getLayout().getLineBottom(line)) / 2;
        long now = SystemClock.uptimeMillis();

        return mMethod.onTouchEvent(mView, mSpannable,
                MotionEvent.obtain(now, now, action, x, y, 0));
    }

    private boolean pressOnLine(int line) {
        return performMotionOnLine(line, MotionEvent.ACTION_DOWN);
    }

    private boolean releaseOnLine(int line) {
        return performMotionOnLine(line, MotionEvent.ACTION_UP);
    }

    private void assertSelection(Spannable spannable, int start, int end) {
        assertEquals(start, Selection.getSelectionStart(spannable));
        assertEquals(end, Selection.getSelectionEnd(spannable));
    }

    private void assertSelection(Spannable spannable, int position) {
        assertSelection(spannable, position, position);
    }

    private void assertSelectClickableLeftToRight(Spannable spannable,
            ClickableSpan clickableSpan) {
        assertSelection(spannable, spannable.getSpanStart(clickableSpan),
                spannable.getSpanEnd(clickableSpan));
    }

    private void assertSelectClickableRightToLeft(Spannable spannable,
            ClickableSpan clickableSpan) {
        assertSelection(spannable,  spannable.getSpanEnd(clickableSpan),
                spannable.getSpanStart(clickableSpan));
    }

    private static class MyLinkMovementMethod extends LinkMovementMethod {
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

    public static class MockClickableSpan extends ClickableSpan {
        @Override
        public void onClick(View widget) {
        }
    }
}
