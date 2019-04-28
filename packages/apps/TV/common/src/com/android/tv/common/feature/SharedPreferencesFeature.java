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
import android.content.SharedPreferences;
import android.util.Log;
import com.android.tv.common.SharedPreferencesUtils;

/**
 * Feature controlled by shared preferences.
 */
public final class SharedPreferencesFeature implements Feature {
    private static final String TAG = "SharedPrefFeature";
    private static final boolean DEBUG = false;

    private final String mKey;
    private boolean mEnabled;
    private final boolean mDefaultValue;
    private SharedPreferences mSharedPreferences;
    private final Feature mBaseFeature;

    /**
     * Create SharedPreferences controlled feature.
     *
     * @param key the SharedPreferences key.
     * @param defaultValue the value to return if the property is undefined or empty.
     * @param baseFeature if {@code baseFeature} is turned off, this feature is always disabled.
     */
    public SharedPreferencesFeature(String key, boolean defaultValue, Feature baseFeature) {
        mKey = key;
        mDefaultValue = defaultValue;
        mBaseFeature = baseFeature;
    }

    @Override
    public boolean isEnabled(Context context) {
        if (!mBaseFeature.isEnabled(context)) {
            return false;
        }
        if (mSharedPreferences == null) {
            mSharedPreferences = context.getSharedPreferences(
                    SharedPreferencesUtils.SHARED_PREF_FEATURES, Context.MODE_PRIVATE);
            mEnabled = mSharedPreferences.getBoolean(mKey, mDefaultValue);
        }
        if (DEBUG) Log.d(TAG, mKey + " is " + mEnabled);
        return mEnabled;
    }

    @Override
    public String toString() {
        return "SharedPreferencesFeature:key=" + mKey + ",value=" + mEnabled;
    }

    public void setEnabled(Context context, boolean enable) {
        if (DEBUG) Log.d(TAG, mKey + " is set to " + enable);
        if (mSharedPreferences == null) {
            mSharedPreferences = context.getSharedPreferences(
                    SharedPreferencesUtils.SHARED_PREF_FEATURES, Context.MODE_PRIVATE);
            mEnabled = enable;
            mSharedPreferences.edit().putBoolean(mKey, enable).apply();
        } else if (mEnabled != enable) {
            mEnabled = enable;
            mSharedPreferences.edit().putBoolean(mKey, enable).apply();
        }

    }
}
