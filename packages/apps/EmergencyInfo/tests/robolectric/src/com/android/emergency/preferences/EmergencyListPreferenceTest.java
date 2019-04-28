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
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.test.suitebuilder.annotation.SmallTest;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.TextUtils;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.TestConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit tests for {@link EmergencyListPreference}. */
@SmallTest
@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public final class EmergencyListPreferenceTest {
    @Mock private PreferenceManager mPreferenceManager;
    @Mock private SharedPreferences mSharedPreferences;
    private EmergencyListPreference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mPreferenceManager.getSharedPreferences()).thenReturn(mSharedPreferences);

        Context context = RuntimeEnvironment.application;

        mPreference = spy(
                new EmergencyListPreference(RuntimeEnvironment.application, null /* attrs */));

        PreferenceGroup prefRoot = spy(new PreferenceScreen(context, null /* attrs */));
        when(prefRoot.getPreferenceManager()).thenReturn(mPreferenceManager);
        prefRoot.addPreference(mPreference);
    }

    @Test
    public void testReloadFromPreference() {
        CharSequence[] organDonorValues =
                RuntimeEnvironment.application.getResources().getStringArray(
                        R.array.organ_donor_entries);
        when(mSharedPreferences.getString(eq(PreferenceKeys.KEY_ORGAN_DONOR), anyString()))
                .thenReturn((String) organDonorValues[0]);

        mPreference.setKey(PreferenceKeys.KEY_ORGAN_DONOR);
        mPreference.setEntryValues(organDonorValues);

        mPreference.reloadFromPreference();
        assertThat(mPreference.getValue()).isEqualTo(mPreference.getEntryValues()[0]);
        assertThat(mPreference.isNotSet()).isFalse();
    }

    @Test
    public void testSetValue() {
        CharSequence[] organDonorEntries =
                RuntimeEnvironment.application.getResources().getStringArray(
                        R.array.organ_donor_entries);
        CharSequence[] organDonorValues =
                RuntimeEnvironment.application.getResources().getStringArray(
                        R.array.organ_donor_values);
        mPreference.setKey(PreferenceKeys.KEY_ORGAN_DONOR);
        mPreference.setEntries(organDonorEntries);
        mPreference.setEntryValues(organDonorValues);
        when(mSharedPreferences.getString(eq(PreferenceKeys.KEY_ORGAN_DONOR), anyString()))
                .thenAnswer(new CyclingStringArrayAnswer(organDonorValues));

        for (int i = 0; i < mPreference.getEntryValues().length; i++) {
            mPreference.setValue((String) mPreference.getEntryValues()[i]);

            assertThat(mPreference.getValue()).isEqualTo(mPreference.getEntryValues()[i]);
            if (!TextUtils.isEmpty(mPreference.getEntryValues()[i])) {
                assertThat(mPreference.getSummary()).isEqualTo(mPreference.getEntries()[i]);
            } else {
                assertThat(mPreference.getSummary()).isEqualTo(
                        RuntimeEnvironment.application.getResources().getString(
                                R.string.unknown_organ_donor));
            }
        }
    }

    /** An Answer that cycles through a list of string values in its answer. */
    private static class CyclingStringArrayAnswer implements Answer<String> {
        private CharSequence[] mValues;
        private int mIndex;

        public CyclingStringArrayAnswer(CharSequence[] values) {
            mValues = values;
            mIndex = 0;
        }

        @Override
        public String answer(InvocationOnMock invocation) {
            String value = (String) mValues[mIndex % mValues.length];
            mIndex++;
            return value;
        }
    }
}
