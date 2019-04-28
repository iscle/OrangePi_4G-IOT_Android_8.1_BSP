/*
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;

public class MessageHeaderContactBadge extends ImageView implements View.OnClickListener {

    private QuickContactBadge mQuickContactBadge;

    private Drawable mDefaultAvatar;

    public MessageHeaderContactBadge(Context context) {
        this(context, null);
    }

    public MessageHeaderContactBadge(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MessageHeaderContactBadge(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        Analytics.getInstance().sendEvent("quick_contact", "clicked", null, 0);
        if (mQuickContactBadge != null) {
            mQuickContactBadge.onClick(v);
        }
    }

    public void setImageToDefault() {
        if (mDefaultAvatar == null) {
            mDefaultAvatar = getResources().getDrawable(R.drawable.ic_contact_picture);
        }
        setImageDrawable(mDefaultAvatar);
    }

    public void assignContactUri(Uri contactUri) {
        if (mQuickContactBadge != null) {
            mQuickContactBadge.assignContactUri(contactUri);
        }
    }

    public void assignContactFromEmail(String emailAddress, boolean lazyLookup) {
        if (mQuickContactBadge != null) {
            mQuickContactBadge.assignContactFromEmail(emailAddress, lazyLookup);
        }
    }

    public void setQuickContactBadge(QuickContactBadge quickContactBadge) {
        mQuickContactBadge = quickContactBadge;
    }
}
