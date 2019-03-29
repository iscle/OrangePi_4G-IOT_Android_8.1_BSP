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

import android.content.Context;
import android.media.AudioManager;
import android.util.Log;

import com.android.cts.verifier.audio.audiolib.SignalGenerator;
import com.android.cts.verifier.audio.audiolib.StreamPlayer;
import com.android.cts.verifier.audio.audiolib.WaveTableFloatFiller;
import com.android.cts.verifier.audio.peripheralprofile.USBDeviceInfoHelper;

public abstract class USBAudioPeripheralPlayerActivity extends USBAudioPeripheralActivity {
    private static final String TAG = "USBAudioPeripheralPlayerActivity";

    protected  int mSystemBufferSize;

    // Player
    protected boolean mIsPlaying = false;
    protected StreamPlayer mPlayer = null;
    protected WaveTableFloatFiller mFiller = null;

    protected float[] mWavBuffer = null;

    protected boolean mOverridePlayFlag = true;

    private static final int WAVBUFF_SIZE_IN_SAMPLES = 2048;

    protected void setupPlayer() {
        mSystemBufferSize =
            StreamPlayer.calcNumBurstFrames((AudioManager)getSystemService(Context.AUDIO_SERVICE));

        // the +1 is so we can repeat the 0th sample and simplify the interpolation calculation.
        mWavBuffer = new float[WAVBUFF_SIZE_IN_SAMPLES + 1];

        SignalGenerator.fillFloatSine(mWavBuffer);
        mFiller = new WaveTableFloatFiller(mWavBuffer);

        mPlayer = new StreamPlayer();
    }

    protected void startPlay() {
        if (mOutputDevInfo != null && !mIsPlaying) {
            int numChans = USBDeviceInfoHelper.calcMaxChannelCount(mOutputDevInfo);
            mPlayer.open(numChans, mSystemSampleRate, mSystemBufferSize, mFiller);
            mPlayer.start();
            mIsPlaying = true;
        }
    }

    protected void stopPlay() {
        if (mIsPlaying) {
            mPlayer.stop();
            mPlayer.close();
            mIsPlaying = false;
        }
    }

    public boolean isPlaying() {
        return mIsPlaying;
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopPlay();
    }
}
