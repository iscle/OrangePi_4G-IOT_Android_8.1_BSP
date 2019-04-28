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

package com.android.car.cluster.sample.cards;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.car.cluster.sample.R;

/**
 * Card that represents hangout notification.
 */
public class MessageCard extends CardView {

    private TextView mContactName;

    public MessageCard(Context context, PriorityChangedListener listener) {
        this(context, null, CardType.HANGOUT, listener);
    }

    public MessageCard(Context context, AttributeSet attrs, @CardType int cardType,
            PriorityChangedListener listener) {
        super(context, attrs, cardType, listener);
    }

    @Override
    protected void init() {
        super.init();

        inflate(R.layout.hangout_layout);

        mDetailsPanel = viewById(R.id.msg_text_panel);
        mContactName = viewById(R.id.msg_card_contact_name);
        setRightIcon(BitmapFactory.decodeResource(getResources(), R.drawable.hangouts_icon));

        mPriority = PRIORITY_HANGOUT_NOTIFICATION;

        // Remove this notification in 5 seconds.
        runDelayed(5000, new Runnable() {
            @Override
            public void run() {
                removeGracefully();
            }
        });
    }

    public void setContactName(String contactName) {
        mContactName.setText(contactName);
    }
}
