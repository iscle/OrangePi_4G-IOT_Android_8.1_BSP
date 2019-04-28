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
package com.android.emergency.edit;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;

import com.android.emergency.ContactTestUtils;
import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.preferences.EmergencyContactsPreference;
import com.android.emergency.preferences.EmergencyEditTextPreference;
import com.android.emergency.preferences.EmergencyListPreference;
import com.android.emergency.preferences.NameAutoCompletePreference;
import com.android.emergency.util.PreferenceUtils;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link EditInfoActivity}. */
@RunWith(AndroidJUnit4.class)
public final class EditInfoActivityTest {
    private Instrumentation mInstrumentation;
    private Context mTargetContext;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        // In case a previous test crashed or failed, clear any previous shared preference value.
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().clear().commit();
    }

    @After
    public void tearDown() {
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().clear().commit();
    }

    @Test
    public void testInitialState() {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();
        PreferenceGroup medicalInfoParent = fragment.getMedicalInfoParent();

        // Because the initial state of each preference is empty, the edit activity removes the
        // preference. As a result, we expect them all to be null.
        for (String key : PreferenceKeys.KEYS_EDIT_EMERGENCY_INFO) {
            assertWithMessage(key).that(medicalInfoParent.findPreference(key)).isNull();
        }
        EmergencyContactsPreference emergencyContactsPreference =
                (EmergencyContactsPreference) fragment.findPreference(
                        PreferenceKeys.KEY_EMERGENCY_CONTACTS);
        assertThat(emergencyContactsPreference).isNotNull();
        assertThat(emergencyContactsPreference.getPreferenceCount()).isEqualTo(0);

        final PackageManager packageManager = mTargetContext.getPackageManager();
        final String packageName = mTargetContext.getPackageName();
        final ComponentName componentName = new ComponentName(
                packageName,
                packageName + PreferenceUtils.SETTINGS_SUGGESTION_ACTIVITY_ALIAS);
        assertThat(packageManager.getComponentEnabledSetting(componentName))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    @Test
    public void testClearAllPreferences() {
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().putString(
                PreferenceKeys.KEY_NAME, "John").commit();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().putString(
                PreferenceKeys.KEY_ADDRESS, "Home").commit();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().putString(
                PreferenceKeys.KEY_BLOOD_TYPE, "A+").commit();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().putString(
                PreferenceKeys.KEY_ALLERGIES, "Peanuts").commit();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().putString(
                PreferenceKeys.KEY_MEDICATIONS, "Aspirin").commit();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().putString(
                PreferenceKeys.KEY_MEDICAL_CONDITIONS, "Asthma").commit();
        PreferenceManager.getDefaultSharedPreferences(mTargetContext).edit().putString(
                PreferenceKeys.KEY_ORGAN_DONOR, "Yes").commit();

        final Uri contactUri = ContactTestUtils
                .createContact(mTargetContext.getContentResolver(), "Michael", "789");
        PreferenceManager.getDefaultSharedPreferences(mTargetContext)
                .edit().putString(PreferenceKeys.KEY_EMERGENCY_CONTACTS, contactUri.toString())
                .commit();

        final PackageManager packageManager = mTargetContext.getPackageManager();
        final String packageName = mTargetContext.getPackageName();
        final ComponentName componentName = new ComponentName(
                packageName,
                packageName + PreferenceUtils.SETTINGS_SUGGESTION_ACTIVITY_ALIAS);
        // With emergency info settings present, the settings suggestion should be disabled.
        assertThat(packageManager.getComponentEnabledSetting(componentName))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();

        final NameAutoCompletePreference namePreference =
                (NameAutoCompletePreference) fragment.getMedicalInfoPreference(
                        PreferenceKeys.KEY_NAME);
        final EmergencyEditTextPreference addressPreference =
                (EmergencyEditTextPreference) fragment.getMedicalInfoPreference(
                        PreferenceKeys.KEY_ADDRESS);
        final EmergencyListPreference bloodTypePreference =
                (EmergencyListPreference) fragment.getMedicalInfoPreference(
                        PreferenceKeys.KEY_BLOOD_TYPE);
        final EmergencyEditTextPreference allergiesPreference =
                (EmergencyEditTextPreference) fragment.getMedicalInfoPreference(
                        PreferenceKeys.KEY_ALLERGIES);
        final EmergencyEditTextPreference medicationsPreference =
                (EmergencyEditTextPreference) fragment.getMedicalInfoPreference(
                        PreferenceKeys.KEY_MEDICATIONS);
        final EmergencyEditTextPreference medicalConditionsPreference =
                (EmergencyEditTextPreference) fragment.getMedicalInfoPreference(
                        PreferenceKeys.KEY_MEDICAL_CONDITIONS);
        final EmergencyListPreference organDonorPreference =
                (EmergencyListPreference) fragment.getMedicalInfoPreference(
                        PreferenceKeys.KEY_ORGAN_DONOR);
        final EmergencyContactsPreference emergencyContactsPreference =
                (EmergencyContactsPreference) fragment.findPreference(
                        PreferenceKeys.KEY_EMERGENCY_CONTACTS);

        String unknownName = activity.getResources().getString(R.string.unknown_name);
        String unknownAddress = activity.getResources().getString(R.string.unknown_address);
        String unknownBloodType = activity.getResources().getString(R.string.unknown_blood_type);
        String unknownAllergies = activity.getResources().getString(R.string.unknown_allergies);
        String unknownMedications = activity.getResources().getString(R.string.unknown_medications);
        String unknownMedicalConditions =
                activity.getResources().getString(R.string.unknown_medical_conditions);
        String unknownOrganDonor = activity.getResources().getString(R.string.unknown_organ_donor);

        assertThat(namePreference.getSummary()).isNotEqualTo(unknownName);
        assertThat(addressPreference.getSummary()).isNotEqualTo(unknownAddress);
        assertThat(bloodTypePreference.getSummary()).isNotEqualTo(unknownBloodType);
        assertThat(allergiesPreference.getSummary()).isNotEqualTo(unknownAllergies);
        assertThat(medicationsPreference.getSummary()).isNotEqualTo(unknownMedications);
        assertThat(medicalConditionsPreference.getSummary()).isNotEqualTo(unknownMedicalConditions);
        assertThat(organDonorPreference.getSummary()).isNotEqualTo(unknownOrganDonor);
        assertThat(emergencyContactsPreference.getEmergencyContacts()).hasSize(1);
        assertThat(emergencyContactsPreference.getPreferenceCount()).isEqualTo(1);

        EditInfoActivity.ClearAllDialogFragment clearAllDialogFragment =
                (EditInfoActivity.ClearAllDialogFragment) activity.getFragmentManager()
                        .findFragmentByTag(EditInfoActivity.TAG_CLEAR_ALL_DIALOG);
        assertThat(clearAllDialogFragment).isNull();

        openActionBarOverflowOrOptionsMenu(mTargetContext);
        onView(withText(R.string.clear_all)).perform(click());

        final EditInfoActivity.ClearAllDialogFragment clearAllDialogFragmentAfterwards =
                (EditInfoActivity.ClearAllDialogFragment) activity.getFragmentManager()
                        .findFragmentByTag(EditInfoActivity.TAG_CLEAR_ALL_DIALOG);

        assertThat(clearAllDialogFragmentAfterwards).isNotNull();
        Dialog clearAllDialog = clearAllDialogFragmentAfterwards.getDialog();
        assertThat(clearAllDialog).isNotNull();
        assertThat(clearAllDialog.isShowing()).isTrue();

        onView(withText(R.string.clear)).perform(click());

        // After the clear all the preferences dialog is confirmed, the preferences values are
        // reloaded, and the existing object references are updated in-place.
        assertThat(namePreference.getSummary()).isNull();
        assertThat(addressPreference.getSummary()).isNull();
        assertThat(bloodTypePreference.getSummary().toString()).isEqualTo(unknownBloodType);
        assertThat(allergiesPreference.getSummary()).isNull();
        assertThat(medicationsPreference.getSummary()).isNull();
        assertThat(medicalConditionsPreference.getSummary()).isNull();
        assertThat(organDonorPreference.getSummary()).isEqualTo(unknownOrganDonor);
        assertThat(emergencyContactsPreference.getEmergencyContacts()).isEmpty();
        assertThat(emergencyContactsPreference.getPreferenceCount()).isEqualTo(0);

        // The preference values are not displayed, being empty.
        PreferenceGroup medicalInfoParent = fragment.getMedicalInfoParent();
        for (String key : PreferenceKeys.KEYS_EDIT_EMERGENCY_INFO) {
            assertWithMessage(key).that(medicalInfoParent.findPreference(key)).isNull();
        }

        // Now that the settings have been cleared, the settings suggestion should reappear.
        assertThat(packageManager.getComponentEnabledSetting(componentName))
                .isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        assertThat(ContactTestUtils
                .deleteContact(activity.getContentResolver(), "Michael", "789")).isTrue();
    }

    @Test
    public void testAddContactPreference() throws Throwable {
        EditInfoActivity activity = startEditInfoActivity();
        EditInfoFragment fragment = (EditInfoFragment) activity.getFragment();

        Preference addContactPreference =
                fragment.findPreference(PreferenceKeys.KEY_ADD_EMERGENCY_CONTACT);
        assertThat(addContactPreference).isNotNull();

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PICK);
        intentFilter.addDataType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        Instrumentation.ActivityMonitor activityMonitor =
                mInstrumentation.addMonitor(intentFilter, null, true /* block */);

        addContactPreference
                .getOnPreferenceClickListener().onPreferenceClick(addContactPreference);
        assertThat(mInstrumentation.checkMonitorHit(activityMonitor, 1 /* minHits */)).isTrue();
    }

    private EditInfoActivity startEditInfoActivity() {
        final Intent editActivityIntent = new Intent(mTargetContext, EditInfoActivity.class);
        return (EditInfoActivity) mInstrumentation.startActivitySync(editActivityIntent);
    }
}
