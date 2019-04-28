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

package com.android.car.messenger.tts;

import android.content.Context;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Component that wraps platform TTS engine and supports play-out of batches of text.
 * <p>
 * It takes care of setting up TTS Engine when text is played out and shutting it down after an idle
 * period with no play-out. This is desirable since owning app is long-lived and TTS Engine brings
 * up another service-process.
 * <p>
 * As batch of text is played-out, it issues callbacks on {@link Listener} provided with the batch.
 */
public class TTSHelper {
    /**
     * Listener interface used by clients to be notified as batch of text is played out.
     */
    public interface Listener {
        /**
         * Called when play-out starts for batch. May never get called if batch has errors or
         * interruptions.
         */
         void onTTSStarted();

        /**
         * Called when play-out ends for batch.
         *
         * @param error Whether play-out ended due to an error or not. Not if it was aborted, its
         *              not considered an error.
         */
        void onTTSStopped(boolean error);
    }

    private static final String TAG = "Messenger.TTSHelper";
    private static boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final char UTTERANCE_ID_SEPARATOR = ';';
    private static final long DEFAULT_SHUTDOWN_DELAY_MILLIS = 60 * 1000;

    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final long mShutdownDelayMillis;
    private TTSEngine mTTSEngine;
    private int mInitStatus;
    private SpeechRequest mPendingRequest;
    private final Map<String, BatchListener> mListeners = new HashMap<>();
    private String currentBatchId;

    /**
     * Construct with default settings.
     */
    public TTSHelper(Context context) {
        this(context, new AndroidTTSEngine(), DEFAULT_SHUTDOWN_DELAY_MILLIS);
    }

    @VisibleForTesting
    TTSHelper(Context context, TTSEngine ttsEngine, long shutdownDelayMillis) {
        mContext = context;
        mTTSEngine = ttsEngine;
        mShutdownDelayMillis = shutdownDelayMillis;
        // OnInitListener will only set to SUCCESS/ERROR. So we initialize to STOPPED.
        mInitStatus = TextToSpeech.STOPPED;
    }

    private void initMaybeAndKeepAlive() {
        if (!mTTSEngine.isInitialized()) {
            if (DBG) {
                Log.d(TAG, "Initializing TTS Engine");
            }
            mTTSEngine.initialize(mContext, this::handleInitCompleted);
            mTTSEngine.setOnUtteranceProgressListener(mProgressListener);
        }
        // Since we're handling a request, delay engine shutdown.
        mHandler.removeCallbacks(mMaybeShutdownRunnable);
        mHandler.postDelayed(mMaybeShutdownRunnable, mShutdownDelayMillis);
    }

    private void handleInitCompleted(int initStatus) {
        if (DBG) {
            Log.d(TAG, "init completed: " + initStatus);
        }
        mInitStatus = initStatus;
        if (mPendingRequest != null) {
            playInternal(mPendingRequest.mTextToSpeak, mPendingRequest.mListener);
            mPendingRequest = null;
        }
    }

