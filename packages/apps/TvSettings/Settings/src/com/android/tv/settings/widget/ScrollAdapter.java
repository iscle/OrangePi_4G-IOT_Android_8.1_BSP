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

import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;

/**
 * The adapter for ScrollAdapterView which supports expanding.
 */
public interface ScrollAdapter extends ScrollAdapterBase {

    /**
     * Optional method to be implemented by {@link ScrollAdapter}. Returns the
     * {@link ScrollAdapterBase} that {@link ScrollAdapterView} will invoke
     * {@link Adapter#getView(int,View,ViewGroup)} to create an expanded view. Returns null if the
     * adapter does not want to support expanding.
     */
    public ScrollAdapterBase getExpandAdapter();

}
