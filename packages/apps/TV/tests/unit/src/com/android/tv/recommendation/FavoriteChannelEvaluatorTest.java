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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link FavoriteChannelEvaluator}.
 */
@SmallTest
public class FavoriteChannelEvaluatorTest extends EvaluatorTestCase<FavoriteChannelEvaluator> {
    private static final int DEFAULT_NUMBER_OF_CHANNELS = 4;
    private static final long DEFAULT_WATCH_START_TIME_MS =
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2);
    private static final long DEFAULT_WATCH_END_TIME_MS =
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
    private static final long DEFAULT_MAX_WATCH_DURATION_MS = TimeUnit.HOURS.toMillis(1);

    @Override
    public FavoriteChannelEvaluator createEvaluator() {
        return new FavoriteChannelEvaluator();
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
        // For two channels which has ID x and y (x < y), the channel y is more watched
        // than the channel x. (Duration is longer than channel x)
        long latestWatchEndTimeMs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2);
        long durationMs = 0;
        List<Long> channelIdList = getChannelIdListSorted();
        for (long channelId : channelIdList) {
            durationMs += TimeUnit.MINUTES.toMillis(30);
            addWatchLog(channelId, latestWatchEndTimeMs, durationMs);
            latestWatchEndTimeMs += durationMs;
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
    public void testTwoChannelsWithSameWatchDuration() {
        long channelOne = addChannel().getId();
        long channelTwo = addChannel().getId();
        addWatchLog(channelOne, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
                TimeUnit.MINUTES.toMillis(30));
        addWatchLog(channelTwo, System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30),
                TimeUnit.MINUTES.toMillis(30));
        notifyChannelAndWatchLogLoaded();

        assertTrue(mEvaluator.evaluateChannel(channelOne) ==
                mEvaluator.evaluateChannel(channelTwo));
    }

    @Test
    public void testTwoChannelsWithDifferentWatchDuration() {
        long channelOne = addChannel().getId();
        long channelTwo = addChannel().getId();
        addWatchLog(channelOne, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3),
                TimeUnit.MINUTES.toMillis(30));
        addWatchLog(channelTwo, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2),
                TimeUnit.HOURS.toMillis(1));
        notifyChannelAndWatchLogLoaded();

        // Channel two was watched longer than channel one, so it's score is bigger.
        assertTrue(mEvaluator.evaluateChannel(channelOne) < mEvaluator.evaluateChannel(channelTwo));

        addWatchLog(channelOne, System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1),
                TimeUnit.HOURS.toMillis(1));

        // Now, channel one was watched longer than channel two, so it's score is bigger.
        assertTrue(mEvaluator.evaluateChannel(channelOne) > mEvaluator.evaluateChannel(channelTwo));
    }

    @Test
    public void testScoreIncreasesWithNewWatchLog() {
        long channelId = addChannel().getId();
        addRandomWatchLogs(DEFAULT_WATCH_START_TIME_MS, DEFAULT_WATCH_END_TIME_MS,
                DEFAULT_MAX_WATCH_DURATION_MS);
        notifyChannelAndWatchLogLoaded();

        long latestWatchEndTimeMs = getLatestWatchEndTimeMs();
        double previousScore = mEvaluator.evaluateChannel(channelId);

        addWatchLog(channelId, latestWatchEndTimeMs, TimeUnit.MINUTES.toMillis(10));

        // Score must be increased because total watch duration of the channel increases.
        assertTrue(previousScore <= mEvaluator.evaluateChannel(channelId));
    }
}
