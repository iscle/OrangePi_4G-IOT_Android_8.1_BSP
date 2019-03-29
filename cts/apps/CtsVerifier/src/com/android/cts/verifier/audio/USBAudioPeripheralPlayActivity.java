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

package com.android.cts.verifier.audio;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.audio.peripheralprofile.PeripheralProfile;
import com.android.cts.verifier.R;  // needed to access resource in CTSVerifier project namespace.

public class USBAudioPeripheralPlayActivity extends USBAudioPeripheralPlayerActivity {
    private static final String TAG = "USBAudioPeripheralPlayActivity";

    // Widgets
    private Button mPlayBtn;
    private LocalClickListener mButtonClickListener = new LocalClickListener();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uap_play_panel);

        connectPeripheralStatusWidgets();

        // Local widgets
        mPlayBtn = (Button)findViewById(R.id.uap_playPlayBtn);
        mPlayBtn.setOnClickListener(mButtonClickListener);

        setupPlayer();

        setPassFailButtonClickListeners();
        setInfoResources(R.string.usbaudio_play_test, R.string.usbaudio_play_info, -1);
    }

    //
    // USBAudioPeripheralActivity
    //
    public void updateConnectStatus() {
        getPassButton().setEnabled(mOutputDevInfo != null);
    }

    public class LocalClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
            case R.id.uap_playPlayBtn:
                Log.i(TAG, "Play Button Pressed");
                if (!isPlaying()) {
                    startPlay();
                    mPlayBtn.setText(getString(R.string.audio_uap_play_stopBtn));
                } else {
                    stopPlay();
                    mPlayBtn.setText(getString(R.string.audio_uap_play_playBtn));
                }
                break;
            }
        }
    }
}
