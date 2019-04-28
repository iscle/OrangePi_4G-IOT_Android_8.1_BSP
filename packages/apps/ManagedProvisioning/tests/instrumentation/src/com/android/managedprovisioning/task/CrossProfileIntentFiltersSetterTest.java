/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.support.test.filters.SmallTest;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link CrossProfileIntentFiltersSetter}
 */
@SmallTest
public class CrossProfileIntentFiltersSetterTest {

    private static final int TEST_PARENT_USER_ID = 101;
    private static final int TEST_PROFILE_USER_ID = 123;

    @Mock PackageManager mPackageManager;
    @Mock UserManager mUserManager;

    private CrossProfileIntentFiltersSetter mSetter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mSetter = new CrossProfileIntentFiltersSetter(mPackageManager, mUserManager);
    }

    @Test
    public void testSetFilters() {
        // WHEN setting the filters
        mSetter.setFilters(TEST_PARENT_USER_ID, TEST_PROFILE_USER_ID);

        // THEN the right number of filters is applied
        verify(mPackageManager, times(CrossProfileIntentFiltersSetter.FILTERS.size()))
                .addCrossProfileIntentFilter(any(IntentFilter.class), anyInt(), anyInt(), anyInt());

        // THEN the HOME intent should be forwarded from the profile to the parent
        verify(mPackageManager).addCrossProfileIntentFilter(
                CrossProfileIntentFiltersSetter.HOME.filter,
                TEST_PROFILE_USER_ID, TEST_PARENT_USER_ID,
                CrossProfileIntentFiltersSetter.HOME.flags);

        // THEN the ACTION_SEND intent should be forwarded from the parent to the profile
        verify(mPackageManager).addCrossProfileIntentFilter(
                CrossProfileIntentFiltersSetter.ACTION_SEND.filter,
                TEST_PARENT_USER_ID, TEST_PROFILE_USER_ID,
                CrossProfileIntentFiltersSetter.ACTION_SEND.flags);
    }

    @Test
    public void testResetFilters_NoProfiles() {
        // GIVEN that the user has no profiles
        UserInfo ui = new UserInfo(UserHandle.USER_SYSTEM, null, UserInfo.FLAG_PRIMARY);
        when(mUserManager.getProfiles(TEST_PARENT_USER_ID))
                .thenReturn(Collections.singletonList(ui));

        // WHEN calling reset filters
        mSetter.resetFilters(TEST_PARENT_USER_ID);

        // THEN nothing should happen
        verifyZeroInteractions(mPackageManager);
    }

    @Test
    public void testResetFilters_OneProfile() {
        // GIVEN that the user has no profiles
        UserInfo parent = new UserInfo(TEST_PARENT_USER_ID, null, UserInfo.FLAG_PRIMARY);
        UserInfo profile = new UserInfo(TEST_PROFILE_USER_ID, null, UserInfo.FLAG_MANAGED_PROFILE);
        when(mUserManager.getProfiles(TEST_PARENT_USER_ID))
                .thenReturn(Arrays.asList(parent, profile));

        // WHEN calling reset filters
        mSetter.resetFilters(TEST_PARENT_USER_ID);

        // THEN the existing filters should be removed
        verify(mPackageManager).clearCrossProfileIntentFilters(TEST_PARENT_USER_ID);
        verify(mPackageManager).clearCrossProfileIntentFilters(TEST_PROFILE_USER_ID);

        // THEN the right number of filters is applied
        verify(mPackageManager, times(CrossProfileIntentFiltersSetter.FILTERS.size()))
                .addCrossProfileIntentFilter(any(IntentFilter.class), anyInt(), anyInt(), anyInt());
    }
}
