package com.android.car.messenger.tts;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * Interface for TTS Engine that closely matches {@link TextToSpeech}; facilitates mocking/faking.
 */
public interface TTSEngine {
    /**
     * Initializes engine.
     *
     * @param context Context to use.
     * @param initListener Listener to monitor initialization result.
     */
    void initialize(Context context, TextToSpeech.OnInitListener initListener);

    /**
     * @return Whether engine is already initialized.
     */
    boolean isInitialized();

    /**
     * @see TextToSpeech#setOnUtteranceProgressListener(UtteranceProgressListener)
     */
    void setOnUtteranceProgressListener(UtteranceProgressListener progressListener);

    /**
     * @see TextToSpeech#speak(CharSequence, int, Bundle, String)
     */
    int speak(CharSequence text, int queueMode, Bundle params, String utteranceId);

    /**
     * @see TextToSpeech#stop()
     */
    void stop();

    /**
     * @return Whether TTS is playing out currently.
     */
    boolean isSpeaking();

    /**
     * Un-initialize engine and release resources.
     * {@link #initialize(Context, TextToSpeech.OnInitListener)} will need to be called again before
     * using this engine.
     */
    void shutdown();
}
