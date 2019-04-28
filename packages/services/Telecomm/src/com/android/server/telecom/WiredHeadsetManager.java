/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Listens for and caches headset state. */
@VisibleForTesting
public class WiredHeadsetManager {
    @VisibleForTesting
    public interface Listener {
        void onWiredHeadsetPluggedInChanged(boolean oldIsPluggedIn, boolean newIsPluggedIn);
    }

    /** Receiver for wired headset plugged and unplugged events. */
    private class WiredHeadsetCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            Log.startSession("WHC.oADA");
            try {
                updateHeadsetStatus();
            } finally {
                Log.endSession();
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) {
            Log.startSession("WHC.oADR");
            try {
                updateHeadsetStatus();
            } finally {
                Log.endSession();
            }
        }

        private void updateHeadsetStatus() {
            final boolean isPluggedIn = isWiredHeadsetPluggedIn();

            Log.i(WiredHeadsetManager.this, "ACTION_HEADSET_PLUG event, plugged in: %b, ",
                    isPluggedIn);
            onHeadsetPluggedInChanged(isPluggedIn);
        }
    }

    private final AudioManager mAudioManager;
    private boolean mIsPluggedIn;
    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<>(8, 0.9f, 1));

    public WiredHeadsetManager(Context context) {
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mIsPluggedIn = isWiredHeadsetPluggedIn();

        mAudioManager.registerAudioDeviceCallback(new WiredHeadsetCallback(), null);
    }

    @VisibleForTesting
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    @VisibleForTesting
    public boolean isPluggedIn() {
        return mIsPluggedIn;
    }

    private boolean isWiredHeadsetPluggedIn() {
        AudioDeviceInfo[] devices = mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        boolean isPluggedIn = false;
        for (AudioDeviceInfo device : devices) {
            switch (device.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                    isPluggedIn = true;
            }
            if (isPluggedIn) {
                break;
            }
        }
        return isPluggedIn;
    }

    private void onHeadsetPluggedInChanged(boolean isPluggedIn) {
        if (mIsPluggedIn != isPluggedIn) {
            Log.v(this, "onHeadsetPluggedInChanged, mIsPluggedIn: %b -> %b", mIsPluggedIn,
                    isPluggedIn);
            boolean oldIsPluggedIn = mIsPluggedIn;
            mIsPluggedIn = isPluggedIn;
            for (Listener listener : mListeners) {
                listener.onWiredHeadsetPluggedInChanged(oldIsPluggedIn, mIsPluggedIn);
            }
        }
    }

    /**
     * Dumps the state of the {@link WiredHeadsetManager}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("mIsPluggedIn: " + mIsPluggedIn);
    }
}
