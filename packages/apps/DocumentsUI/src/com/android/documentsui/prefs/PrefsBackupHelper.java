/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.support.annotation.VisibleForTesting;

import java.util.Map;

/**
 * Class providing core logic for backup and restore of DocumentsUI preferences.
 */
final class PrefsBackupHelper {

    private SharedPreferences mDefaultPreferences;

    @VisibleForTesting
    PrefsBackupHelper(SharedPreferences overridePreferences) {
        mDefaultPreferences = overridePreferences;
    }

    PrefsBackupHelper(Context context) {
        mDefaultPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Loads all applicable preferences to supplied backup file.
     */
    void getBackupPreferences(SharedPreferences prefs) {
        Editor editor = prefs.edit();
        editor.clear();

        copyMatchingPreferences(mDefaultPreferences, editor);
        editor.apply();
    }

    /**
     * Restores all applicable preferences from the supplied preferences file.
     */
    void putBackupPreferences(SharedPreferences prefs) {
        Editor editor = mDefaultPreferences.edit();

        copyMatchingPreferences(prefs, editor);
        editor.apply();
    }

    private void copyMatchingPreferences(SharedPreferences source, Editor destination) {
        for (Map.Entry<String, ?> preference : source.getAll().entrySet()) {
            if (Preferences.shouldBackup(preference.getKey())) {
                setPreference(destination, preference);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @VisibleForTesting
    void setPreference(Editor target, final Map.Entry<String, ?> preference) {
        final String key = preference.getKey();
        final Object value = preference.getValue();
        // Only handle already know types.
        if (value instanceof Integer) {
            target.putInt(key, (Integer) value);
        } else if (value instanceof Boolean) {
            target.putBoolean(key, (Boolean) value);
        } else {
            throw new IllegalArgumentException("DocumentsUI backup: invalid preference "
                    + (value == null ? null : value.getClass()));
        }
    }
}
