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

package com.android.tv;

import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Handler;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.util.TvInputManagerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * It manages the current tuned channel among browsable channels. And it determines the next channel
 * by channel up/down. But, it doesn't actually tune through TvView.
 */
@MainThread
public class ChannelTuner {
    private static final String TAG = "ChannelTuner";

    private boolean mStarted;
    private boolean mChannelDataManagerLoaded;
    private final List<Channel> mChannels = new ArrayList<>();
    private final List<Channel> mBrowsableChannels = new ArrayList<>();
    private final Map<Long, Channel> mChannelMap = new HashMap<>();
    // TODO: need to check that mChannelIndexMap can be removed, once mCurrentChannelIndex
    // is changed to mCurrentChannel(Id).
    private final Map<Long, Integer> mChannelIndexMap = new HashMap<>();

    private final Handler mHandler = new Handler();
    private final ChannelDataManager mChannelDataManager;
    private final Set<Listener> mListeners = new ArraySet<>();
    @Nullable
    private Channel mCurrentChannel;
    private final TvInputManagerHelper mInputManager;
    @Nullable
    private TvInputInfo mCurrentChannelInputInfo;

    private final ChannelDataManager.Listener mChannelDataManagerListener =
            new ChannelDataManager.Listener() {
                @Override
                public void onLoadFinished() {
                    mChannelDataManagerLoaded = true;
                    updateChannelData(mChannelDataManager.getChannelList());
                    for (Listener l : mListeners) {
                        l.onLoadFinished();
                    }
                }

                @Override
                public void onChannelListUpdated() {
                    updateChannelData(mChannelDataManager.getChannelList());
                }

                @Override
                public void onChannelBrowsableChanged() {
                    updateBrowsableChannels();
                    for (Listener l : mListeners) {
                        l.onBrowsableChannelListChanged();
                    }
                }
    };

    public ChannelTuner(ChannelDataManager channelDataManager, TvInputManagerHelper inputManager) {
        mChannelDataManager = channelDataManager;
        mInputManager = inputManager;
    }

