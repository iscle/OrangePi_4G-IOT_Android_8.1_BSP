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

import java.util.concurrent.TimeUnit;

public class RecentChannelEvaluator extends Recommender.Evaluator {
    private static final long WATCH_DURATION_MS_LOWER_BOUND = TimeUnit.MINUTES.toMillis(3);
    private static final long WATCH_DURATION_MS_UPPER_BOUND = TimeUnit.MINUTES.toMillis(7);

    private static final double MAX_SCORE_FOR_LOWER_BOUND = 0.1;

    private long mLastWatchLogUpdateTimeMs;

    public RecentChannelEvaluator() {
        mLastWatchLogUpdateTimeMs = System.currentTimeMillis();
    }

    @Override
    public void onNewWatchLog(ChannelRecord channelRecord) {
        mLastWatchLogUpdateTimeMs = System.currentTimeMillis();
    }

    @Override
    public double evaluateChannel(long channelId) {
        ChannelRecord cr = getRecommender().getChannelRecord(channelId);
        if (cr == null) {
            return NOT_RECOMMENDED;
        }
        WatchedProgram[] watchHistory = cr.getWatchHistory();
        double maxScore = 0.0;
        for (int i = watchHistory.length - 1; i >= 0; --i) {
            double recentWatchScore =
                    (double) watchHistory[i].getWatchEndTimeMs() / mLastWatchLogUpdateTimeMs;
            double watchDurationScore;
            double watchDuration = watchHistory[i].getWatchedDurationMs();
            if (watchDuration < WATCH_DURATION_MS_LOWER_BOUND) {
                watchDurationScore = MAX_SCORE_FOR_LOWER_BOUND;
            } else if (watchDuration < WATCH_DURATION_MS_UPPER_BOUND) {
                watchDurationScore = (watchDuration - WATCH_DURATION_MS_LOWER_BOUND)
                        / (WATCH_DURATION_MS_UPPER_BOUND - WATCH_DURATION_MS_LOWER_BOUND)
                        * (1 - MAX_SCORE_FOR_LOWER_BOUND) + MAX_SCORE_FOR_LOWER_BOUND;
            } else {
                watchDurationScore = 1.0;
            }
            maxScore = Math.max(maxScore, watchDurationScore * recentWatchScore);
        }
        return (maxScore > 0.0) ? maxScore : NOT_RECOMMENDED;
    }
}