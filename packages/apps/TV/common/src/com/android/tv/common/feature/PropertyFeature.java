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
 * limitations under the License
 */

package com.android.tv.common.feature;

import android.content.Context;

import com.android.tv.common.BooleanSystemProperty;

/**
 * Feature controlled by system property.
 *
 * <p>See {@link BooleanSystemProperty} for instructions on how to set using adb.
 */
public final class PropertyFeature implements Feature {
    private final BooleanSystemProperty mProperty;

    /**
     * Create System Property backed feature.
     *
     * @param key the system property key.  Length must be <= 31 characters.
     * @param defaultValue the value to return if the property is undefined or empty.
     */
    public PropertyFeature(String key, boolean defaultValue) {
        if (key.length() > 31) {
            // Since Features are initialized at startup and the keys are static go ahead and kill
            // the application.
            throw new IllegalArgumentException(
                    "Property keys have a max length of 31 characters but '" + key + "' is " + key
                            .length() + " characters.");
        }
        mProperty = new BooleanSystemProperty(key, defaultValue);
    }

    @Override
    public boolean isEnabled(Context context) {
        return mProperty.getValue();
    }

    @Override
    public String toString() {
        return mProperty.toString();
    }
}
