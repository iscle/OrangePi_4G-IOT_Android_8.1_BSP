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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link TwoLineListItem}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TwoLineListItemTest {
    private Activity mActivity;

    @Rule
    public ActivityTestRule<TwoLineListItemCtsActivity> mActivityRule =
            new ActivityTestRule<>(TwoLineListItemCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        AttributeSet attrs = mActivity.getResources().getLayout(R.layout.twolinelistitem);
        assertNotNull(attrs);

        new TwoLineListItem(mActivity);
        new TwoLineListItem(mActivity, attrs);
        new TwoLineListItem(mActivity, null);
        new TwoLineListItem(mActivity, attrs, 0);
        new TwoLineListItem(mActivity, null, 0);
        new TwoLineListItem(mActivity, attrs, Integer.MAX_VALUE);
        new TwoLineListItem(mActivity, attrs, Integer.MIN_VALUE);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new TwoLineListItem(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        AttributeSet attrs = mActivity.getResources().getLayout(R.layout.twolinelistitem);
        new TwoLineListItem(null, attrs);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        AttributeSet attrs = mActivity.getResources().getLayout(R.layout.twolinelistitem);
        new TwoLineListItem(null, attrs, 0);
    }

    @Test
    public void testGetTexts() {
        TwoLineListItem twoLineListItem =
            (TwoLineListItem) mActivity.findViewById(R.id.twoLineListItem);

        Resources res = mActivity.getResources();
        assertNotNull(twoLineListItem.getText1());
        assertEquals(res.getString(R.string.twolinelistitem_test_text1),
                twoLineListItem.getText1().getText().toString());
        assertNotNull(twoLineListItem.getText2());
        assertEquals(res.getString(R.string.twolinelistitem_test_text2),
                twoLineListItem.getText2().getText().toString());
    }

    @UiThreadTest
    @Test
    public void testOnFinishInflate() {
        MockTwoLineListItem twoLineListItem = new MockTwoLineListItem(mActivity);
        TextView text1 = new TextView(mActivity);
        text1.setId(android.R.id.text1);
        TextView text2 = new TextView(mActivity);
        text2.setId(android.R.id.text2);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        twoLineListItem.addView(text1, params);
        twoLineListItem.addView(text2, params);

        assertNull(twoLineListItem.getText1());
        assertNull(twoLineListItem.getText2());
        twoLineListItem.onFinishInflate();
        assertSame(text1, twoLineListItem.getText1());
        assertSame(text2, twoLineListItem.getText2());
    }

    /**
     * The Class MockTwoLineListItem is just a wrapper of TwoLineListItem to
     * make access to protected method possible .
     */
    private class MockTwoLineListItem extends TwoLineListItem {
        public MockTwoLineListItem(Context context) {
            super(context);
        }

        @Override
        protected void onFinishInflate() {
            super.onFinishInflate();
        }
    }
}
