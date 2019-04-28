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

import static android.support.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tv.data.Channel;
import com.android.tv.recommendation.RecommendationUtils.ChannelRecordSortedMapHelper;
import com.android.tv.recommendation.Recommender.Evaluator;
import com.android.tv.testing.Utils;

import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

/**
 * Base test case for Recommendation Evaluator Unit tests.
 */
public abstract class EvaluatorTestCase<T extends Evaluator> {
    private static final long INVALID_CHANNEL_ID = -1;

    private static final double SCORE_DELTA = 0.01;

    private ChannelRecordSortedMapHelper mChannelRecordSortedMap;
    private RecommendationDataManager mDataManager;

    public T mEvaluator;

    @Before
    public void setUp() {
        mChannelRecordSortedMap = new ChannelRecordSortedMapHelper(getContext());
        mDataManager = RecommendationUtils
                .createMockRecommendationDataManager(mChannelRecordSortedMap);
        Recommender mRecommender = new FakeRecommender();
        mEvaluator = createEvaluator();
        mEvaluator.setRecommender(mRecommender);
        mChannelRecordSortedMap.setRecommender(mRecommender);
        mChannelRecordSortedMap.resetRandom(Utils.createTestRandom());
    }

    /**
     * Each evaluator test has to create Evaluator in {@code mEvaluator}.
     */
    public abstract T createEvaluator();

    public void addChannels(int numberOfChannels) {
        mChannelRecordSortedMap.addChannels(numberOfChannels);
    }

    public Channel addChannel() {
        return mChannelRecordSortedMap.addChannel();
    }

    public void addRandomWatchLogs(long watchStartTimeMs, long watchEndTimeMs,
            long maxWatchDurationMs) {
        assertTrue(mChannelRecordSortedMap.addRandomWatchLogs(watchStartTimeMs, watchEndTimeMs,
                maxWatchDurationMs));
    }

    public void addWatchLog(long channelId, long watchStartTimeMs, long durationTimeMs) {
        assertTrue(mChannelRecordSortedMap.addWatchLog(channelId, watchStartTimeMs,
                durationTimeMs));
    }

    public List<Long> getChannelIdListSorted() {
        return new ArrayList<>(mChannelRecordSortedMap.keySet());
    }

    public long getLatestWatchEndTimeMs() {
        long latestWatchEndTimeMs = 0;
        for (ChannelRecord channelRecord : mChannelRecordSortedMap.values()) {
            latestWatchEndTimeMs = Math.max(latestWatchEndTimeMs,
                    channelRecord.getLastWatchEndTimeMs());
        }
        return latestWatchEndTimeMs;
    }

    /**
     * Check whether scores of each channels are valid.
     */
    protected void assertChannelScoresValid() {
        assertEqualScores(Evaluator.NOT_RECOMMENDED,
                mEvaluator.evaluateChannel(INVALID_CHANNEL_ID));
        assertEqualScores(Evaluator.NOT_RECOMMENDED,
                mEvaluator.evaluateChannel(mChannelRecordSortedMap.size()));

        for (long channelId : mChannelRecordSortedMap.keySet()) {
            double score = mEvaluator.evaluateChannel(channelId);
            assertTrue("Channel " + channelId + " score of " + score + "is not valid",
                    score == Evaluator.NOT_RECOMMENDED || (0.0 <= score && score <= 1.0));
        }
    }

    /**
     * Notify that loading channels and watch logs are finished.
     */
    protected void notifyChannelAndWatchLogLoaded() {
        mEvaluator.onChannelRecordListChanged(new ArrayList<>(mChannelRecordSortedMap.values()));
    }

    void assertEqualScores(double expected, double actual) {
        assertEquals(expected, actual, SCORE_DELTA);
    }

    void assertEqualScores(String message, double expected, double actual) {
        assertEquals(message, expected, actual, SCORE_DELTA);
    }

    private class FakeRecommender extends Recommender {
        public FakeRecommender() {
            super(new Recommender.Listener() {
                @Override
                public void onRecommenderReady() {
                }

                @Override
                public void onRecommendationChanged() {
                }
            }, true, mDataManager);
        }

        @Override
        public ChannelRecord getChannelRecord(long channelId) {
            return mChannelRecordSortedMap.get(channelId);
        }
    }
}
