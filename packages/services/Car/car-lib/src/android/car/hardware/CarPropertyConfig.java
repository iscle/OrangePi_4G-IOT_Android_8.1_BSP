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

package android.car.hardware;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.VehicleAreaType;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

import java.lang.reflect.Array;

/**
 * Represents general information about car property such as data type and min/max ranges for car
 * areas (if applicable). This class supposed to be immutable, parcelable and could be passed over.
 *
 * <p>Use {@link CarPropertyConfig#newBuilder} to create an instance of this class.
 *
 * @param <T> refer to Parcel#writeValue(Object) to get a list of all supported types. The class
 * should be visible to framework as default class loader is being used here.
 *
 * @hide
 */
@SystemApi
public class CarPropertyConfig<T> implements Parcelable {
    private final int mPropertyId;
    private final Class<T> mType;
    private final int mAreaType;
    private final SparseArray<AreaConfig<T>> mSupportedAreas;

    private CarPropertyConfig(Class<T> type, int propertyId, int areaType,
            SparseArray<AreaConfig<T>> supportedAreas) {
        mPropertyId = propertyId;
        mType = type;
        mAreaType = areaType;
        mSupportedAreas = supportedAreas;
    }

    public int getPropertyId() { return mPropertyId; }
    public Class<T> getPropertyType() { return mType; }
    public @VehicleAreaType.VehicleAreaTypeValue int getAreaType() { return mAreaType; }

    /** Returns true if this property doesn't hold car area-specific configuration */
    public boolean isGlobalProperty() {
        return mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_NONE;
    }

    public int getAreaCount() {
        return mSupportedAreas.size();
    }

    public int[] getAreaIds() {
        int[] areaIds = new int[mSupportedAreas.size()];
        for (int i = 0; i < areaIds.length; i++) {
            areaIds[i] = mSupportedAreas.keyAt(i);
        }
        return areaIds;
    }

    /**
     * Returns the first areaId.
     * Throws {@link IllegalStateException} if supported area count not equals to one.
     * */
    public int getFirstAndOnlyAreaId() {
        if (mSupportedAreas.size() != 1) {
            throw new IllegalStateException("Expected one and only area in this property. Prop: 0x"
                    + Integer.toHexString(mPropertyId));
        }
        return mSupportedAreas.keyAt(0);
    }

    public boolean hasArea(int areaId) {
        return mSupportedAreas.indexOfKey(areaId) >= 0;
    }

    @Nullable
    public T getMinValue(int areaId) {
        AreaConfig<T> area = mSupportedAreas.get(areaId);
        return area == null ? null : area.getMinValue();
    }

    @Nullable
    public T getMaxValue(int areaId) {
        AreaConfig<T> area = mSupportedAreas.get(areaId);
        return area == null ? null : area.getMaxValue();
    }

    @Nullable
    public T getMinValue() {
        AreaConfig<T> area = mSupportedAreas.valueAt(0);
        return area == null ? null : area.getMinValue();
    }

