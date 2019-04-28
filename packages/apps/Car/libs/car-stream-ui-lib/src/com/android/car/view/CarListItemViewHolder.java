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
package com.android.car.view;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewStub;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.car.stream.ui.R;

/**
 * ViewHolder for @layout/sdk_car_list_item that is used to handle the various sdk item templates
 * @hide
 */
public class CarListItemViewHolder extends RecyclerView.ViewHolder {
    public final FrameLayout iconContainer;
    public final ImageView icon;
    public final TextView title;
    public final TextView text;
    public final ImageView rightImage;
    public final CheckBox rightCheckbox;
    public final TextView rightText;
    public final FrameLayout remoteViewsContainer;

    public CarListItemViewHolder(View v, int viewStubLayoutId) {
        super(v);
        icon = (ImageView) v.findViewById(R.id.icon);
        iconContainer = (FrameLayout) v.findViewById(R.id.icon_container);
        title = (TextView) v.findViewById(R.id.title);
        text = (TextView) v.findViewById(R.id.text);
        remoteViewsContainer = (FrameLayout) v.findViewById(R.id.remoteviews);
        ViewStub rightStub = (ViewStub) v.findViewById(R.id.right_item);
        if (rightStub != null) {
            rightStub.setLayoutResource(viewStubLayoutId);
            rightStub.setInflatedId(R.id.right_item);

            if (viewStubLayoutId == R.layout.car_menu_checkbox) {
                rightCheckbox = (CheckBox) rightStub.inflate();
                rightImage = null;
                rightText = null;
            } else if (viewStubLayoutId == R.layout.car_imageview) {
                rightImage = (ImageView) rightStub.inflate();
                rightCheckbox = null;
                rightText = null;
            } else if (viewStubLayoutId == R.layout.car_textview) {
                rightText = (TextView) rightStub.inflate();
                rightCheckbox = null;
                rightImage = null;
            } else {
                rightImage = null;
                rightCheckbox = null;
                rightText = null;
            }
        } else {
            rightImage = null;
            rightCheckbox = null;
            rightText = null;
        }
    }
}
