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
package android.support.car.media;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioRecord;
import android.support.annotation.RestrictTo;
import android.support.car.CarNotConnectedException;

import static android.support.annotation.RestrictTo.Scope.GROUP_ID;

/**
 * @hide
 */
@RestrictTo(GROUP_ID)
public class CarAudioManagerEmbedded extends CarAudioManager {

    private static final int MAX_BUFFER_SIZE_BYTE = 512 * 1024;
    private static final int SAMPLING_RATE = 16000;
    private static final AudioFormat AUDIO_RECORD_FORMAT = new AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setSampleRate(SAMPLING_RATE)
            .build();

    private final android.car.media.CarAudioManager mManager;

    public CarAudioManagerEmbedded(Object manager) {
        mManager = (android.car.media.CarAudioManager) manager;
    }

    @Override
    public AudioAttributes getAudioAttributesForCarUsage(@CarAudioUsage int carUsage)
            throws CarNotConnectedException {
        try {
            return mManager.getAudioAttributesForCarUsage(carUsage);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public int requestAudioFocus(OnAudioFocusChangeListener listener,
            AudioAttributes requestAttributes,
            int durationHint,
            int flags) throws CarNotConnectedException, IllegalArgumentException {
        try {
            return mManager.requestAudioFocus(listener, requestAttributes, durationHint, flags);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public int requestAudioFocus(OnAudioFocusChangeListener listener,
            AudioAttributes requestAttributes,
            int durationHint) throws CarNotConnectedException, IllegalArgumentException {
        try {
            return mManager.requestAudioFocus(listener, requestAttributes, durationHint,
                    0 /*flags*/);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void abandonAudioFocus(OnAudioFocusChangeListener listener, AudioAttributes aa) {
        mManager.abandonAudioFocus(listener, aa);
    }

    @Override
    public boolean isAudioRecordSupported() throws CarNotConnectedException {
        //always true in embedded
        return true;
    }

    @Override
    public AudioFormat getAudioRecordAudioFormat() throws CarNotConnectedException {
        return AUDIO_RECORD_FORMAT;
    }

    @Override
    public int getAudioRecordMinBufferSize() throws CarNotConnectedException {
        return AudioRecord.getMinBufferSize(SAMPLING_RATE, AUDIO_RECORD_FORMAT.getChannelMask(),
                AUDIO_RECORD_FORMAT.getEncoding());
    }

    @Override
    public int getAudioRecordMaxBufferSize() throws CarNotConnectedException {
        return Math.max(getAudioRecordMinBufferSize(), MAX_BUFFER_SIZE_BYTE);
    }

    @Override
    public CarAudioRecord createCarAudioRecord(int bufferSize)
            throws CarNotConnectedException, SecurityException {
        if (bufferSize < getAudioRecordMinBufferSize() ||
            bufferSize > getAudioRecordMaxBufferSize()) {
            throw new IllegalArgumentException("Bad bufferSize value");
        }
        return new CarAudioRecordEmbedded(AUDIO_RECORD_FORMAT, bufferSize);
    }

    @Override
    public boolean isMediaMuted() throws CarNotConnectedException {
        try {
            return mManager.isMediaMuted();
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public boolean setMediaMute(boolean mute) throws CarNotConnectedException {
        try {
            return mManager.setMediaMute(mute);
        } catch (android.car.CarNotConnectedException e) {
            throw new CarNotConnectedException(e);
        }
    }

    @Override
    public void onCarDisconnected() {
        //nothing to do
    }
}
