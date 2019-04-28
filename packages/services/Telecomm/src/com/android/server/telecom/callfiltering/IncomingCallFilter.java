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
 * limitations under the License
 */

package com.android.server.telecom.callfiltering;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Log;
import android.telecom.Logging.Runnable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.telecom.Call;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

import java.util.List;

public class IncomingCallFilter implements CallFilterResultCallback {

    public interface CallFilter {
        void startFilterLookup(Call call, CallFilterResultCallback listener);
    }

    private final TelecomSystem.SyncRoot mTelecomLock;
    private final Context mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<CallFilter> mFilters;
    private final Call mCall;
    private final CallFilterResultCallback mListener;
    private final Timeouts.Adapter mTimeoutsAdapter;

    private CallFilteringResult mResult = new CallFilteringResult(
            true, // shouldAllowCall
            false, // shouldReject
            true, // shouldAddToCallLog
            true // shouldShowNotification
    );

    private boolean mIsPending = true;
    private int mNumPendingFilters;

    public IncomingCallFilter(Context context, CallFilterResultCallback listener, Call call,
            TelecomSystem.SyncRoot lock, Timeouts.Adapter timeoutsAdapter,
            List<CallFilter> filters) {
        mContext = context;
        mListener = listener;
        mCall = call;
        mTelecomLock = lock;
        mFilters = filters;
        mNumPendingFilters = filters.size();
        mTimeoutsAdapter = timeoutsAdapter;
    }

    public void performFiltering() {
        Log.addEvent(mCall, LogUtils.Events.FILTERING_INITIATED);
        for (CallFilter filter : mFilters) {
            filter.startFilterLookup(mCall, this);
        }
        // synchronized to prevent a race on mResult and to enter into Telecom.
        mHandler.postDelayed(new Runnable("ICF.pFTO", mTelecomLock) { // performFiltering time-out
            @Override
            public void loggedRun() {
                if (mIsPending) {
                    Log.i(IncomingCallFilter.this, "Call filtering has timed out.");
                    Log.addEvent(mCall, LogUtils.Events.FILTERING_TIMED_OUT);
                    mListener.onCallFilteringComplete(mCall, mResult);
                    mIsPending = false;
                }
            }
        }.prepare(), mTimeoutsAdapter.getCallScreeningTimeoutMillis(mContext.getContentResolver()));
    }

    public void onCallFilteringComplete(Call call, CallFilteringResult result) {
        synchronized (mTelecomLock) { // synchronizing to prevent race on mResult
            mNumPendingFilters--;
            mResult = result.combine(mResult);
            if (mNumPendingFilters == 0) {
                // synchronized on mTelecomLock to enter into Telecom.
                mHandler.post(new Runnable("ICF.oCFC", mTelecomLock) {
                    @Override
                    public void loggedRun() {
                        if (mIsPending) {
                            Log.addEvent(mCall, LogUtils.Events.FILTERING_COMPLETED, mResult);
                            mListener.onCallFilteringComplete(mCall, mResult);
                            mIsPending = false;
                        }
                    }
                }.prepare());
            }
        }
    }

    /**
     * Returns the handler, for testing purposes.
     */
    @VisibleForTesting
    public Handler getHandler() {
        return mHandler;
    }
}
