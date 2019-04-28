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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v7.preference.PreferenceManager;

import com.android.storagemanager.testing.StorageManagerRobolectricTestRunner;
import com.android.storagemanager.testing.TestingConstants;
import com.android.storagemanager.utils.IconProvider;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(StorageManagerRobolectricTestRunner.class)
@Config(manifest = TestingConstants.MANIFEST, sdk = TestingConstants.SDK_VERSION)
public class DownloadsDeletionPreferenceGroupTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock private IconProvider mIconProvider;

    private Context mContext;
    private DownloadsDeletionPreferenceGroup mGroup;
    private DownloadsDeletionType mType;
    private File mTempDir;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;

        mGroup = spy(new DownloadsDeletionPreferenceGroup(mContext));
        final PreferenceManager preferenceManager = mock(PreferenceManager.class);
        when(mGroup.getPreferenceManager()).thenReturn(preferenceManager);
        mType = new DownloadsDeletionType(mContext, new String[0]);

        mTempDir = temporaryFolder.newFolder();

        mGroup.registerDeletionService(mType);
        mGroup.injectIconProvider(mIconProvider);
    }

    @Test
    public void thumbnailsArePopulated() {
        FetchDownloadsLoader.DownloadsResult result = new FetchDownloadsLoader.DownloadsResult();
        File imageFile = new File(mTempDir, "test.bmp");
        result.files.add(imageFile);
        result.thumbnails.put(imageFile, Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565));
        File nonImageFile = new File(mTempDir, "test.txt");
        result.files.add(nonImageFile);

        mType.onLoadFinished(null, result);

        assertThat(mGroup.getPreferenceCount()).isEqualTo(2);
        assertThat(mGroup.getPreference(0).getIcon() instanceof BitmapDrawable).isTrue();
        assertThat(mGroup.getPreference(1).getIcon() instanceof BitmapDrawable).isFalse();
    }
}
