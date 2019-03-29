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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.MultiAutoCompleteTextView;
import android.widget.MultiAutoCompleteTextView.CommaTokenizer;
import android.widget.MultiAutoCompleteTextView.Tokenizer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MultiAutoCompleteTextViewTest {
    private Activity mActivity;
    private MultiAutoCompleteTextView mMultiAutoCompleteTextView_country;
    private MultiAutoCompleteTextView mMultiAutoCompleteTextView_name;

    @Rule
    public ActivityTestRule<MultiAutoCompleteTextViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(MultiAutoCompleteTextViewCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mMultiAutoCompleteTextView_country = (MultiAutoCompleteTextView)mActivity
                .findViewById(R.id.country_edit);
        mMultiAutoCompleteTextView_name = (MultiAutoCompleteTextView)mActivity
                .findViewById(R.id.name_edit);
    }

    @Test
    public void testConstructor() {
        new MultiAutoCompleteTextView(mActivity);
        new MultiAutoCompleteTextView(mActivity, null);
        new MultiAutoCompleteTextView(mActivity, null, android.R.attr.autoCompleteTextViewStyle);
        new MultiAutoCompleteTextView(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_AutoCompleteTextView);
        new MultiAutoCompleteTextView(mActivity, null, 0,
                android.R.style.Widget_DeviceDefault_Light_AutoCompleteTextView);
        new MultiAutoCompleteTextView(mActivity, null, 0,
                android.R.style.Widget_Material_AutoCompleteTextView);
        new MultiAutoCompleteTextView(mActivity, null, 0,
                android.R.style.Widget_Material_Light_AutoCompleteTextView);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new MultiAutoCompleteTextView(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new MultiAutoCompleteTextView(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new MultiAutoCompleteTextView(null, null, -1);
    }

    private void setText(final MultiAutoCompleteTextView m, final CharSequence c) {
        m.setText(c);
        m.setSelection(0, c.length());
    }

    @UiThreadTest
    @Test
    public void testMultiAutoCompleteTextView() {
        mMultiAutoCompleteTextView_country.setTokenizer(new CommaTokenizer());
        mMultiAutoCompleteTextView_name.setTokenizer(new CommaTokenizer());

        mMultiAutoCompleteTextView_country.setThreshold(3);
        mMultiAutoCompleteTextView_name.setThreshold(2);

        assertFalse(mMultiAutoCompleteTextView_country.enoughToFilter());
        assertFalse(mMultiAutoCompleteTextView_name.enoughToFilter());

        setText(mMultiAutoCompleteTextView_country, "Ar");
        assertFalse(mMultiAutoCompleteTextView_country.enoughToFilter());

        setText(mMultiAutoCompleteTextView_country, "Arg");
        assertTrue(mMultiAutoCompleteTextView_country.enoughToFilter());

        setText(mMultiAutoCompleteTextView_country, "Argentina");
        assertTrue(mMultiAutoCompleteTextView_country.enoughToFilter());

        setText(mMultiAutoCompleteTextView_name, "J");
        assertFalse(mMultiAutoCompleteTextView_name.enoughToFilter());

        setText(mMultiAutoCompleteTextView_name, "Ja");
        assertTrue(mMultiAutoCompleteTextView_name.enoughToFilter());

        setText(mMultiAutoCompleteTextView_name, "Jacky");
        assertTrue(mMultiAutoCompleteTextView_name.enoughToFilter());
    }

    @UiThreadTest
    @Test
    public void testPerformValidation() {
        final AutoCompleteTextView.Validator validator = mock(AutoCompleteTextView.Validator.class);
        when(validator.isValid(any(CharSequence.class))).thenReturn(true);
        when(validator.fixText(any(CharSequence.class))).thenAnswer(
                new Answer<CharSequence>() {
                    @Override
                    public CharSequence answer(InvocationOnMock invocation) throws Throwable {
                        // Return the originally passed parameter
                        return (CharSequence) invocation.getArguments()[0];
                    }
                });

        mMultiAutoCompleteTextView_country.setValidator(validator);
        MockTokenizer t = new MockTokenizer();
        mMultiAutoCompleteTextView_country.setTokenizer(t);
        String str = new String("Foo, Android Test, OH");
        mMultiAutoCompleteTextView_country.setText(str);
        mMultiAutoCompleteTextView_country.performValidation();
        assertEquals(str, mMultiAutoCompleteTextView_country.getText().toString());

        when(validator.isValid(any(CharSequence.class))).thenReturn(false);
        mMultiAutoCompleteTextView_country.performValidation();
        assertEquals(str + ", ", mMultiAutoCompleteTextView_country.getText().toString());
    }

    @UiThreadTest
    @Test
    public void testPerformFiltering() {
        MyMultiAutoCompleteTextView multiAutoCompleteTextView =
            new MyMultiAutoCompleteTextView(mActivity);
        CommaTokenizer t = new CommaTokenizer();
        multiAutoCompleteTextView.setTokenizer(t);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(mActivity,
                R.layout.simple_dropdown_item_1line);
        assertNotNull(adapter);

        multiAutoCompleteTextView.setAdapter(adapter);
        assertNotNull(multiAutoCompleteTextView.getFilter());

        String text = "Android test.";
        multiAutoCompleteTextView.setText(text);
        multiAutoCompleteTextView.setSelection(0, 12);
        multiAutoCompleteTextView.performFiltering(text, KeyEvent.KEYCODE_0);

        assertNotNull(multiAutoCompleteTextView.getFilter());

        multiAutoCompleteTextView.performFiltering(text, 0, text.length(), KeyEvent.KEYCODE_E);
        assertNotNull(multiAutoCompleteTextView.getFilter());
    }

    @UiThreadTest
    @Test
    public void testReplaceText() {
        MyMultiAutoCompleteTextView multiAutoCompleteTextView =
            new MyMultiAutoCompleteTextView(mActivity);
        CommaTokenizer t = new CommaTokenizer();
        multiAutoCompleteTextView.setTokenizer(t);

        String text = "CTS.";
        multiAutoCompleteTextView.setText(text);
        assertEquals(text, multiAutoCompleteTextView.getText().toString());
        multiAutoCompleteTextView.setSelection(0, text.length());

        // set the selection range.
        multiAutoCompleteTextView.replaceText("Android Test.");
        assertEquals("Android Test., ", multiAutoCompleteTextView.getText().toString());

        // do not set the selection range.
        multiAutoCompleteTextView.replaceText("replace test");
        assertEquals("Android Test., replace test, ",
                multiAutoCompleteTextView.getText().toString());
    }

    private class MockTokenizer implements Tokenizer {
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0 && (text.charAt(i - 1) == ' ' || text.charAt(i - 1) == ',')) {
                i--;
            }

            while (i > 0 && text.charAt(i - 1) != ',') {
                i--;
            }

            while (i < cursor && text.charAt(i) == ' ') {
                i++;
            }

            return i;
        }

        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();

            while (i < len) {
                if (text.charAt(i) == ',') {
                    return i;
                } else {
                    i++;
                }
            }

            return len;
        }

        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();

            while (i > 0 && text.charAt(i - 1) == ' ') {
                i--;
            }

            if (i > 0 && text.charAt(i - 1) == ',') {
                return text;
            } else {
                if (text instanceof Spanned) {
                    SpannableString sp = new SpannableString(text + ", ");
                    TextUtils.copySpansFrom((Spanned)text, 0, text.length(), Object.class, sp, 0);
                    return sp;
                } else {
                    return text + ", ";
                }
            }
        }
    }

    /**
     * MyMultiAutoCompleteTextView
     */
    private class MyMultiAutoCompleteTextView extends MultiAutoCompleteTextView {
        public MyMultiAutoCompleteTextView(Context c) {
            super(c);
        }

        protected void performFiltering(CharSequence text, int keyCode) {
            super.performFiltering(text, keyCode);
        }

        protected void performFiltering(CharSequence text, int start, int end, int keyCode) {
            super.performFiltering(text, start, end, keyCode);
        }

        protected void replaceText(CharSequence text) {
            super.replaceText(text);
        }

        protected Filter getFilter() {
            return super.getFilter();
        }
    }
}
