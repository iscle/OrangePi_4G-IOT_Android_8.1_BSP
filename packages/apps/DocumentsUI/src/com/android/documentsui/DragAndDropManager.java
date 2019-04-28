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

package com.android.documentsui;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.annotation.VisibleForTesting;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.View;

import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.IconHelper;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Manager that tracks control key state, calculates the default file operation (move or copy)
 * when user drops, and updates drag shadow state.
 */
public interface DragAndDropManager {

    @IntDef({ STATE_NOT_ALLOWED, STATE_UNKNOWN, STATE_MOVE, STATE_COPY })
    @Retention(RetentionPolicy.SOURCE)
    @interface State {}
    int STATE_UNKNOWN = 0;
    int STATE_NOT_ALLOWED = 1;
    int STATE_MOVE = 2;
    int STATE_COPY = 3;

    /**
     * Intercepts and handles a {@link KeyEvent}. Used to track the state of Ctrl key state.
     */
    void onKeyEvent(KeyEvent event);

    /**
     * Starts a drag and drop.
     *
     * @param v the view which
     *          {@link View#startDragAndDrop(ClipData, View.DragShadowBuilder, Object, int)} will be
     *          called.
     * @param srcs documents that are dragged
     * @param root the root in which documents being dragged are
     * @param invalidDest destinations that don't accept this drag and drop
     * @param iconHelper used to load document icons
     * @param parent {@link DocumentInfo} of the container of srcs
     */
    void startDrag(
            View v,
            List<DocumentInfo> srcs,
            RootInfo root,
            List<Uri> invalidDest,
            SelectionDetails selectionDetails,
            IconHelper iconHelper,
            @Nullable DocumentInfo parent);

    /**
     * Checks whether the document can be spring opened.
     * @param root the root in which the document is
     * @param doc the document to check
     * @return true if policy allows spring opening it; false otherwise
     */
    boolean canSpringOpen(RootInfo root, DocumentInfo doc);

    /**
     * Updates the state to {@link #STATE_NOT_ALLOWED} without any further checks. This is used when
     * the UI component that handles the drag event already has enough information to disallow
     * dropping by itself.
     *
     * @param v the view which {@link View#updateDragShadow(View.DragShadowBuilder)} will be called.
     */
    void updateStateToNotAllowed(View v);

    /**
     * Updates the state according to the destination passed.
     * @param v the view which {@link View#updateDragShadow(View.DragShadowBuilder)} will be called.
     * @param destRoot the root of the destination document.
     * @param destDoc the destination document. Can be null if this is TBD. Must be a folder.
     * @return the new state. Can be any state in {@link State}.
     */
    @State int updateState(
            View v, RootInfo destRoot, @Nullable DocumentInfo destDoc);

    /**
     * Resets state back to {@link #STATE_UNKNOWN}. This is used when user drags items leaving a UI
     * component.
     * @param v the view which {@link View#updateDragShadow(View.DragShadowBuilder)} will be called.
     */
    void resetState(View v);

    /**
     * Drops items onto the a root.
     *
     * @param clipData the clip data that contains sources information.
     * @param localState used to determine if this is a multi-window drag and drop.
     * @param destRoot the target root
     * @param actions {@link ActionHandler} used to load root document.
     * @param callback callback called when file operation is rejected or scheduled.
     * @return true if target accepts this drop; false otherwise
     */
    boolean drop(ClipData clipData, Object localState, RootInfo destRoot, ActionHandler actions,
            FileOperations.Callback callback);

    /**
     * Drops items onto the target.
     *
     * @param clipData the clip data that contains sources information.
     * @param localState used to determine if this is a multi-window drag and drop.
     * @param dstStack the document stack pointing to the destination folder.
     * @param callback callback called when file operation is rejected or scheduled.
     * @return true if target accepts this drop; false otherwise
     */
    boolean drop(ClipData clipData, Object localState, DocumentStack dstStack,
            FileOperations.Callback callback);

    /**
     * Called when drag and drop ended.
     *
     * This can be called multiple times as multiple {@link View.OnDragListener} might delegate
     * {@link DragEvent#ACTION_DRAG_ENDED} events to this class so any work inside needs to be
     * idempotent.
     */
    void dragEnded();

    static DragAndDropManager create(Context context, DocumentClipper clipper) {
        return new RuntimeDragAndDropManager(context, clipper);
    }

    class RuntimeDragAndDropManager implements DragAndDropManager {
        private static final String SRC_ROOT_KEY = "dragAndDropMgr:srcRoot";

        private final Context mContext;
        private final DocumentClipper mClipper;
        private final DragShadowBuilder mShadowBuilder;
        private final Drawable mDefaultShadowIcon;

        private @State int mState = STATE_UNKNOWN;

        // Key events info. This is used to derive state when user drags items into a view to derive
        // type of file operations.
        private boolean mIsCtrlPressed;

        // Drag events info. These are used to derive state and update drag shadow when user changes
        // Ctrl key state.
        private View mView;
        private List<Uri> mInvalidDest;
        private ClipData mClipData;
        private RootInfo mDestRoot;
        private DocumentInfo mDestDoc;

