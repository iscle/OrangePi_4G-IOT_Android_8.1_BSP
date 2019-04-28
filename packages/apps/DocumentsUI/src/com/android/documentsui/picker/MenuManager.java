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

package com.android.documentsui.picker;

import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;

import com.android.documentsui.base.State;
import com.android.documentsui.queries.SearchViewManager;

import java.util.List;
import java.util.function.IntFunction;

public final class MenuManager extends com.android.documentsui.MenuManager {

    public MenuManager(SearchViewManager searchManager, State displayState, DirectoryDetails dirDetails) {
        super(searchManager, displayState, dirDetails);

    }

    @Override
    public void updateKeyboardShortcutsMenu(
            List<KeyboardShortcutGroup> data, IntFunction<String> stringSupplier) {
        // None as of yet.
    }

    private boolean picking() {
        return mState.action == ACTION_CREATE
                || mState.action == ACTION_OPEN_TREE
                || mState.action == ACTION_PICK_COPY_DESTINATION;
    }

    @Override
    public void updateOptionMenu(Menu menu) {
        super.updateOptionMenu(menu);
        if (picking()) {
            // May already be hidden because the root
            // doesn't support search.
            mSearchManager.showMenu(null);
        }
    }

    @Override
    protected void updateModePicker(MenuItem grid, MenuItem list) {
        // No display options in recent directories
        if (picking() && mDirDetails.isInRecents()) {
            grid.setVisible(false);
            list.setVisible(false);
        } else {
            super.updateModePicker(grid, list);
        }
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll) {
        boolean enabled = mState.allowMultiple;
        selectAll.setVisible(enabled);
        selectAll.setEnabled(enabled);
    }

    @Override
    protected void updateCreateDir(MenuItem createDir) {
        createDir.setVisible(picking());
        createDir.setEnabled(picking() && mDirDetails.canCreateDirectory());
    }

    @Override
    protected void updateOpenInActionMode(MenuItem open, SelectionDetails selectionDetails) {
        updateOpen(open, selectionDetails);
    }

    @Override
    protected void updateOpenInContextMenu(MenuItem open, SelectionDetails selectionDetails) {
        updateOpen(open, selectionDetails);
    }

    private void updateOpen(MenuItem open, SelectionDetails selectionDetails) {
        open.setVisible(mState.action == ACTION_GET_CONTENT
                || mState.action == ACTION_OPEN);
        open.setEnabled(selectionDetails.size() > 0);
    }
}
