/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.support.annotation.VisibleForTesting;
import android.text.format.DateUtils;

import com.android.storagemanager.utils.AsyncLoader;
import com.android.storagemanager.utils.IconProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * FetchDownloadsLoader is an asynchronous task which returns files in the Downloads
 * directory which have not been modified in longer than 90 days.
 */
public class FetchDownloadsLoader extends AsyncLoader<FetchDownloadsLoader.DownloadsResult> {
    private static final String DEBUG_FILE_AGE_OVERRIDE = "debug.asm.file_age_limit";
    private static final int MINIMUM_AGE_DAYS = 0;
    private File mDirectory;

    /**
     * Sets up a FetchDownloadsLoader in any directory.
     *
     * @param directory The directory to look into.
     */
    public FetchDownloadsLoader(Context context, File directory) {
        super(context);
        mDirectory = directory;
    }

    @Override
    protected void onDiscardResult(DownloadsResult result) {
    }

    @Override
    public DownloadsResult loadInBackground() {
        return collectFiles(mDirectory);
    }

    @VisibleForTesting
    static DownloadsResult collectFiles(File dir) {
        return collectFiles(dir, new DownloadsResult());
    }

    private static DownloadsResult collectFiles(File dir, DownloadsResult result) {
        int minimumAgeDays = SystemProperties.getInt(DEBUG_FILE_AGE_OVERRIDE, MINIMUM_AGE_DAYS);
        final long last_modified_threshold = System.currentTimeMillis() -
                minimumAgeDays * DateUtils.DAY_IN_MILLIS;
        File downloadFiles[] = dir.listFiles();
        if (downloadFiles != null && downloadFiles.length > 0) {
            for (File currentFile : downloadFiles) {
                if (currentFile.isDirectory()) {
                    collectFiles(currentFile, result);
                } else {
                    // Skip files that have been modified too recently.
                    if (last_modified_threshold < currentFile.lastModified()) {
                        continue;
                    }

                    if (currentFile.lastModified() < result.youngestLastModified) {
                        result.youngestLastModified = currentFile.lastModified();
                    }
                    result.files.add(currentFile);
                    result.totalSize += currentFile.length();

                    if (IconProvider.isImageType(currentFile)) {
                        Bitmap thumbnail =
                                ThumbnailUtils.createImageThumbnail(
                                        currentFile.getAbsolutePath(),
                                        MediaStore.Images.Thumbnails.MINI_KIND);
                        result.thumbnails.put(currentFile, thumbnail);
                    }
                }
            }
        }

        return result;
    }

    /**
     * The DownloadsResult is the result of a {@link FetchDownloadsLoader} with the files
     * and the amount of space they use.
     */
    public static class DownloadsResult {
        public long totalSize;
        public long youngestLastModified;
        public ArrayList<File> files;
        public HashMap<File, Bitmap> thumbnails;

        public DownloadsResult() {
            this(0, Long.MAX_VALUE, new ArrayList<File>(), new HashMap<>());
        }

        public DownloadsResult(
                long totalSize,
                long youngestLastModified,
                ArrayList<File> files,
                HashMap<File, Bitmap> thumbnails) {
            this.totalSize = totalSize;
            this.youngestLastModified = youngestLastModified;
            this.files = files;
            this.thumbnails = thumbnails;
        }
    }
}