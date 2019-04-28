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
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable unversioned channel state.
 */
public final class ChannelStateData implements Parcelable {
    public static final Creator<ChannelStateData> CREATOR = new Creator<ChannelStateData>() {
        @Override
        public ChannelStateData createFromParcel(Parcel in) {
            return new ChannelStateData(in);
        }

        @Override
        public ChannelStateData[] newArray(int size) {
            return new ChannelStateData[size];
        }
    };

    public final List<TvTrackInfo> mTvTrackInfos = new ArrayList<>();
    public int mTuneStatus = ChannelState.TUNE_STATUS_VIDEO_AVAILABLE;
    public String mSelectedAudioTrackId = ChannelState.DEFAULT_AUDIO_TRACK.getId();
    public String mSelectedVideoTrackId = ChannelState.DEFAULT_VIDEO_TRACK.getId();

    public ChannelStateData() {
        mTvTrackInfos.add(ChannelState.DEFAULT_VIDEO_TRACK);
        mTvTrackInfos.add(ChannelState.DEFAULT_AUDIO_TRACK);
    }

    private ChannelStateData(Parcel in) {
        mTuneStatus = in.readInt();
        in.readTypedList(mTvTrackInfos, TvTrackInfo.CREATOR);
        mSelectedAudioTrackId = in.readString();
        mSelectedVideoTrackId = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTuneStatus);
        dest.writeTypedList(mTvTrackInfos);
        dest.writeString(mSelectedAudioTrackId);
        dest.writeString(mSelectedVideoTrackId);
    }

    @Override
    public String toString() {
        return "{"
                + "tune=" + mTuneStatus
                + ", tracks=" + mTvTrackInfos
                + "}";
    }
}
