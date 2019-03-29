/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.os.cts;

import android.os.LocaleList;
import android.os.Parcel;
import android.test.AndroidTestCase;

import java.util.Locale;

public class LocaleListTest extends AndroidTestCase {
    public void testEmptyLocaleList() {
        LocaleList ll = new LocaleList();
        assertNotNull(ll);
        assertTrue(ll.isEmpty());
        assertEquals(0, ll.size());
        assertNull(ll.get(0));
        assertNull(ll.get(1));
        assertNull(ll.get(10));

        ll = new LocaleList(new Locale[0]);
        assertNotNull(ll);
        assertTrue(ll.isEmpty());
        assertEquals(0, ll.size());
        assertNull(ll.get(0));
        assertNull(ll.get(1));
        assertNull(ll.get(10));
    }

    public void testOneMemberLocaleList() {
        final LocaleList ll = new LocaleList(Locale.US);
        assertNotNull(ll);
        assertFalse(ll.isEmpty());
        assertEquals(1, ll.size());
        assertEquals(Locale.US, ll.get(0));
        assertNull(ll.get(10));
    }

    public void testTwoMemberLocaleList() {
        final Locale enPH = Locale.forLanguageTag("en-PH");
        final Locale[] la = {enPH, Locale.US};
        final LocaleList ll = new LocaleList(la);
        assertNotNull(ll);
        assertFalse(ll.isEmpty());
        assertEquals(2, ll.size());
        assertEquals(enPH, ll.get(0));
        assertEquals(Locale.US, ll.get(1));
        assertNull(ll.get(10));
    }

    public void testNullArgument() {
        try {
            LocaleList ll = new LocaleList((Locale) null);
            fail("Initializing a LocaleList with a null argument should throw.");
        } catch (Throwable e) {
            assertEquals(NullPointerException.class, e.getClass());
        }
        try {
            LocaleList ll = new LocaleList((Locale[]) null);
            fail("Initializing a LocaleList with a null array should throw.");
        } catch (Throwable e) {
            assertEquals(NullPointerException.class, e.getClass());
        }
    }

    public void testNullArguments() {
        final Locale[] la = {Locale.US, null};
        LocaleList ll = null;
        try {
            ll = new LocaleList(la);
            fail("Initializing a LocaleList with an array containing null should throw.");
        } catch (Throwable e) {
            assertEquals(NullPointerException.class, e.getClass());
        }
    }

