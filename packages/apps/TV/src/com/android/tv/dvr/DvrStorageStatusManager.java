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
 * limitations under the License
 */

package com.android.tv.dvr;

import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Looper;
import android.os.RemoteException;
import android.os.StatFs;
import android.support.annotation.AnyThread;
import android.support.annotation.IntDef;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.tuner.tvinput.TunerTvInputService;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Signals DVR storage status change such as plugging/unplugging.
 */
public class DvrStorageStatusManager {
    private static final String TAG = "DvrStorageStatusManager";
    private static final boolean DEBUG = false;

    /**
     * Minimum storage size to support DVR
     */
    public static final long MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES = 50 * 1024 * 1024 * 1024L; // 50GB
    private static final long MIN_FREE_STORAGE_SIZE_FOR_DVR_IN_BYTES
            = 10 * 1024 * 1024 * 1024L; // 10GB
    private static final String RECORDING_DATA_SUB_PATH = "/recording";

    private static final String[] PROJECTION = {
            TvContract.RecordedPrograms._ID,
            TvContract.RecordedPrograms.COLUMN_PACKAGE_NAME,
            TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI
    };
    private final static int BATCH_OPERATION_COUNT = 100;

    @IntDef({STORAGE_STATUS_OK, STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL,
            STORAGE_STATUS_FREE_SPACE_INSUFFICIENT, STORAGE_STATUS_MISSING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface StorageStatus {
    }

    /**
     * Current storage is OK to record a program.
     */
    public static final int STORAGE_STATUS_OK = 0;

    /**
     * Current storage's total capacity is smaller than DVR requirement.
     */
    public static final int STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL = 1;

    /**
     * Current storage's free space is insufficient to record programs.
     */
    public static final int STORAGE_STATUS_FREE_SPACE_INSUFFICIENT = 2;

    /**
     * Current storage is missing.
     */
    public static final int STORAGE_STATUS_MISSING = 3;

    private final Context mContext;
    private final Set<OnStorageMountChangedListener> mOnStorageMountChangedListeners =
            new CopyOnWriteArraySet<>();
    private final boolean mRunningInMainProcess;
    private MountedStorageStatus mMountedStorageStatus;
    private boolean mStorageValid;
    private CleanUpDbTask mCleanUpDbTask;

    private class MountedStorageStatus {
        private final boolean mStorageMounted;
        private final File mStorageMountedDir;
        private final long mStorageMountedCapacity;

        private MountedStorageStatus(boolean mounted, File mountedDir, long capacity) {
            mStorageMounted = mounted;
            mStorageMountedDir = mountedDir;
            mStorageMountedCapacity = capacity;
        }

        private boolean isValidForDvr() {
            return mStorageMounted && mStorageMountedCapacity >= MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MountedStorageStatus)) {
                return false;
            }
            MountedStorageStatus status = (MountedStorageStatus) other;
            return mStorageMounted == status.mStorageMounted
                    && Objects.equals(mStorageMountedDir, status.mStorageMountedDir)
                    && mStorageMountedCapacity == status.mStorageMountedCapacity;
        }
    }

    public interface OnStorageMountChangedListener {

        /**
         * Listener for DVR storage status change.
         *
         * @param storageMounted {@code true} when DVR possible storage is mounted,
         *                       {@code false} otherwise.
         */
        void onStorageMountChanged(boolean storageMounted);
    }

