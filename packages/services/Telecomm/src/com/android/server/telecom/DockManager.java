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

package com.android.server.telecom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telecom.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Listens for and caches car dock state. */
@VisibleForTesting
public class DockManager {
    @VisibleForTesting
    public interface Listener {
        void onDockChanged(boolean isDocked);
    }

    /** Receiver for car dock plugged and unplugged events. */
    private class DockBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("DM.oR");
            try {
                if (Intent.ACTION_DOCK_EVENT.equals(intent.getAction())) {
                    int dockState = intent.getIntExtra(
                            Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED);
                    onDockChanged(dockState);
                }
            } finally {
                Log.endSession();
            }
        }
    }

    private final DockBroadcastReceiver mReceiver;

    /**
     * ConcurrentHashMap constructor params: 8 is initial table size, 0.9f is
     * load factor before resizing, 1 means we only expect a single thread to
     * access the map so make only a single shard
     */
    private final Set<Listener> mListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<Listener, Boolean>(8, 0.9f, 1));

    private int mDockState = Intent.EXTRA_DOCK_STATE_UNDOCKED;

    DockManager(Context context) {
        mReceiver = new DockBroadcastReceiver();

        // Register for misc other intent broadcasts.
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_DOCK_EVENT);
        context.registerReceiver(mReceiver, intentFilter);
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

    boolean isDocked() {
        switch (mDockState) {
            case Intent.EXTRA_DOCK_STATE_DESK:
            case Intent.EXTRA_DOCK_STATE_HE_DESK:
            case Intent.EXTRA_DOCK_STATE_LE_DESK:
            case Intent.EXTRA_DOCK_STATE_CAR:
                return true;
            default:
                return false;
        }
    }

    private void onDockChanged(int dockState) {
        if (mDockState != dockState) {
            Log.v(this, "onDockChanged: is docked?%b", dockState == Intent.EXTRA_DOCK_STATE_CAR);
            mDockState = dockState;
            for (Listener listener : mListeners) {
                listener.onDockChanged(isDocked());
            }
        }
    }

    /**
     * Dumps the state of the {@link DockManager}.
     *
     * @param pw The {@code IndentingPrintWriter} to write the state to.
     */
    public void dump(IndentingPrintWriter pw) {
        pw.println("mIsDocked: " + isDocked());
    }
}
