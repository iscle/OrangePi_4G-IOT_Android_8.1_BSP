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

package com.android.tv.menu;

import android.content.Context;

import com.android.tv.R;
import com.android.tv.menu.ItemListRowView.ItemListAdapter;

/**
 * A menu item which is used to represents the list of the items.
 * A list will be displayed by a HorizontalGridView with cards, so an adapter
 * for the GridView is necessary.
 */
@SuppressWarnings("rawtypes")
public class ItemListRow extends MenuRow {
    private ItemListAdapter mAdapter;

    public ItemListRow(Context context, Menu menu, int titleResId, int itemHeightResId,
            ItemListAdapter adapter) {
        this(context, menu, context.getString(titleResId), itemHeightResId, adapter);
    }

    public ItemListRow(Context context, Menu menu, String title, int itemHeightResId,
            ItemListAdapter adapter) {
        super(context, menu, title, itemHeightResId);
        mAdapter = adapter;
    }

    /**
     * Returns the adapter.
     */
    public ItemListAdapter<?> getAdapter() {
        return mAdapter;
    }

    public void setAdapter(ItemListAdapter<?> adapter) {
        mAdapter = adapter;
    }

    @Override
    public void update() {
        mAdapter.update();
    }

    @Override
    public boolean isVisible() {
        return mAdapter.getItemCount() > 0;
    }

    @Override
    public int getLayoutResId() {
        return R.layout.item_list;
    }

    @Override
    public String getId() {
        return this.getClass().getName();
    }
}
