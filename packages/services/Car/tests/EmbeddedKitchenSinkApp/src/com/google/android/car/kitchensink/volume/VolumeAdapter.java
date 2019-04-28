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
package com.google.android.car.kitchensink.volume;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.car.kitchensink.R;
import com.google.android.car.kitchensink.volume.VolumeTestFragment.VolumeInfo;


public class VolumeAdapter extends ArrayAdapter<VolumeInfo> {

    private final Context mContext;
    private VolumeInfo[] mVolumeList;
    private final int mLayoutResourceId;
    private VolumeTestFragment mFragment;


    public VolumeAdapter(Context c, int layoutResourceId, VolumeInfo[] locations,
            VolumeTestFragment fragment) {
        super(c, layoutResourceId, locations);
        mFragment = fragment;
        mContext = c;
        this.mLayoutResourceId = layoutResourceId;
        this.mVolumeList = locations;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh = new ViewHolder();
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(mLayoutResourceId, parent, false);
            vh.id = (TextView) convertView.findViewById(R.id.stream_id);
            vh.maxVolume = (TextView) convertView.findViewById(R.id.volume_limit);
            vh.currentVolume = (TextView) convertView.findViewById(R.id.current_volume);
            vh.logicalVolume = (TextView) convertView.findViewById(R.id.logical_volume);
            vh.logicalMax = (TextView) convertView.findViewById(R.id.logical_max);
            vh.upButton = (Button) convertView.findViewById(R.id.volume_up);
            vh.downButton = (Button) convertView.findViewById(R.id.volume_down);
            vh.requestButton = (Button) convertView.findViewById(R.id.request);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }
        if (mVolumeList[position] != null) {
            vh.id.setText(mVolumeList[position].mId);
            vh.maxVolume.setText(String.valueOf(mVolumeList[position].mMax));
            vh.currentVolume.setText(String.valueOf(mVolumeList[position].mCurrent));
            vh.logicalVolume.setText(String.valueOf(mVolumeList[position].mLogicalCurrent));
            vh.logicalMax.setText(String.valueOf(mVolumeList[position].mLogicalMax));
            int color = mVolumeList[position].mHasFocus ? Color.GREEN : Color.GRAY;
            vh.requestButton.setBackgroundColor(color);
            if (position == 0) {
                vh.upButton.setVisibility(View.INVISIBLE);
                vh.downButton.setVisibility(View.INVISIBLE);
                vh.requestButton.setVisibility(View.INVISIBLE);
            } else {
                vh.upButton.setVisibility(View.VISIBLE);
                vh.downButton.setVisibility(View.VISIBLE);
                vh.requestButton.setVisibility(View.VISIBLE);
                vh.upButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mFragment.adjustVolumeByOne(mVolumeList[position].logicalStream, true);
                    }
                });

                vh.downButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mFragment.adjustVolumeByOne(mVolumeList[position].logicalStream, false);
                    }
                });

                vh.requestButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mFragment.requestFocus(mVolumeList[position].logicalStream);
                    }
                });
            }
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return mVolumeList.length;
    }


    public void refreshVolumes(VolumeInfo[] volumes) {
        mVolumeList = volumes;
        notifyDataSetChanged();
    }

    static class ViewHolder {
        TextView id;
        TextView maxVolume;
        TextView currentVolume;
        TextView logicalMax;
        TextView logicalVolume;
        Button upButton;
        Button downButton;
        Button requestButton;
    }
}
