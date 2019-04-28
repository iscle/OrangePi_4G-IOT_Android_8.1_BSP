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
 * limitations under the License
 */

package com.android.server.telecom;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.telecom.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Provides various system states to the rest of the telecom codebase. So far, that's only car-mode.
 */
public class SystemStateProvider {

    public static interface SystemStateListener {
        public void onCarModeChanged(boolean isCarMode);
    }

    private final Context mContext;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("SSP.oR");
            try {
                String action = intent.getAction();
                if (UiModeManager.ACTION_ENTER_CAR_MODE.equals(action)) {
                    onEnterCarMode();
                } else if (UiModeManager.ACTION_EXIT_CAR_MODE.equals(action)) {
                    onExitCarMode();
                } else {
                    Log.w(this, "Unexpected intent received: %s", intent.getAction());
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private Set<SystemStateListener> mListeners = new CopyOnWriteArraySet<>();
    private boolean mIsCarMode;

    public SystemStateProvider(Context context) {
        mContext = context;

        IntentFilter intentFilter = new IntentFilter(UiModeManager.ACTION_ENTER_CAR_MODE);
        intentFilter.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
        Log.i(this, "Registering car mode receiver: %s", intentFilter);

        mIsCarMode = getSystemCarMode();
    }

    public void addListener(SystemStateListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    public boolean removeListener(SystemStateListener listener) {
        return mListeners.remove(listener);
    }

    public boolean isCarMode() {
        return mIsCarMode;
    }

    private void onEnterCarMode() {
        if (!mIsCarMode) {
            Log.i(this, "Entering carmode");
            mIsCarMode = true;
            notifyCarMode();
        }
    }

    private void onExitCarMode() {
        if (mIsCarMode) {
            Log.i(this, "Exiting carmode");
            mIsCarMode = false;
            notifyCarMode();
        }
    }

    private void notifyCarMode() {
        for (SystemStateListener listener : mListeners) {
            listener.onCarModeChanged(mIsCarMode);
        }
    }

    /**
     * Checks the system for the current car mode.
     *
     * @return True if in car mode, false otherwise.
     */
    private boolean getSystemCarMode() {
        UiModeManager uiModeManager =
                (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);

        if (uiModeManager != null) {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR;
        }

        return false;
    }
}
