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
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.customization.CustomAction;
import com.android.tv.customization.TvCustomizationManager;
import com.android.tv.ui.TunableTvView;

import java.util.List;

/**
 * A factory class to create menu rows.
 */
public class MenuRowFactory {
    private final MainActivity mMainActivity;
    private final TunableTvView mTvView;
    private final TvCustomizationManager mTvCustomizationManager;

    /**
     * A constructor.
     */
    public MenuRowFactory(MainActivity mainActivity, TunableTvView tvView) {
        mMainActivity = mainActivity;
        mTvView = tvView;
        mTvCustomizationManager = new TvCustomizationManager(mainActivity);
        mTvCustomizationManager.initialize();
    }

    /**
     * Creates an object corresponding to the given {@code key}.
     */
    @Nullable
    public MenuRow createMenuRow(Menu menu, Class<?> key) {
        if (PlayControlsRow.class.equals(key)) {
            return new PlayControlsRow(mMainActivity, mTvView, menu,
                    mMainActivity.getTimeShiftManager());
        } else if (ChannelsRow.class.equals(key)) {
            return new ChannelsRow(mMainActivity, menu, mMainActivity.getProgramDataManager());
        } else if (PartnerRow.class.equals(key)) {
            List<CustomAction> customActions = mTvCustomizationManager.getCustomActions(
                    TvCustomizationManager.ID_PARTNER_ROW);
            String title = mTvCustomizationManager.getPartnerRowTitle();
            if (customActions != null && !TextUtils.isEmpty(title)) {
                return new PartnerRow(mMainActivity, menu, title, customActions);
            }
            return null;
        } else if (TvOptionsRow.class.equals(key)) {
            return new TvOptionsRow(mMainActivity, menu, mTvCustomizationManager
                    .getCustomActions(TvCustomizationManager.ID_OPTIONS_ROW));
        }
        return null;
    }

    /**
     * A menu row which represents the TV options row.
     */
    public static class TvOptionsRow extends ItemListRow {
        /**
         * The ID of the row.
         */
        public static final String ID = TvOptionsRow.class.getName();

        private TvOptionsRow(Context context, Menu menu, List<CustomAction> customActions) {
            super(context, menu, R.string.menu_title_options, R.dimen.action_card_height,
                    new TvOptionsRowAdapter(context, customActions));
        }
    }

    /**
     * A menu row which represents the partner row.
     */
    public static class PartnerRow extends ItemListRow {
        private PartnerRow(Context context, Menu menu, String title,
                           List<CustomAction> customActions) {
            super(context, menu, title, R.dimen.action_card_height,
                    new PartnerOptionsRowAdapter(context, customActions));
        }
    }
}
