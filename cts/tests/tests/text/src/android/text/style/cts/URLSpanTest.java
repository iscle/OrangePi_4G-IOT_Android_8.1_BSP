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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.os.Parcel;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.cts.R;
import android.text.style.URLSpan;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class URLSpanTest {
    // The scheme of TEST_URL must be "ctstesttext" to launch MockURLSpanTestActivity
    private static final String TEST_URL = "ctstesttext://urlSpan/test";

    private Activity mActivity;

    @Rule
    public ActivityTestRule<URLSpanCtsActivity> mActivityRule =
            new ActivityTestRule<>(URLSpanCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        URLSpan urlSpan = new URLSpan(TEST_URL);

        final Parcel p = Parcel.obtain();
        try {
            urlSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new URLSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetURL() {
        URLSpan urlSpan = new URLSpan(TEST_URL);
        assertEquals(TEST_URL, urlSpan.getURL());
    }

    @LargeTest
    @Test
    public void testOnClick() throws Throwable {
        final URLSpan urlSpan = new URLSpan(TEST_URL);
        final TextView textView = (TextView) mActivity.findViewById(R.id.url);

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        ActivityMonitor am = instrumentation.addMonitor(MockURLSpanTestActivity.class.getName(),
                null, false);

        mActivityRule.runOnUiThread(() -> urlSpan.onClick(textView));

        Activity newActivity = am.waitForActivityWithTimeout(5000);
        assertNotNull(newActivity);
        newActivity.finish();
    }

    @Test(expected=NullPointerException.class)
    public void testOnClickFailure() {
        URLSpan urlSpan = new URLSpan(TEST_URL);

        urlSpan.onClick(null);
    }

    @Test
    public void testDescribeContents() {
        URLSpan urlSpan = new URLSpan(TEST_URL);
        urlSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        URLSpan urlSpan = new URLSpan(TEST_URL);
        urlSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            URLSpan urlSpan = new URLSpan(TEST_URL);
            urlSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            URLSpan u = new URLSpan(p);
            assertEquals(TEST_URL, u.getURL());
        } finally {
            p.recycle();
        }
    }
}
