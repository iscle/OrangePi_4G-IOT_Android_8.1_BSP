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

import android.view.View;
import android.view.ViewGroup;

import com.android.mail.R;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.utils.FolderUri;

class BottomSpaceDrawerItem extends DrawerItem {
    BottomSpaceDrawerItem(ControllableActivity activity) {
        super(activity, null, NONFOLDER_ITEM, null);
    }

    @Override
    public String toString() {
        return "[DrawerItem VIEW_BOTTOM_SPACE]";
    }

    /**
     * Returns a blank spacer
     *
     * @param convertView A previous view, perhaps null
     * @param parent the parent of this view
     * @return a blank spacer
     */
    @Override
    public View getView(View convertView, ViewGroup parent) {
        final View blankHeaderView;
        if (convertView != null) {
            blankHeaderView = convertView;
        } else {
            blankHeaderView = mInflater.inflate(R.layout.folder_list_bottom_space, parent,
                    false);
        }
        return blankHeaderView;
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
    public @DrawerItemType int getType() {
        return VIEW_BOTTOM_SPACE;
    }
}
