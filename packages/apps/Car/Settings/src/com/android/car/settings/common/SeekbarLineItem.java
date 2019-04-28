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
import android.annotation.DrawableRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.car.settings.R;

/**
 * Contains logic for a line item represents a description and a seekbar.
 */
public abstract class SeekbarLineItem
        extends TypedPagedListAdapter.LineItem<SeekbarLineItem.ViewHolder> {
    private final CharSequence mTitle;
    @DrawableRes
    private final Integer mIconResId;

    private SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            SeekbarLineItem.this.onSeekbarChanged(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // no-op
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // no-op
        }
    };

    public SeekbarLineItem(CharSequence title) {
        this(title, null);
    }

    public SeekbarLineItem(CharSequence title, Integer iconResId) {
        mTitle = title;
        mIconResId = iconResId;
    }

    @Override
    public int getType() {
        return SEEKBAR_TYPE;
    }

    @Override
    public void bindViewHolder(ViewHolder viewHolder) {
        viewHolder.titleView.setText(mTitle);
        viewHolder.seekBar.setMax(getMaxSeekbarValue());
        viewHolder.seekBar.setProgress(getSeekbarValue());
        viewHolder.seekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
        if (mIconResId != null) {
            viewHolder.iconView.setVisibility(View.VISIBLE);
            viewHolder.iconView.setImageResource(mIconResId);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final SeekBar seekBar;
        final ImageView iconView;

        public ViewHolder(View view) {
            super(view);
            titleView = view.findViewById(R.id.title);
            seekBar = view.findViewById(R.id.seekbar);
            iconView = view.findViewById(R.id.icon);
        }
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.seekbar_line_item, parent, false);
        return new ViewHolder(v);
    }

    // Seekbar Line item does not have description field for now.
    @Override
    public CharSequence getDesc() {
        return null;
    }

    public abstract int getSeekbarValue();

    public abstract int getMaxSeekbarValue();

    public abstract void onSeekbarChanged(int progress);

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public boolean isExpandable() {
        return false;
    }
}
