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
 * limitations under the License.
 */

package com.example.android.effectivenavigation;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isSelected;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

import static org.hamcrest.Matchers.not;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {

    @Rule
    public final ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void checkPreconditions() {
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.pager)).check(matches(isDisplayed()));
        onView(withId(R.id.tabs)).check(matches(isDisplayed()));
        onView(withId(R.id.demo_collection_button)).check(matches(isDisplayed()));
        onView(withId(R.id.demo_external_activity)).check(matches(isDisplayed()));
    }

    @Test
    public void clickTabToChangeSection() {
        onView(withText("Section 2 is just a dummy section.")).check(matches(not(isDisplayed())));
        onView(withText("Section 2")).perform(click()).check(matches(isSelected()));
        onView(withText("Section 2 is just a dummy section.")).check(matches(isDisplayed()));
    }

    @Test
    public void swipeToChangeSection() {
        onView(withText("Section 2 is just a dummy section.")).check(matches(not(isDisplayed())));
        onView(withId(R.id.pager)).perform(swipeLeft());
        onView(withText("Section 2 is just a dummy section.")).check(matches(isDisplayed()));
        onView(withText("Section 2")).check(matches(isSelected()));
    }

    @Test
    public void openAndCloseCollection() {
        onView(withId(R.id.demo_collection_button)).perform(click());
        // We should be in CollectionDemoActivity now
        onView(withText("Demo Collection")).check(matches(isDisplayed()));
        onView(withContentDescription("Navigate up")).perform(click());
        // We should be back in MainActivity now
        onView(withText("Effective Navigation")).check(matches(isDisplayed()));
    }

}
