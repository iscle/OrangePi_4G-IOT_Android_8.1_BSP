/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.tv.quicksettings;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class DialogAdapter extends RecyclerView.Adapter<DialogAdapter.ViewHolder> {

    private static final float FOCUSED_SCALE = 1f;
    private static final float UNFOCUSED_SCALE = 0.5f;

    private final ArrayList<Setting> mSettings;
    private final int mPivotX;
    private final int mPivotY;
    private final SettingClickedListener mSettingClickedListener;

    public DialogAdapter(ArrayList<Setting> settings, int pivotX, int pivotY,
            SettingClickedListener settingClickedListener) {
        mSettings = settings;
        mPivotX = pivotX;
        mPivotY = pivotY;
        mSettingClickedListener = settingClickedListener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.dialog_setting, parent,
                false);
        final ViewHolder vh = new ViewHolder(v);
        v.setPivotX(mPivotX);
        v.setPivotY(mPivotY);
        v.setScaleX(UNFOCUSED_SCALE);
        v.setScaleY(UNFOCUSED_SCALE);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Setting s = vh.mSetting;
                if (s != null) {
                    mSettingClickedListener.onSettingClicked(s);
                }
            }
        });
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean focusGained) {
                float scale = focusGained ? FOCUSED_SCALE : UNFOCUSED_SCALE;
                v.animate().cancel();
                v.animate().scaleX(scale).scaleY(scale).setDuration(
                        v.getContext().getResources()
                                .getInteger(android.R.integer.config_shortAnimTime))
                        .start();
            }
        });
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Setting s = mSettings.get(position);
        holder.setSetting(s);
        holder.mTitle.setText(s.getTitle());
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        holder.setSetting(null);
    }

    @Override
    public int getItemCount() {
        return mSettings.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView mTitle;
        private Setting mSetting;

        public ViewHolder(View itemView) {
            super(itemView);
            mTitle = (TextView) itemView.findViewById(R.id.setting_title);
        }

        public void setSetting(Setting setting) {
            mSetting = setting;
        }
    }
}
