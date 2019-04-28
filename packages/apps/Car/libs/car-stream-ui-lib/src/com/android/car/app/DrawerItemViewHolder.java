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
 * limitations under the License.
 */

package com.android.car.app;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.stream.ui.R;

/**
 * Re-usable ViewHolder for displaying items in the Drawer PagedListView.
 */
public class DrawerItemViewHolder extends RecyclerView.ViewHolder {
    private final ImageView mIcon;
    private final TextView mTitle;
    private final TextView mText;
    private final ImageView mRightIcon;

    DrawerItemViewHolder(View view) {
        super(view);
        mIcon = view.findViewById(R.id.icon);
        mTitle = view.findViewById(R.id.title);
        if (mIcon == null || mTitle == null) {
            throw new IllegalArgumentException("Missing required elements in provided view!");
        }
        // Next two are optional and may be null.
        mText = view.findViewById(R.id.text);
        mRightIcon = view.findViewById(R.id.right_icon);
    }

    /**
     * @return Icon ImageView from inflated layout.
     */
    @NonNull
    public ImageView getIcon() {
        return mIcon;
    }

    /**
     * @return Main title TextView from inflated layout.
     */
    @NonNull
    public TextView getTitle() {
        return mTitle;
    }

    /**
     * @return Main text TextView from inflated layout. Will be {@code null} for small and empty
     *      item layouts.
     */
    @Nullable
    public TextView getText() {
        return mText;
    }

    /**
     * @return Right-Icon ImageView from inflated layout. Will be {@code null} for empty-item
     *      layout.
     */
    @Nullable
    public ImageView getRightIcon() {
        return mRightIcon;
    }

    /**
     * Set click-listener on the view wrapped by this ViewHolder.
     */
    void setItemClickListener(@Nullable DrawerItemClickListener listener) {
        if (listener != null) {
            itemView.setOnClickListener((unusedView) -> listener.onItemClick(getAdapterPosition()));
        } else {
            itemView.setOnClickListener(null);
        }
    }
}
