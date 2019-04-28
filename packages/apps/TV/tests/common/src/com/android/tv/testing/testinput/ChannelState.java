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
package com.android.tv.testing.testinput;

import android.media.tv.TvTrackInfo;

import com.android.tv.testing.Constants;

import java.util.Collections;
import java.util.List;

/**
 * Versioned state information for a channel.
 */
public class ChannelState {

    /**
     * The video track a channel has by default.
     */
    public static final TvTrackInfo DEFAULT_VIDEO_TRACK = Constants.FHD1080P50_VIDEO_TRACK;
    /**
     * The video track a channel has by default.
     */
    public static final TvTrackInfo DEFAULT_AUDIO_TRACK = Constants.EN_STEREO_AUDIO_TRACK;
    /**
     * The channel is "tuned" and video available.
     *
     * @see #getTuneStatus()
     */
    public static final int TUNE_STATUS_VIDEO_AVAILABLE = -2;

    private static final int CHANNEL_VERSION_DEFAULT = 1;
    /**
     * Default ChannelState with version @{value #CHANNEL_VERSION_DEFAULT} and default {@link
     * ChannelStateData}.
     */
    public static final ChannelState DEFAULT = new ChannelState(CHANNEL_VERSION_DEFAULT,
            new ChannelStateData());
    private final int mVersion;
    private final ChannelStateData mData;


    private ChannelState(int version, ChannelStateData channelStateData) {
        mVersion = version;
        mData = channelStateData;
    }

    /**
     * Returns the id of the selected audio track, or null if none is selected.
     */
    public String getSelectedAudioTrackId() {
        return mData.mSelectedAudioTrackId;
    }

    /**
     * Returns the id of the selected audio track, or null if none is selected.
     */
    public String getSelectedVideoTrackId() {
        return mData.mSelectedVideoTrackId;
    }

    /**
     * The current version. Larger version numbers are newer.
     *
     * <p>The version is increased by {@link #next(ChannelStateData)}.
     */
    public int getVersion() {
        return mVersion;
    }

    /**
     * Tune status is either {@link #TUNE_STATUS_VIDEO_AVAILABLE} or a  {@link
     * android.media.tv.TvInputService.Session#notifyVideoUnavailable(int) video unavailable
     * reason}
     */
    public int getTuneStatus() {
        return mData.mTuneStatus;
    }

    /**
     * An unmodifiable list of TvTrackInfo for a channel, suitable for {@link
     * android.media.tv.TvInputService.Session#notifyTracksChanged(List)}
     */
    public List<TvTrackInfo> getTrackInfoList() {
        return Collections.unmodifiableList(mData.mTvTrackInfos);
    }

    @Override
    public String toString() {
        return "v" + mVersion + ":" + mData;
    }

    /**
     * Creates a new ChannelState, with an incremented version and {@code data} provided.
     *
     * @param data the data for the new ChannelState
     */
    public ChannelState next(ChannelStateData data) {
        return new ChannelState(mVersion + 1, data);
    }
}
