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
import static android.support.test.espresso.matcher.ViewMatchers.isEnabled;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.preference.PreferenceManager;
import android.view.View;

import com.android.emergency.ContactTestUtils;
import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.edit.EditInfoActivity;
import com.android.emergency.edit.EditInfoFragment;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link EmergencyContactsPreference}. */
@RunWith(AndroidJUnit4.class)
public final class EmergencyContactsPreferenceTest {
    private static final String NAME = "Jane";
    private static final String PHONE_NUMBER = "456";

    private Instrumentation mInstrumentation;
    private Context mTargetContext;
    private EmergencyContactsPreference mPreference;

    @BeforeClass
    public static void oneTimeSetup() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();

        // In case a previous test crashed or failed, clear any previous shared preference value.
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().clear().commit();

        // Create a contact that'll be used in each unit test.
        final Uri contactUri = ContactTestUtils.createContact(
                mTargetContext.getContentResolver(), NAME, PHONE_NUMBER);
        PreferenceManager.getDefaultSharedPreferences(mTargetContext)
                .edit().putString(PreferenceKeys.KEY_EMERGENCY_CONTACTS, contactUri.toString())
                .commit();

        mPreference = startActivityAndGetEmergencyContactsPreference();
        mPreference.addNewEmergencyContact(contactUri);
    }

    @After
    public void tearDown() {
        // Clean up the inserted contact
        assertThat(ContactTestUtils.deleteContact(
                mTargetContext.getContentResolver(), NAME, PHONE_NUMBER)).isTrue();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().clear().commit();
    }

    @Test
    public void testWidgetClick_positiveButton() {
        assertThat(mPreference.getEmergencyContacts()).hasSize(1);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);

        onView(withId(R.id.delete_contact)).perform(new RelaxedClick());
        onView(withText(R.string.remove)).perform(click());

        assertThat(mPreference.getEmergencyContacts()).isEmpty();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void testWidgetClick_negativeButton() {
        assertThat(mPreference.getEmergencyContacts()).hasSize(1);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);

        onView(withId(R.id.delete_contact)).perform(new RelaxedClick());
        onView(withText(android.R.string.cancel)).perform(click());

        assertThat(mPreference.getEmergencyContacts()).hasSize(1);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
    }

    private EmergencyContactsPreference startActivityAndGetEmergencyContactsPreference() {
        final Intent editActivityIntent = new Intent(mTargetContext, EditInfoActivity.class);
        EditInfoActivity activity =
                (EditInfoActivity) mInstrumentation.startActivitySync(editActivityIntent);
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();

        return (EmergencyContactsPreference) fragment.findPreference(
                PreferenceKeys.KEY_EMERGENCY_CONTACTS);
    }

    /** ViewAction that allows a click even when the UI is partially obscured. */
    private static class RelaxedClick implements ViewAction {
        @Override
        public Matcher<View> getConstraints() {
            // No constraints, the caller ensures them.
            return isEnabled();
        }

        @Override
        public String getDescription() {
            return "single click, no constraints!";
        }

        @Override
        public void perform(UiController uiController, View view) {
            view.performClick();
        }
    }
}
