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

import com.android.tv.data.Channel;
import com.android.tv.testing.Utils;

import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class RecommendationUtils {
    private static final long INVALID_CHANNEL_ID = -1;

    /**
     * Create a mock RecommendationDataManager backed by a {@link ChannelRecordSortedMapHelper}.
     */
    public static RecommendationDataManager createMockRecommendationDataManager(
            final ChannelRecordSortedMapHelper channelRecordSortedMap) {
        RecommendationDataManager dataManager = Mockito.mock(RecommendationDataManager.class);
        Mockito.doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) throws Throwable {
                return channelRecordSortedMap.size();
            }
        }).when(dataManager).getChannelRecordCount();
        Mockito.doAnswer(new Answer<Collection<ChannelRecord>>() {
            @Override
            public Collection<ChannelRecord> answer(InvocationOnMock invocation) throws Throwable {
                return channelRecordSortedMap.values();
            }
        }).when(dataManager).getChannelRecords();
        Mockito.doAnswer(new Answer<ChannelRecord>() {
            @Override
            public ChannelRecord answer(InvocationOnMock invocation) throws Throwable {
                long channelId = (long) invocation.getArguments()[0];
                return channelRecordSortedMap.get(channelId);
            }
        }).when(dataManager).getChannelRecord(Matchers.anyLong());
        return dataManager;
    }

    public static class ChannelRecordSortedMapHelper extends TreeMap<Long, ChannelRecord> {
        private final Context mContext;
        private Recommender mRecommender;
        private Random mRandom = Utils.createTestRandom();

        public ChannelRecordSortedMapHelper(Context context) {
            mContext = context;
        }

        public void setRecommender(Recommender recommender) {
            mRecommender = recommender;
        }

        public void resetRandom(Random random) {
            mRandom = random;
        }

        /**
         * Add new {@code numberOfChannels} channels by adding channel record to
         * {@code channelRecordMap} with no history.
         * This action corresponds to loading channels in the RecommendationDataManger.
         */
        public void addChannels(int numberOfChannels) {
            for (int i = 0; i < numberOfChannels; ++i) {
                addChannel();
            }
        }

        /**
         * Add new one channel by adding channel record to {@code channelRecordMap} with no history.
         * This action corresponds to loading one channel in the RecommendationDataManger.
         *
         * @return The new channel was made by this method.
         */
        public Channel addChannel() {
            long channelId = size();
            Channel channel = new Channel.Builder().setId(channelId).build();
            ChannelRecord channelRecord = new ChannelRecord(mContext, channel, false);
            put(channelId, channelRecord);
            return channel;
        }

        /**
         * Add the watch logs which its durationTime is under {@code maxWatchDurationMs}.
         * Add until latest watch end time becomes bigger than {@code watchEndTimeMs},
         * starting from {@code watchStartTimeMs}.
         *
         * @return true if adding watch log success, otherwise false.
         */
        public boolean addRandomWatchLogs(long watchStartTimeMs, long watchEndTimeMs,
                long maxWatchDurationMs) {
            long latestWatchEndTimeMs = watchStartTimeMs;
            long previousChannelId = INVALID_CHANNEL_ID;
            List<Long> channelIdList = new ArrayList<>(keySet());
            while (latestWatchEndTimeMs < watchEndTimeMs) {
                long channelId = channelIdList.get(mRandom.nextInt(channelIdList.size()));
                if (previousChannelId == channelId) {
                    // Time hopping with random minutes.
                    latestWatchEndTimeMs += TimeUnit.MINUTES.toMillis(mRandom.nextInt(30) + 1);
                }
                long watchedDurationMs = mRandom.nextInt((int) maxWatchDurationMs) + 1;
                if (!addWatchLog(channelId, latestWatchEndTimeMs, watchedDurationMs)) {
                    return false;
                }
                latestWatchEndTimeMs += watchedDurationMs;
                previousChannelId = channelId;
            }
            return true;
        }

        /**
         * Add new watch log to channel that id is {@code ChannelId}. Add watch log starts from
         * {@code watchStartTimeMs} with duration {@code durationTimeMs}. If adding is finished,
         * notify the recommender that there's a new watch log.
         *
         * @return true if adding watch log success, otherwise false.
         */
        public boolean addWatchLog(long channelId, long watchStartTimeMs, long durationTimeMs) {
            ChannelRecord channelRecord = get(channelId);
            if (channelRecord == null ||
                    watchStartTimeMs + durationTimeMs > System.currentTimeMillis()) {
                return false;
            }

            channelRecord.logWatchHistory(new WatchedProgram(null, watchStartTimeMs,
                    watchStartTimeMs + durationTimeMs));
            if (mRecommender != null) {
                mRecommender.onNewWatchLog(channelRecord);
            }
            return true;
        }
    }
}
