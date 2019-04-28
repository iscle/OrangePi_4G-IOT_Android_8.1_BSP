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

package com.android.bluetooth.avrcpcontroller;

import android.bluetooth.BluetoothAvrcpPlayerSettings;
import android.media.session.PlaybackState;
import android.util.Log;

import java.util.ArrayList;

/*
 * Contains information about remote player
 */
class AvrcpPlayer {
    private static final String TAG = "AvrcpPlayer";
    private static final boolean DBG = true;

    public static final int INVALID_ID = -1;

    private int mPlayStatus = PlaybackState.STATE_NONE;
    private long mPlayTime   = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
    private int mId;
    private String mName = "";
    private int mPlayerType;
    private TrackInfo mCurrentTrack = new TrackInfo();

    AvrcpPlayer() {
        mId = INVALID_ID;
    }

    AvrcpPlayer(int id, String name, int transportFlags, int playStatus, int playerType) {
        mId = id;
        mName = name;
        mPlayerType = playerType;
    }

    public int getId() {
        return mId;
    }

    public String getName() {
      return mName;
    }

    public void setPlayTime(int playTime) {
        mPlayTime = playTime;
    }

    public long getPlayTime() {
        return mPlayTime;
    }

    public void setPlayStatus(int playStatus) {
        mPlayStatus = playStatus;
    }

    public PlaybackState getPlaybackState() {
        if (DBG) {
            Log.d(TAG, "getPlayBackState state " + mPlayStatus + " time " + mPlayTime);
        }

        long position = mPlayTime;
        float speed = 1;
        switch (mPlayStatus) {
            case PlaybackState.STATE_STOPPED:
                position = 0;
                speed = 0;
                break;
            case PlaybackState.STATE_PAUSED:
                speed = 0;
                break;
            case PlaybackState.STATE_FAST_FORWARDING:
                speed = 3;
                break;
            case PlaybackState.STATE_REWINDING:
                speed = -3;
                break;
        }
        return new PlaybackState.Builder().setState(mPlayStatus, position, speed).build();
    }

    synchronized public void updateCurrentTrack(TrackInfo update) {
        mCurrentTrack = update;
    }

    synchronized public TrackInfo getCurrentTrack() {
        return mCurrentTrack;
    }
}
