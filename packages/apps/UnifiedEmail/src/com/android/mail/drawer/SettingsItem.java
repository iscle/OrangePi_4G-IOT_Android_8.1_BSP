/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.mail.drawer;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.providers.Account;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.FolderListFragment;
import com.android.mail.utils.FolderUri;
import com.android.mail.utils.Utils;

class SettingsItem extends FooterItem {
    SettingsItem(ControllableActivity activity, Account account,
            FolderListFragment.DrawerStateListener drawerListener) {
        super(activity, account, drawerListener,
                R.drawable.ic_drawer_settings_24dp, R.string.menu_settings);
    }

    @Override
    public void onFooterClicked() {
        Analytics.getInstance().sendMenuItemEvent(Analytics.EVENT_CATEGORY_MENU_ITEM,
                R.id.settings, getEventLabel(), 0);
        Utils.showSettings(mActivity.getActivityContext(), mAccount);
    }

    @Override
    public int getType() {
        return VIEW_FOOTER_SETTINGS;
    }

    @Override
    public boolean isHighlighted(FolderUri currentFolder, int currentType) {
        return false;
    }

    @Override
    public boolean isItemEnabled() {
        return false;
    }

    @Override
    public String toString() {
        return "[FooterItem VIEW_SETTINGS_ITEM]";
    }
}
