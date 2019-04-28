/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.telecom.Log;
import android.telecom.Logging.Session;

import com.android.internal.annotations.VisibleForTesting;

// TODO: Needed for move to system service: import com.android.internal.R;

/**
 * Plays DTMF tones locally for the caller to hear. In order to reduce (1) the amount of times we
 * check the "play local tones" setting and (2) the length of time we keep the tone generator, this
 * class employs a concept of a call "session" that starts and stops when the foreground call
 * changes.
 */
public class DtmfLocalTonePlayer {
    public static class ToneGeneratorProxy {
        /** Generator used to actually play the tone. */
        private ToneGenerator mToneGenerator;

        public void create() {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(AudioManager.STREAM_DTMF, 80);
                } catch (RuntimeException e) {
                    Log.e(this, e, "Error creating local tone generator.");
                    mToneGenerator = null;
                }
            }
        }

        public void release() {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }

        public boolean isPresent() {
            return mToneGenerator != null;
        }

        public void startTone(int toneType, int durationMs) {
            if (mToneGenerator != null) {
                mToneGenerator.startTone(toneType, durationMs);
            }
        }

        public void stopTone() {
            if (mToneGenerator != null) {
                mToneGenerator.stopTone();
            }
        }
    }

    private final class ToneHandler extends Handler {

        public ToneHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            try {
                if (msg.obj instanceof Session) {
                    Log.continueSession((Session) msg.obj, "DLTP.TH");
                }

                switch (msg.what) {
                    case EVENT_START_SESSION:
                        mToneGeneratorProxy.create();
                        break;
                    case EVENT_END_SESSION:
                        mToneGeneratorProxy.release();
                        break;
                    case EVENT_PLAY_TONE:
                        char c = (char) msg.arg1;
                        if (!mToneGeneratorProxy.isPresent()) {
                            Log.d(this, "playTone: no tone generator, %c.", c);
                        } else {
                            Log.d(this, "starting local tone: %c.", c);
                            int tone = getMappedTone(c);
                            if (tone != ToneGenerator.TONE_UNKNOWN) {
                                mToneGeneratorProxy.startTone(tone, -1 /* toneDuration */);
                            }
                        }
                        break;
                    case EVENT_STOP_TONE:
                        if (mToneGeneratorProxy.isPresent()) {
                            mToneGeneratorProxy.stopTone();
                        }
                        break;
                    default:
                        Log.w(this, "Unknown message: %d", msg.what);
                        break;
                }
            } finally {
                Log.endSession();
            }
        }
    }

    /** The current call associated with an existing dtmf session. */
    private Call mCall;

    /**
     * Message codes to be used for creating and deleting ToneGenerator object in the tonegenerator
     * thread, as well as for actually playing the tones.
     */
    private static final int EVENT_START_SESSION = 1;
    private static final int EVENT_END_SESSION = 2;
    private static final int EVENT_PLAY_TONE = 3;
    private static final int EVENT_STOP_TONE = 4;

    /** Handler running on the tonegenerator thread. */
    private ToneHandler mHandler;

    private final ToneGeneratorProxy mToneGeneratorProxy;

    public DtmfLocalTonePlayer(ToneGeneratorProxy toneGeneratorProxy) {
        mToneGeneratorProxy = toneGeneratorProxy;
    }

    public void onForegroundCallChanged(Call oldForegroundCall, Call newForegroundCall) {
        endDtmfSession(oldForegroundCall);
        startDtmfSession(newForegroundCall);
    }

    /**
     * Starts playing the dtmf tone specified by c.
     *
     * @param call The associated call.
     * @param c The digit to play.
     */
    public void playTone(Call call, char c) {
        // Do nothing if it is not the right call.
        if (mCall != call) {
            return;
        }

        getHandler().sendMessage(
                getHandler().obtainMessage(EVENT_PLAY_TONE, (int) c, 0, Log.createSubsession()));
    }

    /**
     * Stops any currently playing dtmf tone.
     *
     * @param call The associated call.
     */
    public void stopTone(Call call) {
        // Do nothing if it's not the right call.
        if (mCall != call) {
            return;
        }

        getHandler().sendMessage(
                getHandler().obtainMessage(EVENT_STOP_TONE, Log.createSubsession()));
    }

    /**
     * Runs initialization requires to play local tones during a call.
     *
     * @param call The call associated with this dtmf session.
     */
    private void startDtmfSession(Call call) {
        if (call == null) {
            return;
        }
        final Context context = call.getContext();
        final boolean areLocalTonesEnabled;
        if (context.getResources().getBoolean(R.bool.allow_local_dtmf_tones)) {
            areLocalTonesEnabled = Settings.System.getInt(
                    context.getContentResolver(), Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;
        } else {
            areLocalTonesEnabled = false;
        }

        mCall = call;

        if (areLocalTonesEnabled) {
            Log.d(this, "Posting create.");
            getHandler().sendMessage(
                    getHandler().obtainMessage(EVENT_START_SESSION, Log.createSubsession()));
        }
    }

    /**
     * Releases resources needed for playing local dtmf tones.
     *
     * @param call The call associated with the session to end.
     */
    private void endDtmfSession(Call call) {
        if (call != null && mCall == call) {
            // Do a stopTone() in case the sessions ends before we are told to stop the tone.
            stopTone(call);

            mCall = null;
            Log.d(this, "Posting delete.");
            getHandler().sendMessage(
                    getHandler().obtainMessage(EVENT_END_SESSION, Log.createSubsession()));
        }
    }

    /**
     * Creates a new ToneHandler on a separate thread if none exists, and returns it.
     * No need for locking, since everything that calls this is protected by the Telecom lock.
     */
    @VisibleForTesting
    public ToneHandler getHandler() {
        if (mHandler == null) {
            HandlerThread thread = new HandlerThread("tonegenerator-dtmf");
            thread.start();
            mHandler = new ToneHandler(thread.getLooper());
        }
        return mHandler;
    }

    private static int getMappedTone(char digit) {
        if (digit >= '0' && digit <= '9') {
            return ToneGenerator.TONE_DTMF_0 + digit - '0';
        } else if (digit == '#') {
            return ToneGenerator.TONE_DTMF_P;
        } else if (digit == '*') {
            return ToneGenerator.TONE_DTMF_S;
        }
        return ToneGenerator.TONE_UNKNOWN;
    }
}
