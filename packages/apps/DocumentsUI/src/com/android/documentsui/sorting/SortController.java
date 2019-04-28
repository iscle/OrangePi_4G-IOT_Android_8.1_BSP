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

import android.annotation.Nullable;
import android.app.Activity;
import android.view.View;

import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;

/**
 * A high level controller that manages sort widgets. This is useful when sort widgets can and will
 * appear in different locations in the UI, like the menu, above the file list (pinned) and embedded
 * at the top of file list... and maybe other places too.
 */
public final class SortController {

    private final WidgetController mDropdownController;
    private final @Nullable WidgetController mTableHeaderController;

    public SortController(
            WidgetController dropdownController,
            @Nullable WidgetController tableHeaderController) {

        assert(dropdownController != null);
        mDropdownController = dropdownController;
        mTableHeaderController = tableHeaderController;
    }

    public void onViewModeChanged(@ViewMode int mode) {
        // in phone layouts we only ever have the dropdown sort controller.
        if (mTableHeaderController == null) {
            mDropdownController.setVisibility(View.VISIBLE);
            return;
        }

        // in tablet mode, we have fancy pants tabular header.
        switch (mode) {
            case State.MODE_GRID:
            case State.MODE_UNKNOWN:
                mTableHeaderController.setVisibility(View.GONE);
                mDropdownController.setVisibility(View.VISIBLE);
                break;
            case State.MODE_LIST:
                mTableHeaderController.setVisibility(View.VISIBLE);
                mDropdownController.setVisibility(View.GONE);
                break;
        }
    }

    public void destroy() {
        mDropdownController.destroy();
        if (mTableHeaderController != null) {
            mTableHeaderController.destroy();
        }
    }

    public static SortController create(
            Activity activity,
            @ViewMode int initialMode,
            SortModel sortModel) {

        sortModel.setMetricRecorder((SortDimension dimension) -> {
            switch (dimension.getId()) {
                case SortModel.SORT_DIMENSION_ID_TITLE:
                    Metrics.logUserAction(activity, Metrics.USER_ACTION_SORT_NAME);
                    break;
                case SortModel.SORT_DIMENSION_ID_SIZE:
                    Metrics.logUserAction(activity, Metrics.USER_ACTION_SORT_SIZE);
                    break;
                case SortModel.SORT_DIMENSION_ID_DATE:
                    Metrics.logUserAction(activity, Metrics.USER_ACTION_SORT_DATE);
                    break;
            }
        });

        SortController controller = new SortController(
                new DropdownSortWidgetController(
                        sortModel,
                        activity.findViewById(R.id.dropdown_sort_widget)),
                TableHeaderController.create(
                        sortModel,
                        activity.findViewById(R.id.table_header)));

        controller.onViewModeChanged(initialMode);
        return controller;
    }

    public interface WidgetController {
        void setVisibility(int visibility);
        void destroy();
    }
}
