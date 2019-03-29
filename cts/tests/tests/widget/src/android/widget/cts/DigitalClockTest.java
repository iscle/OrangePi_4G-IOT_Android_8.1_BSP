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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.DigitalClock;
import android.widget.LinearLayout;
import android.widget.cts.util.XmlUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Test {@link DigitalClock}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DigitalClockTest {
    private Activity mActivity;

    @Rule
    public ActivityTestRule<DigitalClockCtsActivity> mActivityRule =
            new ActivityTestRule<>(DigitalClockCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        // new the DigitalClock instance
        new DigitalClock(mActivity);

        // new the DigitalClock instance with null AttributeSet
        new DigitalClock(mActivity, null);

        // new the DigitalClock instance with real AttributeSet
        new DigitalClock(mActivity, getAttributeSet(R.layout.digitalclock_layout));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext() {
        new DigitalClock(null, getAttributeSet(R.layout.digitalclock_layout));
    }

    @UiThreadTest
    @Test
    public void testOnDetachedFromWindow() {
        final MockDigitalClock digitalClock = createDigitalClock();

        final LinearLayout linearLayout = (LinearLayout) mActivity.findViewById(
                R.id.digitalclock_root);

        assertFalse(digitalClock.hasCalledOnAttachedToWindow());
        linearLayout.addView(digitalClock);

        assertTrue(digitalClock.hasCalledOnAttachedToWindow());
        linearLayout.removeView(digitalClock);
    }

    @UiThreadTest
    @Test
    public void testOnAttachedToWindow() {
        final MockDigitalClock digitalClock = createDigitalClock();

        final LinearLayout linearLayout = (LinearLayout) mActivity.findViewById(
                R.id.digitalclock_root);

        linearLayout.addView(digitalClock);

        assertFalse(digitalClock.hasCalledOnDetachedFromWindow());

        // Clear linearLayout
        linearLayout.removeView(digitalClock);

        assertTrue(digitalClock.hasCalledOnDetachedFromWindow());
    }

    private MockDigitalClock createDigitalClock() {
        MockDigitalClock datePicker = new MockDigitalClock(mActivity,
                getAttributeSet(R.layout.digitalclock_layout));

        return datePicker;
    }

    private AttributeSet getAttributeSet(int resourceId) {
        XmlResourceParser parser = mActivity.getResources().getXml(resourceId);
        try {
            XmlUtils.beginDocument(parser, "android.widget.cts.alarmclock.DigitalClock");
        } catch (XmlPullParserException e) {
            fail("unexpected XmlPullParserException.");
        } catch (IOException e) {
            fail("unexpected IOException.");
        }
        AttributeSet attr = Xml.asAttributeSet(parser);
        assertNotNull(attr);
        return attr;
    }

    private class MockDigitalClock extends DigitalClock {
        private boolean mCalledOnAttachedToWindow   = false;
        private boolean mCalledOnDetachedFromWindow = false;

        public MockDigitalClock(Context context) {
            super(context);
        }

        public MockDigitalClock(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            mCalledOnAttachedToWindow = true;
        }

        public boolean hasCalledOnAttachedToWindow() {
            return mCalledOnAttachedToWindow;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            mCalledOnDetachedFromWindow = true;
        }

        public boolean hasCalledOnDetachedFromWindow() {
            return mCalledOnDetachedFromWindow;
        }
    }
}