    @Nullable
    public T getMaxValue() {
        AreaConfig<T> area = mSupportedAreas.valueAt(0);
        return area == null ? null : area.getMaxValue();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPropertyId);
        dest.writeString(mType.getName());
        dest.writeInt(mAreaType);
        dest.writeInt(mSupportedAreas.size());
        for (int i = 0; i < mSupportedAreas.size(); i++) {
            dest.writeInt(mSupportedAreas.keyAt(i));
            dest.writeParcelable(mSupportedAreas.valueAt(i), flags);
        }
    }

    @SuppressWarnings("unchecked")
    private CarPropertyConfig(Parcel in) {
        mPropertyId = in.readInt();
        String className = in.readString();
        try {
            mType = (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className);
        }
        mAreaType = in.readInt();
        int areaSize = in.readInt();
        mSupportedAreas = new SparseArray<>(areaSize);
        for (int i = 0; i < areaSize; i++) {
            int areaId = in.readInt();
            AreaConfig<T> area = in.readParcelable(getClass().getClassLoader());
            mSupportedAreas.put(areaId, area);
        }
    }

    public static final Creator<CarPropertyConfig> CREATOR = new Creator<CarPropertyConfig>() {
        @Override
        public CarPropertyConfig createFromParcel(Parcel in) {
            return new CarPropertyConfig(in);
        }

        @Override
        public CarPropertyConfig[] newArray(int size) {
            return new CarPropertyConfig[size];
        }
    };

    @Override
    public String toString() {
        return "CarPropertyConfig{" +
                "mPropertyId=" + mPropertyId +
                ", mType=" + mType +
                ", mAreaType=" + mAreaType +
                ", mSupportedAreas=" + mSupportedAreas +
                '}';
    }

    public static class AreaConfig<T> implements Parcelable {
        @Nullable private final T mMinValue;
        @Nullable private final T mMaxValue;

        private AreaConfig(T minValue, T maxValue) {
            mMinValue = minValue;
            mMaxValue = maxValue;
        }

        public static final Parcelable.Creator<AreaConfig<Object>> CREATOR
                = getCreator(Object.class);

        private static <E> Parcelable.Creator<AreaConfig<E>> getCreator(final Class<E> clazz) {
            return new Creator<AreaConfig<E>>() {
                @Override
                public AreaConfig<E> createFromParcel(Parcel source) {
                    return new AreaConfig<>(source);
                }

                @Override @SuppressWarnings("unchecked")
                public AreaConfig<E>[] newArray(int size) {
                    return (AreaConfig<E>[]) Array.newInstance(clazz, size);
                }
            };
        }

        @SuppressWarnings("unchecked")
        private AreaConfig(Parcel in) {
            mMinValue = (T) in.readValue(getClass().getClassLoader());
            mMaxValue = (T) in.readValue(getClass().getClassLoader());
        }

        @Nullable public T getMinValue() { return mMinValue; }
        @Nullable public T getMaxValue() { return mMaxValue; }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeValue(mMinValue);
            dest.writeValue(mMaxValue);
        }

        @Override
        public String toString() {
            return "CarAreaConfig{" +
                    "mMinValue=" + mMinValue +
                    ", mMaxValue=" + mMaxValue +
                    '}';
        }
    }

    public static <T> Builder<T> newBuilder(Class<T> clazz, int propertyId, int areaType,
            int areaCapacity) {
        return new Builder<>(clazz, propertyId, areaType, areaCapacity);
    }


    public static <T> Builder<T> newBuilder(Class<T> clazz, int propertyId, int areaType) {
        return newBuilder(clazz, propertyId, areaType, 0);
    }

    public static class Builder<T> {
        private final Class<T> mType;
        private final int mPropertyId;
        private final int mAreaType;
        private final SparseArray<AreaConfig<T>> mAreas;

        private Builder(Class<T> type, int propertyId, int areaType, int areaCapacity) {
            mType = type;
            mPropertyId = propertyId;
            mAreaType = areaType;
            if (areaCapacity != 0) {
                mAreas = new SparseArray<>(areaCapacity);
            } else {
                mAreas = new SparseArray<>();
            }
        }

        public Builder<T> addAreas(int[] areaIds) {
            for (int id : areaIds) {
                mAreas.put(id, null);
            }
            return this;
        }

        public Builder<T> addArea(int areaId) {
            return addAreaConfig(areaId, null, null);
        }

        public Builder<T> addAreaConfig(int areaId, T min, T max) {
            if (min == null && max == null) {
                mAreas.put(areaId, null);
            } else {
                mAreas.put(areaId, new AreaConfig<>(min, max));
            }
            return this;
        }

        public CarPropertyConfig<T> build() {
            return new CarPropertyConfig<>(mType, mPropertyId, mAreaType, mAreas);
        }
    }
}
