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

package com.android.car.radio.service;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import java.util.Objects;

/**
 * A representation of a radio station.
 */
public class RadioStation implements Parcelable {
    private int mChannelNumber;
    private int mSubChannelNumber;
    private int mBand;
    private RadioRds mRds;

    /**
     * @param channelNumber Channel number in Hz.
     * @param subChannelNumber The subchannel number.
     * @param band One of {@link android.hardware.radio.RadioManager#BAND_AM},
     *             {@link android.hardware.radio.RadioManager#BAND_FM},
     *             {@link android.hardware.radio.RadioManager#BAND_AM_HD} or
     *             {@link android.hardware.radio.RadioManager#BAND_FM_HD}.
     * @param rds The Radio Data System for a particular channel. This represents the radio
     *            metadata.
     */
    public RadioStation(int channelNumber, int subChannelNumber, int band,
            @Nullable RadioRds rds) {
        mChannelNumber = channelNumber;
        mSubChannelNumber = subChannelNumber;
        mBand = band;
        mRds = rds;
    }

    private RadioStation(Parcel in) {
        mChannelNumber = in.readInt();
        mSubChannelNumber = in.readInt();
        mBand = in.readInt();
        mRds = in.readParcelable(RadioRds.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mChannelNumber);
        out.writeInt(mSubChannelNumber);
        out.writeInt(mBand);
        out.writeParcelable(mRds, 0);
    }

    public int getChannelNumber() {
        return mChannelNumber;
    }

    public int getSubChannelNumber() {
        return mSubChannelNumber;
    }

    public int getRadioBand() {
        return mBand;
    }

    @Nullable
    public RadioRds getRds() {
        return mRds;
    }

    @Override
    public String toString() {
        return String.format("RadioStation [channel: %s, subchannel: %s, band: %s, rds: %s]",
                mChannelNumber, mSubChannelNumber, mBand, mRds);
    }

    /**
     * Returns {@code true} if two {@link RadioStation}s are equal. RadioStations are considered
     * equal if they have the same channel and subchannel numbers.
     */
    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }

        if (!(object instanceof RadioStation)) {
            return false;
        }

        RadioStation station = (RadioStation) object;
        return station.getChannelNumber() == mChannelNumber
                && station.getSubChannelNumber() == mSubChannelNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mChannelNumber, mSubChannelNumber);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RadioStation> CREATOR = new
            Parcelable.Creator<RadioStation>() {
                public RadioStation createFromParcel(Parcel in) {
                    return new RadioStation(in);
                }

                public RadioStation[] newArray(int size) {
                    return new RadioStation[size];
                }
            };
}
