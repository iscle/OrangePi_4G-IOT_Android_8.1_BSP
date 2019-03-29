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
import android.text.method.DateKeyListener;
import android.view.KeyEvent;

import com.android.compatibility.common.util.CtsKeyEventUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Test {@link android.text.method.DateKeyListener}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class DateKeyListenerTest extends KeyListenerTestCase {
    @Test
    public void testConstructor() {
        // deprecated empty constructor
        new DateKeyListener();

        // newer constructor that takes locales
        new DateKeyListener(null); // fallback to old behavior
        new DateKeyListener(Locale.US);
        new DateKeyListener(Locale.forLanguageTag("fa-IR"));
    }

    @Test
    public void testGetInstance() {
        final DateKeyListener emptyListener1 = DateKeyListener.getInstance();
        final DateKeyListener emptyListener2 = DateKeyListener.getInstance();
        final DateKeyListener nullListener = DateKeyListener.getInstance(null);

        assertNotNull(emptyListener1);
        assertNotNull(emptyListener2);
        assertNotNull(nullListener);
        assertSame(emptyListener1, emptyListener2);
        assertSame(emptyListener1, nullListener);

        final DateKeyListener usListener1 = DateKeyListener.getInstance(Locale.US);
        final DateKeyListener usListener2 = DateKeyListener.getInstance(new Locale("en", "US"));
        final DateKeyListener irListener = DateKeyListener.getInstance(
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
        assertNotNull(DateKeyListener.CHARACTERS);

        final MockDateKeyListener emptyMockDateKeyListener = new MockDateKeyListener();
        assertSame(DateKeyListener.CHARACTERS, emptyMockDateKeyListener.getAcceptedChars());

        final MockDateKeyListener usMockDateKeyListener = new MockDateKeyListener(Locale.US);
        assertNotSame(DateKeyListener.CHARACTERS, usMockDateKeyListener.getAcceptedChars());

        MockDateKeyListener irMockDateKeyListener = new MockDateKeyListener(
                Locale.forLanguageTag("fa-IR"));
        final String acceptedChars = new String(irMockDateKeyListener.getAcceptedChars());
        // Make sure all these chararacters are accepted.
        final char[] expectedChars = {
            '\u06F0', '\u06F1', '\u06F2', '\u06F3', '\u06F4',
            '\u06F5', '\u06F6', '\u06F7', '\u06F8', '\u06F9',
            '/'
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
                | InputType.TYPE_DATETIME_VARIATION_DATE;
        // Fallback for locales that need more characters.
        final int textType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;

        // Deprecated constructor that needs to preserve pre-existing behavior.
        DateKeyListener dateKeyListener = new DateKeyListener();
        assertEquals(dateTimeType, dateKeyListener.getInputType());

        // TYPE_CLASS_DATETIME is fine for English locales.
        dateKeyListener = new DateKeyListener(Locale.US);
        assertEquals(dateTimeType, dateKeyListener.getInputType());
        dateKeyListener = new DateKeyListener(Locale.UK);
        assertEquals(dateTimeType, dateKeyListener.getInputType());

        // Persian needs more characters then typically provided by datetime inputs, so it falls
        // back on normal text.
        dateKeyListener = new DateKeyListener(Locale.forLanguageTag("fa-IR"));
        assertEquals(textType, dateKeyListener.getInputType());
    }

    /*
     * Scenario description:
     * 1. Press '1' key and check if the content of TextView becomes "1"
     * 2. Press '2' key and check if the content of TextView becomes "12"
     * 3. Press an unaccepted key if it exists and this key will not be accepted.
     * 4. Press '-' key and check if the content of TextView becomes "12-"
     * 5. Press '/' key and check if the content of TextView becomes "12-/"
     * 6. remove DateKeyListener and Press '/' key, this key will not be accepted
     */
    @Test
    public void testDateTimeKeyListener() {
        final DateKeyListener dateKeyListener = DateKeyListener.getInstance();

        setKeyListenerSync(dateKeyListener);
        assertEquals("", mTextView.getText().toString());

        // press '1' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_1);
        assertEquals("1", mTextView.getText().toString());

        // press '2' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_2);
        assertEquals("12", mTextView.getText().toString());

        // press an unaccepted key if it exists.
        int keyCode = TextMethodUtils.getUnacceptedKeyCode(DateKeyListener.CHARACTERS);
        if (-1 != keyCode) {
            CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, keyCode);
            assertEquals("12", mTextView.getText().toString());
        }

        // press '-' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_MINUS);
        assertEquals("12-", mTextView.getText().toString());

        // press '/' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_SLASH);
        assertEquals("12-/", mTextView.getText().toString());

        // remove DateKeyListener
        setKeyListenerSync(null);
        assertEquals("12-/", mTextView.getText().toString());

        // press '/' key, it will not be accepted.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_SLASH);
        assertEquals("12-/", mTextView.getText().toString());
    }

    /**
     * A mocked {@link android.text.method.DateKeyListener} for testing purposes.
     *
     * Allows {@link DateKeyListenerTest} to call
     * {@link android.text.method.DateKeyListener#getAcceptedChars()}.
     */
    private class MockDateKeyListener extends DateKeyListener {
        MockDateKeyListener() {
            super();
        }

        MockDateKeyListener(Locale locale) {
            super(locale);
        }

        @Override
        protected char[] getAcceptedChars() {
            return super.getAcceptedChars();
        }
    }
}
