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

package com.google.android.car.kitchensink.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

import com.google.android.car.kitchensink.R;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for playing music.
 *
 * This is not thread safe and all public calls should be made from main thread.
 *
 * MP3 used is from http://freemusicarchive.org/music/John_Harrison_with_the_Wichita_State_University_Chamber_Players/The_Four_Seasons_Vivaldi/05_-_Vivaldi_Summer_mvt_2_Adagio_-_John_Harrison_violin
 * from John Harrison with the Wichita State University Chamber Players
 * Copyright under Create Commons license.
 */
public class AudioPlayer {

    public interface PlayStateListener {
        void onCompletion();
    }

    private static final String TAG = AudioPlayer.class.getSimpleName();

    private final AudioManager.OnAudioFocusChangeListener mFocusListener =
            new AudioManager.OnAudioFocusChangeListener() {

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.i(TAG, "audio focus change " + focusChange);
            if (mPlayer == null) {
                return;
            }
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                mPlayer.setVolume(1.0f, 1.0f);
                if (mRepeat && isPlaying()) {
                    doResume();
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (isPlaying()) {
                    // Duck to 20% volume (which matches system ducking as of this date)
                    mPlayer.setVolume(0.2f, 0.2f);
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && mRepeat) {
                if (isPlaying()) {
                    doPause();
                }
            } else {
                if (isPlaying()) {
                    doStop();
                }
            }
        }
    };

    private AudioManager mAudioManager;
    private MediaPlayer mPlayer;

    private final Context mContext;
    private final int mResourceId;
    private final AudioAttributes mAttrib;

    private final AtomicBoolean mPlaying = new AtomicBoolean(false);

    private volatile boolean mHandleFocus;
    private volatile boolean mRepeat;

    private PlayStateListener mListener;

    public AudioPlayer(Context context, int resourceId, AudioAttributes attrib) {
        mContext = context;
        mResourceId = resourceId;
        mAttrib = attrib;
    }

    public void start(boolean handleFocus, boolean repeat, int focusRequest) {
        mHandleFocus = handleFocus;
        mRepeat = repeat;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        int ret = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (mHandleFocus) {
            ret = mAudioManager.requestAudioFocus(mFocusListener, mAttrib,
                    focusRequest, 0);
        }
        if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            doStart();
        } else {
            Log.i(TAG, "no focus");
        }
    }

    public void start(boolean handleFocus, boolean repeat, int focusRequest,
            PlayStateListener listener) {
        mListener = listener;
        start(handleFocus, repeat, focusRequest);
    }

    private void doStart() {
        if (mPlaying.getAndSet(true)) {
            Log.i(TAG, "already playing");
            return;
        }
        Log.i(TAG, "doStart audio");
        mPlayer = new MediaPlayer();
        mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "audio error what " + what + " extra " + extra);
                mPlaying.set(false);
                if (!mRepeat && mHandleFocus) {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                    mAudioManager.abandonAudioFocus(mFocusListener);
                }
                return false;
            }

        });
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mPlaying.set(false);
                if (!mRepeat && mHandleFocus) {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                    mAudioManager.abandonAudioFocus(mFocusListener);
                    if (mListener != null) {
                        mListener.onCompletion();
                    }
                }
            }
        });
        mPlayer.setAudioAttributes(mAttrib);
        mPlayer.setLooping(mRepeat);
        mPlayer.setVolume(1.0f, 1.0f);
        try {
            AssetFileDescriptor afd =
                    mContext.getResources().openRawResourceFd(mResourceId);
            if (afd == null) {
                throw new RuntimeException("no res");
            }
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
            afd.close();
            mPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        mPlayer.start();
    }

    public void stop() {
        doStop();
        if (mHandleFocus) {
            mAudioManager.abandonAudioFocus(mFocusListener);
        }
    }

    public void release() {
        if (isPlaying()) {
            stop();
        }
    }

    private void doStop() {
        if (!mPlaying.getAndSet(false)) {
            Log.i(TAG, "already stopped");
            return;
        }
        Log.i(TAG, "doStop audio");
        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
    }

    private void doPause() {
        mPlayer.pause();
    }

    private void doResume() {
        mPlayer.start();
    }

    public boolean isPlaying() {
        return mPlaying.get();
    }
}
