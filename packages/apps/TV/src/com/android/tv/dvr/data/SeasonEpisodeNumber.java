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

package com.android.tv.dvr.data;

import android.text.TextUtils;

import java.util.Objects;

/**
 * A plain java object which includes the season/episode number for the series recording.
 */
public class SeasonEpisodeNumber {
    public final long seriesRecordingId;
    public final String seasonNumber;
    public final String episodeNumber;

    /**
     * Creates a new Builder with the values set from an existing {@link ScheduledRecording}.
     */
    public SeasonEpisodeNumber(ScheduledRecording r) {
        this(r.getSeriesRecordingId(), r.getSeasonNumber(), r.getEpisodeNumber());
    }

    public SeasonEpisodeNumber(long seriesRecordingId, String seasonNumber, String episodeNumber) {
        this.seriesRecordingId = seriesRecordingId;
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SeasonEpisodeNumber)
                || TextUtils.isEmpty(seasonNumber) || TextUtils.isEmpty(episodeNumber)) {
            return false;
        }
        SeasonEpisodeNumber that = (SeasonEpisodeNumber) o;
        return seriesRecordingId == that.seriesRecordingId
                && Objects.equals(seasonNumber, that.seasonNumber)
                && Objects.equals(episodeNumber, that.episodeNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(seriesRecordingId, seasonNumber, episodeNumber);
    }

    @Override
    public String toString() {
        return "SeasonEpisodeNumber{" +
                "seriesRecordingId=" + seriesRecordingId +
                ", seasonNumber='" + seasonNumber +
                ", episodeNumber=" + episodeNumber +
                '}';
    }
}