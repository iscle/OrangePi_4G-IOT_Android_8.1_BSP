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

import android.os.Bundle;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;

/**
 * Tests Audio Device Connection events for output by prompting the user to insert/remove a
 * wired headset (or microphone) and noting the presence (or absence) of notifications.
 */
public class AudioInputDeviceNotificationsActivity extends HeadsetHonorSystemActivity {
    Context mContext;

    TextView mConnectView;
    TextView mDisconnectView;
    Button mClearMsgsBtn;

    private class TestAudioDeviceCallback extends AudioDeviceCallback {
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if (addedDevices.length != 0) {
                mConnectView.setText(
                    mContext.getResources().getString(R.string.audio_dev_notification_connectMsg));
            }
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if (removedDevices.length != 0) {
                mDisconnectView.setText(
                    mContext.getResources().getString(
                        R.string.audio_dev_notification_disconnectMsg));
            }
        }
    }

    @Override
    protected void enableTestButtons(boolean enabled) {
        // Nothing to do.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_dev_notify);

        mContext = this;

        mConnectView = (TextView)findViewById(R.id.audio_dev_notification_connect_msg);
        mDisconnectView = (TextView)findViewById(R.id.audio_dev_notification_disconnect_msg);

        ((TextView)findViewById(R.id.info_text)).setText(mContext.getResources().getString(
                R.string.audio_in_devices_notification_instructions));

        mClearMsgsBtn = (Button)findViewById(R.id.audio_dev_notification_connect_clearmsgs_btn);
        mClearMsgsBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mConnectView.setText("");
                mDisconnectView.setText("");
            }
        });

        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.registerAudioDeviceCallback(new TestAudioDeviceCallback(), null);

        // "Honor System" buttons
        super.setup();

        setPassFailButtonClickListeners();
    }
}
