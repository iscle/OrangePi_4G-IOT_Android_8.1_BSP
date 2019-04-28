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
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.TextView;

import com.android.car.settings.R;

/**
 * Contains logic for a line item represents title text and a checkbox.
 */
public abstract class CheckBoxLineItem
        extends TypedPagedListAdapter.LineItem<CheckBoxLineItem.CheckboxLineItemViewHolder> {
    private final CharSequence mTitle;

    private View.OnClickListener mOnClickListener = (v) -> {
            CheckBox checkBox = v.findViewById(R.id.checkbox);
            CheckBoxLineItem.this.onClick(checkBox.isChecked());
        };

    public CheckBoxLineItem(CharSequence title) {
        mTitle = title;
    }

    public int getType() {
        return CHECKBOX_TYPE;
    }

    public void bindViewHolder(CheckboxLineItemViewHolder viewHolder) {
        viewHolder.titleView.setText(mTitle);
        viewHolder.checkbox.setChecked(isChecked());
        viewHolder.itemView.setOnClickListener(mOnClickListener);
    }

    public static class CheckboxLineItemViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        public final CheckBox checkbox;

        public CheckboxLineItemViewHolder(View view) {
            super(view);
            titleView = view.findViewById(R.id.title);
            checkbox = view.findViewById(R.id.checkbox);
        }
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.checkbox_line_item, parent, false);
        return new CheckboxLineItemViewHolder(v);
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

    @Override
    public CharSequence getDesc() {
        return null;
    }
}
