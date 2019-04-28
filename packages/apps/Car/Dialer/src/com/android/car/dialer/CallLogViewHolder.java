/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.dialer;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.android.car.view.CarListItemViewHolder;

public class CallLogViewHolder extends CarListItemViewHolder {
    public View card;
    public ViewGroup container;
    public LinearLayout callType;
    public CallTypeIconsView callTypeIconsView;
    public ImageView smallIcon;

    public CallLogViewHolder(View v) {
        super(v, R.layout.car_textview);

        card = v.findViewById(R.id.call_log_card);
        callType = (LinearLayout) v.findViewById(R.id.call_type);
        callTypeIconsView = (CallTypeIconsView) v.findViewById(R.id.call_type_icons);
        smallIcon = (ImageView) v.findViewById(R.id.small_icon);
        container = (ViewGroup) v.findViewById(R.id.container);
    }
}
