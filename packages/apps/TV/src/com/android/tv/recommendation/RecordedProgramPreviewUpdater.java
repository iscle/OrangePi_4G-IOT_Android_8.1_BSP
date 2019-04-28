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

package com.android.tv.recommendation;

import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.TvApplication;
import com.android.tv.data.PreviewDataManager;
import com.android.tv.data.PreviewProgramContent;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.data.RecordedProgram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Class to update the preview data for {@link RecordedProgram}
 */
@RequiresApi(Build.VERSION_CODES.O)
public class RecordedProgramPreviewUpdater {
    private static final String TAG = "RecordedProgramPreviewUpdater";
    // STOPSHIP: set it to false.
    private static final boolean DEBUG = true;

    private static final int RECOMMENDATION_COUNT = 6;

    private static RecordedProgramPreviewUpdater sRecordedProgramPreviewUpdater;

    /**
     * Creates and returns the {@link RecordedProgramPreviewUpdater}.
     */
    public static RecordedProgramPreviewUpdater getInstance(Context context) {
        if (sRecordedProgramPreviewUpdater == null) {
            sRecordedProgramPreviewUpdater
                    = new RecordedProgramPreviewUpdater(context.getApplicationContext());
        }
        return sRecordedProgramPreviewUpdater;
    }

    private final Context mContext;
    private final PreviewDataManager mPreviewDataManager;
    private final DvrDataManager mDvrDataManager;

    private RecordedProgramPreviewUpdater(Context context) {
        mContext = context.getApplicationContext();
        ApplicationSingletons applicationSingletons = TvApplication.getSingletons(mContext);
        mPreviewDataManager = applicationSingletons.getPreviewDataManager();
        mDvrDataManager = applicationSingletons.getDvrDataManager();
        mDvrDataManager.addRecordedProgramListener(new DvrDataManager.RecordedProgramListener() {
            @Override
            public void onRecordedProgramsAdded(RecordedProgram... recordedPrograms) {
                if (DEBUG) Log.d(TAG, "Add new preview recorded programs");
                updatePreviewDataForRecordedPrograms();
            }

            @Override
            public void onRecordedProgramsChanged(RecordedProgram... recordedPrograms) {
                if (DEBUG) Log.d(TAG, "Update preview recorded programs");
                updatePreviewDataForRecordedPrograms();
            }

            @Override
            public void onRecordedProgramsRemoved(RecordedProgram... recordedPrograms) {
                if (DEBUG) Log.d(TAG, "Delete preview recorded programs");
                updatePreviewDataForRecordedPrograms();
            }
        });
    }

    /**
     * Updates the preview data for recorded programs.
     */
    public void updatePreviewDataForRecordedPrograms() {
        if (!mPreviewDataManager.isLoadFinished()) {
            mPreviewDataManager.addListener(new PreviewDataManager.PreviewDataListener() {
                @Override
                public void onPreviewDataLoadFinished() {
                    mPreviewDataManager.removeListener(this);
                    updatePreviewDataForRecordedPrograms();
                }

                @Override
                public void onPreviewDataUpdateFinished() { }
            });
            return;
        }
        if (!mDvrDataManager.isRecordedProgramLoadFinished()) {
            mDvrDataManager.addRecordedProgramLoadFinishedListener(
                    new DvrDataManager.OnRecordedProgramLoadFinishedListener() {
                @Override
                public void onRecordedProgramLoadFinished() {
                    mDvrDataManager.removeRecordedProgramLoadFinishedListener(this);
                    updatePreviewDataForRecordedPrograms();
                }
            });
            return;
        }
        updatePreviewDataForRecordedProgramsInternal();
    }

    private void updatePreviewDataForRecordedProgramsInternal() {
        Set<RecordedProgram> recordedPrograms = generateRecommendationRecordedPrograms();
        Long recordedPreviewChannelId = mPreviewDataManager.getPreviewChannelId(
                PreviewDataManager.TYPE_RECORDED_PROGRAM_PREVIEW_CHANNEL);
        if (recordedPreviewChannelId == PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID
                && !recordedPrograms.isEmpty()) {
            createPreviewChannelForRecordedPrograms();
        } else {
            mPreviewDataManager.updatePreviewProgramsForChannel(recordedPreviewChannelId,
                    generatePreviewProgramContentsFromRecordedPrograms(
                            recordedPreviewChannelId, recordedPrograms), null);
        }
    }

    private void createPreviewChannelForRecordedPrograms() {
        mPreviewDataManager.createPreviewChannel(
                PreviewDataManager.TYPE_RECORDED_PROGRAM_PREVIEW_CHANNEL,
                new PreviewDataManager.OnPreviewChannelCreationResultListener() {
                    @Override
                    public void onPreviewChannelCreationResult(long createdPreviewChannelId) {
                        if (createdPreviewChannelId
                                != PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID) {
                            updatePreviewDataForRecordedProgramsInternal();
                        }
                    }
                });
    }

    private Set<RecordedProgram> generateRecommendationRecordedPrograms() {
        Set<RecordedProgram> programs = new HashSet<>();
        ArrayList<RecordedProgram> sortedRecordedPrograms =
                new ArrayList<>(mDvrDataManager.getRecordedPrograms());
        Collections.sort(
                sortedRecordedPrograms, RecordedProgram.START_TIME_THEN_ID_COMPARATOR.reversed());
        for (RecordedProgram recordedProgram : sortedRecordedPrograms) {
            if (!TextUtils.isEmpty(recordedProgram.getPosterArtUri())) {
                programs.add(recordedProgram);
                if (programs.size() >= RECOMMENDATION_COUNT) {
                    break;
                }
            }
        }
        return programs;
    }

    private Set<PreviewProgramContent> generatePreviewProgramContentsFromRecordedPrograms(
            long previewChannelId, Set<RecordedProgram> recordedPrograms) {
        Set<PreviewProgramContent> result = new HashSet<>();
        for (RecordedProgram recordedProgram : recordedPrograms) {
            result.add(PreviewProgramContent.createFromRecordedProgram(mContext, previewChannelId,
                    recordedProgram));
        }
        return result;
    }
}
