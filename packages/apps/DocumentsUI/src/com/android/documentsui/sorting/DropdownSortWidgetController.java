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

import android.annotation.StringRes;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortController.WidgetController;
import com.android.documentsui.sorting.SortDimension.SortDirection;
import com.android.documentsui.sorting.SortModel.SortDimensionId;
import com.android.documentsui.sorting.SortModel.UpdateType;

/**
 * View controller for the sort widget in grid mode and in small screens.
 */
public final class DropdownSortWidgetController implements WidgetController {

    private static final int LEVEL_UPWARD = 0;
    private static final int LEVEL_DOWNWARD = 10000;

    private final SortModel mModel;
    private final View mWidget;
    private final TextView mDimensionButton;
    private final PopupMenu mMenu;
    private final ImageView mArrow;
    private final SortModel.UpdateListener mListener;

    public DropdownSortWidgetController(SortModel model, View widget) {
        mModel = model;
        mWidget = widget;

        mDimensionButton = (TextView) mWidget.findViewById(R.id.sort_dimen_dropdown);
        mDimensionButton.setOnClickListener(this::showMenu);

        mMenu = new PopupMenu(widget.getContext(), mDimensionButton, Gravity.END | Gravity.TOP);
        mMenu.setOnMenuItemClickListener(this::onSelectDimension);

        mArrow = (ImageView) mWidget.findViewById(R.id.sort_arrow);
        mArrow.setOnClickListener(this::onChangeDirection);

        populateMenuItems();
        onModelUpdate(mModel, SortModel.UPDATE_TYPE_UNSPECIFIED);

        mListener = this::onModelUpdate;
        mModel.addListener(mListener);
    }

    @Override
    public void setVisibility(int visibility) {
        mWidget.setVisibility(visibility);
    }

    @Override
    public void destroy() {
        mModel.removeListener(mListener);
    }

    private void populateMenuItems() {
        Menu menu = mMenu.getMenu();
        menu.clear();
        for (int i = 0; i < mModel.getSize(); ++i) {
            SortDimension dimension = mModel.getDimensionAt(i);
            if (dimension.getSortCapability() != SortDimension.SORT_CAPABILITY_NONE) {
                menu.add(0, dimension.getId(), Menu.NONE, dimension.getLabelId());
            }
        }
    }

    private void showMenu(View v) {
        mMenu.show();
    }

    private void onModelUpdate(SortModel model, @UpdateType int updateType) {
        final @SortDimensionId int sortedId = model.getSortedDimensionId();

        if ((updateType & SortModel.UPDATE_TYPE_VISIBILITY) != 0) {
            updateVisibility();
        }

        if ((updateType & SortModel.UPDATE_TYPE_SORTING) != 0) {
            bindSortedDimension(sortedId);
            bindSortDirection(sortedId);
        }
    }

    private void updateVisibility() {
        Menu menu = mMenu.getMenu();

        for (int i = 0; i < menu.size(); ++i) {
            MenuItem item = menu.getItem(i);
            SortDimension dimension = mModel.getDimensionById(item.getItemId());
            item.setVisible(dimension.getVisibility() == View.VISIBLE);
        }
    }

    private void bindSortedDimension(@SortDimensionId int sortedId) {
        if (sortedId == SortModel.SORT_DIMENSION_ID_UNKNOWN) {
            mDimensionButton.setText(R.string.not_sorted);
        } else {
            SortDimension dimension = mModel.getDimensionById(sortedId);
            mDimensionButton.setText(dimension.getLabelId());
        }
    }

    private void bindSortDirection(@SortDimensionId int sortedId) {
        if (sortedId == SortModel.SORT_DIMENSION_ID_UNKNOWN) {
            mArrow.setVisibility(View.INVISIBLE);
        } else {
            final SortDimension dimension = mModel.getDimensionById(sortedId);
            switch (dimension.getSortDirection()) {
                case SortDimension.SORT_DIRECTION_NONE:
                    mArrow.setVisibility(View.INVISIBLE);
                    break;
                case SortDimension.SORT_DIRECTION_ASCENDING:
                    showArrow(LEVEL_UPWARD, R.string.sort_direction_ascending);
                    break;
                case SortDimension.SORT_DIRECTION_DESCENDING:
                    showArrow(LEVEL_DOWNWARD, R.string.sort_direction_descending);
                    break;
                default:
                    throw new IllegalStateException(
                            "Unknown sort direction: " + dimension.getSortDirection() + ".");
            }
        }
    }

    private void showArrow(int level, @StringRes int descriptionId) {
        mArrow.setVisibility(View.VISIBLE);

        mArrow.getDrawable().mutate();
        mArrow.setImageLevel(level);
        mArrow.setContentDescription(mArrow.getContext().getString(descriptionId));
    }

    private boolean onSelectDimension(MenuItem item) {
        final @SortDirection int preferredDirection = mModel.getCurrentSortDirection();

        final SortDimension dimension = mModel.getDimensionById(item.getItemId());
        final @SortDirection int direction;
        if ((dimension.getSortCapability() & preferredDirection) > 0) {
            direction = preferredDirection;
        } else {
            direction = dimension.getDefaultSortDirection();
        }

        mModel.sortByUser(dimension.getId(), direction);

        return true;
    }

    private void onChangeDirection(View v) {
        final @SortDimensionId int id = mModel.getSortedDimensionId();
        assert(id != SortModel.SORT_DIMENSION_ID_UNKNOWN);

        final SortDimension dimension = mModel.getDimensionById(id);
        mModel.sortByUser(dimension.getId(), dimension.getNextDirection());
    }
}
