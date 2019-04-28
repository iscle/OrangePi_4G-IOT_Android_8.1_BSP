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

package com.android.tv.data.epg;

import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.android.tv.data.Channel;
import com.android.tv.data.Lineup;
import com.android.tv.data.Program;
import com.android.tv.dvr.data.SeriesInfo;

import java.util.List;
import java.util.Map;

/**
 * An interface used to retrieve the EPG data. This class should be used in worker thread.
 */
@WorkerThread
public interface EpgReader {
    /**
     * Checks if the reader is available.
     */
    boolean isAvailable();

    /**
     * Returns the timestamp of the current EPG.
     * The format should be YYYYMMDDHHmmSS as a long value. ex) 20160308141500
     */
    long getEpgTimestamp();

    /** Sets the region code. */
    void setRegionCode(String regionCode);

    /** Returns the lineups list. */
    List<Lineup> getLineups(@NonNull String postalCode);

    /**
     * Returns the list of channel numbers (unsorted) for the given lineup. The result is used to
     * choose the most appropriate lineup among others by comparing the channel numbers of the
     * existing channels on the device.
     */
    List<String> getChannelNumbers(@NonNull String lineupId);

    /**
     * Returns the list of channels for the given lineup. The returned channels should map into the
     * existing channels on the device. This method is usually called after selecting the lineup.
     */
    List<Channel> getChannels(@NonNull String lineupId);

    /** Pre-loads and caches channels for a given lineup. */
    void preloadChannels(@NonNull String lineupId);

    /**
     * Clears cached channels for a given lineup.
     */
    @AnyThread
    void clearCachedChannels(@NonNull String lineupId);

    /**
     * Returns the programs for the given channel. Must call {@link #getChannels(String)}
     * beforehand. Note that the {@code Program} doesn't have valid program ID because it's not
     * retrieved from TvProvider.
     */
    List<Program> getPrograms(long channelId);

    /**
     * Returns the programs for the given channels. Note that the {@code Program} doesn't have valid
     * program ID because it's not retrieved from TvProvider. This method is only used to get
     * programs for a short duration typically.
     */
    Map<Long, List<Program>> getPrograms(@NonNull List<Long> channelIds, long duration);

    /** Returns the series information for the given series ID. */
    SeriesInfo getSeriesInfo(@NonNull String seriesId);
}