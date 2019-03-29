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
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MetaKeyKeyListener;
import android.text.method.MovementMethod;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ArrowKeyMovementMethod}. The class is an implementation of interface
 * {@link MovementMethod}. The typical usage of {@link MovementMethod} is tested in
 * {@link android.widget.cts.TextViewTest} and this test case is only focused on the
 * implementation of the methods.
 *
 * @see android.widget.cts.TextViewTest
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ArrowKeyMovementMethodTest {
    private static final String THREE_LINES_TEXT = "first line\nsecond line\nlast line";
    private static final int END_OF_ALL_TEXT = THREE_LINES_TEXT.length();
    private static final int END_OF_1ST_LINE = THREE_LINES_TEXT.indexOf('\n');
    private static final int START_OF_2ND_LINE = END_OF_1ST_LINE + 1;
    private static final int END_OF_2ND_LINE = THREE_LINES_TEXT.indexOf('\n', START_OF_2ND_LINE);
    private static final int START_OF_3RD_LINE = END_OF_2ND_LINE + 1;
    private static final int SPACE_IN_2ND_LINE = THREE_LINES_TEXT.indexOf(' ', START_OF_2ND_LINE);

    private Instrumentation mInstrumentation;
    private TextView mTextView;
    private ArrowKeyMovementMethod mArrowKeyMovementMethod;
    private Editable mEditable;
    private MyMetaKeyKeyListener mMetaListener;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mMetaListener = new MyMetaKeyKeyListener();
        mArrowKeyMovementMethod = new ArrowKeyMovementMethod();

        mActivityRule.runOnUiThread(() -> {;
            initTextViewWithNullLayout();

            Activity activity = mActivityRule.getActivity();
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

            activity.setContentView(mTextView);
            mTextView.setFocusable(true);
            mTextView.requestFocus();
        });
        PollingCheck.waitFor(() -> mTextView.isFocused() && (mTextView.getLayout() != null));
    }

    @Test
    public void testConstructor() {
        new ArrowKeyMovementMethod();
    }

    @Test
    public void testCanSelectArbitrarily() {
        assertTrue(new ArrowKeyMovementMethod().canSelectArbitrarily());
    }

    @Test
    public void testGetInstance() {
        MovementMethod method0 = ArrowKeyMovementMethod.getInstance();
        assertNotNull(method0);

        MovementMethod method1 = ArrowKeyMovementMethod.getInstance();
        assertNotNull(method1);
        assertSame(method0, method1);
    }

    @Test
    public void testOnTakeFocus() throws Throwable {
        /*
         * The following assertions depend on whether the TextView has a layout.
         * The text view will not get layout in setContent method but in other
         * handler's function. Assertion which is following the setContent will
         * not get the expecting result. It have to wait all the handlers'
         * operations on the UiTread to finish. So all these cases are divided
         * into several steps, setting the content at first, waiting the layout,
         * and checking the assertion at last.
         */
        verifySelection(-1);
        mActivityRule.runOnUiThread(() -> {
            Selection.removeSelection(mEditable);
            mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_UP);
        });
        mInstrumentation.waitForIdleSync();
        verifySelection(END_OF_ALL_TEXT);

        mActivityRule.runOnUiThread(() -> {
            Selection.removeSelection(mEditable);
            mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_LEFT);
        });
        mInstrumentation.waitForIdleSync();
        verifySelection(END_OF_ALL_TEXT);

        mActivityRule.runOnUiThread(mTextView::setSingleLine);
        // wait until the textView gets layout
        mInstrumentation.waitForIdleSync();
        assertNotNull(mTextView.getLayout());
        assertEquals(1, mTextView.getLayout().getLineCount());

        mActivityRule.runOnUiThread(() -> {
            Selection.removeSelection(mEditable);
            mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_UP);
        });
        verifySelection(END_OF_ALL_TEXT);

        mActivityRule.runOnUiThread(() -> {
            Selection.removeSelection(mEditable);
            mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_LEFT);
        });
        verifySelection(END_OF_ALL_TEXT);
    }

    @UiThreadTest
    @Test
    public void testOnTakeFocusWithNullLayout() {
        initTextViewWithNullLayout();
        verifySelectEndOfContent();
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTakeFocusNullView() {
        // Should throw NullPointerException when param textView is null
        mArrowKeyMovementMethod.onTakeFocus(null, mEditable, View.FOCUS_DOWN);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTakeFocusNullSpannable() {
        initTextViewWithNullLayout();
        // Should throw NullPointerException when param spannable is null
        mArrowKeyMovementMethod.onTakeFocus(mTextView, null, View.FOCUS_DOWN);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodeUp() {
        // shift+alt tests
        final KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_UP, 0, KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_UP, shiftAltEvent));
        // |first line
        // second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, 0);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0,
                KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_UP, shiftEvent));
        // first lin|e
        // second |line
        // last line
        assertEquals(SPACE_IN_2ND_LINE, Selection.getSelectionStart(mEditable));
        int correspondingIn1stLine = Selection.getSelectionEnd(mEditable);
        assertTrue(correspondingIn1stLine >= 0);
        assertTrue(correspondingIn1stLine <= END_OF_1ST_LINE);

        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_UP, shiftEvent));
        // |first line
        // second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, 0);

        // alt tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0,
                KeyEvent.META_ALT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_UP, altEvent));
        // |first line
        // second line
        // last line
        verifySelection(0);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP,
                0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_UP, noMetaEvent));
        // first lin|e
        // second line
        // last line
        verifySelection(correspondingIn1stLine);

        // Move to beginning of first line (behavior changed in L)
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_UP, noMetaEvent));
        // |first line
        // second line
        // last line
        verifySelection(0);

        assertFalse(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_UP, noMetaEvent));
        // first lin|e
        // second line
        // last line
        verifySelection(0);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodeDown() {
        // shift+alt tests
        KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_DOWN, 0, KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_DOWN, shiftAltEvent));
        // first line
        // second |line
        // last line|
        verifySelection(SPACE_IN_2ND_LINE, END_OF_ALL_TEXT);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
                0, KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_DOWN, shiftEvent));
        // first line
        // second |line
        // last lin|e
        assertEquals(SPACE_IN_2ND_LINE, Selection.getSelectionStart(mEditable));
        int correspondingIn3rdLine = Selection.getSelectionEnd(mEditable);
        assertTrue(correspondingIn3rdLine >= START_OF_3RD_LINE);
        assertTrue(correspondingIn3rdLine <= END_OF_ALL_TEXT);

        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_DOWN, shiftEvent));
        // first line
        // second |line
        // last line|
        verifySelection(SPACE_IN_2ND_LINE, END_OF_ALL_TEXT);

        // alt tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0,
                KeyEvent.META_ALT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_DOWN, altEvent));
        // first line
        // second line
        // last line|
        verifySelection(END_OF_ALL_TEXT);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN,
                0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_DOWN, noMetaEvent));
        // first line
        // second line
        // last lin|e
        verifySelection(correspondingIn3rdLine);

        // move to end of last line (behavior changed in L)
        Selection.setSelection(mEditable, END_OF_ALL_TEXT - 1);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_DOWN, noMetaEvent));
        // first line
        // second line
        // last line|
        verifySelection(END_OF_ALL_TEXT);

        assertFalse(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_DOWN, noMetaEvent));
        // first line
        // second line
        // last line|
        verifySelection(END_OF_ALL_TEXT);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodeLeft() {
        // shift+alt tests
        KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, 0, KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, shiftAltEvent));
        // first line
        // |second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, START_OF_2ND_LINE);

        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, shiftAltEvent));
        // first line
        // |second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, START_OF_2ND_LINE);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                0, KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, shiftEvent));
        // first line
        // second| |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, SPACE_IN_2ND_LINE - 1);

        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, shiftEvent));
        // first line
        // secon|d |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, SPACE_IN_2ND_LINE - 2);

        // alt tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0,
                KeyEvent.META_ALT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, altEvent));
        // first line
        // |second line
        // last line
        verifySelection(START_OF_2ND_LINE);

        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, altEvent));
        // first line
        // |second line
        // last line
        verifySelection(START_OF_2ND_LINE);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, noMetaEvent));
        // first line
        // second| line
        // last line
        verifySelection(SPACE_IN_2ND_LINE - 1);

        Selection.setSelection(mEditable, START_OF_2ND_LINE);
        // first line
        // |second line
        // last line
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_LEFT, noMetaEvent));
        // first line|
        // second line
        // last line
        verifySelection(END_OF_1ST_LINE);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodeRight() {
        // shift+alt tests
        KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT, 0, KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, shiftAltEvent));
        // first line
        // second |line|
        // last line
        verifySelection(SPACE_IN_2ND_LINE, END_OF_2ND_LINE);

        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, shiftAltEvent));
        // first line
        // second |line|
        // last line
        verifySelection(SPACE_IN_2ND_LINE, END_OF_2ND_LINE);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
                0, KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, shiftEvent));
        // first line
        // second |l|ine
        // last line
        verifySelection(SPACE_IN_2ND_LINE, SPACE_IN_2ND_LINE + 1);

        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, shiftEvent));
        // first line
        // second |li|ne
        // last line
        verifySelection(SPACE_IN_2ND_LINE, SPACE_IN_2ND_LINE + 2);

        // alt tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT,
                0, KeyEvent.META_ALT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, altEvent));
        // first line
        // second line|
        // last line
        verifySelection(END_OF_2ND_LINE);

        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, altEvent));
        // first line
        // second line|
        // last line
        verifySelection(END_OF_2ND_LINE);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, noMetaEvent));
        // first line
        // second l|ine
        // last line
        verifySelection(SPACE_IN_2ND_LINE + 1);

        Selection.setSelection(mEditable, END_OF_2ND_LINE);
        // first line
        // second line|
        // last line
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_RIGHT, noMetaEvent));
        // first line
        // second line
        // |last line
        verifySelection(START_OF_3RD_LINE);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodePageUp() {
        // shift+alt tests
        KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_PAGE_UP, 0, KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_UP, shiftAltEvent));
        // |first line
        // second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, 0);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP,
                0, KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_UP, shiftEvent));
        // |first line
        // second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, 0);

        // alt tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP, 0,
                KeyEvent.META_ALT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_UP, altEvent));
        // |first line
        // second line
        // last line
        verifySelection(0);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP,
                0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_UP, noMetaEvent));
        // |first line
        // second line
        // last line
        verifySelection(0);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodePageDown() {
        // shift+alt tests
        KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_PAGE_DOWN, 0, KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressBothShiftAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_DOWN, shiftAltEvent));
        // first line
        // second |line
        // last line|
        verifySelection(SPACE_IN_2ND_LINE, END_OF_ALL_TEXT);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN,
                0, KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_DOWN, shiftEvent));
        // first line
        // second |line
        // last line|
        verifySelection(SPACE_IN_2ND_LINE, END_OF_ALL_TEXT);

        // alt tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0,
                KeyEvent.META_ALT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressAlt();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_DOWN, altEvent));
        // first line
        // second line
        // last line|
        verifySelection(END_OF_ALL_TEXT);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN,
                0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_PAGE_DOWN, noMetaEvent));
        // first line
        // second line
        // last line|
        verifySelection(END_OF_ALL_TEXT);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodeMoveHome() {
        // shift+ctrl tests
        KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MOVE_HOME, 0, KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_HOME, shiftAltEvent));
        // |first line
        // second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, 0);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME,
                0, KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_HOME, shiftEvent));
        // first line
        // |second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, START_OF_2ND_LINE);

        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_HOME, shiftEvent));
        // first line
        // |second |line
        // last line
        verifySelection(SPACE_IN_2ND_LINE, START_OF_2ND_LINE);

        // ctrl tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME, 0,
                KeyEvent.META_CTRL_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_HOME, altEvent));
        // |first line
        // second line
        // last line
        verifySelection(0);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME,
                0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_HOME, noMetaEvent));
        // first line
        // |second line
        // last line
        verifySelection(START_OF_2ND_LINE);

        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_HOME, noMetaEvent));
        // first line
        // |second line
        // last line
        verifySelection(START_OF_2ND_LINE);
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithKeyCodeMoveEnd() {
        // shift+ctrl tests
        KeyEvent shiftAltEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MOVE_END, 0, KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON);

        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_END, shiftAltEvent));
        // first line
        // second |line
        // last line|
        verifySelection(SPACE_IN_2ND_LINE, END_OF_ALL_TEXT);

        // shift tests
        KeyEvent shiftEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END,
                0, KeyEvent.META_SHIFT_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_END, shiftEvent));
        // first line
        // second |line|
        // last line
        verifySelection(SPACE_IN_2ND_LINE, END_OF_2ND_LINE);

        pressShift();
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_END, shiftEvent));
        // first line
        // second |line|
        // last line
        verifySelection(SPACE_IN_2ND_LINE, END_OF_2ND_LINE);

        // ctrl tests
        KeyEvent altEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END, 0,
                KeyEvent.META_CTRL_ON);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_END, altEvent));
        // first line
        // second line
        // last line|
        verifySelection(END_OF_ALL_TEXT);

        // no-meta tests
        KeyEvent noMetaEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END,
                0, 0);

        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_END, noMetaEvent));
        // first line
        // second line|
        // last line
        verifySelection(END_OF_2ND_LINE);

        MetaKeyKeyListener.resetMetaState(mEditable);
        assertTrue(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_MOVE_END, noMetaEvent));
        // first line
        // second line|
        // last line
        verifySelection(END_OF_2ND_LINE);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnKeyDownWithNullLayout() {
        initTextViewWithNullLayout();
        // Should throw NullPointerException when layout of the view is null
        mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable, KeyEvent.KEYCODE_DPAD_RIGHT, null);
    }

    @UiThreadTest
    @Test
    public void testOnKeyOther() {
        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);

        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_DPAD_CENTER, 2)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_0, 2)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_E, 2)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_UNKNOWN, 2)));

        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP, 0)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, 0)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_UP, 0)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, 0)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME, 0)));
        assertFalse(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END, 0)));

        // only repeat arrow key, page up/down and move home/end events get handled
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_DPAD_UP, 2)));
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_DPAD_DOWN, 2)));
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_DPAD_LEFT, 2)));
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_DPAD_RIGHT, 2)));
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_PAGE_UP, 2)));
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_PAGE_DOWN, 2)));
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_MOVE_HOME, 2)));
        assertTrue(mArrowKeyMovementMethod.onKeyOther(mTextView, mEditable,
                new KeyEvent(0, 0, KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_MOVE_END, 2)));
    }

    @UiThreadTest
    @Test
    public void testOnKeyDownWithOtherKeyCode() {
        // first line
        // second |line
        // last line
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);

        assertFalse(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_DPAD_CENTER, new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_CENTER)));
        assertFalse(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_0, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0)));
        assertFalse(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_E, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E)));
        assertFalse(mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable,
                KeyEvent.KEYCODE_UNKNOWN, new KeyEvent(KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_UNKNOWN)));
    }

    @UiThreadTest
    @Test
    public void testOnTouchEvent() throws Throwable {
        long now = SystemClock.currentThreadTimeMillis();
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        assertFalse(mArrowKeyMovementMethod.onTouchEvent(mTextView, mEditable,
                MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1, 1, 0)));
        verifySelection(SPACE_IN_2ND_LINE);

        assertFalse(mArrowKeyMovementMethod.onTouchEvent(mTextView, mEditable,
                MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1, 1,
                        KeyEvent.META_SHIFT_ON)));
        verifySelection(SPACE_IN_2ND_LINE);
    }

    @UiThreadTest
    @Test
    public void testOnTouchEventWithNullLayout() {
        initTextViewWithNullLayout();
        mTextView.setFocusable(true);
        mTextView.requestFocus();
        assertTrue(mTextView.isFocused());

        long now = SystemClock.currentThreadTimeMillis();
        assertFalse(mArrowKeyMovementMethod.onTouchEvent(mTextView, mEditable,
                    MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1, 1, 0)));
    }

    @UiThreadTest
    @Test
    public void testOnTouchEventWithoutFocus() {
        long now = SystemClock.currentThreadTimeMillis();
        Selection.setSelection(mEditable, SPACE_IN_2ND_LINE);
        assertFalse(mArrowKeyMovementMethod.onTouchEvent(mTextView, mEditable,
                MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1, 1, 0)));
        verifySelection(SPACE_IN_2ND_LINE);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTouchEventNullView() {
        // Should throw NullPointerException when param textView is null
        mArrowKeyMovementMethod.onTouchEvent(null, mEditable, MotionEvent.obtain(0, 0, 0, 1, 1, 0));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTouchEventNullSpannable() {
        initTextViewWithNullLayout();
        // Should throw NullPointerException when param spannable is null
        mArrowKeyMovementMethod.onTouchEvent(mTextView, null, MotionEvent.obtain(0, 0, 0, 1, 1, 0));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testOnTouchEventNullEvent() {
        initTextViewWithNullLayout();
        // Should throw NullPointerException when param motionEvent is null
        mArrowKeyMovementMethod.onTouchEvent(mTextView, mEditable, null);
    }

    @Test
    public void testInitialize() {
        Spannable spannable = new SpannableString("test content");
        ArrowKeyMovementMethod method = new ArrowKeyMovementMethod();

        assertEquals(-1, Selection.getSelectionStart(spannable));
        assertEquals(-1, Selection.getSelectionEnd(spannable));
        method.initialize(null, spannable);
        assertEquals(0, Selection.getSelectionStart(spannable));
        assertEquals(0, Selection.getSelectionEnd(spannable));

        Selection.setSelection(spannable, 2);
        assertEquals(2, Selection.getSelectionStart(spannable));
        assertEquals(2, Selection.getSelectionEnd(spannable));
        method.initialize(null, spannable);
        assertEquals(0, Selection.getSelectionStart(spannable));
        assertEquals(0, Selection.getSelectionEnd(spannable));
    }

    @Test(expected=NullPointerException.class)
    public void testIntializeNullSpannable() {
        ArrowKeyMovementMethod method = new ArrowKeyMovementMethod();
        // Should throw NullPointerException when param spannable is null
        method.initialize(mTextView, null);
    }

    @UiThreadTest
    @Test
    public void testOnTrackballEven() {
        assertFalse(mArrowKeyMovementMethod.onTrackballEvent(mTextView, mEditable,
                MotionEvent.obtain(0, 0, 0, 1, 1, 0)));

        initTextViewWithNullLayout();

        assertFalse(mArrowKeyMovementMethod.onTrackballEvent(mTextView, mEditable,
                MotionEvent.obtain(0, 0, 0, 1, 1, 0)));

        assertFalse(mArrowKeyMovementMethod.onTrackballEvent(mTextView, null,
                MotionEvent.obtain(0, 0, 0, 1, 1, 0)));

        assertFalse(mArrowKeyMovementMethod.onTrackballEvent(mTextView, mEditable, null));
    }

    @UiThreadTest
    @Test
    public void testOnKeyUp() {
        ArrowKeyMovementMethod method = new ArrowKeyMovementMethod();
        SpannableString spannable = new SpannableString("Test Content");
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0);
        TextView view = new TextViewNoIme(mActivityRule.getActivity());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);

        assertFalse(method.onKeyUp(view, spannable, KeyEvent.KEYCODE_0, event));
        assertFalse(method.onKeyUp(null, null, 0, null));
        assertFalse(method.onKeyUp(null, spannable, KeyEvent.KEYCODE_0, event));
        assertFalse(method.onKeyUp(view, null, KeyEvent.KEYCODE_0, event));
        assertFalse(method.onKeyUp(view, spannable, 0, event));
        assertFalse(method.onKeyUp(view, spannable, KeyEvent.KEYCODE_0, null));
    }

    private static final String TEXT_WORDS =
            "Lorem ipsum; dolor sit \u00e4met, conse\u0ca0_\u0ca0ctetur?       Adipiscing"
            + ".elit.integ\u00e9r. Etiam    tristique\ntortor nec   ?:?    \n\n"
            + "lectus porta consequ\u00e4t...  LOReM iPSuM";

    @UiThreadTest
    @Test
    public void testFollowingWordStartToEnd() {

        // NOTE: there seems to be much variation in how word boundaries are
        // navigated; the behaviors asserted here were derived from Google
        // Chrome 10.0.648.133 beta.

        initTextViewWithNullLayout(TEXT_WORDS);

        // |Lorem ipsum; dolor sit $met,
        Selection.setSelection(mEditable, 0);
        verifySelection(0);

        // Lorem| ipsum; dolor sit $met,
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(5);

        // Lorem ipsum|; dolor sit $met,
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(11);

        // Lorem ipsum; dolor| sit $met,
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(18);

        // Lorem ipsum; dolor sit| $met,
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(22);

        // $met|, conse$_$ctetur$       Adipiscing.elit.integ$r.
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(27);

        // $met, conse$_$ctetur|$       Adipiscing.elit.integ$r.
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(43);

        // TODO: enable these two additional word breaks when implemented
//        // $met, conse$_$ctetur$       Adipiscing|.elit.integ$r.
//        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
//        verifySelection(61);
//
//        // $met, conse$_$ctetur$       Adipiscing.elit|.integ$r.
//        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
//        verifySelection(66);

        // $met, conse$_$ctetur$       Adipiscing.elit.integ$r|.
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(74);

        // integ$r. Etiam|    tristique$tortor nec   ?:?    $$lectus porta
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(81);

        // integ$r. Etiam    tristique|$tortor nec   ?:?    $$lectus porta
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(94);

        // integ$r. Etiam    tristique$tortor| nec   ?:?    $$lectus porta
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(101);

        // integ$r. Etiam    tristique$tortor nec|   ?:?    $$lectus porta
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(105);

        // integ$r. Etiam    tristique$tortor nec   ?:?    $$lectus| porta
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(123);

        // $$lectus porta| consequ$t...  LOReM iPSuM
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(129);

        // $$lectus porta consequ$t|...  LOReM iPSuM
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(139);

        // $$lectus porta consequ$t...  LOReM| iPSuM
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(149);

        // $$lectus porta consequ$t...  LOReM iPSuM|
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(155);

        // keep trying to push beyond end, which should fail
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(155);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(155);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(155);

    }

    @UiThreadTest
    @Test
    public void testPrecedingWordEndToStart() {

        // NOTE: there seems to be much variation in how word boundaries are
        // navigated; the behaviors asserted here were derived from Google
        // Chrome 10.0.648.133 beta.

        initTextViewWithNullLayout(TEXT_WORDS);

        // $$lectus porta consequ$t...  LOReM iPSuM|
        Selection.setSelection(mEditable, mEditable.length());
        verifySelection(155);

        // $$lectus porta consequ$t...  LOReM |iPSuM
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(150);

        // $$lectus porta consequ$t...  |LOReM iPSuM
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(144);

        // $$lectus porta |consequ$t...  LOReM iPSuM
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(130);

        // $$lectus |porta consequ$t...  LOReM iPSuM
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(124);

        // integ$r. Etiam    tristique$tortor nec   ?:?    $$|lectus
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(117);

        // integ$r. Etiam    tristique$tortor |nec   ?:?    $$lectus
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(102);

        // integ$r. Etiam    tristique$|tortor nec   ?:?    $$lectus
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(95);

        // integ$r. Etiam    |tristique$tortor nec   ?:?    $$lectus
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(85);

        // integ$r. |Etiam    tristique$tortor nec   ?:?    $$lectus
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(76);

        // TODO: enable these two additional word breaks when implemented
//        // dolor sit $met, conse$_$ctetur$       Adipiscing.elit.|integ$r.
//        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
//        verifySelection(67);
//
//        // dolor sit $met, conse$_$ctetur$       Adipiscing.|elit.integ$r.
//        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
//        verifySelection(62);

        // dolor sit $met, conse$_$ctetur$       |Adipiscing.elit.integ$r.
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(51);

        // dolor sit $met, |conse$_$ctetur$       Adipiscing.elit.integ$r.
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(29);

        // dolor sit |$met, conse$_$ctetur$       Adipiscing.elit.integ$r.
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(23);

        // Lorem ipsum; dolor |sit $met
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(19);

        // Lorem ipsum; |dolor sit $met
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(13);

        // Lorem |ipsum; dolor sit $met
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(6);

        // |Lorem ipsum; dolor sit $met
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);

        // keep trying to push before beginning, which should fail
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);

    }

    private static final String TEXT_WORDS_WITH_NUMBERS =
            "Lorem ipsum123,456.90   dolor sit.. 4-0.0=2 ADipiscing4";

    @UiThreadTest
    @Test
    public void testFollowingWordStartToEndWithNumbers() {

        initTextViewWithNullLayout(TEXT_WORDS_WITH_NUMBERS);

        // |Lorem ipsum123,456.90   dolor sit.. 4-0.0=2 ADipiscing4
        Selection.setSelection(mEditable, 0);
        verifySelection(0);

        // Lorem| ipsum123,456.90   dolor sit.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(5);

        // Lorem ipsum123,456.90|   dolor sit.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(21);

        // Lorem ipsum123,456.90   dolor| sit.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(29);

        // Lorem ipsum123,456.90   dolor sit|.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(33);

        // Lorem ipsum123,456.90   dolor sit.. 4|-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(37);

        // Lorem ipsum123,456.90   dolor sit.. 4-0.0|=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(41);

        // Lorem ipsum123,456.90   dolor sit.. 4-0.0=2| ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(43);

        // Lorem ipsum123,456.90   dolor sit.. 4-0.0=2 ADipiscing4|
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(55);

        // keep trying to push beyond end, which should fail
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(55);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(55);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(55);

    }

    @UiThreadTest
    @Test
    public void testFollowingWordEndToStartWithNumbers() {

        initTextViewWithNullLayout(TEXT_WORDS_WITH_NUMBERS);

        // Lorem ipsum123,456.90   dolor sit.. 4-0.0=2 ADipiscing4|
        Selection.setSelection(mEditable, mEditable.length());
        verifySelection(55);

        // Lorem ipsum123,456.90   dolor sit.. 4-0.0=2 |ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(44);

        // Lorem ipsum123,456.90   dolor sit.. 4-0.0=|2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(42);

        // Lorem ipsum123,456.90   dolor sit.. 4-|0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(38);

        // Lorem ipsum123,456.90   dolor sit.. |4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(36);

        // Lorem ipsum123,456.90   dolor |sit.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(30);

        // Lorem ipsum123,456.90   |dolor sit.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(24);

        // Lorem |ipsum123,456.90   dolor sit.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(6);

        // |Lorem ipsum123,456.90   dolor sit.. 4-0.0=2 ADipiscing4
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);

        // keep trying to push before beginning, which should fail
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);

    }

    private static final String TEXT_WORDS_WITH_1CHAR_FINAL_WORD = "abc d";

    @UiThreadTest
    @Test
    public void testFollowingWordStartToEndWithOneCharFinalWord() {

        initTextViewWithNullLayout(TEXT_WORDS_WITH_1CHAR_FINAL_WORD);

        // |abc d
        Selection.setSelection(mEditable, 0);
        verifySelection(0);

        // abc| d
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(3);

        // abc d|
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
        verifySelection(mEditable.length());

    }

    @UiThreadTest
    @Test
    public void testFollowingWordEndToStartWithOneCharFinalWord() {

        initTextViewWithNullLayout(TEXT_WORDS_WITH_1CHAR_FINAL_WORD);

        // abc d|
        Selection.setSelection(mEditable, mEditable.length());
        verifySelection(5);

        // abc |d
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(4);

        // |abc d
        assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
        verifySelection(0);

    }

    @UiThreadTest
    @Test
    public void testMovementFromMiddleOfWord() {

        initTextViewWithNullLayout("before word after");
        verifyMoveFromInsideWord(7, 10);

        // Surrogate characters: bairkan should be considered as a standard letter
        final String BAIRKAN = "\uD800\uDF31";

        initTextViewWithNullLayout("before wo" + BAIRKAN + "rd after");
        verifyMoveFromInsideWord(7, 12);

        initTextViewWithNullLayout("before " + BAIRKAN + BAIRKAN + "xx after");
        verifyMoveFromInsideWord(7, 12);

        initTextViewWithNullLayout("before xx" + BAIRKAN + BAIRKAN + " after");
        verifyMoveFromInsideWord(7, 12);

        initTextViewWithNullLayout("before x" + BAIRKAN + "x" + BAIRKAN + " after");
        verifyMoveFromInsideWord(7, 12);

        initTextViewWithNullLayout("before " + BAIRKAN + "x" + BAIRKAN + "x after");
        verifyMoveFromInsideWord(7, 12);

        initTextViewWithNullLayout("before " + BAIRKAN + BAIRKAN + BAIRKAN + " after");
        verifyMoveFromInsideWord(7, 12);
    }

    private void verifyMoveFromInsideWord(int wordStart, int wordEnd) {

        CharSequence text = mTextView.getText();

        // Check following always goes at the end of the word
        for (int offset = wordStart; offset != wordEnd + 1; offset++) {
            // Skip positions located between a pair of surrogate characters
            if (Character.isSurrogatePair(text.charAt(offset - 1), text.charAt(offset))) {
                continue;
            }
            Selection.setSelection(mEditable, offset);
            assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_RIGHT));
            verifySelection(wordEnd + 1);
        }

        // Check preceding always goes at the beginning of the word
        for (int offset = wordEnd + 1; offset != wordStart; offset--) {
            if (Character.isSurrogatePair(text.charAt(offset - 1), text.charAt(offset))) {
                continue;
            }
            Selection.setSelection(mEditable, offset);
            assertTrue(pressCtrlChord(KeyEvent.KEYCODE_DPAD_LEFT));
            verifySelection(wordStart);
        }
    }

    private void initTextViewWithNullLayout() {
        initTextViewWithNullLayout(THREE_LINES_TEXT);
    }

    private void initTextViewWithNullLayout(CharSequence text) {
        mTextView = new TextViewNoIme(mActivityRule.getActivity());
        mTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        mTextView.setText(text, BufferType.EDITABLE);
        assertNull(mTextView.getLayout());
        mEditable = (Editable) mTextView.getText();
    }

    private void pressMetaKey(int metakey, int expectedState) {
        mMetaListener.onKeyDown(null, mEditable, metakey, null);
        assertEquals(1, MetaKeyKeyListener.getMetaState(mEditable, expectedState));
    }

    private void pressShift() {
        MetaKeyKeyListener.resetMetaState(mEditable);
        pressMetaKey(KeyEvent.KEYCODE_SHIFT_LEFT, MetaKeyKeyListener.META_SHIFT_ON);
    }

    private void pressAlt() {
        MetaKeyKeyListener.resetMetaState(mEditable);
        pressMetaKey(KeyEvent.KEYCODE_ALT_LEFT, MetaKeyKeyListener.META_ALT_ON);
    }

    private void pressBothShiftAlt() {
        MetaKeyKeyListener.resetMetaState(mEditable);
        pressMetaKey(KeyEvent.KEYCODE_SHIFT_LEFT, MetaKeyKeyListener.META_SHIFT_ON);
        pressMetaKey(KeyEvent.KEYCODE_ALT_LEFT, MetaKeyKeyListener.META_ALT_ON);
    }

    private boolean pressCtrlChord(int keyCode) {
        final long now = System.currentTimeMillis();
        final KeyEvent keyEvent = new KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_CTRL_LEFT_ON);
        return mArrowKeyMovementMethod.onKeyDown(mTextView, mEditable, keyCode, keyEvent);
    }

    private void verifySelection(int expectedPosition) {
        verifySelection(expectedPosition, expectedPosition);
    }

    private void verifySelection(int expectedStart, int expectedEnd) {
        final int actualStart = Selection.getSelectionStart(mEditable);
        final int actualEnd = Selection.getSelectionEnd(mEditable);

        verifyCharSequenceIndexEquals(mEditable, expectedStart, actualStart);
        verifyCharSequenceIndexEquals(mEditable, expectedEnd, actualEnd);
    }

    private static void verifyCharSequenceIndexEquals(CharSequence text, int expected, int actual) {
        final String message = "expected <" + getCursorSnippet(text, expected) + "> but was <"
                + getCursorSnippet(text, actual) + ">";
        assertEquals(message, expected, actual);
    }

    private static String getCursorSnippet(CharSequence text, int index) {
        if (index >= 0 && index < text.length()) {
            return text.subSequence(Math.max(0, index - 5), index) + "|"
                    + text.subSequence(index, Math.min(text.length() - 1, index + 5));
        } else {
            return null;
        }
    }

    private void verifySelectEndOfContent() {
        Selection.removeSelection(mEditable);
        mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_DOWN);
        verifySelection(END_OF_ALL_TEXT);

        Selection.removeSelection(mEditable);
        mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_RIGHT);
        verifySelection(END_OF_ALL_TEXT);

        verifySelectEndOfContentExceptFocusForward();
    }

    private void verifySelectEndOfContentExceptFocusForward() {
        Selection.removeSelection(mEditable);
        mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_UP);
        verifySelection(END_OF_ALL_TEXT);

        Selection.removeSelection(mEditable);
        mArrowKeyMovementMethod.onTakeFocus(mTextView, mEditable, View.FOCUS_LEFT);
        verifySelection(END_OF_ALL_TEXT);
    }

    private static class MyMetaKeyKeyListener extends MetaKeyKeyListener {
    }
}
