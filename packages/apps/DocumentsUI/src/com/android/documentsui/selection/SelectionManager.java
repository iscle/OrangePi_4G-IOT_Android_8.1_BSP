/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.selection;

import static com.android.documentsui.base.Shared.DEBUG;

import android.annotation.IntDef;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.android.documentsui.dirlist.DocumentsAdapter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * MultiSelectManager provides support traditional multi-item selection support to RecyclerView.
 * Additionally it can be configured to restrict selection to a single element, @see
 * #setSelectMode.
 */
public final class SelectionManager {

    @IntDef(flag = true, value = {
            MODE_MULTIPLE,
            MODE_SINGLE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SelectionMode {}
    public static final int MODE_MULTIPLE = 0;
    public static final int MODE_SINGLE = 1;

    @IntDef({
            RANGE_REGULAR,
            RANGE_PROVISIONAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RangeType {}
    public static final int RANGE_REGULAR = 0;
    public static final int RANGE_PROVISIONAL = 1;

    static final String TAG = "SelectionManager";

    private final Selection mSelection = new Selection();

    private final List<Callback> mCallbacks = new ArrayList<>(1);
    private final List<ItemCallback> mItemCallbacks = new ArrayList<>(1);

    private @Nullable DocumentsAdapter mAdapter;
    private @Nullable Range mRanger;
    private boolean mSingleSelect;

    private RecyclerView.AdapterDataObserver mAdapterObserver;
    private SelectionPredicate mCanSetState;

    public SelectionManager(@SelectionMode int mode) {
        mSingleSelect = mode == MODE_SINGLE;
    }

    public SelectionManager reset(DocumentsAdapter adapter, SelectionPredicate canSetState) {

        mCallbacks.clear();
        mItemCallbacks.clear();
        if (mAdapter != null && mAdapterObserver != null) {
            mAdapter.unregisterAdapterDataObserver(mAdapterObserver);
        }

        clearSelectionQuietly();

        assert(adapter != null);
        assert(canSetState != null);

        mAdapter = adapter;
        mCanSetState = canSetState;

        mAdapterObserver = new RecyclerView.AdapterDataObserver() {

            private List<String> mModelIds;

            @Override
            public void onChanged() {
                mModelIds = mAdapter.getModelIds();

                // Update the selection to remove any disappeared IDs.
                mSelection.cancelProvisionalSelection();
                mSelection.intersect(mModelIds);

                notifyDataChanged();
            }

            @Override
            public void onItemRangeChanged(
                    int startPosition, int itemCount, Object payload) {
                // No change in position. Ignoring.
            }

            @Override
            public void onItemRangeInserted(int startPosition, int itemCount) {
                mSelection.cancelProvisionalSelection();
            }

            @Override
            public void onItemRangeRemoved(int startPosition, int itemCount) {
                assert(startPosition >= 0);
                assert(itemCount > 0);

                mSelection.cancelProvisionalSelection();
                // Remove any disappeared IDs from the selection.
                mSelection.intersect(mModelIds);
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                throw new UnsupportedOperationException();
            }
        };

        mAdapter.registerAdapterDataObserver(mAdapterObserver);
        return this;
    }

    void bindContoller(BandController controller) {
        // Provides BandController with access to private mSelection state.
        controller.bindSelection(mSelection);
    }

    /**
     * Adds {@code callback} such that it will be notified when {@code MultiSelectManager}
     * events occur.
     *
     * @param callback
     */
    public void addCallback(Callback callback) {
        assert(callback != null);
        mCallbacks.add(callback);
    }

    public void addItemCallback(ItemCallback itemCallback) {
        assert(itemCallback != null);
        mItemCallbacks.add(itemCallback);
    }

    public boolean hasSelection() {
        return !mSelection.isEmpty();
    }

    /**
     * Returns a Selection object that provides a live view
     * on the current selection.
     *
     * @see #getSelection(Selection) on how to get a snapshot
     *     of the selection that will not reflect future changes
     *     to selection.
     *
     * @return The current selection.
     */
    public Selection getSelection() {
        return mSelection;
    }

    /**
     * Updates {@code dest} to reflect the current selection.
     * @param dest
     *
     * @return The Selection instance passed in, for convenience.
     */
    public Selection getSelection(Selection dest) {
        dest.copyFrom(mSelection);
        return dest;
    }

    @VisibleForTesting
    public void replaceSelection(Iterable<String> ids) {
        clearSelection();
        setItemsSelected(ids, true);
    }

    /**
     * Restores the selected state of specified items. Used in cases such as restore the selection
     * after rotation etc.
     */
    public void restoreSelection(Selection other) {
        setItemsSelectedQuietly(other.mSelection, true);
        // NOTE: We intentionally don't restore provisional selection. It's provisional.
        notifySelectionRestored();
    }

    /**
     * Sets the selected state of the specified items. Note that the callback will NOT
     * be consulted to see if an item can be selected.
     *
     * @param ids
     * @param selected
     * @return
     */
    public boolean setItemsSelected(Iterable<String> ids, boolean selected) {
        final boolean changed = setItemsSelectedQuietly(ids, selected);
        notifySelectionChanged();
        return changed;
    }

    private boolean setItemsSelectedQuietly(Iterable<String> ids, boolean selected) {
        boolean changed = false;
        for (String id: ids) {
            final boolean itemChanged =
                    selected
                    ? canSetState(id, true) && mSelection.add(id)
                    : canSetState(id, false) && mSelection.remove(id);
            if (itemChanged) {
                notifyItemStateChanged(id, selected);
            }
            changed |= itemChanged;
        }
        return changed;
    }

    /**
     * Clears the selection and notifies (if something changes).
     */
    public void clearSelection() {
        if (!hasSelection()) {
            return;
        }

        clearSelectionQuietly();
        notifySelectionChanged();
    }

    /**
     * Clears the selection, without notifying selection listeners. UI elements still need to be
     * notified about state changes so that they can update their appearance.
     */
    private void clearSelectionQuietly() {
        mRanger = null;

        if (!hasSelection()) {
            return;
        }

        Selection oldSelection = getSelection(new Selection());
        mSelection.clear();

        for (String id: oldSelection.mSelection) {
            notifyItemStateChanged(id, false);
        }
        for (String id: oldSelection.mProvisionalSelection) {
            notifyItemStateChanged(id, false);
        }
    }

    /**
     * Toggles selection on the item with the given model ID.
     *
     * @param modelId
     */
    public void toggleSelection(String modelId) {
        assert(modelId != null);

        final boolean changed = mSelection.contains(modelId)
                ? attemptDeselect(modelId)
                : attemptSelect(modelId);

        if (changed) {
            notifySelectionChanged();
        }
    }

    /**
     * Starts a range selection. If a range selection is already active, this will start a new range
     * selection (which will reset the range anchor).
     *
     * @param pos The anchor position for the selection range.
     */
    public void startRangeSelection(int pos) {
        attemptSelect(mAdapter.getModelId(pos));
        setSelectionRangeBegin(pos);
    }

    public void snapRangeSelection(int pos) {
        snapRangeSelection(pos, RANGE_REGULAR);
    }

    void snapProvisionalRangeSelection(int pos) {
        snapRangeSelection(pos, RANGE_PROVISIONAL);
    }

    /*
     * Starts and extends range selection in one go. This assumes item at startPos is not selected
     * beforehand.
     */
    public void formNewSelectionRange(int startPos, int endPos) {
        assert(!mSelection.contains(mAdapter.getModelId(startPos)));
        startRangeSelection(startPos);
        snapRangeSelection(endPos);
    }

    /**
     * Sets the end point for the current range selection, started by a call to
     * {@link #startRangeSelection(int)}. This function should only be called when a range selection
     * is active (see {@link #isRangeSelectionActive()}. Items in the range [anchor, end] will be
     * selected or in provisional select, depending on the type supplied. Note that if the type is
     * provisional select, one should do {@link Selection#applyProvisionalSelection()} at some point
     * before calling on {@link #endRangeSelection()}.
     *
     * @param pos The new end position for the selection range.
     * @param type The type of selection the range should utilize.
     */
    private void snapRangeSelection(int pos, @RangeType int type) {
        if (!isRangeSelectionActive()) {
            throw new IllegalStateException("Range start point not set.");
        }

        mRanger.snapSelection(pos, type);

        // We're being lazy here notifying even when something might not have changed.
        // To make this more correct, we'd need to update the Ranger class to return
        // information about what has changed.
        notifySelectionChanged();
    }

    void cancelProvisionalSelection() {
        for (String id : mSelection.mProvisionalSelection) {
            notifyItemStateChanged(id, false);
        }
        mSelection.cancelProvisionalSelection();
    }

    /**
     * Stops an in-progress range selection. All selection done with
     * {@link #snapRangeSelection(int, int)} with type RANGE_PROVISIONAL will be lost if
     * {@link Selection#applyProvisionalSelection()} is not called beforehand.
     */
    public void endRangeSelection() {
        mRanger = null;
        // Clean up in case there was any leftover provisional selection
        cancelProvisionalSelection();
    }

    /**
     * @return Whether or not there is a current range selection active.
     */
    public boolean isRangeSelectionActive() {
        return mRanger != null;
    }

    /**
     * Sets the magic location at which a selection range begins (the selection anchor). This value
     * is consulted when determining how to extend, and modify selection ranges. Calling this when a
     * range selection is active will reset the range selection.
     */
    public void setSelectionRangeBegin(int position) {
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        if (mSelection.contains(mAdapter.getModelId(position))) {
            mRanger = new Range(this::updateForRange, position);
        }
    }

    /**
     * @param modelId
     * @return True if the update was applied.
     */
    private boolean selectAndNotify(String modelId) {
        boolean changed = mSelection.add(modelId);
        if (changed) {
            notifyItemStateChanged(modelId, true);
        }
        return changed;
    }

    /**
     * @param id
     * @return True if the update was applied.
     */
    private boolean attemptDeselect(String id) {
        assert(id != null);
        if (canSetState(id, false)) {
            mSelection.remove(id);
            notifyItemStateChanged(id, false);

            // if there's nothing in the selection and there is an active ranger it results
            // in unexpected behavior when the user tries to start range selection: the item
            // which the ranger 'thinks' is the already selected anchor becomes unselectable
            if (mSelection.isEmpty() && isRangeSelectionActive()) {
                endRangeSelection();
            }
            if (DEBUG) Log.d(TAG, "Selection after deselect: " + mSelection);
            return true;
        } else {
            if (DEBUG) Log.d(TAG, "Select cancelled by listener.");
            return false;
        }
    }

    /**
     * @param id
     * @return True if the update was applied.
     */
    private boolean attemptSelect(String id) {
        assert(id != null);
        boolean canSelect = canSetState(id, true);
        if (!canSelect) {
            return false;
        }
        if (mSingleSelect && hasSelection()) {
            clearSelectionQuietly();
        }

        selectAndNotify(id);
        return true;
    }

    boolean canSetState(String id, boolean nextState) {
        return mCanSetState.test(id, nextState);
    }

    private void notifyDataChanged() {
        final int lastListener = mItemCallbacks.size() - 1;

        for (int i = lastListener; i >= 0; i--) {
            mItemCallbacks.get(i).onSelectionReset();
        }

        for (String id : mSelection) {
            if (!canSetState(id, true)) {
                attemptDeselect(id);
            } else {
                for (int i = lastListener; i >= 0; i--) {
                    mItemCallbacks.get(i).onItemStateChanged(id, true);
                }
            }
        }
    }

    /**
     * Notifies registered listeners when the selection status of a single item
     * (identified by {@code position}) changes.
     */
    void notifyItemStateChanged(String id, boolean selected) {
        assert(id != null);
        int lastListener = mItemCallbacks.size() - 1;
        for (int i = lastListener; i >= 0; i--) {
            mItemCallbacks.get(i).onItemStateChanged(id, selected);
        }
        mAdapter.onItemSelectionChanged(id);
    }

    /**
     * Notifies registered listeners when the selection has changed. This
     * notification should be sent only once a full series of changes
     * is complete, e.g. clearingSelection, or updating the single
     * selection from one item to another.
     */
    void notifySelectionChanged() {
        int lastListener = mCallbacks.size() - 1;
        for (int i = lastListener; i > -1; i--) {
            mCallbacks.get(i).onSelectionChanged();
        }
    }

    private void notifySelectionRestored() {
        int lastListener = mCallbacks.size() - 1;
        for (int i = lastListener; i > -1; i--) {
            mCallbacks.get(i).onSelectionRestored();
        }
    }

    void updateForRange(int begin, int end, boolean selected, @RangeType int type) {
        switch (type) {
            case RANGE_REGULAR:
                updateForRegularRange(begin, end, selected);
                break;
            case RANGE_PROVISIONAL:
                updateForProvisionalRange(begin, end, selected);
                break;
            default:
                throw new IllegalArgumentException("Invalid range type: " + type);
        }
    }

    private void updateForRegularRange(int begin, int end, boolean selected) {
        assert(end >= begin);
        for (int i = begin; i <= end; i++) {
            String id = mAdapter.getModelId(i);
            if (id == null) {
                continue;
            }

            if (selected) {
                boolean canSelect = canSetState(id, true);
                if (canSelect) {
                    if (mSingleSelect && hasSelection()) {
                        clearSelectionQuietly();
                    }
                    selectAndNotify(id);
                }
            } else {
                attemptDeselect(id);
            }
        }
    }

    private void updateForProvisionalRange(int begin, int end, boolean selected) {
        assert (end >= begin);
        for (int i = begin; i <= end; i++) {
            String id = mAdapter.getModelId(i);
            if (id == null) {
                continue;
            }

            boolean changedState = false;
            if (selected) {
                boolean canSelect = canSetState(id, true);
                if (canSelect && !mSelection.mSelection.contains(id)) {
                    mSelection.mProvisionalSelection.add(id);
                    changedState = true;
                }
            } else {
                mSelection.mProvisionalSelection.remove(id);
                changedState = true;
            }

            // Only notify item callbacks when something's state is actually changed in provisional
            // selection.
            if (changedState) {
                notifyItemStateChanged(id, selected);
            }
        }
        notifySelectionChanged();
    }

    public interface ItemCallback {
        void onItemStateChanged(String id, boolean selected);

        void onSelectionReset();
    }

    public interface Callback {
        /**
         * Called immediately after completion of any set of changes.
         */
        void onSelectionChanged();

        /**
         * Called immediately after selection is restored.
         */
        void onSelectionRestored();
    }

    @FunctionalInterface
    public interface SelectionPredicate {
        boolean test(String id, boolean nextState);
    }
}
