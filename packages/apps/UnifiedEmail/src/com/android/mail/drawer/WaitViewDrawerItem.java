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

class WaitViewDrawerItem extends DrawerItem {
    WaitViewDrawerItem(ControllableActivity activity) {
        super(activity, null, NONFOLDER_ITEM, null);
    }

    @Override
    public String toString() {
        return "[DrawerItem VIEW_WAITING_FOR_SYNC]";
    }

    /**
     * Return a view for the 'Waiting for sync' item with the indeterminate progress indicator.
     *
     * @param convertView a view, possibly null, to be recycled.
     * @param parent the parent hosting this view.
     * @return a view for "Waiting for sync..." at given position.
     */
    @Override
    public View getView(View convertView, ViewGroup parent) {
        final ViewGroup emptyView;
        if (convertView != null) {
            emptyView = (ViewGroup) convertView;
        } else {
            emptyView = (ViewGroup) mInflater.inflate(R.layout.drawer_empty_view, parent, false);
        }
        return emptyView;
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
        return VIEW_WAITING_FOR_SYNC;
    }
}
