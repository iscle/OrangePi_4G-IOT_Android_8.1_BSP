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

package android.content.res.cts;

import org.xmlpull.v1.XmlPullParser;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.util.Xml;
import android.view.View;

import android.content.cts.R;

import java.util.Locale;


public class Resources_ThemeTest extends AndroidTestCase {

    private Resources.Theme mResTheme;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResTheme = getContext().getResources().newTheme();
    }

    @SmallTest
    public void testSetMethods() {
        // call a native method, and have no way to get the style
        mResTheme.applyStyle(R.raw.testmp3, false);
        // call a native method, this method is just for debug to the log
        mResTheme.dump(1, "hello", "world");
        // call a native method
        final Theme other = getContext().getTheme();
        mResTheme.setTo(other);
    }

    @SmallTest
    public void testObtainStyledAttributes() {
        final int[] attrs = new int[1];
        attrs[0] = R.raw.testmp3;

        TypedArray testTypedArray = mResTheme.obtainStyledAttributes(attrs);
        assertNotNull(testTypedArray);
        assertTrue(testTypedArray.length() > 0);
        testTypedArray.recycle();

        testTypedArray = mResTheme.obtainStyledAttributes(R.raw.testmp3, attrs);
        assertNotNull(testTypedArray);
        assertTrue(testTypedArray.length() > 0);
        testTypedArray.recycle();

        XmlPullParser parser = getContext().getResources().getXml(R.xml.colors);
        AttributeSet set = Xml.asAttributeSet(parser);
        attrs[0] = R.xml.colors;
        testTypedArray =mResTheme.obtainStyledAttributes(set, attrs, 0, 0);
        assertNotNull(testTypedArray);
        assertTrue(testTypedArray.length() > 0);
        testTypedArray.recycle();
    }

    @SmallTest
    public void testObtainStyledAttributesWithInlineStringInTheme() {
        mResTheme.applyStyle(R.style.Theme_InlineString, false);
        final TypedArray ta = mResTheme.obtainStyledAttributes(new int[] { R.attr.testString });
        assertNotNull(ta);
        assertEquals(1, ta.length());
        assertEquals(TypedValue.TYPE_STRING, ta.getType(0));
        assertEquals("This is a string", ta.getString(0));
    }

    @SmallTest
    public void testResolveAttribute() {
        final TypedValue value = new TypedValue();
        getContext().getResources().getValue(R.raw.testmp3, value, true);
        assertFalse(mResTheme.resolveAttribute(R.raw.testmp3, value, false));
    }

    @SmallTest
    public void testGetChangingConfigurations() {
        Resources.Theme theme = getContext().getResources().newTheme();
        assertEquals("Initial changing configuration mask is empty",
                0, theme.getChangingConfigurations());

        theme.applyStyle(R.style.Theme_OrientationDependent, true);
        assertEquals("First call to Theme.applyStyle() sets changing configuration",
                ActivityInfo.CONFIG_ORIENTATION, theme.getChangingConfigurations());

        theme.applyStyle(R.style.Theme_LayoutDirectionDependent, true);
        assertEquals("Multiple calls to Theme.applyStyle() update changing configuration",
                ActivityInfo.CONFIG_ORIENTATION | ActivityInfo.CONFIG_LAYOUT_DIRECTION,
                theme.getChangingConfigurations());

        Resources.Theme other = getContext().getResources().newTheme();
        other.setTo(theme);
        assertEquals("Theme.setTheme() copies changing confguration",
                ActivityInfo.CONFIG_ORIENTATION | ActivityInfo.CONFIG_LAYOUT_DIRECTION,
                theme.getChangingConfigurations());
    }

    @SmallTest
    public void testRebase() {
        Resources res = getContext().getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(Locale.ENGLISH);
        res.updateConfiguration(config, null);

        assertEquals("Theme will be created in LTR config",
                View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());

        Resources.Theme theme = res.newTheme();
        theme.applyStyle(R.style.Theme_LayoutIsRTL, true);

        TypedArray t = theme.obtainStyledAttributes(new int[] { R.attr.themeBoolean });
        assertEquals("Theme was created in LTR config", false, t.getBoolean(0, true));
        t.recycle();

        config.setLocale(new Locale("iw"));
        res.updateConfiguration(config, null);

        assertEquals("Theme will be rebased in RTL config",
                View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());

        theme.rebase();

        t = theme.obtainStyledAttributes(new int[] { R.attr.themeBoolean });
        assertEquals("Theme was rebased in RTL config", true, t.getBoolean(0, false));
        t.recycle();
    }

    @SmallTest
    public void testGetDrawable() {
        final Resources res = getContext().getResources();
        final Theme theme = res.newTheme();
        theme.applyStyle(R.style.Theme_ThemedDrawableTest, true);

        final ColorDrawable dr = (ColorDrawable) theme.getDrawable(R.drawable.colordrawable_themed);
        assertEquals(Color.BLACK, dr.getColor());
    }

    @SmallTest
    public void testGetResources() {
        final Resources res = getContext().getResources();
        final Theme theme = res.newTheme();
        assertSame(res, theme.getResources());
    }

    @SmallTest
    public void testEmptyDoesNotGetOverriden() {
        final Resources res = getContext().getResources();
        final Theme theme = res.newTheme();

        theme.applyStyle(R.style.Theme_Empty, false /*force*/);

        final TypedValue tv = new TypedValue();
        assertTrue(theme.resolveAttribute(R.attr.type1, tv, false));
        assertEquals(TypedValue.TYPE_NULL, tv.type);
        assertEquals(TypedValue.DATA_NULL_EMPTY, tv.data);

        // @empty is treated just like a regular value. No override unless forced.
        theme.applyStyle(R.style.Whatever, false /*force*/);
        assertTrue(theme.resolveAttribute(R.attr.type1, tv, false));
        assertEquals(TypedValue.TYPE_NULL, tv.type);
        assertEquals(TypedValue.DATA_NULL_EMPTY, tv.data);

        // Force the override now.
        theme.applyStyle(R.style.Whatever, true /*force*/);
        assertTrue(theme.resolveAttribute(R.attr.type1, tv, false));
        assertNotSame(TypedValue.TYPE_NULL, tv.type);
    }
}
