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
 * limitations under the License.
 */

package com.android.cts.verifier.audio;

import com.android.cts.verifier.R;

import android.content.Context;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTrack;

import android.os.Bundle;
import android.os.Handler;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;

/**
 * Tests AudioTrack and AudioRecord (re)Routing messages.
 */
public class AudioOutputRoutingNotificationsActivity extends HeadsetHonorSystemActivity {
    private static final String TAG = "AudioOutputRoutingNotificationsActivity";

    Context mContext;

    Button playBtn;
    Button stopBtn;

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    int mNumTrackNotifications = 0;

    TrivialPlayer mAudioPlayer = new TrivialPlayer();

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.audio_routingnotification_playBtn:
                    mAudioPlayer.start();
                    break;

                case R.id.audio_routingnotification_playStopBtn:
                    mAudioPlayer.stop();
                    break;
            }
        }
    }

    private class AudioTrackRoutingChangeListener implements AudioTrack.OnRoutingChangedListener {
        public void onRoutingChanged(AudioTrack audioTrack) {
            mNumTrackNotifications++;
            TextView textView =
                (TextView)findViewById(R.id.audio_routingnotification_audioTrack_change);
            String msg = mContext.getResources().getString(
                    R.string.audio_routingnotification_trackRoutingMsg);
            AudioDeviceInfo routedDevice = audioTrack.getRoutedDevice();
            CharSequence deviceName = routedDevice != null ? routedDevice.getProductName() : "none";
            int deviceType = routedDevice != null ? routedDevice.getType() : -1;
            textView.setText(msg + " - " +
                             deviceName + " [0x" + Integer.toHexString(deviceType) + "]" +
                             " - " + mNumTrackNotifications);
        }
    }

    @Override
    protected void enableTestButtons(boolean enabled) {
        playBtn.setEnabled(enabled);
        stopBtn.setEnabled(enabled);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_output_routingnotifications_test);

        mContext = this;

        playBtn = (Button)findViewById(R.id.audio_routingnotification_playBtn);
        playBtn.setOnClickListener(mBtnClickListener);
        stopBtn = (Button)findViewById(R.id.audio_routingnotification_playStopBtn);
        stopBtn.setOnClickListener(mBtnClickListener);

        AudioTrack audioTrack = mAudioPlayer.getAudioTrack();
        audioTrack.addOnRoutingChangedListener(
            new AudioTrackRoutingChangeListener(), new Handler());

        // "Honor System" buttons
        super.setup();

        setPassFailButtonClickListeners();
    }

    @Override
    public void onBackPressed () {
        mAudioPlayer.shutDown();
        super.onBackPressed();
    }
}
