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
package com.android.documentsui.selection;

import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.VERBOSE;

import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.android.documentsui.selection.SelectionManager.RangeType;

/**
 * Class providing support for managing range selections.
 */
final class Range {
    private static final int UNDEFINED = -1;

    private final Range.RangeUpdater mUpdater;
    private final int mBegin;
    private int mEnd = UNDEFINED;

    public Range(Range.RangeUpdater updater, int begin) {
        if (DEBUG) Log.d(SelectionManager.TAG, "New Ranger created beginning @ " + begin);
        mUpdater = updater;
        mBegin = begin;
    }

    void snapSelection(int position, @RangeType int type) {
        assert(position != RecyclerView.NO_POSITION);

        if (mEnd == UNDEFINED || mEnd == mBegin) {
            // Reset mEnd so it can be established in establishRange.
            mEnd = UNDEFINED;
            establishRange(position, type);
        } else {
            reviseRange(position, type);
        }
    }

    private void establishRange(int position, @RangeType int type) {
        assert(mEnd == UNDEFINED);

        if (position == mBegin) {
            mEnd = position;
        }

        if (position > mBegin) {
            updateRange(mBegin + 1, position, true, type);
        } else if (position < mBegin) {
            updateRange(position, mBegin - 1, true, type);
        }

        mEnd = position;
    }

    private void reviseRange(int position, @RangeType int type) {
        assert(mEnd != UNDEFINED);
        assert(mBegin != mEnd);

        if (position == mEnd) {
            if (VERBOSE) Log.v(SelectionManager.TAG, "Ignoring no-op revision for range: " + this);
        }

        if (mEnd > mBegin) {
            reviseAscendingRange(position, type);
        } else if (mEnd < mBegin) {
            reviseDescendingRange(position, type);
        }
        // the "else" case is covered by checkState at beginning of method.

        mEnd = position;
    }

    /**
     * Updates an existing ascending seleciton.
     * @param position
     */
    private void reviseAscendingRange(int position, @RangeType int type) {
        // Reducing or reversing the range....
        if (position < mEnd) {
            if (position < mBegin) {
                updateRange(mBegin + 1, mEnd, false, type);
                updateRange(position, mBegin -1, true, type);
            } else {
                updateRange(position + 1, mEnd, false, type);
            }
        }

        // Extending the range...
        else if (position > mEnd) {
            updateRange(mEnd + 1, position, true, type);
        }
    }

    private void reviseDescendingRange(int position, @RangeType int type) {
        // Reducing or reversing the range....
        if (position > mEnd) {
            if (position > mBegin) {
                updateRange(mEnd, mBegin - 1, false, type);
                updateRange(mBegin + 1, position, true, type);
            } else {
                updateRange(mEnd, position - 1, false, type);
            }
        }

        // Extending the range...
        else if (position < mEnd) {
            updateRange(position, mEnd - 1, true, type);
        }
    }

    /**
     * Try to set selection state for all elements in range. Not that callbacks can cancel
     * selection of specific items, so some or even all items may not reflect the desired state
     * after the update is complete.
     *
     * @param begin Adapter position for range start (inclusive).
     * @param end Adapter position for range end (inclusive).
     * @param selected New selection state.
     */
    private void updateRange(int begin, int end, boolean selected, @RangeType int type) {
        mUpdater.updateForRange(begin, end, selected, type);
    }

    @Override
    public String toString() {
        return "Range{begin=" + mBegin + ", end=" + mEnd + "}";
    }

    /*
     * @see {@link MultiSelectManager#updateForRegularRange(int, int , boolean)} and {@link
     * MultiSelectManager#updateForProvisionalRange(int, int, boolean)}
     */
    @FunctionalInterface
    interface RangeUpdater {
        void updateForRange(int begin, int end, boolean selected, @RangeType int type);
    }
}
