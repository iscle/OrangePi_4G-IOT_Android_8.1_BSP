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

package android.widget.cts;

import static com.android.compatibility.common.util.WidgetTestUtils.sameCharSequence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.KeyCharacterMap;
import android.widget.DialerFilter;
import android.widget.EditText;
import android.widget.RelativeLayout;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DialerFilterTest {
    private Activity mActivity;
    private Instrumentation mInstrumentation;
    private DialerFilter mDialerFilter;

    @Rule
    public ActivityTestRule<DialerFilterCtsActivity> mActivityRule =
            new ActivityTestRule<>(DialerFilterCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);

        mDialerFilter = (DialerFilter) mActivity.findViewById(R.id.dialer_filter);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        final XmlPullParser parser = mActivity.getResources().getXml(R.layout.dialerfilter_layout);
        final AttributeSet attrs = Xml.asAttributeSet(parser);

        new DialerFilter(mActivity);
        new DialerFilter(mActivity, attrs);
    }

    @UiThreadTest
    @Test
    public void testIsQwertyKeyboard() {
        // Simply call the method. Return value may depend on the default keyboard.
        mDialerFilter.isQwertyKeyboard();
    }

    @Test
    public void testOnKeyUpDown() throws Throwable {
        // The exact behavior depends on the implementation of DialerKeyListener and
        // TextKeyListener, but even that may be changed. Simply assert basic scenarios.

        mActivityRule.runOnUiThread(() -> {
            mDialerFilter.setMode(DialerFilter.DIGITS_ONLY);
            mDialerFilter.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(mDialerFilter.hasFocus());

        CtsKeyEventUtil.sendString(mInstrumentation, mDialerFilter, "123");
        assertEquals("", mDialerFilter.getLetters().toString());
        assertEquals("123", mDialerFilter.getDigits().toString());

        mActivityRule.runOnUiThread(() -> {
            mDialerFilter.clearText();
            mDialerFilter.setMode(DialerFilter.LETTERS_ONLY);
        });
        mInstrumentation.waitForIdleSync();

        // 12-key support
        KeyCharacterMap keymap
                = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        if (keymap.getKeyboardType() == KeyCharacterMap.NUMERIC) {
            // "adg" in case of 12-key(NUMERIC) keyboard
            CtsKeyEventUtil.sendString(mInstrumentation, mDialerFilter, "234");
        }
        else {
            CtsKeyEventUtil.sendString(mInstrumentation, mDialerFilter, "adg");
        }
        assertEquals("ADG", mDialerFilter.getLetters().toString());
        assertEquals("", mDialerFilter.getDigits().toString());

        mActivityRule.runOnUiThread(() -> {
            mDialerFilter.clearText();
            mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        });
        mInstrumentation.waitForIdleSync();

        // 12-key support
        if (keymap.getKeyboardType() == KeyCharacterMap.NUMERIC) {
            // "adg" in case of 12-key(NUMERIC) keyboard
            CtsKeyEventUtil.sendString(mInstrumentation, mDialerFilter, "234");
        }
        else {
            CtsKeyEventUtil.sendString(mInstrumentation, mDialerFilter, "adg");
        }
        assertEquals("ADG", mDialerFilter.getLetters().toString());
        // A, D, K may map to numbers on some keyboards. Don't test.

        mActivityRule.runOnUiThread(() -> {
            mDialerFilter.clearText();
            mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        });
        mInstrumentation.waitForIdleSync();

        CtsKeyEventUtil.sendString(mInstrumentation, mDialerFilter, "123");
        // 1, 2, 3 may map to letters on some keyboards. Don't test.
        assertEquals("123", mDialerFilter.getDigits().toString());
    }

    @UiThreadTest
    @Test
    public void testAccessMode() {
        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS_NO_LETTERS);
        assertEquals(DialerFilter.DIGITS_AND_LETTERS_NO_LETTERS, mDialerFilter.getMode());

        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        assertEquals(DialerFilter.DIGITS_AND_LETTERS, mDialerFilter.getMode());

        mDialerFilter.setMode(-1);
        assertEquals(-1, mDialerFilter.getMode());
    }

    @UiThreadTest
    @Test
    public void testGetLetters() {
        assertEquals("", mDialerFilter.getLetters().toString());

        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        mDialerFilter.append("ANDROID");
        assertEquals("ANDROID", mDialerFilter.getLetters().toString());
    }

    @UiThreadTest
    @Test
    public void testGetDigits() {
        assertEquals("", mDialerFilter.getDigits().toString());

        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        mDialerFilter.append("12345");
        assertEquals("12345", mDialerFilter.getDigits().toString());
    }

    @UiThreadTest
    @Test
    public void testGetFilterText() {
        assertEquals("", mDialerFilter.getFilterText().toString());

        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        mDialerFilter.append("CTS12345");
        assertEquals("CTS12345", mDialerFilter.getFilterText().toString());

        mDialerFilter.setMode(DialerFilter.DIGITS_ONLY);
        assertEquals("12345", mDialerFilter.getFilterText().toString());

        mDialerFilter.setMode(DialerFilter.LETTERS_ONLY);
        assertEquals("CTS12345", mDialerFilter.getFilterText().toString());
    }

    @UiThreadTest
    @Test
    public void testAppend() {
        mDialerFilter.setMode(DialerFilter.LETTERS_ONLY);
        mDialerFilter.append("ANDROID");
        assertEquals("ANDROID", mDialerFilter.getLetters().toString());
        assertEquals("", mDialerFilter.getDigits().toString());
        assertEquals("ANDROID", mDialerFilter.getFilterText().toString());

        mDialerFilter.setMode(DialerFilter.DIGITS_ONLY);
        mDialerFilter.append("123");
        assertEquals("", mDialerFilter.getLetters().toString());
        assertEquals("123", mDialerFilter.getDigits().toString());
        assertEquals("123", mDialerFilter.getFilterText().toString());

        mDialerFilter.clearText();
        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        mDialerFilter.append("ABC123DEF456GHI789");
        assertEquals("ABC123DEF456GHI789", mDialerFilter.getLetters().toString());
        assertEquals("123456789", mDialerFilter.getDigits().toString());
        assertEquals("ABC123DEF456GHI789", mDialerFilter.getFilterText().toString());

        mDialerFilter.clearText();
        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS_NO_DIGITS);
        mDialerFilter.append("ABC123DEF456GHI789");
        assertEquals("ABC123DEF456GHI789", mDialerFilter.getLetters().toString());
        assertEquals("", mDialerFilter.getDigits().toString());
        assertEquals("ABC123DEF456GHI789", mDialerFilter.getFilterText().toString());

        mDialerFilter.clearText();
        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS_NO_LETTERS);
        mDialerFilter.append("ABC123DEF456GHI789");
        assertEquals("", mDialerFilter.getLetters().toString());
        assertEquals("123456789", mDialerFilter.getDigits().toString());
        assertEquals("", mDialerFilter.getFilterText().toString());

        mDialerFilter.clearText();
        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        mDialerFilter.append("");
        assertEquals("", mDialerFilter.getLetters().toString());
        assertEquals("", mDialerFilter.getDigits().toString());
        assertEquals("", mDialerFilter.getFilterText().toString());
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testAppendNull() {
        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        mDialerFilter.append(null);
    }

    @UiThreadTest
    @Test
    public void testClearText() {
        assertEquals("", mDialerFilter.getLetters().toString());
        assertEquals("", mDialerFilter.getDigits().toString());

        mDialerFilter.setMode(DialerFilter.DIGITS_AND_LETTERS);
        mDialerFilter.append("CTS12345");
        assertEquals("CTS12345", mDialerFilter.getLetters().toString());
        assertEquals("12345", mDialerFilter.getDigits().toString());

        mDialerFilter.clearText();
        assertEquals("", mDialerFilter.getLetters().toString());
        assertEquals("", mDialerFilter.getDigits().toString());
        assertEquals(DialerFilter.DIGITS_AND_LETTERS, mDialerFilter.getMode());
    }

    @UiThreadTest
    @Test
    public void testSetLettersWatcher() {
        final TextWatcher mockTextWatcher = mock(TextWatcher.class);

        Spannable span = (Spannable) mDialerFilter.getLetters();
        assertEquals(-1, span.getSpanStart(mockTextWatcher));
        assertEquals(-1, span.getSpanEnd(mockTextWatcher));

        mDialerFilter.setMode(DialerFilter.LETTERS_ONLY);
        mDialerFilter.setLettersWatcher(mockTextWatcher);
        mDialerFilter.append("ANDROID");
        verify(mockTextWatcher, times(1)).onTextChanged(sameCharSequence("ANDROID"), eq(0),
                eq(0), eq(7));

        span = (Spannable) mDialerFilter.getLetters();
        assertEquals(0, span.getSpanStart(mockTextWatcher));
        assertEquals(mDialerFilter.getLetters().length(), span.getSpanEnd(mockTextWatcher));
        assertEquals("ANDROID", span.toString());

        reset(mockTextWatcher);
        mDialerFilter.setLettersWatcher(mockTextWatcher);
        mDialerFilter.append("");
        verifyZeroInteractions(mockTextWatcher);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testSetLettersWatcherWithNullAppend() {
        final TextWatcher mockTextWatcher = mock(TextWatcher.class);

        mDialerFilter.setLettersWatcher(mockTextWatcher);
        mDialerFilter.append(null);
    }

    @UiThreadTest
    @Test
    public void testSetDigitsWatcher() {
        final TextWatcher mockTextWatcher = mock(TextWatcher.class);

        Spannable span = (Spannable) mDialerFilter.getDigits();
        assertEquals(-1, span.getSpanStart(mockTextWatcher));
        assertEquals(-1, span.getSpanEnd(mockTextWatcher));

        mDialerFilter.setDigitsWatcher(mockTextWatcher);
        assertEquals(0, span.getSpanStart(mockTextWatcher));
        assertEquals(mDialerFilter.getDigits().length(), span.getSpanEnd(mockTextWatcher));

        mDialerFilter.setMode(DialerFilter.DIGITS_ONLY);
        mDialerFilter.append("12345");
        verify(mockTextWatcher, times(1)).onTextChanged(sameCharSequence("12345"), eq(0),
                eq(0), eq(5));
    }

    @UiThreadTest
    @Test
    public void testSetFilterWatcher() {
        final TextWatcher mockTextWatcher = mock(TextWatcher.class);

        Spannable span = (Spannable) mDialerFilter.getLetters();
        assertEquals(-1, span.getSpanStart(mockTextWatcher));
        assertEquals(-1, span.getSpanEnd(mockTextWatcher));

        mDialerFilter.setMode(DialerFilter.LETTERS_ONLY);
        mDialerFilter.setFilterWatcher(mockTextWatcher);
        mDialerFilter.append("ANDROID");
        verify(mockTextWatcher, times(1)).onTextChanged(sameCharSequence("ANDROID"), eq(0),
                eq(0), eq(7));
        span = (Spannable) mDialerFilter.getLetters();

        assertEquals(0, span.getSpanStart(mockTextWatcher));
        assertEquals(mDialerFilter.getLetters().length(), span.getSpanEnd(mockTextWatcher));

        mDialerFilter.setMode(DialerFilter.DIGITS_ONLY);
        mDialerFilter.setFilterWatcher(mockTextWatcher);
        mDialerFilter.append("12345");
        verify(mockTextWatcher, times(1)).onTextChanged(sameCharSequence("12345"), eq(0),
                eq(0), eq(5));
    }

    @UiThreadTest
    @Test
    public void testRemoveFilterWatcher() {
        final TextWatcher mockTextWatcher = mock(TextWatcher.class);

        Spannable span = (Spannable) mDialerFilter.getLetters();
        assertEquals(-1, span.getSpanStart(mockTextWatcher));
        assertEquals(-1, span.getSpanEnd(mockTextWatcher));

        mDialerFilter.setMode(DialerFilter.LETTERS_ONLY);
        mDialerFilter.setFilterWatcher(mockTextWatcher);
        mDialerFilter.append("ANDROID");
        verify(mockTextWatcher, times(1)).onTextChanged(sameCharSequence("ANDROID"), eq(0),
                eq(0), eq(7));

        span = (Spannable) mDialerFilter.getLetters();
        assertEquals(0, span.getSpanStart(mockTextWatcher));
        assertEquals(mDialerFilter.getLetters().length(), span.getSpanEnd(mockTextWatcher));

        reset(mockTextWatcher);
        mDialerFilter.removeFilterWatcher(mockTextWatcher);
        mDialerFilter.append("GOLF");
        verifyZeroInteractions(mockTextWatcher);

        assertEquals(-1, span.getSpanStart(mockTextWatcher));
        assertEquals(-1, span.getSpanEnd(mockTextWatcher));
    }

    @UiThreadTest
    @Test
    public void testOnModeChange() {
        final MockDialerFilter dialerFilter = createMyDialerFilter();
        dialerFilter.onFinishInflate();

        assertEquals(0, dialerFilter.getOldMode());
        assertEquals(MockDialerFilter.DIGITS_AND_LETTERS, dialerFilter.getNewMode());

        dialerFilter.setMode(MockDialerFilter.DIGITS_AND_LETTERS_NO_LETTERS);
        assertEquals(MockDialerFilter.DIGITS_AND_LETTERS, dialerFilter.getOldMode());
        assertEquals(MockDialerFilter.DIGITS_AND_LETTERS_NO_LETTERS, dialerFilter.getNewMode());

        dialerFilter.setMode(MockDialerFilter.DIGITS_AND_LETTERS_NO_DIGITS);
        assertEquals(MockDialerFilter.DIGITS_AND_LETTERS_NO_LETTERS, dialerFilter.getOldMode());
        assertEquals(MockDialerFilter.DIGITS_AND_LETTERS_NO_DIGITS, dialerFilter.getNewMode());
    }

    private MockDialerFilter createMyDialerFilter() {
        final MockDialerFilter dialerFilter = new MockDialerFilter(mActivity);

        final EditText text1 = new EditText(mActivity);
        text1.setId(android.R.id.hint);
        final EditText text2 = new EditText(mActivity);
        text2.setId(android.R.id.primary);

        dialerFilter.addView(text1, new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT));
        dialerFilter.addView(text2, new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT));

        return dialerFilter;
    }

    /**
     * MockDialerFilter for test
     */
    private class MockDialerFilter extends DialerFilter {
        private int mOldMode = 0;
        private int mNewMode = 0;

        public MockDialerFilter(Context context) {
            super(context);
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
        }

        @Override
        protected void onModeChange(final int oldMode, final int newMode) {
            super.onModeChange(oldMode, newMode);
            mOldMode = oldMode;
            mNewMode = newMode;
        }

        public int getOldMode() {
            return mOldMode;
        }

        public int getNewMode() {
            return mNewMode;
        }
    }
}
