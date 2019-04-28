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

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Object representing the current selection. Provides read only access
 * public access, and private write access.
 */
public final class Selection implements Iterable<String>, Parcelable {

    // This class tracks selected items by managing two sets: the saved selection, and the total
    // selection. Saved selections are those which have been completed by tapping an item or by
    // completing a band select operation. Provisional selections are selections which have been
    // temporarily created by an in-progress band select operation (once the user releases the
    // mouse button during a band select operation, the selected items become saved). The total
    // selection is the combination of both the saved selection and the provisional
    // selection. Tracking both separately is necessary to ensure that saved selections do not
    // become deselected when they are removed from the provisional selection; for example, if
    // item A is tapped (and selected), then an in-progress band select covers A then uncovers
    // A, A should still be selected as it has been saved. To ensure this behavior, the saved
    // selection must be tracked separately.
    final Set<String> mSelection;
    final Set<String> mProvisionalSelection;

    public Selection() {
        mSelection = new HashSet<>();
        mProvisionalSelection = new HashSet<>();
    }

    /**
     * Used by CREATOR.
     */
    private Selection(Set<String> selection) {
        mSelection = selection;
        mProvisionalSelection = new HashSet<>();
    }

    /**
     * @param id
     * @return true if the position is currently selected.
     */
    public boolean contains(@Nullable String id) {
        return mSelection.contains(id) || mProvisionalSelection.contains(id);
    }

    /**
     * Returns an {@link Iterator} that iterators over the selection, *excluding*
     * any provisional selection.
     *
     * {@inheritDoc}
     */
    @Override
    public Iterator<String> iterator() {
        return mSelection.iterator();
    }

    /**
     * @return size of the selection including both final and provisional selected items.
     */
    public int size() {
        return mSelection.size() + mProvisionalSelection.size();
    }

    /**
     * @return true if the selection is empty.
     */
    public boolean isEmpty() {
        return mSelection.isEmpty() && mProvisionalSelection.isEmpty();
    }

    /**
     * Sets the provisional selection, which is a temporary selection that can be saved,
     * canceled, or adjusted at a later time. When a new provision selection is applied, the old
     * one (if it exists) is abandoned.
     * @return Map of ids added or removed. Added ids have a value of true, removed are false.
     */
    @VisibleForTesting
    protected Map<String, Boolean> setProvisionalSelection(Set<String> newSelection) {
        Map<String, Boolean> delta = new HashMap<>();

        for (String id: mProvisionalSelection) {
            // Mark each item that used to be in the selection but is unsaved and not in the new
            // provisional selection.
            if (!newSelection.contains(id) && !mSelection.contains(id)) {
                delta.put(id, false);
            }
        }

        for (String id: mSelection) {
            // Mark each item that used to be in the selection but is unsaved and not in the new
            // provisional selection.
            if (!newSelection.contains(id)) {
                delta.put(id, false);
            }
        }

        for (String id: newSelection) {
            // Mark each item that was not previously in the selection but is in the new
            // provisional selection.
            if (!mSelection.contains(id) && !mProvisionalSelection.contains(id)) {
                delta.put(id, true);
            }
        }

        // Now, iterate through the changes and actually add/remove them to/from the current
        // selection. This could not be done in the previous loops because changing the size of
        // the selection mid-iteration changes iteration order erroneously.
        for (Map.Entry<String, Boolean> entry: delta.entrySet()) {
            String id = entry.getKey();
            if (entry.getValue()) {
                mProvisionalSelection.add(id);
            } else {
                mProvisionalSelection.remove(id);
            }
        }

        return delta;
    }

    /**
     * Saves the existing provisional selection. Once the provisional selection is saved,
     * subsequent provisional selections which are different from this existing one cannot
     * cause items in this existing provisional selection to become deselected.
     */
    @VisibleForTesting
    protected void applyProvisionalSelection() {
        mSelection.addAll(mProvisionalSelection);
        mProvisionalSelection.clear();
    }

    /**
     * Abandons the existing provisional selection so that all items provisionally selected are
     * now deselected.
     */
    @VisibleForTesting
    void cancelProvisionalSelection() {
        mProvisionalSelection.clear();
    }

    /** @hide */
    @VisibleForTesting
    public boolean add(String id) {
        if (!mSelection.contains(id)) {
            mSelection.add(id);
            return true;
        }
        return false;
    }

    /** @hide */
    @VisibleForTesting
    boolean remove(String id) {
        if (mSelection.contains(id)) {
            mSelection.remove(id);
            return true;
        }
        return false;
    }

    public void clear() {
        mSelection.clear();
    }

    /**
     * Trims this selection to be the intersection of itself with the set of given IDs.
     */
    public void intersect(Collection<String> ids) {
        mSelection.retainAll(ids);
        mProvisionalSelection.retainAll(ids);
    }

    @VisibleForTesting
    void copyFrom(Selection source) {
        mSelection.clear();
        mSelection.addAll(source.mSelection);

        mProvisionalSelection.clear();
        mProvisionalSelection.addAll(source.mProvisionalSelection);
    }

    @Override
    public String toString() {
        if (size() <= 0) {
            return "size=0, items=[]";
        }

        StringBuilder buffer = new StringBuilder(size() * 28);
        buffer.append("Selection{")
            .append("applied{size=" + mSelection.size())
            .append(", entries=" + mSelection)
            .append("}, provisional{size=" + mProvisionalSelection.size())
            .append(", entries=" + mProvisionalSelection)
            .append("}}");
        return buffer.toString();
    }

    @Override
    public int hashCode() {
        return mSelection.hashCode() ^ mProvisionalSelection.hashCode();
    }

    @Override
    public boolean equals(Object that) {
      if (this == that) {
          return true;
      }

      if (!(that instanceof Selection)) {
          return false;
      }

      return mSelection.equals(((Selection) that).mSelection) &&
              mProvisionalSelection.equals(((Selection) that).mProvisionalSelection);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(new ArrayList<>(mSelection));
        // We don't include provisional selection since it is
        // typically coupled to some other runtime state (like a band).
    }

    public static final ClassLoaderCreator<Selection> CREATOR =
            new ClassLoaderCreator<Selection>() {
        @Override
        public Selection createFromParcel(Parcel in) {
            return createFromParcel(in, null);
        }

        @Override
        public Selection createFromParcel(Parcel in, ClassLoader loader) {
            ArrayList<String> selected = new ArrayList<>();
            in.readStringList(selected);

            return new Selection(new HashSet<>(selected));
        }

        @Override
        public Selection[] newArray(int size) {
            return new Selection[size];
        }
    };
}