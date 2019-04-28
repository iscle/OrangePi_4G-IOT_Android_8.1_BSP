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

import android.annotation.DrawableRes;
import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.android.car.settings.R;

/**
 * Contains logic for a line item represents title text, description text and a toggle widget.
 */
public abstract class IconToggleLineItem
        extends TypedPagedListAdapter.LineItem<IconToggleLineItem.ViewHolder> {
    protected final Context mContext;
    private final CharSequence mTitle;
    protected IconUpdateListener mIconUpdateListener;

    public interface IconUpdateListener {
        void onUpdateIcon(@DrawableRes int iconRes);
    }

    private final View.OnClickListener mOnClickListener = v -> onClicked();

    private final Switch.OnCheckedChangeListener mOnCheckedChangeListener =
            (view, isChecked) -> onToggleClicked(isChecked);

    public IconToggleLineItem(CharSequence title, Context context) {
        mTitle = title;
        mContext = context;
    }

    public int getType() {
        return ICON_TOGGLE_TYPE;
    }

    public void bindViewHolder(ViewHolder viewHolder) {
        viewHolder.title.setText(mTitle);
        viewHolder.summary.setText(getDesc());
        viewHolder.toggle.setChecked(isChecked());
        viewHolder.onUpdateIcon(getIcon());
        viewHolder.itemView.setOnClickListener(mOnClickListener);
        viewHolder.toggle.setOnCheckedChangeListener(mOnCheckedChangeListener);
        mIconUpdateListener = viewHolder;
    }

    static class ViewHolder extends RecyclerView.ViewHolder implements IconUpdateListener {
        public final ImageView icon;
        public final TextView title;
        public final TextView summary;
        public final Switch toggle;

        public ViewHolder(View itemView) {
            super(itemView);
            icon = (ImageView) itemView.findViewById(R.id.icon);
            title = (TextView) itemView.findViewById(R.id.title);
            summary = (TextView) itemView.findViewById(R.id.desc);
            toggle = (Switch) itemView.findViewById(R.id.toggle_switch);
        }

        @Override
        public void onUpdateIcon(@DrawableRes int iconRes) {
            icon.setImageResource(iconRes);
        }
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.icon_toggle_line_item, parent, false);
        return new ViewHolder(v);
    }

    /**
     * Called when any part of the line is clicked.
     * @param isChecked the state of the switch widget at the time of click.
     */
    public abstract void onToggleClicked(boolean isChecked);

    /**
     * called when anywhere other than the toggle on the line item got clicked.
     */
    public abstract void onClicked();

    public abstract boolean isChecked();

    public abstract @DrawableRes int getIcon();

    @Override
    public boolean isClickable() {
        return true;
    }
}
