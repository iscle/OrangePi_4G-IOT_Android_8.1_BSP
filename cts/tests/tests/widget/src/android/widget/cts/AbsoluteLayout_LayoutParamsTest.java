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

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.AbsoluteLayout;
import android.widget.AbsoluteLayout.LayoutParams;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AbsoluteLayout_LayoutParamsTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    private AttributeSet getAttributeSet() throws XmlPullParserException, IOException {
        XmlPullParser parser = mContext.getResources().getLayout(R.layout.absolute_layout);
        WidgetTestUtils.beginDocument(parser, "LinearLayout");
        return Xml.asAttributeSet(parser);
    }

    @Test
    public void testConstructor() throws XmlPullParserException, IOException {
        LayoutParams layoutParams;

        layoutParams = new AbsoluteLayout.LayoutParams(1, 2, 3, 4);
        assertEquals(1, layoutParams.width);
        assertEquals(2, layoutParams.height);
        assertEquals(3, layoutParams.x);
        assertEquals(4, layoutParams.y);

        LayoutParams params = new AbsoluteLayout.LayoutParams(layoutParams);
        assertEquals(1, params.width);
        assertEquals(2, params.height);
        assertEquals(0, params.x);
        assertEquals(0, params.y);

        new AbsoluteLayout.LayoutParams(mContext, getAttributeSet());
    }

    @Test
    public void testDebug() {
        LayoutParams layoutParams = new AbsoluteLayout.LayoutParams(1, 2, 3, 4);
        assertNotNull(layoutParams.debug("test: "));
    }
}
