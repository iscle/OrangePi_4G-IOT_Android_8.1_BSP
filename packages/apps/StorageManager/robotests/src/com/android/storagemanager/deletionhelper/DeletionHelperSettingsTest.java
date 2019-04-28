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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.storage.StorageManager;
import android.support.v7.preference.PreferenceScreen;

import com.android.storagemanager.testing.StorageManagerRobolectricTestRunner;
import com.android.storagemanager.testing.TestingConstants;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(StorageManagerRobolectricTestRunner.class)
@Config(manifest = TestingConstants.MANIFEST, sdk = TestingConstants.SDK_VERSION)
public class DeletionHelperSettingsTest {
    private static final String URI_APP_SCHEME = "android-app";
    private static final String PACKAGE_NAME = "com.package";
    private Context mContext;
    private PackageManager mPackageManager;

    @Before
    public void setUp() throws Exception {
        mContext = spy(RuntimeEnvironment.application);
        mPackageManager = spy(mContext.getPackageManager());
    }

    @Test
    public void nullAppHasNoGaugeTitle() {
        Intent intent = new Intent(StorageManager.ACTION_MANAGE_STORAGE);
        intent.putExtra(StorageManager.EXTRA_REQUESTED_BYTES, 100L);
        assertThat(DeletionHelperSettings.getGaugeString(mContext, intent, PACKAGE_NAME)).isNull();
    }

    @Test
    public void realAppHasGaugeTitle() throws Exception {
        mPackageManager = spy(mContext.getPackageManager());
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        ApplicationInfo info = mock(ApplicationInfo.class);
        when(info.loadLabel(any(PackageManager.class))).thenReturn("My Package");
        doReturn(info).when(mPackageManager).getApplicationInfo(anyString(), anyInt());
        Intent intent = new Intent(StorageManager.ACTION_MANAGE_STORAGE);
        intent.putExtra(StorageManager.EXTRA_REQUESTED_BYTES, 100L);
        assertThat(DeletionHelperSettings.getGaugeString(mContext, intent, PACKAGE_NAME))
                .isNotNull();
    }

    @Test
    public void downloadsNotDeletedInNoThresholdMode() throws Exception {
        DeletionHelperSettings settings =
                spy(DeletionHelperSettings.newInstance(AppsAsyncLoader.NO_THRESHOLD));
        PreferenceScreen preferenceScreen = mock(PreferenceScreen.class);
        doReturn(preferenceScreen).when(settings).getPreferenceScreen();
        DownloadsDeletionType downloadsDeletionType = mock(DownloadsDeletionType.class);
        settings.setDownloadsDeletionType(downloadsDeletionType);

        settings.setupEmptyState();
        settings.clearData();

        verify(downloadsDeletionType, never()).clearFreeableData(any());
    }

    @Test
    public void onFreeableChangeChecksForNull() throws Exception {
        DeletionHelperSettings settings =
                DeletionHelperSettings.newInstance(AppsAsyncLoader.NO_THRESHOLD);
        AppDeletionType appBackend = mock(AppDeletionType.class);
        when(appBackend.isEmpty()).thenReturn(true);
        settings.mAppBackend = appBackend;

        settings.onFreeableChanged(0, 0L);
    }
}
