/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.content.res.cts;

import java.io.IOException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.os.Parcel;
import android.test.AndroidTestCase;

import android.content.cts.R;
import android.test.suitebuilder.annotation.SmallTest;

public class ColorStateListTest extends AndroidTestCase {

    @SmallTest
    public void testConstructor() {
        final int[][] state = new int[][]{{0}, {0}};
        final int[] colors = new int[]{Color.RED, Color.BLUE};
        final ColorStateList c = new ColorStateList(state, colors);
        assertTrue(c.isStateful());
        assertEquals(Color.RED, c.getDefaultColor());
    }

    @SmallTest
    public void testCreateFromXml() throws Exception {
        final int xmlId = R.color.testcolor;
        final int colorInXml = 0xFFA6C839; // this color value is defined in testcolor.xml file.
        final Resources res = getContext().getResources();
        final ColorStateList c = ColorStateList.createFromXml(res, res.getXml(xmlId));
        assertEquals(colorInXml, c.getDefaultColor());
        assertEquals(0, c.describeContents());
        assertFalse(c.isStateful());
        assertNotNull(c.toString());
        assertEquals(colorInXml, c.getColorForState(new int[]{0}, 0));
    }

    @SmallTest
    public void testCreateFromXmlThemed() throws Exception {
        final int xmlId = R.color.testcolor_themed;
        final int colorInXml = Color.BLACK; // this color value is defined in styles.xml file.
        final Resources res = getContext().getResources();
        final Theme theme = res.newTheme();
        theme.applyStyle(R.style.Theme_ThemedDrawableTest, true);
        final ColorStateList c = ColorStateList.createFromXml(res, res.getXml(xmlId), theme);
        assertEquals(colorInXml, c.getDefaultColor());
        assertEquals(0, c.describeContents());
        assertFalse(c.isStateful());
        assertNotNull(c.toString());
        assertEquals(colorInXml, c.getColorForState(new int[]{0}, 0));
    }

    @SmallTest
    public void testGetChangingConfigurations() {
        final Resources res = getContext().getResources();
        ColorStateList c;

        c = res.getColorStateList(R.color.testcolor, null);
        assertEquals(c.getChangingConfigurations(), 0);

        c = res.getColorStateList(R.color.testcolor_orientation, null);
        assertEquals(ActivityInfo.CONFIG_ORIENTATION, c.getChangingConfigurations());
    }

    @SmallTest
    public void testWithAlpha() {
        final int[][] state = new int[][]{{0}, {0}};
        final int[] colors = new int[]{Color.RED, Color.BLUE};
        final ColorStateList c = new ColorStateList(state, colors);
        final int alpha = 36;
        final ColorStateList c1 = c.withAlpha(alpha);
        assertNotSame(Color.RED, c1.getDefaultColor());
        assertEquals(alpha, c1.getDefaultColor() >>> 24);
        assertEquals(Color.RED & 0x00FF0000, c1.getDefaultColor() & 0x00FF0000);
    }

    @SmallTest
    public void testValueOf() {
        final ColorStateList c = ColorStateList.valueOf(Color.GRAY);
        assertEquals(Color.GRAY, c.getDefaultColor());
    }

    @SmallTest
    public void testParcelable() {
        final ColorStateList c = ColorStateList.valueOf(Color.GRAY);
        final Parcel parcel = Parcel.obtain();
        c.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        final ColorStateList actual = ColorStateList.CREATOR.createFromParcel(parcel);
        assertEquals(c.isStateful(), actual.isStateful());
        assertEquals(c.getDefaultColor(), actual.getDefaultColor());
    }

    @SmallTest
    public void testIsOpaque() {
        ColorStateList c;

        c = ColorStateList.valueOf(Color.GRAY);
        assertTrue(c.isOpaque());

        c = ColorStateList.valueOf(0x80FFFFFF);
        assertFalse(c.isOpaque());

        c = ColorStateList.valueOf(Color.TRANSPARENT);
        assertFalse(c.isOpaque());
    }
}
