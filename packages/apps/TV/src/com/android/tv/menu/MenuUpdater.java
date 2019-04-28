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

package com.android.tv.menu;

import android.support.annotation.Nullable;

import com.android.tv.ChannelTuner;
import com.android.tv.TvOptionsManager;
import com.android.tv.TvOptionsManager.OptionChangedListener;
import com.android.tv.TvOptionsManager.OptionType;
import com.android.tv.data.Channel;
import com.android.tv.menu.MenuRowFactory.TvOptionsRow;
import com.android.tv.ui.TunableTvView;
import com.android.tv.ui.TunableTvView.OnScreenBlockingChangedListener;

/**
 * Update menu items when needed.
 *
 * <p>As the menu is updated when it shows up, this class handles only the dynamic updates.
 */
public class MenuUpdater {
    private final Menu mMenu;
    // Can be null for testing.
    @Nullable private final TunableTvView mTvView;
    @Nullable private final TvOptionsManager mOptionsManager;
    private ChannelTuner mChannelTuner;

    private final ChannelTuner.Listener mChannelTunerListener = new ChannelTuner.Listener() {
        @Override
        public void onLoadFinished() {}

        @Override
        public void onBrowsableChannelListChanged() {
            mMenu.update(ChannelsRow.ID);
        }

        @Override
        public void onCurrentChannelUnavailable(Channel channel) {}

        @Override
        public void onChannelChanged(Channel previousChannel, Channel currentChannel) {
            mMenu.update(ChannelsRow.ID);
        }
    };
    private final OptionChangedListener mOptionChangeListener = new OptionChangedListener() {
        @Override
        public void onOptionChanged(@OptionType int optionType, String newString) {
            mMenu.update(TvOptionsRow.ID);
        }
    };

    public MenuUpdater(Menu menu, TunableTvView tvView, TvOptionsManager optionsManager) {
        mMenu = menu;
        mTvView = tvView;
        mOptionsManager = optionsManager;
        if (mTvView != null) {
            mTvView.setOnScreenBlockedListener(new OnScreenBlockingChangedListener() {
                    @Override
                    public void onScreenBlockingChanged(boolean blocked) {
                        mMenu.update(PlayControlsRow.ID);
                    }
            });
        }
        if (mOptionsManager != null) {
            mOptionsManager.setOptionChangedListener(TvOptionsManager.OPTION_CLOSED_CAPTIONS,
                    mOptionChangeListener);
            mOptionsManager.setOptionChangedListener(TvOptionsManager.OPTION_DISPLAY_MODE,
                    mOptionChangeListener);
            mOptionsManager.setOptionChangedListener(TvOptionsManager.OPTION_MULTI_AUDIO,
                    mOptionChangeListener);
        }
    }

    /**
     * Sets the instance of {@link ChannelTuner}. Call this method when the channel tuner is ready.
     */
    public void setChannelTuner(ChannelTuner channelTuner) {
        if (mChannelTuner != null) {
            mChannelTuner.removeListener(mChannelTunerListener);
        }
        mChannelTuner = channelTuner;
        if (mChannelTuner != null) {
            mChannelTuner.addListener(mChannelTunerListener);
        }
    }

    /**
     * Called when the stream information changes.
     */
    public void onStreamInfoChanged() {
        mMenu.update(TvOptionsRow.ID);
    }

    /**
     * Called at the end of the menu's lifetime.
     */
    public void release() {
        if (mChannelTuner != null) {
            mChannelTuner.removeListener(mChannelTunerListener);
        }
        if (mTvView != null) {
            mTvView.setOnScreenBlockedListener(null);
        }
        if (mOptionsManager != null) {
            mOptionsManager.setOptionChangedListener(TvOptionsManager.OPTION_CLOSED_CAPTIONS, null);
            mOptionsManager.setOptionChangedListener(TvOptionsManager.OPTION_DISPLAY_MODE, null);
            mOptionsManager.setOptionChangedListener(TvOptionsManager.OPTION_MULTI_AUDIO, null);
        }
    }
}
