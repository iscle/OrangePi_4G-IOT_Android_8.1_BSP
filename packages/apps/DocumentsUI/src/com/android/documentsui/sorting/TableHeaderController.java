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

import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortModel.SortDimensionId;

import javax.annotation.Nullable;

/**
 * View controller for table header that associates header cells in table header and columns.
 */
public final class TableHeaderController implements SortController.WidgetController {
    private View mTableHeader;

    private final HeaderCell mTitleCell;
    private final HeaderCell mSummaryCell;
    private final HeaderCell mSizeCell;
    private final HeaderCell mFileTypeCell;
    private final HeaderCell mDateCell;

    // We assign this here porque each method reference creates a new object
    // instance (which is wasteful).
    private final View.OnClickListener mOnCellClickListener = this::onCellClicked;
    private final SortModel.UpdateListener mModelListener = this::onModelUpdate;

    private final SortModel mModel;

    private TableHeaderController(SortModel sortModel, View tableHeader) {
        assert(sortModel != null);
        assert(tableHeader != null);

        mModel = sortModel;
        mTableHeader = tableHeader;

        mTitleCell = (HeaderCell) tableHeader.findViewById(android.R.id.title);
        mSummaryCell = (HeaderCell) tableHeader.findViewById(android.R.id.summary);
        mSizeCell = (HeaderCell) tableHeader.findViewById(R.id.size);
        mFileTypeCell = (HeaderCell) tableHeader.findViewById(R.id.file_type);
        mDateCell = (HeaderCell) tableHeader.findViewById(R.id.date);

        onModelUpdate(mModel, SortModel.UPDATE_TYPE_UNSPECIFIED);

        mModel.addListener(mModelListener);
    }

    private void onModelUpdate(SortModel model, int updateTypeUnspecified) {
        bindCell(mTitleCell, SortModel.SORT_DIMENSION_ID_TITLE);
        bindCell(mSummaryCell, SortModel.SORT_DIMENSION_ID_SUMMARY);
        bindCell(mSizeCell, SortModel.SORT_DIMENSION_ID_SIZE);
        bindCell(mFileTypeCell, SortModel.SORT_DIMENSION_ID_FILE_TYPE);
        bindCell(mDateCell, SortModel.SORT_DIMENSION_ID_DATE);
    }

    @Override
    public void setVisibility(int visibility) {
        mTableHeader.setVisibility(visibility);
    }

    @Override
    public void destroy() {
        mModel.removeListener(mModelListener);
    }

    private void bindCell(HeaderCell cell, @SortDimensionId int id) {
        assert(cell != null);
        SortDimension dimension = mModel.getDimensionById(id);

        cell.setTag(dimension);

        cell.onBind(dimension);
        if (dimension.getVisibility() == View.VISIBLE
                && dimension.getSortCapability() != SortDimension.SORT_CAPABILITY_NONE) {
            cell.setOnClickListener(mOnCellClickListener);
        } else {
            cell.setOnClickListener(null);
        }
    }

    private void onCellClicked(View v) {
        SortDimension dimension = (SortDimension) v.getTag();

        mModel.sortByUser(dimension.getId(), dimension.getNextDirection());
    }

    public static @Nullable TableHeaderController create(
            SortModel sortModel, @Nullable View tableHeader) {
        return (tableHeader == null) ? null : new TableHeaderController(sortModel, tableHeader);
    }
}
