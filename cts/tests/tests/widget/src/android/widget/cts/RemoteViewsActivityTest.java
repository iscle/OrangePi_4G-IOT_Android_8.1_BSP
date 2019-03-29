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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Parcel;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.InflateException;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.compatibility.common.util.NullWebViewUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class RemoteViewsActivityTest {
    private static final String PACKAGE_NAME = "android.widget.cts";
    private Activity mActivity;

    @Rule
    public ActivityTestRule<RemoteViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(RemoteViewsCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testGood() {
        RemoteViews orig = new RemoteViews(PACKAGE_NAME, R.layout.remote_view_test_good);
        Parcel p = Parcel.obtain();
        orig.writeToParcel(p, 0);
        p.setDataPosition(0);

        RemoteViews r = RemoteViews.CREATOR.createFromParcel(p);

        ViewGroup parent = (ViewGroup) mActivity.findViewById(R.id.remoteView_host);

        View result = r.apply(mActivity, parent);

        p.recycle();

        assertTrue("LinearLayout not inflated", result.findViewById(R.id.linear) != null);
        assertTrue("TextView not inflated", result.findViewById(R.id.text) != null);
        assertTrue("ImageView not inflated", result.findViewById(R.id.image) != null);
        assertTrue("FrameLayout not inflated", result.findViewById(R.id.frame) != null);
        assertTrue("RelateiveLayout not inflated", result.findViewById(R.id.relative) != null);
        assertTrue("AbsoluteLayout not inflated", result.findViewById(R.id.absolute) != null);
        assertTrue("ProgressBar not inflated", result.findViewById(R.id.progress) != null);
        assertTrue("ImageButton not inflated", result.findViewById(R.id.image_button) != null);
        assertTrue("Button not inflated", result.findViewById(R.id.button) != null);
    }

    @Test
    public void testDerivedClass() {
        RemoteViews orig = new RemoteViews(PACKAGE_NAME, R.layout.remote_view_test_bad_1);
        Parcel p = Parcel.obtain();
        orig.writeToParcel(p, 0);
        p.setDataPosition(0);

        RemoteViews r = RemoteViews.CREATOR.createFromParcel(p);

        ViewGroup parent = (ViewGroup) mActivity.findViewById(R.id.remoteView_host);

        boolean exceptionThrown = false;
        View result = null;

        try {
            result = r.apply(mActivity, parent);
        } catch (InflateException e) {
            exceptionThrown = true;
        }

        p.recycle();

        assertTrue("Derived class (EditText) allowed to be inflated", exceptionThrown);
        assertNull("Derived class (EditText) allowed to be inflated", result);
    }

    @Test
    public void testWebView() {
        if (!NullWebViewUtils.isWebViewAvailable()) {
            return;
        }
        RemoteViews orig = new RemoteViews(PACKAGE_NAME, R.layout.remote_view_test_bad_2);
        Parcel p = Parcel.obtain();
        orig.writeToParcel(p, 0);
        p.setDataPosition(0);

        RemoteViews r = RemoteViews.CREATOR.createFromParcel(p);

        ViewGroup parent = (ViewGroup) mActivity.findViewById(R.id.remoteView_host);

        boolean exceptionThrown = false;
        View result = null;

        try {
            result = r.apply(mActivity, parent);
        } catch (InflateException e) {
            exceptionThrown = true;
        }

        p.recycle();

        assertTrue("WebView allowed to be inflated", exceptionThrown);
        assertNull("WebView allowed to be inflated", result);
    }
}
