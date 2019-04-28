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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.preference.PreferenceScreen;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.emergency.ContactTestUtils;
import com.android.emergency.EmergencyContactManager;
import com.android.emergency.PreferenceKeys;
import com.android.emergency.TestConfig;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit tests for {@link EmergencyContactsPreference}. */
@SmallTest
@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class EmergencyContactsPreferenceTest {
    @Mock private PackageManager mPackageManager;
    @Mock private PreferenceManager mPreferenceManager;
    @Mock private SharedPreferences mSharedPreferences;
    @Mock private EmergencyContactsPreference.ContactValidator mContactValidator;
    @Mock private ContactPreference.ContactFactory mContactFactory;
    private ContextWrapper mContext;
    private EmergencyContactsPreference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mPreferenceManager.getSharedPreferences()).thenReturn(mSharedPreferences);

        mContext = spy(RuntimeEnvironment.application);
        doReturn(mPackageManager).when(mContext).getPackageManager();

        mPreference = spy(new EmergencyContactsPreference(RuntimeEnvironment.application,
                    null /* attrs */, mContactValidator, mContactFactory));

        PreferenceGroup prefRoot = spy(new PreferenceScreen(mContext, null /* attrs */));
        when(prefRoot.getPreferenceManager()).thenReturn(mPreferenceManager);
        prefRoot.addPreference(mPreference);
    }

    @Test
    public void testDefaultProperties() {
        assertThat(mPreference.isPersistent()).isTrue();
        assertThat(mPreference.isNotSet()).isTrue();
        assertThat(mPreference.getEmergencyContacts()).isEmpty();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void testAddAndRemoveEmergencyContact() throws Throwable {
        final String name = "Jane";
        final String phoneNumber = "456";

        when(mContactValidator.isValidEmergencyContact(any(), any())).thenReturn(true);

        EmergencyContactManager.Contact contact = mock(EmergencyContactManager.Contact.class);
        when(mContactFactory.getContact(any(), any())).thenReturn(contact);
        when(contact.getName()).thenReturn(name);
        when(contact.getPhoneNumber()).thenReturn(phoneNumber);

        Uri uri = mock(Uri.class);
        when(contact.getPhoneUri()).thenReturn(uri);

        mPreference.addNewEmergencyContact(uri);

        assertThat(mPreference.getEmergencyContacts()).hasSize(1);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        ContactPreference contactPreference = (ContactPreference) mPreference.getPreference(0);

        assertThat(contactPreference.getPhoneUri()).isEqualTo(uri);
        assertThat(contactPreference.getTitle()).isEqualTo(name);
        assertThat((String) contactPreference.getSummary()).contains(phoneNumber);

        mPreference.onRemoveContactPreference((ContactPreference) mPreference.getPreference(0));

        assertThat(mPreference.getEmergencyContacts()).isEmpty();
        assertThat(mPreference.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void testReloadFromPreference() throws Throwable {
        final String nameJane = "Jane";
        final String phoneNumberJane = "456";
        Uri contactUriJane = Uri.parse("tel:" + phoneNumberJane);
        EmergencyContactManager.Contact contactJane = mock(EmergencyContactManager.Contact.class);
        when(mContactFactory.getContact(any(), eq(contactUriJane))).thenReturn(contactJane);
        when(contactJane.getName()).thenReturn(nameJane);
        when(contactJane.getPhoneNumber()).thenReturn(phoneNumberJane);
        when(contactJane.getPhoneUri()).thenReturn(contactUriJane);

        final String nameJohn = "John";
        final String phoneNumberJohn = "123";
        Uri contactUriJohn = Uri.parse("tel:" + phoneNumberJohn);
        EmergencyContactManager.Contact contactJohn = mock(EmergencyContactManager.Contact.class);
        when(mContactFactory.getContact(any(), eq(contactUriJohn))).thenReturn(contactJohn);
        when(contactJohn.getName()).thenReturn(nameJohn);
        when(contactJohn.getPhoneNumber()).thenReturn(phoneNumberJohn);
        when(contactJohn.getPhoneUri()).thenReturn(contactUriJohn);

        final List<Uri> emergencyContacts = new ArrayList<>();
        emergencyContacts.add(contactUriJane);
        emergencyContacts.add(contactUriJohn);
        mPreference.setEmergencyContacts(emergencyContacts);

        assertThat(mPreference.getEmergencyContacts().size()).isEqualTo(2);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(2);

        // "Delete" Jane by reloading from preferences. The mock SharedPreferences still have both
        // contacts stored, but the validator only believes John is valid.
        mPreference.setKey(PreferenceKeys.KEY_EMERGENCY_CONTACTS);
        when(mSharedPreferences.getString(eq(mPreference.getKey()), any()))
                .thenReturn(mPreference.serialize(emergencyContacts));
        when(mContactValidator.isValidEmergencyContact(any(), eq(contactUriJane)))
                .thenReturn(false);
        when(mContactValidator.isValidEmergencyContact(any(), eq(contactUriJohn))).thenReturn(true);
        // Override the preference's persist behavior, to avoid EmergencyContactsPreference
        // attempting to write to SharedPreferences. (Preference's default behavior is unmockable.)
        doNothing().when(mPreference).persistEmergencyContacts(any());
        mPreference.reloadFromPreference();

        // Assert the only remaining contact is John
        assertThat(mPreference.getEmergencyContacts()).hasSize(1);
        assertThat(mPreference.getPreferenceCount()).isEqualTo(1);
        ContactPreference contactPreference = (ContactPreference) mPreference.getPreference(0);
        assertThat(contactPreference.getPhoneUri()).isEqualTo(contactUriJohn);
    }
}
