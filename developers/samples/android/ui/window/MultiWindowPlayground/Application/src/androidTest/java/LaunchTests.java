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

import com.android.multiwindowplayground.MainActivity;
import com.android.multiwindowplayground.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class LaunchTests {

    @Rule
    public ActivityTestRule<MainActivity> mActivityRule = new ActivityTestRule<>(
            MainActivity.class);


    @Test
    public void testLaunchBasicActivity() {
        // Click the 'start basic activity' button.
        onView(withId(R.id.button_start_basic)).perform(scrollTo(), click());

        // Verify that the description for the basic activity is now displayed.
        onView(withId(R.id.description))
                .check(matches(withText(R.string.activity_description_basic)));
    }

    @Test
    public void testLaunchUnresizableActivity() {
        // Click the 'start unresizable activity' button.
        onView(withId(R.id.start_unresizable)).perform(scrollTo(), click());

        // Verify that the description for the unresizable activity is now displayed.
        onView(withId(R.id.description))
                .check(matches(withText(R.string.activity_description_unresizable)));
    }

    @Test
    public void testLaunchAdjacentActivity() {

        // Click the 'start adjacent activity' button.
        onView(withId(R.id.start_adjacent)).perform(scrollTo(), click());

        // Verify that the correct description is now displayed.
        onView(withId(R.id.description))
                .check(matches(withText(R.string.activity_adjacent_description)));
    }

    @Test
    public void testLaunchCustomConfigurationActivity() {
        // Click the 'start activity that handles configuration changes' button.
        onView(withId(R.id.start_customconfiguration)).perform(scrollTo(), click());

        // Verify that the correct description is now displayed.
        onView(withId(R.id.description))
                .check(matches(withText(R.string.activity_custom_description)));
    }


    @Test
    public void testLaunchMinimumSizeActivity() {
        // Click the 'start activity with minimum size' button.
        onView(withId(R.id.start_minimumsize)).perform(scrollTo(), click());

        // Verify that the correct description is now displayed.
        onView(withId(R.id.description))
                .check(matches(withText(R.string.activity_minimum_description)));
    }

    @Test
    public void testLaunchBoundsActivity() {
        // Click the 'start activity with launch bounds' button.
        onView(withId(R.id.start_launchbounds)).perform(scrollTo(), click());

        // Verify that the correct description is now displayed.
        onView(withId(R.id.description))
                .check(matches(withText(R.string.activity_bounds_description)));
    }


}
