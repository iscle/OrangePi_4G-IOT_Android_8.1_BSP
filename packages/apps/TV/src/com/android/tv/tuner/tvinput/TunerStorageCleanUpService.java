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

package com.android.tv.tuner.tvinput;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrStorageStatusManager;
import com.android.tv.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Creates {@link JobService} to clean up recorded program files which are not referenced
 * from database.
 */
public class TunerStorageCleanUpService extends JobService {
    private static final String TAG = "TunerStorageCleanUpService";

    private CleanUpStorageTask mTask;

    @Override
    public void onCreate() {
        if (!TvApplication.getSingletons(this).getTvInputManagerHelper().hasTvInputManager()) {
            Log.wtf(TAG, "Stopping because device does not have a TvInputManager");
            this.stopSelf();
            return;
        }
        TvApplication.setCurrentRunningProcess(this, false);
        super.onCreate();
        mTask = new CleanUpStorageTask(this, this);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /**
     * Cleans up recorded program files which are not referenced from database.
     * Cleaning up will be done periodically.
     */
    public static class CleanUpStorageTask extends AsyncTask<JobParameters, Void, JobParameters[]> {
        private final static String[] mProjection = {
                TvContract.RecordedPrograms.COLUMN_PACKAGE_NAME,
                TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI
        };
        private final static long ELAPSED_MILLIS_TO_DELETE = TimeUnit.DAYS.toMillis(1);

        private final Context mContext;
        private final DvrStorageStatusManager mDvrStorageStatusManager;
        private final JobService mJobService;
        private final ContentResolver mContentResolver;

        /**
         * Creates a recurring storage cleaning task.
         *
         * @param context {@link Context}
         * @param jobService {@link JobService}
         */
        public CleanUpStorageTask(Context context, JobService jobService) {
            mContext = context;
            mDvrStorageStatusManager =
                    TvApplication.getSingletons(mContext).getDvrStorageStatusManager();
            mJobService = jobService;
            mContentResolver = mContext.getContentResolver();
        }

        private Set<String> getRecordedProgramsDirs() {
            try (Cursor c = mContentResolver.query(
                    TvContract.RecordedPrograms.CONTENT_URI, mProjection, null, null, null)) {
                if (c == null) {
                    return null;
                }
                Set<String> recordedProgramDirs = new HashSet<>();
                while (c.moveToNext()) {
                    String packageName = c.getString(0);
                    String dataUriString = c.getString(1);
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
                    try {
                        recordedProgramDirs.add(recordedProgramDir.getCanonicalPath());
                    } catch (IOException | SecurityException e) {
                    }
                }
                return recordedProgramDirs;
            }
        }

        @Override
        protected JobParameters[] doInBackground(JobParameters... params) {
            if (mDvrStorageStatusManager.getDvrStorageStatus()
                    == DvrStorageStatusManager.STORAGE_STATUS_MISSING) {
                return params;
            }
            File dvrRecordingDir = mDvrStorageStatusManager.getRecordingRootDataDirectory();
            if (dvrRecordingDir == null || !dvrRecordingDir.isDirectory()) {
                return params;
            }
            Set<String> recordedProgramDirs = getRecordedProgramsDirs();
            if (recordedProgramDirs == null) {
                return params;
            }
            File[] files = dvrRecordingDir.listFiles();
            if (files == null || files.length == 0) {
                return params;
            }
            for (File recordingDir : files) {
                try {
                    if (!recordedProgramDirs.contains(recordingDir.getCanonicalPath())) {
                        long lastModified = recordingDir.lastModified();
                        long now = System.currentTimeMillis();
                        if (lastModified != 0
                                && lastModified < now - ELAPSED_MILLIS_TO_DELETE) {
                            // To prevent current recordings from being deleted,
                            // deletes recordings which was not modified for long enough time.
                            Utils.deleteDirOrFile(recordingDir);
                        }
                    }
                } catch (IOException | SecurityException e) {
                    // would not happen
                }
            }
            return params;
        }

        @Override
        protected void onPostExecute(JobParameters[] params) {
            for (JobParameters param : params) {
                mJobService.jobFinished(param, false);
            }
        }
    }
}
