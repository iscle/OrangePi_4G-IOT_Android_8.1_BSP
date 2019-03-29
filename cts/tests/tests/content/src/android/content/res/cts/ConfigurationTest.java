/*
 * Copyright (C) 2006 The Android Open Source Project
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

import java.util.Locale;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.view.View;

public class ConfigurationTest extends AndroidTestCase {

    private Configuration mConfigDefault;
    private Configuration mConfig;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mConfigDefault = new Configuration();
        mConfigDefault.setToDefaults();
        makeConfiguration();
    }

    private void makeConfiguration() {
        mConfig = new Configuration();
        mConfig.fontScale = 2;
        mConfig.mcc = mConfig.mnc = 1;
        mConfig.setLocale(Locale.getDefault());
        mConfig.touchscreen = Configuration.TOUCHSCREEN_NOTOUCH;
        mConfig.keyboard = Configuration.KEYBOARD_NOKEYS;
        mConfig.keyboardHidden = Configuration.KEYBOARDHIDDEN_NO;
        mConfig.navigation = Configuration.NAVIGATION_NONAV;
        mConfig.orientation = Configuration.ORIENTATION_PORTRAIT;
    }

    public void testConstructor() {
        new Configuration();
        new Configuration(mConfigDefault);
    }

    public void testCompareTo() {
        final Configuration cfg1 = new Configuration();
        final Configuration cfg2 = new Configuration();
        assertEquals(0, cfg1.compareTo(cfg2));

        cfg1.colorMode = 2;
        cfg2.colorMode = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.colorMode = 3;
        cfg2.colorMode = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.orientation = 2;
        cfg2.orientation = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.orientation = 3;
        cfg2.orientation = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.navigation = 2;
        cfg2.navigation = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.navigation = 3;
        cfg2.navigation = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.keyboardHidden = 2;
        cfg2.keyboardHidden = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.keyboardHidden = 3;
        cfg2.keyboardHidden = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.keyboard = 2;
        cfg2.keyboard = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.keyboard = 3;
        cfg2.keyboard = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.touchscreen = 2;
        cfg2.touchscreen = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.touchscreen = 3;
        cfg2.touchscreen = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.setLocales(LocaleList.forLanguageTags("fr"));
        cfg2.setLocales(LocaleList.forLanguageTags("fr,en"));
        assertTrue(cfg1.compareTo(cfg2) < 0);
        cfg1.setLocales(LocaleList.forLanguageTags("fr,en"));
        cfg2.setLocales(LocaleList.forLanguageTags("fr"));
        assertTrue(cfg1.compareTo(cfg2) > 0);

        cfg1.setLocales(LocaleList.forLanguageTags("fr,en"));
        cfg2.setLocales(LocaleList.forLanguageTags("fr,en-US"));
        assertTrue(cfg1.compareTo(cfg2) < 0);
        cfg1.setLocales(LocaleList.forLanguageTags("fr,en-US"));
        cfg2.setLocales(LocaleList.forLanguageTags("fr,en"));
        assertTrue(cfg1.compareTo(cfg2) > 0);

        cfg1.locale = Locale.forLanguageTag("en");
        cfg2.locale = Locale.forLanguageTag("en-Shaw");
        assertTrue(cfg1.compareTo(cfg2) < 0);
        cfg1.locale = Locale.forLanguageTag("en-Shaw");
        cfg2.locale = Locale.forLanguageTag("en");
        assertTrue(cfg1.compareTo(cfg2) > 0);

        cfg1.locale = new Locale("", "", "2");
        cfg2.locale = new Locale("", "", "3");
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.locale = new Locale("", "", "3");
        cfg2.locale = new Locale("", "", "2");
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.locale = new Locale("", "2", "");
        cfg2.locale = new Locale("", "3", "");
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.locale = new Locale("", "3", "");
        cfg2.locale = new Locale("", "2", "");
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.locale = new Locale("2", "", "");
        cfg2.locale = new Locale("3", "", "");
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.locale = new Locale("3", "", "");
        cfg2.locale = new Locale("2", "", "");
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.locale = new Locale("");
        cfg2.locale = null;
        assertTrue(cfg1.compareTo(cfg2) < 0);
        cfg1.locale = null;
        cfg2.locale = new Locale("");
        assertTrue(cfg1.compareTo(cfg2) > 0);

        cfg1.mnc = 2;
        cfg2.mnc = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.mnc = 3;
        cfg2.mnc = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.mcc = 2;
        cfg2.mcc = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.mcc = 3;
        cfg2.mcc = 2;
        assertEquals(1, cfg1.compareTo(cfg2));

        cfg1.fontScale = 2;
        cfg2.fontScale = 3;
        assertEquals(-1, cfg1.compareTo(cfg2));
        cfg1.fontScale = 3;
        cfg2.fontScale = 2;
        assertEquals(1, cfg1.compareTo(cfg2));
    }

    public void testDescribeContents() {
        assertEquals(0, mConfigDefault.describeContents());
    }

    private static void doConfigCompare(int expectedFlags, Configuration c1, Configuration c2) {
        assertEquals(expectedFlags, c1.diff(c2));
        Configuration tmpc1 = new Configuration(c1);
        assertEquals(expectedFlags, tmpc1.updateFrom(c2));
        assertEquals(0, tmpc1.diff(c2));
    }
    
    public void testDiff() {
        Configuration config = new Configuration();
        config.mcc = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC, mConfigDefault, config);
        config.mnc = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC, mConfigDefault, config);
        config.locale = Locale.getDefault();
        config.setLayoutDirection(config.locale);
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION, mConfigDefault, config);
        config.setLocales(LocaleList.forLanguageTags("fr,en"));
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION, mConfigDefault, config);
        config.screenLayout = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT, mConfigDefault, config);
        config.touchscreen = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN, mConfigDefault, config);
        config.keyboard = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD, mConfigDefault, config);
        config.keyboardHidden = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN, mConfigDefault, config);
        config.keyboardHidden = 0;
        config.hardKeyboardHidden = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN, mConfigDefault, config);
        config.hardKeyboardHidden = 0;
        config.navigationHidden = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN, mConfigDefault, config);
        config.navigation = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
                | ActivityInfo.CONFIG_NAVIGATION, mConfigDefault, config);
        config.orientation = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
                | ActivityInfo.CONFIG_NAVIGATION
                | ActivityInfo.CONFIG_ORIENTATION, mConfigDefault, config);
        config.uiMode = 1;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
                | ActivityInfo.CONFIG_NAVIGATION
                | ActivityInfo.CONFIG_ORIENTATION
                | ActivityInfo.CONFIG_UI_MODE, mConfigDefault, config);
        config.fontScale = 2;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
                | ActivityInfo.CONFIG_NAVIGATION
                | ActivityInfo.CONFIG_ORIENTATION
                | ActivityInfo.CONFIG_UI_MODE
                | ActivityInfo.CONFIG_FONT_SCALE, mConfigDefault, config);
        config.colorMode = 2;
        doConfigCompare(ActivityInfo.CONFIG_MCC
                | ActivityInfo.CONFIG_MNC
                | ActivityInfo.CONFIG_LOCALE
                | ActivityInfo.CONFIG_LAYOUT_DIRECTION
                | ActivityInfo.CONFIG_SCREEN_LAYOUT
                | ActivityInfo.CONFIG_TOUCHSCREEN
                | ActivityInfo.CONFIG_KEYBOARD
                | ActivityInfo.CONFIG_KEYBOARD_HIDDEN
                | ActivityInfo.CONFIG_NAVIGATION
                | ActivityInfo.CONFIG_ORIENTATION
                | ActivityInfo.CONFIG_UI_MODE
                | ActivityInfo.CONFIG_FONT_SCALE
                | ActivityInfo.CONFIG_COLOR_MODE, mConfigDefault, config);
    }

    public void testEquals() {
        assertFalse(mConfigDefault.equals(mConfig));
        assertFalse(mConfigDefault.equals(new Object()));
    }

    public void testHashCode() {
        assertFalse(mConfigDefault.hashCode() == mConfig.hashCode());
    }

    public void testNeedNewResources() {
        assertTrue(Configuration.needNewResources(ActivityInfo.CONFIG_MCC,
                ActivityInfo.CONFIG_MCC));
        assertFalse(Configuration.needNewResources(ActivityInfo.CONFIG_MNC,
                ActivityInfo.CONFIG_MCC));
        assertTrue(Configuration.needNewResources(
                ActivityInfo.CONFIG_MNC|ActivityInfo.CONFIG_FONT_SCALE,
                ActivityInfo.CONFIG_MCC));
    }

    public void testSetToDefaults() {
        final Configuration config = new Configuration(mConfig);
        assertFalse(config.equals(mConfigDefault));

        config.setToDefaults();
        assertTrue(config.equals(mConfigDefault));

        assertEquals(1.0f, config.fontScale);
        assertEquals(0, config.mcc);
        assertEquals(0, config.mnc);
        assertTrue(config.getLocales().isEmpty());
        assertEquals(null, config.locale);
        assertFalse(config.userSetLocale);
        assertEquals(Configuration.TOUCHSCREEN_UNDEFINED, config.touchscreen);
        assertEquals(Configuration.KEYBOARD_UNDEFINED, config.keyboard);
        assertEquals(Configuration.KEYBOARDHIDDEN_UNDEFINED, config.keyboardHidden);
        assertEquals(Configuration.HARDKEYBOARDHIDDEN_UNDEFINED, config.hardKeyboardHidden);
        assertEquals(Configuration.NAVIGATION_UNDEFINED, config.navigation);
        assertEquals(Configuration.NAVIGATIONHIDDEN_UNDEFINED, config.navigationHidden);
        assertEquals(Configuration.ORIENTATION_UNDEFINED, config.orientation);
        assertEquals(Configuration.SCREENLAYOUT_UNDEFINED, config.screenLayout);
        assertEquals(Configuration.UI_MODE_TYPE_UNDEFINED, config.uiMode);
        assertEquals(Configuration.SCREEN_WIDTH_DP_UNDEFINED, config.screenWidthDp);
        assertEquals(Configuration.SCREEN_WIDTH_DP_UNDEFINED, config.compatScreenWidthDp);
        assertEquals(Configuration.SCREEN_HEIGHT_DP_UNDEFINED, config.screenHeightDp);
        assertEquals(Configuration.SCREEN_HEIGHT_DP_UNDEFINED, config.compatScreenHeightDp);
        assertEquals(Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED,
                config.smallestScreenWidthDp);
        assertEquals(Configuration.DENSITY_DPI_UNDEFINED, config.densityDpi);
        assertEquals(Configuration.COLOR_MODE_UNDEFINED, config.colorMode);
    }

    public void testUnset() {
        Configuration config = new Configuration();
        assertEquals(0.0f, config.fontScale);
        assertEquals(0, config.mcc);
        assertEquals(0, config.mnc);
        assertTrue(config.getLocales().isEmpty());
        assertEquals(null, config.locale);
        assertFalse(config.userSetLocale);
        assertEquals(Configuration.TOUCHSCREEN_UNDEFINED, config.touchscreen);
        assertEquals(Configuration.KEYBOARD_UNDEFINED, config.keyboard);
        assertEquals(Configuration.KEYBOARDHIDDEN_UNDEFINED, config.keyboardHidden);
        assertEquals(Configuration.HARDKEYBOARDHIDDEN_UNDEFINED, config.hardKeyboardHidden);
        assertEquals(Configuration.NAVIGATION_UNDEFINED, config.navigation);
        assertEquals(Configuration.NAVIGATIONHIDDEN_UNDEFINED, config.navigationHidden);
        assertEquals(Configuration.ORIENTATION_UNDEFINED, config.orientation);
        assertEquals(Configuration.SCREENLAYOUT_UNDEFINED, config.screenLayout);
        assertEquals(Configuration.UI_MODE_TYPE_UNDEFINED, config.uiMode);
        assertEquals(Configuration.SCREEN_WIDTH_DP_UNDEFINED, config.screenWidthDp);
        assertEquals(Configuration.SCREEN_WIDTH_DP_UNDEFINED, config.compatScreenWidthDp);
        assertEquals(Configuration.SCREEN_HEIGHT_DP_UNDEFINED, config.screenHeightDp);
        assertEquals(Configuration.SCREEN_HEIGHT_DP_UNDEFINED, config.compatScreenHeightDp);
        assertEquals(Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED,
                config.smallestScreenWidthDp);
        assertEquals(Configuration.DENSITY_DPI_UNDEFINED, config.densityDpi);
        assertEquals(Configuration.COLOR_MODE_UNDEFINED, config.colorMode);
    }

    public void testToString() {
        assertNotNull(mConfigDefault.toString());
    }

    public void testWriteToParcel() {
        assertWriteToParcel(createConfig((Locale) null), Parcel.obtain());
        assertWriteToParcel(createConfig(new Locale("")), Parcel.obtain());
        assertWriteToParcel(createConfig(Locale.JAPAN), Parcel.obtain());
        assertWriteToParcel(createConfig(Locale.forLanguageTag("en-Shaw")), Parcel.obtain());
        assertWriteToParcel(createConfig(LocaleList.forLanguageTags("fr,en-US")), Parcel.obtain());
    }

    public void testSetLocale() {
        Configuration config = new Configuration();

        config.setLocale(null);
        assertNull(config.locale);
        assertTrue(config.getLocales().isEmpty());

        config.setLocale(Locale.getDefault());
        assertEquals(Locale.getDefault(), config.locale);
        assertEquals(new LocaleList(Locale.getDefault()), config.getLocales());

        config.setLocale(Locale.ENGLISH);
        assertEquals(Locale.ENGLISH, config.locale);
        assertEquals(new LocaleList(Locale.ENGLISH), config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());

        config.setLocale(Locale.US);
        assertEquals(Locale.US, config.locale);
        assertEquals(new LocaleList(Locale.US), config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());

        final Locale arEGLocale = new Locale("ar", "EG");
        config.setLocale(arEGLocale);
        assertEquals(arEGLocale, config.locale);
        assertEquals(new LocaleList(arEGLocale), config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());

        final Locale faIRLocale = new Locale("fa", "IR");
        config.setLocale(faIRLocale);
        assertEquals(faIRLocale, config.locale);
        assertEquals(new LocaleList(faIRLocale), config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());

        final Locale iwILLocale = new Locale("iw", "IL");
        config.setLocale(iwILLocale);
        assertEquals(iwILLocale, config.locale);
        assertEquals(new LocaleList(iwILLocale), config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());

        final Locale urPKLocale = new Locale("ur", "PK");
        config.setLocale(urPKLocale);
        assertEquals(urPKLocale, config.locale);
        assertEquals(new LocaleList(urPKLocale), config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());
    }

    public void testSetGetLayoutDirection() {
        Configuration config = new Configuration();

        config.setLayoutDirection(Locale.ENGLISH);
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());

        config.setLayoutDirection(Locale.US);
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());

        final Locale arEGLocale = new Locale("ar", "EG");
        config.setLayoutDirection(arEGLocale);
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());

        final Locale faIRLocale = new Locale("fa", "IR");
        config.setLayoutDirection(faIRLocale);
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());

        final Locale iwILLocale = new Locale("iw", "IL");
        config.setLayoutDirection(iwILLocale);
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());

        final Locale urPKLocale = new Locale("ur", "PK");
        config.setLayoutDirection(urPKLocale);
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());
    }

    public void testIsScreenRound() {
        Configuration config = new Configuration();
        assertFalse(config.isScreenRound());

        config.screenLayout |= Configuration.SCREENLAYOUT_ROUND_YES;
        assertTrue(config.isScreenRound());
    }

    public void testIsScreenHdr() {
        Configuration config = new Configuration();
        assertFalse(config.isScreenHdr());

        config.colorMode |= Configuration.COLOR_MODE_HDR_YES;
        assertTrue(config.isScreenHdr());
    }

    public void testIsScreenWideColorGamut() {
        Configuration config = new Configuration();
        assertFalse(config.isScreenWideColorGamut());

        config.colorMode |= Configuration.COLOR_MODE_WIDE_COLOR_GAMUT_YES;
        assertTrue(config.isScreenWideColorGamut());
    }

    public void testFixUpLocaleList() {
        Configuration config = new Configuration();

        config.setLocales(LocaleList.forLanguageTags("fr"));
        config.locale = null;
        assertEquals(LocaleList.getEmptyLocaleList(), config.getLocales());

        config.setLocales(LocaleList.forLanguageTags("fr,en"));
        config.locale = Locale.forLanguageTag("en");
        assertEquals(LocaleList.forLanguageTags("en"), config.getLocales());

        config.setLocales(LocaleList.forLanguageTags("fr,en"));
        config.locale = Locale.forLanguageTag("fr");
        assertEquals(LocaleList.forLanguageTags("fr,en"), config.getLocales());
    }

    public void testSetTo_nullLocale() {
        Configuration config1 = new Configuration();
        Configuration config2 = new Configuration();
        assertEquals(null, config2.locale);

        config1.setLocale(Locale.FRENCH);
        config1.setTo(config2);
        assertEquals(null, config1.locale);
    }

    public void testSetTo_localeFixUp() {
        Configuration config1 = new Configuration();
        Configuration config2 = new Configuration();
        config2.locale = Locale.FRENCH;

        config1.setTo(config2);
        assertEquals(Locale.FRENCH, config1.locale);
        assertEquals(new LocaleList(Locale.FRENCH), config1.getLocales());
        assertEquals(new LocaleList(Locale.FRENCH), config2.getLocales());
    }

    public void testToString_localeFixUp() {
        Configuration config1 = new Configuration();
        Configuration config2 = new Configuration();
        config1.setLocales(LocaleList.forLanguageTags("fr,en"));
        config1.locale = Locale.forLanguageTag("en");
        config2.setLocales(LocaleList.forLanguageTags("en"));

        assertEquals(config1.toString(), config2.toString());
    }

    public void testUpdateFrom_localeFixUp() {
        Configuration config1, config2;
        int changed;

        config1 = new Configuration();
        config2 = new Configuration();
        config1.locale = Locale.FRENCH;
        changed = config1.updateFrom(config2);
        assertEquals(0, changed);
        assertEquals(Locale.FRENCH, config1.locale);
        assertEquals(new LocaleList(Locale.FRENCH), config1.getLocales());

        config1 = new Configuration();
        config2 = new Configuration();
        config2.locale = Locale.FRENCH;
        changed = config1.updateFrom(config2);
        assertEquals(ActivityInfo.CONFIG_LOCALE | ActivityInfo.CONFIG_LAYOUT_DIRECTION, changed);
        assertEquals(Locale.FRENCH, config1.locale);
        assertEquals(new LocaleList(Locale.FRENCH), config1.getLocales());
        assertEquals(new LocaleList(Locale.FRENCH), config2.getLocales());

        config1 = new Configuration();
        config2 = new Configuration();
        config1.setLocales(LocaleList.forLanguageTags("en,fr"));
        config1.locale = Locale.forLanguageTag("fr");
        config2.setLocales(LocaleList.forLanguageTags("en,de"));
        config2.locale = Locale.forLanguageTag("fr");
        changed = config1.updateFrom(config2);
        assertEquals(0, changed);
        assertEquals(Locale.forLanguageTag("fr"), config1.locale);
        assertEquals(LocaleList.forLanguageTags("fr"), config1.getLocales());
        assertEquals(LocaleList.forLanguageTags("fr"), config2.getLocales());
    }

    public void testUpdateFrom_layoutDirection() {
        Configuration config1, config2;
        int changed;

        config1 = new Configuration();
        config2 = new Configuration();
        config1.setLocales(LocaleList.forLanguageTags("fr,en"));
        config2.setLocales(LocaleList.forLanguageTags("de,en"));
        changed = config1.updateFrom(config2);
        assertTrue((changed & ActivityInfo.CONFIG_LAYOUT_DIRECTION) != 0);

        config1 = new Configuration();
        config2 = new Configuration();
        config1.setLocales(LocaleList.forLanguageTags("fr,en"));
        config2.setLocales(LocaleList.forLanguageTags("fr,de"));
        changed = config1.updateFrom(config2);
        assertEquals(0, changed & ActivityInfo.CONFIG_LAYOUT_DIRECTION);
    }

    public void testDiff_localeFixUp() {
        Configuration config1 = new Configuration();
        Configuration config2 = new Configuration();
        config1.setLocales(LocaleList.forLanguageTags("en,fr"));
        config1.locale = Locale.forLanguageTag("fr");
        config2.setLocales(LocaleList.forLanguageTags("en,de"));
        config2.locale = Locale.forLanguageTag("fr");

        int diff = config1.diff(config2);
        assertEquals(0, diff);
    }

    public void testCompareTo_localeFixUp() {
        Configuration config1 = new Configuration();
        Configuration config2 = new Configuration();
        config1.setLocales(LocaleList.forLanguageTags("en,fr"));
        config2.setLocales(LocaleList.forLanguageTags("en,fr"));
        assertEquals(0, config1.compareTo(config2));
        config1.locale = new Locale("2");
        config2.locale = new Locale("3");
        assertEquals(-1, config1.compareTo(config2));
    }

    public void testSetLocales_null() {
        Configuration config = new Configuration();
        config.setLocales(null);
        assertNull(config.locale);
        assertNotNull(config.getLocales());
        assertTrue(config.getLocales().isEmpty());
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());
    }

    public void testSetLocales_emptyList() {
        Configuration config = new Configuration();
        config.setLocales(LocaleList.getEmptyLocaleList());
        assertNull(config.locale);
        assertNotNull(config.getLocales());
        assertTrue(config.getLocales().isEmpty());
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());
    }

    public void testSetLocales_oneLtr() {
        Configuration config = new Configuration();
        Locale loc = Locale.forLanguageTag("en");
        LocaleList ll = new LocaleList(loc);
        config.setLocales(ll);
        assertEquals(loc, config.locale);
        assertEquals(ll, config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());
    }

    public void testSetLocales_oneRtl() {
        Configuration config = new Configuration();
        Locale loc = Locale.forLanguageTag("az-Arab");
        LocaleList ll = new LocaleList(loc);
        config.setLocales(ll);
        assertEquals(loc, config.locale);
        assertEquals(ll, config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());
    }

    public void testSetLocales_twoLocales() {
        Configuration config = new Configuration();
        Locale rtlLoc = Locale.forLanguageTag("az-Arab");
        Locale ltrLoc = Locale.forLanguageTag("en");
        LocaleList ll = LocaleList.forLanguageTags("az-Arab,en");
        config.setLocales(ll);
        assertEquals(rtlLoc, config.locale);
        assertEquals(ll, config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());
    }

    public void testSetLocales_overridesLocale() {
        Configuration config = new Configuration();
        config.locale = Locale.forLanguageTag("en");
        LocaleList ll = LocaleList.forLanguageTags("az-Arab,en");
        config.setLocales(ll);

        assertEquals(Locale.forLanguageTag("az-Arab"), config.locale);
        assertEquals(ll, config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());
    }

    public void testSetLocales_overridesSetLocale() {
        Configuration config = new Configuration();
        config.setLocale(Locale.forLanguageTag("en"));
        LocaleList ll = LocaleList.forLanguageTags("az-Arab,en");
        config.setLocales(ll);

        assertEquals(Locale.forLanguageTag("az-Arab"), config.locale);
        assertEquals(ll, config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_RTL, config.getLayoutDirection());
    }

    public void testSetLocale_overridesSetLocales() {
        Configuration config = new Configuration();
        config.setLocales(LocaleList.forLanguageTags("az-Arab,en"));
        config.setLocale(Locale.ENGLISH);

        assertEquals(Locale.ENGLISH, config.locale);
        assertEquals(new LocaleList(Locale.ENGLISH), config.getLocales());
        assertEquals(View.LAYOUT_DIRECTION_LTR, config.getLayoutDirection());
    }

    private Configuration createConfig(LocaleList list) {
        Configuration config = createConfig();
        config.setLocales(list);
        return config;
    }

    private Configuration createConfig(Locale locale) {
        Configuration config = createConfig();
        config.locale = locale;
        return config;
    }

    private Configuration createConfig() {
        Configuration config = new Configuration();
        config.fontScale = 13.37f;
        config.mcc = 0;
        config.mnc = 1;
        config.touchscreen = Configuration.TOUCHSCREEN_STYLUS;
        config.keyboard = Configuration.KEYBOARD_UNDEFINED;
        config.keyboardHidden = Configuration.KEYBOARDHIDDEN_YES;
        config.hardKeyboardHidden = Configuration.KEYBOARDHIDDEN_UNDEFINED;
        config.navigation = Configuration.NAVIGATION_DPAD;
        config.navigationHidden = Configuration.NAVIGATIONHIDDEN_UNDEFINED;
        config.orientation = Configuration.ORIENTATION_PORTRAIT;
        config.screenLayout = Configuration.SCREENLAYOUT_LONG_UNDEFINED;
        return config;
    }

    private void assertWriteToParcel(Configuration config, Parcel parcel) {
        config.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Configuration readConf = new Configuration();
        readConf.readFromParcel(parcel);
        assertEquals(config, readConf);
    }
}
