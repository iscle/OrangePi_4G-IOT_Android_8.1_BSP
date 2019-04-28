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
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.settings.R;

/**
 * Contains logic for a line item represents icon and texts of a title and a description.
 */
public abstract class IconTextLineItem
        extends TypedPagedListAdapter.LineItem<IconTextLineItem.ViewHolder> {
    private final CharSequence mTitle;

    private View.OnClickListener mOnClickListener = (v) -> onClick();

    public IconTextLineItem(CharSequence title) {
        mTitle = title;
    }

    @Override
    public int getType() {
        return ICON_TEXT_TYPE;
    }

    @Override
    public void bindViewHolder(ViewHolder viewHolder) {
        viewHolder.titleView.setText(mTitle);
        setIcon(viewHolder.iconView);
        CharSequence desc = getDesc();
        if (TextUtils.isEmpty(desc)) {
            viewHolder.descView.setVisibility(View.GONE);
        } else {
            viewHolder.descView.setVisibility(View.VISIBLE);
            viewHolder.descView.setText(desc);
        }
        viewHolder.itemView.setOnClickListener(mOnClickListener);
        viewHolder.rightArrow.setVisibility(
                isExpandable() ? View.VISIBLE : View.INVISIBLE);
        viewHolder.dividerLine.setVisibility(
                isClickable() && isEnabled() ? View.VISIBLE : View.INVISIBLE);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView titleView;
        final TextView descView;
        final ImageView iconView;
        final ImageView rightArrow;
        public final View dividerLine;

        public ViewHolder(View view) {
            super(view);
            iconView = (ImageView) view.findViewById(R.id.icon);
            titleView = (TextView) view.findViewById(R.id.title);
            descView = (TextView) view.findViewById(R.id.desc);
            rightArrow = (ImageView) view.findViewById(R.id.right_chevron);
            dividerLine = view.findViewById(R.id.line_item_divider);
        }
    }

    public static RecyclerView.ViewHolder createViewHolder(ViewGroup parent) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.icon_text_line_item, parent, false);
        return new ViewHolder(v);
    }

    public abstract void setIcon(ImageView iconView);

    public abstract void onClick();
}
