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

package com.android.tv.settings.dialog.old;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.settings.widget.ScrollAdapter;
import com.android.tv.settings.widget.ScrollAdapterView;

/**
 * *************************
 *   DO NOT ADD LOGIC HERE!
 * *************************
 * This is a wrapper for {@link BaseScrollAdapterFragment}. Place your logic
 * in there and call the function from here
 */
public class ScrollAdapterFragment extends Fragment implements LiteFragment {

    private BaseScrollAdapterFragment mBase = new BaseScrollAdapterFragment(this);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return mBase.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mBase.onViewCreated(view, savedInstanceState);
    }

    public ScrollAdapterView getScrollAdapterView() {
        return mBase.getScrollAdapterView();
    }

    public ScrollAdapter getAdapter() {
        return mBase.getAdapter();
    }

    public void setAdapter(ScrollAdapter adapter) {
        mBase.setAdapter(adapter);
    }

    public void setSelection(int position) {
        mBase.setSelection(position);
    }

    public void setSelectionSmooth(int position) {
        mBase.setSelectionSmooth(position);
    }

    public int getSelectedItemPosition() {
        return mBase.getSelectedItemPosition();
    }

    protected void setBaseScrollAdapterFragment(BaseScrollAdapterFragment base) {
        mBase = base;
    }
}
