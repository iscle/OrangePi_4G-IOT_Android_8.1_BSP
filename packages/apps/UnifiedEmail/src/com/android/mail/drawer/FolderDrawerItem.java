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
import com.android.mail.providers.Folder;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.FolderItemView;
import com.android.mail.utils.FolderUri;

class FolderDrawerItem extends DrawerItem {
    FolderDrawerItem(ControllableActivity activity, Folder folder,
            @DrawerItemCategory int folderCategory) {
        super(activity, folder,  folderCategory, null);
    }

    @Override
    public String toString() {
        return "[DrawerItem VIEW_FOLDER, mFolder=" + mFolder + ", mItemCategory=" +
                mItemCategory + "]";
    }

    /**
     * Return a folder: either a parent folder or a normal (child or flat)
     * folder.
     *
     * @param convertView a view, possibly null, to be recycled.
     * @return a view showing a folder at the given position.
     */
    @Override
    public View getView(View convertView, ViewGroup parent) {
        final FolderItemView folderItemView;
        if (convertView != null) {
            folderItemView = (FolderItemView) convertView;
        } else {
            folderItemView =
                    (FolderItemView) mInflater.inflate(R.layout.folder_item, parent, false);
        }
        folderItemView.bind(mFolder, null /* parentUri */);
        folderItemView.setIcon(mFolder);
        return folderItemView;
    }

    @Override
    public boolean isHighlighted(FolderUri currentFolder, int currentType) {
        // True if folder types and URIs are the same
        if (currentFolder != null && mFolder != null && mFolder.folderUri != null) {
            return (mItemCategory == currentType) && mFolder.folderUri.equals(currentFolder);
        }
        return false;
    }

    @Override
    public boolean isItemEnabled() {
        return true;
    }

    @Override
    public @DrawerItemType int getType() {
        return VIEW_FOLDER;
    }
}
