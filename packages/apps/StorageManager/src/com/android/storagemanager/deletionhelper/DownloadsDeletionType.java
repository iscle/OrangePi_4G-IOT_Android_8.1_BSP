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

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Loader;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.ArraySet;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.storagemanager.deletionhelper.FetchDownloadsLoader.DownloadsResult;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

/**
 * The DownloadsDeletionType provides stale download file information to the
 * {@link DownloadsDeletionPreferenceGroup}.
 */
public class DownloadsDeletionType implements DeletionType, LoaderCallbacks<DownloadsResult> {
    public static final String EXTRA_UNCHECKED_DOWNLOADS = "uncheckedFiles";
    private long mBytes;
    private long mMostRecent;
    private FreeableChangedListener mListener;
    private Context mContext;
    private ArraySet<File> mFiles;
    private ArraySet<String> mUncheckedFiles;
    private HashMap<File, Bitmap> mThumbnails;
    private int mLoadingStatus;

    public DownloadsDeletionType(Context context, String[] uncheckedFiles) {
        mLoadingStatus = LoadingStatus.LOADING;
        mContext = context;
        mFiles = new ArraySet<>();
        mUncheckedFiles = new ArraySet<>();
        if (uncheckedFiles != null) {
            Collections.addAll(mUncheckedFiles, uncheckedFiles);
        }
    }

    @Override
    public void registerFreeableChangedListener(FreeableChangedListener listener) {
        mListener = listener;
        if (mFiles != null) {
            maybeUpdateListener();
        }
    }

    @Override
    public void onResume() {
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onSaveInstanceStateBundle(Bundle savedInstanceState) {
        savedInstanceState.putStringArray(EXTRA_UNCHECKED_DOWNLOADS,
                mUncheckedFiles.toArray(new String[mUncheckedFiles.size()]));
    }

    @Override
    public void clearFreeableData(Activity activity) {
        if (mFiles != null) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    boolean succeeded = true;
                    for (File entry : mFiles) {
                        if (isChecked(entry)) {
                            succeeded = succeeded && entry.delete();
                        }
                    }

                    if (!succeeded) {
                        MetricsLogger.action(activity,
                                MetricsEvent.ACTION_DELETION_HELPER_DOWNLOADS_DELETION_FAIL);
                    }
                }
            });
        }
    }

    @Override
    public int getLoadingStatus() {
        return mLoadingStatus;
    }

    @Override
    public int getContentCount() {
        return mFiles.size();
    }

    @Override
    public void setLoadingStatus(@LoadingStatus int loadingStatus) {
        mLoadingStatus = loadingStatus;
    }

    @Override
    public Loader<DownloadsResult> onCreateLoader(int id, Bundle args) {
        return new FetchDownloadsLoader(mContext,
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
    }

    @Override
    public void onLoadFinished(Loader<DownloadsResult> loader, DownloadsResult data) {
        mMostRecent = data.youngestLastModified;
        for (File file : data.files) {
            mFiles.add(file);
        }
        mBytes = data.totalSize;
        mThumbnails = data.thumbnails;
        updateLoadingStatus();
        maybeUpdateListener();
    }

    @Override
    public void onLoaderReset(Loader<DownloadsResult> loader) {
    }

    /**
     * Returns the most recent last modified time for any clearable file.
     * @return The last modified time.
     */
    public long getMostRecentLastModified() {
        return mMostRecent;
    }

    /**
     * Returns the files in the Downloads folder after the loader task finishes.
     */
    public Set<File> getFiles() {
        if (mFiles == null) {
            return null;
        }
        return mFiles;
    }

    /**
     * Set if a file should be deleted when the service is asked to clear files.
     */
    public void setFileChecked(File file, boolean checked) {
        if (checked) {
            mUncheckedFiles.remove(file.getPath());
        } else {
            mUncheckedFiles.add(file.getPath());
        }
    }

    /** Returns the number of bytes that would be cleared if the deletion tasks runs. */
    public long getFreeableBytes(boolean countUnchecked) {
        long freedBytes = 0;
        for (File file : mFiles) {
            if (isChecked(file) || countUnchecked) {
                freedBytes += file.length();
            }
        }
        return freedBytes;
    }

    /** Returns a thumbnail for a given file, if it exists. If it does not exist, returns null. */
    public @Nullable Bitmap getCachedThumbnail(File imageFile) {
        if (mThumbnails == null) {
            return null;
        }
        return mThumbnails.get(imageFile);
    }

    /**
     * Return if a given file is checked for deletion.
     *
     * @param file The file to check.
     */
    public boolean isChecked(File file) {
        return !mUncheckedFiles.contains(file.getPath());
    }

    private void maybeUpdateListener() {
        if (mListener != null) {
            mListener.onFreeableChanged(mFiles.size(), mBytes);
        }
    }
}
