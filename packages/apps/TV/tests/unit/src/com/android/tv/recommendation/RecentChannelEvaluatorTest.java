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

import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link RecentChannelEvaluator}.
 */
@SmallTest
public class RecentChannelEvaluatorTest extends EvaluatorTestCase<RecentChannelEvaluator> {
    private static final int DEFAULT_NUMBER_OF_CHANNELS = 4;
    private static final long DEFAULT_WATCH_START_TIME_MS =
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2);
    private static final long DEFAULT_WATCH_END_TIME_MS =
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
    private static final long DEFAULT_MAX_WATCH_DURATION_MS = TimeUnit.HOURS.toMillis(1);

    @Override
    public RecentChannelEvaluator createEvaluator() {
        return new RecentChannelEvaluator();
    }

    @Test
    public void testOneChannelWithNoWatchLog() {
        long channelId = addChannel().getId();
        notifyChannelAndWatchLogLoaded();

        assertEqualScores(Recommender.Evaluator.NOT_RECOMMENDED,
                mEvaluator.evaluateChannel(channelId));
    }

    @Test
    public void testOneChannelWithRandomWatchLogs() {
        addChannel();
        addRandomWatchLogs(DEFAULT_WATCH_START_TIME_MS, DEFAULT_WATCH_END_TIME_MS,
                DEFAULT_MAX_WATCH_DURATION_MS);
        notifyChannelAndWatchLogLoaded();

        assertChannelScoresValid();
    }

    @Test
    public void testMultiChannelsWithNoWatchLog() {
        addChannels(DEFAULT_NUMBER_OF_CHANNELS);
        notifyChannelAndWatchLogLoaded();

        List<Long> channelIdList = getChannelIdListSorted();
        for (long channelId : channelIdList) {
            assertEqualScores(Recommender.Evaluator.NOT_RECOMMENDED,
                    mEvaluator.evaluateChannel(channelId));
        }
    }

    @Test
    public void testMultiChannelsWithRandomWatchLogs() {
        addChannels(DEFAULT_NUMBER_OF_CHANNELS);
        addRandomWatchLogs(DEFAULT_WATCH_START_TIME_MS, DEFAULT_WATCH_END_TIME_MS,
                DEFAULT_MAX_WATCH_DURATION_MS);
        notifyChannelAndWatchLogLoaded();

        assertChannelScoresValid();
    }

    @Test
    public void testMultiChannelsWithSimpleWatchLogs() {
        addChannels(DEFAULT_NUMBER_OF_CHANNELS);
        // Every channel has one watch log with 1 hour. Also, for two channels
        // which has ID x and y (x < y), the channel y is watched later than the channel x.
        long latestWatchEndTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2);
        List<Long> channelIdList = getChannelIdListSorted();
        for (long channelId : channelIdList) {
            addWatchLog(channelId, latestWatchEndTimeMs, TimeUnit.HOURS.toMillis(1));
            latestWatchEndTimeMs += TimeUnit.HOURS.toMillis(1);
        }
        notifyChannelAndWatchLogLoaded();

        assertChannelScoresValid();
        // Channel score must be increased as channel ID increased.
        double previousScore = Recommender.Evaluator.NOT_RECOMMENDED;
        for (long channelId : channelIdList) {
            double score = mEvaluator.evaluateChannel(channelId);
            assertTrue(previousScore <= score);
            previousScore = score;
        }
    }

    @Test
    public void testScoreIncreasesWithNewWatchLog() {
        addChannels(DEFAULT_NUMBER_OF_CHANNELS);
        addRandomWatchLogs(DEFAULT_WATCH_START_TIME_MS, DEFAULT_WATCH_END_TIME_MS,
                DEFAULT_MAX_WATCH_DURATION_MS);
        notifyChannelAndWatchLogLoaded();

        List<Long> channelIdList = getChannelIdListSorted();
        long latestWatchEndTimeMs = getLatestWatchEndTimeMs();
        for (long channelId : channelIdList) {
            double previousScore = mEvaluator.evaluateChannel(channelId);

            long durationMs = TimeUnit.MINUTES.toMillis(10);
            addWatchLog(channelId, latestWatchEndTimeMs, durationMs);
            latestWatchEndTimeMs += durationMs;

            // Score must be increased because recentness of the log increases.
            assertTrue(previousScore <= mEvaluator.evaluateChannel(channelId));
        }
    }

    @Test
    public void testScoreDecreasesWithIncrementOfWatchedLogUpdatedTime() {
        addChannels(DEFAULT_NUMBER_OF_CHANNELS);
        addRandomWatchLogs(DEFAULT_WATCH_START_TIME_MS, DEFAULT_WATCH_END_TIME_MS,
                DEFAULT_MAX_WATCH_DURATION_MS);
        notifyChannelAndWatchLogLoaded();

        Map<Long, Double> scores = new HashMap<>();
        List<Long> channelIdList = getChannelIdListSorted();
        long latestWatchedEndTimeMs = getLatestWatchEndTimeMs();

        for (long channelId : channelIdList) {
            scores.put(channelId, mEvaluator.evaluateChannel(channelId));
        }

        long newChannelId = addChannel().getId();
        addWatchLog(newChannelId, latestWatchedEndTimeMs, TimeUnit.MINUTES.toMillis(10));

        for (long channelId : channelIdList) {
            // Score must be decreased because LastWatchLogUpdateTime increases by new log.
            assertTrue(mEvaluator.evaluateChannel(channelId) <= scores.get(channelId));
        }
    }
}
