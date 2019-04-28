/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.tv.quicksettings;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.widget.OnChildSelectedListener;
import android.support.v17.leanback.widget.VerticalGridView;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;

public class SettingsDialog extends Activity {

    private static final int PRESET_SETTING_INDEX = 0;
    private static final int INTEGER_SETTING_START_INDEX = 1;

    private static final String TAG = "SettingsDialog";
    private static final boolean DEBUG = true;

    static final String EXTRA_START_POS = "com.android.tv.quicksettings.START_POS";
    private static final int SETTING_INT_VALUE_MIN = 0;
    private static final int SETTING_INT_VALUE_STEP = 10;

    private VerticalGridView mPanelList;
    private SeekBar mSeekBar;
    private TextView mSettingValue;
    private DialogAdapter mAdapter;
    private final SettingSelectedListener mSettingSelectedListener = new SettingSelectedListener();
    private Setting mFocusedSetting;
    private ArrayList<Setting> mSettings;
    private SharedPreferences mSharedPreferences;

    private PresetSettingsListener mPresetSettingsListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPresetSettingsListener = new PresetSettingsListener(this);

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER;
        lp.y = getResources().getDimensionPixelSize(R.dimen.panel_y_offset);
        getWindow().setAttributes(lp);

        setContentView(R.layout.main_quicksettings);

        Intent intent = getIntent();
        int startPos = intent.getIntExtra(EXTRA_START_POS, -1);
        if (DEBUG)
            Log.d(TAG, "startPos=" + startPos);

        mPanelList = (VerticalGridView) findViewById(R.id.main_panel_list);
        mPanelList.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        mPanelList.setOnChildSelectedListener(mSettingSelectedListener);

        mSettings = getSettings();

        final int pivotX;
        if (getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            pivotX = getResources().getDimensionPixelSize(R.dimen.slider_horizontal_padding);
        } else {
            pivotX = getResources().getDimensionPixelSize(
                    R.dimen.main_panel_text_width_minus_padding);
        }
        final int pivotY = getResources().getDimensionPixelSize(R.dimen.main_panel_text_height_half);

