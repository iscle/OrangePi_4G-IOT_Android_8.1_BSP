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

package com.android.tv.ui.sidepanel;

import android.content.Context;

import com.android.tv.R;
import com.android.tv.data.DisplayMode;
import com.android.tv.ui.TvViewUiManager;

import java.util.ArrayList;
import java.util.List;

public class DisplayModeFragment extends SideFragment {
    private static final String TRACKER_LABEL = "display mode";
    private TvViewUiManager mTvViewUiManager;

    @Override
    protected String getTitle() {
        return getString(R.string.side_panel_title_display_mode);
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    protected List<Item> getItemList() {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < DisplayMode.SIZE_OF_RATIO_TYPES; ++i) {
            items.add(new DisplayModeRadioItem(i));
        }
        return items;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mTvViewUiManager = getMainActivity().getTvViewUiManager();
    }

    @Override
    public void onResume() {
        super.onResume();
        setSelectedPosition(mTvViewUiManager.getDisplayMode());
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mTvViewUiManager.restoreDisplayMode(true);
    }

    private class DisplayModeRadioItem extends RadioButtonItem {
        private final int mDisplayMode;

        private DisplayModeRadioItem(int displayMode) {
            super(DisplayMode.getLabel(displayMode, getActivity()));
            mDisplayMode = displayMode;
        }

        @Override
        protected void onUpdate() {
            super.onUpdate();
            setEnabled(mTvViewUiManager.isDisplayModeAvailable(mDisplayMode));
            setChecked(mDisplayMode == mTvViewUiManager.getDisplayMode());
        }

        @Override
        protected void onSelected() {
            super.onSelected();
            mTvViewUiManager.setDisplayMode(mDisplayMode, true, true);
            closeFragment();
        }

        @Override
        protected void onFocused() {
            super.onFocused();
            mTvViewUiManager.setDisplayMode(mDisplayMode, false, true);
        }
    }
}
