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

package android.car.hardware.radio;

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * CarPreset object corresponds to a preset that is stored on the car's Radio unit.
 * @hide
 */
@SystemApi
public class CarRadioPreset implements Parcelable {

    /*
     * Preset number at which this preset is stored.
     *
     * The value is 1 index based.
     */
    private final int mPresetNumber;
    /**
     * Radio band this preset belongs to.
     * See {@link RadioManager.BAND_FM}, {@link RadioManager.BAND_AM} etc.
     */
    private final int mBand;
    /**
     * Channel number.
     */
    private final int mChannel;
    /**
     * Sub channel number.
     */
    private final int mSubChannel;

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mPresetNumber);
        out.writeInt(mBand);
        out.writeInt(mChannel);
        out.writeInt(mSubChannel);
    }

    public static final Parcelable.Creator<CarRadioPreset> CREATOR
            = new Parcelable.Creator<CarRadioPreset>() {
        public CarRadioPreset createFromParcel(Parcel in) {
            return new CarRadioPreset(in);
        }

        public CarRadioPreset[] newArray(int size) {
            return new CarRadioPreset[size];
        }
    };

    private CarRadioPreset(Parcel in) {
        mPresetNumber = in.readInt();
        mBand = in.readInt();
        mChannel = in.readInt();
        mSubChannel = in.readInt();
    }

    public CarRadioPreset(int presetNumber, int bandType, int channel, int subChannel) {
        mPresetNumber = presetNumber;
        mBand = bandType;
        mChannel = channel;
        mSubChannel = subChannel;
    }

    // Getters.
    public int getPresetNumber() { return mPresetNumber; }

    public int getBand() { return mBand; }

    public int getChannel() { return mChannel; }

    public int getSubChannel() { return mSubChannel; }

    // Printer.
    public String toString() {
        return "Preset Number: " + mPresetNumber + "\n" +
            "Band: " + mBand + "\n" +
            "Channel: " + mChannel + "\n" +
            "Sub channel: " + mSubChannel;
    }

    // Comparator.
    public boolean equals(Object o) {
        CarRadioPreset that = (CarRadioPreset) o;

        return that.getPresetNumber() == mPresetNumber &&
            that.getBand() == mBand &&
            that.getChannel() == mChannel &&
            that.getSubChannel() == mSubChannel;

    }
}