    private final class StorageStatusBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            MountedStorageStatus result = getStorageStatusInternal();
            if (mMountedStorageStatus.equals(result)) {
                return;
            }
            mMountedStorageStatus = result;
            if (result.mStorageMounted && mRunningInMainProcess) {
                // Cleans up DB in LC process.
                // Tuner process is not always on.
                if (mCleanUpDbTask != null) {
                    mCleanUpDbTask.cancel(true);
                }
                mCleanUpDbTask = new CleanUpDbTask();
                mCleanUpDbTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
            boolean valid = result.isValidForDvr();
            if (valid == mStorageValid) {
                return;
            }
            mStorageValid = valid;
            for (OnStorageMountChangedListener l : mOnStorageMountChangedListeners) {
                l.onStorageMountChanged(valid);
            }
        }
    }

    /**
     * Creates DvrStorageStatusManager.
     *
     * @param context {@link Context}
     */
    public DvrStorageStatusManager(final Context context, boolean runningInMainProcess) {
        mContext = context;
        mRunningInMainProcess = runningInMainProcess;
        mMountedStorageStatus = getStorageStatusInternal();
        mStorageValid = mMountedStorageStatus.isValidForDvr();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addDataScheme(ContentResolver.SCHEME_FILE);
        mContext.registerReceiver(new StorageStatusBroadcastReceiver(), filter);
    }

    /**
     * Adds the listener for receiving storage status change.
     *
     * @param listener
     */
    public void addListener(OnStorageMountChangedListener listener) {
        mOnStorageMountChangedListeners.add(listener);
    }

    /**
     * Removes the current listener.
     */
    public void removeListener(OnStorageMountChangedListener listener) {
        mOnStorageMountChangedListeners.remove(listener);
    }

    /**
     * Returns true if a storage is mounted.
     */
    public boolean isStorageMounted() {
        return mMountedStorageStatus.mStorageMounted;
    }

    /**
     * Returns the path to DVR recording data directory.
     * This can take for a while sometimes.
     */
    @WorkerThread
    public File getRecordingRootDataDirectory() {
        SoftPreconditions.checkState(Looper.myLooper() != Looper.getMainLooper());
        if (mMountedStorageStatus.mStorageMountedDir == null) {
            return null;
        }
        File root = mContext.getExternalFilesDir(null);
        String rootPath;
        try {
            rootPath = root != null ? root.getCanonicalPath() : null;
        } catch (IOException | SecurityException e) {
            return null;
        }
        return rootPath == null ? null : new File(rootPath + RECORDING_DATA_SUB_PATH);
    }

    /**
     * Returns the current storage status for DVR recordings.
     *
     * @return {@link StorageStatus}
     */
    @AnyThread
    public @StorageStatus int getDvrStorageStatus() {
        MountedStorageStatus status = mMountedStorageStatus;
        if (status.mStorageMountedDir == null) {
            return STORAGE_STATUS_MISSING;
        }
        if (CommonFeatures.FORCE_RECORDING_UNTIL_NO_SPACE.isEnabled(mContext)) {
            return STORAGE_STATUS_OK;
        }
        if (status.mStorageMountedCapacity < MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES) {
            return STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL;
        }
        try {
            StatFs statFs = new StatFs(status.mStorageMountedDir.toString());
            if (statFs.getAvailableBytes() < MIN_FREE_STORAGE_SIZE_FOR_DVR_IN_BYTES) {
                return STORAGE_STATUS_FREE_SPACE_INSUFFICIENT;
            }
        } catch (IllegalArgumentException e) {
            // In rare cases, storage status change was not notified yet.
            SoftPreconditions.checkState(false);
            return STORAGE_STATUS_FREE_SPACE_INSUFFICIENT;
        }
        return STORAGE_STATUS_OK;
    }

    /**
     * Returns whether the storage has sufficient storage.
     *
     * @return {@code true} when there is sufficient storage, {@code false} otherwise
     */
    public boolean isStorageSufficient() {
        return getDvrStorageStatus() == STORAGE_STATUS_OK;
    }

    private MountedStorageStatus getStorageStatusInternal() {
        boolean storageMounted =
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        File storageMountedDir = storageMounted ? Environment.getExternalStorageDirectory() : null;
        storageMounted = storageMounted && storageMountedDir != null;
        long storageMountedCapacity = 0L;
        if (storageMounted) {
            try {
                StatFs statFs = new StatFs(storageMountedDir.toString());
                storageMountedCapacity = statFs.getTotalBytes();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Storage mount status was changed.");
                storageMounted = false;
                storageMountedDir = null;
            }
        }
        return new MountedStorageStatus(
                storageMounted, storageMountedDir, storageMountedCapacity);
    }

    private class CleanUpDbTask extends AsyncTask<Void, Void, Boolean> {
        private final ContentResolver mContentResolver;

        private CleanUpDbTask() {
            mContentResolver = mContext.getContentResolver();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            @DvrStorageStatusManager.StorageStatus int storageStatus = getDvrStorageStatus();
            if (storageStatus == DvrStorageStatusManager.STORAGE_STATUS_MISSING) {
                return null;
            }
            if (storageStatus == DvrStorageStatusManager.STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL) {
                return true;
            }
            List<ContentProviderOperation> ops = getDeleteOps();
            if (ops == null || ops.isEmpty()) {
                return null;
            }
            Log.i(TAG, "New device storage mounted. # of recordings to be forgotten : "
                    + ops.size());
            for (int i = 0 ; i < ops.size() && !isCancelled() ; i += BATCH_OPERATION_COUNT) {
                int toIndex = (i + BATCH_OPERATION_COUNT) > ops.size()
                        ? ops.size() : (i + BATCH_OPERATION_COUNT);
                ArrayList<ContentProviderOperation> batchOps =
                        new ArrayList<>(ops.subList(i, toIndex));
                try {
                    mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, batchOps);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "Failed to clean up  RecordedPrograms.", e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Boolean forgetStorage) {
            if (forgetStorage != null && forgetStorage == true) {
                DvrManager dvrManager = TvApplication.getSingletons(mContext).getDvrManager();
                TvInputManagerHelper tvInputManagerHelper =
                        TvApplication.getSingletons(mContext).getTvInputManagerHelper();
                List<TvInputInfo> tvInputInfoList =
                        tvInputManagerHelper.getTvInputInfos(true, false);
                if (tvInputInfoList == null || tvInputInfoList.isEmpty()) {
                    return;
                }
                for (TvInputInfo info : tvInputInfoList) {
                    if (Utils.isBundledInput(info.getId())) {
                        dvrManager.forgetStorage(info.getId());
                    }
                }
            }
            if (mCleanUpDbTask == this) {
                mCleanUpDbTask = null;
            }
        }

        private List<ContentProviderOperation> getDeleteOps() {
            List<ContentProviderOperation> ops = new ArrayList<>();

            try (Cursor c = mContentResolver.query(
                    TvContract.RecordedPrograms.CONTENT_URI, PROJECTION, null, null, null)) {
                if (c == null) {
                    return null;
                }
                while (c.moveToNext()) {
                    @DvrStorageStatusManager.StorageStatus int storageStatus =
                            getDvrStorageStatus();
                    if (isCancelled()
                            || storageStatus == DvrStorageStatusManager.STORAGE_STATUS_MISSING) {
                        ops.clear();
                        break;
                    }
                    String id = c.getString(0);
                    String packageName = c.getString(1);
                    String dataUriString = c.getString(2);
                    if (dataUriString == null) {
                        continue;
                    }
                    Uri dataUri = Uri.parse(dataUriString);
                    if (!Utils.isInBundledPackageSet(packageName)
                            || dataUri == null || dataUri.getPath() == null
                            || !ContentResolver.SCHEME_FILE.equals(dataUri.getScheme())) {
                        continue;
                    }
                    File recordedProgramDir = new File(dataUri.getPath());
                    if (!recordedProgramDir.exists()) {
                        ops.add(ContentProviderOperation.newDelete(
                                TvContract.buildRecordedProgramUri(Long.parseLong(id))).build());
                    }
                }
                return ops;
            }
        }
    }
}
