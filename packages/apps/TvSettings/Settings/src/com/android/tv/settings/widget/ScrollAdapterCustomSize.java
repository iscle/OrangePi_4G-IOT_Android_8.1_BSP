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

/**
 * The adapter can optionally implement ScrollAdapterCustomSize in addition to ScrollAdapter.
 * This class is used as same purpose of attribute "selectedSize" of ScrollAdapterView but
 * can handle complicated size (e.g. the size of focused item is not same)
 */
public interface ScrollAdapterCustomSize extends ScrollAdapter {

    /**
     * return in pixels space when an item is in selected state.
     * For example,  the app may set a focused item to take 120 pixels comparing to
     * 100pixels of unfocused one.  ScrollAdapterView will assign more or less
     * space to the focused item and animate the growing/shrinking.<p>
     * NOTE: {@link ScrollAdapterView} does not actually scale the View or change
     * it's width or height,  the view itself is responsible doing the scale in
     * {@link ScrollAdapterTransform} or {@link ScrollAdapterView.OnScrollListener}
     */
    int getSelectItemSize(int adapterIndex, View view);

}
