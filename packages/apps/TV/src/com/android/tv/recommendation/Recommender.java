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

import android.content.Context;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Pair;

import com.android.tv.data.Channel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Recommender implements RecommendationDataManager.Listener {
    private static final String TAG = "Recommender";

    @VisibleForTesting
    static final String INVALID_CHANNEL_SORT_KEY = "INVALID";
    private static final long MINIMUM_RECOMMENDATION_UPDATE_PERIOD = TimeUnit.MINUTES.toMillis(5);
    private static final Comparator<Pair<Channel, Double>> mChannelScoreComparator =
            new Comparator<Pair<Channel, Double>>() {
                @Override
                public int compare(Pair<Channel, Double> lhs, Pair<Channel, Double> rhs) {
                    // Sort the scores with descending order.
                    return rhs.second.compareTo(lhs.second);
                }
            };

    private final List<EvaluatorWrapper> mEvaluators = new ArrayList<>();
    private final boolean mIncludeRecommendedOnly;
    private final Listener mListener;

    private final Map<Long, String> mChannelSortKey = new HashMap<>();
    private final RecommendationDataManager mDataManager;
    private List<Channel> mPreviousRecommendedChannels = new ArrayList<>();
    private long mLastRecommendationUpdatedTimeUtcMillis;
    private boolean mChannelRecordLoaded;

    /**
     * Create a recommender object.
     *
     * @param includeRecommendedOnly true to include only recommended results, or false.
     */
    public Recommender(Context context, Listener listener, boolean includeRecommendedOnly) {
        mListener = listener;
        mIncludeRecommendedOnly = includeRecommendedOnly;
        mDataManager = RecommendationDataManager.acquireManager(context, this);
    }

    @VisibleForTesting
    Recommender(Listener listener, boolean includeRecommendedOnly,
            RecommendationDataManager dataManager) {
        mListener = listener;
        mIncludeRecommendedOnly = includeRecommendedOnly;
        mDataManager = dataManager;
    }

    public boolean isReady() {
        return mChannelRecordLoaded;
    }

    public void release() {
        mDataManager.release(this);
    }

    public void registerEvaluator(Evaluator evaluator) {
        registerEvaluator(evaluator,
                EvaluatorWrapper.DEFAULT_BASE_SCORE, EvaluatorWrapper.DEFAULT_WEIGHT);
    }

    /**
     * Register the evaluator used in recommendation.
     *
     * The range of evaluated scores by this evaluator will be between {@code baseScore} and
     * {@code baseScore} + {@code weight} (inclusive).

     * @param evaluator The evaluator to register inside this recommender.
     * @param baseScore Base(Minimum) score of the score evaluated by {@code evaluator}.
     * @param weight Weight value to rearrange the score evaluated by {@code evaluator}.
     */
    public void registerEvaluator(Evaluator evaluator, double baseScore, double weight) {
        mEvaluators.add(new EvaluatorWrapper(this, evaluator, baseScore, weight));
    }

    public List<Channel> recommendChannels() {
        return recommendChannels(mDataManager.getChannelRecordCount());
    }

    /**
     * Return the channel list of recommendation up to {@code n} or the number of channels.
     * During the evaluation, this method updates the channel sort key of recommended channels.
     *
     * @param size The number of channels that might be recommended.
     * @return Top {@code size} channels recommended sorted by score in descending order. If
     *         {@code size} is bigger than the number of channels, the number of results could
     *         be less than {@code size}.
     */
    public List<Channel> recommendChannels(int size) {
        List<Pair<Channel, Double>> records = new ArrayList<>();
        Collection<ChannelRecord> channelRecordList = mDataManager.getChannelRecords();
        for (ChannelRecord cr : channelRecordList) {
            double maxScore = Evaluator.NOT_RECOMMENDED;
            for (EvaluatorWrapper evaluator : mEvaluators) {
                double score = evaluator.getScaledEvaluatorScore(cr.getChannel().getId());
                if (score > maxScore) {
                    maxScore = score;
                }
            }
            if (!mIncludeRecommendedOnly || maxScore != Evaluator.NOT_RECOMMENDED) {
                records.add(new Pair<>(cr.getChannel(), maxScore));
            }
        }
        if (size > records.size()) {
            size = records.size();
        }
        Collections.sort(records, mChannelScoreComparator);

        List<Channel> results = new ArrayList<>();

        mChannelSortKey.clear();
        String sortKeyFormat = "%0" + String.valueOf(size).length() + "d";
        for (int i = 0; i < size; ++i) {
            // Channel with smaller sort key has higher priority.
            mChannelSortKey.put(records.get(i).first.getId(), String.format(sortKeyFormat, i));
            results.add(records.get(i).first);
        }
        return results;
    }

    /**
     * Returns the {@link Channel} object for a given channel ID from the channel pool that this
     * recommendation engine has.
     *
     * @param channelId The channel ID to retrieve the {@link Channel} object for.
     * @return the {@link Channel} object for the given channel ID, {@code null} if such a channel
     *         is not found.
     */
    public Channel getChannel(long channelId) {
        ChannelRecord record = mDataManager.getChannelRecord(channelId);
        return record == null ? null : record.getChannel();
    }

    /**
     * Returns the {@link ChannelRecord} object for a given channel ID.
     *
     * @param channelId The channel ID to receive the {@link ChannelRecord} object for.
     * @return the {@link ChannelRecord} object for the given channel ID.
     */
    public ChannelRecord getChannelRecord(long channelId) {
        return mDataManager.getChannelRecord(channelId);
    }

    /**
     * Returns the sort key of a given channel Id. Sort key is determined in
     * {@link #recommendChannels()} and getChannelSortKey must be called after that.
     *
     * If getChannelSortKey was called before evaluating the channels or trying to get sort key
     * of non-recommended channel, it returns {@link #INVALID_CHANNEL_SORT_KEY}.
     */
    public String getChannelSortKey(long channelId) {
        String key = mChannelSortKey.get(channelId);
        return key == null ? INVALID_CHANNEL_SORT_KEY : key;
    }

    @Override
    public void onChannelRecordLoaded() {
        mChannelRecordLoaded = true;
        mListener.onRecommenderReady();
        List<ChannelRecord> channels = new ArrayList<>(mDataManager.getChannelRecords());
        for (EvaluatorWrapper evaluator : mEvaluators) {
            evaluator.onChannelListChanged(Collections.unmodifiableList(channels));
        }
    }

    @Override
    public void onNewWatchLog(ChannelRecord channelRecord) {
        for (EvaluatorWrapper evaluator : mEvaluators) {
            evaluator.onNewWatchLog(channelRecord);
        }
        checkRecommendationChanged();
    }

    @Override
    public void onChannelRecordChanged() {
        if (mChannelRecordLoaded) {
            List<ChannelRecord> channels = new ArrayList<>(mDataManager.getChannelRecords());
            for (EvaluatorWrapper evaluator : mEvaluators) {
                evaluator.onChannelListChanged(Collections.unmodifiableList(channels));
            }
        }
        checkRecommendationChanged();
    }

    private void checkRecommendationChanged() {
        long currentTimeUtcMillis = System.currentTimeMillis();
        if (currentTimeUtcMillis - mLastRecommendationUpdatedTimeUtcMillis
                < MINIMUM_RECOMMENDATION_UPDATE_PERIOD) {
            return;
        }
        mLastRecommendationUpdatedTimeUtcMillis = currentTimeUtcMillis;
        List<Channel> recommendedChannels = recommendChannels();
        if (!recommendedChannels.equals(mPreviousRecommendedChannels)) {
            mPreviousRecommendedChannels = recommendedChannels;
            mListener.onRecommendationChanged();
        }
    }

    @VisibleForTesting
    void setLastRecommendationUpdatedTimeUtcMs(long newUpdatedTimeMs) {
        mLastRecommendationUpdatedTimeUtcMillis = newUpdatedTimeMs;
    }

    public static abstract class Evaluator {
        public static final double NOT_RECOMMENDED = -1.0;
        private Recommender mRecommender;

        protected Evaluator() {}

        protected void onChannelRecordListChanged(List<ChannelRecord> channelRecords) {
        }

        /**
         * This will be called when a new watch log comes into WatchedPrograms table.
         *
         * @param channelRecord The channel record corresponds to the new watch log.
         */
        protected void onNewWatchLog(ChannelRecord channelRecord) {
        }

        /**
         * The implementation should return the recommendation score for the given channel ID.
         * The return value should be in the range of [0.0, 1.0] or NOT_RECOMMENDED for denoting
         * that it gives up to calculate the score for the channel.
         *
         * @param channelId The channel ID which will be evaluated by this recommender.
         * @return The recommendation score
         */
        protected abstract double evaluateChannel(final long channelId);

        protected void setRecommender(Recommender recommender) {
            mRecommender = recommender;
        }

        protected Recommender getRecommender() {
            return mRecommender;
        }
    }

    private static class EvaluatorWrapper {
        private static final double DEFAULT_BASE_SCORE = 0.0;
        private static final double DEFAULT_WEIGHT = 1.0;

        private final Evaluator mEvaluator;
        // The minimum score of the Recommender unless it gives up to provide the score.
        private final double mBaseScore;
        // The weight of the recommender. The return-value of getScore() will be multiplied by
        // this value.
        private final double mWeight;

        public EvaluatorWrapper(Recommender recommender, Evaluator evaluator,
                double baseScore, double weight) {
            mEvaluator = evaluator;
            evaluator.setRecommender(recommender);
            mBaseScore = baseScore;
            mWeight = weight;
        }

        /**
         * This returns the scaled score for the given channel ID based on the returned value
         * of evaluateChannel().
         *
         * @param channelId The channel ID which will be evaluated by the recommender.
         * @return Returns the scaled score (mBaseScore + score * mWeight) when evaluateChannel() is
         *         in the range of [0.0, 1.0]. If evaluateChannel() returns NOT_RECOMMENDED or any
         *         negative numbers, it returns NOT_RECOMMENDED. If calculateScore() returns more
         *         than 1.0, it returns (mBaseScore + mWeight).
         */
        private double getScaledEvaluatorScore(long channelId) {
            double score = mEvaluator.evaluateChannel(channelId);
            if (score < 0.0) {
                if (score != Evaluator.NOT_RECOMMENDED) {
                    Log.w(TAG, "Unexpected score (" + score + ") from the recommender"
                            + mEvaluator);
                }
                // If the recommender gives up to calculate the score, return 0.0
                return Evaluator.NOT_RECOMMENDED;
            } else if (score > 1.0) {
                Log.w(TAG, "Unexpected score (" + score + ") from the recommender"
                        + mEvaluator);
                score = 1.0;
            }
            return mBaseScore + score * mWeight;
        }

        public void onNewWatchLog(ChannelRecord channelRecord) {
            mEvaluator.onNewWatchLog(channelRecord);
        }

        public void onChannelListChanged(List<ChannelRecord> channelRecords) {
            mEvaluator.onChannelRecordListChanged(channelRecords);
        }
    }

    public interface Listener {
        /**
         * Called after channel record map is loaded.
         */
        void onRecommenderReady();

        /**
         * Called when the recommendation changes.
         */
        void onRecommendationChanged();
    }
}
