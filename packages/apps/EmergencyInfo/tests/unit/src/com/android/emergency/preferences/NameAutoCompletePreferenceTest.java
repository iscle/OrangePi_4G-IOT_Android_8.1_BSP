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
package com.android.emergency.preferences;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.not;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.edit.EditMedicalInfoActivity;
import com.android.emergency.edit.EditMedicalInfoFragment;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link NameAutoCompletePreference}. */
@RunWith(AndroidJUnit4.class)
public final class NameAutoCompletePreferenceTest {
    private NameAutoCompletePreference mNameAutoCompletePreference;

    @BeforeClass
    public static void oneTimeSetup() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        final Intent editActivityIntent = new Intent(
                instrumentation.getTargetContext(), EditMedicalInfoActivity.class);
        EditMedicalInfoActivity activity =
                (EditMedicalInfoActivity) instrumentation.startActivitySync(editActivityIntent);
        EditMedicalInfoFragment fragment = activity.getFragment();

        mNameAutoCompletePreference =
                (NameAutoCompletePreference) fragment.findPreference(PreferenceKeys.KEY_NAME);
    }

    @Test
    public void testDialogShow() {
        onView(withText(R.string.name)).perform(click());

        onView(withText(android.R.string.ok))
                .check(matches(isDisplayed()));
        onView(withText(android.R.string.cancel))
                .check(matches(isDisplayed()));
    }
}
