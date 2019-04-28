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

import static com.android.documentsui.base.Shared.DEBUG;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.DocumentsContract.Document;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.sorting.SortDimension.SortDirection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sort model that contains all columns and their sorting state.
 */
public class SortModel implements Parcelable {
    @IntDef({
            SORT_DIMENSION_ID_UNKNOWN,
            SORT_DIMENSION_ID_TITLE,
            SORT_DIMENSION_ID_SUMMARY,
            SORT_DIMENSION_ID_SIZE,
            SORT_DIMENSION_ID_FILE_TYPE,
            SORT_DIMENSION_ID_DATE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortDimensionId {}
    public static final int SORT_DIMENSION_ID_UNKNOWN = 0;
    public static final int SORT_DIMENSION_ID_TITLE = android.R.id.title;
    public static final int SORT_DIMENSION_ID_SUMMARY = android.R.id.summary;
    public static final int SORT_DIMENSION_ID_SIZE = R.id.size;
    public static final int SORT_DIMENSION_ID_FILE_TYPE = R.id.file_type;
    public static final int SORT_DIMENSION_ID_DATE = R.id.date;

    @IntDef(flag = true, value = {
            UPDATE_TYPE_NONE,
            UPDATE_TYPE_UNSPECIFIED,
            UPDATE_TYPE_VISIBILITY,
            UPDATE_TYPE_SORTING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UpdateType {}
    /**
     * Default value for update type. Nothing is updated.
     */
    public static final int UPDATE_TYPE_NONE = 0;
    /**
     * Indicates the visibility of at least one dimension has changed.
     */
    public static final int UPDATE_TYPE_VISIBILITY = 1;
    /**
     * Indicates the sorting order has changed, either because the sorted dimension has changed or
     * the sort direction has changed.
     */
    public static final int UPDATE_TYPE_SORTING = 1 << 1;
    /**
     * Anything can be changed if the type is unspecified.
     */
    public static final int UPDATE_TYPE_UNSPECIFIED = -1;

    private static final String TAG = "SortModel";

    private final SparseArray<SortDimension> mDimensions;

    private transient final List<UpdateListener> mListeners;
    private transient Consumer<SortDimension> mMetricRecorder;

    private int mDefaultDimensionId = SORT_DIMENSION_ID_UNKNOWN;
    private boolean mIsUserSpecified = false;
    private @Nullable SortDimension mSortedDimension;

    @VisibleForTesting
    SortModel(Collection<SortDimension> columns) {
        mDimensions = new SparseArray<>(columns.size());

        for (SortDimension column : columns) {
            if (column.getId() == SORT_DIMENSION_ID_UNKNOWN) {
                throw new IllegalArgumentException(
                        "SortDimension id can't be " + SORT_DIMENSION_ID_UNKNOWN + ".");
            }
            if (mDimensions.get(column.getId()) != null) {
                throw new IllegalStateException(
                        "SortDimension id must be unique. Duplicate id: " + column.getId());
            }
            mDimensions.put(column.getId(), column);
        }

        mListeners = new ArrayList<>();
    }

    public int getSize() {
        return mDimensions.size();
    }

    public SortDimension getDimensionAt(int index) {
        return mDimensions.valueAt(index);
    }

    public @Nullable SortDimension getDimensionById(int id) {
        return mDimensions.get(id);
    }

    /**
     * Gets the sorted dimension id.
     * @return the sorted dimension id or {@link #SORT_DIMENSION_ID_UNKNOWN} if there is no sorted
     * dimension.
     */
    public int getSortedDimensionId() {
        return mSortedDimension != null ? mSortedDimension.getId() : SORT_DIMENSION_ID_UNKNOWN;
    }

    public @SortDirection int getCurrentSortDirection() {
        return mSortedDimension != null
                ? mSortedDimension.getSortDirection()
                : SortDimension.SORT_DIRECTION_NONE;
    }

    /**
     * Sort by the default direction of the given dimension if user has never specified any sort
     * direction before.
     * @param dimensionId the id of the dimension
     */
    public void setDefaultDimension(int dimensionId) {
        final boolean mayNeedSorting = (mDefaultDimensionId != dimensionId);

        mDefaultDimensionId = dimensionId;

        if (mayNeedSorting) {
            sortOnDefault();
        }
    }

    void setMetricRecorder(Consumer<SortDimension> metricRecorder) {
        mMetricRecorder = metricRecorder;
    }

    /**
     * Sort by given dimension and direction. Should only be used when user explicitly asks to sort
     * docs.
     * @param dimensionId the id of the dimension
     * @param direction the direction to sort docs in
     */
    public void sortByUser(int dimensionId, @SortDirection int direction) {
        SortDimension dimension = mDimensions.get(dimensionId);
        if (dimension == null) {
            throw new IllegalArgumentException("Unknown column id: " + dimensionId);
        }

        sortByDimension(dimension, direction);

        if (mMetricRecorder != null) {
            mMetricRecorder.accept(dimension);
        }

        mIsUserSpecified = true;
    }

    private void sortByDimension(
            SortDimension newSortedDimension, @SortDirection int direction) {
        if (newSortedDimension == mSortedDimension
                && mSortedDimension.mSortDirection == direction) {
            // Sort direction not changed, no need to proceed.
            return;
        }

        if ((newSortedDimension.getSortCapability() & direction) == 0) {
            throw new IllegalStateException(
                    "Dimension with id: " + newSortedDimension.getId()
                    + " can't be sorted in direction:" + direction);
        }

        switch (direction) {
            case SortDimension.SORT_DIRECTION_ASCENDING:
            case SortDimension.SORT_DIRECTION_DESCENDING:
                newSortedDimension.mSortDirection = direction;
                break;
            default:
                throw new IllegalArgumentException("Unknown sort direction: " + direction);
        }

        if (mSortedDimension != null && mSortedDimension != newSortedDimension) {
            mSortedDimension.mSortDirection = SortDimension.SORT_DIRECTION_NONE;
        }

        mSortedDimension = newSortedDimension;

        notifyListeners(UPDATE_TYPE_SORTING);
    }

    public void setDimensionVisibility(int columnId, int visibility) {
        assert(mDimensions.get(columnId) != null);

        mDimensions.get(columnId).mVisibility = visibility;

        notifyListeners(UPDATE_TYPE_VISIBILITY);
    }

    public Cursor sortCursor(Cursor cursor, Lookup<String, String> fileTypesMap) {
        if (mSortedDimension != null) {
            return new SortingCursorWrapper(cursor, mSortedDimension, fileTypesMap);
        } else {
            return cursor;
        }
    }

    public void addQuerySortArgs(Bundle queryArgs) {
        // should only be called when R.bool.feature_content_paging is true

        final int id = getSortedDimensionId();
        switch (id) {
            case SORT_DIMENSION_ID_UNKNOWN:
                return;
            case SortModel.SORT_DIMENSION_ID_TITLE:
                queryArgs.putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        new String[]{ Document.COLUMN_DISPLAY_NAME });
                break;
            case SortModel.SORT_DIMENSION_ID_DATE:
                queryArgs.putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        new String[]{ Document.COLUMN_LAST_MODIFIED });
                break;
            case SortModel.SORT_DIMENSION_ID_SIZE:
                queryArgs.putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        new String[]{ Document.COLUMN_SIZE });
                break;
            case SortModel.SORT_DIMENSION_ID_FILE_TYPE:
                // Unfortunately sorting by mime type is pretty much guaranteed different from
                // sorting by user-friendly type, so there is no point to guide the provider to sort
                // in a particular order.
                return;
            default:
                throw new IllegalStateException(
                        "Unexpected sort dimension id: " + id);
        }

        final SortDimension dimension = getDimensionById(id);
        switch (dimension.getSortDirection()) {
            case SortDimension.SORT_DIRECTION_ASCENDING:
                queryArgs.putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_ASCENDING);
                break;
            case SortDimension.SORT_DIRECTION_DESCENDING:
                queryArgs.putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING);
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected sort direction: " + dimension.getSortDirection());
        }
    }

    public @Nullable String getDocumentSortQuery() {
        // This method should only be called when R.bool.feature_content_paging exists.
        // Once that feature is enabled by default (and reference removed), this method
        // should also be removed.
        // The following log message exists simply to make reference to
        // R.bool.feature_content_paging so that compiler will fail when value
        // is remove from config.xml.
        int readTheCommentAbove = R.bool.feature_content_paging;

        final int id = getSortedDimensionId();
        final String columnName;
        switch (id) {
            case SORT_DIMENSION_ID_UNKNOWN:
                return null;
            case SortModel.SORT_DIMENSION_ID_TITLE:
                columnName = Document.COLUMN_DISPLAY_NAME;
                break;
            case SortModel.SORT_DIMENSION_ID_DATE:
                columnName = Document.COLUMN_LAST_MODIFIED;
                break;
            case SortModel.SORT_DIMENSION_ID_SIZE:
                columnName = Document.COLUMN_SIZE;
                break;
            case SortModel.SORT_DIMENSION_ID_FILE_TYPE:
                // Unfortunately sorting by mime type is pretty much guaranteed different from
                // sorting by user-friendly type, so there is no point to guide the provider to sort
                // in a particular order.
                return null;
            default:
                throw new IllegalStateException(
                        "Unexpected sort dimension id: " + id);
        }

        final SortDimension dimension = getDimensionById(id);
        final String direction;
        switch (dimension.getSortDirection()) {
            case SortDimension.SORT_DIRECTION_ASCENDING:
                direction = " ASC";
                break;
            case SortDimension.SORT_DIRECTION_DESCENDING:
                direction = " DESC";
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected sort direction: " + dimension.getSortDirection());
        }

        return columnName + direction;
    }

    private void notifyListeners(@UpdateType int updateType) {
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            mListeners.get(i).onModelUpdate(this, updateType);
        }
    }

    public void addListener(UpdateListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(UpdateListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Sort by default dimension and direction if there is no history of user specifying a sort
     * order.
     */
    private void sortOnDefault() {
        if (!mIsUserSpecified) {
            SortDimension dimension = mDimensions.get(mDefaultDimensionId);
            if (dimension == null) {
                if (DEBUG) Log.d(TAG, "No default sort dimension.");
                return;
            }

            sortByDimension(dimension, dimension.getDefaultSortDirection());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof SortModel)) {
            return false;
        }

        if (this == o) {
            return true;
        }

        SortModel other = (SortModel) o;
        if (mDimensions.size() != other.mDimensions.size()) {
            return false;
        }
        for (int i = 0; i < mDimensions.size(); ++i) {
            final SortDimension dimension = mDimensions.valueAt(i);
            final int id = dimension.getId();
            if (!dimension.equals(other.getDimensionById(id))) {
                return false;
            }
        }

        return mDefaultDimensionId == other.mDefaultDimensionId
                && (mSortedDimension == other.mSortedDimension
                    || mSortedDimension.equals(other.mSortedDimension));
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("SortModel{")
                .append("dimensions=").append(mDimensions)
                .append(", defaultDimensionId=").append(mDefaultDimensionId)
                .append(", sortedDimension=").append(mSortedDimension)
                .append("}")
                .toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flag) {
        out.writeInt(mDimensions.size());
        for (int i = 0; i < mDimensions.size(); ++i) {
            out.writeParcelable(mDimensions.valueAt(i), flag);
        }

        out.writeInt(mDefaultDimensionId);
        out.writeInt(getSortedDimensionId());
    }

    public static Parcelable.Creator<SortModel> CREATOR = new Parcelable.Creator<SortModel>() {

        @Override
        public SortModel createFromParcel(Parcel in) {
            final int size = in.readInt();
            Collection<SortDimension> columns = new ArrayList<>(size);
            for (int i = 0; i < size; ++i) {
                columns.add(in.readParcelable(getClass().getClassLoader()));
            }
            SortModel model = new SortModel(columns);

            model.mDefaultDimensionId = in.readInt();
            model.mSortedDimension = model.getDimensionById(in.readInt());

            return model;
        }

        @Override
        public SortModel[] newArray(int size) {
            return new SortModel[size];
        }
    };

    /**
     * Creates a model for all other roots.
     *
     * TODO: move definition of columns into xml, and inflate model from it.
     */
    public static SortModel createModel() {
        List<SortDimension> dimensions = new ArrayList<>(4);
        SortDimension.Builder builder = new SortDimension.Builder();

        // Name column
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_TITLE)
                .withLabelId(R.string.sort_dimension_name)
                .withDataType(SortDimension.DATA_TYPE_STRING)
                .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
                .withDefaultSortDirection(SortDimension.SORT_DIRECTION_ASCENDING)
                .withVisibility(View.VISIBLE)
                .build()
        );

        // Summary column
        // Summary is only visible in Downloads and Recents root.
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_SUMMARY)
                .withLabelId(R.string.sort_dimension_summary)
                .withDataType(SortDimension.DATA_TYPE_STRING)
                .withSortCapability(SortDimension.SORT_CAPABILITY_NONE)
                .withVisibility(View.INVISIBLE)
                .build()
        );

        // Size column
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_SIZE)
                .withLabelId(R.string.sort_dimension_size)
                .withDataType(SortDimension.DATA_TYPE_NUMBER)
                .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
                .withDefaultSortDirection(SortDimension.SORT_DIRECTION_ASCENDING)
                .withVisibility(View.VISIBLE)
                .build()
        );

        // Type column
        dimensions.add(builder
            .withId(SORT_DIMENSION_ID_FILE_TYPE)
            .withLabelId(R.string.sort_dimension_file_type)
            .withDataType(SortDimension.DATA_TYPE_STRING)
            .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
            .withDefaultSortDirection(SortDimension.SORT_DIRECTION_ASCENDING)
            .withVisibility(View.VISIBLE)
            .build());

        // Date column
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_DATE)
                .withLabelId(R.string.sort_dimension_date)
                .withDataType(SortDimension.DATA_TYPE_NUMBER)
                .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
                .withDefaultSortDirection(SortDimension.SORT_DIRECTION_DESCENDING)
                .withVisibility(View.VISIBLE)
                .build()
        );

        return new SortModel(dimensions);
    }

    public interface UpdateListener {
        void onModelUpdate(SortModel newModel, @UpdateType int updateType);
    }
}
