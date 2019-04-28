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

package com.android.car.settings.system;

import android.content.Context;
import android.content.Intent;
import android.widget.ImageView;

import com.android.car.settings.R;
import com.android.car.settings.common.IconTextLineItem;


/**
 * A LineItem that displays info about legal document.
 */
class LegalInfoLineItem extends IconTextLineItem {

    private final Context mContext;

    public LegalInfoLineItem(Context context) {
        super(context.getString(R.string.legal_information));
        mContext = context;
    }

    @Override
    public CharSequence getDesc() {
        return null;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isExpandable() {
        return true;
    }

    @Override
    public void onClick() {
        Intent intent = new Intent();
        intent.setAction("android.settings.WEBVIEW_LICENSE");
        mContext.startActivity(intent);
    }

    @Override
    public void setIcon(ImageView iconView) {
        iconView.setImageResource(R.drawable.ic_settings_about);
    }
}
