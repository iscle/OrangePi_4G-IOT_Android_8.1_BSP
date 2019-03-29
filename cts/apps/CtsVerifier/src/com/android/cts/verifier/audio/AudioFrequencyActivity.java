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

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.wavelib.*;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * Audio Frequency Test base activity
 */
public class AudioFrequencyActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioFrequencyActivity";

    public int mMaxLevel = 0;

    public void setMaxLevel() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMaxLevel = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(mMaxLevel), 0);
    }

    public void setMinLevel() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
    }

    public void testMaxLevel() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int currentLevel =  am.getStreamVolume(AudioManager.STREAM_MUSIC);
        Log.i(TAG, String.format("Max level: %d curLevel: %d", mMaxLevel, currentLevel));
        if (currentLevel != mMaxLevel) {
            new AlertDialog.Builder(this)
                .setTitle(R.string.audio_general_warning)
                .setMessage(R.string.audio_general_level_not_max)
                .setPositiveButton(R.string.audio_general_ok, null)
                .show();
        }
    }

}
