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
package com.android.documentsui.inspector.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.inspector.InspectorController;

/**
 * Displays different actions to the user. The action that's displayed depends on what Action is
 * passed in to init.
 */
public final class ActionView extends LinearLayout implements InspectorController.ActionDisplay {

    private final TextView mHeader;
    private final ImageView mAppIcon;
    private final TextView mAppName;
    private final ImageButton mActionButton;
    private Action mAction;

    public ActionView(Context context) {
        this(context, null);
    }

    public ActionView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
            Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.inspector_action_view, null);
        addView(view);

        mHeader = (TextView) findViewById(R.id.action_header);
        mAppIcon = (ImageView) findViewById(R.id.app_icon);
        mAppName = (TextView) findViewById(R.id.app_name);
        mActionButton = (ImageButton) findViewById(R.id.inspector_action_button);
    }

    @Override
    public void init(Action action, OnClickListener listener) {
        mAction = action;
        setActionHeader(mAction.getHeader());

        setAppIcon(mAction.getAppIcon());
        setAppName(mAction.getAppName());

        mActionButton.setOnClickListener(listener);
        showAction(true);
    }

    @Override
    public void setVisible(boolean visible) {
        setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setActionHeader(String header) {
        mHeader.setText(header);
    }

    @Override
    public void setAppIcon(Drawable icon) {
        mAppIcon.setImageDrawable(icon);
    }

    @Override
    public void setAppName(String name) {
        mAppName.setText(name);
    }

    @Override
    public void showAction(boolean visible) {

        if (visible) {
            mActionButton.setImageResource(mAction.getButtonIcon());
            mActionButton.setVisibility(View.VISIBLE);
        } else {
            mActionButton.setVisibility(View.GONE);
        }
    }
}