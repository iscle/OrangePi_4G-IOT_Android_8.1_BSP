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

import android.content.Context;

import android.support.annotation.NonNull;
import com.android.tv.data.Channel;
import com.android.tv.data.Lineup;
import com.android.tv.data.Program;
import com.android.tv.dvr.data.SeriesInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A stub class to read EPG.
 */
public class StubEpgReader implements EpgReader{
    public StubEpgReader(@SuppressWarnings("unused") Context context) {
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public long getEpgTimestamp() {
        return 0;
    }

    @Override
    public void setRegionCode(String regionCode) {
        // Do nothing
    }

    @Override
    public List<Lineup> getLineups(@NonNull String postalCode) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getChannelNumbers(@NonNull String lineupId) {
        return Collections.emptyList();
    }

    @Override
    public List<Channel> getChannels(@NonNull String lineupId) {
        return Collections.emptyList();
    }

    @Override
    public void preloadChannels(@NonNull String lineupId) {
        // Do nothing
    }

    @Override
    public void clearCachedChannels(@NonNull String lineupId) {
        // Do nothing
    }

    @Override
    public List<Program> getPrograms(long channelId) {
        return Collections.emptyList();
    }

    @Override
    public Map<Long, List<Program>> getPrograms(@NonNull List<Long> channelIds, long duration) {
        return Collections.emptyMap();
    }

    @Override
    public SeriesInfo getSeriesInfo(@NonNull String seriesId) {
        return null;
    }
}