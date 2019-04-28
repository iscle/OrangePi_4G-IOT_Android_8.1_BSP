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

package com.android.tv.menu;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.tv.R;

/**
 * A view to render an item of TV options.
 */
public class ActionCardView extends RelativeLayout implements ItemListRowView.CardView<MenuAction> {
    private static final String TAG = MenuView.TAG;
    private static final boolean DEBUG = MenuView.DEBUG;

    private static final float OPACITY_DISABLED = 0.3f;
    private static final float OPACITY_ENABLED = 1.0f;

    private ImageView mIconView;
    private TextView mLabelView;
    private TextView mStateView;

    public ActionCardView(Context context) {
        this(context, null);
    }

    public ActionCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ActionCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = (ImageView) findViewById(R.id.action_card_icon);
        mLabelView = (TextView) findViewById(R.id.action_card_label);
        mStateView = (TextView) findViewById(R.id.action_card_state);
    }

    @Override
    public void onBind(MenuAction action, boolean selected) {
        if (DEBUG) {
            Log.d(TAG, "onBind: action=" + action.getActionName(getContext()));
        }
        mIconView.setImageDrawable(action.getDrawable(getContext()));
        mLabelView.setText(action.getActionName(getContext()));
        mStateView.setText(action.getActionDescription());
        if (action.isEnabled()) {
            setEnabled(true);
            setFocusable(true);
            mIconView.setAlpha(OPACITY_ENABLED);
            mLabelView.setAlpha(OPACITY_ENABLED);
            mStateView.setAlpha(OPACITY_ENABLED);
        } else {
            setEnabled(false);
            setFocusable(false);
            mIconView.setAlpha(OPACITY_DISABLED);
            mLabelView.setAlpha(OPACITY_DISABLED);
            mStateView.setAlpha(OPACITY_DISABLED);
        }
    }

    @Override
    public void onSelected() {
        if (DEBUG) {
            Log.d(TAG, "onSelected: action=" + mLabelView.getText());
        }
    }

    @Override
    public void onDeselected() {
        if (DEBUG) {
            Log.d(TAG, "onDeselected: action=" + mLabelView.getText());
        }
    }

    @Override
    public void onRecycled() { }
}
