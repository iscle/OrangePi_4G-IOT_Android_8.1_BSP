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

package com.android.tv.menu;

import android.content.Context;

import com.android.tv.R;
import com.android.tv.TimeShiftManager;
import com.android.tv.ui.TunableTvView;

public class PlayControlsRow extends MenuRow {
    public static final String ID = PlayControlsRow.class.getName();

    private final TunableTvView mTvView;
    private final TimeShiftManager mTimeShiftManager;

    public PlayControlsRow(Context context, TunableTvView tvView, Menu menu,
            TimeShiftManager timeShiftManager) {
        super(context, menu, R.string.menu_title_play_controls, R.dimen.play_controls_height);
        mTvView = tvView;
        mTimeShiftManager = timeShiftManager;
    }

    @Override
    public void update() {
        ((PlayControlsRowView) getMenuRowView()).update();
    }

    @Override
    public int getLayoutResId() {
        return R.layout.play_controls;
    }

    /**
     * Returns TV view.
     */
    public TunableTvView getTvView() {
        return mTvView;
    }

    /**
     * Returns an instance of {@link TimeShiftManager}.
     */
    public TimeShiftManager getTimeShiftManager() {
        return mTimeShiftManager;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public boolean isVisible() {
        return mTimeShiftManager.isAvailable();
    }

    @Override
    public boolean hideTitleWhenSelected() {
        return true;
    }
}
