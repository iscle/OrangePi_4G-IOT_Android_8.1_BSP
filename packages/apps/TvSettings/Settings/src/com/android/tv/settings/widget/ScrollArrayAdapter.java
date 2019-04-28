/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.widget;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class ScrollArrayAdapter<T> extends ArrayAdapter<T> implements ScrollAdapter {

    private int mLayoutResource = -1;

    public ScrollArrayAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    public ScrollArrayAdapter(Context context, int resource, int textViewResourceId) {
        super(context, resource, textViewResourceId);
        mLayoutResource = resource;
    }

    public ScrollArrayAdapter(Context context, int textViewResourceId, T[] objects) {
        super(context, textViewResourceId, objects);
    }

    public ScrollArrayAdapter(Context context, int resource, int textViewResourceId,
            T[] objects) {
        super(context, resource, textViewResourceId, objects);
        mLayoutResource = resource;
    }

    public ScrollArrayAdapter(Context context, int textViewResourceId, List<T> objects) {
        super(context, textViewResourceId, objects);
    }

    public ScrollArrayAdapter(Context context, int resource, int textViewResourceId,
            List<T> objects) {
        super(context, resource, textViewResourceId, objects);
        mLayoutResource = resource;
    }

    @Override
    public View getScrapView(ViewGroup parent) {
        if (getCount() > 0) {
            return getView(0, null, parent);
        } else {
            if (mLayoutResource != -1) {
                LayoutInflater inflater = (LayoutInflater)
                        parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                return inflater.inflate(mLayoutResource, parent);
            } else {
                return new TextView(parent.getContext());
            }
        }
    }

    @Override
    public void viewRemoved(View view) {
    }

    @Override
    public ScrollAdapterBase getExpandAdapter() {
        return null;
    }
}
