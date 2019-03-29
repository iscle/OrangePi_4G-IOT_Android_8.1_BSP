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

package android.widget.cts;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

class DummyAdapter extends BaseAdapter {
    int mCount;
    // these are views w/ special types and are returned as requested
    View[] mSpecialViews;

    public DummyAdapter(int count, View... specialViews) {
        mCount = count;
        mSpecialViews = specialViews;
    }

    @Override
    public int getCount() {
        return mCount;
    }

    @Override
    public Object getItem(int position) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return mSpecialViews.length + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return position < mSpecialViews.length ? position : mSpecialViews.length;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position < mSpecialViews.length) {
            return mSpecialViews[position];
        }
        if (convertView == null) {
            convertView = new View(parent.getContext());
            convertView.setMinimumHeight(200);
        }
        return convertView;
    }
}