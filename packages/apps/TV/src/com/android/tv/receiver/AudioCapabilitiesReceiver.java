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
package com.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.android.tv.ApplicationSingletons;
import com.android.tv.TvApplication;
import com.android.tv.analytics.Analytics;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.SharedPreferencesUtils;

/**
 * Creates HDMI plug broadcast receiver, and reports AC3 passthrough capabilities to Google
 * Analytics and listeners. Call {@link #register} to start receiving notifications, and
 * {@link #unregister} to stop.
 */
public final class AudioCapabilitiesReceiver {
    private static final String SETTINGS_KEY_AC3_PASSTHRU_REPORTED = "ac3_passthrough_reported";
    private static final String SETTINGS_KEY_AC3_PASSTHRU_CAPABILITIES = "ac3_passthrough";
    private static final String SETTINGS_KEY_AC3_REPORT_REVISION = "ac3_report_revision";

    // AC3 capabilities stat is sent to Google Analytics just once in order to avoid
    // duplicated stat reports since it doesn't change over time in most cases.
    // Increase this revision when we should force the stat to be sent again.
    // TODO: Consier using custom metrics.
    private static final int REPORT_REVISION = 1;

    private final Context mContext;
    private final Analytics mAnalytics;
    private final Tracker mTracker;
    @Nullable
    private final OnAc3PassthroughCapabilityChangeListener mListener;
    private final BroadcastReceiver mReceiver = new HdmiAudioPlugBroadcastReceiver();

    /**
     * Constructs a new audio capabilities receiver.
     *
     * @param context context for registering to receive broadcasts
     * @param listener listener which receives AC3 passthrough capability change notification
     */
    public AudioCapabilitiesReceiver(@NonNull Context context,
            @Nullable OnAc3PassthroughCapabilityChangeListener listener) {
        mContext = context;
        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        mAnalytics = appSingletons.getAnalytics();
        mTracker = appSingletons.getTracker();
        mListener = listener;
    }

    public void register() {
        mContext.registerReceiver(mReceiver, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
    }

    public void unregister() {
        mContext.unregisterReceiver(mReceiver);
    }

    private final class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
                return;
            }
            boolean supported = false;
            int[] supportedEncodings = intent.getIntArrayExtra(AudioManager.EXTRA_ENCODINGS);
            if (supportedEncodings != null) {
                for (int supportedEncoding : supportedEncodings) {
                    if (supportedEncoding == AudioFormat.ENCODING_AC3) {
                        supported = true;
                        break;
                    }
                }
            }
            if (mListener != null) {
                mListener.onAc3PassthroughCapabilityChange(supported);
            }
            if (!mAnalytics.isAppOptOut()) {
                reportAudioCapabilities(supported);
            }
        }
    }

    private void reportAudioCapabilities(boolean ac3Supported) {
        boolean oldVal = getBoolean(SETTINGS_KEY_AC3_PASSTHRU_CAPABILITIES, false);
        boolean reported = getBoolean(SETTINGS_KEY_AC3_PASSTHRU_REPORTED, false);
        int revision = getInt(SETTINGS_KEY_AC3_REPORT_REVISION, 0);

        // Send the value just once. But we send it again if the value changed, to include
        // the case where users have switched TV device with different AC3 passthrough capabilities.
        if (!reported || oldVal != ac3Supported || REPORT_REVISION > revision) {
            mTracker.sendAc3PassthroughCapabilities(ac3Supported);
            setBoolean(SETTINGS_KEY_AC3_PASSTHRU_REPORTED, true);
            setBoolean(SETTINGS_KEY_AC3_PASSTHRU_CAPABILITIES, ac3Supported);
            if (REPORT_REVISION > revision) {
                setInt(SETTINGS_KEY_AC3_REPORT_REVISION, REPORT_REVISION);
            }
        }
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(SharedPreferencesUtils.SHARED_PREF_AUDIO_CAPABILITIES,
                Context.MODE_PRIVATE);
    }

    private boolean getBoolean(String key, boolean def) {
        return getSharedPreferences().getBoolean(key, def);
    }

    private void setBoolean(String key, boolean val) {
        getSharedPreferences().edit().putBoolean(key, val).apply();
    }

    private int getInt(String key, int def) {
        return getSharedPreferences().getInt(key, def);
    }

    private void setInt(String key, int val) {
        getSharedPreferences().edit().putInt(key, val).apply();
    }

    /**
     * Listener notified when AC3 passthrough capability changes.
     */
    public interface OnAc3PassthroughCapabilityChangeListener {
        /**
         * Called when the AC3 passthrough capability changes.
         */
        void onAc3PassthroughCapabilityChange(boolean capability);
    }
}
