/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.overview;

import android.content.Context;
import android.support.annotation.CallSuper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import com.android.car.stream.StreamCard;

/**
 * A base {@link RecyclerView.ViewHolder} for binding by the {@link StreamAdapter}.
 */
public abstract class StreamViewHolder extends RecyclerView.ViewHolder {
    protected View mActionContainer;
    protected Context mContext;

    public StreamViewHolder(Context context, View itemView) {
        super(itemView);
        mContext = context;
        mActionContainer = itemView.findViewById(R.id.primary_action_container);
        if (mActionContainer == null) {
            throw new IllegalStateException("primary_action_container not found in layout." +
                    " Cards must have an affordance for the a primary action.");
        }
    }

    /**
     * Bind a {@link StreamCard} to the views being held by this {@link RecyclerView.ViewHolder}
     * @param card
     */
    @CallSuper
    public void bindStreamCard(StreamCard card) {
        resetViews();
    }

    protected abstract void resetViews();
}
