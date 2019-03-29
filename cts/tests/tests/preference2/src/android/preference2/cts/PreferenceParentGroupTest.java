/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.preference2.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.preference.CheckBoxPreference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.UiThreadTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link android.preference.Preference#getParent()} feature.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PreferenceParentGroupTest {

    private PreferencesFromXmlNested mActivity;

    @Rule
    public ActivityTestRule<PreferencesFromXmlNested> mActivityRule =
            new ActivityTestRule<>(PreferencesFromXmlNested.class);


    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    /**
     * Tests that parent PreferenceGroup is correctly assigned and removed when creating preferences
     * from code.
     */
    @Test
    @UiThreadTest
    public void parentViaCodeTest() {
        PreferenceScreen screen = mActivity.getPreferenceScreen();
        assertNull(screen.getParent());

        PreferenceCategory category = new PreferenceCategory(mActivity);
        assertNull(category.getParent());

        CheckBoxPreference pref = new CheckBoxPreference(mActivity);
        assertNull(pref.getParent());

        screen.addPreference(category);
        assertEquals(screen, category.getParent());

        category.addPreference(pref);
        assertEquals(category, pref.getParent());

        screen.removePreference(category);
        assertNull(category.getParent());

        category.removePreference(pref);
        assertNull(pref.getParent());
    }

    /**
     * Tests that parent PreferenceGroup is correctly assigned during inflation and can be modified.
     * To see the tested hierarchy check pref_nested.xml.
     */
    @Test
    @UiThreadTest
    public void parentViaInflationTest() {
        PreferenceScreen screen = mActivity.getPreferenceScreen();

        PreferenceCategory category = (PreferenceCategory) screen.findPreference("pref_category");
        assertNotNull(category);

        PreferenceScreen screenInner =
                (PreferenceScreen) screen.findPreference("pref_screen_inner");
        assertNotNull(screenInner);

        CheckBoxPreference pref = (CheckBoxPreference) screen.findPreference("pref_checkbox");
        assertNotNull(pref);

        // Validate parents
        assertEquals(screen, category.getParent());
        assertEquals(category, screenInner.getParent());
        assertEquals(screenInner, pref.getParent());

        // Remove and validate
        pref.getParent().removePreference(pref);
        assertNull(pref.getParent());
        assertEquals(0, screenInner.getPreferenceCount());
    }

    /**
     * Adds preference into two different groups without removing it first.
     */
    @Test
    @UiThreadTest
    public void parentDoubleAddTest() throws InterruptedException {
        PreferenceScreen screen = mActivity.getPreferenceScreen();

        PreferenceCategory category = new PreferenceCategory(mActivity);
        screen.addPreference(category);

        PreferenceCategory category2 = new PreferenceCategory(mActivity);
        screen.addPreference(category2);

        CheckBoxPreference pref = new CheckBoxPreference(mActivity);
        assertNull(pref.getParent());

        category.addPreference(pref);
        category2.addPreference(pref);

        assertEquals(category2, pref.getParent());
    }
}
