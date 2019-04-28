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

package com.android.tv.data;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.media.tv.ChannelLogoUtils;
import android.support.media.tv.PreviewProgram;
import android.util.Log;
import android.util.Pair;

import com.android.tv.R;
import com.android.tv.util.PermissionUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Class to manage the preview data.
 */
@TargetApi(Build.VERSION_CODES.O)
@MainThread
public class PreviewDataManager {
    private static final String TAG = "PreviewDataManager";
    // STOPSHIP: set it to false.
    private static final boolean DEBUG = true;

    /**
     * Invalid preview channel ID.
     */
    public static final long INVALID_PREVIEW_CHANNEL_ID = -1;
    @IntDef({TYPE_DEFAULT_PREVIEW_CHANNEL, TYPE_RECORDED_PROGRAM_PREVIEW_CHANNEL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PreviewChannelType{}

    /**
     * Type of default preview channel
     */
    public static final long TYPE_DEFAULT_PREVIEW_CHANNEL = 1;
    /**
     * Type of recorded program channel
     */
    public static final long TYPE_RECORDED_PROGRAM_PREVIEW_CHANNEL = 2;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private boolean mLoadFinished;
    private PreviewData mPreviewData = new PreviewData();
    private final Set<PreviewDataListener> mPreviewDataListeners = new CopyOnWriteArraySet<>();

    private QueryPreviewDataTask mQueryPreviewTask;
    private final Map<Long, CreatePreviewChannelTask> mCreatePreviewChannelTasks =
            new HashMap<>();
    private final Map<Long, UpdatePreviewProgramTask> mUpdatePreviewProgramTasks = new HashMap<>();

    private final int mPreviewChannelLogoWidth;
    private final int mPreviewChannelLogoHeight;

    public PreviewDataManager(Context context) {
        mContext = context.getApplicationContext();
        mContentResolver = context.getContentResolver();
        mPreviewChannelLogoWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.preview_channel_logo_width);
        mPreviewChannelLogoHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.preview_channel_logo_height);
    }

    /**
     * Starts the preview data manager.
     */
    public void start() {
        if (mQueryPreviewTask == null) {
            mQueryPreviewTask = new QueryPreviewDataTask();
            mQueryPreviewTask.execute();
        }
    }

    /**
     * Stops the preview data manager.
     */
    public void stop() {
        if (mQueryPreviewTask != null) {
            mQueryPreviewTask.cancel(true);
        }
        for (CreatePreviewChannelTask createPreviewChannelTask
                : mCreatePreviewChannelTasks.values()) {
            createPreviewChannelTask.cancel(true);
        }
        for (UpdatePreviewProgramTask updatePreviewProgramTask
                : mUpdatePreviewProgramTasks.values()) {
            updatePreviewProgramTask.cancel(true);
        }

        mQueryPreviewTask = null;
        mCreatePreviewChannelTasks.clear();
        mUpdatePreviewProgramTasks.clear();
    }

    /**
     * Gets preview channel ID from the preview channel type.
     */
    public @PreviewChannelType long getPreviewChannelId(long previewChannelType) {
        return mPreviewData.getPreviewChannelId(previewChannelType);
    }

    /**
     * Creates default preview channel.
     */
    public void createDefaultPreviewChannel(
            OnPreviewChannelCreationResultListener onPreviewChannelCreationResultListener) {
        createPreviewChannel(TYPE_DEFAULT_PREVIEW_CHANNEL, onPreviewChannelCreationResultListener);
    }

    /**
     * Creates a preview channel for specific channel type.
     */
    public void createPreviewChannel(@PreviewChannelType long previewChannelType,
            OnPreviewChannelCreationResultListener onPreviewChannelCreationResultListener) {
        CreatePreviewChannelTask currentRunningCreateTask =
                mCreatePreviewChannelTasks.get(previewChannelType);
        if (currentRunningCreateTask == null) {
            CreatePreviewChannelTask createPreviewChannelTask = new CreatePreviewChannelTask(
                    previewChannelType);
            createPreviewChannelTask.addOnPreviewChannelCreationResultListener(
                    onPreviewChannelCreationResultListener);
            createPreviewChannelTask.execute();
            mCreatePreviewChannelTasks.put(previewChannelType, createPreviewChannelTask);
        } else {
            currentRunningCreateTask.addOnPreviewChannelCreationResultListener(
                    onPreviewChannelCreationResultListener);
        }
    }

    /**
     * Returns {@code true} if the preview data is loaded.
     */
    public boolean isLoadFinished() {
        return mLoadFinished;
    }

