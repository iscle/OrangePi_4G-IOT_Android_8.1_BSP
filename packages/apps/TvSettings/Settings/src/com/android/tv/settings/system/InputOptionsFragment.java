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
 * limitations under the License
 */

package com.android.tv.settings.system;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;

import java.util.Map;
import java.util.Set;

public class InputOptionsFragment extends LeanbackPreferenceFragment implements
        InputCustomNameFragment.Callback {

    private static final String KEY_SHOW_INPUT = "show_input";
    private static final String KEY_NAMES = "names";
    private static final String KEY_NAME_DEFAULT = "name_default";
    private static final String KEY_NAME_CUSTOM = "name_custom";

    private static final String ARG_INPUT = "input";

    private TwoStatePreference mShowPref;
    private PreferenceGroup mNamesGroup;
    private TwoStatePreference mNameDefaultPref;
    private TwoStatePreference mNameCustomPref;

    private Map<String, String> mCustomLabels;
    private Set<String> mHiddenIds;

    private TvInputInfo mInputInfo;

    public static void prepareArgs(@NonNull Bundle args, TvInputInfo inputInfo) {
        args.putParcelable(ARG_INPUT, inputInfo);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mInputInfo = getArguments().getParcelable(ARG_INPUT);

        super.onCreate(savedInstanceState);

        final Context context = getContext();
        mCustomLabels =
                TvInputInfo.TvInputSettings.getCustomLabels(context, UserHandle.USER_SYSTEM);
        mHiddenIds =
                TvInputInfo.TvInputSettings.getHiddenTvInputIds(context, UserHandle.USER_SYSTEM);

    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.input_options, null);
        getPreferenceScreen().setTitle(mInputInfo.loadLabel(getContext()));

        mShowPref = (TwoStatePreference) findPreference(KEY_SHOW_INPUT);

        mNamesGroup = (PreferenceGroup) findPreference(KEY_NAMES);

        mNameDefaultPref = (TwoStatePreference) findPreference(KEY_NAME_DEFAULT);
        mNameCustomPref = (TwoStatePreference) findPreference(KEY_NAME_CUSTOM);

    }

    private void refresh() {

        mShowPref.setChecked(!mHiddenIds.contains(mInputInfo.getId()));

        final CharSequence defaultLabel = mInputInfo.loadLabel(getContext());
        final CharSequence customLabel = mCustomLabels.get(mInputInfo.getId());

        boolean nameMatched = false;
        for (int i = 0; i < mNamesGroup.getPreferenceCount(); i++) {
            final TwoStatePreference namePref = (TwoStatePreference) mNamesGroup.getPreference(i);

            if (TextUtils.equals(namePref.getKey(), KEY_NAME_DEFAULT)
                    || TextUtils.equals(namePref.getKey(), KEY_NAME_CUSTOM)) {
                continue;
            }
            final boolean nameMatch = TextUtils.equals(namePref.getTitle(), customLabel);
            namePref.setChecked(nameMatch);
            nameMatched |= nameMatch;
        }

        mNameDefaultPref.setTitle(defaultLabel);

        final boolean nameIsDefault = TextUtils.isEmpty(customLabel);
        mNameDefaultPref.setChecked(nameIsDefault);

        InputCustomNameFragment.prepareArgs(mNameCustomPref.getExtras(), defaultLabel,
                nameIsDefault ? defaultLabel : customLabel);

        if (!nameIsDefault && !nameMatched) {
            mNameCustomPref.setChecked(true);
            mNameCustomPref.setSummary(customLabel);
        } else {
            mNameCustomPref.setChecked(false);
            mNameCustomPref.setSummary(null);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (key == null) {
            return super.onPreferenceTreeClick(preference);
        }
        if (preference instanceof RadioPreference) {
            final RadioPreference radioPreference = (RadioPreference) preference;
            radioPreference.setChecked(true);
            radioPreference.clearOtherRadioPreferences(mNamesGroup);

            if (TextUtils.equals(key, KEY_NAME_CUSTOM)) {
                return super.onPreferenceTreeClick(preference);
            } else if (TextUtils.equals(key, KEY_NAME_DEFAULT)) {
                setInputName(null);
                return true;
            } else {
                setInputName(preference.getTitle());
            }
        }
        switch (key) {
            case KEY_SHOW_INPUT:
                setInputVisible(((TwoStatePreference) preference).isChecked());
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void setInputName(CharSequence name) {
        if (TextUtils.isEmpty(name)) {
            mCustomLabels.remove(mInputInfo.getId());
        } else {
            mCustomLabels.put(mInputInfo.getId(), name.toString());
        }

        TvInputInfo.TvInputSettings
                .putCustomLabels(getContext(), mCustomLabels, UserHandle.USER_SYSTEM);
    }

    private void setInputVisible(boolean visible) {
        final boolean wasVisible = !mHiddenIds.contains(mInputInfo.getId());

        if (wasVisible == visible) {
            return;
        }

        if (visible) {
            mHiddenIds.remove(mInputInfo.getId());
        } else {
            mHiddenIds.add(mInputInfo.getId());
        }

        TvInputInfo.TvInputSettings
                .putHiddenTvInputs(getContext(), mHiddenIds, UserHandle.USER_SYSTEM);
    }

    @Override
    public void onSetCustomName(CharSequence name) {
        setInputName(name);
    }
}
