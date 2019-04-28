/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.util.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

public class LedConfiguration implements Parcelable {

    public static final Parcelable.Creator<LedConfiguration> CREATOR =
            new Parcelable.Creator<LedConfiguration>() {

        @Override
        public LedConfiguration createFromParcel(Parcel source) {
            int color0 = source.readInt();
            int color1 = source.readInt();
            boolean[] bools = new boolean[2];
                    source.readBooleanArray(bools);
            LedConfiguration config = new LedConfiguration(color0, color1, bools[0]);
            config.isTransient = bools[1];
            return config;
        }

        @Override
        public LedConfiguration[] newArray(int size) {
            return new LedConfiguration[size];
        }
    };

    public final int color0;
    public final int color1;
    public final boolean pulse;
    public boolean isTransient;

    public LedConfiguration(int color0, int color1, boolean pulse) {
        this.color0 = color0;
        this.color1 = color1;
        this.pulse = pulse;
    }

    public LedConfiguration(LedConfiguration that) {
        this.color0 = that.color0;
        this.color1 = that.color1;
        this.pulse = that.pulse;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LedConfiguration)) {
            return false;
        }
        final LedConfiguration that = (LedConfiguration)o;
        return areColorsEqual(that)
                && this.pulse == that.pulse
                && this.isTransient == that.isTransient;
    }

    public boolean areColorsEqual(LedConfiguration that) {
        if (that == null) {
            return false;
        }
        return (this.color0 == that.color0 && this.color1 == that.color1)
                || (this.color0 == that.color1 && this.color1 == that.color0);
    }

    @Override
    public String toString() {
        return "LedConfiguration(" + getNameString() + ")";
    }

    public String getNameString() {
        return String.format("#%06x-#%06x%s%s", color0 & 0x00ffffff, color1 & 0x00ffffff,
                        this.pulse ? "p" : "", this.isTransient ? "t" : "");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(color0);
        parcel.writeInt(color1);
        parcel.writeBooleanArray(new boolean[] {pulse, isTransient});
    }
}