    /**
     * Adds listener.
     */
    public void addListener(PreviewDataListener previewDataListener) {
        mPreviewDataListeners.add(previewDataListener);
    }

    /**
     * Removes listener.
     */
    public void removeListener(PreviewDataListener previewDataListener) {
        mPreviewDataListeners.remove(previewDataListener);
    }

    /**
     * Updates the preview programs table for a specific preview channel.
     */
    public void updatePreviewProgramsForChannel(long previewChannelId,
            Set<PreviewProgramContent> programs, PreviewDataListener previewDataListener) {
        UpdatePreviewProgramTask currentRunningUpdateTask =
                mUpdatePreviewProgramTasks.get(previewChannelId);
        if (currentRunningUpdateTask != null
                && currentRunningUpdateTask.getPrograms().equals(programs)) {
            currentRunningUpdateTask.addPreviewDataListener(previewDataListener);
            return;
        }
        UpdatePreviewProgramTask updatePreviewProgramTask =
                new UpdatePreviewProgramTask(previewChannelId, programs);
        updatePreviewProgramTask.addPreviewDataListener(previewDataListener);
        if (currentRunningUpdateTask != null) {
            currentRunningUpdateTask.cancel(true);
            currentRunningUpdateTask.saveStatus();
            updatePreviewProgramTask.addPreviewDataListeners(
                    currentRunningUpdateTask.getPreviewDataListeners());
        }
        updatePreviewProgramTask.execute();
        mUpdatePreviewProgramTasks.put(previewChannelId, updatePreviewProgramTask);
    }

    private void notifyPreviewDataLoadFinished() {
        for (PreviewDataListener l : mPreviewDataListeners) {
            l.onPreviewDataLoadFinished();
        }
    }

    public interface PreviewDataListener {
        /**
         * Called when the preview data is loaded.
         */
        void onPreviewDataLoadFinished();

        /**
         * Called when the preview data is updated.
         */
        void onPreviewDataUpdateFinished();
    }

    public interface OnPreviewChannelCreationResultListener {
        /**
         * Called when the creation of preview channel is finished.
         * @param createdPreviewChannelId The preview channel ID if created successfully,
         *        otherwise it's {@value #INVALID_PREVIEW_CHANNEL_ID}.
         */
        void onPreviewChannelCreationResult(long createdPreviewChannelId);
    }

    private final class QueryPreviewDataTask extends AsyncTask<Void, Void, PreviewData> {
        private final String PARAM_PREVIEW = "preview";
        private final String mChannelSelection = TvContract.Channels.COLUMN_PACKAGE_NAME + "=?";

