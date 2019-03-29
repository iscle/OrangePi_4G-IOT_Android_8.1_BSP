/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.sample;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;

import java.lang.Override;

/**
 * A simple activity for using the SharedPreferences API.
 */
public class SampleDeviceActivity extends Activity {

    private SharedPreferences mPreferences;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // Get a reference to this context's shared preference.
        mPreferences = getPreferences(Context.MODE_PRIVATE);
    }

    /**
     * Saves the given key value pair to the shared preferences.
     *
     * @param key
     * @param value
     */
    public void savePreference(String key, String value) {
        // Get an editor to modify the preferences.
        Editor editor = mPreferences.edit();
        // Insert the key value pair.
        editor.putString(key, value);
        // Commit the changes - important.
        editor.commit();
    }

    /**
     * Looks up the given key in the shared preferences.
     *
     * @param key
     * @return
     */
    public String getPreference(String key) {
        return mPreferences.getString(key, null);
    }

    /**
     * Deletes all entries in the shared preferences.
     */
    public void clearPreferences() {
        // Get an editor to modify the preferences.
        Editor editor = mPreferences.edit();
        // Delete all entries.
        editor.clear();
        // Commit the changes - important.
        editor.commit();
    }

}
