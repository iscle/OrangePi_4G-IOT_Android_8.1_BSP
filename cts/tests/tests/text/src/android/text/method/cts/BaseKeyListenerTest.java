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
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.BaseKeyListener;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.TextView.BufferType;

import com.android.compatibility.common.util.CtsKeyEventUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link android.text.method.BaseKeyListener}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class BaseKeyListenerTest extends KeyListenerTestCase {
    private static final CharSequence TEST_STRING = "123456";

    @Test
    public void testBackspace() throws Throwable {
        verifyBackspace(0);
    }

    private void verifyBackspace(int modifiers) throws Throwable {
        final BaseKeyListener mockBaseKeyListener = new MockBaseKeyListener();
        final KeyEvent event = getKey(KeyEvent.KEYCODE_DEL, modifiers);
        Editable content = Editable.Factory.getInstance().newEditable(TEST_STRING);

        // Nothing to delete when the cursor is at the beginning.
        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("123456", content.toString());

        // Delete the first three letters using a selection.
        prepTextViewSync(content, mockBaseKeyListener, false, 0, 3);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("456", content.toString());

        // Delete the character prior to the cursor when there's no selection
        prepTextViewSync(content, mockBaseKeyListener, false, 2, 2);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("46", content.toString());
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("6", content.toString());

        // The deletion works on a Logical direction basis in RTL text..
        String testText = "\u05E9\u05DC\u05D5\u05DD\u002E";
        content = Editable.Factory.getInstance().newEditable(testText);

        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals(testText, content.toString());

        int end = testText.length();
        prepTextViewSync(content, mockBaseKeyListener, false, end, end);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("\u05E9\u05DC\u05D5\u05DD", content.toString());

        int middle = testText.length() / 2;
        prepTextViewSync(content, mockBaseKeyListener, false, middle, middle);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("\u05E9\u05D5\u05DD", content.toString());

        // And in BiDi text
        testText = "\u05D6\u05D4\u0020Android\u0020\u05E2\u05D5\u05D1\u05D3";
        content = Editable.Factory.getInstance().newEditable(testText);

        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals(content.toString(), content.toString());

        end = testText.length();
        prepTextViewSync(content, mockBaseKeyListener, false, end, end);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("\u05D6\u05D4\u0020Android\u0020\u05E2\u05D5\u05D1", content.toString());

        prepTextViewSync(content, mockBaseKeyListener, false, 6, 6);
        mockBaseKeyListener.backspace(mTextView, content, event.getKeyCode(), event);
        assertEquals("\u05D6\u05D4\u0020Anroid\u0020\u05E2\u05D5\u05D1", content.toString());
    }

    @Test
    public void testBackspace_withShift() throws Throwable {
        verifyBackspace(KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON);
    }

    @Test
    public void testBackspace_withAlt() throws Throwable {
        final BaseKeyListener mockBaseKeyListener = new MockBaseKeyListener();
        Editable content = Editable.Factory.getInstance().newEditable(TEST_STRING);

        // Delete the entire line with ALT + DEL, even if we're at the head...
        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);
        executeAltBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());

        // ...or the tail...
        content = Editable.Factory.getInstance().newEditable(TEST_STRING);
        final int end = TEST_STRING.length();
        prepTextViewSync(content, mockBaseKeyListener, false, end, end);
        executeAltBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());

        // ...or somewhere in the middle.
        content = Editable.Factory.getInstance().newEditable(TEST_STRING);
        final int middle = end / 2;
        prepTextViewSync(content, mockBaseKeyListener, false, middle, middle);
        executeAltBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
    }

    @Test
    public void testBackspace_withSendKeys() throws Throwable {
        final BaseKeyListener mockBaseKeyListener = new MockBaseKeyListener();

        // Delete the first character '1'
        prepTextViewSync(TEST_STRING, mockBaseKeyListener, true, 1, 1);
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        assertEquals("23456", mTextView.getText().toString());

        // Delete character '2' and '3'
        prepTextViewSync(TEST_STRING, mockBaseKeyListener, true, 1, 3);
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        assertEquals("1456", mTextView.getText().toString());

        // Delete everything on the line the cursor is on.
        prepTextViewSync(TEST_STRING, mockBaseKeyListener, true, 0, 0);
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(
                mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ALT_LEFT);
        assertEquals("", mTextView.getText().toString());

        // ALT+DEL deletes the selection only.
        prepTextViewSync(TEST_STRING, mockBaseKeyListener, true, 2, 4);
        CtsKeyEventUtil.sendKeyWhileHoldingModifier(
                mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ALT_LEFT);
        assertEquals("1256", mTextView.getText().toString());

        // DEL key does not take effect when TextView does not have BaseKeyListener.
        prepTextViewSync(TEST_STRING, null, true, 1, 1);
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        assertEquals(TEST_STRING, mTextView.getText().toString());
    }

    private void verifyCursorPosition(Editable content, int offset) {
        assertEquals(offset, Selection.getSelectionStart(content));
        assertEquals(offset, Selection.getSelectionEnd(content));
    }

    @Test
    public void testBackspace_withCtrl() throws Throwable {
        final BaseKeyListener mockBaseKeyListener = new MockBaseKeyListener();

        // If the contents only having symbolic characters, delete all characters.
        String testText = "!#$%&'()`{*}_?+";
        Editable content = Editable.Factory.getInstance().newEditable(testText);
        prepTextViewSync(content, mockBaseKeyListener, false, testText.length(), testText.length());
        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        // Latin ASCII text
        testText = "Hello, World. This is Android.";
        content = Editable.Factory.getInstance().newEditable(testText);

        // If the cursor is head of the text, should do nothing.
        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);
        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, World. This is Android.", content.toString());
        verifyCursorPosition(content, 0);

        prepTextViewSync(content, mockBaseKeyListener, false, testText.length(), testText.length());
        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, World. This is ", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, World. This ", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, World. ", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, ", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        // Latin ASCII, cursor is middle of the text.
        testText = "Hello, World. This is Android.";
        content = Editable.Factory.getInstance().newEditable(testText);
        int charsFromTail = 12;  // Cursor location is 12 chars from the tail.(before "is").
        prepTextViewSync(content, mockBaseKeyListener, false,
                         testText.length() - charsFromTail, testText.length() - charsFromTail);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, World.  is Android.", content.toString());
        verifyCursorPosition(content, content.toString().length() - charsFromTail);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello,  is Android.", content.toString());
        verifyCursorPosition(content, content.toString().length() - charsFromTail);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals(" is Android.", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals(" is Android.", content.toString());
        verifyCursorPosition(content, 0);

        // Latin ASCII, cursor is inside word.
        testText = "Hello, World. This is Android.";
        content = Editable.Factory.getInstance().newEditable(testText);
        charsFromTail = 14;  // Cursor location is 12 chars from the tail. (inside "This")
        prepTextViewSync(content, mockBaseKeyListener, false,
                         testText.length() - charsFromTail, testText.length() - charsFromTail);


        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, World. is is Android.", content.toString());
        verifyCursorPosition(content, content.toString().length() - charsFromTail);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("Hello, is is Android.", content.toString());
        verifyCursorPosition(content, content.toString().length() - charsFromTail);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("is is Android.", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("is is Android.", content.toString());
        verifyCursorPosition(content, 0);

        // Hebrew Text
        // The deletion works on a Logical direction basis.
        testText = "\u05E9\u05DC\u05D5\u05DD\u0020\u05D4\u05E2\u05D5\u05DC\u05DD\u002E\u0020" +
                   "\u05D6\u05D4\u0020\u05D0\u05E0\u05D3\u05E8\u05D5\u05D0\u05D9\u05D3\u002E";
        content = Editable.Factory.getInstance().newEditable(testText);
        prepTextViewSync(content, mockBaseKeyListener, false, testText.length(), testText.length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("\u05E9\u05DC\u05D5\u05DD\u0020\u05D4\u05E2\u05D5\u05DC\u05DD\u002E\u0020" +
                     "\u05D6\u05D4\u0020", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("\u05E9\u05DC\u05D5\u05DD\u0020\u05D4\u05E2\u05D5\u05DC\u05DD\u002E\u0020",
                     content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("\u05E9\u05DC\u05D5\u05DD\u0020", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        // BiDi Text
        // The deletion works on a Logical direction basis.
        testText = "\u05D6\u05D4\u0020\u05DC\u002D\u0020\u0041Android\u0020\u05E2\u05D5\u05D1" +
                   "\u05D3\u0020\u05D4\u05D9\u05D8\u05D1\u002E";
        content = Editable.Factory.getInstance().newEditable(testText);
        prepTextViewSync(content, mockBaseKeyListener, false, testText.length(), testText.length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("\u05D6\u05D4\u0020\u05DC\u002D\u0020\u0041Android\u0020\u05E2\u05D5\u05D1" +
                     "\u05D3\u0020", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("\u05D6\u05D4\u0020\u05DC\u002D\u0020\u0041Android\u0020", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("\u05D6\u05D4\u0020\u05DC\u002D\u0020", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("\u05D6\u05D4\u0020", content.toString());
        verifyCursorPosition(content, content.toString().length());

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlBackspace(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);
    }

    @Test
    public void testForwardDelete_withCtrl() throws Throwable {
        final BaseKeyListener mockBaseKeyListener = new MockBaseKeyListener();

        // If the contents only having symbolic characters, delete all characters.
        String testText = "!#$%&'()`{*}_?+";
        Editable content = Editable.Factory.getInstance().newEditable(testText);
        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);
        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        // Latin ASCII text
        testText = "Hello, World. This is Android.";
        content = Editable.Factory.getInstance().newEditable(testText);

        // If the cursor is tail of the text, should do nothing.
        prepTextViewSync(content, mockBaseKeyListener, false, testText.length(), testText.length());
        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. This is Android.", content.toString());
        verifyCursorPosition(content, testText.length());

        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);
        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals(", World. This is Android.", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals(". This is Android.", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals(" is Android.", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals(" Android.", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals(".", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        // Latin ASCII, cursor is middle of the text.
        testText = "Hello, World. This is Android.";
        content = Editable.Factory.getInstance().newEditable(testText);
        int charsFromHead = 14;  // Cursor location is 14 chars from the head.(before "This").
        prepTextViewSync(content, mockBaseKeyListener, false, charsFromHead, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World.  is Android.", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World.  Android.", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. .", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. ", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. ", content.toString());
        verifyCursorPosition(content, charsFromHead);

        // Latin ASCII, cursor is inside word.
        testText = "Hello, World. This is Android.";
        content = Editable.Factory.getInstance().newEditable(testText);
        charsFromHead = 16;  // Cursor location is 16 chars from the head. (inside "This")
        prepTextViewSync(content, mockBaseKeyListener, false, charsFromHead, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. Th is Android.", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. Th Android.", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. Th.", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. Th", content.toString());
        verifyCursorPosition(content, charsFromHead);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("Hello, World. Th", content.toString());
        verifyCursorPosition(content, charsFromHead);

        // Hebrew Text
        // The deletion works on a Logical direction basis.
        testText = "\u05E9\u05DC\u05D5\u05DD\u0020\u05D4\u05E2\u05D5\u05DC\u05DD\u002E\u0020" +
                   "\u05D6\u05D4\u0020\u05D0\u05E0\u05D3\u05E8\u05D5\u05D0\u05D9\u05D3\u002E";
        content = Editable.Factory.getInstance().newEditable(testText);
        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u0020\u05D4\u05E2\u05D5\u05DC\u05DD\u002E\u0020\u05D6\u05D4\u0020\u05D0" +
                     "\u05E0\u05D3\u05E8\u05D5\u05D0\u05D9\u05D3\u002E", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u002E\u0020\u05D6\u05D4\u0020\u05D0\u05E0\u05D3\u05E8\u05D5\u05D0\u05D9" +
                "\u05D3\u002E", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u0020\u05D0\u05E0\u05D3\u05E8\u05D5\u05D0\u05D9\u05D3\u002E",
                     content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u002E", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        // BiDi Text
        // The deletion works on a Logical direction basis.
        testText = "\u05D6\u05D4\u0020\u05DC\u002D\u0020\u0041Android\u0020\u05E2\u05D5\u05D1" +
                   "\u05D3\u0020\u05D4\u05D9\u05D8\u05D1\u002E";
        content = Editable.Factory.getInstance().newEditable(testText);
        prepTextViewSync(content, mockBaseKeyListener, false, 0, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u0020\u05DC\u002D\u0020\u0041Android\u0020\u05E2\u05D5\u05D1\u05D3\u0020" +
                     "\u05D4\u05D9\u05D8\u05D1\u002E", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u002D\u0020\u0041Android\u0020\u05E2\u05D5\u05D1\u05D3\u0020\u05D4\u05D9" +
                     "\u05D8\u05D1\u002E", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u0020\u05E2\u05D5\u05D1\u05D3\u0020\u05D4\u05D9\u05D8\u05D1\u002E",
                     content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u0020\u05D4\u05D9\u05D8\u05D1\u002E", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("\u002E", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);

        executeCtrlForwardDelete(content, mockBaseKeyListener);
        assertEquals("", content.toString());
        verifyCursorPosition(content, 0);
    }

    /*
     * Check point:
     * 1. Press 0 key, the content of TextView does not changed.
     * 2. Set a selection and press DEL key, the selection is deleted.
     * 3. ACTION_MULTIPLE KEYCODE_UNKNOWN by inserting the event's text into the content.
     */
    @Test
    public void testPressKey() throws Throwable {
        final BaseKeyListener mockBaseKeyListener = new MockBaseKeyListener();

        // press '0' key.
        prepTextViewSync(TEST_STRING, mockBaseKeyListener, true, 0, 0);
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_0);
        assertEquals("123456", mTextView.getText().toString());

        // delete character '2'
        prepTextViewSync(mTextView.getText(), mockBaseKeyListener, true, 1, 2);
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_DEL);
        assertEquals("13456", mTextView.getText().toString());

        // test ACTION_MULTIPLE KEYCODE_UNKNOWN key event.
        KeyEvent event = new KeyEvent(SystemClock.uptimeMillis(), "abcd",
                KeyCharacterMap.BUILT_IN_KEYBOARD, 0);
        prepTextViewSync(mTextView.getText(), mockBaseKeyListener, true, 2, 2);
        CtsKeyEventUtil.sendKey(mInstrumentation, mTextView, event);
        mInstrumentation.waitForIdleSync();
        // the text of TextView is never changed, onKeyOther never works.
//        assertEquals("13abcd456", mTextView.getText().toString());
    }

    @Test
    public void testOnKeyOther() {
        final BaseKeyListener mockBaseKeyListener = new MockBaseKeyListener();
        final String string = "abc";
        final SpannableStringBuilder content = new SpannableStringBuilder(string);

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN);
        assertFalse(mockBaseKeyListener.onKeyOther(mTextView, content, event));
        assertEquals(string, content.toString());

        event = new KeyEvent(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_0);
        assertFalse(mockBaseKeyListener.onKeyOther(mTextView, content, event));
        assertEquals(string, content.toString());

        Selection.setSelection(content, 1, 0);
        event = new KeyEvent(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_UNKNOWN);
        assertFalse(mockBaseKeyListener.onKeyOther(mTextView, content, event));
        assertEquals(string, content.toString());

        event = new KeyEvent(SystemClock.uptimeMillis(), "b", 0, 0);
        assertTrue(mockBaseKeyListener.onKeyOther(mTextView, content, event));
        assertEquals("bbc", content.toString());
    }

    private void executeAltBackspace(Editable content, BaseKeyListener listener) {
        final KeyEvent delKeyEvent = getKey(KeyEvent.KEYCODE_DEL,
                KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON);
        listener.backspace(mTextView, content, KeyEvent.KEYCODE_DEL, delKeyEvent);
    }

    private void executeCtrlBackspace(Editable content, BaseKeyListener listener) {
        final KeyEvent delKeyEvent = getKey(KeyEvent.KEYCODE_DEL,
                KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
        listener.backspace(mTextView, content, KeyEvent.KEYCODE_DEL, delKeyEvent);
    }

    private void executeCtrlForwardDelete(Editable content, BaseKeyListener listener) {
        final KeyEvent delKeyEvent = getKey(KeyEvent.KEYCODE_FORWARD_DEL,
                KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON);
        listener.forwardDelete(mTextView, content, KeyEvent.KEYCODE_FORWARD_DEL, delKeyEvent);
    }

    /**
     * Prepares mTextView state for tests by synchronously setting the content and key listener, on
     * the UI thread.
     */
    private void prepTextViewSync(final CharSequence content, final BaseKeyListener keyListener,
            final boolean selectInTextView, final int selectionStart, final int selectionEnd)
                    throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mTextView.setText(content, BufferType.EDITABLE);
            mTextView.setKeyListener(keyListener);
            Selection.setSelection(
                    selectInTextView ? mTextView.getText() : (Spannable) content,
                    selectionStart, selectionEnd);
        });
        mInstrumentation.waitForIdleSync();
        assertTrue(mTextView.hasWindowFocus());
    }

    /**
     * A mocked {@link android.text.method.BaseKeyListener} for testing purposes.
     */
    private class MockBaseKeyListener extends BaseKeyListener {
        public int getInputType() {
            return InputType.TYPE_CLASS_DATETIME
                    | InputType.TYPE_DATETIME_VARIATION_DATE;
        }
    }
}
