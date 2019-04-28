/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.util.List;

public class FavoriteChannelEvaluator extends Recommender.Evaluator {
    private static final long MIN_WATCH_PERIOD_MS = 1000 * 60 * 60 * 24;  // 1 day
    // When there is no watch history, use the current time as a default value.
    private long mEarliestWatchStartTimeMs = System.currentTimeMillis();

    @Override
    protected void onChannelRecordListChanged(List<ChannelRecord> channelRecords) {
        for (ChannelRecord cr : channelRecords) {
            WatchedProgram[] watchedPrograms = cr.getWatchHistory();
            if (watchedPrograms.length > 0
                    && mEarliestWatchStartTimeMs > watchedPrograms[0].getWatchStartTimeMs()) {
                mEarliestWatchStartTimeMs = watchedPrograms[0].getWatchStartTimeMs();
            }
        }
    }

    @Override
    public double evaluateChannel(long channelId) {
        ChannelRecord cr = getRecommender().getChannelRecord(channelId);
        if (cr == null) {
            return NOT_RECOMMENDED;
        }

        if (cr.getTotalWatchDurationMs() == 0) {
            return NOT_RECOMMENDED;
        }

        long watchPeriodMs = System.currentTimeMillis() - mEarliestWatchStartTimeMs;
        return (double) cr.getTotalWatchDurationMs() /
                Math.max(watchPeriodMs, MIN_WATCH_PERIOD_MS);
    }
}
