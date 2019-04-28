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

package com.android.tv.settings.inputmethod;

import android.app.Fragment;

import com.android.tv.settings.BaseSettingsFragment;
import com.android.tv.settings.TvSettingsActivity;

/**
 * Activity for android.settings.INPUT_METHOD_SUBTYPE_SETTINGS
 */
public class InputMethodAndSubtypeEnablerActivity extends TvSettingsActivity {
    @Override
    protected Fragment createSettingsFragment() {
        return SettingsFragment.newInstance();
    }

    /**
     * UI chrome for TV settings
     */
    public static class SettingsFragment extends BaseSettingsFragment {

        /**
         * @return new instance of {@link SettingsFragment}
         */
        public static SettingsFragment newInstance() {
            return new SettingsFragment();
        }

        @Override
        public void onPreferenceStartInitialScreen() {
            final InputMethodAndSubtypeEnablerFragment fragment =
                    InputMethodAndSubtypeEnablerFragment.newInstance();
            startPreferenceFragment(fragment);
        }
    }
}