    private final Runnable mMaybeShutdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (mListeners.isEmpty() || mPendingRequest == null) {
                shutdownEngine();
            } else {
                mHandler.postDelayed(this, mShutdownDelayMillis);
            }
        }
    };

    /**
     * Plays out given batch of text. If engine is not active, it is setup and the request is stored
     * until then. Only one batch is supported at a time; If a previous batch is waiting engine
     * setup, that batch is dropped. If a previous batch is playing, the play-out is stopped and
     * next one is passed to the TTS Engine. Callbacks are issued on the provided {@code listener}.
     *
     * NOTE: Underlying engine may have limit on length of text in each element of the batch; it
     * will reject anything longer. See {@link TextToSpeech#getMaxSpeechInputLength()}.
     *
     * @param textToSpeak Batch of text to play-out.
     * @param listener Observer that will receive callbacks about play-out progress.
     */
    public void requestPlay(List<CharSequence> textToSpeak, Listener listener) {
        if (textToSpeak == null || textToSpeak.size() < 1) {
            throw new IllegalArgumentException("Empty/null textToSpeak");
        }
        initMaybeAndKeepAlive();

        // Check if its still initializing.
        if (mInitStatus == TextToSpeech.STOPPED) {
            // Squash any already queued request.
            if (mPendingRequest != null) {
                mPendingRequest.mListener.onTTSStopped(false /* error */);
            }
            mPendingRequest = new SpeechRequest(textToSpeak, listener);
        } else {
            playInternal(textToSpeak, listener);
        }
    }

    public void requestStop() {
        mTTSEngine.stop();
        currentBatchId = null;
    }

    public boolean isSpeaking() {
        return mTTSEngine.isSpeaking();
    }

    private void playInternal(List<CharSequence> textToSpeak, Listener listener) {
        if (mInitStatus == TextToSpeech.ERROR) {
            Log.e(TAG, "TTS setup failed!");
            mHandler.post(() -> listener.onTTSStopped(true /* error */));
            return;
        }

        // Abort anything currently playing and flushes queue.
        mTTSEngine.stop();

        // Queue up new batch. We assign id's = "batchId:index" where index decrements from
        // batchSize - 1 down to 0. If queueing fails, we abort the whole batch.
        currentBatchId = Integer.toString(listener.hashCode());
        int index = textToSpeak.size() - 1;
        for (CharSequence text : textToSpeak) {
            String utteranceId =
                    String.format("%s%c%d", currentBatchId, UTTERANCE_ID_SEPARATOR, index);
            if (DBG) {
                Log.d(TAG, String.format("Queueing tts: '%s' [%s]", text, utteranceId));
            }
            if (mTTSEngine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
                    != TextToSpeech.SUCCESS) {
                mTTSEngine.stop();
                currentBatchId = null;
                Log.e(TAG, "Queuing text failed!");
                mHandler.post(() -> listener.onTTSStopped(true /* error */));
                return;
            }
            index--;
        }
        // Register BatchListener for entire batch. Will invoke callbacks on Listener as batch
        // progresses.
        mListeners.put(currentBatchId, new BatchListener(listener));
    }

    /**
     * Releases resources and shuts down TTS Engine.
     */
    public void cleanup() {
        mHandler.removeCallbacksAndMessages(null /* token */);
        shutdownEngine();
    }

    private void shutdownEngine() {
        if (mTTSEngine.isInitialized()) {
            if (DBG) {
                Log.d(TAG, "Shutting down TTS Engine");
            }
            mTTSEngine.stop();
            mTTSEngine.shutdown();
            mInitStatus = TextToSpeech.STOPPED;
        }
    }

    private static Pair<String, Integer> parse(String utteranceId) {
        int separatorIndex = utteranceId.indexOf(UTTERANCE_ID_SEPARATOR);
        String batchId = utteranceId.substring(0, separatorIndex);
        int index = Integer.parseInt(utteranceId.substring(separatorIndex + 1));
        return Pair.create(batchId, index);
    }

    // Handles all callbacks from TTSEngine. Possible order of callbacks:
    // - onStart, onDone: successful play-out.
    // - onStart, onStop: play-out starts, but interrupted.
    // - onStart, onError: play-out starts and fails.
    // - onStop: play-out never starts, but aborted.
    // - onError: play-out never starts, but fails.
    // Since the callbacks arrive on other threads, they are dispatched onto mHandler where the
    // appropriate BatchListener is invoked.
    private final UtteranceProgressListener mProgressListener = new UtteranceProgressListener() {
        private void safeInvokeAsync(String utteranceId,
                BiConsumer<BatchListener, Pair<String, Integer>> callback) {
            mHandler.post(() -> {
                Pair<String, Integer> parsedId = parse(utteranceId);
                BatchListener listener = mListeners.get(parsedId.first);
                if (listener != null) {
                    callback.accept(listener, parsedId);
                } else {
                    if (DBG) {
                        Log.d(TAG, "Missing batch listener: " + utteranceId);
                    }
                }
            });
        }

        @Override
        public void onStart(String utteranceId) {
            if (DBG) {
                Log.d(TAG, "TTS onStart: " + utteranceId);
            }
            safeInvokeAsync(utteranceId, BatchListener::onStart);
        }

        @Override
        public void onDone(String utteranceId) {
            if (DBG) {
                Log.d(TAG, "TTS onDone: " + utteranceId);
            }
            safeInvokeAsync(utteranceId, BatchListener::onDone);
        }

        @Override
        public void onStop(String utteranceId, boolean interrupted) {
            if (DBG) {
                Log.d(TAG, "TTS onStop: " + utteranceId);
            }
            safeInvokeAsync(utteranceId, BatchListener::onStop);
        }

        @Override
        public void onError(String utteranceId) {
            if (DBG) {
                Log.d(TAG, "TTS onError: " + utteranceId);
            }
            safeInvokeAsync(utteranceId, BatchListener::onError);
        }
    };

    /**
     * Handles callbacks for a single batch of TTS text and issues callbacks on wrapped
     * {@link Listener} that client is listening on.
     */
    private class BatchListener {
        private final Listener mListener;
        private boolean mBatchStarted = false;

        BatchListener(Listener listener) {
            mListener = listener;
        }

        // Issues Listener.onTTSStarted when first item of batch starts.
        void onStart(Pair<String, Integer> parsedId) {
            if (!mBatchStarted) {
                mBatchStarted = true;
                mListener.onTTSStarted();
            }
        }

        // Issues Listener.onTTSStopped when last item of batch finishes.
        void onDone(Pair<String, Integer> parsedId) {
            if (parsedId.second == 0) {
                handleBatchFinished(parsedId, false /* error */);
            }
        }

        // If any item of batch fails, abort the batch and issue Listener.onTTSStopped.
        void onError(Pair<String, Integer> parsedId) {
            if (parsedId.first.equals(currentBatchId)) {
                mTTSEngine.stop();
            }
            handleBatchFinished(parsedId, true /* error */);
        }

        // If any item of batch is preempted (rest should also be), issue Listener.onTTSStopped.
        void onStop(Pair<String, Integer> parsedId) {
            handleBatchFinished(parsedId, false /* error */);
        }

        // Handles terminal callbacks for the batch. We invoke stopped and remove ourselves.
        // No further callbacks will be handled for the batch.
        private void handleBatchFinished(Pair<String, Integer> parsedId, boolean error) {
            mListener.onTTSStopped(error);
            mListeners.remove(parsedId.first);
        }
    }

    private static class SpeechRequest {
        final List<CharSequence> mTextToSpeak;
        final Listener mListener;

        SpeechRequest(List<CharSequence> textToSpeak, Listener listener) {
            mTextToSpeak = textToSpeak;
            mListener = listener;
        }
    }
}
