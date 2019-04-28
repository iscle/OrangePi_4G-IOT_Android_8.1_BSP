/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.deletionhelper;

import com.android.storagemanager.deletionhelper.DeletionType.LoadingStatus;
import com.android.storagemanager.testing.TestingConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestingConstants.MANIFEST, sdk = TestingConstants.SDK_VERSION)
public class AppDeletionTypeTest {
    private static String PACKAGE_NAME = "com.package.package";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DeletionHelperSettings mFragment;

    @Mock private AppDeletionPreferenceGroup mGroup;
    private AppDeletionType mDeletion;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDeletion = new AppDeletionType(mFragment, null, AppsAsyncLoader.NO_THRESHOLD);
        mDeletion.registerView(mGroup);
        mDeletion.registerFreeableChangedListener(mFragment);
    }

    @Test
    public void uncheckedAppNotReported() {
        List<AppsAsyncLoader.PackageInfo> loadedPackages = new ArrayList<>();
        loadedPackages.add(
                new AppsAsyncLoader.PackageInfo.Builder()
                        .setDaysSinceLastUse(100)
                        .setDaysSinceFirstInstall(101)
                        .setUserId(0)
                        .setPackageName(PACKAGE_NAME)
                        .setSize(1000)
                        .setFlags(0)
                        .build());

        // By default, no packages are checked, so these will be unchecked.
        mDeletion.onLoadFinished(null, loadedPackages);

        verify(mFragment).onFreeableChanged(eq(1), eq(0L));
    }

    @Test
    public void checkedAppIsReported() {
        List<AppsAsyncLoader.PackageInfo> loadedPackages = new ArrayList<>();
        loadedPackages.add(
                new AppsAsyncLoader.PackageInfo.Builder()
                        .setDaysSinceLastUse(100)
                        .setDaysSinceFirstInstall(101)
                        .setUserId(0)
                        .setPackageName(PACKAGE_NAME)
                        .setSize(1000)
                        .setFlags(0)
                        .build());

        // By default, no packages are checked, so these will be unchecked.
        mDeletion.onLoadFinished(null, loadedPackages);
        mDeletion.setChecked(PACKAGE_NAME, true);

        verify(mFragment).onFreeableChanged(eq(1), eq(1000L));
    }

    @Test
    public void dontCrashWhenClearingAndAppsArentLoaded() {
        mDeletion.clearFreeableData(mFragment.getActivity());
    }

    @Test
    public void testLoadingState_initiallyIncomplete() {
        // We should always be in the incomplete state when we start out
        assertThat(mDeletion.getLoadingStatus()).isEqualTo(LoadingStatus.LOADING);
    }

    @Test
    public void testLoadingState_completeEmptyOnNothingFound() {
        // We should be in EMPTY if nothing is found
        List<AppsAsyncLoader.PackageInfo> apps = new ArrayList<>();
        mDeletion.onLoadFinished(null, apps);
        assertThat(mDeletion.isEmpty()).isTrue();
    }

    @Test
    public void testLoadingState_completeOnDeletableContentFound() {
        // We should be in COMPLETE if apps were found
        List<AppsAsyncLoader.PackageInfo> apps = new ArrayList<>();
        apps.add(
                new AppsAsyncLoader.PackageInfo.Builder()
                        .setDaysSinceLastUse(100)
                        .setDaysSinceFirstInstall(101)
                        .setUserId(0)
                        .setPackageName(PACKAGE_NAME)
                        .setSize(1000)
                        .setFlags(0)
                        .build());
        mDeletion.onLoadFinished(null, apps);
        assertThat(mDeletion.isComplete()).isTrue();
    }
}
