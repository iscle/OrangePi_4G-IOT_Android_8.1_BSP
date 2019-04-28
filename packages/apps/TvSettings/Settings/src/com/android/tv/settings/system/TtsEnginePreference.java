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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.speech.tts.TextToSpeech;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.util.Log;
import android.widget.Checkable;
import android.widget.RadioButton;

import com.android.tv.settings.R;

public class TtsEnginePreference extends Preference {

    private static final String TAG = "TtsEnginePreference";

    /**
     * The engine information for the engine this preference represents.
     * Contains it's name, label etc. which are used for display.
     */
    private final TextToSpeech.EngineInfo mEngineInfo;

    /**
     * The shared radio button state, which button is checked etc.
     */
    private final RadioButtonGroupState mSharedState;

    private RadioButton mRadioButton;

    public TtsEnginePreference(Context context, TextToSpeech.EngineInfo info,
            RadioButtonGroupState state) {
        super(context);
        setWidgetLayoutResource(R.layout.radio_preference_widget);

        mSharedState = state;
        mEngineInfo = info;

        setKey(mEngineInfo.name);
        setTitle(mEngineInfo.label);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder viewHolder) {
        super.onBindViewHolder(viewHolder);

        final RadioButton rb = (RadioButton) viewHolder.findViewById(android.R.id.checkbox);

        boolean isChecked = getKey().equals(mSharedState.getCurrentKey());
        if (isChecked) {
            mSharedState.setCurrentChecked(rb);
        }

        rb.setChecked(isChecked);

        mRadioButton = rb;
    }

    @Override
    protected void onClick() {
        super.onClick();
        onRadioButtonClicked(mRadioButton, !mRadioButton.isChecked());
    }

    private boolean shouldDisplayDataAlert() {
        return !mEngineInfo.system;
    }

    private void displayDataAlert(
            DialogInterface.OnClickListener positiveOnClickListener,
            DialogInterface.OnClickListener negativeOnClickListener) {
        // TODO: don't use phone UI
        Log.i(TAG, "Displaying data alert for :" + mEngineInfo.name);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(android.R.string.dialog_alert_title)
                .setMessage(getContext().getString(
                        R.string.tts_engine_security_warning, mEngineInfo.label))
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, positiveOnClickListener)
                .setNegativeButton(android.R.string.cancel, negativeOnClickListener);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void onRadioButtonClicked(final Checkable buttonView,
            boolean isChecked) {
        if (mSharedState.getCurrentChecked() == buttonView) {
            return;
        }

        if (isChecked) {
            // Should we alert user? if that's true, delay making engine current one.
            if (shouldDisplayDataAlert()) {
                displayDataAlert(
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                makeCurrentEngine(buttonView);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Undo the click.
                                buttonView.setChecked(false);
                            }
                        });
            } else {
                // Privileged engine, set it current
                makeCurrentEngine(buttonView);
            }
        }
    }

    private void makeCurrentEngine(Checkable current) {
        if (mSharedState.getCurrentChecked() != null) {
            mSharedState.getCurrentChecked().setChecked(false);
        }
        mSharedState.setCurrentChecked(current);
        mSharedState.setCurrentKey(getKey());
        callChangeListener(mSharedState.getCurrentKey());
    }


    /**
     * Holds all state that is common to this group of radio buttons, such
     * as the currently selected key and the currently checked compound button.
     * (which corresponds to this key).
     */
    public interface RadioButtonGroupState {
        String getCurrentKey();
        Checkable getCurrentChecked();

        void setCurrentKey(String key);
        void setCurrentChecked(Checkable current);
    }

}
