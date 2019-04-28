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
 * limitations under the License
 */

package com.android.car.settings.common;

import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import com.android.car.settings.R;

/**
 * Contains logic for a line item represents title text, description text and a checkbox widget.
 */
public abstract class ToggleLineItem
        extends TypedPagedListAdapter.LineItem<ToggleLineItem.ToggleLineItemViewHolder> {
    private final CharSequence mTitle;

    private View.OnClickListener mOnClickListener = (v) -> {
            Switch switchToggle = (Switch) v.findViewById(R.id.toggle_switch);
            ToggleLineItem.this.onClick(switchToggle.isChecked());
        };

    public ToggleLineItem(CharSequence title) {
        mTitle = title;
    }

    public int getType() {
        return TOGGLE_TYPE;
    }

    public void bindViewHolder(ToggleLineItemViewHolder viewHolder) {
        viewHolder.titleView.setText(mTitle);
        CharSequence desc = getDesc();
        if (TextUtils.isEmpty(desc)) {
            viewHolder.descView.setVisibility(View.GONE);
        } else {
            viewHolder.descView.setVisibility(View.VISIBLE);
            viewHolder.descView.setText(desc);
        }
        viewHolder.toggle.setChecked(isChecked());
        viewHolder.itemView.setOnClickListener(mOnClickListener);
    }

    public static class ToggleLineItemViewHolder extends RecyclerView.ViewHolder {
        public final TextView titleView;
        public final TextView descView;
        public final Switch toggle;

        public ToggleLineItemViewHolder(View view) {
            super(view);
            titleView = (TextView) view.findViewById(R.id.title);
            descView = (TextView) view.findViewById(R.id.desc);
            toggle = (Switch) view.findViewById(R.id.toggle_switch);
        }
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.toggle_line_item, parent, false);
        return new ToggleLineItemViewHolder(v);
    }

    /**
     * Called when any part of the line is clicked.
     * @param isChecked the state of the switch widget at the time of click.
     */
    public abstract void onClick(boolean isChecked);

    public abstract boolean isChecked();

    @Override
    public boolean isClickable() {
        return true;
    }
}
