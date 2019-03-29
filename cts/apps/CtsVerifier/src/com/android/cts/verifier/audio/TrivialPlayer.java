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

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.lang.InterruptedException;
import java.lang.Math;
import java.lang.Runnable;

public class TrivialPlayer implements Runnable {
    AudioTrack mAudioTrack;
    int mBufferSize;

    boolean mPlay;
    boolean mIsPlaying;

    short[] mAudioData;

    Thread mFillerThread = null;

    public TrivialPlayer() {
        mBufferSize =
                AudioTrack.getMinBufferSize(
                    41000,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
        mAudioTrack =
            new AudioTrack(
                AudioManager.STREAM_MUSIC,
                41000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                mBufferSize,
                AudioTrack.MODE_STREAM);

        mPlay = false;
        mIsPlaying = false;

        // setup audio data (silence will suffice)
        mAudioData = new short[mBufferSize];
        for (int index = 0; index < mBufferSize; index++) {
            // mAudioData[index] = 0;
            // keep this code since one might want to hear the playnig audio
            // for debugging/verification.
            mAudioData[index] =
                (short)(((Math.random() * 2.0) - 1.0) * (double)Short.MAX_VALUE/2.0);
        }
    }

    public AudioTrack getAudioTrack() { return mAudioTrack; }

    public boolean isPlaying() {
        synchronized (this) {
            return mIsPlaying;
        }
    }

    public void start() {
        mPlay = true;
        mFillerThread = new Thread(this);
        mFillerThread.start();
    }

    public void stop() {
        mPlay = false;
        mFillerThread = null;
    }

    public void shutDown() {
        stop();
        while (isPlaying()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
            }
        }
        mAudioTrack.release();
    }

    @Override
    public void run() {
        mAudioTrack.play();
        synchronized (this) {
            mIsPlaying = true;
        }
        while (mAudioTrack != null && mPlay) {
            mAudioTrack.write(mAudioData, 0, mBufferSize);
        }
        synchronized (this) {
            mIsPlaying = false;
        }
        mAudioTrack.stop();
    }
}
