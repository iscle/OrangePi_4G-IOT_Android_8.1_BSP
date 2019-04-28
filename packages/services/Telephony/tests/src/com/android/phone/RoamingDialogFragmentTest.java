/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.phone;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.support.test.espresso.matcher.PreferenceMatchers;
import android.support.test.rule.ActivityTestRule;
import android.support.test.filters.FlakyTest;
import com.google.common.truth.Truth;
import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isChecked;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

/**
 * Espresso tests to check some properties of the dialog that appears when a user
 * tries to turn on data roaming.
 */
public class RoamingDialogFragmentTest {

    @Rule
    public ActivityTestRule<MobileNetworkSettings> mRule =
            new ActivityTestRule<>(MobileNetworkSettings.class);
    private Activity mActivity;

    /**
     * Make sure roaming is off before we start a test since this checks the dialog that only
     * shows up when we try to turn it on.
     */
    @Before
    public void disableRoaming() {
        mActivity = mRule.getActivity();

        // turn off data roaming if it is on
        try {
            onData(PreferenceMatchers.withTitle(R.string.roaming))
                    .check(matches(hasDescendant(isChecked())))
                    .perform(click());
        } catch (AssertionFailedError e) {
            // don't click the switch if it is already off.
        }
    }

    @FlakyTest
    @Test
    public void dataRoamingDialogPersistsOnRotation() {
        // click on the data roaming preference to trigger warning dialog
        onData(PreferenceMatchers.withTitle(R.string.roaming)).perform(click());

        // request both orientations to ensure at least one rotation occurs
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // verify the title of the dialog is visible
        onView(withText(R.string.roaming_alert_title)).check(matches(isDisplayed()));

    }

    @FlakyTest
    @Test
    public void dataRoamingEnabledWhenPositiveButtonClicked() throws SettingNotFoundException {
        // click on the data roaming preference to trigger warning dialog
        onData(PreferenceMatchers.withTitle(R.string.roaming)).perform(click());

        // click to confirm we want to turn on data roaming
        onView(withId(android.R.id.button1)).perform(click());

        // verify that the the setting has actually been changed
        assertThat(Global.getInt(mActivity.getApplicationContext().getContentResolver(),
                Global.DATA_ROAMING)).isEqualTo(1);
    }

    @FlakyTest
    @Test
    public void dialogDismissedOnNegativeButtonClicked() {
        // click on the data roaming preference to trigger warning dialog
        onData(PreferenceMatchers.withTitle(R.string.roaming)).perform(click());

        // click to cancel turning on data roaming
        onView(withId(android.R.id.button2)).perform(click());

        // verify the title of the dialog is gone
        onView(withText(R.string.roaming_alert_title)).check(doesNotExist());
    }

    @FlakyTest
    @Test
    public void dataRoamingStaysDisabledWhenDialogCanceled() throws SettingNotFoundException {
        // click on the data roaming preference to trigger warning dialog
        onData(PreferenceMatchers.withTitle(R.string.roaming)).perform(click());

        // click to cancel turning on data roaming
        onView(withId(android.R.id.button2)).perform(click());

        // verify that the the setting has not been changed
        assertThat(Global.getInt(mActivity.getApplicationContext().getContentResolver(),
                Global.DATA_ROAMING)).isEqualTo(0);

    }
}
