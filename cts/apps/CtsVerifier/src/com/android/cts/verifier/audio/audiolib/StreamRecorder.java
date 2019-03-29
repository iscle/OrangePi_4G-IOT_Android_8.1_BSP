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

package com.android.cts.verifier.audio.audiolib;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;

import android.util.Log;

/**
 * Records audio data to a stream.
 */
public class StreamRecorder {
    @SuppressWarnings("unused")
    private static final String TAG = "StreamRecorder";

    // Sample Buffer
    private float[] mBurstBuffer;
    private int mNumBurstFrames;
    private int mNumChannels;

    // Recording attributes
    private int mSampleRate;

    // Recording state
    Thread mRecorderThread = null;
    private AudioRecord mAudioRecord = null;
    private boolean mRecording = false;

    private StreamRecorderListener mListener = null;

    private AudioDeviceInfo mRoutingDevice = null;

    public StreamRecorder() {}

    public int getNumBurstFrames() { return mNumBurstFrames; }
    public int getSampleRate() { return mSampleRate; }

    /*
     * State
     */
    public static int calcNumBufferBytes(int numChannels, int sampleRate, int encoding) {
        // NOTE: Special handling of 4-channels. There is currently no AudioFormat positional
        // constant for 4-channels of input, so in this case, calculate for 2 and double it.
        int numBytes = 0;
        if (numChannels == 4) {
            numBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO,
                    encoding);
            numBytes *= 2;
        } else {
            numBytes = AudioRecord.getMinBufferSize(sampleRate,
                    AudioUtils.countToInPositionMask(numChannels), encoding);
        }

        return numBytes;
    }

    public static int calcNumBufferFrames(int numChannels, int sampleRate, int encoding) {
        return calcNumBufferBytes(numChannels, sampleRate, encoding) /
                AudioUtils.calcFrameSizeInBytes(encoding, numChannels);
    }

    public boolean isInitialized() {
        return mAudioRecord != null && mAudioRecord.getState() == AudioRecord.STATE_INITIALIZED;
    }

    public boolean isRecording() { return mRecording; }

    public void setRouting(AudioDeviceInfo routingDevice) {
        Log.i(TAG, "setRouting(" + (routingDevice != null ? routingDevice.getId() : -1) + ")");
        mRoutingDevice = routingDevice;
        if (mAudioRecord != null) {
            mAudioRecord.setPreferredDevice(mRoutingDevice);
        }
    }

    /*
     * Accessors
     */
    public float[] getBurstBuffer() { return mBurstBuffer; }

    public int getNumChannels() { return mNumChannels; }

    /*
     * Events
     */
    public void setListener(StreamRecorderListener listener) {
        mListener = listener;
    }

    private void waitForRecorderThreadToExit() {
        try {
            if (mRecorderThread != null) {
                mRecorderThread.join();
                mRecorderThread = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean open_internal(int numChans, int sampleRate) {
        Log.i(TAG, "StreamRecorder.open_internal(chans:" + numChans + ", rate:" + sampleRate);

        mNumChannels = numChans;
        mSampleRate = sampleRate;

        int chanMask = AudioUtils.countToIndexMask(numChans);
        int bufferSizeInBytes = 2048;   // Some, non-critical value

        try {
            mAudioRecord = new AudioRecord.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(mSampleRate)
                            .setChannelIndexMask(chanMask)
                            .build())
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build();

            return true;
        } catch (UnsupportedOperationException ex) {
            Log.i(TAG, "Couldn't open AudioRecord: " + ex);
            mAudioRecord = null;
            return false;
        }
    }

    public boolean open(int numChans, int sampleRate, int numBurstFrames) {
        boolean sucess = open_internal(numChans, sampleRate);
        if (sucess) {
            mNumBurstFrames = numBurstFrames;
            mBurstBuffer = new float[mNumBurstFrames * mNumChannels];
        }

        return sucess;
    }

    public void close() {
        stop();

        waitForRecorderThreadToExit();

        mAudioRecord.release();
        mAudioRecord = null;
    }

    public boolean start() {
        mAudioRecord.setPreferredDevice(mRoutingDevice);

        if (mListener != null) {
            mListener.sendEmptyMessage(StreamRecorderListener.MSG_START);
        }

        try {
            mAudioRecord.startRecording();
        } catch (IllegalStateException ex) {
            Log.i("", "ex: " + ex);
        }
        mRecording = true;

        waitForRecorderThreadToExit(); // just to be sure.

        mRecorderThread = new Thread(new StreamRecorderRunnable(), "StreamRecorder Thread");
        mRecorderThread.start();

        return true;
    }

    public void stop() {
        if (mRecording) {
            mRecording = false;
        }
    }

    /*
     * StreamRecorderRunnable
     */
    private class StreamRecorderRunnable implements Runnable {
        @Override
        public void run() {
            final int numBurstSamples = mNumBurstFrames * mNumChannels;
            while (mRecording) {
                int numReadSamples = mAudioRecord.read(
                        mBurstBuffer, 0, numBurstSamples, AudioRecord.READ_BLOCKING);

                if (numReadSamples < 0) {
                    // error
                    Log.i(TAG, "AudioRecord write error: " + numReadSamples);
                    stop();
                } else if (numReadSamples < numBurstSamples) {
                    // got less than requested?
                    Log.i(TAG, "AudioRecord Underflow: " + numReadSamples +
                            " vs. " + numBurstSamples);
                    stop();
                }

                if (mListener != null && numReadSamples == numBurstSamples) {
                    mListener.sendEmptyMessage(StreamRecorderListener.MSG_BUFFER_FILL);
                }
            }

            if (mListener != null) {
                // TODO: on error or underrun we may be send bogus data.
                mListener.sendEmptyMessage(StreamRecorderListener.MSG_STOP);
            }
            mAudioRecord.stop();
        }
    }
}
