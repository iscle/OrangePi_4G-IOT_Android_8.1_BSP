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
import static com.android.documentsui.base.Shared.VERBOSE;

import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.selection.SelectionManager;

import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;

/**
 * Grand unified-ish gesture/event listener for items in the directory list.
 */
public final class UserInputHandler<T extends InputEvent>
        extends GestureDetector.SimpleOnGestureListener
        implements DocumentHolder.KeyboardEventListener {

    private static final String TAG = "UserInputHandler";

    private ActionHandler mActions;
    private final FocusHandler mFocusHandler;
    private final SelectionManager mSelectionMgr;
    private final Function<MotionEvent, T> mEventConverter;
    private final Predicate<DocumentDetails> mSelectable;

    private final EventHandler<InputEvent> mContextMenuClickHandler;

    private final EventHandler<InputEvent> mTouchDragListener;
    private final EventHandler<InputEvent> mGestureSelectHandler;
    private final Runnable mPerformHapticFeedback;

    private final TouchInputDelegate mTouchDelegate;
    private final MouseInputDelegate mMouseDelegate;
    private final KeyInputHandler mKeyListener;

    public UserInputHandler(
            ActionHandler actions,
            FocusHandler focusHandler,
            SelectionManager selectionMgr,
            Function<MotionEvent, T> eventConverter,
            Predicate<DocumentDetails> selectable,
            EventHandler<InputEvent> contextMenuClickHandler,
            EventHandler<InputEvent> touchDragListener,
            EventHandler<InputEvent> gestureSelectHandler,
            Runnable performHapticFeedback) {

        mActions = actions;
        mFocusHandler = focusHandler;
        mSelectionMgr = selectionMgr;
        mEventConverter = eventConverter;
        mSelectable = selectable;
        mContextMenuClickHandler = contextMenuClickHandler;
        mTouchDragListener = touchDragListener;
        mGestureSelectHandler = gestureSelectHandler;
        mPerformHapticFeedback = performHapticFeedback;

        mTouchDelegate = new TouchInputDelegate();
        mMouseDelegate = new MouseInputDelegate();
        mKeyListener = new KeyInputHandler();
    }

    @Override
    public boolean onDown(MotionEvent e) {
        try (T event = mEventConverter.apply(e)) {
            return onDown(event);
        }
    }

    @VisibleForTesting
    boolean onDown(T event) {
        return event.isMouseEvent()
                ? mMouseDelegate.onDown(event)
                : mTouchDelegate.onDown(event);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2,
            float distanceX, float distanceY) {
        try (T event = mEventConverter.apply(e2)) {
            return onScroll(event);
        }
    }

    @VisibleForTesting
    boolean onScroll(T event) {
        return event.isMouseEvent()
                ? mMouseDelegate.onScroll(event)
                : mTouchDelegate.onScroll(event);
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        try (T event = mEventConverter.apply(e)) {
            return onSingleTapUp(event);
        }
    }

    @VisibleForTesting
    boolean onSingleTapUp(T event) {
        return event.isMouseEvent()
                ? mMouseDelegate.onSingleTapUp(event)
                : mTouchDelegate.onSingleTapUp(event);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        try (T event = mEventConverter.apply(e)) {
            return onSingleTapConfirmed(event);
        }
    }

    @VisibleForTesting
    boolean onSingleTapConfirmed(T event) {
        return event.isMouseEvent()
                ? mMouseDelegate.onSingleTapConfirmed(event)
                : mTouchDelegate.onSingleTapConfirmed(event);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        try (T event = mEventConverter.apply(e)) {
            return onDoubleTap(event);
        }
    }

    @VisibleForTesting
    boolean onDoubleTap(T event) {
        return event.isMouseEvent()
                ? mMouseDelegate.onDoubleTap(event)
                : mTouchDelegate.onDoubleTap(event);
    }

    @Override
    public void onLongPress(MotionEvent e) {
        try (T event = mEventConverter.apply(e)) {
            onLongPress(event);
        }
    }

    @VisibleForTesting
    void onLongPress(T event) {
        if (event.isMouseEvent()) {
            mMouseDelegate.onLongPress(event);
        } else {
            mTouchDelegate.onLongPress(event);
        }
    }

    // Only events from RecyclerView are fed into UserInputHandler#onDown.
    // ListeningGestureDetector#onTouch directly calls this method to support context menu in empty
    // view
    boolean onRightClick(MotionEvent e) {
        try (T event = mEventConverter.apply(e)) {
            return mMouseDelegate.onRightClick(event);
        }
    }

    @Override
    public boolean onKey(DocumentHolder doc, int keyCode, KeyEvent event) {
        return mKeyListener.onKey(doc, keyCode, event);
    }

    private boolean selectDocument(DocumentDetails doc) {
        assert(doc != null);
        assert(doc.hasModelId());
        mSelectionMgr.toggleSelection(doc.getModelId());
        mSelectionMgr.setSelectionRangeBegin(doc.getAdapterPosition());

        // we set the focus on this doc so it will be the origin for keyboard events or shift+clicks
        // if there is only a single item selected, otherwise clear focus
        if (mSelectionMgr.getSelection().size() == 1) {
            mFocusHandler.focusDocument(doc.getModelId());
        } else {
            mFocusHandler.clearFocus();
        }
        return true;
    }

    private boolean focusDocument(DocumentDetails doc) {
        assert(doc != null);
        assert(doc.hasModelId());

        mSelectionMgr.clearSelection();
        mFocusHandler.focusDocument(doc.getModelId());
        return true;
    }

    private void extendSelectionRange(DocumentDetails doc) {
        mSelectionMgr.snapRangeSelection(doc.getAdapterPosition());
        mFocusHandler.focusDocument(doc.getModelId());
    }

    boolean isRangeExtension(T event) {
        return event.isShiftKeyDown() && mSelectionMgr.isRangeSelectionActive();
    }

    private boolean shouldClearSelection(T event, DocumentDetails doc) {
        return !event.isCtrlKeyDown()
                && !doc.isInSelectionHotspot(event)
                && !doc.isOverDocIcon(event)
                && !isSelected(doc);
    }

    private boolean isSelected(DocumentDetails doc) {
        return mSelectionMgr.getSelection().contains(doc.getModelId());
    }

    private static final String TTAG = "TouchInputDelegate";
    private final class TouchInputDelegate {

        boolean onDown(T event) {
            if (VERBOSE) Log.v(TTAG, "Delegated onDown event.");
            return false;
        }

        // Don't consume so the RecyclerView will get the event and will get touch-based scrolling
        boolean onScroll(T event) {
            if (VERBOSE) Log.v(TTAG, "Delegated onScroll event.");
            return false;
        }

        boolean onSingleTapUp(T event) {
            if (VERBOSE) Log.v(TTAG, "Delegated onSingleTapUp event.");
            if (!event.isOverModelItem()) {
                if (DEBUG) Log.d(TTAG, "Tap not associated w/ model item. Clearing selection.");
                mSelectionMgr.clearSelection();
                return false;
            }

            DocumentDetails doc = event.getDocumentDetails();
            if (mSelectionMgr.hasSelection()) {
                if (isRangeExtension(event)) {
                    extendSelectionRange(doc);
                } else if (mSelectionMgr.getSelection().contains(doc.getModelId())) {
                    mSelectionMgr.toggleSelection(doc.getModelId());
                } else {
                    selectDocument(doc);
                }

                return true;
            }

            // Touch events select if they occur in the selection hotspot,
            // otherwise they activate.
            return doc.isInSelectionHotspot(event)
                    ? selectDocument(doc)
                    : mActions.openDocument(doc, ActionHandler.VIEW_TYPE_PREVIEW,
                            ActionHandler.VIEW_TYPE_REGULAR);
        }

        boolean onSingleTapConfirmed(T event) {
            if (VERBOSE) Log.v(TTAG, "Delegated onSingleTapConfirmed event.");
            return false;
        }

        boolean onDoubleTap(T event) {
            if (VERBOSE) Log.v(TTAG, "Delegated onDoubleTap event.");
            return false;
        }

        final void onLongPress(T event) {
            if (VERBOSE) Log.v(TTAG, "Delegated onLongPress event.");
            if (!event.isOverModelItem()) {
                if (DEBUG) Log.d(TTAG, "Ignoring LongPress on non-model-backed item.");
                return;
            }

            DocumentDetails doc = event.getDocumentDetails();
            boolean handled = false;
            if (isRangeExtension(event)) {
                extendSelectionRange(doc);
                handled = true;
            } else {
                if (!mSelectionMgr.getSelection().contains(doc.getModelId())) {
                    selectDocument(doc);
                    // If we cannot select it, we didn't apply anchoring - therefore should not
                    // start gesture selection
                    if (mSelectable.test(doc)) {
                        mGestureSelectHandler.accept(event);
                        handled = true;
                    }
                } else {
                    // We only initiate drag and drop on long press for touch to allow regular
                    // touch-based scrolling
                    mTouchDragListener.accept(event);
                    handled = true;
                }
            }
            if (handled) {
                mPerformHapticFeedback.run();
            }
        }
    }

    private static final String MTAG = "MouseInputDelegate";
    private final class MouseInputDelegate {
        // The event has been handled in onSingleTapUp
        private boolean mHandledTapUp;
        // true when the previous event has consumed a right click motion event
        private boolean mHandledOnDown;

        boolean onDown(T event) {
            if (VERBOSE) Log.v(MTAG, "Delegated onDown event.");
            if (event.isSecondaryButtonPressed()
                    || (event.isAltKeyDown() && event.isPrimaryButtonPressed())) {
                mHandledOnDown = true;
                return onRightClick(event);
            }

            return false;
        }

        // Don't scroll content window in response to mouse drag
        boolean onScroll(T event) {
            if (VERBOSE) Log.v(MTAG, "Delegated onScroll event.");
            // If it's two-finger trackpad scrolling, we want to scroll
            return !event.isTouchpadScroll();
        }

        boolean onSingleTapUp(T event) {
            if (VERBOSE) Log.v(MTAG, "Delegated onSingleTapUp event.");

            // See b/27377794. Since we don't get a button state back from UP events, we have to
            // explicitly save this state to know whether something was previously handled by
            // DOWN events or not.
            if (mHandledOnDown) {
                if (VERBOSE) Log.v(MTAG, "Ignoring onSingleTapUp, previously handled in onDown.");
                mHandledOnDown = false;
                return false;
            }

            if (!event.isOverModelItem()) {
                if (DEBUG) Log.d(MTAG, "Tap not associated w/ model item. Clearing selection.");
                mSelectionMgr.clearSelection();
                mFocusHandler.clearFocus();
                return false;
            }

            if (event.isTertiaryButtonPressed()) {
                if (DEBUG) Log.d(MTAG, "Ignoring middle click");
                return false;
            }

            DocumentDetails doc = event.getDocumentDetails();
            if (mSelectionMgr.hasSelection()) {
                if (isRangeExtension(event)) {
                    extendSelectionRange(doc);
                } else {
                    if (shouldClearSelection(event, doc)) {
                        mSelectionMgr.clearSelection();
                    }
                    if (isSelected(doc)) {
                        mSelectionMgr.toggleSelection(doc.getModelId());
                        mFocusHandler.clearFocus();
                    } else {
                        selectOrFocusItem(event);
                    }
                }
                mHandledTapUp = true;
                return true;
            }

            return false;
        }

        boolean onSingleTapConfirmed(T event) {
            if (VERBOSE) Log.v(MTAG, "Delegated onSingleTapConfirmed event.");
            if (mHandledTapUp) {
                if (VERBOSE) Log.v(MTAG, "Ignoring onSingleTapConfirmed, previously handled in onSingleTapUp.");
                mHandledTapUp = false;
                return false;
            }

            if (mSelectionMgr.hasSelection()) {
                return false;  // should have been handled by onSingleTapUp.
            }

            if (!event.isOverItem()) {
                if (DEBUG) Log.d(MTAG, "Ignoring Confirmed Tap on non-item.");
                return false;
            }

            if (event.isTertiaryButtonPressed()) {
                if (DEBUG) Log.d(MTAG, "Ignoring middle click");
                return false;
            }

            @Nullable DocumentDetails doc = event.getDocumentDetails();
            if (doc == null || !doc.hasModelId()) {
                Log.w(MTAG, "Ignoring Confirmed Tap. No document details associated w/ event.");
                return false;
            }

            if (mFocusHandler.hasFocusedItem() && event.isShiftKeyDown()) {
                mSelectionMgr.formNewSelectionRange(mFocusHandler.getFocusPosition(),
                        doc.getAdapterPosition());
            } else {
                selectOrFocusItem(event);
            }
            return true;
        }

        boolean onDoubleTap(T event) {
            if (VERBOSE) Log.v(MTAG, "Delegated onDoubleTap event.");
            mHandledTapUp = false;

            if (!event.isOverModelItem()) {
                if (DEBUG) Log.d(MTAG, "Ignoring DoubleTap on non-model-backed item.");
                return false;
            }

            if (event.isTertiaryButtonPressed()) {
                if (DEBUG) Log.d(MTAG, "Ignoring middle click");
                return false;
            }

            DocumentDetails doc = event.getDocumentDetails();
            return mActions.openDocument(doc, ActionHandler.VIEW_TYPE_REGULAR,
                    ActionHandler.VIEW_TYPE_PREVIEW);
        }

        final void onLongPress(T event) {
            if (VERBOSE) Log.v(MTAG, "Delegated onLongPress event.");
            return;
        }

        private boolean onRightClick(T event) {
            if (VERBOSE) Log.v(MTAG, "Delegated onRightClick event.");
            if (event.isOverModelItem()) {
                DocumentDetails doc = event.getDocumentDetails();
                if (!mSelectionMgr.getSelection().contains(doc.getModelId())) {
                    mSelectionMgr.clearSelection();
                    selectDocument(doc);
                }
            }

            // We always delegate final handling of the event,
            // since the handler might want to show a context menu
            // in an empty area or some other weirdo view.
            return mContextMenuClickHandler.accept(event);
        }

        private void selectOrFocusItem(T event) {
            if (event.isOverDocIcon() || event.isCtrlKeyDown()) {
                selectDocument(event.getDocumentDetails());
            } else {
                focusDocument(event.getDocumentDetails());
            }
        }
    }

    private final class KeyInputHandler {
        // TODO: Refactor FocusManager to depend only on DocumentDetails so we can eliminate
        // difficult to test dependency on DocumentHolder.

        boolean onKey(@Nullable DocumentHolder doc, int keyCode, KeyEvent event) {
            // Only handle key-down events. This is simpler, consistent with most other UIs, and
            // enables the handling of repeated key events from holding down a key.
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            // Ignore tab key events.  Those should be handled by the top-level key handler.
            if (keyCode == KeyEvent.KEYCODE_TAB) {
                return false;
            }

            // Ignore events sent to Addon Holders.
            if (doc != null) {
                int itemType = doc.getItemViewType();
                if (itemType == DocumentsAdapter.ITEM_TYPE_HEADER_MESSAGE
                        || itemType == DocumentsAdapter.ITEM_TYPE_INFLATED_MESSAGE
                        || itemType == DocumentsAdapter.ITEM_TYPE_SECTION_BREAK) {
                    return false;
                }
            }

            if (mFocusHandler.handleKey(doc, keyCode, event)) {
                // Handle range selection adjustments. Extending the selection will adjust the
                // bounds of the in-progress range selection. Each time an unshifted navigation
                // event is received, the range selection is restarted.
                if (shouldExtendSelection(doc, event)) {
                    if (!mSelectionMgr.isRangeSelectionActive()) {
                        // Start a range selection if one isn't active
                        mSelectionMgr.startRangeSelection(doc.getAdapterPosition());
                    }
                    mSelectionMgr.snapRangeSelection(mFocusHandler.getFocusPosition());
                } else {
                    mSelectionMgr.endRangeSelection();
                    mSelectionMgr.clearSelection();
                }
                return true;
            }

            // we don't yet have a mechanism to handle opening/previewing multiple documents at once
            if (mSelectionMgr.getSelection().size() > 1) {
                return false;
            }

            // Handle enter key events
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_BUTTON_A:
                    return mActions.openDocument(doc, ActionHandler.VIEW_TYPE_REGULAR,
                            ActionHandler.VIEW_TYPE_PREVIEW);
                case KeyEvent.KEYCODE_SPACE:
                    return mActions.openDocument(doc, ActionHandler.VIEW_TYPE_PREVIEW,
                            ActionHandler.VIEW_TYPE_NONE);
            }

            return false;
        }

        private boolean shouldExtendSelection(DocumentDetails doc, KeyEvent event) {
            if (!Events.isNavigationKeyCode(event.getKeyCode()) || !event.isShiftPressed()) {
                return false;
            }

            return mSelectable.test(doc);
        }
    }
}
