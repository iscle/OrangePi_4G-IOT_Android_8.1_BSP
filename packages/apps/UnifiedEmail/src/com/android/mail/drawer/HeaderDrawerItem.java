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
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.utils.FolderUri;

class HeaderDrawerItem extends DrawerItem {
    private final int mResource;

    HeaderDrawerItem(ControllableActivity activity, int resource) {
        super(activity, null, NONFOLDER_ITEM, null);
        mResource = resource;
    }

    @Override
    public String toString() {
        return "[DrawerItem VIEW_HEADER, mResource=" + mResource + "]";
    }

    /**
     * Returns a text divider between divisions.
     *
     * @param convertView a previous view, perhaps null
     * @param parent the parent of this view
     * @return a text header at the given position.
     */
    @Override
    public View getView(View convertView, ViewGroup parent) {
        final View headerView;
        if (convertView != null) {
            headerView = convertView;
        } else {
            headerView = mInflater.inflate(R.layout.folder_list_header, parent, false);
        }
        final TextView textView = (TextView) headerView.findViewById(R.id.header_text);
        textView.setText(mResource);
        return headerView;
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
        return VIEW_HEADER;
    }
}
