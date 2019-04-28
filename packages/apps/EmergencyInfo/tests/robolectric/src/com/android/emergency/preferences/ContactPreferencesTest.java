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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Looper;
import android.provider.ContactsContract;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.emergency.ContactTestUtils;
import com.android.emergency.EmergencyContactManager;
import com.android.emergency.TestConfig;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** Unit tests for {@link ContactPreferences}. */
@SmallTest
@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class ContactPreferencesTest {
    private static final String NAME = "Jake";
    private static final String PHONE_NUMBER = "123456";
    @Mock private PackageManager mPackageManager;
    @Mock private ContactPreference.ContactFactory mContactFactory;
    @Mock private EmergencyContactManager.Contact mContact;
    private ContextWrapper mContext;
    private ContactPreference mPreference;
    private Uri mPhoneUri;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = spy(RuntimeEnvironment.application);
        doReturn(mPackageManager).when(mContext).getPackageManager();

        mPhoneUri = ContactTestUtils.createContact(
                mContext.getContentResolver(), NAME, PHONE_NUMBER);

        when(mContactFactory.getContact(any(), any())).thenReturn(mContact);
        when(mContact.getName()).thenReturn(NAME);
        when(mContact.getPhoneUri()).thenReturn(mPhoneUri);
        when(mContact.getPhoneNumber()).thenReturn(PHONE_NUMBER);
        when(mContact.getContactLookupUri()).thenReturn(mPhoneUri);

        mPreference = new ContactPreference(mContext, mPhoneUri, mContactFactory);
    }

    @Test
    public void testContactPreference() {
        assertThat(mPreference.getPhoneUri()).isEqualTo(mPhoneUri);
        assertThat(mPreference.getContact().getName()).isEqualTo(NAME);
        assertThat(mPreference.getContact().getPhoneNumber()).isEqualTo(PHONE_NUMBER);

        assertThat(mPreference.getRemoveContactDialog()).isNull();
        mPreference.setRemoveContactPreferenceListener(
                new ContactPreference.RemoveContactPreferenceListener() {
                    @Override
                    public void onRemoveContactPreference(ContactPreference preference) {
                        // Do nothing
                    }
                });
        assertThat(mPreference.getRemoveContactDialog()).isNotNull();
    }

    @Test
    public void testDisplayContact() throws Throwable {
        mPreference.displayContact();

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(mPhoneUri);
        assertThat(Shadows.shadowOf(mContext).getNextStartedActivity().filterEquals(intent))
                .isTrue();
    }

    @Test
    public void testCallContact() throws Throwable {
        final String name = "a name";
        final String packageName = "a package name";

        List<ResolveInfo> resolveInfos = new ArrayList<>();
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.name = name;
        resolveInfo.activityInfo.packageName = packageName;
        resolveInfos.add(resolveInfo);
        when(mPackageManager.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(resolveInfos);

        mPreference.callContact();

        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + PHONE_NUMBER));
        intent.setComponent(new ComponentName(packageName, name));
        assertThat(Shadows.shadowOf(mContext).getNextStartedActivity().filterEquals(intent))
                .isTrue();
    }
}