    /**
     * Starts ChannelTuner. It cannot be called twice before calling {@link #stop}.
     */
    public void start() {
        if (mStarted) {
            throw new IllegalStateException("start is called twice");
        }
        mStarted = true;
        mChannelDataManager.addListener(mChannelDataManagerListener);
        if (mChannelDataManager.isDbLoadFinished()) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mChannelDataManagerListener.onLoadFinished();
                }
            });
        }
    }

    /**
     * Stops ChannelTuner.
     */
    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mHandler.removeCallbacksAndMessages(null);
        mChannelDataManager.removeListener(mChannelDataManagerListener);
        mCurrentChannel = null;
        mChannels.clear();
        mBrowsableChannels.clear();
        mChannelMap.clear();
        mChannelIndexMap.clear();
        mChannelDataManagerLoaded = false;
    }

    /**
     * Returns true, if all the channels are loaded.
     */
    public boolean areAllChannelsLoaded() {
        return mChannelDataManagerLoaded;
    }

    /**
     * Returns browsable channel lists.
     */
    public List<Channel> getBrowsableChannelList() {
        return Collections.unmodifiableList(mBrowsableChannels);
    }

    /**
     * Returns the number of browsable channels.
     */
    public int getBrowsableChannelCount() {
        return mBrowsableChannels.size();
    }

    /**
     * Returns the current channel.
     */
    @Nullable
    public Channel getCurrentChannel() {
        return mCurrentChannel;
    }

    /**
     * Sets the current channel. Call this method only when setting the current channel without
     * actually tuning to it.
     *
     * @param currentChannel The new current channel to set to.
     */
    public void setCurrentChannel(Channel currentChannel) {
        mCurrentChannel = currentChannel;
    }

    /**
     * Returns the current channel's ID.
     */
    public long getCurrentChannelId() {
        return mCurrentChannel != null ? mCurrentChannel.getId() : Channel.INVALID_ID;
    }

    /**
     * Returns the current channel's URI
     */
    public Uri getCurrentChannelUri() {
        if (mCurrentChannel == null) {
            return null;
        }
        if (mCurrentChannel.isPassthrough()) {
            return TvContract.buildChannelUriForPassthroughInput(mCurrentChannel.getInputId());
        } else {
            return TvContract.buildChannelUri(mCurrentChannel.getId());
        }
    }

    /**
     * Returns the current {@link TvInputInfo}.
     */
    @Nullable
    public TvInputInfo getCurrentInputInfo() {
        return mCurrentChannelInputInfo;
    }

    /**
     * Returns true, if the current channel is for a passthrough TV input.
     */
    public boolean isCurrentChannelPassthrough() {
        return mCurrentChannel != null && mCurrentChannel.isPassthrough();
    }

    /**
     * Moves the current channel to the next (or previous) browsable channel.
     *
     * @return true, if the channel is changed to the adjacent channel. If there is no
     *         browsable channel, it returns false.
     */
    public boolean moveToAdjacentBrowsableChannel(boolean up) {
        Channel channel = getAdjacentBrowsableChannel(up);
        if (channel == null) {
            return false;
        }
        setCurrentChannelAndNotify(mChannelMap.get(channel.getId()));
        return true;
    }

    /**
     * Returns a next browsable channel. It doesn't change the current channel unlike
     * {@link #moveToAdjacentBrowsableChannel}.
     */
    public Channel getAdjacentBrowsableChannel(boolean up) {
        if (isCurrentChannelPassthrough() || getBrowsableChannelCount() == 0) {
            return null;
        }
        int channelIndex;
        if (mCurrentChannel == null) {
            channelIndex = 0;
            Channel channel = mChannels.get(channelIndex);
            if (channel.isBrowsable()) {
                return channel;
            }
        } else {
            channelIndex = mChannelIndexMap.get(mCurrentChannel.getId());
        }
        int size = mChannels.size();
        for (int i = 0; i < size; ++i) {
            int nextChannelIndex = up ? channelIndex + 1 + i
                    : channelIndex - 1 - i + size;
            if (nextChannelIndex >= size) {
                nextChannelIndex -= size;
            }
            Channel channel = mChannels.get(nextChannelIndex);
            if (channel.isBrowsable()) {
                return channel;
            }
        }
        Log.e(TAG, "This code should not be reached");
        return null;
    }

    /**
     * Finds the nearest browsable channel from a channel with {@code channelId}. If the channel
     * with {@code channelId} is browsable, the channel will be returned.
     */
    public Channel findNearestBrowsableChannel(long channelId) {
        if (getBrowsableChannelCount() == 0) {
            return null;
        }
        Channel channel = mChannelMap.get(channelId);
        if (channel == null) {
            return mBrowsableChannels.get(0);
        } else if (channel.isBrowsable()) {
            return channel;
        }
        int index = mChannelIndexMap.get(channelId);
        int size = mChannels.size();
        for (int i = 1; i <= size / 2; ++i) {
            Channel upChannel = mChannels.get((index + i) % size);
            if (upChannel.isBrowsable()) {
                return upChannel;
            }
            Channel downChannel = mChannels.get((index - i + size) % size);
            if (downChannel.isBrowsable()) {
                return downChannel;
            }
        }
        throw new IllegalStateException(
                "This code should be unreachable in findNearestBrowsableChannel");
    }

    /**
     * Moves the current channel to {@code channel}. It can move to a non-browsable channel as well
     * as a browsable channel.
     *
     * @return true, the channel change is success. But, if the channel doesn't exist, the channel
     *         change will be failed and it will return false.
     */
    public boolean moveToChannel(Channel channel) {
        if (channel == null) {
            return false;
        }
        if (channel.isPassthrough()) {
            setCurrentChannelAndNotify(channel);
            return true;
        }
        SoftPreconditions.checkState(mChannelDataManagerLoaded, TAG, "Channel data is not loaded");
        Channel newChannel = mChannelMap.get(channel.getId());
        if (newChannel != null) {
            setCurrentChannelAndNotify(newChannel);
            return true;
        }
        return false;
    }

    /**
     * Resets the current channel to {@code null}.
     */
    public void resetCurrentChannel() {
        setCurrentChannelAndNotify(null);
    }

    /**
     * Adds {@link Listener}.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes {@link Listener}.
     */
    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    public interface Listener {
        /**
         * Called when all the channels are loaded.
         */
        void onLoadFinished();
        /**
         * Called when the browsable channel list is changed.
         */
        void onBrowsableChannelListChanged();
        /**
         * Called when the current channel is removed.
         */
        void onCurrentChannelUnavailable(Channel channel);
        /**
         * Called when the current channel is changed.
         */
        void onChannelChanged(Channel previousChannel, Channel currentChannel);
    }

    private void setCurrentChannelAndNotify(Channel channel) {
        if (mCurrentChannel == channel
                || (channel != null && channel.hasSameReadOnlyInfo(mCurrentChannel))) {
            return;
        }
        Channel previousChannel = mCurrentChannel;
        mCurrentChannel = channel;
        if (mCurrentChannel != null) {
            mCurrentChannelInputInfo = mInputManager.getTvInputInfo(mCurrentChannel.getInputId());
        }
        for (Listener l : mListeners) {
            l.onChannelChanged(previousChannel, mCurrentChannel);
        }
    }

    private void updateChannelData(List<Channel> channels) {
        mChannels.clear();
        mChannels.addAll(channels);

        mChannelMap.clear();
        mChannelIndexMap.clear();
        for (int i = 0; i < channels.size(); ++i) {
            Channel channel = channels.get(i);
            long channelId = channel.getId();
            mChannelMap.put(channelId, channel);
            mChannelIndexMap.put(channelId, i);
        }
        updateBrowsableChannels();

        if (mCurrentChannel != null && !mCurrentChannel.isPassthrough()) {
            Channel prevChannel = mCurrentChannel;
            setCurrentChannelAndNotify(mChannelMap.get(mCurrentChannel.getId()));
            if (mCurrentChannel == null) {
                for (Listener l : mListeners) {
                    l.onCurrentChannelUnavailable(prevChannel);
                }
            }
        }
        // TODO: Do not call onBrowsableChannelListChanged, when only non-browsable
        // channels are changed.
        for (Listener l : mListeners) {
            l.onBrowsableChannelListChanged();
        }
    }

    private void updateBrowsableChannels() {
        mBrowsableChannels.clear();
        for (Channel channel : mChannels) {
            if (channel.isBrowsable()) {
                mBrowsableChannels.add(channel);
            }
        }
    }
}
