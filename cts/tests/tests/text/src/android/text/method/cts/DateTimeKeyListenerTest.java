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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.InputType;
import android.text.method.DateTimeKeyListener;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import com.android.compatibility.common.util.CtsKeyEventUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Test {@link android.text.method.DateTimeKeyListener}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class DateTimeKeyListenerTest extends KeyListenerTestCase {
    @Test
    public void testConstructor() {
        // deprecated empty constructor
        new DateTimeKeyListener();

        // newer constructor that takes locales
        new DateTimeKeyListener(null); // fallback to old behavior
        new DateTimeKeyListener(Locale.US);
        new DateTimeKeyListener(Locale.forLanguageTag("fa-IR"));
    }

    @Test
    public void testGetInstance() {
        final DateTimeKeyListener emptyListener1 = DateTimeKeyListener.getInstance();
        final DateTimeKeyListener emptyListener2 = DateTimeKeyListener.getInstance();
        final DateTimeKeyListener nullListener = DateTimeKeyListener.getInstance(null);

        assertNotNull(emptyListener1);
        assertNotNull(emptyListener2);
        assertNotNull(nullListener);
        assertSame(emptyListener1, emptyListener2);
        assertSame(emptyListener1, nullListener);

        final DateTimeKeyListener usListener1 = DateTimeKeyListener.getInstance(Locale.US);
        final DateTimeKeyListener usListener2 = DateTimeKeyListener.getInstance(
                new Locale("en", "US"));
        final DateTimeKeyListener irListener = DateTimeKeyListener.getInstance(
                Locale.forLanguageTag("fa-IR"));

        assertNotNull(usListener1);
        assertNotNull(usListener2);
        assertNotNull(irListener);
        assertSame(usListener1, usListener2);
        assertNotSame(usListener1, irListener);
        assertNotSame(usListener1, nullListener);
    }

    @Test
    public void testGetAcceptedChars() {
        assertNotNull(DateTimeKeyListener.CHARACTERS);

        final MockDateTimeKeyListener emptyMockDateTimeKeyListener = new MockDateTimeKeyListener();
        assertSame(DateTimeKeyListener.CHARACTERS, emptyMockDateTimeKeyListener.getAcceptedChars());

        final MockDateTimeKeyListener usMockDateTimeKeyListener =
                new MockDateTimeKeyListener(Locale.US);
        assertNotSame(DateTimeKeyListener.CHARACTERS, usMockDateTimeKeyListener.getAcceptedChars());

        MockDateTimeKeyListener irMockDateTimeKeyListener = new MockDateTimeKeyListener(
                Locale.forLanguageTag("fa-IR"));
        final String acceptedChars = new String(irMockDateTimeKeyListener.getAcceptedChars());
        // Make sure all these chararacters are accepted.
        final char[] expectedChars = {
            '\u06F0', '\u06F1', '\u06F2', '\u06F3', '\u06F4',
            '\u06F5', '\u06F6', '\u06F7', '\u06F8', '\u06F9',
            '/', ':'
        };
        for (int i = 0; i < expectedChars.length; i++) {
            assertNotEquals(-1, acceptedChars.indexOf(expectedChars[i]));
        }
        // Make sure all these chararacters are not accepted.
        final char[] unexpectedChars = {
            '0', '1', '2', '3', '4',
            '5', '6', '7', '8', '9'
        };
        for (int i = 0; i < unexpectedChars.length; i++) {
            assertEquals(-1, acceptedChars.indexOf(unexpectedChars[i]));
        }
    }

    @Test
    public void testGetInputType() {
        // The "normal" input type that has been used consistently until Android O.
        final int dateTimeType = InputType.TYPE_CLASS_DATETIME
                | InputType.TYPE_DATETIME_VARIATION_NORMAL;
        // Fallback for locales that need more characters.
        final int textType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;

        // Deprecated constructor that needs to preserve pre-existing behavior.
        DateTimeKeyListener listener = DateTimeKeyListener.getInstance();
        assertEquals(dateTimeType, listener.getInputType());

        // TYPE_CLASS_DATETIME is fine for English locales.
        listener = DateTimeKeyListener.getInstance(Locale.US);
        assertEquals(dateTimeType, listener.getInputType());
        listener = DateTimeKeyListener.getInstance(Locale.UK);
        assertEquals(dateTimeType, listener.getInputType());

        // Persian needs more characters then typically provided by datetime inputs, so it falls
        // back on normal text.
        listener = DateTimeKeyListener.getInstance(Locale.forLanguageTag("fa-IR"));
        assertEquals(textType, listener.getInputType());
    }

    /*
     * Scenario description:
     * 1. Press '1' key and check if the content of TextView becomes "1"
     * 2. Press '2' key and check if the content of TextView becomes "12"
     * 3. Press 'a' key if it is producible
     * 4. Press 'p' key if it is producible
     * 5. Press 'm' key if it is producible
     * 6. Press an unaccepted key if it exists. and this key will not be accepted.
     * 7. Remove DateTimeKeyListener and Press '1' key, this key will not be accepted
     */
    @Test
    public void testDateTimeKeyListener() {
        final DateTimeKeyListener dateTimeKeyListener = DateTimeKeyListener.getInstance();
        setKeyListenerSync(dateTimeKeyListener);
        String expectedText = "";
        assertEquals(expectedText, mTextView.getText().toString());

        // press '1' key.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "1");
        expectedText += "1";
        assertEquals(expectedText, mTextView.getText().toString());

        // press '2' key.
        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "2");
        expectedText += "2";
        assertEquals(expectedText, mTextView.getText().toString());

        // press 'a' key if producible
        KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        if ('a' == kcm.getMatch(KeyEvent.KEYCODE_A, DateTimeKeyListener.CHARACTERS)) {
            expectedText += "a";
            CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTextView, KeyEvent.KEYCODE_A);
            assertEquals(expectedText, mTextView.getText().toString());
        }

        // press 'p' key if producible
        if ('p' == kcm.getMatch(KeyEvent.KEYCODE_P, DateTimeKeyListener.CHARACTERS)) {
            expectedText += "p";
            CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTextView, KeyEvent.KEYCODE_P);
            assertEquals(expectedText, mTextView.getText().toString());
        }

        // press 'm' key if producible
        if ('m' == kcm.getMatch(KeyEvent.KEYCODE_M, DateTimeKeyListener.CHARACTERS)) {
            expectedText += "m";
            CtsKeyEventUtil.sendKeyDownUp(mInstrumentation, mTextView, KeyEvent.KEYCODE_M);
            assertEquals(expectedText, mTextView.getText().toString());
        }

        // press an unaccepted key if it exists.
        int keyCode = TextMethodUtils.getUnacceptedKeyCode(DateTimeKeyListener.CHARACTERS);
        if (-1 != keyCode) {
            CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, keyCode);
            assertEquals(expectedText, mTextView.getText().toString());
        }

        // remove DateTimeKeyListener
        setKeyListenerSync(null);
        assertEquals(expectedText, mTextView.getText().toString());

        CtsKeyEventUtil.sendString(mInstrumentation, mTextView, "1");
        assertEquals(expectedText, mTextView.getText().toString());
    }


    /**
     * A mocked {@link android.text.method.DateTimeKeyListener} for testing purposes.
     *
     * Allows {@link DateTimeKeyListenerTest} to call
     * {@link android.text.method.DateTimeKeyListener#getAcceptedChars()}.
     */
    private class MockDateTimeKeyListener extends DateTimeKeyListener {
        MockDateTimeKeyListener() {
            super();
        }

        MockDateTimeKeyListener(Locale locale) {
            super(locale);
        }

        @Override
        protected char[] getAcceptedChars() {
            return super.getAcceptedChars();
        }
    }
}