    public void testRepeatedArguments() {
        final Locale[] la = {Locale.US, Locale.US};
        LocaleList ll = null;
        try {
            ll = new LocaleList(la);
            fail("Initializing a LocaleList with an array containing duplicates should throw.");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    public void testIndexOf() {
        final LocaleList empty = new LocaleList();
        assertEquals(-1, empty.indexOf(Locale.US));

        final LocaleList oneMember = new LocaleList(Locale.US);
        assertEquals(0, oneMember.indexOf(Locale.US));
        assertEquals(-1, oneMember.indexOf(Locale.ENGLISH));

        final LocaleList twoMember = LocaleList.forLanguageTags("en,fr");
        assertEquals(0, twoMember.indexOf(Locale.forLanguageTag("en")));
        assertEquals(1, twoMember.indexOf(Locale.forLanguageTag("fr")));
        assertEquals(-1, twoMember.indexOf(Locale.forLanguageTag("en-US")));
    }

    public void testEquals() {
        final LocaleList empty = new LocaleList();
        final LocaleList anotherEmpty = new LocaleList();
        LocaleList oneMember = new LocaleList(Locale.US);
        LocaleList sameOneMember = new LocaleList(new Locale("en", "US"));
        LocaleList differentOneMember = new LocaleList(Locale.FRENCH);
        Locale[] la = {Locale.US, Locale.FRENCH};
        LocaleList twoMember = new LocaleList(la);

        assertFalse(empty.equals(null));
        assertFalse(oneMember.equals(null));

        assertFalse(empty.equals(new Object()));

        assertTrue(empty.equals(empty));
        assertTrue(oneMember.equals(oneMember));

        assertFalse(empty.equals(oneMember));
        assertFalse(oneMember.equals(twoMember));

        assertFalse(oneMember.equals(differentOneMember));

        assertTrue(empty.equals(anotherEmpty));
        assertTrue(oneMember.equals(sameOneMember));
    }

    public void testHashCode() {
        final LocaleList empty = new LocaleList();
        final LocaleList anotherEmpty = new LocaleList();
        Locale[] la1 = {Locale.US};
        LocaleList oneMember = new LocaleList(la1);
        LocaleList sameOneMember = new LocaleList(la1);

        assertEquals(empty.hashCode(), anotherEmpty.hashCode());
        assertEquals(oneMember.hashCode(), sameOneMember.hashCode());
    }

    public void testToString() {
        LocaleList ll = new LocaleList();
        assertEquals("[]", ll.toString());

        final Locale[] la1 = {Locale.US};
        ll = new LocaleList(la1);
        assertEquals("["+Locale.US.toString()+"]", ll.toString());

        final Locale[] la2 = {Locale.US, Locale.FRENCH};
        ll = new LocaleList(la2);
        assertEquals("["+Locale.US.toString()+","+Locale.FRENCH.toString()+"]", ll.toString());
    }

    public void testToLanguageTags() {
        LocaleList ll = new LocaleList();
        assertEquals("", ll.toLanguageTags());

        final Locale[] la1 = {Locale.US};
        ll = new LocaleList(la1);
        assertEquals(Locale.US.toLanguageTag(), ll.toLanguageTags());

        final Locale[] la2 = {Locale.US, Locale.FRENCH};
        ll = new LocaleList(la2);
        assertEquals(Locale.US.toLanguageTag()+","+Locale.FRENCH.toLanguageTag(),
                ll.toLanguageTags());
    }

    public void testGetEmptyLocaleList() {
        LocaleList empty = LocaleList.getEmptyLocaleList();
        LocaleList anotherEmpty = LocaleList.getEmptyLocaleList();
        LocaleList constructedEmpty = new LocaleList();

        assertEquals(constructedEmpty, empty);
        assertSame(empty, anotherEmpty);
    }

    public void testForLanguageTags() {
        assertEquals(LocaleList.getEmptyLocaleList(), LocaleList.forLanguageTags(null));
        assertEquals(LocaleList.getEmptyLocaleList(), LocaleList.forLanguageTags(""));

        assertEquals(new LocaleList(Locale.forLanguageTag("en-US")),
                LocaleList.forLanguageTags("en-US"));

        final Locale[] la = {Locale.forLanguageTag("en-PH"), Locale.forLanguageTag("en-US")};
        assertEquals(new LocaleList(la), LocaleList.forLanguageTags("en-PH,en-US"));
    }

    public void testGetDefault() {
        final LocaleList ll = LocaleList.getDefault();
        assertNotNull(ll);
        assertTrue(ll.size() >= 1);

        final Locale defaultLocale = Locale.getDefault();
        assertTrue(ll.indexOf(defaultLocale) != -1);
    }

    public void testGetAdjustedDefault() {
        final LocaleList ll = LocaleList.getDefault();
        assertNotNull(ll);
        assertTrue(ll.size() >= 1);

        final Locale defaultLocale = Locale.getDefault();
        assertTrue(ll.indexOf(defaultLocale) == 0);
    }

    public void testGetDefault_localeSetDefaultCalled() {
        final Locale originalLocale = Locale.getDefault();
        final LocaleList originalLocaleList = LocaleList.getDefault();

        LocaleList.setDefault(LocaleList.forLanguageTags("ae,en,ja"));

        try {
            Locale.setDefault(Locale.forLanguageTag("fr"));
            assertEquals("fr,ae,en,ja", LocaleList.getDefault().toLanguageTags());
            assertEquals("fr,ae,en,ja", LocaleList.getAdjustedDefault().toLanguageTags());

            Locale.setDefault(Locale.forLanguageTag("de"));
            assertEquals("de,ae,en,ja", LocaleList.getDefault().toLanguageTags());
            assertEquals("de,ae,en,ja", LocaleList.getAdjustedDefault().toLanguageTags());

            Locale.setDefault(Locale.forLanguageTag("en"));
            assertEquals("en,ae,ja", LocaleList.getDefault().toLanguageTags());
            assertEquals("en,ae,ja", LocaleList.getAdjustedDefault().toLanguageTags());

            Locale.setDefault(Locale.forLanguageTag("es"));
            assertEquals("es,ae,en,ja", LocaleList.getDefault().toLanguageTags());
            assertEquals("es,ae,en,ja", LocaleList.getAdjustedDefault().toLanguageTags());

            Locale.setDefault(Locale.forLanguageTag("ja"));
            assertEquals("ja,ae,en", LocaleList.getDefault().toLanguageTags());
            assertEquals("ja,ae,en", LocaleList.getAdjustedDefault().toLanguageTags());

            Locale.setDefault(Locale.forLanguageTag("ae"));
            assertEquals("ae,en,ja", LocaleList.getDefault().toLanguageTags());
            assertEquals("ae,en,ja", LocaleList.getAdjustedDefault().toLanguageTags());
        } finally {
            // restore the original values
            LocaleList.setDefault(originalLocaleList, originalLocaleList.indexOf(originalLocale));
        }
    }

    public void testSetDefault_null() {
        try {
            LocaleList.setDefault(null);
            fail("Setting the default locale list to null should throw.");
        } catch (Throwable e) {
            assertEquals(NullPointerException.class, e.getClass());
        }
    }

    public void testSetDefault_empty() {
        try {
            LocaleList.setDefault(LocaleList.getEmptyLocaleList());
            fail("Setting the default locale list to an empty list should throw.");
        } catch (Throwable e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }

    public void testSetDefault_oneLocale() {
        final Locale originalLocale = Locale.getDefault();
        final LocaleList originalLocaleList = LocaleList.getDefault();

        final LocaleList locales = LocaleList.forLanguageTags("ae");
        LocaleList.setDefault(locales);
        try {
            assertEquals("ae", Locale.getDefault().toLanguageTag());
            assertEquals(locales, LocaleList.getDefault());
            assertEquals(locales, LocaleList.getAdjustedDefault());
        } finally {
            // restore the original values
            LocaleList.setDefault(originalLocaleList, originalLocaleList.indexOf(originalLocale));
        }
    }

    public void testSetDefault_twoLocales() {
        final Locale originalLocale = Locale.getDefault();
        final LocaleList originalLocaleList = LocaleList.getDefault();

        final LocaleList locales = LocaleList.forLanguageTags("ae,en");
        LocaleList.setDefault(locales);
        try {
            assertEquals("ae", Locale.getDefault().toLanguageTag());
            assertEquals(locales, LocaleList.getDefault());
            assertEquals(locales, LocaleList.getAdjustedDefault());
        } finally {
            // restore the original values
            LocaleList.setDefault(originalLocaleList, originalLocaleList.indexOf(originalLocale));
        }
    }

    public void testParcelable() {
        // Make sure an empty LocaleList can be marshalled/unmarshalled via Parcel.
        assertEquals(LocaleList.getEmptyLocaleList(),
                cloneViaParcel(LocaleList.getEmptyLocaleList()));

        // Make sure a non-empty LocaleList can be marshalled/unmarshalled via Parcel.
        LocaleList original = LocaleList.forLanguageTags("en-PH,en-US");
        assertEquals(original, cloneViaParcel(original));
    }

    public void testDescribeContents_doesNotThrowException() {
        // Just check calling describeContents() should not cause any exceptions.
        LocaleList.forLanguageTags("en").describeContents();
        LocaleList.forLanguageTags("").describeContents();
        LocaleList.forLanguageTags(null).describeContents();
        LocaleList.getEmptyLocaleList().describeContents();
    }

    private static LocaleList cloneViaParcel(final LocaleList original) {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return LocaleList.CREATOR.createFromParcel(parcel);
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
    }

    public void testGetFirstMatch_noAssets() {
        String[] noAssets = {};
        assertNull(LocaleList.getEmptyLocaleList().getFirstMatch(noAssets));
        assertEquals(
                Locale.forLanguageTag("fr-BE"),
                LocaleList.forLanguageTags("fr-BE").getFirstMatch(noAssets));
        assertEquals(
                Locale.forLanguageTag("fr-BE"),
                LocaleList.forLanguageTags("fr-BE,nl-BE").getFirstMatch(noAssets));
    }

    public void testGetFirstMatch_oneAsset() {
        String[] oneDutchAsset = {"nl"};
        assertNull(LocaleList.getEmptyLocaleList().getFirstMatch(oneDutchAsset));
        assertEquals(
                Locale.forLanguageTag("fr-BE"),
                LocaleList.forLanguageTags("fr-BE").getFirstMatch(oneDutchAsset));
        assertEquals(
                Locale.forLanguageTag("nl-BE"),
                LocaleList.forLanguageTags("nl-BE").getFirstMatch(oneDutchAsset));
        assertEquals(
                Locale.forLanguageTag("nl-BE"),
                LocaleList.forLanguageTags("fr-BE,nl-BE").getFirstMatch(oneDutchAsset));
        assertEquals(
                Locale.forLanguageTag("nl-BE"),
                LocaleList.forLanguageTags("nl-BE,fr-BE").getFirstMatch(oneDutchAsset));
    }

    public void testGetFirstMatch_twoAssets() {
        String[] FrenchAndDutchAssets = {"fr", "nl"};
        assertNull(LocaleList.getEmptyLocaleList().getFirstMatch(FrenchAndDutchAssets));
        assertEquals(
                Locale.forLanguageTag("fr-BE"),
                LocaleList.forLanguageTags("fr-BE").getFirstMatch(FrenchAndDutchAssets));
        assertEquals(
                Locale.forLanguageTag("nl-BE"),
                LocaleList.forLanguageTags("nl-BE").getFirstMatch(FrenchAndDutchAssets));
        assertEquals(
                Locale.forLanguageTag("fr-BE"),
                LocaleList.forLanguageTags("fr-BE,nl-BE").getFirstMatch(FrenchAndDutchAssets));
        assertEquals(
                Locale.forLanguageTag("nl-BE"),
                LocaleList.forLanguageTags("nl-BE,fr-BE").getFirstMatch(FrenchAndDutchAssets));
    }

    public void testGetFirstMatch_oneChineseAsset() {
        String[] oneChineseAsset = {"zh-CN"};  // Assumed to mean zh-Hans-CN
        // The following Chinese examples would all match, so they will be chosen.
        assertEquals(
                Locale.forLanguageTag("zh"),
                LocaleList.forLanguageTags("ko-KR,zh").getFirstMatch(oneChineseAsset));
        assertEquals(
                Locale.forLanguageTag("zh-CN"),
                LocaleList.forLanguageTags("ko-KR,zh-CN").getFirstMatch(oneChineseAsset));
        assertEquals(
                Locale.forLanguageTag("zh-Hans"),
                LocaleList.forLanguageTags("ko-KR,zh-Hans").getFirstMatch(oneChineseAsset));
        assertEquals(
                Locale.forLanguageTag("zh-Hans-CN"),
                LocaleList.forLanguageTags("ko-KR,zh-Hans-CN").getFirstMatch(oneChineseAsset));
        assertEquals(
                Locale.forLanguageTag("zh-Hans-HK"),
                LocaleList.forLanguageTags("ko-KR,zh-Hans-HK").getFirstMatch(oneChineseAsset));

        // The following Chinese examples wouldn't match, so the first locale will be chosen
        // instead.
        assertEquals(
                Locale.forLanguageTag("ko-KR"),
                LocaleList.forLanguageTags("ko-KR,zh-TW").getFirstMatch(oneChineseAsset));
        assertEquals(
                Locale.forLanguageTag("ko-KR"),
                LocaleList.forLanguageTags("ko-KR,zh-Hant").getFirstMatch(oneChineseAsset));
        assertEquals(
                Locale.forLanguageTag("ko-KR"),
                LocaleList.forLanguageTags("ko-KR,zh-Hant-TW").getFirstMatch(oneChineseAsset));
    }

    public void testGetFirstMatch_serbianCyrillic() {
        String[] oneSerbianAsset = {"sr"};  // Assumed to mean sr-Cyrl-RS
        // The following Serbian examples would all match, so they will be chosen.
        assertEquals(
                Locale.forLanguageTag("sr"),
                LocaleList.forLanguageTags("hr-HR,sr").getFirstMatch(oneSerbianAsset));
        assertEquals(
                Locale.forLanguageTag("sr-RS"),
                LocaleList.forLanguageTags("hr-HR,sr-RS").getFirstMatch(oneSerbianAsset));
        assertEquals(
                Locale.forLanguageTag("sr-Cyrl"),
                LocaleList.forLanguageTags("hr-HR,sr-Cyrl").getFirstMatch(oneSerbianAsset));
        assertEquals(
                Locale.forLanguageTag("sr-Cyrl-RS"),
                LocaleList.forLanguageTags("hr-HR,sr-Cyrl-RS").getFirstMatch(oneSerbianAsset));
        assertEquals(
                Locale.forLanguageTag("sr-Cyrl-ME"),
                LocaleList.forLanguageTags("hr-HR,sr-Cyrl-ME").getFirstMatch(oneSerbianAsset));

        // The following Serbian examples wouldn't match, so the first locale will be chosen
        // instead.
        assertEquals(
                Locale.forLanguageTag("hr-HR"),
                LocaleList.forLanguageTags("hr-HR,sr-ME").getFirstMatch(oneSerbianAsset));
        assertEquals(
                Locale.forLanguageTag("hr-HR"),
                LocaleList.forLanguageTags("hr-HR,sr-Latn").getFirstMatch(oneSerbianAsset));
        assertEquals(
                Locale.forLanguageTag("hr-HR"),
                LocaleList.forLanguageTags("hr-HR,sr-Latn-ME").getFirstMatch(oneSerbianAsset));
    }

    public void testGetFirstMatch_LtrPseudoLocale() {
        String[] onePseudoLocale = {"en-XA"};
        // "en-XA" matches itself
        assertEquals(
                Locale.forLanguageTag("en-XA"),
                LocaleList.forLanguageTags("sr,en-XA").getFirstMatch(onePseudoLocale));

        // "en-XA" doesn't match "en" or "en-US"
        assertEquals(
                Locale.forLanguageTag("sr"),
                LocaleList.forLanguageTags("sr,en").getFirstMatch(onePseudoLocale));
        assertEquals(
                Locale.forLanguageTag("sr"),
                LocaleList.forLanguageTags("sr,en-US").getFirstMatch(onePseudoLocale));
    }

    public void testGetFirstMatch_RtlPseudoLocale() {
        String[] onePseudoLocale = {"ar-XB"};
        // "ar-XB" matches itself
        assertEquals(
                Locale.forLanguageTag("ar-XB"),
                LocaleList.forLanguageTags("sr,ar-XB").getFirstMatch(onePseudoLocale));

        // "ar-XB" doesn't match "ar" or "ar-EG"
        assertEquals(
                Locale.forLanguageTag("sr"),
                LocaleList.forLanguageTags("sr,ar").getFirstMatch(onePseudoLocale));
        assertEquals(
                Locale.forLanguageTag("sr"),
                LocaleList.forLanguageTags("sr,ar-EG").getFirstMatch(onePseudoLocale));
    }

    public void testGetFirstMatch_privateUseWithoutCountry() {
        String[] onePrivateLocale = {"qaa"};
        // "qaa" supports itself and "qaa-CA"
        assertEquals(
                Locale.forLanguageTag("qaa"),
                LocaleList.forLanguageTags("sr,qaa").getFirstMatch(onePrivateLocale));
        assertEquals(
                Locale.forLanguageTag("qaa-CA"),
                LocaleList.forLanguageTags("sr,qaa-CA").getFirstMatch(onePrivateLocale));
    }

    public void testGetFirstMatch_privateUseWithCountry() {
        String[] onePrivateLocale = {"qaa-US"};
        // "qaa-US" supports itself
        assertEquals(
                Locale.forLanguageTag("qaa-US"),
                LocaleList.forLanguageTags("sr,qaa-US").getFirstMatch(onePrivateLocale));

        // "qaa-US" doesn't support "qaa" or "qaa-CA"
        assertEquals(
                Locale.forLanguageTag("sr"),
                LocaleList.forLanguageTags("sr,qaa-CA").getFirstMatch(onePrivateLocale));
        assertEquals(
                Locale.forLanguageTag("sr"),
                LocaleList.forLanguageTags("sr,qaa").getFirstMatch(onePrivateLocale));
    }
}
