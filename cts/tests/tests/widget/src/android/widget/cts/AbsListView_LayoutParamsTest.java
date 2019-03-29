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

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.AbsListView;
import android.widget.AbsListView.LayoutParams;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AbsListView_LayoutParamsTest {
    private Context mContext;
    private AttributeSet mAttributeSet;

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        XmlPullParser parser = mContext.getResources().getXml(R.layout.abslistview_layout);
        WidgetTestUtils.beginDocument(parser, "ViewGroup_Layout");
        mAttributeSet = Xml.asAttributeSet(parser);
    }

    @Test
    public void testConstructors() {
        int TEST_WIDTH = 25;
        int TEST_HEIGHT = 25;
        int TEST_HEIGHT2 = 30;
        AbsListView.LayoutParams layoutParams;

        layoutParams = new AbsListView.LayoutParams(mContext, mAttributeSet);
        assertEquals(TEST_WIDTH, layoutParams.width);
        assertEquals(TEST_HEIGHT, layoutParams.height);

        layoutParams = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.width);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.height);

        layoutParams = new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT, 0);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.width);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.height);

        AbsListView.LayoutParams tmpParams = new AbsListView.LayoutParams(TEST_WIDTH, TEST_HEIGHT2);
        layoutParams = new AbsListView.LayoutParams(tmpParams);
        assertEquals(TEST_WIDTH, layoutParams.width);
        assertEquals(TEST_HEIGHT2, layoutParams.height);
    }
}