        @Override
        protected PreviewData doInBackground(Void... voids) {
            // Query preview channels and programs.
            if (DEBUG) Log.d(TAG, "QueryPreviewDataTask.doInBackground");
            PreviewData previewData = new PreviewData();
            try {
                Uri previewChannelsUri =
                        PreviewDataUtils.addQueryParamToUri(
                                TvContract.Channels.CONTENT_URI,
                                new Pair<>(PARAM_PREVIEW, String.valueOf(true)));
                String packageName = mContext.getPackageName();
                if (PermissionUtils.hasAccessAllEpg(mContext)) {
                    try (Cursor cursor =
                            mContentResolver.query(
                                    previewChannelsUri,
                                    android.support.media.tv.Channel.PROJECTION,
                                    mChannelSelection,
                                    new String[] {packageName},
                                    null)) {
                        if (cursor != null) {
                            while (cursor.moveToNext()) {
                                android.support.media.tv.Channel previewChannel =
                                        android.support.media.tv.Channel.fromCursor(cursor);
                                Long previewChannelType = previewChannel.getInternalProviderFlag1();
                                if (previewChannelType != null) {
                                    previewData.addPreviewChannelId(
                                            previewChannelType, previewChannel.getId());
                                }
                            }
                        }
                    }
                } else {
                    try (Cursor cursor =
                            mContentResolver.query(
                                    previewChannelsUri,
                                    android.support.media.tv.Channel.PROJECTION,
                                    null,
                                    null,
                                    null)) {
                        if (cursor != null) {
                            while (cursor.moveToNext()) {
                                android.support.media.tv.Channel previewChannel =
                                        android.support.media.tv.Channel.fromCursor(cursor);
                                Long previewChannelType = previewChannel.getInternalProviderFlag1();
                                if (previewChannel.getPackageName() == packageName
                                        && previewChannelType != null) {
                                    previewData.addPreviewChannelId(
                                            previewChannelType, previewChannel.getId());
                                }
                            }
                        }
                    }
                }

                for (long previewChannelId : previewData.getAllPreviewChannelIds().values()) {
                    Uri previewProgramsUriForPreviewChannel =
                            TvContract.buildPreviewProgramsUriForChannel(previewChannelId);
                    try (Cursor previewProgramCursor =
                            mContentResolver.query(
                                    previewProgramsUriForPreviewChannel,
                                    PreviewProgram.PROJECTION,
                                    null,
                                    null,
                                    null)) {
                        if (previewProgramCursor != null) {
                            while (previewProgramCursor.moveToNext()) {
                                PreviewProgram previewProgram =
                                        PreviewProgram.fromCursor(previewProgramCursor);
                                previewData.addPreviewProgram(previewProgram);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                Log.w(TAG, "Unable to get preview data", e);
            }
            return previewData;
        }

        @Override
        protected void onPostExecute(PreviewData result) {
            super.onPostExecute(result);
            if (mQueryPreviewTask == this) {
                mQueryPreviewTask = null;
                mPreviewData = new PreviewData(result);
                mLoadFinished = true;
                notifyPreviewDataLoadFinished();
            }
        }
    }

    private final class CreatePreviewChannelTask extends AsyncTask<Void, Void, Long> {
        private final long mPreviewChannelType;
        private Set<OnPreviewChannelCreationResultListener>
                mOnPreviewChannelCreationResultListeners = new CopyOnWriteArraySet<>();

        public CreatePreviewChannelTask(long previewChannelType) {
            mPreviewChannelType = previewChannelType;
        }

        public void addOnPreviewChannelCreationResultListener(
                OnPreviewChannelCreationResultListener onPreviewChannelCreationResultListener) {
            if (onPreviewChannelCreationResultListener != null) {
                mOnPreviewChannelCreationResultListeners.add(
                        onPreviewChannelCreationResultListener);
            }
        }

        @Override
        protected Long doInBackground(Void... params) {
            if (DEBUG) Log.d(TAG, "CreatePreviewChannelTask.doInBackground");
            long previewChannelId;
            try {
                Uri channelUri = mContentResolver.insert(TvContract.Channels.CONTENT_URI,
                        PreviewDataUtils.createPreviewChannel(mContext, mPreviewChannelType)
                                .toContentValues());
                if (channelUri != null) {
                    previewChannelId = ContentUris.parseId(channelUri);
                } else {
                    Log.e(TAG, "Fail to insert preview channel");
                    return INVALID_PREVIEW_CHANNEL_ID;
                }
            } catch (UnsupportedOperationException | NumberFormatException e) {
                Log.e(TAG, "Fail to get channel ID");
                return INVALID_PREVIEW_CHANNEL_ID;
            }
            Drawable appIcon = mContext.getApplicationInfo().loadIcon(mContext.getPackageManager());
            if (appIcon != null && appIcon instanceof BitmapDrawable) {
                ChannelLogoUtils.storeChannelLogo(mContext, previewChannelId,
                        Bitmap.createScaledBitmap(((BitmapDrawable) appIcon).getBitmap(),
                                mPreviewChannelLogoWidth, mPreviewChannelLogoHeight, false));
            }
            return previewChannelId;
        }

        @Override
        protected void onPostExecute(Long result) {
            super.onPostExecute(result);
            if (result != INVALID_PREVIEW_CHANNEL_ID) {
                mPreviewData.addPreviewChannelId(mPreviewChannelType, result);
            }
            for (OnPreviewChannelCreationResultListener onPreviewChannelCreationResultListener
                    : mOnPreviewChannelCreationResultListeners) {
                onPreviewChannelCreationResultListener.onPreviewChannelCreationResult(result);
            }
            mCreatePreviewChannelTasks.remove(mPreviewChannelType);
        }
    }

    /**
     * Updates the whole data which belongs to the package in preview programs table for a
     * specific preview channel with a set of {@link PreviewProgramContent}.
     */
    private final class UpdatePreviewProgramTask extends AsyncTask<Void, Void, Void> {
        private long mPreviewChannelId;
        private Set<PreviewProgramContent> mPrograms;
        private Map<Long, Long> mCurrentProgramId2PreviewProgramId;
        private Set<PreviewDataListener> mPreviewDataListeners = new CopyOnWriteArraySet<>();

        public UpdatePreviewProgramTask(long previewChannelId,
                Set<PreviewProgramContent> programs) {
            mPreviewChannelId = previewChannelId;
            mPrograms = programs;
            if (mPreviewData.getPreviewProgramIds(previewChannelId) == null) {
                mCurrentProgramId2PreviewProgramId = new HashMap<>();
            } else {
                mCurrentProgramId2PreviewProgramId = new HashMap<>(
                        mPreviewData.getPreviewProgramIds(previewChannelId));
            }
        }

        public void addPreviewDataListener(PreviewDataListener previewDataListener) {
            if (previewDataListener != null) {
                mPreviewDataListeners.add(previewDataListener);
            }
        }

        public void addPreviewDataListeners(Set<PreviewDataListener> previewDataListeners) {
            if (previewDataListeners != null) {
                mPreviewDataListeners.addAll(previewDataListeners);
            }
        }

        public Set<PreviewProgramContent> getPrograms() {
            return mPrograms;
        }

        public Set<PreviewDataListener> getPreviewDataListeners() {
            return mPreviewDataListeners;
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (DEBUG) Log.d(TAG, "UpdatePreviewProgamTask.doInBackground");
            Map<Long, Long> uncheckedPrograms = new HashMap<>(mCurrentProgramId2PreviewProgramId);
            for (PreviewProgramContent program : mPrograms) {
                if (isCancelled()) {
                    return null;
                }
                Long existingPreviewProgramId = uncheckedPrograms.remove(program.getId());
                if (existingPreviewProgramId != null) {
                    if (DEBUG) Log.d(TAG, "Preview program " + existingPreviewProgramId + " " +
                            "already exists for program " + program.getId());
                    continue;
                }
                try {
                    Uri programUri = mContentResolver.insert(TvContract.PreviewPrograms.CONTENT_URI,
                            PreviewDataUtils.createPreviewProgramFromContent(program)
                                    .toContentValues());
                    if (programUri != null) {
                        long previewProgramId = ContentUris.parseId(programUri);
                        mCurrentProgramId2PreviewProgramId.put(program.getId(), previewProgramId);
                        if (DEBUG) Log.d(TAG, "Add new preview program " + previewProgramId);
                    } else {
                        Log.e(TAG, "Fail to insert preview program");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Fail to get preview program ID");
                }
            }

            for (Long key : uncheckedPrograms.keySet()) {
                if (isCancelled()) {
                    return null;
                }
                try {
                    if (DEBUG) Log.d(TAG, "Remove preview program " + uncheckedPrograms.get(key));
                    mContentResolver.delete(TvContract.buildPreviewProgramUri(
                            uncheckedPrograms.get(key)), null, null);
                    mCurrentProgramId2PreviewProgramId.remove(key);
                } catch (Exception e) {
                    Log.e(TAG, "Fail to remove preview program " + uncheckedPrograms.get(key));
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mPreviewData.setPreviewProgramIds(
                    mPreviewChannelId, mCurrentProgramId2PreviewProgramId);
            mUpdatePreviewProgramTasks.remove(mPreviewChannelId);
            for (PreviewDataListener previewDataListener : mPreviewDataListeners) {
                previewDataListener.onPreviewDataUpdateFinished();
            }
        }

        public void saveStatus() {
            mPreviewData.setPreviewProgramIds(
                    mPreviewChannelId, mCurrentProgramId2PreviewProgramId);
        }
    }

    /**
     * Class to store the query result of preview data.
     */
    private static final class PreviewData {
        private Map<Long, Long> mPreviewChannelType2Id = new HashMap<>();
        private Map<Long, Map<Long, Long>> mProgramId2PreviewProgramId = new HashMap<>();

        PreviewData() {
            mPreviewChannelType2Id = new HashMap<>();
            mProgramId2PreviewProgramId = new HashMap<>();
        }

        PreviewData(PreviewData previewData) {
            mPreviewChannelType2Id = new HashMap<>(previewData.mPreviewChannelType2Id);
            mProgramId2PreviewProgramId = new HashMap<>(previewData.mProgramId2PreviewProgramId);
        }

        public void addPreviewProgram(PreviewProgram previewProgram) {
            long previewChannelId = previewProgram.getChannelId();
            Map<Long, Long> programId2PreviewProgram =
                    mProgramId2PreviewProgramId.get(previewChannelId);
            if (programId2PreviewProgram == null) {
                programId2PreviewProgram = new HashMap<>();
            }
            mProgramId2PreviewProgramId.put(previewChannelId, programId2PreviewProgram);
            if (previewProgram.getInternalProviderId() != null) {
                programId2PreviewProgram.put(
                        Long.parseLong(previewProgram.getInternalProviderId()),
                        previewProgram.getId());
            }
        }

        public @PreviewChannelType long getPreviewChannelId(long previewChannelType) {
            Long result = mPreviewChannelType2Id.get(previewChannelType);
            return result == null ? INVALID_PREVIEW_CHANNEL_ID : result;
        }

        public Map<Long, Long> getAllPreviewChannelIds() {
            return mPreviewChannelType2Id;
        }

        public void addPreviewChannelId(long previewChannelType, long previewChannelId) {
            mPreviewChannelType2Id.put(previewChannelType, previewChannelId);
        }

        public void removePreviewChannelId(long previewChannelType) {
            mPreviewChannelType2Id.remove(previewChannelType);
        }

        public void removePreviewChannel(long previewChannelId) {
            removePreviewChannelId(previewChannelId);
            removePreviewProgramIds(previewChannelId);
        }

        public Map<Long, Long> getPreviewProgramIds(long previewChannelId) {
            return mProgramId2PreviewProgramId.get(previewChannelId);
        }

        public Map<Long, Map<Long, Long>> getAllPreviewProgramIds() {
            return mProgramId2PreviewProgramId;
        }

        public void setPreviewProgramIds(
                long previewChannelId, Map<Long, Long> programId2PreviewProgramId) {
            mProgramId2PreviewProgramId.put(previewChannelId, programId2PreviewProgramId);
        }

        public void removePreviewProgramIds(long previewChannelId) {
            mProgramId2PreviewProgramId.remove(previewChannelId);
        }
    }

    /**
     * A utils class for preview data.
     */
    public final static class PreviewDataUtils {
        /**
         * Creates a preview channel.
         */
        public static android.support.media.tv.Channel createPreviewChannel(
                Context context, @PreviewChannelType long previewChannelType) {
            if (previewChannelType == TYPE_RECORDED_PROGRAM_PREVIEW_CHANNEL) {
                return createRecordedProgramPreviewChannel(context, previewChannelType);
            }
            return createDefaultPreviewChannel(context, previewChannelType);
        }

        private static android.support.media.tv.Channel createDefaultPreviewChannel(
                Context context, @PreviewChannelType long previewChannelType) {
            android.support.media.tv.Channel.Builder builder =
                    new android.support.media.tv.Channel.Builder();
            CharSequence appLabel =
                    context.getApplicationInfo().loadLabel(context.getPackageManager());
            CharSequence appDescription =
                    context.getApplicationInfo().loadDescription(context.getPackageManager());
            builder.setType(TvContract.Channels.TYPE_PREVIEW)
                    .setDisplayName(appLabel == null ? null : appLabel.toString())
                    .setDescription(appDescription == null ?  null : appDescription.toString())
                    .setAppLinkIntentUri(TvContract.Channels.CONTENT_URI)
                    .setInternalProviderFlag1(previewChannelType);
            return builder.build();
        }

        private static android.support.media.tv.Channel createRecordedProgramPreviewChannel(
                Context context, @PreviewChannelType long previewChannelType) {
            android.support.media.tv.Channel.Builder builder =
                    new android.support.media.tv.Channel.Builder();
            builder.setType(TvContract.Channels.TYPE_PREVIEW)
                    .setDisplayName(context.getResources().getString(
                            R.string.recorded_programs_preview_channel))
                    .setAppLinkIntentUri(TvContract.Channels.CONTENT_URI)
                    .setInternalProviderFlag1(previewChannelType);
            return builder.build();
        }

        /**
         * Creates a preview program.
         */
        public static PreviewProgram createPreviewProgramFromContent(
                PreviewProgramContent program) {
            PreviewProgram.Builder builder = new PreviewProgram.Builder();
            builder.setChannelId(program.getPreviewChannelId())
                    .setType(program.getType())
                    .setLive(program.getLive())
                    .setTitle(program.getTitle())
                    .setDescription(program.getDescription())
                    .setPosterArtUri(program.getPosterArtUri())
                    .setIntentUri(program.getIntentUri())
                    .setPreviewVideoUri(program.getPreviewVideoUri())
                    .setInternalProviderId(Long.toString(program.getId()));
            return builder.build();
        }

        /**
         * Appends query parameters to a Uri.
         */
        public static Uri addQueryParamToUri(Uri uri, Pair<String, String> param) {
            return uri.buildUpon().appendQueryParameter(param.first, param.second).build();
        }
    }
}
