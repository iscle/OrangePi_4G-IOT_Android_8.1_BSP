/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.radio.service;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import java.util.Objects;

/**
 * An object that wraps the metadata for a particular radio station.
 */
public class RadioRds implements Parcelable {
    private String mProgramService;
    private String mSongArtist;
    private String mSongTitle;

    /**
     * @param programService The programme service for the current station This is typically the
     *                       call letters or station identity name.
     * @param songArtist The name of the artist for the current song.
     * @param songTitle The name of the current song.
     */
    public RadioRds(@Nullable String programService, @Nullable String songArtist,
            @Nullable String songTitle) {
        mProgramService = programService;
        mSongArtist = songArtist;
        mSongTitle = songTitle;
    }

    private RadioRds(Parcel in) {
        mProgramService = in.readString();
        mSongArtist = in.readString();
        mSongTitle = in.readString();
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mProgramService);
        out.writeString(mSongArtist);
        out.writeString(mSongTitle);
    }

    @Nullable
    public String getProgramService() {
        return mProgramService;
    }

    @Nullable
    public String getSongArtist() {
        return mSongArtist;
    }

    @Nullable
    public String getSongTitle() {
        return mSongTitle;
    }

    @Override
    public String toString() {
        return String.format("RadioRds [ps: %s, song artist: %s, song title: %s]",
                mProgramService, mSongArtist, mSongTitle);
    }

    /**
     * Returns {@code true} if two {@link RadioRds}s are equal. {@code RadioRds}s are considered
     * equal if all their fields are equal.
     */
    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (!(object instanceof RadioRds)) {
            return false;
        }

        RadioRds rds = (RadioRds) object;
        return Objects.equals(mProgramService, rds.getProgramService())
                && Objects.equals(mSongArtist, rds.getSongArtist())
                && Objects.equals(mSongTitle, rds.getSongTitle());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mProgramService, mSongArtist, mSongTitle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RadioRds> CREATOR = new
            Parcelable.Creator<RadioRds>() {
                public RadioRds createFromParcel(Parcel in) {
                    return new RadioRds(in);
                }

                public RadioRds[] newArray(int size) {
                    return new RadioRds[size];
                }
            };
}
