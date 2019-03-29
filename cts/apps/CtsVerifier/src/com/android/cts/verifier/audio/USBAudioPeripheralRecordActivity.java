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

import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.audio.audiolib.StreamRecorder;
import com.android.cts.verifier.audio.audiolib.StreamRecorderListener;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;

import com.android.cts.verifier.audio.peripheralprofile.PeripheralProfile;
import com.android.cts.verifier.audio.peripheralprofile.USBDeviceInfoHelper;

import com.android.cts.verifier.R;  // needed to access resource in CTSVerifier project namespace.

public class USBAudioPeripheralRecordActivity extends USBAudioPeripheralPlayerActivity {
    private static final String TAG = "USBAudioPeripheralRecordActivity";

    // Recorder
    private StreamRecorder mRecorder = null;
    private RecordListener mRecordListener = null;
    private boolean mIsRecording = false;

    // Widgets
    private Button mRecordBtn;
    private Button mRecordLoopbackBtn;

    private LocalClickListener mButtonClickListener = new LocalClickListener();

    private WaveScopeView mWaveView = null;

    private void connectWaveView() {
        // Log.i(TAG, "connectWaveView() rec:" + (mRecorder != null));
        if (mRecorder != null) {
            float[] smplFloatBuff = mRecorder.getBurstBuffer();
            int numChans = mRecorder.getNumChannels();
            int numFrames = smplFloatBuff.length / numChans;
            mWaveView.setPCMFloatBuff(smplFloatBuff, numChans, numFrames);
            mWaveView.invalidate();

            mRecorder.setListener(mRecordListener);
        }
    }

    public boolean startRecording(boolean withLoopback) {
        if (mInputDevInfo == null) {
            return false;
        }

        if (mRecorder == null) {
            mRecorder = new StreamRecorder();
        } else if (mRecorder.isRecording()) {
            mRecorder.stop();
        }

        int numChans = USBDeviceInfoHelper.calcMaxChannelCount(mInputDevInfo);

        if (mRecorder.open(numChans, mSystemSampleRate, mSystemBufferSize)) {
            connectWaveView();  // Setup the WaveView

            mIsRecording = mRecorder.start();

            if (withLoopback) {
                startPlay();
            }

            return mIsRecording;
        } else {
            return false;
        }
    }

    public void stopRecording() {
        if (mRecorder != null) {
            mRecorder.stop();
        }

        if (mPlayer != null && mPlayer.isPlaying()) {
            mPlayer.stop();
        }

        mIsRecording = false;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uap_record_panel);

        connectPeripheralStatusWidgets();

        // Local widgets
        mRecordBtn = (Button)findViewById(R.id.uap_recordRecordBtn);
        mRecordBtn.setOnClickListener(mButtonClickListener);
        mRecordLoopbackBtn = (Button)findViewById(R.id.uap_recordRecordLoopBtn);
        mRecordLoopbackBtn.setOnClickListener(mButtonClickListener);

        setupPlayer();

        mRecorder = new StreamRecorder();
        mRecordListener = new RecordListener();

        mWaveView = (WaveScopeView)findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.usbaudio_record_test, R.string.usbaudio_record_info, -1);
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
            int id = view.getId();
            switch (id) {
            case R.id.uap_recordRecordBtn:
                if (!isRecording()) {
                    if (startRecording(false)) {
                        mRecordBtn.setText(getString(R.string.audio_uap_record_stopBtn));
                        mRecordLoopbackBtn.setEnabled(false);
                    }
                } else {
                    stopRecording();
                    mRecordBtn.setText(getString(R.string.audio_uap_record_recordBtn));
                    mRecordLoopbackBtn.setEnabled(true);
                }
                break;

            case R.id.uap_recordRecordLoopBtn:
                if (!isRecording()) {
                    if (startRecording(true)) {
                        mRecordLoopbackBtn.setText(getString(R.string.audio_uap_record_stopBtn));
                        mRecordBtn.setEnabled(false);
                    }
                } else {
                    if (isPlaying()) {
                        stopPlay();
                    }
                    stopRecording();
                    mRecordLoopbackBtn.setText(
                        getString(R.string.audio_uap_record_recordLoopbackBtn));
                    mRecordBtn.setEnabled(true);
                }
                break;
            }
        }
    }

    private class RecordListener extends StreamRecorderListener {
        /*package*/ RecordListener() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            // Log.i(TAG, "RecordListener.HandleMessage(" + msg.what + ")");
            switch (msg.what) {
                case MSG_START:
                    break;

                case MSG_BUFFER_FILL:
                    mWaveView.invalidate();
                    break;

                case MSG_STOP:
                    break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopPlay();
    }
}

