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
package com.android.documentsui.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.android.documentsui.R;

/**
 * Provides an interface (and runtime implementation) for preferences that are
 * scoped (presumably to an activity). This eliminates the need to pass
 * scoping values into {@link LocalPreferences}, as well as eliminates
 * the static-coupling to {@link LocalPreferences} increasing testability.
 */
public interface ScopedPreferences {

    static final String INCLUDE_DEVICE_ROOT = "includeDeviceRoot";
    static final String ENABLE_ARCHIVE_CREATION = "enableArchiveCreation-";

    boolean getShowDeviceRoot();
    void setShowDeviceRoot(boolean display);

    /**
     * @param scope An arbitrary string representitive of the scope
     *        for prefs that are set using this object.
     */
    public static ScopedPreferences create(Context context, String scope) {
        return new RuntimeScopedPreferences(context,
                PreferenceManager.getDefaultSharedPreferences(context), scope);
    }

    static final class RuntimeScopedPreferences implements ScopedPreferences {

        private final SharedPreferences mSharedPrefs;
        private final String mScope;
        private final boolean mDefaultShowDeviceRoot;

        private RuntimeScopedPreferences(Context context, SharedPreferences sharedPrefs,
                String scope)  {
            assert(!TextUtils.isEmpty(scope));

            mSharedPrefs = sharedPrefs;
            mScope = scope;
            mDefaultShowDeviceRoot = context.getResources()
                    .getBoolean(R.bool.config_default_show_device_root);
        }

        @Override
        public boolean getShowDeviceRoot() {
            return mSharedPrefs.getBoolean(INCLUDE_DEVICE_ROOT, mDefaultShowDeviceRoot);
        }

        @Override
        public void setShowDeviceRoot(boolean display) {
            mSharedPrefs.edit().putBoolean(INCLUDE_DEVICE_ROOT, display).apply();
        }
    }

    static boolean shouldBackup(String s) {
        return INCLUDE_DEVICE_ROOT.equals(s);
    }
}
