package com.android.car.messenger.tts;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * Implementation of {@link TTSEngine} that delegates to Android's {@link TextToSpeech} API.
 * <p>
 * NOTE: {@link #initialize(Context, TextToSpeech.OnInitListener)} must be called to use this
 * engine. After {@link #shutdown()}, {@link #initialize(Context, TextToSpeech.OnInitListener)} may
 * be called again to use it again.
 */
class AndroidTTSEngine implements TTSEngine {
    private TextToSpeech mTextToSpeech;

    @Override
    public void initialize(Context context, TextToSpeech.OnInitListener initListener) {
        if (mTextToSpeech == null) {
            mTextToSpeech = new TextToSpeech(context, initListener);
        }
    }

    @Override
    public boolean isInitialized() {
        return mTextToSpeech != null;
    }

    @Override
    public void setOnUtteranceProgressListener(UtteranceProgressListener progressListener) {
        mTextToSpeech.setOnUtteranceProgressListener(progressListener);
    }

    @Override
    public int speak(CharSequence text, int queueMode, Bundle params, String utteranceId) {
        return mTextToSpeech.speak(text, queueMode, params, utteranceId);
    }

    @Override
    public void stop() {
        mTextToSpeech.stop();
    }

    @Override
    public boolean isSpeaking() {
        return mTextToSpeech != null ? mTextToSpeech.isSpeaking() : false;
    }

    @Override
    public void shutdown() {
        mTextToSpeech.shutdown();
        mTextToSpeech = null;
    }
}
