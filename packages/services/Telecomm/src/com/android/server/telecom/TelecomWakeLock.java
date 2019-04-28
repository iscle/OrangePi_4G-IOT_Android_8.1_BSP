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

import android.content.Context;
import android.os.PowerManager;
import android.telecom.Log;

/**
 * Container for PowerManager / PowerManager.WakeLock access in telecom to facilitate unit testing.
 */
public class TelecomWakeLock {

    public class WakeLockAdapter {

        private PowerManager.WakeLock mWakeLock;

        public WakeLockAdapter(PowerManager.WakeLock wakeLock) {
            mWakeLock = wakeLock;
        }

        public void acquire() {
            mWakeLock.acquire();
        }

        public boolean isHeld() {
            return mWakeLock.isHeld();
        }

        public void release(int flags) {
            mWakeLock.release(flags);
        }

        public void setReferenceCounted(boolean isReferencedCounted){
            mWakeLock.setReferenceCounted(isReferencedCounted);
        }

    }

    private static final String TAG = "TelecomWakeLock";

    private Context mContext;
    private int mWakeLockLevel;
    private String mWakeLockTag;
    private WakeLockAdapter mWakeLock;

    public TelecomWakeLock(Context context, int wakeLockLevel, String wakeLockTag) {
        mContext = context;
        mWakeLockLevel = wakeLockLevel;
        mWakeLockTag = wakeLockTag;
        mWakeLock = getWakeLockFromPowerManager();
    }

    // Used For Testing
    public TelecomWakeLock(Context context, WakeLockAdapter wakeLockAdapter, int wakeLockLevel,
            String wakeLockTag) {
        mContext = context;
        mWakeLockLevel = wakeLockLevel;
        mWakeLockTag = wakeLockTag;
        mWakeLock = wakeLockAdapter;
    }

    private WakeLockAdapter getWakeLockFromPowerManager() {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        WakeLockAdapter adapter = null;
        if(powerManager.isWakeLockLevelSupported(mWakeLockLevel)) {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(mWakeLockLevel, mWakeLockTag);
            adapter = new WakeLockAdapter(wakeLock);
        }
        return adapter;
    }

    public boolean isHeld() {
        return mWakeLock != null && mWakeLock.isHeld();
    }

    public void acquire() {
        if(mWakeLock == null) {
            Log.i(TAG, "Can not acquire WakeLock (not supported) with level: " + mWakeLockLevel);
            return;
        }

        if (!isHeld()) {
            mWakeLock.acquire();
            Log.i(TAG, "Acquiring WakeLock with id: " + mWakeLockLevel);
        } else {
            Log.i(TAG, "WakeLock already acquired for id: " + mWakeLockLevel);
        }
    }

    public void release(int flags) {
        if (mWakeLock == null) {
            Log.i(TAG, "Can not release WakeLock (not supported) with id: " + mWakeLockLevel);
            return;
        }

        if (isHeld()) {
            mWakeLock.release(flags);
            Log.i(TAG, "Releasing WakeLock with id: " + mWakeLockLevel);
        } else {
            Log.i(TAG, "WakeLock already released with id: " + mWakeLockLevel);
        }
    }

    public void setReferenceCounted(boolean isReferencedCounted) {
        if (mWakeLock == null) {
            return;
        }
        mWakeLock.setReferenceCounted(isReferencedCounted);
    }

    @Override
    public String toString() {
        if(mWakeLock != null) {
            return mWakeLock.toString();
        } else {
            return "null";
        }
    }
}
