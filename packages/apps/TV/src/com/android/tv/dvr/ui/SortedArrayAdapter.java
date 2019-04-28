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

package com.android.tv.dvr.ui;

import android.support.annotation.VisibleForTesting;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.PresenterSelector;

import com.android.tv.common.SoftPreconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps a set of items sorted
 *
 * <p>{@code T} must have stable IDs.
 */
public abstract class SortedArrayAdapter<T> extends ArrayObjectAdapter {
    private final Comparator<T> mComparator;
    private final int mMaxItemCount;
    private int mExtraItemCount;
    private final Set<Long> mIds = new HashSet<>();

    public SortedArrayAdapter(PresenterSelector presenterSelector, Comparator<T> comparator) {
        this(presenterSelector, comparator, Integer.MAX_VALUE);
    }

    public SortedArrayAdapter(PresenterSelector presenterSelector, Comparator<T> comparator,
            int maxItemCount) {
        super(presenterSelector);
        mComparator = comparator;
        mMaxItemCount = maxItemCount;
        setHasStableIds(true);
    }

    /**
     * Sets the objects in the given collection to the adapter keeping the elements sorted.
     *
     * @param items A {@link Collection} of items to be set.
     */
    @VisibleForTesting
    final void setInitialItems(List<T> items) {
        List<T> itemsCopy = new ArrayList<>(items);
        Collections.sort(itemsCopy, mComparator);
        for (T item : itemsCopy) {
            add(item, true);
            if (size() == mMaxItemCount) {
                break;
            }
        }
    }

    /**
     * Adds an item in sorted order to the adapter.
     *
     * @param item The item to add in sorted order to the adapter.
     */
    @Override
    public final void add(Object item) {
        add((T) item, false);
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Adds an item in sorted order to the adapter.
     *
     * @param item The item to add in sorted order to the adapter.
     * @param insertToEnd If items are inserted in a more or less sorted fashion,
     *                    sets this parameter to {@code true} to search insertion position from
     *                    the end to save search time.
     */
    public final void add(T item, boolean insertToEnd) {
        long newItemId = getId(item);
        SoftPreconditions.checkState(!mIds.contains(newItemId));
        mIds.add(newItemId);
        int i;
        if (insertToEnd) {
            i = findInsertPosition(item);
        } else {
            i = findInsertPositionBinary(item);
        }
        super.add(i, item);
        if (mMaxItemCount < Integer.MAX_VALUE && size() > mMaxItemCount + mExtraItemCount) {
            Object removedItem = get(mMaxItemCount);
            remove(removedItem);
        }
    }

    /**
     * Adds an extra item to the end of the adapter. The items will not be subjected to the sorted
     * order or the maximum number of items. One or more extra items can be added to the adapter.
     * They will be presented in their insertion order.
     */
    public int addExtraItem(T item) {
        long newItemId = getId(item);
        SoftPreconditions.checkState(!mIds.contains(newItemId));
        mIds.add(newItemId);
        super.add(item);
        return ++mExtraItemCount;
    }

    @Override
    public boolean remove(Object item) {
        return removeWithId((T) item);
    }

    /**
     * Removes an item which has the same ID as {@code item}.
     */
    public boolean removeWithId(T item) {
        int index = indexWithId(item);
        return index >= 0 && index < size() && removeItems(index, 1) == 1;
    }

    @Override
    public int removeItems(int position, int count) {
        int upperBound = Math.min(position + count, size());
        for (int i = position; i < upperBound; i++) {
            mIds.remove(getId((T) get(i)));
        }
        if (upperBound > size() - mExtraItemCount) {
            mExtraItemCount -= upperBound - Math.max(size() - mExtraItemCount, position);
        }
        return super.removeItems(position, count);
    }

    @Override
    public void replace(int position, Object item) {
        boolean wasExtra = position >= size() - mExtraItemCount;
        removeItems(position, 1);
        if (!wasExtra) {
            add(item);
        } else {
            addExtraItem((T) item);
        }
    }

    @Override
    public void clear() {
        mIds.clear();
        super.clear();
    }

    /**
     * Changes an item in the list.
     * @param item The item to change.
     */
    public final void change(T item) {
        int oldIndex = indexWithId(item);
        if (oldIndex != -1) {
            T old = (T) get(oldIndex);
            if (mComparator.compare(old, item) == 0) {
                replace(oldIndex, item);
                return;
            }
            remove(old);
        }
        add(item);
    }

    /**
     * Checks whether the item is in the list.
     */
    public final boolean contains(T item) {
        return indexWithId(item) != -1;
    }

    @Override
    public long getId(int position) {
        return getId((T) get(position));
    }

    /**
     * Returns the id of the the given {@code item}, which will be used in {@link #change} to
     * decide if the given item is already existed in the adapter.
     *
     * The id must be stable.
     */
    protected abstract long getId(T item);

    private int indexWithId(T item) {
        long id = getId(item);
        for (int i = 0; i < size() - mExtraItemCount; i++) {
            T r = (T) get(i);
            if (getId(r) == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the position that the given item should be inserted to keep the sorted order.
     */
    public int findInsertPosition(T item) {
        for (int i = size() - mExtraItemCount - 1; i >=0; i--) {
            T r = (T) get(i);
            if (mComparator.compare(r, item) <= 0) {
                return i + 1;
            }
        }
        return 0;
    }

    private int findInsertPositionBinary(T item) {
        int lb = 0;
        int ub = size() - mExtraItemCount - 1;
        while (lb <= ub) {
            int mid = (lb + ub) / 2;
            T r = (T) get(mid);
            int compareResult = mComparator.compare(item, r);
            if (compareResult == 0) {
                return mid;
            } else if (compareResult > 0) {
                lb = mid + 1;
            } else {
                ub = mid - 1;
            }
        }
        return lb;
    }
}