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

package com.android.tv.dvr.ui.list;

import com.android.tv.data.Program;
import com.android.tv.dvr.data.SeriesRecording;

import java.util.List;

/**
 * A base class for the rows for schedules' header.
 */
abstract class SchedulesHeaderRow {
    private String mTitle;
    private String mDescription;
    private int mItemCount;

    public SchedulesHeaderRow(String title, String description, int itemCount) {
        mTitle = title;
        mItemCount = itemCount;
        mDescription = description;
    }

    /**
     * Sets title.
     */
    public void setTitle(String title) {
        mTitle = title;
    }

    /**
     * Sets description.
     */
    public void setDescription(String description) {
        mDescription = description;
    }

    /**
     * Sets count of items.
     */
    public void setItemCount(int itemCount) {
        mItemCount = itemCount;
    }

    /**
     * Returns title.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Returns description.
     */
    public String getDescription() {
        return mDescription;
    }

    /**
     * Returns count of items.
     */
    public int getItemCount() {
        return mItemCount;
    }

    /**
     * The header row which represent the date.
     */
    public static class DateHeaderRow extends SchedulesHeaderRow {
        private long mDeadLineMs;

        public DateHeaderRow(String title, String description, int itemCount, long deadLineMs) {
            super(title, description, itemCount);
            mDeadLineMs = deadLineMs;
        }

        /**
         * Returns the latest time of the list which belongs to the header row.
         */
        public long getDeadLineMs() {
            return mDeadLineMs;
        }
    }

    /**
     * The header row which represent the series recording.
     */
    public static class SeriesRecordingHeaderRow extends SchedulesHeaderRow {
        private SeriesRecording mSeriesRecording;
        private List<Program> mPrograms;

        public SeriesRecordingHeaderRow(String title, String description, int itemCount,
                SeriesRecording series, List<Program> programs) {
            super(title, description, itemCount);
            mSeriesRecording = series;
            mPrograms = programs;
        }

        /**
         * Returns the list of programs which belong to the series.
         */
        public List<Program> getPrograms() {
            return mPrograms;
        }

        /**
         * Returns the series recording, it is for series schedules list.
         */
        public SeriesRecording getSeriesRecording() {
            return mSeriesRecording;
        }

        /**
         * Sets the series recording.
         */
        public void setSeriesRecording(SeriesRecording seriesRecording) {
            mSeriesRecording = seriesRecording;
        }
    }
}