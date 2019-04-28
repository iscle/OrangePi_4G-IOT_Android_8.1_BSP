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


/**
 * A feature controlled by a GServices flag.
 */
public class GServiceFeature implements Feature {
    private static final String LIVECHANNELS_PREFIX = "livechannels:";
    private final String mKey;
    private final boolean mDefaultValue;

    public GServiceFeature(String key, boolean defaultValue) {
        mKey = LIVECHANNELS_PREFIX + key;
        mDefaultValue = defaultValue;
    }

    @Override
    public boolean isEnabled(Context context) {

        // GServices is not available outside of Google.
        return mDefaultValue;
    }

    @Override
    public String toString() {
        return "GService[hash=" + mKey.hashCode() + "]";
    }
}
