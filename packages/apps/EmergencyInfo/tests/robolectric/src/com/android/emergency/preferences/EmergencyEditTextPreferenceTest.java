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

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.TestConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit tests for {@link EmergencyEditTextPreference}. */
@SmallTest
@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class EmergencyEditTextPreferenceTest {
    @Mock private PreferenceManager mPreferenceManager;
    @Mock private SharedPreferences mSharedPreferences;
    private EmergencyEditTextPreference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mPreferenceManager.getSharedPreferences()).thenReturn(mSharedPreferences);

        Context context = RuntimeEnvironment.application;

        mPreference = spy(new EmergencyEditTextPreference(context, null));

        PreferenceGroup prefRoot = spy(new PreferenceScreen(context, null));
        when(prefRoot.getPreferenceManager()).thenReturn(mPreferenceManager);
        prefRoot.addPreference(mPreference);
    }

    @Test
    public void testDefaultProperties() {
        assertThat(mPreference.isEnabled()).isTrue();
        assertThat(mPreference.isPersistent()).isTrue();
        assertThat(mPreference.isSelectable()).isTrue();
        assertThat(mPreference.isNotSet()).isTrue();
    }

    @Test
    public void testReloadFromPreference() throws Throwable {
        mPreference.setKey(PreferenceKeys.KEY_MEDICAL_CONDITIONS);

        String medicalConditions = "Asthma";
        when(mSharedPreferences.getString(eq(mPreference.getKey()), anyString()))
                .thenReturn(medicalConditions);

        mPreference.reloadFromPreference();
        assertThat(mPreference.getText()).isEqualTo(medicalConditions);
        assertThat(mPreference.isNotSet()).isFalse();
    }

    @Test
    public void testOnPreferenceChange() throws Throwable {
        final String medicalConditions = "Asthma";
        mPreference.onPreferenceChange(mPreference, medicalConditions);

        assertThat(mPreference.getSummary()).isEqualTo(medicalConditions);
    }

    @Test
    public void testSetText() throws Throwable {
        final String medicalConditions = "Asthma";
        mPreference.setText(medicalConditions);

        assertThat(mPreference.getText()).isEqualTo(medicalConditions);
        assertThat(mPreference.getSummary()).isEqualTo(medicalConditions);
    }
}
