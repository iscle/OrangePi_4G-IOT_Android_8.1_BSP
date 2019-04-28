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
package com.android.car.systemupdater;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.File;


public class FileAdapter extends ArrayAdapter<File> {
    private final Context mContext;
    private File[] mLocations;
    private final int mLayoutResourceId;

    public FileAdapter(Context c, int layoutResourceId, File[] locations) {
        super(c, layoutResourceId, locations);
        mContext = c;
        this.mLayoutResourceId = layoutResourceId;
        this.mLocations = locations;
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder vh = new ViewHolder();
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(mLayoutResourceId, parent, false);
            vh.textView = (TextView) convertView.findViewById(R.id.text);
            vh.descriptionView = (TextView) convertView.findViewById(R.id.description);
            convertView.setTag(vh);
        } else {
            vh = (ViewHolder) convertView.getTag();
        }
        if (mLocations[position] != null) {
            vh.textView.setText(mLocations[position].getAbsolutePath());
            if (mLocations[position].getAbsolutePath().endsWith(".zip")
                    || mLocations[position].isDirectory()) {
                vh.textView.setTextColor(Color.GREEN);
            } else {
                vh.textView.setTextColor(Color.GRAY);
            }
        }
        return convertView;
    }

    @Override
    public int getCount() {
        return mLocations.length;
    }

    public void setLocations(File[] locations) {
        mLocations = locations;
        notifyDataSetChanged();
    }

    static class ViewHolder {
        TextView textView;
        TextView descriptionView;
    }
}
