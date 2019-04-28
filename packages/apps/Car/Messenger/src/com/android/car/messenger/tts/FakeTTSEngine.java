package com.android.car.messenger.tts;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.LinkedList;

/**
 * Fake implementation of {@link TTSEngine} for unit-testing.
 */
class FakeTTSEngine implements TTSEngine {
    TextToSpeech.OnInitListener mOnInitListener;
    UtteranceProgressListener mProgressListener;
    LinkedList<Request> mRequests = new LinkedList<>();

    @Override
    public void initialize(Context context, TextToSpeech.OnInitListener initListener) {
        mOnInitListener = initListener;
    }

    @Override
    public boolean isInitialized() {
        return mOnInitListener != null;
    }

    @Override
    public void setOnUtteranceProgressListener(UtteranceProgressListener progressListener) {
        mProgressListener = progressListener;
    }

    @Override
    public int speak(CharSequence text, int queueMode, Bundle params, String utteranceId) {
        mRequests.add(new Request(text, queueMode, params, utteranceId));
        return TextToSpeech.SUCCESS;
    }

    @Override
    public void stop() {
        mRequests.clear();
    }

    @Override
    public boolean isSpeaking() {
        // NOTE: currently not used in tests.
        return false;
    }

    @Override
    public void shutdown() {
        stop();
        mOnInitListener = null;
    }

    void startRequest(String utteranceId) {
        mProgressListener.onStart(utteranceId);
    }

    void finishRequest(String utteranceId) {
        removeRequest(utteranceId);
        mProgressListener.onDone(utteranceId);
    }

    void interruptRequest(String utteranceId, boolean interrupted) {
        removeRequest(utteranceId);
        mProgressListener.onStop(utteranceId, interrupted);
    }

    void failRequest(String utteranceId, int errorCode) {
        removeRequest(utteranceId);
        mProgressListener.onError(utteranceId, errorCode);
    }

    private void removeRequest(String utteranceId) {
        mRequests.removeIf((request) -> request.mUtteranceId.equals(utteranceId));
    }

    static class Request {
        CharSequence mText;
        int mQueueMode;
        Bundle mParams;
        String mUtteranceId;

        public Request(CharSequence text, int queueMode, Bundle params, String utteranceId) {
            mText = text;
            mQueueMode = queueMode;
            mParams = params;
            mUtteranceId = utteranceId;
        }
    }
}
