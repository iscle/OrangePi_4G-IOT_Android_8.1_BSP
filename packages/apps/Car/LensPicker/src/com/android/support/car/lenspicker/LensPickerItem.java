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
package com.android.support.car.lenspicker;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/**
 * Object to hold all necessary information required to represent an application/activity
 * in the lens picker.
 */
public class LensPickerItem {
    private final Drawable mIcon;
    private final String mLabel;
    private final Intent mLaunchIntent;
    private final String mFacetId;

    public LensPickerItem(String label, Drawable icon, Intent launchIntent, String facetId) {
        mIcon = icon;
        mLabel = label;
        mLaunchIntent = launchIntent;
        mFacetId = facetId;
    }

    /**
     * Gets a {@link Drawable} icon to represent this {@link LensPickerItem}.
     */
    public Drawable getIcon() {
        return mIcon;
    }

    /**
     * Gets a label that describes this {@link LensPickerItem}.
     */
    public String getLabel() {
        return mLabel;
    }

    /**
     * Gets the {@link Intent} to be launched when this {@link LensPickerItem} is selected.
     */
    public Intent getLaunchIntent() {
        return mLaunchIntent;
    }

    /**
     * Gets the id that identifies which facet this {@link LensPickerItem} belongs to.
     */
    public String getFacetId(){
        return mFacetId;
    }
}
