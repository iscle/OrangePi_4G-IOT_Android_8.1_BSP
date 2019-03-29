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
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FrameLayout_LayoutParamsTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    private AttributeSet getAttributeSet() throws XmlPullParserException, IOException {
        XmlPullParser parser = mContext.getResources().getLayout(R.layout.framelayout_layout);
        WidgetTestUtils.beginDocument(parser, "LinearLayout");
        return Xml.asAttributeSet(parser);
    }

    @Test
    public void testConstructor() throws XmlPullParserException, IOException {
        AttributeSet attrs = getAttributeSet();

        new LayoutParams(mContext, attrs);
        new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0);
        new LayoutParams(new ViewGroup.LayoutParams(mContext, attrs));
        new LayoutParams(new LayoutParams(mContext, attrs));
        new LayoutParams(new MarginLayoutParams(mContext, attrs));

        new LayoutParams(-1, -1);
        new LayoutParams(-1, -1, -1);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext() {
        new LayoutParams(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullViewGroupParams() {
        new LayoutParams((ViewGroup.LayoutParams) null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullViewGroupMarginParams() {
        new LayoutParams((ViewGroup.MarginLayoutParams) null);
    }

    @Test
    public void testCopyConstructor() {
        FrameLayout.LayoutParams copy;

        final FrameLayout.LayoutParams fllp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        fllp.gravity = Gravity.BOTTOM;
        fllp.leftMargin = 5;
        fllp.topMargin = 10;
        fllp.rightMargin = 15;
        fllp.bottomMargin = 20;

        copy = new FrameLayout.LayoutParams(fllp);
        assertEquals("Width", fllp.width, copy.width);
        assertEquals("Height", fllp.height, copy.height);
        assertEquals("Gravity", fllp.gravity, copy.gravity);
        assertEquals("Left margin", fllp.leftMargin, copy.leftMargin);
        assertEquals("Top margin", fllp.topMargin, copy.topMargin);
        assertEquals("Right margin", fllp.rightMargin, copy.rightMargin);
        assertEquals("Bottom margin", fllp.bottomMargin, copy.bottomMargin);

        final MarginLayoutParams mlp = new MarginLayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mlp.leftMargin = 5;
        mlp.topMargin = 10;
        mlp.rightMargin = 15;
        mlp.bottomMargin = 20;

        copy = new FrameLayout.LayoutParams(mlp);
        assertEquals("Width", mlp.width, copy.width);
        assertEquals("Height", mlp.height, copy.height);
        assertEquals("Left margin", fllp.leftMargin, copy.leftMargin);
        assertEquals("Top margin", fllp.topMargin, copy.topMargin);
        assertEquals("Right margin", fllp.rightMargin, copy.rightMargin);
        assertEquals("Bottom margin", fllp.bottomMargin, copy.bottomMargin);

        final ViewGroup.LayoutParams vglp = new ViewGroup.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        copy = new FrameLayout.LayoutParams(vglp);
        assertEquals("Width", vglp.width, copy.width);
        assertEquals("Height", vglp.height, copy.height);
    }
}
