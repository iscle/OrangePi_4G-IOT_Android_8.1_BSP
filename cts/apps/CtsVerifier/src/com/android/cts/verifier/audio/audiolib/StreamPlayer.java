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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import android.util.Log;

/**
 * Plays audio data from a stream. Audio data comes from a provided AudioFiller subclass instance.
 */
public class StreamPlayer {
    @SuppressWarnings("unused")
    private static String TAG = "StreamPlayer";

    private int mSampleRate;
    private int mNumChans;

    private AudioFiller mFiller;

    private Thread mPlayerThread;

    private AudioTrack mAudioTrack;
    private int mNumAudioTrackFrames; // number of frames for INTERNAL AudioTrack buffer

    // The Burst Buffer. This is the buffer we fill with audio and feed into the AudioTrack.
    private int mNumBurstFrames;
    private float[] mBurstBuffer;

    private float[] mChanGains;
    private volatile boolean mPlaying;

    private AudioDeviceInfo mRoutingDevice;

    public StreamPlayer() {}

    public int getSampleRate() { return mSampleRate; }
    public int getNumBurstFrames() { return mNumBurstFrames; }

    public void setChanGains(float[] chanGains) {
        mChanGains = chanGains;
    }

    public boolean isPlaying() { return mPlaying; }

    private void applyChannelGains() {
        if (mChanGains != null) {
            int buffIndex = 0;
            for (int frame = 0; frame < mNumBurstFrames; frame++) {
                for (int chan = 0; chan < mNumChans; chan++) {
                    mBurstBuffer[buffIndex++] *= mChanGains[chan];
                }
            }
        }
    }

    public void setFiller(AudioFiller filler) { mFiller = filler; }

    public void setRouting(AudioDeviceInfo routingDevice) {
        mRoutingDevice = routingDevice;
        if (mAudioTrack != null) {
            mAudioTrack.setPreferredDevice(mRoutingDevice);
        }
    }

    public AudioTrack getAudioTrack() { return mAudioTrack; }

    private void allocBurstBuffer() {
        mBurstBuffer = new float[mNumBurstFrames * mNumChans];
    }

    private static int calcNumBufferBytes(int sampleRate, int numChannels, int encoding) {
        return AudioTrack.getMinBufferSize(sampleRate,
                    AudioUtils.countToOutPositionMask(numChannels),
                    encoding);
    }

    private static int calcNumBufferFrames(int sampleRate, int numChannels, int encoding) {
        return calcNumBufferBytes(sampleRate, numChannels, encoding)
                / AudioUtils.calcFrameSizeInBytes(encoding, numChannels);
    }

    public static int calcNumBurstFrames(AudioManager am) {
        String framesPerBuffer = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        return Integer.parseInt(framesPerBuffer, 10);
    }

    public boolean open(int numChans, int sampleRate, int numBurstFrames, AudioFiller filler) {
//        Log.i(TAG, "StreamPlayer.open(chans:" + numChans + ", rate:" + sampleRate +
//                ", frames:" + numBurstFrames);

        mNumChans = numChans;
        mSampleRate = sampleRate;
        mNumBurstFrames = numBurstFrames;

        mNumAudioTrackFrames =
                calcNumBufferFrames(sampleRate, numChans, AudioFormat.ENCODING_PCM_FLOAT);

        mFiller = filler;

        int bufferSizeInBytes = mNumAudioTrackFrames *
                AudioUtils.calcFrameSizeInBytes(AudioFormat.ENCODING_PCM_FLOAT, mNumChans);
        try {
            mAudioTrack = new AudioTrack.Builder()
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setSampleRate(mSampleRate)
                            .setChannelIndexMask(AudioUtils.countToIndexMask(mNumChans))
                            .build())
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build();

            allocBurstBuffer();
            return true;
        }  catch (UnsupportedOperationException ex) {
            Log.i(TAG, "Couldn't open AudioTrack: " + ex);
            mAudioTrack = null;
            return false;
        }
    }

    private void waitForPlayerThreadToExit() {
        try {
            if (mPlayerThread != null) {
                mPlayerThread.join();
                mPlayerThread = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        stop();

        waitForPlayerThreadToExit();

        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
    }

    public boolean start() {
        if (!mPlaying && mAudioTrack != null) {
            mPlaying = true;

            waitForPlayerThreadToExit(); // just to be sure.

            mPlayerThread = new Thread(new StreamPlayerRunnable(), "StreamPlayer Thread");
            mPlayerThread.start();

            return true;
        }

        return false;
    }

    public void stop() {
        mPlaying = false;
    }

    //
    // StreamPlayerRunnable
    //
    private class StreamPlayerRunnable implements Runnable {
        @Override
        public void run() {
            final int numBurstSamples = mNumBurstFrames * mNumChans;

            mAudioTrack.play();
            while (true) {
                boolean playing;
                synchronized(this) {
                    playing = mPlaying;
                }
                if (!playing) {
                    break;
                }

                mFiller.fill(mBurstBuffer, mNumBurstFrames, mNumChans);
                if (mChanGains != null) {
                    applyChannelGains();
                }
                int numSamplesWritten =
                        mAudioTrack.write(mBurstBuffer, 0, numBurstSamples, AudioTrack.WRITE_BLOCKING);
                if (numSamplesWritten < 0) {
                    // error
                    Log.i(TAG, "AudioTrack write error: " + numSamplesWritten);
                    stop();
                } else if (numSamplesWritten < numBurstSamples) {
                    // end of stream
                    Log.i(TAG, "Stream Complete.");
                    stop();
                }
            }
        }
    }
}
