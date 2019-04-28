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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.Shared.DEBUG;

import android.net.Uri;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.View;

import com.android.documentsui.DragAndDropManager;
import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.Model;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.base.State;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * Listens for potential "drag-like" events and kick-start dragging as needed. Also allows external
 * direct call to {@code #startDrag(RecyclerView, View)} if explicit start is needed, such as long-
 * pressing on an item via touch. (e.g. {@link UserInputHandler#onLongPress(InputEvent)} via touch.)
 */
interface DragStartListener {

    static final DragStartListener DUMMY = new DragStartListener() {
        @Override
        public boolean onMouseDragEvent(InputEvent event) {
            return false;
        }
        @Override
        public boolean onTouchDragEvent(InputEvent event) {
            return false;
        }
    };

    boolean onMouseDragEvent(InputEvent event);
    boolean onTouchDragEvent(InputEvent event);

    @VisibleForTesting
    class ActiveListener implements DragStartListener {

        private static String TAG = "DragStartListener";

        private final IconHelper mIconHelper;
        private final State mState;
        private final SelectionManager mSelectionMgr;
        private final SelectionDetails mSelectionDetails;
        private final ViewFinder mViewFinder;
        private final Function<View, String> mIdFinder;
        private final Function<Selection, List<DocumentInfo>> mDocsConverter;
        private final DragAndDropManager mDragAndDropManager;

        // use DragStartListener.create
        @VisibleForTesting
        public ActiveListener(
                IconHelper iconHelper,
                State state,
                SelectionManager selectionMgr,
                SelectionDetails selectionDetails,
                ViewFinder viewFinder,
                Function<View, String> idFinder,
                Function<Selection, List<DocumentInfo>> docsConverter,
                DragAndDropManager dragAndDropManager) {

            mIconHelper = iconHelper;
            mState = state;
            mSelectionMgr = selectionMgr;
            mSelectionDetails = selectionDetails;
            mViewFinder = viewFinder;
            mIdFinder = idFinder;
            mDocsConverter = docsConverter;
            mDragAndDropManager = dragAndDropManager;
        }

        @Override
        public final boolean onMouseDragEvent(InputEvent event) {
            assert(Events.isMouseDragEvent(event));
            return startDrag(mViewFinder.findView(event.getX(), event.getY()), event);
        }

        @Override
        public final boolean onTouchDragEvent(InputEvent event) {
            return startDrag(mViewFinder.findView(event.getX(), event.getY()), event);
        }

        /**
         * May be called externally when drag is initiated from other event handling code.
         */
        private boolean startDrag(@Nullable View view, InputEvent event) {

            if (view == null) {
                if (DEBUG) Log.d(TAG, "Ignoring drag event, null view.");
                return false;
            }

            @Nullable String modelId = mIdFinder.apply(view);
            if (modelId == null) {
                if (DEBUG) Log.d(TAG, "Ignoring drag on view not represented in model.");
                return false;
            }

            Selection selection = getSelectionToBeCopied(modelId, event);

            final List<DocumentInfo> srcs = mDocsConverter.apply(selection);

            final List<Uri> invalidDest = new ArrayList<>(srcs.size() + 1);
            for (DocumentInfo doc : srcs) {
                invalidDest.add(doc.derivedUri);
            }

            final DocumentInfo parent = mState.stack.peek();
            // parent is null when we're in Recents
            if (parent != null) {
                invalidDest.add(parent.derivedUri);
            }

            mDragAndDropManager.startDrag(view, srcs, mState.stack.getRoot(), invalidDest,
                    mSelectionDetails, mIconHelper, parent);

            return true;
        }

        /**
         * Given the InputEvent (for CTRL case) and modelId of the view associated with the
         * coordinates of the event, return a valid selection for drag and drop operation
         */
        @VisibleForTesting
        Selection getSelectionToBeCopied(String modelId, InputEvent event) {
            Selection selection = new Selection();
            // If CTRL-key is held down and there's other existing selection, add item to
            // selection (if not already selected)
            if (event.isCtrlKeyDown() && !mSelectionMgr.getSelection().contains(modelId)
                    && mSelectionMgr.hasSelection()) {
                mSelectionMgr.toggleSelection(modelId);
            }

            if (mSelectionMgr.getSelection().contains(modelId)) {
                mSelectionMgr.getSelection(selection);
            } else {
                selection.add(modelId);
                mSelectionMgr.clearSelection();
            }
            return selection;
        }
    }

    static DragStartListener create(
            IconHelper iconHelper,
            Model model,
            SelectionManager selectionMgr,
            SelectionDetails selectionDetails,
            State state,
            Function<View, String> idFinder,
            ViewFinder viewFinder,
            DragAndDropManager dragAndDropManager) {

        return new ActiveListener(
                iconHelper,
                state,
                selectionMgr,
                selectionDetails,
                viewFinder,
                idFinder,
                model::getDocuments,
                dragAndDropManager);
    }

    @FunctionalInterface
    interface ViewFinder {
        @Nullable View findView(float x, float y);
    }
}
