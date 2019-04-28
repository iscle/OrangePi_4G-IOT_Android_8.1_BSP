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

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.style.TtsSpan;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.edit.EditInfoActivity;
import com.android.emergency.edit.EditInfoFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link EmergencyListPreference}. */
@RunWith(AndroidJUnit4.class)
public final class EmergencyListPreferenceTest {
    private Instrumentation mInstrumentation;
    private Context mTargetContext;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().clear().commit();
    }

    @After
    public void tearDown() {
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().clear().commit();
    }

    @Test
    public void testSummary_organDonor() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        EmergencyListPreference preference =
                getEmergencyListPreference(fragment, PreferenceKeys.KEY_ORGAN_DONOR);
        String summary = (String) preference.getSummary();
        String summaryExp =
                mTargetContext.getResources().getString(R.string.unknown_organ_donor);
        assertThat(summary).isEqualTo(summaryExp);
    }

    @Test
    public void testSummary_bloodType() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        EmergencyListPreference preference =
                getEmergencyListPreference(fragment, PreferenceKeys.KEY_BLOOD_TYPE);
        String summary = preference.getSummary().toString();
        CharSequence summaryExp =
                mTargetContext.getResources().getString(R.string.unknown_blood_type);
        assertThat(summary).isEqualTo(summaryExp);
    }

    @Test
    public void testTitle_organDonor() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        EmergencyListPreference preference =
                getEmergencyListPreference(fragment, PreferenceKeys.KEY_ORGAN_DONOR);
        String title = (String) preference.getTitle();
        String titleExp = mTargetContext.getResources().getString(R.string.organ_donor);
        assertThat(title).isEqualTo(titleExp);
    }

    @Test
    public void testTitle_bloodType() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        EmergencyListPreference preference =
                getEmergencyListPreference(fragment, PreferenceKeys.KEY_BLOOD_TYPE);
        String title = preference.getTitle().toString();
        CharSequence titleExp = mTargetContext.getResources().getString(R.string.blood_type);
        assertThat(title).isEqualTo(titleExp);
    }

    @Test
    public void testProperties_organDonor() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        EmergencyListPreference preference =
                getEmergencyListPreference(fragment, PreferenceKeys.KEY_ORGAN_DONOR);
        assertThat(preference).isNotNull();
        assertThat(preference.getKey()).isEqualTo(PreferenceKeys.KEY_ORGAN_DONOR);
        assertThat(preference.isPersistent()).isTrue();
        assertThat(preference.isNotSet()).isTrue();
        assertThat(preference.getValue()).isEqualTo("");
        assertThat(preference.getEntries().length).isEqualTo(preference.getEntryValues().length);
        assertThat(preference.getContentDescriptions()).isNull();
    }

    @Test
    public void testProperties_bloodType() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        EmergencyListPreference preference =
                getEmergencyListPreference(fragment, PreferenceKeys.KEY_BLOOD_TYPE);
        assertThat(preference).isNotNull();
        assertThat(preference.getKey()).isEqualTo(PreferenceKeys.KEY_BLOOD_TYPE);
        assertThat(preference.isPersistent()).isTrue();
        assertThat(preference.isNotSet()).isTrue();
        assertThat(preference.getValue()).isEqualTo("");
        assertThat(preference.getEntries().length).isEqualTo(preference.getEntryValues().length);
        assertThat(preference.getContentDescriptions().length).isEqualTo(
                preference.getEntries().length);
    }

    @Test
    public void testContentDescriptions() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        EmergencyListPreference preference =
                getEmergencyListPreference(fragment, PreferenceKeys.KEY_BLOOD_TYPE);
        for (int i = 0; i < preference.getEntries().length; i++) {
            SpannableString entry = ((SpannableString) preference.getEntries()[i]);
            TtsSpan[] span = entry.getSpans(0,
                    preference.getContentDescriptions().length, TtsSpan.class);
            assertThat(span.length).isEqualTo(1);
            assertThat(preference.getContentDescriptions()[i]).isEqualTo(
                    span[0].getArgs().get(TtsSpan.ARG_TEXT));
        }
    }

    private EditInfoActivity startEditInfoActivity() {
        final Intent editActivityIntent = new Intent(mTargetContext, EditInfoActivity.class);
        return (EditInfoActivity) mInstrumentation.startActivitySync(editActivityIntent);
    }

    private EmergencyListPreference getEmergencyListPreference(
            EditInfoFragment fragment, String key) {
        return (EmergencyListPreference) fragment.getMedicalInfoPreference(key);
    }
}