        // Boolean flag for current drag and drop operation. Returns true if the files can only
        // be copied (ie. files that don't support delete or remove).
        private boolean mMustBeCopied;

        private RuntimeDragAndDropManager(Context context, DocumentClipper clipper) {
            this(
                    context.getApplicationContext(),
                    clipper,
                    new DragShadowBuilder(context),
                    context.getDrawable(R.drawable.ic_doc_generic));
        }

        @VisibleForTesting
        RuntimeDragAndDropManager(Context context, DocumentClipper clipper,
                DragShadowBuilder builder, Drawable defaultShadowIcon) {
            mContext = context;
            mClipper = clipper;
            mShadowBuilder = builder;
            mDefaultShadowIcon = defaultShadowIcon;
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_CTRL_LEFT:
                case KeyEvent.KEYCODE_CTRL_RIGHT:
                    adjustCtrlKeyCount(event);
            }
        }

        private void adjustCtrlKeyCount(KeyEvent event) {
            assert(event.getKeyCode() == KeyEvent.KEYCODE_CTRL_LEFT
                    || event.getKeyCode() == KeyEvent.KEYCODE_CTRL_RIGHT);

            mIsCtrlPressed = event.isCtrlPressed();

            // There is an ongoing drag and drop if mView is not null.
            if (mView != null) {
                // There is no need to update the state if current state is unknown or not allowed.
                if (mState == STATE_COPY || mState == STATE_MOVE) {
                    updateState(mView, mDestRoot, mDestDoc);
                }
            }
        }

        @Override
        public void startDrag(
                View v,
                List<DocumentInfo> srcs,
                RootInfo root,
                List<Uri> invalidDest,
                SelectionDetails selectionDetails,
                IconHelper iconHelper,
                @Nullable DocumentInfo parent) {

            mView = v;
            mInvalidDest = invalidDest;
            mMustBeCopied = !selectionDetails.canDelete();

            List<Uri> uris = new ArrayList<>(srcs.size());
            for (DocumentInfo doc : srcs) {
                uris.add(doc.derivedUri);
            }
            mClipData = (parent == null)
                    ? mClipper.getClipDataForDocuments(uris, FileOperationService.OPERATION_UNKNOWN)
                    : mClipper.getClipDataForDocuments(
                            uris, FileOperationService.OPERATION_UNKNOWN, parent);
            mClipData.getDescription().getExtras()
                    .putString(SRC_ROOT_KEY, root.getUri().toString());

            updateShadow(srcs, iconHelper);

            int flag = View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_OPAQUE;
            if (!selectionDetails.containsFilesInArchive()) {
                flag |= View.DRAG_FLAG_GLOBAL_URI_READ
                        | View.DRAG_FLAG_GLOBAL_URI_WRITE;
            }
            startDragAndDrop(
                    v,
                    mClipData,
                    mShadowBuilder,
                    this, // Used to detect multi-window drag and drop
                    flag);
        }

        private void updateShadow(List<DocumentInfo> srcs, IconHelper iconHelper) {
            final String title;
            final Drawable icon;

            final int size = srcs.size();
            if (size == 1) {
                DocumentInfo doc = srcs.get(0);
                title = doc.displayName;
                icon = iconHelper.getDocumentIcon(mContext, doc);
            } else {
                title = mContext.getResources()
                        .getQuantityString(R.plurals.elements_dragged, size, size);
                icon = mDefaultShadowIcon;
            }

            mShadowBuilder.updateTitle(title);
            mShadowBuilder.updateIcon(icon);

            mShadowBuilder.onStateUpdated(STATE_UNKNOWN);
        }

        /**
         * A workaround of that
         * {@link View#startDragAndDrop(ClipData, View.DragShadowBuilder, Object, int)} is final.
         */
        @VisibleForTesting
        void startDragAndDrop(View v, ClipData clipData, DragShadowBuilder builder,
                Object localState, int flags) {
            v.startDragAndDrop(clipData, builder, localState, flags);
        }

        @Override
        public boolean canSpringOpen(RootInfo root, DocumentInfo doc) {
            return isValidDestination(root, doc.derivedUri);
        }

        @Override
        public void updateStateToNotAllowed(View v) {
            mView = v;
            updateState(STATE_NOT_ALLOWED);
        }

        @Override
        public @State int updateState(
                View v, RootInfo destRoot, @Nullable DocumentInfo destDoc) {

            mView = v;
            mDestRoot = destRoot;
            mDestDoc = destDoc;

            if (!destRoot.supportsCreate()) {
                updateState(STATE_NOT_ALLOWED);
                return STATE_NOT_ALLOWED;
            }

            if (destDoc == null) {
                updateState(STATE_UNKNOWN);
                return STATE_UNKNOWN;
            }

            assert(destDoc.isDirectory());

            if (!destDoc.isCreateSupported() || mInvalidDest.contains(destDoc.derivedUri)) {
                updateState(STATE_NOT_ALLOWED);
                return STATE_NOT_ALLOWED;
            }

            @State int state;
            final @OpType int opType = calculateOpType(mClipData, destRoot);
            switch (opType) {
                case FileOperationService.OPERATION_COPY:
                    state = STATE_COPY;
                    break;
                case FileOperationService.OPERATION_MOVE:
                    state = STATE_MOVE;
                    break;
                default:
                    // Should never happen
                    throw new IllegalStateException("Unknown opType: " + opType);
            }

            updateState(state);
            return state;
        }

