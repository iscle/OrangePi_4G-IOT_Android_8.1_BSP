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
import android.media.AudioRecord;

import android.os.Bundle;
import android.os.Handler;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;

/**
 * Tests AudioRecord (re)Routing messages.
 */
public class AudioInputRoutingNotificationsActivity extends HeadsetHonorSystemActivity {
    private static final String TAG = "AudioInputRoutingNotificationsActivity";

    Button recordBtn;
    Button stopBtn;

    Context mContext;

    int mNumRecordNotifications = 0;

    OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    TrivialRecorder mAudioRecorder = new TrivialRecorder();

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.audio_routingnotification_recordBtn:
                    mAudioRecorder.start();
                    break;

                case R.id.audio_routingnotification_recordStopBtn:
                    mAudioRecorder.stop();
                    break;
            }
        }
    }

    private class AudioRecordRoutingChangeListener implements AudioRecord.OnRoutingChangedListener {
        public void onRoutingChanged(AudioRecord audioRecord) {
            mNumRecordNotifications++;
            TextView textView =
                    (TextView)findViewById(R.id.audio_routingnotification_audioRecord_change);
            String msg = mContext.getResources().getString(
                    R.string.audio_routingnotification_recordRoutingMsg);
            AudioDeviceInfo routedDevice = audioRecord.getRoutedDevice();
            CharSequence deviceName = routedDevice != null ? routedDevice.getProductName() : "none";
            int deviceType = routedDevice != null ? routedDevice.getType() : -1;
            textView.setText(msg + " - " +
                             deviceName + " [0x" + Integer.toHexString(deviceType) + "]" +
                             " - " + mNumRecordNotifications);
        }
    }

    protected void enableTestButtons(boolean enabled) {
        recordBtn.setEnabled(enabled);
        stopBtn.setEnabled(enabled);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_input_routingnotifications_test);

        Button btn;
        recordBtn = (Button)findViewById(R.id.audio_routingnotification_recordBtn);
        recordBtn.setOnClickListener(mBtnClickListener);
        stopBtn = (Button)findViewById(R.id.audio_routingnotification_recordStopBtn);
        stopBtn.setOnClickListener(mBtnClickListener);

        mContext = this;

        AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
        audioRecord.addOnRoutingChangedListener(
            new AudioRecordRoutingChangeListener(), new Handler());

        // "Honor System" buttons
        super.setup();

        setPassFailButtonClickListeners();
    }

    @Override
    public void onBackPressed () {
        mAudioRecorder.shutDown();
        super.onBackPressed();
    }
}
