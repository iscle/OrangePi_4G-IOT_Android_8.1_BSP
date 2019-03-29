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

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.rtp.AudioStream;
import android.util.Log;

import com.android.cts.verifier.audio.wavelib.PipeShort;

import java.io.IOException;

public class SoundPlayerObject extends Thread {
    private static final String LOGTAG = "SoundPlayerObject";
    private MediaPlayer mMediaPlayer;
    private boolean isInitialized = false;
    private boolean isRunning = false;

    public PipeShort mPipe = new PipeShort(65536);
    private short[] mAudioShortArray;

    public AudioTrack mAudioTrack;
    public int mSamplingRate = 48000;
    private int mChannelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int mMinPlayBufferSizeInBytes = 0;
    int mMinBufferSizeInSamples = 0;

    private int mStreamType = AudioManager.STREAM_MUSIC;
    private int mResId = -1;
    private boolean mUseMediaPlayer = true;
    private float mBalance = 0.5f; //0 left, 1 right

    public int getCurrentResId() {
        return mResId;
    }

    public int getStreamType () {
        return mStreamType;
    }

    public void run() {
        setPriority(Thread.MAX_PRIORITY);
        isRunning = true;
        while (isRunning) {

            if (!mUseMediaPlayer && isInitialized && isPlaying()) {
                int valuesAvailable = mPipe.availableToRead();
                if (valuesAvailable > 0) {

                    int valuesOfInterest = valuesAvailable;
                    if (mMinBufferSizeInSamples < valuesOfInterest) {
                        valuesOfInterest = mMinBufferSizeInSamples;
                    }
                    mPipe.read(mAudioShortArray, 0,valuesOfInterest);
                    //inject into output.
                    mAudioTrack.write(mAudioShortArray, 0, valuesOfInterest);
                }
            } else {
                try {
                    sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setBalance(float balance) {
        mBalance = balance;
        if (mUseMediaPlayer) {
            if (mMediaPlayer != null) {
                float left = Math.min(2.0f * (1.0f - mBalance), 1.0f);
                float right = Math.min(2.0f * mBalance, 1.0f);
                mMediaPlayer.setVolume(left, right);
                log(String.format("Setting balance to %f", mBalance));
            }
        }
    }

    public void setStreamType(int streamType) {
        mStreamType = streamType;
    }

    public void rewind() {
        if (mUseMediaPlayer) {
            if (mMediaPlayer != null) {
                mMediaPlayer.seekTo(0);
            }
        }
    }

    public void setSoundWithResId(Context context, int resId) {
        boolean playing = isPlaying();
        //release player
        releasePlayer();
        mResId = resId;
        if (mUseMediaPlayer) {
            mMediaPlayer = new MediaPlayer();
            try {
                log("opening resource with stream type: " + mStreamType);
                mMediaPlayer.setAudioStreamType(mStreamType);
                mMediaPlayer.setDataSource(context.getApplicationContext(),
                        Uri.parse("android.resource://com.android.cts.verifier/" + resId));
                mMediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mMediaPlayer.setLooping(true);
            setBalance(mBalance);
        } else {
            mMinPlayBufferSizeInBytes = AudioTrack.getMinBufferSize(mSamplingRate,
                    mChannelConfigOut, mAudioFormat);

            mMinBufferSizeInSamples = mMinPlayBufferSizeInBytes / 2;
            mAudioShortArray = new short[mMinBufferSizeInSamples * 4];

            mAudioTrack = new AudioTrack(mStreamType,
                    mSamplingRate,
                    mChannelConfigOut,
                    mAudioFormat,
                    mMinPlayBufferSizeInBytes,
                    AudioTrack.MODE_STREAM /* FIXME runtime test for API level 9 ,
                mSessionId */);
            mPipe.flush();
            isInitialized = true;
        }

        log("done preparing media player");
        if (playing)
            play(true); //start playing if it was playing before
    }

    public boolean isPlaying() {
        boolean result = false;
        if (mUseMediaPlayer) {
            if (mMediaPlayer != null) {
                result = mMediaPlayer.isPlaying();
            }
        } else {
            if (mAudioTrack != null) {
                result = mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
            }
        }
        return result;
    }

    public void play(boolean play) {
        if (mUseMediaPlayer) {
            if (mMediaPlayer != null) {
                if (play) {
                    mMediaPlayer.start();
                } else {
                    mMediaPlayer.pause();
                }
            }
        } else {
            if (mAudioTrack != null && isInitialized) {
                if (play) {
                    mPipe.flush();
                    mAudioTrack.flush();
                    mAudioTrack.play();
                } else {
                    mAudioTrack.pause();
                }
            }
        }
    }

    public void finish() {
        play(false);
        releasePlayer();
    }

    private void releasePlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        if (mAudioTrack != null) {
            mAudioTrack.stop();
            mAudioTrack.release();
            mAudioTrack = null;
        }
        isInitialized = false;
    }

    /*
       Misc
    */
    private static void log(String msg) {
        Log.v(LOGTAG, msg);
    }
}
