/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.VERBOSE;

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.util.Log;

import com.android.documentsui.base.Shared;
import com.android.documentsui.selection.BandController;

/**
 * A lock used by {@link DirectoryLoader} and {@link BandController} to ensure refresh is blocked
 * while Band Selection is active.
 */
public final class DirectoryReloadLock {
    private static final String TAG = "DirectoryReloadLock";

    private int mPauseCount = 0;
    private @Nullable Runnable mCallback;

    /**
     * Increment the block count by 1
     */
    @MainThread
    public void block() {
        Shared.checkMainLoop();
        mPauseCount++;
        if (VERBOSE) Log.v(TAG, "Block count increments to " + mPauseCount + ".");
    }

    /**
     * Decrement the block count by 1; If no other object is trying to block and there exists some
     * callback, that callback will be run
     */
    @MainThread
    public void unblock() {
        Shared.checkMainLoop();
        assert(mPauseCount > 0);
        mPauseCount--;
        if (VERBOSE) Log.v(TAG, "Block count decrements to " + mPauseCount + ".");
        if (mPauseCount == 0 && mCallback != null) {
            mCallback.run();
            mCallback = null;
        }
    }

    /**
     * Attempts to run the given Runnable if not-blocked, or else the Runnable is set to be ran next
     * (replacing any previous set Runnables).
     */
    public void tryUpdate(Runnable update) {
        if (mPauseCount == 0) {
            update.run();
        } else {
            mCallback = update;
        }
    }
}
