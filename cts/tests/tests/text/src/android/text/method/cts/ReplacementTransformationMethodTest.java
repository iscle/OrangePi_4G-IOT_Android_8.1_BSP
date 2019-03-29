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
import static org.junit.Assert.fail;

import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.method.ReplacementTransformationMethod;
import android.util.TypedValue;
import android.widget.EditText;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ReplacementTransformationMethod}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ReplacementTransformationMethodTest {
    private final char[] ORIGINAL = new char[] { '0', '1' };
    private final char[] ORIGINAL_WITH_MORE_CHARS = new char[] { '0', '1', '2' };
    private final char[] ORIGINAL_WITH_SAME_CHARS = new char[] { '0', '0' };
    private final char[] REPLACEMENT = new char[] { '3', '4' };
    private final char[] REPLACEMENT_WITH_MORE_CHARS = new char[] { '3', '4', '5' };
    private final char[] REPLACEMENT_WITH_SAME_CHARS = new char[] { '3', '3' };

    private EditText mEditText;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @UiThreadTest
    @Before
    public void setup() throws Throwable {
        mEditText = new EditTextNoIme(mActivityRule.getActivity());
        mEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
    }

    @Test
    public void testGetTransformation() {
        MyReplacementTransformationMethod method =
            new MyReplacementTransformationMethod(ORIGINAL, REPLACEMENT);
        CharSequence result = method.getTransformation("010101", null);
        assertEquals("343434", result.toString());

        mEditText.setTransformationMethod(method);
        mEditText.setText("010101");
        // TODO cannot get transformed text from the view
    }

    @Test
    public void testGetTransformationWithAbnormalCharSequence() {
        ReplacementTransformationMethod method = new MyReplacementTransformationMethod(ORIGINAL,
                REPLACEMENT);

        try {
            method.getTransformation(null, null);
            fail("The method should check whether the char sequence is null.");
        } catch (NullPointerException e) {
            // expected
        }

        assertEquals("", method.getTransformation("", null).toString());
    }

    @Test
    public void testGetTransformationWithAbmornalReplacement() {
        // replacement has same chars
        ReplacementTransformationMethod method =
            new MyReplacementTransformationMethod(ORIGINAL, REPLACEMENT_WITH_SAME_CHARS);
        assertEquals("333333", method.getTransformation("010101", null).toString());

        mEditText.setTransformationMethod(method);
        mEditText.setText("010101");
        // TODO cannot get transformed text from the view

        // replacement has more chars than original
        method = new MyReplacementTransformationMethod(ORIGINAL, REPLACEMENT_WITH_MORE_CHARS);
        assertEquals("343434", method.getTransformation("010101", null).toString());

        mEditText.setTransformationMethod(method);
        mEditText.setText("010101");
        // TODO cannot get transformed text from the view
    }

    @Test
    public void testGetTransformationWithAbnormalOriginal() {
        // original has same chars
        ReplacementTransformationMethod method =
                new MyReplacementTransformationMethod(ORIGINAL_WITH_SAME_CHARS, REPLACEMENT);
        assertEquals("414141", method.getTransformation("010101", null).toString());

        mEditText.setTransformationMethod(method);
        mEditText.setText("010101");
        // TODO cannot get transformed text from the view
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testGetTransformationMismatchCharCount() {
        // original has more chars than replacement
        ReplacementTransformationMethod method =
                new MyReplacementTransformationMethod(ORIGINAL_WITH_MORE_CHARS, REPLACEMENT);
        method.getTransformation("012012012", null);
    }

    @Test
    public void testOnFocusChanged() {
        ReplacementTransformationMethod method = new MyReplacementTransformationMethod(ORIGINAL,
                REPLACEMENT);
        // blank method
        method.onFocusChanged(null, null, true, 0, null);
    }

    private static class MyReplacementTransformationMethod extends ReplacementTransformationMethod {
        private char[] mOriginal;

        private char[] mReplacement;

        public MyReplacementTransformationMethod(char[] original, char[] replacement) {
            mOriginal = original;
            mReplacement = replacement;
        }

        @Override
        protected char[] getOriginal() {
            return mOriginal;
        }

        @Override
        protected char[] getReplacement() {
            return mReplacement;
        }
    }
}