        mAdapter = new DialogAdapter(mSettings, pivotX, pivotY, new SettingClickedListener() {
            @Override
            public void onSettingClicked(Setting s) {
                if (s.getType() != Setting.TYPE_UNKNOWN) {
                    finish();
                } else {
                    new AlertDialog.Builder(SettingsDialog.this).setPositiveButton(
                            android.R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    // User clicked OK button
                                    String[] presetSettingValues = getResources().getStringArray(
                                            R.array.setting_preset_values);
                                    mSettings.get(PRESET_SETTING_INDEX).setValue(
                                            presetSettingValues[getResources().getInteger(
                                                    R.integer.standard_setting_index)]);
                                }
                            }).setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    // User cancelled the dialog - do nothing
                                }
                            }).setTitle(R.string.reset_dialog_message).create().show();
                }
            }
        });

        mPanelList.setAdapter(mAdapter);
        mPanelList.setSelectedPosition(startPos + 1);
        mPanelList.requestFocus();

        mSeekBar = (SeekBar) findViewById(R.id.main_slider);

        mSettingValue = (TextView) findViewById(R.id.setting_value);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSharedPreferences.registerOnSharedPreferenceChangeListener(mPresetSettingsListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(mPresetSettingsListener);
    }

    private ArrayList<Setting> getSettings() {
        ArrayList<Setting> settings = new ArrayList<>();

        settings.add(new Setting(mSharedPreferences, "preset",
                getString(R.string.setting_preset_name)));

        String[] settingNames = getResources().getStringArray(R.array.setting_names);
        String[] settingKeys = getResources().getStringArray(R.array.setting_keys);
        int[] maxSettingValues = getResources().getIntArray(R.array.setting_max_values);
        for (int i = 0; i < settingNames.length; i++) {
            settings.add(
                    new Setting(mSharedPreferences, settingKeys[i], settingNames[i],
                            maxSettingValues[i]));
        }
        settings.add(new Setting(getString(R.string.setting_reset_defaults_name)));

        return settings;
    }

    private class SettingSelectedListener implements OnChildSelectedListener {
        @Override
        public void onChildSelected(ViewGroup parent, View view, int position, long id) {
            mFocusedSetting = mSettings.get(position);
            switch (mFocusedSetting.getType()) {
                case Setting.TYPE_STRING:
                    mSettingValue.setVisibility(View.VISIBLE);
                    mSettingValue.setText(mFocusedSetting.getStringValue());
                    mSeekBar.setVisibility(View.GONE);
                    break;
                case Setting.TYPE_INT:
                    mSettingValue.setVisibility(View.VISIBLE);
                    mSettingValue.setText(Integer.toString(mFocusedSetting.getIntValue()));
                    mSeekBar.setMax(mFocusedSetting.getMaxValue());
                    mSeekBar.setProgress(mFocusedSetting.getIntValue());
                    mSeekBar.setVisibility(View.VISIBLE);
                    break;
                default:
                    mSettingValue.setVisibility(View.GONE);
                    mSeekBar.setVisibility(View.GONE);
                    break;
            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        if (mFocusedSetting == null) {
            return super.onKeyUp(keyCode, event);
        }
        switch (mFocusedSetting.getType()) {
            case Setting.TYPE_INT:
                return integerSettingHandleKeyCode(keyCode, event);
            case Setting.TYPE_STRING:
                return stringSettingHandleKeyCode(keyCode, event);
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private boolean integerSettingHandleKeyCode(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                setFocusedSettingToValue(Math.min(
                        mFocusedSetting.getIntValue() + SETTING_INT_VALUE_STEP,
                        mFocusedSetting.getMaxValue()));
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                setFocusedSettingToValue(Math.max(
                        mFocusedSetting.getIntValue() - SETTING_INT_VALUE_STEP,
                        SETTING_INT_VALUE_MIN));
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    private void setFocusedSettingToValue(int value) {
        mFocusedSetting.setValue(value);
        mSeekBar.setProgress(mFocusedSetting.getIntValue());
        mSettingValue.setText(Integer.toString(mFocusedSetting.getIntValue()));
        String[] presetSettingChoices = getResources().getStringArray(
                R.array.setting_preset_values);
        mSettings.get(PRESET_SETTING_INDEX).setValue(
                presetSettingChoices[getResources().getInteger(R.integer.custom_setting_index)]);
    }

    private boolean stringSettingHandleKeyCode(int keyCode, KeyEvent event) {
        if (!mFocusedSetting.getTitle().equals(getString(R.string.setting_preset_name))) {
            return super.onKeyUp(keyCode, event);
        }

        String[] presetSettingChoices = getResources().getStringArray(
                R.array.setting_preset_choices);
        String[] presetSettingValues = getResources().getStringArray(R.array.setting_preset_values);

        int currentIndex = Arrays.asList(presetSettingValues).indexOf(
                mFocusedSetting.getStringValue());
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                currentIndex++;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                currentIndex--;
                break;
            default:
                return super.onKeyUp(keyCode, event);
        }
        int newIndex = (currentIndex + presetSettingValues.length) % presetSettingValues.length;
        mFocusedSetting.setValue(presetSettingValues[newIndex]);
        mSettingValue.setText(presetSettingChoices[newIndex]);
        int[] newSettingValues = null;
        if (newIndex == getResources().getInteger(R.integer.standard_setting_index)) {
            newSettingValues = getResources().getIntArray(R.array.standard_setting_values);
        } else if (newIndex == getResources().getInteger(R.integer.cinema_setting_index)) {
            newSettingValues = getResources().getIntArray(R.array.cinema_setting_values);
        } else if (newIndex == getResources().getInteger(R.integer.vivid_setting_index)) {
            newSettingValues = getResources().getIntArray(R.array.vivid_setting_values);
        } else if (newIndex == getResources().getInteger(R.integer.game_setting_index)) {
            newSettingValues = getResources().getIntArray(R.array.game_setting_values);
        }
        if (newSettingValues != null) {
            for (int i = 0; i < newSettingValues.length; i++) {
                mSettings.get(i + INTEGER_SETTING_START_INDEX).setValue(
                        newSettingValues[i]);
            }
        }
        return true;
    }
}
