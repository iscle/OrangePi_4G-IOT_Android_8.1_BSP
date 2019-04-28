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

package com.android.documentsui.archives;

import com.android.internal.annotations.GuardedBy;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;

/**
 * Loads an instance of Archive lazily.
 */
public class Loader {
    private static final String TAG = "Loader";

    public static final int STATUS_OPENING = 0;
    public static final int STATUS_OPENED = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_CLOSING = 3;
    public static final int STATUS_CLOSED = 4;

    private final Context mContext;
    private final Uri mArchiveUri;
    private final int mAccessMode;
    private final Uri mNotificationUri;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private int mStatus = STATUS_OPENING;
    @GuardedBy("mLock")
    private int mRefCount = 0;
    private Archive mArchive = null;

    Loader(Context context, Uri archiveUri, int accessMode, Uri notificationUri) {
        this.mContext = context;
        this.mArchiveUri = archiveUri;
        this.mAccessMode = accessMode;
        this.mNotificationUri = notificationUri;

        // Start loading the archive immediately in the background.
        mExecutor.submit(this::get);
    }

    synchronized Archive get() {
        synchronized (mLock) {
            if (mStatus == STATUS_OPENED) {
                return mArchive;
            }
        }

        synchronized (mLock) {
            if (mStatus != STATUS_OPENING) {
                throw new IllegalStateException(
                        "Trying to perform an operation on an archive which is invalidated.");
            }
        }

        try {
            if (ReadableArchive.supportsAccessMode(mAccessMode)) {
                mArchive = ReadableArchive.createForParcelFileDescriptor(
                        mContext,
                        mContext.getContentResolver().openFileDescriptor(
                                mArchiveUri, "r", null /* signal */),
                        mArchiveUri, mAccessMode, mNotificationUri);
            } else if (WriteableArchive.supportsAccessMode(mAccessMode)) {
                mArchive = WriteableArchive.createForParcelFileDescriptor(
                        mContext,
                        mContext.getContentResolver().openFileDescriptor(
                                mArchiveUri, "w", null /* signal */),
                        mArchiveUri, mAccessMode, mNotificationUri);
            } else {
                throw new IllegalStateException("Access mode not supported.");
            }
            synchronized (mLock) {
                if (mRefCount == 0) {
                    mArchive.close();
                    mStatus = STATUS_CLOSED;
                } else {
                    mStatus = STATUS_OPENED;
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Failed to open the archive.", e);
            synchronized (mLock) {
                mStatus = STATUS_FAILED;
            }
            throw new IllegalStateException("Failed to open the archive.", e);
        } finally {
            synchronized (mLock) {
                // Only notify when there might be someone listening.
                if (mRefCount > 0) {
                    // Notify observers that the root directory is loaded (or failed)
                    // so clients reload it.
                    mContext.getContentResolver().notifyChange(
                            ArchivesProvider.buildUriForArchive(mArchiveUri, mAccessMode),
                            null /* observer */, false /* syncToNetwork */);
                }
            }
        }

        return mArchive;
    }

    int getStatus() {
        synchronized (mLock) {
            return mStatus;
        }
    }

    void acquire() {
        synchronized (mLock) {
            mRefCount++;
        }
    }

    void release() {
        synchronized (mLock) {
            mRefCount--;
            if (mRefCount == 0) {
                assert(mStatus == STATUS_OPENING
                        || mStatus == STATUS_OPENED
                        || mStatus == STATUS_FAILED);

                switch (mStatus) {
                    case STATUS_OPENED:
                        try {
                            mArchive.close();
                            mStatus = STATUS_CLOSED;
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to close the archive on release.", e);
                        }
                        break;
                    case STATUS_FAILED:
                        mStatus = STATUS_CLOSED;
                        break;
                    case STATUS_OPENING:
                        mStatus = STATUS_CLOSING;
                        // ::get() will close the archive once opened.
                        break;
                }
            }
        }
    }
}