        @Override
        public void resetState(View v) {
            mView = v;

            updateState(STATE_UNKNOWN);
        }

        private void updateState(@State int state) {
            mState = state;

            mShadowBuilder.onStateUpdated(state);
            updateDragShadow(mView);
        }

        /**
         * A workaround of that {@link View#updateDragShadow(View.DragShadowBuilder)} is final.
         */
        @VisibleForTesting
        void updateDragShadow(View v) {
            v.updateDragShadow(mShadowBuilder);
        }

        @Override
        public boolean drop(ClipData clipData, Object localState, RootInfo destRoot,
                ActionHandler action, FileOperations.Callback callback) {

            final Uri rootDocUri =
                    DocumentsContract.buildDocumentUri(destRoot.authority, destRoot.documentId);
            if (!isValidDestination(destRoot, rootDocUri)) {
                return false;
            }

            // Calculate the op type now just in case user releases Ctrl key while we're obtaining
            // root document in the background.
            final @OpType int opType = calculateOpType(clipData, destRoot);
            action.getRootDocument(
                    destRoot,
                    TimeoutTask.DEFAULT_TIMEOUT,
                    (DocumentInfo doc) -> {
                        dropOnRootDocument(clipData, localState, destRoot, doc, opType, callback);
                    });

            return true;
        }

        private void dropOnRootDocument(
                ClipData clipData,
                Object localState,
                RootInfo destRoot,
                @Nullable DocumentInfo destRootDoc,
                @OpType int opType,
                FileOperations.Callback callback) {
            if (destRootDoc == null) {
                callback.onOperationResult(
                        FileOperations.Callback.STATUS_FAILED,
                        opType,
                        0);
            } else {
                dropChecked(
                        clipData,
                        localState,
                        new DocumentStack(destRoot, destRootDoc),
                        opType,
                        callback);
            }
        }

        @Override
        public boolean drop(ClipData clipData, Object localState, DocumentStack dstStack,
                FileOperations.Callback callback) {

            if (!canCopyTo(dstStack)) {
                return false;
            }

            dropChecked(
                    clipData,
                    localState,
                    dstStack,
                    calculateOpType(clipData, dstStack.getRoot()),
                    callback);
            return true;
        }

        private void dropChecked(ClipData clipData, Object localState, DocumentStack dstStack,
                @OpType int opType, FileOperations.Callback callback) {

            // Recognize multi-window drag and drop based on the fact that localState is not
            // carried between processes. It will stop working when the localsState behavior
            // is changed. The info about window should be passed in the localState then.
            // The localState could also be null for copying from Recents in single window
            // mode, but Recents doesn't offer this functionality (no directories).
            Metrics.logUserAction(mContext,
                    localState == null ? Metrics.USER_ACTION_DRAG_N_DROP_MULTI_WINDOW
                            : Metrics.USER_ACTION_DRAG_N_DROP);

            mClipper.copyFromClipData(dstStack, clipData, opType, callback);
        }

        @Override
        public void dragEnded() {
            // Multiple drag listeners might delegate drag ended event to this method, so anything
            // in this method needs to be idempotent. Otherwise we need to designate one listener
            // that always exists and only let it notify us when drag ended, which will further
            // complicate code and introduce one more coupling. This is a Android framework
            // limitation.

            mView = null;
            mInvalidDest = null;
            mClipData = null;
            mDestDoc = null;
            mDestRoot = null;
            mMustBeCopied = false;
        }

        private @OpType int calculateOpType(ClipData clipData, RootInfo destRoot) {
            if (mMustBeCopied) {
                return FileOperationService.OPERATION_COPY;
            }

            final String srcRootUri = clipData.getDescription().getExtras().getString(SRC_ROOT_KEY);
            final String destRootUri = destRoot.getUri().toString();

            assert(srcRootUri != null);
            assert(destRootUri != null);

            if (srcRootUri.equals(destRootUri)) {
                return mIsCtrlPressed
                        ? FileOperationService.OPERATION_COPY
                        : FileOperationService.OPERATION_MOVE;
            } else {
                return mIsCtrlPressed
                        ? FileOperationService.OPERATION_MOVE
                        : FileOperationService.OPERATION_COPY;
            }
        }

        private boolean canCopyTo(DocumentStack dstStack) {
            final RootInfo root = dstStack.getRoot();
            final DocumentInfo dst = dstStack.peek();
            return isValidDestination(root, dst.derivedUri);
        }

        private boolean isValidDestination(RootInfo root, Uri dstUri) {
            return root.supportsCreate()  && !mInvalidDest.contains(dstUri);
        }
    }
}
