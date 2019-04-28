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
package com.android.car.test;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;

final class AudioTestUtils {
    private AudioTestUtils() {}

    static int doRequestFocus(
            AudioManager audioManager,
            OnAudioFocusChangeListener listener,
            int streamType,
            int androidFocus) {
        AudioAttributes.Builder attributesBuilder = new AudioAttributes.Builder();
        attributesBuilder.setLegacyStreamType(streamType);
        return doRequestFocus(audioManager, listener, attributesBuilder.build(), androidFocus);
    }

    static int doRequestFocus(
            AudioManager audioManager,
            OnAudioFocusChangeListener listener,
            AudioAttributes attributes,
            int androidFocus) {
        return doRequestFocus(audioManager, listener, attributes, androidFocus, false);
    }

    static int doRequestFocus(
        AudioManager audioManager,
        OnAudioFocusChangeListener listener,
        AudioAttributes attributes,
        int androidFocus,
        boolean acceptsDelayedFocus) {
        AudioFocusRequest.Builder focusBuilder = new AudioFocusRequest.Builder(androidFocus);
        focusBuilder.setOnAudioFocusChangeListener(listener).setAcceptsDelayedFocusGain(
                acceptsDelayedFocus);
        focusBuilder.setAudioAttributes(attributes);

        return audioManager.requestAudioFocus(focusBuilder.build());
    }
}
