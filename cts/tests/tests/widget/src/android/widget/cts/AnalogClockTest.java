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

import android.app.Activity;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.AnalogClock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AnalogClockTest {
    private AttributeSet mAttrSet;
    private Activity mActivity;

    @Rule
    public ActivityTestRule<FrameLayoutCtsActivity> mActivityRule =
            new ActivityTestRule<>(FrameLayoutCtsActivity.class);

    @Before
    public void setup() throws Exception {
        mActivity = mActivityRule.getActivity();
        XmlPullParser parser = mActivity.getResources().getXml(R.layout.analogclock);
        mAttrSet = Xml.asAttributeSet(parser);
    }

    @Test
    public void testConstructor() {
        new AnalogClock(mActivity);
        new AnalogClock(mActivity, mAttrSet);
        new AnalogClock(mActivity, mAttrSet, 0);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext1() {
        new AnalogClock(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext2() {
        new AnalogClock(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorWithNullContext3() {
        new AnalogClock(null, null, -1);
    }
}
