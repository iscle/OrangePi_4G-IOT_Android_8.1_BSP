/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.common;

import android.content.res.Resources;

import com.android.managedprovisioning.R;

import java.util.List;

/**
 * Concatenates {@link String}s in an i18n safe way.
 * <p>
 * Based on the implementation from <a href="https://android.googlesource.com/platform/packages/apps/Settings/+/2d566e7/src/com/android/settings/applications/AppPermissionSettings.java#136">com.android.settings.applications.AppPermissionSettings</a>
 */
public class StringConcatenator {
    private final Resources mResources;

    public StringConcatenator(Resources resources) {
        mResources = resources;
    }

    public String join(List<String> items) {
        if (items == null) {
            return null;
        }

        if (items.isEmpty()) {
            return "";
        }

        final int count = items.size();

        if (count == 1) {
            return items.get(0);
        }

        if (count == 2) {
            return mResources.getString(R.string.join_two_items, items.get(0), items.get(1));
        }

        String result = items.get(count - 2);
        for (int i = count - 3; i >= 0; i--) {
            result = mResources.getString( // not the fastest, but good enough
                    i == 0 ? R.string.join_many_items_first : R.string.join_many_items_middle,
                    items.get(i), result);
        }
        result = mResources.getString(R.string.join_many_items_last, result, items.get(count - 1));
        return result;
    }
}