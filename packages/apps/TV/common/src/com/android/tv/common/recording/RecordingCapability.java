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
 * limitations under the License
 */

package com.android.tv.common.recording;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Static representation of the recording capability of a TvInputService.
 */
public final class RecordingCapability implements Parcelable{
    /**
     * The inputId this capability represents.
     */
    public final String inputId;

    /**
     * The max number of concurrent sessions that require a tuner.
     *
     * <p>Both recording and playing live TV requires a Tuner.
     */
    public final int maxConcurrentTunedSessions;

    /**
     * The max number concurrent session that play a stream.
     *
     *<p>This is often limited by the number of decoders available.
     * The count includes both playing live TV and playing a recorded stream.
     */
    public final int maxConcurrentPlayingSessions;

    /**
     * Max number of concurrent sessions all types.
     *
     * <p>This may be limited by bandwidth or CPU or other factors.
     */
    public final int maxConcurrentSessionsOfAllTypes;

    /**
     * True if a tuned session can support recording and playback from the same resource.
     */
    public final boolean playbackWhileRecording;

    private RecordingCapability(String inputId, int maxConcurrentTunedSessions,
            int maxConcurrentPlayingSessions, int maxConcurrentSessionsOfAllTypes,
            boolean playbackWhileRecording) {
        this.inputId = inputId;
        this.maxConcurrentTunedSessions = maxConcurrentTunedSessions;
        this.maxConcurrentPlayingSessions = maxConcurrentPlayingSessions;
        this.maxConcurrentSessionsOfAllTypes = maxConcurrentSessionsOfAllTypes;
        this.playbackWhileRecording = playbackWhileRecording;
    }

    protected RecordingCapability(Parcel in) {
        inputId = in.readString();
        maxConcurrentTunedSessions = in.readInt();
        maxConcurrentPlayingSessions = in.readInt();
        maxConcurrentSessionsOfAllTypes = in.readInt();
        playbackWhileRecording = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(inputId);
        parcel.writeInt(maxConcurrentTunedSessions);
        parcel.writeInt(maxConcurrentPlayingSessions);
        parcel.writeInt(maxConcurrentSessionsOfAllTypes);
        parcel.writeByte((byte) (playbackWhileRecording ? 1 : 0));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecordingCapability)) {
            return false;
        }
        RecordingCapability that = (RecordingCapability) o;
        return Objects.equals(maxConcurrentTunedSessions, that.maxConcurrentTunedSessions) &&
                Objects.equals(maxConcurrentPlayingSessions, that.maxConcurrentPlayingSessions) &&
                Objects.equals(maxConcurrentSessionsOfAllTypes,
                        that.maxConcurrentSessionsOfAllTypes) &&
                Objects.equals(playbackWhileRecording, that.playbackWhileRecording) &&
                Objects.equals(inputId, that.inputId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inputId);
    }

    @Override
    public String toString() {
        return "RecordingCapability{" +
                "inputId='" + inputId + '\'' +
                ", maxConcurrentTunedSessions=" + maxConcurrentTunedSessions +
                ", maxConcurrentPlayingSessions=" + maxConcurrentPlayingSessions +
                ", maxConcurrentSessionsOfAllTypes=" + maxConcurrentSessionsOfAllTypes +
                ", playbackWhileRecording=" + playbackWhileRecording +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<RecordingCapability> CREATOR = new Creator<RecordingCapability>() {
        @Override
        public RecordingCapability createFromParcel(Parcel in) {
            return new RecordingCapability(in);
        }

        @Override
        public RecordingCapability[] newArray(int size) {
            return new RecordingCapability[size];
        }
    };

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String mInputId;
        private int mMaxConcurrentTunedSessions;
        private int mMaxConcurrentPlayingSessions;
        private int mMaxConcurrentSessionsOfAllTypes;
        private boolean mPlaybackWhileRecording;

        public Builder setInputId(String inputId) {
            mInputId = inputId;
            return this;
        }

        public Builder setMaxConcurrentTunedSessions(int maxConcurrentTunedSessions) {
            mMaxConcurrentTunedSessions = maxConcurrentTunedSessions;
            return this;
        }

        public Builder setMaxConcurrentPlayingSessions(int maxConcurrentPlayingSessions) {
            mMaxConcurrentPlayingSessions = maxConcurrentPlayingSessions;
            return this;
        }

        public Builder setMaxConcurrentSessionsOfAllTypes(int maxConcurrentSessionsOfAllTypes) {
            mMaxConcurrentSessionsOfAllTypes = maxConcurrentSessionsOfAllTypes;
            return this;
        }

        public Builder setPlaybackWhileRecording(boolean playbackWhileRecording) {
            mPlaybackWhileRecording = playbackWhileRecording;
            return this;
        }

        public RecordingCapability build() {
            return new RecordingCapability(mInputId, mMaxConcurrentTunedSessions,
                    mMaxConcurrentPlayingSessions, mMaxConcurrentSessionsOfAllTypes,
                    mPlaybackWhileRecording);
        }
    }
}


