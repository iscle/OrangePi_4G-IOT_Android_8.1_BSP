/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.overview;

import android.content.Context;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.android.car.apps.common.FabDrawable;

/**
 * A FAB button with an affordance for setting the accent color.
 */
public class OverviewFabButton extends ImageView {
    private final FabDrawable mFabDrawable;

    public OverviewFabButton(Context context) {
        super(context);
    }

    public OverviewFabButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public OverviewFabButton(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
    }

    public OverviewFabButton(Context context, AttributeSet attrs, int defStyleAttrs,
            int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
    }

    {
        Context context = getContext();

        mFabDrawable = new FabDrawable(context);
        setBackground(mFabDrawable);
    }

    /**
     * Sets the color that the FAB button will be.
     */
    public void setAccentColor(@ColorInt int color) {
        mFabDrawable.setFabAndStrokeColor(color);
    }
}
