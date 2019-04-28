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

package com.android.tv.settings.widget;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.View;

/**
 * Maintains a bundle of states for a group of views. Each view must have a unique id to identify
 * it. There are four different strategies {@link #SAVE_NO_CHILD} {@link #SAVE_VISIBLE_CHILD}
 * {@link #SAVE_LIMITED_CHILD} {@link #SAVE_ALL_CHILD}.
 * <p>
 * Why we "invent" another set of strategies beyond the default android view hierarchy saving
 * mechanism? Because android strategy for saving view states has two limitations: all indirect
 * descendant views must have a unique view id or their content will be messed together; no way of
 * defining saving removed views. Those two limitations are critical to AdapterView: AdapterView
 * will inevitably have two descendant views with same view id, we also need save the views when
 * they are scrolled out of viewport and removed.
 * <p>
 * The class is currently used within {@link ScrollAdapterView}, but it might be used by other
 * ViewGroup.
 */
public abstract class ViewsStateBundle {

    /** dont save states of any child views */
    public static final int SAVE_NO_CHILD = 0;
    /** only save visible child views, the states are lost when they are gone */
    public static final int SAVE_VISIBLE_CHILD = 1;
    /** save visible views plus save removed child views states up to {@link #getLimitNumber()} */
    public static final int SAVE_LIMITED_CHILD = 2;
    /**
     * save visible views plus save removed child views without any limitation. This might cause out
     * of memory, only use it when you are dealing with limited data
     */
    public static final int SAVE_ALL_CHILD = 3;

    public static final int SAVE_LIMITED_CHILD_DEFAULT_VALUE = 100;

    private int savePolicy;
    private int limitNumber;

    private final Bundle childStates;

    public ViewsStateBundle(int policy, int limit) {
        savePolicy = policy;
        limitNumber = limit;
        childStates = new Bundle();
    }

    public void clear() {
        childStates.clear();
    }

    /**
     * @return the saved views states
     */
    public final Bundle getChildStates() {
        return childStates;
    }

    /**
     * @return the savePolicy, see {@link #SAVE_NO_CHILD} {@link #SAVE_VISIBLE_CHILD}
     *         {@link #SAVE_LIMITED_CHILD} {@link #SAVE_ALL_CHILD}
     */
    public final int getSavePolicy() {
        return savePolicy;
    }

    /**
     * @return the limitNumber, only works when {@link #getSavePolicy()} is
     *         {@link #SAVE_LIMITED_CHILD}
     */
    public final int getLimitNumber() {
        return limitNumber;
    }

    /**
     * @see ViewsStateBundle#getSavePolicy()
     */
    public final void setSavePolicy(int savePolicy) {
        this.savePolicy = savePolicy;
    }

    /**
     * @see ViewsStateBundle#getLimitNumber()
     */
    public final void setLimitNumber(int limitNumber) {
        this.limitNumber = limitNumber;
    }

    /**
     * Load view from states, it's none operation if the there is no state associated with the id.
     *
     * @param view view where loads into
     * @param id unique id for the view within this ViewsStateBundle
     */
    public final void loadView(View view, int id) {
        String key = getSaveStatesKey(id);
        SparseArray<Parcelable> container = childStates.getSparseParcelableArray(key);
        if (container != null) {
            view.restoreHierarchyState(container);
        }
    }

    /**
     * Save views regardless what's the current policy is.
     *
     * @param view view to save
     * @param id unique id for the view within this ViewsStateBundle
     */
    protected final void saveViewUnchecked(View view, int id) {
        String key = getSaveStatesKey(id);
        SparseArray<Parcelable> container = new SparseArray<Parcelable>();
        view.saveHierarchyState(container);
        childStates.putSparseParcelableArray(key, container);
    }

    /**
     * The visible view is saved when policy is not {@link #SAVE_NO_CHILD}.
     *
     * @param view
     * @param id
     */
    public final void saveVisibleView(View view, int id) {
        if (savePolicy != SAVE_NO_CHILD) {
            saveViewUnchecked(view, id);
        }
    }

    /**
     * Save all visible views
     */
    public final void saveVisibleViews() {
        if (savePolicy != SAVE_NO_CHILD) {
            saveVisibleViewsUnchecked();
        }
    }

    /**
     * Save list of visible views without checking policy. The method is to be implemented by
     * subclass, client should use {@link #saveVisibleViews()}.
     */
    protected abstract void saveVisibleViewsUnchecked();

    /**
     * Save views according to policy.
     *
     * @param view view to save
     * @param id unique id for the view within this ViewsStateBundle
     */
    public final void saveInvisibleView(View view, int id) {
        switch (savePolicy) {
            case SAVE_LIMITED_CHILD:
                if (childStates.size() > limitNumber) {
                    // TODO prune the Bundle to be under limit
                }
                // slip through next case section to save view
            case SAVE_ALL_CHILD:
                saveViewUnchecked(view, id);
                break;
            default:
                break;
        }
    }

    static String getSaveStatesKey(int id) {
        return Integer.toString(id);
    }
}
