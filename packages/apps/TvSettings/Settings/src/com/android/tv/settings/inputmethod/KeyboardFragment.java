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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Fragment for managing IMEs
 */
public class KeyboardFragment extends LeanbackPreferenceFragment {
    private static final String TAG = "KeyboardFragment";
    private static final String KEY_CURRENT_KEYBOARD = "currentKeyboard";
    private static final String KEY_MANAGE_KEYBOARDS = "manageKeyboards";

    private static final String KEY_KEYBOARD_SETTINGS_PREFIX = "keyboardSettings:";

    private InputMethodManager mInputMethodManager;

    /**
     * @return New fragment instance
     */
    public static KeyboardFragment newInstance() {
        return new KeyboardFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mInputMethodManager =
                (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context preferenceContext = getPreferenceManager().getContext();

        final PreferenceScreen screen =
                getPreferenceManager().createPreferenceScreen(preferenceContext);
        screen.setTitle(R.string.system_keyboard);
        setPreferenceScreen(screen);

        final ListPreference currentKeyboard = new ListPreference(preferenceContext);
        currentKeyboard.setPersistent(false);
        currentKeyboard.setTitle(R.string.title_current_keyboard);
        currentKeyboard.setDialogTitle(R.string.title_current_keyboard);
        currentKeyboard.setSummary("%s");
        currentKeyboard.setKey(KEY_CURRENT_KEYBOARD);
        currentKeyboard.setOnPreferenceChangeListener((preference, newValue) -> {
            setInputMethod((String) newValue);
            return true;
        });
        updateCurrentKeyboardPreference(currentKeyboard);
        screen.addPreference(currentKeyboard);

        final Preference manageKeyboards = new Preference(preferenceContext);
        manageKeyboards.setTitle(R.string.manage_keyboards);
        manageKeyboards.setKey(KEY_MANAGE_KEYBOARDS);
        manageKeyboards.setFragment(AvailableVirtualKeyboardFragment.class.getName());
        screen.addPreference(manageKeyboards);

        updatePrefs();
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePrefs();
        final Preference currentKeyboard = findPreference(KEY_CURRENT_KEYBOARD);
        if (currentKeyboard instanceof ListPreference) {
            updateCurrentKeyboardPreference((ListPreference) currentKeyboard);
        }
    }

    private void updateCurrentKeyboardPreference(ListPreference currentKeyboardPref) {
        final PackageManager packageManager = getActivity().getPackageManager();
        List<InputMethodInfo> enabledInputMethodInfos = getEnabledSystemInputMethodList();
        final List<CharSequence> entries = new ArrayList<>(enabledInputMethodInfos.size());
        final List<CharSequence> values = new ArrayList<>(enabledInputMethodInfos.size());

        int defaultIndex = 0;
        final String defaultId = Settings.Secure.getString(getActivity().getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);

        for (final InputMethodInfo info : enabledInputMethodInfos) {
            entries.add(info.loadLabel(packageManager));
            final String id = info.getId();
            values.add(id);
            if (TextUtils.equals(id, defaultId)) {
                defaultIndex = values.size() - 1;
            }
        }

        currentKeyboardPref.setEntries(entries.toArray(new CharSequence[entries.size()]));
        currentKeyboardPref.setEntryValues(values.toArray(new CharSequence[values.size()]));
        if (entries.size() > 0) {
            currentKeyboardPref.setValueIndex(defaultIndex);
        }
    }

    private void updatePrefs() {
        final Context preferenceContext = getPreferenceManager().getContext();
        final PackageManager packageManager = getActivity().getPackageManager();
        List<InputMethodInfo> enabledInputMethodInfos = getEnabledSystemInputMethodList();

        final PreferenceScreen screen = getPreferenceScreen();

        final Set<String> enabledInputMethodKeys = new ArraySet<>(enabledInputMethodInfos.size());
        // Add per-IME settings
        for (final InputMethodInfo info : enabledInputMethodInfos) {
            final Intent settingsIntent = getInputMethodSettingsIntent(info);
            if (settingsIntent == null) {
                continue;
            }
            final String key = KEY_KEYBOARD_SETTINGS_PREFIX + info.getId();

            Preference preference = findPreference(key);
            if (preference == null) {
                preference = new Preference(preferenceContext);
                screen.addPreference(preference);
            }
            preference.setTitle(info.loadLabel(packageManager));
            preference.setKey(key);
            preference.setIntent(settingsIntent);
            enabledInputMethodKeys.add(key);
        }

        for (int i = 0; i < screen.getPreferenceCount();) {
            final Preference preference = screen.getPreference(i);
            final String key = preference.getKey();
            if (!TextUtils.isEmpty(key)
                    && key.startsWith(KEY_KEYBOARD_SETTINGS_PREFIX)
                    && !enabledInputMethodKeys.contains(key)) {
                screen.removePreference(preference);
            } else {
                i++;
            }
        }
    }

    private void setInputMethod(String imid) {
        if (imid == null) {
            throw new IllegalArgumentException("Null ID");
        }

        int userId;
        try {
            userId = ActivityManager.getService().getCurrentUser().id;
            Settings.Secure.putStringForUser(getActivity().getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD, imid, userId);

            Intent intent = new Intent(Intent.ACTION_INPUT_METHOD_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra("input_method_id", imid);
            getActivity().sendBroadcastAsUser(intent, UserHandle.CURRENT);
        } catch (RemoteException e) {
            Log.d(TAG, "set default input method remote exception");
        }
    }

    private List<InputMethodInfo> getEnabledSystemInputMethodList() {
        List<InputMethodInfo> enabledInputMethodInfos =
                new ArrayList<>(mInputMethodManager.getEnabledInputMethodList());
        // Filter auxiliary keyboards out
        enabledInputMethodInfos.removeIf(InputMethodInfo::isAuxiliaryIme);
        return enabledInputMethodInfos;
    }

    private Intent getInputMethodSettingsIntent(InputMethodInfo imi) {
        final Intent intent;
        final String settingsActivity = imi.getSettingsActivity();
        if (!TextUtils.isEmpty(settingsActivity)) {
            intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName(imi.getPackageName(), settingsActivity);
        } else {
            intent = null;
        }
        return intent;
    }
}
