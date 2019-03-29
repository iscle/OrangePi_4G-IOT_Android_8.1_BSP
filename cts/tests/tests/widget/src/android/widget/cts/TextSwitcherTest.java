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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextSwitcher;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link TextSwitcher}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextSwitcherTest {
    /**
     * test width to be used in addView() method.
     */
    private static final int PARAMS_WIDTH = 200;
    /**
     * test height to be used in addView() method.
     */
    private static final int PARAMS_HEIGHT = 300;

    private Activity mActivity;
    private TextSwitcher mTextSwitcher;

    @Rule
    public ActivityTestRule<TextSwitcherCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextSwitcherCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mTextSwitcher = (TextSwitcher) mActivity.findViewById(R.id.switcher);
    }

    @Test
    public void testConstructor() {
        new TextSwitcher(mActivity);

        new TextSwitcher(mActivity, null);
    }

    @UiThreadTest
    @Test
    public void testSetText() {
        final String viewText1 = "Text 1";
        final String viewText2 = "Text 2";
        final String changedText = "Changed";

        TextView tv1 = new TextView(mActivity);
        TextView tv2 = new TextView(mActivity);
        tv1.setText(viewText1);
        tv2.setText(viewText2);
        mTextSwitcher.addView(tv1, 0, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
        mTextSwitcher.addView(tv2, 1, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));

        TextView tvChild1 = (TextView) mTextSwitcher.getChildAt(0);
        TextView tvChild2 = (TextView) mTextSwitcher.getChildAt(1);
        assertEquals(viewText1, (tvChild1.getText().toString()));
        assertEquals(viewText2, (tvChild2.getText().toString()));
        assertSame(tv1, mTextSwitcher.getCurrentView());

        // tvChild2's text is changed
        mTextSwitcher.setText(changedText);
        assertEquals(viewText1, (tvChild1.getText().toString()));
        assertEquals(changedText, (tvChild2.getText().toString()));
        assertSame(tv2, mTextSwitcher.getCurrentView());

        // tvChild1's text is changed
        mTextSwitcher.setText(changedText);
        assertEquals(changedText, (tvChild1.getText().toString()));
        assertEquals(changedText, (tvChild2.getText().toString()));
        assertSame(tv1, mTextSwitcher.getCurrentView());

        // tvChild2's text is changed
        mTextSwitcher.setText(null);
        assertEquals(changedText, (tvChild1.getText().toString()));
        assertEquals("", (tvChild2.getText().toString()));
        assertSame(tv2, mTextSwitcher.getCurrentView());
    }

    @UiThreadTest
    @Test
    public void testSetCurrentText() {
        final String viewText1 = "Text 1";
        final String viewText2 = "Text 2";
        final String changedText1 = "Changed 1";
        final String changedText2 = "Changed 2";

        TextView tv1 = new TextView(mActivity);
        TextView tv2 = new TextView(mActivity);
        tv1.setText(viewText1);
        tv2.setText(viewText2);
        mTextSwitcher.addView(tv1, 0, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
        mTextSwitcher.addView(tv2, 1, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));

        TextView tvChild1 = (TextView) mTextSwitcher.getChildAt(0);
        TextView tvChild2 = (TextView) mTextSwitcher.getChildAt(1);
        assertEquals(viewText1, (tvChild1.getText().toString()));
        assertEquals(viewText2, (tvChild2.getText().toString()));
        assertSame(tv1, mTextSwitcher.getCurrentView());

        // tvChild1's text is changed
        mTextSwitcher.setCurrentText(changedText1);
        assertEquals(changedText1, (tvChild1.getText().toString()));
        assertEquals(viewText2, (tvChild2.getText().toString()));
        assertSame(tv1, mTextSwitcher.getCurrentView());

        // tvChild1's text is changed
        mTextSwitcher.setCurrentText(changedText2);
        assertEquals(changedText2, (tvChild1.getText().toString()));
        assertEquals(viewText2, (tvChild2.getText().toString()));
        assertSame(tv1, mTextSwitcher.getCurrentView());

        // tvChild1's text is changed
        mTextSwitcher.setCurrentText(null);
        assertEquals("", (tvChild1.getText().toString()));
        assertEquals(viewText2, (tvChild2.getText().toString()));
        assertSame(tv1, mTextSwitcher.getCurrentView());
    }

    @UiThreadTest
    @Test
    public void testAddView() {
        TextView tv1 = new TextView(mActivity);
        TextView tv2 = new TextView(mActivity);

        mTextSwitcher.addView(tv1, 0, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
        assertSame(tv1, mTextSwitcher.getChildAt(0));
        assertEquals(1, mTextSwitcher.getChildCount());

        try {
            // tv1 already has a parent
            mTextSwitcher.addView(tv1, 0, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        try {
            mTextSwitcher.addView(tv2, Integer.MAX_VALUE,
                    new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
            fail("Should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected
        }

        mTextSwitcher.addView(tv2, 1,
                new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
        assertSame(tv2, mTextSwitcher.getChildAt(1));
        assertEquals(2, mTextSwitcher.getChildCount());

        TextView tv3 = new TextView(mActivity);

        try {
            // mTextSwitcher already has 2 children.
            mTextSwitcher.addView(tv3, 2, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
            fail("Should throw IllegalStateException");
        } catch (IllegalStateException e) {
            // expected
        }

        mTextSwitcher = new TextSwitcher(mActivity);
        ListView lv = new ListView(mActivity);

        try {
            mTextSwitcher.addView(lv, 0, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mTextSwitcher.addView(null, 0, new ViewGroup.LayoutParams(PARAMS_WIDTH, PARAMS_HEIGHT));
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            mTextSwitcher.addView(tv3, 0, null);
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected
            // issue 1695243, not clear what is supposed to happen if the LayoutParams is null.
        }
    }
}
