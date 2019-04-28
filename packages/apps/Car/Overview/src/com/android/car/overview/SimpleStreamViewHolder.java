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

import android.app.PendingIntent;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.car.stream.StreamCard;

/**
 * A {@link StreamViewHolder} that binds a {@link StreamCard} to a basic card layout.
 */
public class SimpleStreamViewHolder extends StreamViewHolder {
    private static final String TAG = "SimpleStreamCardVH";

    private final TextView mPrimaryTextView;
    private final TextView mSecondaryTextView;
    private final ImageView mPrimaryIconView;
    private final ImageView mSecondaryIconView;

    private PendingIntent mContentPendingIntent;

    public SimpleStreamViewHolder(Context context, View itemView) {
        super(context, itemView);
        mPrimaryTextView = (TextView) itemView.findViewById(R.id.primary_text);
        mSecondaryTextView = (TextView) itemView.findViewById(R.id.secondary_text);
        mPrimaryIconView = (ImageView) itemView.findViewById(R.id.primary_icon_button);
        mSecondaryIconView = (ImageView) itemView.findViewById(R.id.secondary_icon_button);

        mActionContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mContentPendingIntent == null) {
                    return;
                }

                try {
                    mContentPendingIntent.send(mContext, 0 /* resultCode */, null /* intent */);
                } catch (PendingIntent.CanceledException e) {
                    Log.e(TAG, "Failed to send pending intent for card");
                }
            }
        });
    }

    @Override
    public void bindStreamCard(StreamCard card) {
        super.bindStreamCard(card);

        if (!TextUtils.isEmpty(card.getPrimaryText())) {
            mPrimaryTextView.setText(card.getPrimaryText());
        }

        if (!TextUtils.isEmpty(card.getSecondaryText())) {
            mSecondaryTextView.setText(card.getSecondaryText());
        }

        if (card.getPrimaryIcon() != null) {
            mPrimaryIconView.setImageBitmap(card.getPrimaryIcon());
        }

        if (card.getSecondaryIcon() != null) {
            mSecondaryIconView.setImageBitmap(card.getSecondaryIcon());
        }
        mContentPendingIntent = card.getContentPendingIntent();
    }

    @Override
    protected void resetViews() {
        mPrimaryTextView.setText(null);
        mSecondaryTextView.setText(null);
        mPrimaryIconView.setImageBitmap(null);
        mSecondaryIconView.setImageBitmap(null);
        mContentPendingIntent = null;
    }
}
