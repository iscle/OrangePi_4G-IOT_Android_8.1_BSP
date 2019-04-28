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

package com.android.storagemanager.automatic;

import android.os.Bundle;
import android.os.Environment;
import com.android.storagemanager.deletionhelper.DeletionHelperSettings;
import com.android.storagemanager.deletionhelper.DeletionType;
import com.android.storagemanager.deletionhelper.DeletionType.LoadingStatus;
import com.android.storagemanager.deletionhelper.DownloadsDeletionType;
import com.android.storagemanager.deletionhelper.FetchDownloadsLoader.DownloadsResult;
import com.android.storagemanager.testing.TestingConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(manifest= TestingConstants.MANIFEST, sdk=23)
public class DownloadsDeletionTypeTest {
    private DownloadsDeletionType mDeletion;
    private File mDownloadsDirectory;

    @Before
    public void setUp() {
        mDeletion = new DownloadsDeletionType(RuntimeEnvironment.application, null);
        mDownloadsDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    @Test
    public void testInitializeWithUncheckedFiles() throws Exception {
        File temp = new File(mDownloadsDirectory, "temp");
        File temp2 = new File(mDownloadsDirectory, "temp2");
        String[] filePaths = new String[2];
        filePaths[0] = temp.getPath();
        filePaths[1] = temp2.getPath();
        mDeletion = new DownloadsDeletionType(RuntimeEnvironment.application, filePaths);

        assertThat(mDeletion.isChecked(temp)).isFalse();
        assertThat(mDeletion.isChecked(temp2)).isFalse();
    }

    @Test
    public void testFetchDownloads() throws Exception {
        File temp = new File(mDownloadsDirectory, "temp");
        File temp2 = new File(mDownloadsDirectory, "temp2");
        DownloadsResult result = new DownloadsResult();
        result.files.add(temp);
        result.files.add(temp2);

        mDeletion.onLoadFinished(null, result);
        Set<File> fileSet = mDeletion.getFiles();

        assertThat(fileSet.contains(temp)).isTrue();
        assertThat(fileSet.contains(temp2)).isTrue();
    }

    @Test
    public void testSetChecked() throws Exception {
        File temp = new File(mDownloadsDirectory, "temp");
        DownloadsResult result = new DownloadsResult();
        result.files.add(temp);

        mDeletion.onLoadFinished(null, result);

        // Downloads files are default checked.
        assertThat(mDeletion.isChecked(temp)).isTrue();
        mDeletion.setFileChecked(temp, false);

        assertThat(mDeletion.isChecked(temp)).isFalse();
        mDeletion.setFileChecked(temp, true);

        assertThat(mDeletion.isChecked(temp)).isTrue();
    }

    @Test
    public void testUncheckedFilesDoNotCountForSize() throws Exception {
        File temp = new File(mDownloadsDirectory, "temp");
        FileWriter fileWriter = new FileWriter(temp);
        fileWriter.write("test");
        fileWriter.close();
        DownloadsResult result = new DownloadsResult();
        result.files.add(temp);

        mDeletion.onLoadFinished(null, result);

        // Downloads files are default checked.
        assertThat(mDeletion.isChecked(temp)).isTrue();
        assertThat(mDeletion.getFreeableBytes(DeletionHelperSettings.COUNT_CHECKED_ONLY))
                .isEqualTo(4);

        mDeletion.setFileChecked(temp, false);
        assertThat(mDeletion.getFreeableBytes(DeletionHelperSettings.COUNT_CHECKED_ONLY))
                .isEqualTo(0);
    }

    @Test
    public void testSaveAndRestoreRemembersUncheckedFiles() throws Exception {
        File temp = new File(mDownloadsDirectory, "temp");
        File temp2 = new File(mDownloadsDirectory, "temp2");
        DownloadsResult result = new DownloadsResult();
        result.files.add(temp);
        result.files.add(temp2);
        mDeletion.onLoadFinished(null, result);

        mDeletion.setFileChecked(temp, false);
        Bundle savedBundle = new Bundle();
        mDeletion.onSaveInstanceStateBundle(savedBundle);
        mDeletion = new DownloadsDeletionType(RuntimeEnvironment.application,
                savedBundle.getStringArray(DownloadsDeletionType.EXTRA_UNCHECKED_DOWNLOADS));

        assertThat(mDeletion.isChecked(temp)).isFalse();
        assertThat(mDeletion.isChecked(temp2)).isTrue();
    }

    @Test
    public void testCallbackOnFileLoad() throws Exception {
        File temp = new File(mDownloadsDirectory, "temp");
        File temp2 = new File(mDownloadsDirectory, "temp2");
        DownloadsResult result = new DownloadsResult();
        result.files.add(temp);
        result.files.add(temp2);
        result.totalSize = 101L;

        DeletionType.FreeableChangedListener mockListener =
                mock(DeletionType.FreeableChangedListener.class);
        mDeletion.registerFreeableChangedListener(mockListener);

        // Calls back immediately when we add a listener with its most current info.
        verify(mockListener).onFreeableChanged(eq(0), eq(0L));

        // Callback when the load finishes.
        mDeletion.onLoadFinished(null, result);
        verify(mockListener).onFreeableChanged(eq(2), eq(101L));
    }

    @Test
    public void testLoadingState_initiallyIncomplete() {
        // We should always be in the incomplete state when we start out
        assertThat(mDeletion.getLoadingStatus()).isEqualTo(LoadingStatus.LOADING);
    }

    @Test
    public void testLoadingState_completeEmptyOnNothingFound() {
        // We should be in EMPTY if nothing was found
        DownloadsResult result = new DownloadsResult();
        mDeletion.onLoadFinished(null, result);
        assertThat(mDeletion.isEmpty()).isTrue();
    }

    @Test
    public void testLoadingState_completeOnDeletableContentFound() {
        // We should be in COMPLETE if downloads were found
        DownloadsResult result = new DownloadsResult();
        File temp = new File(mDownloadsDirectory, "temp");
        File temp2 = new File(mDownloadsDirectory, "temp2");
        result.files.add(temp);
        result.files.add(temp2);
        mDeletion.onLoadFinished(null, result);
        assertThat(mDeletion.isComplete()).isTrue();
    }
}
