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
import android.media.AudioRecord;

import android.media.MediaRecorder;

import java.lang.InterruptedException;
import java.lang.Runnable;

public class TrivialRecorder implements Runnable {
    AudioRecord mAudioRecord;
    int mBufferSize;

    boolean mRecord;
    boolean mIsRecording;

    short[] mAudioData;

    Thread mReaderThread = null;

    public TrivialRecorder() {
        mBufferSize =
                AudioRecord.getMinBufferSize(
                    41000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
        mAudioRecord =
            new AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                41000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                mBufferSize);

        mRecord = false;
        mIsRecording = false;

        // setup audio data (silence will suffice)
        mAudioData = new short[mBufferSize];
    }

    public AudioRecord getAudioRecord() { return mAudioRecord; }

    public boolean mIsRecording() {
        synchronized (this) {
            return mIsRecording;
        }
    }

    public void start() {
        mRecord = true;
        mReaderThread = new Thread(this);
        mReaderThread.start();
    }

    public void stop() {
        mRecord = false;
        mReaderThread = null;
    }

    public void shutDown() {
        stop();
        while (mIsRecording()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ex) {
            }
        }
        mAudioRecord.release();
    }

    @Override
    public void run() {
        mAudioRecord.startRecording();
        synchronized (this) {
            mIsRecording = true;
        }
       while (mRecord) {
            mAudioRecord.read(mAudioData, 0, mBufferSize);
        }
        mAudioRecord.stop();
        synchronized (this) {
            mIsRecording = false;
        }
    }
}
