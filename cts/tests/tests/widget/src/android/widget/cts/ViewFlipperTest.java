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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.View;
import android.widget.TextView;
import android.widget.ViewFlipper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

/**
 * Test {@link ViewFlipper}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ViewFlipperTest {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<ViewFlipperCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewFlipperCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new ViewFlipper(mActivity);

        new ViewFlipper(mActivity, null);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.viewflipper_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new ViewFlipper(mActivity, attrs);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext() {
        new ViewFlipper(null, null);
    }

    @UiThreadTest
    @Test
    public void testSetFlipInterval() {
        ViewFlipper viewFlipper = new ViewFlipper(mActivity);
        viewFlipper.setFlipInterval(0);
        viewFlipper.setFlipInterval(-1);
    }

    @LargeTest
    @Test
    public void testViewFlipper() throws Throwable {
        final int flipInterval = mActivity.getResources().getInteger(
                R.integer.view_flipper_interval);

        mActivityRule.runOnUiThread(() -> {
            ViewFlipper viewFlipper =
                    (ViewFlipper) mActivity.findViewById(R.id.viewflipper_test);

            TextView iv1 = (TextView) mActivity.findViewById(R.id.viewflipper_textview1);
            TextView iv2 = (TextView) mActivity.findViewById(R.id.viewflipper_textview2);

            assertFalse(viewFlipper.isFlipping());
            assertSame(iv1, viewFlipper.getCurrentView());

            viewFlipper.startFlipping();
            assertTrue(viewFlipper.isFlipping());
            assertSame(iv1, viewFlipper.getCurrentView());
            assertEquals(View.VISIBLE, iv1.getVisibility());
            assertEquals(View.GONE, iv2.getVisibility());
        });

        // wait for a longer time to make sure the view flipping is completed.
        SystemClock.sleep(flipInterval + 200);
        mInstrumentation.waitForIdleSync();
        mActivityRule.runOnUiThread(() -> {
            ViewFlipper viewFlipper =
                    (ViewFlipper) mActivity.findViewById(R.id.viewflipper_test);

            TextView iv1 = (TextView) mActivity.findViewById(R.id.viewflipper_textview1);
            TextView iv2 = (TextView) mActivity.findViewById(R.id.viewflipper_textview2);

            assertSame(iv2, viewFlipper.getCurrentView());
            assertEquals(View.GONE, iv1.getVisibility());
            assertEquals(View.VISIBLE, iv2.getVisibility());
        });

        SystemClock.sleep(flipInterval + 200);
        mInstrumentation.waitForIdleSync();
        mActivityRule.runOnUiThread(() -> {
            ViewFlipper viewFlipper =
                    (ViewFlipper) mActivity.findViewById(R.id.viewflipper_test);

            TextView iv1 = (TextView) mActivity.findViewById(R.id.viewflipper_textview1);
            TextView iv2 = (TextView) mActivity.findViewById(R.id.viewflipper_textview2);

            assertSame(iv1, viewFlipper.getCurrentView());
            assertEquals(View.VISIBLE, iv1.getVisibility());
            assertEquals(View.GONE, iv2.getVisibility());

            viewFlipper.stopFlipping();
            assertFalse(viewFlipper.isFlipping());
        });
    }
}
