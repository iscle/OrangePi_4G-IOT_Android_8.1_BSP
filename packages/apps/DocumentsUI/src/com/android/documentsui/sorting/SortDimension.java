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

package com.android.documentsui.sorting;

import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.StringRes;
import android.view.View;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A model class that describes a sort dimension and its sort state.
 */
public class SortDimension implements Parcelable {

    /**
     * This enum is defined as flag because it's also used to denote whether a column can be sorted
     * in a certain direction.
     */
    @IntDef(flag = true, value = {
            SORT_DIRECTION_NONE,
            SORT_DIRECTION_ASCENDING,
            SORT_DIRECTION_DESCENDING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortDirection {}
    public static final int SORT_DIRECTION_NONE = 0;
    public static final int SORT_DIRECTION_ASCENDING = 1;
    public static final int SORT_DIRECTION_DESCENDING = 2;

    @IntDef({
            SORT_CAPABILITY_NONE,
            SORT_CAPABILITY_BOTH_DIRECTION
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface SortCapability {}
    public static final int SORT_CAPABILITY_NONE = 0;
    public static final int SORT_CAPABILITY_BOTH_DIRECTION =
            SORT_DIRECTION_ASCENDING | SORT_DIRECTION_DESCENDING;

    @IntDef({
            DATA_TYPE_STRING,
            DATA_TYPE_NUMBER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DataType {}
    public static final int DATA_TYPE_STRING = 0;
    public static final int DATA_TYPE_NUMBER = 1;

    private final int mId;
    private final @StringRes int mLabelId;
    private final @DataType int mDataType;
    private final @SortCapability int mSortCapability;
    private final @SortDirection int mDefaultSortDirection;

    @SortDirection int mSortDirection = SORT_DIRECTION_NONE;
    int mVisibility;

    private SortDimension(int id, @StringRes int labelId, @DataType int dataType,
            @SortCapability int sortCapability, @SortDirection int defaultSortDirection) {
        mId = id;
        mLabelId = labelId;
        mDataType = dataType;
        mSortCapability = sortCapability;
        mDefaultSortDirection = defaultSortDirection;
    }

    public int getId() {
        return mId;
    }

    public @StringRes int getLabelId() {
        return mLabelId;
    }

    public @DataType int getDataType() {
        return mDataType;
    }

    public @SortCapability int getSortCapability() {
        return mSortCapability;
    }

    public @SortDirection int getDefaultSortDirection() {
        return mDefaultSortDirection;
    }

    public @SortDirection int getNextDirection() {
        @SortDimension.SortDirection int alternativeDirection =
                (mDefaultSortDirection == SortDimension.SORT_DIRECTION_ASCENDING)
                        ? SortDimension.SORT_DIRECTION_DESCENDING
                        : SortDimension.SORT_DIRECTION_ASCENDING;
        @SortDimension.SortDirection int direction =
                (mSortDirection == mDefaultSortDirection)
                        ? alternativeDirection
                        : mDefaultSortDirection;

        return direction;
    }

    public @SortDirection int getSortDirection() {
        return mSortDirection;
    }

    public int getVisibility() {
        return mVisibility;
    }

    @Override
    public int hashCode() {
        return mId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof SortDimension)) {
            return false;
        }

        if (this == o) {
            return true;
        }

        SortDimension other = (SortDimension) o;

        return mId == other.mId
                && mLabelId == other.mLabelId
                && mDataType == other.mDataType
                && mSortCapability == other.mSortCapability
                && mDefaultSortDirection == other.mDefaultSortDirection
                && mSortDirection == other.mSortDirection
                && mVisibility == other.mVisibility;
    }

    @Override
    public String toString() {
        return new StringBuilder().append("SortDimension{")
                .append("id=").append(mId)
                .append(", labelId=").append(mLabelId)
                .append(", dataType=").append(mDataType)
                .append(", sortCapability=").append(mSortCapability)
                .append(", defaultSortDirection=").append(mDefaultSortDirection)
                .append(", sortDirection=").append(mSortDirection)
                .append(", visibility=").append(mVisibility)
                .append("}")
                .toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flag) {
        out.writeInt(mId);
        out.writeInt(mLabelId);
        out.writeInt(mDataType);
        out.writeInt(mSortCapability);
        out.writeInt(mDefaultSortDirection);
        out.writeInt(mSortDirection);
        out.writeInt(mVisibility);
    }

    public static Parcelable.Creator<SortDimension> CREATOR =
            new Parcelable.Creator<SortDimension>() {

        @Override
        public SortDimension createFromParcel(Parcel in) {
            int id = in.readInt();
            @StringRes int labelId = in.readInt();
            @DataType  int dataType = in.readInt();
            int sortCapability = in.readInt();
            int defaultSortDirection = in.readInt();

            SortDimension column =
                    new SortDimension(id, labelId, dataType, sortCapability, defaultSortDirection);

            column.mSortDirection = in.readInt();
            column.mVisibility = in.readInt();

            return column;
        }

        @Override
        public SortDimension[] newArray(int size) {
            return new SortDimension[size];
        }
    };

    static class Builder {
        private int mId;
        private @StringRes int mLabelId;
        private @DataType int mDataType = DATA_TYPE_STRING;
        private @SortCapability int mSortCapability = SORT_CAPABILITY_BOTH_DIRECTION;
        private @SortDirection int mDefaultSortDirection = SORT_DIRECTION_ASCENDING;
        private int mVisibility = View.VISIBLE;

        Builder withId(int id) {
            mId = id;
            return this;
        }

        Builder withLabelId(@StringRes int labelId) {
            mLabelId = labelId;
            return this;
        }

        Builder withDataType(@DataType int dataType) {
            mDataType = dataType;
            return this;
        }

        Builder withSortCapability(@SortCapability int sortCapability) {
            mSortCapability = sortCapability;
            return this;
        }

        Builder withVisibility(int visibility) {
            mVisibility = visibility;
            return this;
        }

        Builder withDefaultSortDirection(@SortDirection int defaultSortDirection) {
            mDefaultSortDirection = defaultSortDirection;
            return this;
        }

        SortDimension build() {
            if (mLabelId == 0) {
                throw new IllegalStateException("Must set labelId.");
            }

            SortDimension dimension = new SortDimension(
                    mId, mLabelId, mDataType, mSortCapability, mDefaultSortDirection);
            dimension.mVisibility = mVisibility;
            return dimension;
        }
    }
}
