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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.UiThreadTest;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link android.preference.Preference#setRecycleEnabled()} API.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PreferenceRecycleTest {

    private PreferencesFromXmlRecycle mActivity;

    private static int TIMEOUT_MS = 5000;

    @Rule
    public ActivityTestRule<PreferencesFromXmlRecycle> mActivityRule =
            new ActivityTestRule<>(PreferencesFromXmlRecycle.class);


    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    /**
     * Tests that recycling is enabled as default.
     */
    @Test
    @UiThreadTest
    public void recycleIsOnByDefaultTest() {
        CustomCheckBoxPreference pref = new CustomCheckBoxPreference(mActivity);
        assertTrue(pref.isRecycleEnabled());
    }

    @Test
    @UiThreadTest
    public void recycleSetGetTest() {
        Preference pref = new Preference(mActivity);
        pref.setRecycleEnabled(false);
        assertFalse(pref.isRecycleEnabled());
    }

    /**
     * Tests that recycleEnabled attribute is correctly reflected when defined via XLM.
     */
    @Test
    public void recycleSetViaXmlTest() throws Throwable {
        PreferenceScreen screen = mActivity.getPreferenceScreen();

        RecycleCheckPreference recyclePref =
                (RecycleCheckPreference)screen.findPreference("pref_checkbox_recycle");
        RecycleCheckPreference noRecyclePref =
                (RecycleCheckPreference)screen.findPreference("pref_checkbox_no_recycle");

        // At the beginning the views must be always created (no recycling involved).
        assertEquals(1, recyclePref.getViewCalledCnt);
        assertTrue(recyclePref.wasConvertViewNullInLastCall);

        assertEquals(1, noRecyclePref.getViewCalledCnt);
        assertTrue(noRecyclePref.wasConvertViewNullInLastCall);

        // Change a value of some pref to force the list to refresh
        mActivityRule.runOnUiThread(() -> recyclePref.setChecked(!recyclePref.isChecked()));

        // Wait for the list to refresh
        PollingCheck.waitFor(TIMEOUT_MS,
                () -> recyclePref.getViewCalledCnt == 2 && noRecyclePref.getViewCalledCnt == 2);

        assertEquals(2, recyclePref.getViewCalledCnt);
        assertFalse(recyclePref.wasConvertViewNullInLastCall); // Recycling

        assertEquals(2, noRecyclePref.getViewCalledCnt);
        assertTrue(noRecyclePref.wasConvertViewNullInLastCall); // Not recycling
    }

    /**
     * Tests that recycleEnabled attribute is correctly reflected when defined via
     * {@link android.preference.Preference#setRecycleEnabled}.
     */
    @Test
    public void recycleSetViaCodeTest() throws Throwable {

        final PreferenceScreen screen = mActivity.getPreferenceScreen();

        mActivityRule.runOnUiThread(() -> {
            RecycleCheckPreference recyclePref = new RecycleCheckPreference(mActivity);
            recyclePref.setKey("recyclePref");
            recyclePref.setRecycleEnabled(true);
            screen.addPreference(recyclePref);

            RecycleCheckPreference noRecyclePref = new RecycleCheckPreference(mActivity);
            noRecyclePref.setKey("noRecyclePref");
            noRecyclePref.setRecycleEnabled(false);
            screen.addPreference(noRecyclePref);
        });

        // Select the last item in the list to make sure the newly added prefs is actually
        // displayed even on small screen like watches.
        mActivityRule.runOnUiThread(() -> {
            mActivity.getListView().setSelection(mActivity.getListView().getCount() - 1);
        });

        // Grab the preferences we just created on the Ui thread.
        RecycleCheckPreference recyclePref =
                (RecycleCheckPreference)screen.findPreference("recyclePref");
        RecycleCheckPreference noRecyclePref =
                (RecycleCheckPreference)screen.findPreference("noRecyclePref");

        // Wait for the views to be created (because we may scroll the screen to display the
        // latest views, these views may get refreshed more than once).
        PollingCheck.waitFor(TIMEOUT_MS,
                () -> recyclePref.getViewCalledCnt > 0 && noRecyclePref.getViewCalledCnt > 0);

        // Change a value of some pref to force the list to refresh
        mActivityRule.runOnUiThread(() -> recyclePref.setChecked(!recyclePref.isChecked()));

        // Wait for the list to refresh
        PollingCheck.waitFor(TIMEOUT_MS,
                () -> recyclePref.getViewCalledCnt > 1 && noRecyclePref.getViewCalledCnt > 1);

        assertFalse(recyclePref.wasConvertViewNullInLastCall); // Recycling
        assertTrue(noRecyclePref.wasConvertViewNullInLastCall); // Not recycling
    }
}
