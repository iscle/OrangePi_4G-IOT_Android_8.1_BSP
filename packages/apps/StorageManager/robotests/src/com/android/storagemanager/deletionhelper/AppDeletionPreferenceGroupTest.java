/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import com.android.storagemanager.testing.TestingConstants;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestingConstants.MANIFEST, sdk = TestingConstants.SDK_VERSION)
public class AppDeletionPreferenceGroupTest {
    @Mock private AppsAsyncLoader.PackageInfo mPackage1;
    @Mock private AppDeletionType mBackend;
    @Mock private PreferenceScreen mScreen;
    private List<AppsAsyncLoader.PackageInfo> mApps;
    private AppDeletionPreferenceGroup mGroup;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mApps = new ArrayList<>();
        mApps.add(mPackage1);
        mGroup = new AppDeletionPreferenceGroup(RuntimeEnvironment.application);
        mGroup.setDeletionType(mBackend);
        mGroup.mScreen = mScreen;
    }

    @Test
    public void addsPreferenceToScreen_onNoThreshold() {
        when(mBackend.getDeletionThreshold()).thenReturn(0L);
        verify(mScreen, never()).addPreference(any());
        mGroup.onAppRebuild(mApps);

        // Verify that a preference was added to the screen for mPackage1
        verify(mScreen).addPreference(any());
    }

    @Test
    public void addsPreferenceToScreenWithHighOrder_onNoThreshold() {
        when(mBackend.getDeletionThreshold()).thenReturn(0L);

        mGroup.onAppRebuild(mApps);
        ArgumentCaptor<Preference> apps = ArgumentCaptor.forClass(Preference.class);
        verify(mScreen).addPreference(apps.capture());

        assertThat(apps.getValue().getOrder()).isGreaterThan(0);
    }
}
