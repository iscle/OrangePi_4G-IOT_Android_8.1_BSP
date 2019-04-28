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

package com.android.documentsui.sidebar;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.View.OnDragListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.android.documentsui.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The adapter for the {@link android.widget.ListView} in sidebar. It contains the list of
 * {@link Item}s and provides sub-views to {@link android.widget.ListView}.
 */
class RootsAdapter extends ArrayAdapter<Item> {
    private static final Map<String, Long> sIdMap = new HashMap<>();
    // the next available id to associate with a new string id
    private static long sNextAvailableId;

    private final OnDragListener mDragListener;

    public RootsAdapter(
            Activity activity,
            List<Item> items,
            OnDragListener dragListener) {
        super(activity, 0, items);

        mDragListener = dragListener;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        // Ensure this method is only called in main thread because we don't have any
        // concurrency protection.
        assert(Looper.myLooper() == Looper.getMainLooper());

        String stringId = getItem(position).stringId;

        long id;
        if (sIdMap.containsKey(stringId)) {
            id = sIdMap.get(stringId);
        } else {
            id = sNextAvailableId++;
            sIdMap.put(stringId, id);
        }

        return id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Item item = getItem(position);
        final View view = item.getView(convertView, parent);

        if (item.isRoot()) {
            view.setTag(R.id.item_position_tag, position);
            view.setOnDragListener(mDragListener);
        } else {
            view.setTag(R.id.item_position_tag, null);
            view.setOnDragListener(null);
        }
        return view;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) != 1;
    }

    @Override
    public int getItemViewType(int position) {
        final Item item = getItem(position);
        if (item instanceof RootItem || item instanceof AppItem) {
            return 0;
        } else {
            return 1;
        }
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }
}
