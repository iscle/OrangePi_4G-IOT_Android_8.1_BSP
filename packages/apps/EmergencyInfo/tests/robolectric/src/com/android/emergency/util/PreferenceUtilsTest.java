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
package com.android.emergency.util;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.emergency.ContactTestUtils;
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

/** Unit tests for {@link PreferenceUtils}. */
@SmallTest
@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public final class PreferenceUtilsTest {
    private static final String PACKAGE_NAME = "com.android.emergency";
    private static final String NAME = "Jane";
    private static final String PHONE_NUMBER = "5150";
    private static final ComponentName COMPONENT_NAME = new ComponentName(
                PACKAGE_NAME,
                PACKAGE_NAME + PreferenceUtils.SETTINGS_SUGGESTION_ACTIVITY_ALIAS);

    @Mock ContentResolver mContentResolver;
    @Mock Context mContext;
    @Mock Cursor mCursor;
    @Mock PackageManager mPackageManager;
    @Mock SharedPreferences mSharedPreferences;
    @Mock SharedPreferences.Editor mSharedPreferencesEditor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getPackageName()).thenReturn(PACKAGE_NAME);
        when(mContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mSharedPreferences);
        when(mSharedPreferences.edit()).thenReturn(mSharedPreferencesEditor);
        when(mSharedPreferencesEditor.putString(anyString(), anyString()))
                .thenReturn(mSharedPreferencesEditor);
    }

    @Test
    public void testHasAtLeastOnePreferenceSet_notSet() {
        when(mSharedPreferences.getString(anyString(), anyString())).thenReturn("");

        assertThat(PreferenceUtils.hasAtLeastOnePreferenceSet(mContext)).isFalse();
    }

    @Test
    public void testHasAtLeastOnePreferenceSet_set() {
        when(mSharedPreferences.getString(anyString(), anyString())).thenReturn("mxyzptlk");

        assertThat(PreferenceUtils.hasAtLeastOnePreferenceSet(mContext)).isTrue();
    }

    @Test
    public void testHasAtLeastOneEmergencyContact_notSet() {
        when(mSharedPreferences.getString(eq(PreferenceKeys.KEY_EMERGENCY_CONTACTS), any()))
                .thenReturn("");

        assertThat(PreferenceUtils.hasAtLeastOneEmergencyContact(mContext)).isFalse();
    }

    @Test
    public void testHasAtLeastOneEmergencyContact_set() {
        final Uri contactUri = ContactTestUtils.createContact(
                RuntimeEnvironment.application.getContentResolver(), NAME, PHONE_NUMBER);
        when(mSharedPreferences.getString(eq(PreferenceKeys.KEY_EMERGENCY_CONTACTS), any()))
                .thenReturn(contactUri.toString());
        when(mContentResolver.query(eq(contactUri), any(), any(), any(), any()))
                .thenReturn(mCursor);
        when(mCursor.moveToFirst()).thenReturn(true);

        assertThat(PreferenceUtils.hasAtLeastOneEmergencyContact(mContext)).isTrue();
    }

    @Test
    public void testEnableSettingsSuggestion() {
        PreferenceUtils.enableSettingsSuggestion(mContext);

        verify(mPackageManager).setComponentEnabledSetting(
                eq(COMPONENT_NAME),
                eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateSettingsSuggestionState_contactSet() {
        final Uri contactUri = ContactTestUtils.createContact(
                RuntimeEnvironment.application.getContentResolver(), NAME, PHONE_NUMBER);
        when(mSharedPreferences.getString(eq(PreferenceKeys.KEY_EMERGENCY_CONTACTS), any()))
                .thenReturn(contactUri.toString());
        when(mContentResolver.query(eq(contactUri), any(), any(), any(), any()))
                .thenReturn(mCursor);
        when(mCursor.moveToFirst()).thenReturn(true);

        PreferenceUtils.updateSettingsSuggestionState(mContext);

        verify(mPackageManager).setComponentEnabledSetting(
                eq(COMPONENT_NAME),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateSettingsSuggestionState_noPreferencesOrContactSet() {
        when(mSharedPreferences.getString(anyString(), anyString())).thenReturn("");

        PreferenceUtils.updateSettingsSuggestionState(mContext);

        verify(mPackageManager).setComponentEnabledSetting(
                eq(COMPONENT_NAME),
                eq(PackageManager.COMPONENT_ENABLED_STATE_ENABLED),
                eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testUpdateSettingsSuggestionState_preferenceSet() {
        when(mSharedPreferences.getString(eq(PreferenceKeys.KEY_ALLERGIES), any()))
                .thenReturn("peanuts");

        PreferenceUtils.updateSettingsSuggestionState(mContext);

        verify(mPackageManager).setComponentEnabledSetting(
                eq(COMPONENT_NAME),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP));
    }
}
