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

package com.android.documentsui;

import android.support.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;

@LargeTest
public class ArchiveUiTest extends ActivityTest<FilesActivity> {
    public ArchiveUiTest() {
        super(FilesActivity.class);
    }

    public void testArchive_valid() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.openDocument("archive.zip");
        bots.directory.waitForDocument("file1.txt");
        bots.directory.assertDocumentsPresent("dir1", "dir2", "file1.txt");
        bots.directory.openDocument("dir1");
        bots.directory.waitForDocument("cherries.txt");
    }

    public void testArchive_invalid() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.openDocument("broken.zip");

        final String msg = String.valueOf(context.getString(R.string.empty));
        bots.directory.assertPlaceholderMessageText(msg);
    }
}
