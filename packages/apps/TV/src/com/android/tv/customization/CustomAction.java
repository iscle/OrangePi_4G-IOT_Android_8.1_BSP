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

package com.android.tv.customization;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

/**
 * Describes a custom option defined in customization package.
 * This will be added to main menu.
 */
public class CustomAction implements Comparable<CustomAction> {
    private static final int POSITION_THRESHOLD = 100;

    private final int mPositionPriority;
    private final String mTitle;
    private final Drawable mIconDrawable;
    private final Intent mIntent;

    public CustomAction(int positionPriority, String title, Drawable iconDrawable, Intent intent) {
        mPositionPriority = positionPriority;
        mTitle = title;
        mIconDrawable = iconDrawable;
        mIntent = intent;
    }

    /**
     * Returns if this option comes before the existing items.
     * Note that custom options can only be placed at the front or back.
     * (i.e. cannot be added in the middle of existing options.)
     * @return {@code true} if it goes to the beginning. {@code false} if it goes to the end.
     */
    public boolean isFront() {
        return mPositionPriority < POSITION_THRESHOLD;
    }

    @Override
    public int compareTo(@NonNull CustomAction another) {
        return mPositionPriority - another.mPositionPriority;
    }

    /**
     * Returns title.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Returns icon drawable.
     */
    public Drawable getIconDrawable() {
        return mIconDrawable;
    }

    /**
     * Returns intent to launch when this option is clicked.
     */
    public Intent getIntent() {
        return mIntent;
    }
}
