/*
 * Copyright (C) 2011 The Android Open Source Project
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
package android.speech.tts.cts;

import android.media.AudioFormat;
import android.os.ConditionVariable;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.TtsEngines;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Stub implementation of {@link TextToSpeechService}. Used for testing the
 * TTS engine API.
 */
public class StubTextToSpeechService extends TextToSpeechService {
    private static final String LOG_TAG = "StubTextToSpeechService";

    // Object that onSynthesizeText will #block on, if set to non-null
    public static volatile ConditionVariable sSynthesizeTextWait;

    private ArrayList<Locale> supportedLanguages = new ArrayList<Locale>();
    private ArrayList<Locale> supportedCountries = new ArrayList<Locale>();
    private ArrayList<Locale> GBFallbacks = new ArrayList<Locale>();

    public StubTextToSpeechService() {
        supportedLanguages.add(new Locale("eng"));
        supportedCountries.add(new Locale("eng", "USA"));
        supportedCountries.add(new Locale("eng", "GBR"));
        GBFallbacks.add(new Locale("eng", "NZL"));
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[] { "eng", "USA", "" };
    }

    @Override
    protected int onIsLanguageAvailable(String lang, String country, String variant) {
        if (supportedCountries.contains(new Locale(lang, country))) {
            return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        }
        if (supportedLanguages.contains(new Locale(lang))) {
            return TextToSpeech.LANG_AVAILABLE;
        }
 
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String lang, String country, String variant) {
        return onIsLanguageAvailable(lang, country, variant);
    }

    @Override
    protected void onStop() {
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        if (callback.start(16000, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS) {
            return;
        }

        final ConditionVariable synthesizeTextWait = sSynthesizeTextWait;
        if (synthesizeTextWait != null) {
            synthesizeTextWait.block(10000);  // 10s timeout
        }

        // Ten chunks with each a time point.
        for (int i = 0; i < 10; i++) {
            byte[] data = {0x01, 0x2};
            if (callback.audioAvailable(data, 0, data.length) != TextToSpeech.SUCCESS) {
                Log.i("TEST", "audioavailable returned not success");
                return;
            }
            callback.rangeStart(i * 5, i * 5 + 5, i * 10);
        }
        if (callback.done() != TextToSpeech.SUCCESS) {
            return;
        }
    }

    @Override
    public String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        Locale locale = new Locale(lang, country);
        if (supportedCountries.contains(locale)) {
          return TtsEngines.normalizeTTSLocale(locale).toLanguageTag();
        }
        if (lang.equals("eng")) {
            if (GBFallbacks.contains(new Locale(lang, country))) {
                return "en-GB";
            } else {
                return "en-US";
            }
        }
        return super.onGetDefaultVoiceNameFor(lang, country, variant);
    }

}
