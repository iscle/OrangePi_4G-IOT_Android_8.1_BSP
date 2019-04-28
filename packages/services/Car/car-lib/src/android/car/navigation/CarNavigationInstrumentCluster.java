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
package android.car.navigation;

import android.annotation.IntDef;
import android.annotation.SystemApi;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Holds options related to navigation for the car's instrument cluster.
 * @hide
 */
@SystemApi
public class CarNavigationInstrumentCluster implements Parcelable {

    /** Navigation Next Turn messages contain an image, as well as an enum. */
    public static final int CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED = 1;
    /** Navigation Next Turn messages contain only an enum. */
    public static final int CLUSTER_TYPE_IMAGE_CODES_ONLY = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED,
        CLUSTER_TYPE_IMAGE_CODES_ONLY
    })
    public @interface ClusterType {}

    private int mMinIntervalMillis;

    @ClusterType
    private final int mType;

    private final int mImageWidth;

    private final int mImageHeight;

    private final int mImageColorDepthBits;

    private final Bundle mExtra;

    public static final Parcelable.Creator<CarNavigationInstrumentCluster> CREATOR
            = new Parcelable.Creator<CarNavigationInstrumentCluster>() {
        public CarNavigationInstrumentCluster createFromParcel(Parcel in) {
            return new CarNavigationInstrumentCluster(in);
        }

        public CarNavigationInstrumentCluster[] newArray(int size) {
            return new CarNavigationInstrumentCluster[size];
        }
    };

    public static CarNavigationInstrumentCluster createCluster(int minIntervalMillis) {
        return new CarNavigationInstrumentCluster(minIntervalMillis, CLUSTER_TYPE_IMAGE_CODES_ONLY,
                0, 0, 0);
    }

    public static CarNavigationInstrumentCluster createCustomImageCluster(int minIntervalMs,
            int imageWidth, int imageHeight, int imageColorDepthBits) {
        return new CarNavigationInstrumentCluster(minIntervalMs,
                CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED,
                imageWidth, imageHeight, imageColorDepthBits);
    }

    /** Minimum time between instrument cluster updates in milliseconds.*/
    public int getMinIntervalMillis() {
        return mMinIntervalMillis;
    }

    /**
     * Type of instrument cluster, can be {@link #CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED} or
     * {@link #CLUSTER_TYPE_IMAGE_CODES_ONLY}.
     */
    @ClusterType
    public int getType() {
        return mType;
    }

    /** If instrument cluster is image, width of instrument cluster in pixels. */
    public int getImageWidth() {
        return mImageWidth;
    }

    /** If instrument cluster is image, height of instrument cluster in pixels. */
    public int getImageHeight() {
        return mImageHeight;
    }

    /**
     * Contains extra information about instrument cluster.
     * @hide
     */
    public Bundle getExtra() { return mExtra; }

    /**
     * If instrument cluster is image, number of bits of colour depth it supports (8, 16, or 32).
     */
    public int getImageColorDepthBits() {
        return mImageColorDepthBits;
    }

    public CarNavigationInstrumentCluster(CarNavigationInstrumentCluster that) {
      this(that.mMinIntervalMillis,
          that.mType,
          that.mImageWidth,
          that.mImageHeight,
          that.mImageColorDepthBits);
    }

    /**
     * Whether cluster support custom image or not.
     * @return
     */
    public boolean supportsCustomImages() {
      return mType == CLUSTER_TYPE_CUSTOM_IMAGES_SUPPORTED;
    }

    private CarNavigationInstrumentCluster(
            int minIntervalMillis,
            @ClusterType int type,
            int imageWidth,
            int imageHeight,
            int imageColorDepthBits) {
        mMinIntervalMillis = minIntervalMillis;
        mType = type;
        mImageWidth = imageWidth;
        mImageHeight = imageHeight;
        mImageColorDepthBits = imageColorDepthBits;
        mExtra = new Bundle();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mMinIntervalMillis);
        dest.writeInt(mType);
        dest.writeInt(mImageWidth);
        dest.writeInt(mImageHeight);
        dest.writeInt(mImageColorDepthBits);
        dest.writeBundle(mExtra);
    }

    private CarNavigationInstrumentCluster(Parcel in) {
        mMinIntervalMillis = in.readInt();
        mType = in.readInt();
        mImageWidth = in.readInt();
        mImageHeight = in.readInt();
        mImageColorDepthBits = in.readInt();
        mExtra = in.readBundle(getClass().getClassLoader());
    }

    /** Converts to string for debug purpose */
    @Override
    public String toString() {
        return CarNavigationInstrumentCluster.class.getSimpleName() + "{ " +
                "minIntervalMillis: " + mMinIntervalMillis + ", " +
                "type: " + mType + ", " +
                "imageWidth: " + mImageWidth + ", " +
                "imageHeight: " + mImageHeight + ", " +
                "imageColourDepthBits: " + mImageColorDepthBits +
                "extra: " + mExtra + " }";
    }
}
