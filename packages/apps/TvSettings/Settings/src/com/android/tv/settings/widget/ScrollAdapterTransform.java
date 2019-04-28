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
 * performs transform on a view based on distance from center
 */
public interface ScrollAdapterTransform {

    /**
     * performs transform on a view based on distance pixels from center
     *
     * @param view view that to perform transform
     * @param distanceFromCenter distance in pixels from center in the main orientation, negative if
     *        lower than the center, 0 if at center, positive if higher than center
     * @param distanceFromCenter2ndAxis distance in pixels from center in the 2nd orientation for a
     *        grid view; in case of a single row/column,  it will be 0
     */
    public void transform(View view, int distanceFromCenter, int distanceFromCenter2ndAxis);

}
