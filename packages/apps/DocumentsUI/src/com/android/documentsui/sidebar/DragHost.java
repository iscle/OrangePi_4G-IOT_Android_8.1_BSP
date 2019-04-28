/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.documentsui.sidebar;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import com.android.documentsui.AbstractDragHost;
import com.android.documentsui.ActionHandler;
import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Lookup;

/**
 * Drag host for items in {@link RootsFragment}.
 */
class DragHost extends AbstractDragHost {

    private static final String TAG = "RootsDragHost";
    private static final int DRAG_LOAD_TIME_OUT = 500;

    private final Activity mActivity;
    private final Lookup<View, Item> mDestinationLookup;
    private final ActionHandler mActions;

    DragHost(
            Activity activity,
            DragAndDropManager dragAndDropManager,
            Lookup<View, Item> destinationLookup,
            ActionHandler actions) {
        super(dragAndDropManager);
        mActivity = activity;
        mDragAndDropManager = dragAndDropManager;
        mDestinationLookup = destinationLookup;
        mActions = actions;
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }

    @Override
    public void setDropTargetHighlight(View v, boolean highlight) {
        // SpacerView doesn't have DragListener so this view is guaranteed to be a RootItemView.
        RootItemView itemView = (RootItemView) v;
        itemView.setHighlight(highlight);
    }

    @Override
    public void onViewHovered(View v) {
        // SpacerView doesn't have DragListener so this view is guaranteed to be a RootItemView.
        RootItemView itemView = (RootItemView) v;
        itemView.drawRipple();

        mDestinationLookup.lookup(v).open();
    }

    @Override
    public void onDragEntered(View v) {
        final Item item = mDestinationLookup.lookup(v);

        // If a read-only root, no need to see if top level is writable (it's not)
        if (!item.isDropTarget()) {
            mDragAndDropManager.updateStateToNotAllowed(v);
            return;
        }

        final RootItem rootItem = (RootItem) item;
        if (mDragAndDropManager.updateState(v, rootItem.root, null)
                == DragAndDropManager.STATE_UNKNOWN) {
            mActions.getRootDocument(
                    rootItem.root,
                    DRAG_LOAD_TIME_OUT,
                    (DocumentInfo doc) -> {
                        updateDropShadow(v, rootItem, doc);
                    });
        }
    }

    private void updateDropShadow(
            View v, RootItem rootItem, DocumentInfo rootDoc) {
        if (rootDoc == null) {
            Log.e(TAG, "Root DocumentInfo is null. Defaulting to unknown.");
        } else {
            rootItem.docInfo = rootDoc;
            mDragAndDropManager.updateState(v, rootItem.root, rootDoc);
        }
    }
}
