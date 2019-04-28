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

import android.app.backup.BackupManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

import com.android.documentsui.base.ApplicationScope;

import java.util.function.Consumer;

/**
 * A class that monitors changes to the default shared preferences file. If a preference which
 * should be backed up changed, schedule a backup.
 *
 * Also, notifies a callback when such changes are noticed. This is the key mechanism by which
 * we learn about preference changes in other instances of the app.
 */
public final class PreferencesMonitor {

    private final String mPackageName;
    private final SharedPreferences mPrefs;
    private final OnSharedPreferenceChangeListener mListener = this::onSharedPreferenceChanged;
    private final Consumer<String> mChangeCallback;

    public PreferencesMonitor(
            @ApplicationScope String packageName,
            SharedPreferences prefs,
            Consumer<String> listener) {

        mPackageName = packageName;
        mPrefs = prefs;
        mChangeCallback = listener;
    }

    public void start() {
        mPrefs.registerOnSharedPreferenceChangeListener(mListener);
    }

    public void stop() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(mListener);
    }

    // visible for use as a lambda, otherwise treat as a private.
    void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (Preferences.shouldBackup(key)) {
            mChangeCallback.accept(key);
            BackupManager.dataChanged(mPackageName);
        }
    }
}
