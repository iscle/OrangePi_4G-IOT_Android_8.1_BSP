/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.jni;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Describes a width and height in arbitrary units.
 */
public class SizeD implements Parcelable {
    private final double mWidth;
    private final double mHeight;

    public SizeD(double width, double height) {
        validate("width", width);
        validate("height", height);
        mWidth = width;
        mHeight = height;
    }

    /** Ensure the named value is finite and non-negative, or throw */
    private void validate(String name, double value) {
        if (value < 0 || !Double.isFinite(value)) {
            throw new IllegalArgumentException("invalid " + name + ": " + value);
        }
    }

    public SizeD(Parcel in) {
        this(in.readDouble(), in.readDouble());
    }

    public double getWidth() {
        return mWidth;
    }

    public double getHeight() {
        return mHeight;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int i) {
        out.writeDouble(mWidth);
        out.writeDouble(mHeight);
    }

    public static final Parcelable.Creator<SizeD> CREATOR = new Parcelable.Creator<SizeD>() {
        public SizeD createFromParcel(Parcel in) {
            return new SizeD(in);
        }

        public SizeD[] newArray(int size) {
            return new SizeD[size];
        }
    };
}