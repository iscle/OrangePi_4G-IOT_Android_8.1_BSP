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

package com.android.tv.settings.system;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;
import android.support.annotation.NonNull;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class TtsEngineSettingsFragment extends LeanbackPreferenceFragment implements
        Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
    private static final String TAG = "TtsEngineSettings";
    private static final boolean DBG = false;

    /**
     * Key for the name of the TTS engine passed in to the engine
     * settings fragment {@link TtsEngineSettingsFragment}.
     */
    private static final String ARG_ENGINE_NAME = "engineName";

    /**
     * Key for the label of the TTS engine passed in to the engine
     * settings fragment. This is used as the title of the fragment
     * {@link TtsEngineSettingsFragment}.
     */
    private static final String ARG_ENGINE_LABEL = "engineLabel";

    /**
     * Key for the voice data data passed in to the engine settings
     * fragmetn {@link TtsEngineSettingsFragment}.
     */
    private static final String ARG_VOICES = "voices";


    private static final String KEY_ENGINE_LOCALE = "tts_default_lang";
    private static final String KEY_ENGINE_SETTINGS = "tts_engine_settings";
    private static final String KEY_INSTALL_DATA = "tts_install_data";

    private static final String STATE_KEY_LOCALE_ENTRIES = "locale_entries";
    private static final String STATE_KEY_LOCALE_ENTRY_VALUES= "locale_entry_values";
    private static final String STATE_KEY_LOCALE_VALUE = "locale_value";

    private static final int VOICE_DATA_INTEGRITY_CHECK = 1977;

    private TtsEngines mEnginesHelper;
    private ListPreference mLocalePreference;
    private Preference mEngineSettingsPreference;
    private Preference mInstallVoicesPreference;
    private Intent mVoiceDataDetails;

    private TextToSpeech mTts;

    private int mSelectedLocaleIndex = -1;

    private final TextToSpeech.OnInitListener mTtsInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if (status != TextToSpeech.SUCCESS) {
                getFragmentManager().popBackStack();
            } else {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mLocalePreference.setEnabled(true);
                    }
                });
            }
        }
    };

    private final BroadcastReceiver mLanguagesChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Installed or uninstalled some data packs
            if (TextToSpeech.Engine.ACTION_TTS_DATA_INSTALLED.equals(intent.getAction())) {
                checkTtsData();
            }
        }
    };

    public static void prepareArgs(@NonNull Bundle args, String engineName, String engineLabel,
            Intent voiceCheckData) {
        args.clear();

        args.putString(ARG_ENGINE_NAME, engineName);
        args.putString(ARG_ENGINE_LABEL, engineLabel);
        args.putParcelable(ARG_VOICES, voiceCheckData);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        addPreferencesFromResource(R.xml.tts_engine_settings);

        final PreferenceScreen screen = getPreferenceScreen();
        screen.setTitle(getEngineLabel());
        screen.setKey(getEngineName());

        mLocalePreference = (ListPreference) findPreference(KEY_ENGINE_LOCALE);
        mLocalePreference.setOnPreferenceChangeListener(this);
        mEngineSettingsPreference = findPreference(KEY_ENGINE_SETTINGS);
        mEngineSettingsPreference.setOnPreferenceClickListener(this);
        mInstallVoicesPreference = findPreference(KEY_INSTALL_DATA);
        mInstallVoicesPreference.setOnPreferenceClickListener(this);

        mEngineSettingsPreference.setTitle(getResources().getString(
                R.string.tts_engine_settings_title, getEngineLabel()));
        final Intent settingsIntent = mEnginesHelper.getSettingsIntent(getEngineName());
        mEngineSettingsPreference.setIntent(settingsIntent);
        if (settingsIntent == null) {
            mEngineSettingsPreference.setEnabled(false);
        }
        mInstallVoicesPreference.setEnabled(false);

        if (savedInstanceState == null) {
            mLocalePreference.setEnabled(false);
            mLocalePreference.setEntries(new CharSequence[0]);
            mLocalePreference.setEntryValues(new CharSequence[0]);
        } else {
            // Repopulate mLocalePreference with saved state. Will be updated later with
            // up-to-date values when checkTtsData() calls back with results.
            final CharSequence[] entries =
                    savedInstanceState.getCharSequenceArray(STATE_KEY_LOCALE_ENTRIES);
            final CharSequence[] entryValues =
                    savedInstanceState.getCharSequenceArray(STATE_KEY_LOCALE_ENTRY_VALUES);
            final CharSequence value =
                    savedInstanceState.getCharSequence(STATE_KEY_LOCALE_VALUE);

            mLocalePreference.setEntries(entries);
            mLocalePreference.setEntryValues(entryValues);
            mLocalePreference.setValue(value != null ? value.toString() : null);
            mLocalePreference.setEnabled(entries.length > 0);
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mEnginesHelper = new TtsEngines(getActivity());

        super.onCreate(savedInstanceState);

        mVoiceDataDetails = getArguments().getParcelable(ARG_VOICES);

        mTts = new TextToSpeech(getActivity().getApplicationContext(), mTtsInitListener,
                getEngineName());

        // Check if data packs changed
        checkTtsData();

        getActivity().registerReceiver(mLanguagesChangedReceiver,
                new IntentFilter(TextToSpeech.Engine.ACTION_TTS_DATA_INSTALLED));
    }

    @Override
    public void onDestroy() {
        getActivity().unregisterReceiver(mLanguagesChangedReceiver);
        mTts.shutdown();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the mLocalePreference values, so we can repopulate it with entries.
        outState.putCharSequenceArray(STATE_KEY_LOCALE_ENTRIES,
                mLocalePreference.getEntries());
        outState.putCharSequenceArray(STATE_KEY_LOCALE_ENTRY_VALUES,
                mLocalePreference.getEntryValues());
        outState.putCharSequence(STATE_KEY_LOCALE_VALUE,
                mLocalePreference.getValue());
    }

    private void checkTtsData() {
        Intent intent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        intent.setPackage(getEngineName());
        try {
            if (DBG) Log.d(TAG, "Updating engine: Checking voice data: " + intent.toUri(0));
            startActivityForResult(intent, VOICE_DATA_INTEGRITY_CHECK);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Failed to check TTS data, no activity found for " + intent + ")");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE_DATA_INTEGRITY_CHECK) {
            if (resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL) {
                updateVoiceDetails(data);
            } else {
                Log.e(TAG, "CheckVoiceData activity failed");
            }
        }
    }

    private void updateVoiceDetails(Intent data) {
        if (data == null){
            Log.e(TAG, "Engine failed voice data integrity check (null return)" +
                    mTts.getCurrentEngine());
            return;
        }
        mVoiceDataDetails = data;

        if (DBG) Log.d(TAG, "Parsing voice data details, data: " + mVoiceDataDetails.toUri(0));

        final ArrayList<String> available = mVoiceDataDetails.getStringArrayListExtra(
                TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
        final ArrayList<String> unavailable = mVoiceDataDetails.getStringArrayListExtra(
                TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES);

        if (unavailable != null && unavailable.size() > 0) {
            mInstallVoicesPreference.setEnabled(true);
        } else {
            mInstallVoicesPreference.setEnabled(false);
        }

        if (available == null){
            Log.e(TAG, "TTS data check failed (available == null).");
            mLocalePreference.setEnabled(false);
        } else {
            updateDefaultLocalePref(available);
        }
    }

    private void updateDefaultLocalePref(ArrayList<String> availableLangs) {
        if (availableLangs == null || availableLangs.size() == 0) {
            mLocalePreference.setEnabled(false);
            return;
        }
        Locale currentLocale = null;
        if (!mEnginesHelper.isLocaleSetToDefaultForEngine(getEngineName())) {
            currentLocale = mEnginesHelper.getLocalePrefForEngine(getEngineName());
        }

        ArrayList<Pair<String, Locale>> entryPairs =
                new ArrayList<>(availableLangs.size());
        for (int i = 0; i < availableLangs.size(); i++) {
            Locale locale = mEnginesHelper.parseLocaleString(availableLangs.get(i));
            if (locale != null){
                entryPairs.add(new Pair<>(locale.getDisplayName(), locale));
            }
        }

        // Sort it
        Collections.sort(entryPairs, new Comparator<Pair<String, Locale>>() {
            @Override
            public int compare(Pair<String, Locale> lhs, Pair<String, Locale> rhs) {
                return lhs.first.compareToIgnoreCase(rhs.first);
            }
        });

        // Get two arrays out of one of pairs
        mSelectedLocaleIndex = 0; // Will point to the R.string.tts_lang_use_system value
        CharSequence[] entries = new CharSequence[availableLangs.size()+1];
        CharSequence[] entryValues = new CharSequence[availableLangs.size()+1];

        entries[0] = getString(R.string.tts_lang_use_system);
        entryValues[0] = "";

        int i = 1;
        for (Pair<String, Locale> entry : entryPairs) {
            if (entry.second.equals(currentLocale)) {
                mSelectedLocaleIndex = i;
            }
            entries[i] = entry.first;
            entryValues[i++] = entry.second.toString();
        }

        mLocalePreference.setEntries(entries);
        mLocalePreference.setEntryValues(entryValues);
        mLocalePreference.setEnabled(true);
        setLocalePreference(mSelectedLocaleIndex);
    }

    /** Set entry from entry table in mLocalePreference */
    private void setLocalePreference(int index) {
        if (index < 0) {
            mLocalePreference.setValue("");
            mLocalePreference.setSummary(R.string.tts_lang_not_selected);
        } else {
            mLocalePreference.setValueIndex(index);
            mLocalePreference.setSummary(mLocalePreference.getEntries()[index]);
        }
    }

    /**
     * Ask the current default engine to launch the matching INSTALL_TTS_DATA activity
     * so the required TTS files are properly installed.
     */
    private void installVoiceData() {
        if (TextUtils.isEmpty(getEngineName())) return;
        Intent intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        intent.setPackage(getEngineName());
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Failed to install TTS data, no activity found for " + intent + ")");
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mInstallVoicesPreference) {
            installVoiceData();
            return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mLocalePreference) {
            String localeString = (String) newValue;
            updateLanguageTo((!TextUtils.isEmpty(localeString) ?
                    mEnginesHelper.parseLocaleString(localeString) : null));
            return true;
        }
        return false;
    }

    private void updateLanguageTo(Locale locale) {
        int selectedLocaleIndex = -1;
        String localeString = (locale != null) ? locale.toString() : "";
        for (int i=0; i < mLocalePreference.getEntryValues().length; i++) {
            if (localeString.equalsIgnoreCase(mLocalePreference.getEntryValues()[i].toString())) {
                selectedLocaleIndex = i;
                break;
            }
        }

        if (selectedLocaleIndex == -1) {
            Log.w(TAG, "updateLanguageTo called with unknown locale argument");
            return;
        }
        mLocalePreference.setSummary(mLocalePreference.getEntries()[selectedLocaleIndex]);
        mSelectedLocaleIndex = selectedLocaleIndex;

        mEnginesHelper.updateLocalePrefForEngine(getEngineName(), locale);

        if (getEngineName().equals(mTts.getCurrentEngine())) {
            // Null locale means "use system default"
            mTts.setLanguage((locale != null) ? locale : Locale.getDefault());
        }
    }

    private String getEngineName() {
        return getArguments().getString(ARG_ENGINE_NAME);
    }

    private String getEngineLabel() {
        return getArguments().getString(ARG_ENGINE_LABEL);
    }
}
