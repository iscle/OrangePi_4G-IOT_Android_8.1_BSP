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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.SingleLineTransformationMethod;
import android.text.style.AlignmentSpan;
import android.util.TypedValue;
import android.widget.EditText;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link SingleLineTransformationMethod}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SingleLineTransformationMethodTest {
    @Test
    public void testConstructor() {
        new SingleLineTransformationMethod();
    }

    @Test
    public void testGetInstance() {
        SingleLineTransformationMethod method0 = SingleLineTransformationMethod.getInstance();
        assertNotNull(method0);

        SingleLineTransformationMethod method1 = SingleLineTransformationMethod.getInstance();
        assertSame(method0, method1);
    }

    @Test
    public void testGetReplacement() {
        MySingleLineTranformationMethod method = new MySingleLineTranformationMethod();
        assertArrayEquals(new char[] { ' ', '\uFEFF' }, method.getReplacement());
        assertArrayEquals(new char[] { '\n', '\r' }, method.getOriginal());
    }

    @UiThreadTest
    @Test
    public void testGetTransformation() {
        SingleLineTransformationMethod method = SingleLineTransformationMethod.getInstance();
        CharSequence result = method.getTransformation("hello\nworld\r", null);
        assertEquals("hello world\uFEFF", result.toString());

        EditText editText = new EditTextNoIme(InstrumentationRegistry.getTargetContext());
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        editText.setText("hello\nworld\r");
        // TODO cannot get transformed text from the view
    }

    @Test
    public void testSubsequence_doesNotThrowExceptionWithParagraphSpans() {
        final SingleLineTransformationMethod method = SingleLineTransformationMethod.getInstance();
        final SpannableString original = new SpannableString("\ntest data\nb");
        final AlignmentSpan.Standard span = new AlignmentSpan.Standard(
                Layout.Alignment.ALIGN_NORMAL);
        original.setSpan(span, 1, original.length() - 1, Spanned.SPAN_PARAGRAPH);

        final CharSequence transformed = method.getTransformation(original, null);
        // expectation: should not throw an exception
        transformed.subSequence(0, transformed.length());
    }



    private static class MySingleLineTranformationMethod extends SingleLineTransformationMethod {
        @Override
        protected char[] getOriginal() {
            return super.getOriginal();
        }

        @Override
        protected char[] getReplacement() {
            return super.getReplacement();
        }
    }
}
