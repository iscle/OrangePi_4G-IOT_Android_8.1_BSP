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
package com.android.storagemanager.deletionhelper.apptests;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import com.android.storagemanager.R;
import com.android.storagemanager.deletionhelper.DeletionHelperActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeletionHelperActivityTest {

    @Rule
    public ActivityTestRule<DeletionHelperActivity> mActivityRule =
            new ActivityTestRule<>(DeletionHelperActivity.class, true, true);

    @Test
    public void testPhotosPreferenceNotVisible() {
        // Match the apps preference to make sure we don't just pass because we are on the
        // wrong screen
        onView(withText(R.string.deletion_helper_apps_group_title)).check(matches(isDisplayed()));

        // Check that the photos preference does not exist
        onView(withText(R.string.deletion_helper_photos_title)).check(doesNotExist());
    }
}
