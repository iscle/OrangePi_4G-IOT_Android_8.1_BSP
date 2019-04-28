/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.storagemanager.utils;

import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.ArrayMap;

/**
 * The PreferenceListCache is a cache of preferences sourced from a {@link PreferenceGroup} which
 * allows for the re-use of the same preference object when re-creating a list of preferences.
 */
public class PreferenceListCache {
    private PreferenceGroup mGroup;
    private ArrayMap<String, Preference> mCache;

    /**
     * Constructs a PreferenceListCache using the preferences within the given PreferenceGroup.
     * @param group The preference group to source preferences from.
     */
    public PreferenceListCache(PreferenceGroup group) {
        mGroup = group;
        mCache = new ArrayMap<>();
        final int N = group.getPreferenceCount();
        for (int i = 0; i < N; i++) {
            Preference p = group.getPreference(i);
            String key = p.getKey();
            if (TextUtils.isEmpty(key) || mCache.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Invalid key encountered in preference group " + group.getKey());
            }
            mCache.put(p.getKey(), p);
        }
    }

    /**
     * Removes a cached preferenced from the cache, if it exists.
     * @param key The key of the preference.
     * @return The preference, if it exists in the cache, or null.
     */
    public Preference getCachedPreference(String key) {
        return mCache.remove(key);
    }

    /**
     * Removes the un-reused preferences from the original PreferenceGroup.
     */
    public void removeCachedPrefs() {
        for (Preference p : mCache.values()) {
            mGroup.removePreference(p);
        }
    }
}
