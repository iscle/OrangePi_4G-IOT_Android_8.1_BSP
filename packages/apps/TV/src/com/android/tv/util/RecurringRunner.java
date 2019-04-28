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

package com.android.tv.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.android.tv.common.SharedPreferencesUtils;
import com.android.tv.common.SoftPreconditions;

import java.util.Date;

/**
 * Repeatedly executes a {@link Runnable}.
 *
 * <p>The next execution time is saved to a {@link SharedPreferences}, and used on the next start.
 * The given {@link Runnable} will run in the main thread.
 */
public final class RecurringRunner {
    private static final String TAG = "RecurringRunner";
    private static final boolean DEBUG = false;

    private final Handler mHandler;
    private final long mIntervalMs;
    private final Runnable mRunnable;
    private final Runnable mOnStopRunnable;
    private final Context mContext;
    private final String mName;
    private boolean mRunning;

    public RecurringRunner(Context context, long intervalMs, Runnable runnable,
            Runnable onStopRunnable) {
        mContext = context.getApplicationContext();
        mRunnable = runnable;
        mOnStopRunnable = onStopRunnable;
        mIntervalMs = intervalMs;
        mName = runnable.getClass().getCanonicalName();
        if (DEBUG) Log.i(TAG, " Delaying " + mName + " " + (intervalMs / 1000.0) + " seconds");
        mHandler = new Handler(mContext.getMainLooper());
    }

    public void start(boolean resetNextRunTime) {
        SoftPreconditions.checkState(!mRunning, TAG, mName + " start is called twice.");
        if (mRunning) {
            return;
        }
        mRunning = true;
        if (resetNextRunTime) {
            resetNextRunTime();
        }
        new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... params) {
                return getNextRunTime();
            }

            @Override
            protected void onPostExecute(Long nextRunTime) {
                postAt(nextRunTime);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void start() {
        start(false);
    }

    public void stop() {
        mRunning = false;
        mHandler.removeCallbacksAndMessages(null);
        if (mOnStopRunnable != null) {
            mOnStopRunnable.run();
        }
    }

    private void postAt(long next) {
        if (!mRunning) {
            return;
        }
        long now = System.currentTimeMillis();
        // Run it anyways even if it is in the past
        if (DEBUG) Log.i(TAG, "Next run of " + mName + " at " + new Date(next));
        long delay = Math.max(next - now, 0);
        boolean posted = mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (DEBUG) Log.i(TAG, "Starting " + mName);
                    mRunnable.run();
                } catch (Exception e) {
                    Log.w(TAG, "Error running " + mName, e);
                }
                postAt(resetNextRunTime());
            }
        }, delay);
        if (!posted) {
            Log.w(TAG, "Scheduling a future run of " + mName + " at " + new Date(next) + "failed");
        }
        if (DEBUG) Log.i(TAG, "Actual delay of " + mName + " is " + (delay / 1000.0) + " seconds.");
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(SharedPreferencesUtils.SHARED_PREF_RECURRING_RUNNER,
                Context.MODE_PRIVATE);
    }

    @WorkerThread
    private long getNextRunTime() {
        // The access to SharedPreferences is done by an AsyncTask thread because
        // SharedPreferences reads to disk at first time.
        long next = getSharedPreferences().getLong(mName, System.currentTimeMillis());
        if (next > System.currentTimeMillis() + mIntervalMs) {
            next = resetNextRunTime();
        }
        return next;
    }

    private long resetNextRunTime() {
        long next = System.currentTimeMillis() + mIntervalMs;
        getSharedPreferences().edit().putLong(mName, next).apply();
        return next;
    }
}
