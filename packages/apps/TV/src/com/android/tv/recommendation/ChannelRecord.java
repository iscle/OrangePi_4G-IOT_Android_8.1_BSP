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

import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.data.ProgramDataManager;
import com.android.tv.util.Utils;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChannelRecord {
    // TODO: decide the value for max history size.
    @VisibleForTesting static final int MAX_HISTORY_SIZE = 100;
    private final Context mContext;
    private final Deque<WatchedProgram> mWatchHistory;
    private Program mCurrentProgram;
    private Channel mChannel;
    private long mTotalWatchDurationMs;
    private boolean mInputRemoved;

    public ChannelRecord(Context context, Channel channel, boolean inputRemoved) {
        mContext = context;
        mChannel = channel;
        mWatchHistory = new ArrayDeque<>();
        mInputRemoved = inputRemoved;
    }

    public Channel getChannel() {
        return mChannel;
    }

    public void setChannel(Channel channel, boolean inputRemoved) {
        mChannel = channel;
        mInputRemoved = inputRemoved;
    }

    public boolean isInputRemoved() {
        return mInputRemoved;
    }

    public void setInputRemoved(boolean removed) {
        mInputRemoved = removed;
    }

    public long getLastWatchEndTimeMs() {
        WatchedProgram p = mWatchHistory.peekLast();
        return (p == null) ? 0 : p.getWatchEndTimeMs();
    }

    public Program getCurrentProgram() {
        long time = System.currentTimeMillis();
        if (mCurrentProgram == null || mCurrentProgram.getEndTimeUtcMillis() < time) {
            ProgramDataManager manager =
                    TvApplication.getSingletons(mContext).getProgramDataManager();
            mCurrentProgram = manager.getCurrentProgram(mChannel.getId());
        }
        return mCurrentProgram;
    }

    public long getTotalWatchDurationMs() {
        return mTotalWatchDurationMs;
    }

    public final WatchedProgram[] getWatchHistory() {
        return mWatchHistory.toArray(new WatchedProgram[mWatchHistory.size()]);
    }

    public void logWatchHistory(WatchedProgram p) {
        mWatchHistory.offer(p);
        mTotalWatchDurationMs += p.getWatchedDurationMs();
        if (mWatchHistory.size() > MAX_HISTORY_SIZE) {
            WatchedProgram program = mWatchHistory.poll();
            mTotalWatchDurationMs -= program.getWatchedDurationMs();
        }
    }
}
