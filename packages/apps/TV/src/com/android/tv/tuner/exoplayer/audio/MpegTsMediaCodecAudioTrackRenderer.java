/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tv.tuner.exoplayer.audio;

import android.os.Handler;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.SampleSource;

/**
 * MPEG-2 TS audio track renderer.
 *
 * <p>Since the audio output from {@link android.media.MediaExtractor} contains extra samples at the
 * beginning, using original {@link MediaCodecAudioTrackRenderer} as audio renderer causes
 * asynchronous Audio/Video outputs. This class calculates the offset of audio data and adjust the
 * presentation times to avoid the asynchronous Audio/Video problem.
 */
public class MpegTsMediaCodecAudioTrackRenderer extends MediaCodecAudioTrackRenderer {
    private final Ac3EventListener mListener;

    public interface Ac3EventListener extends EventListener {
        /**
         * Invoked when a {@link android.media.PlaybackParams} set to an
         * {@link android.media.AudioTrack} is not valid.
         *
         * @param e The corresponding exception.
         */
        void onAudioTrackSetPlaybackParamsError(IllegalArgumentException e);
    }

    public MpegTsMediaCodecAudioTrackRenderer(
            SampleSource source,
            MediaCodecSelector mediaCodecSelector,
            Handler eventHandler,
            EventListener eventListener) {
        super(source, mediaCodecSelector, eventHandler, eventListener);
        mListener = (Ac3EventListener) eventListener;
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        if (messageType == MSG_SET_PLAYBACK_PARAMS) {
            try {
                super.handleMessage(messageType, message);
            } catch (IllegalArgumentException e) {
                if (isAudioTrackSetPlaybackParamsError(e)) {
                    notifyAudioTrackSetPlaybackParamsError(e);
                }
            }
            return;
        }
        super.handleMessage(messageType, message);
    }

    private void notifyAudioTrackSetPlaybackParamsError(final IllegalArgumentException e) {
        if (eventHandler != null && mListener != null) {
            eventHandler.post(new Runnable()  {
                @Override
                public void run() {
                    mListener.onAudioTrackSetPlaybackParamsError(e);
                }
            });
        }
    }

    static private boolean isAudioTrackSetPlaybackParamsError(IllegalArgumentException e) {
        if (e.getStackTrace() == null || e.getStackTrace().length < 1) {
            return false;
        }
        for (StackTraceElement element : e.getStackTrace()) {
            String elementString = element.toString();
            if (elementString.startsWith("android.media.AudioTrack.setPlaybackParams")) {
                return true;
            }
        }
        return false;
    }
}