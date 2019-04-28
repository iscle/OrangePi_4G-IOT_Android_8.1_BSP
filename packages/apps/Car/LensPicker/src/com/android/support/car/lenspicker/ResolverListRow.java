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
package com.android.support.car.lenspicker;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * A {@link android.support.v7.widget.RecyclerView.ViewHolder} representing a row within the
 * {@link LensResolverActivity}.
 */
public class ResolverListRow extends RecyclerView.ViewHolder {
    private final View mCardView;
    private final ImageView mIconView;
    private final TextView mTitleView;

    public interface ResolverSelectionHandler {
        void onActivitySelected(ResolveInfo info, LensPickerItem item);
    }

    public ResolverListRow(View itemView) {
        super(itemView);
        mCardView = itemView.findViewById(R.id.stream_card);
        mIconView = (ImageView) itemView.findViewById(R.id.icon);
        mTitleView = (TextView) itemView.findViewById(R.id.text);
    }

    public void bind(Context context, ResolveInfo info, LensPickerItem item,
            ResolverSelectionHandler selectionHandler) {
        String label = item.getLabel();
        if (TextUtils.isEmpty(label)) {
            label = context.getString(R.string.unknown_provider_name);
        }

        mTitleView.setText(label);
        mIconView.setImageDrawable(item.getIcon());

        if (selectionHandler != null) {
            mCardView.setOnClickListener(v -> selectionHandler.onActivitySelected(info, item));
        }
    }
}
