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

package com.android.bips.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.util.Log;

import java.util.LinkedList;

/**
 * Nsd resolve requests for the same info cancel each other. Hence this class synchronizes the
 * resolutions to hide this effect.
 */
class NsdResolveQueue {
    private static final String TAG = NsdResolveQueue.class.getSimpleName();
    private static final boolean DEBUG = false;

    /** Lock for {@link #sInstance} */
    private static final Object sLock = new Object();

    /** Instance of this singleton */
    private static NsdResolveQueue sInstance;

    /** Current set of registered service info resolve attempts */
    final LinkedList<NsdResolveRequest> mResolveRequests = new LinkedList<>();

    private final Handler mMainHandler;

    public static NsdResolveQueue getInstance(Context context) {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new NsdResolveQueue(context);
            }
            return sInstance;
        }
    }

    private NsdResolveQueue(Context context) {
        mMainHandler = new Handler(context.getMainLooper());
    }

    /**
     * Resolve a serviceInfo or queue the request if there is a request currently in flight.
     *
     * @param nsdManager  The nsd manager to use
     * @param serviceInfo The service info to resolve
     * @param listener    The listener to call back once the info is resolved.
     */
    public void resolve(NsdManager nsdManager, NsdServiceInfo serviceInfo,
            NsdManager.ResolveListener listener) {
        if (DEBUG) {
            Log.d(TAG, "Adding resolve of " + serviceInfo.getServiceName() + " to queue size=" +
                    mResolveRequests.size());
        }
        mResolveRequests.addLast(new NsdResolveRequest(nsdManager, serviceInfo, listener));
        if (mResolveRequests.size() == 1) {
            resolveNextRequest();
        }
    }

    /** Immediately reject all unstarted requests */
    void clear() {
        while (mResolveRequests.size() > 1) {
            mResolveRequests.remove(1);
        }
    }

    /**
     * Resolve the next request if there is one.
     */
    private void resolveNextRequest() {
        if (!mResolveRequests.isEmpty()) {
            mResolveRequests.getFirst().start();
        }
    }

    /**
     * Holds a request to resolve a {@link NsdServiceInfo}
     */
    private class NsdResolveRequest implements NsdManager.ResolveListener {
        final NsdManager nsdManager;
        final NsdServiceInfo serviceInfo;
        final NsdManager.ResolveListener listener;
        private long mStartTime;

        private NsdResolveRequest(NsdManager nsdManager,
                NsdServiceInfo serviceInfo,
                NsdManager.ResolveListener listener) {
            this.nsdManager = nsdManager;
            this.serviceInfo = serviceInfo;
            this.listener = listener;
        }

        public void start() {
            mStartTime = System.currentTimeMillis();
            if (DEBUG) Log.d(TAG, "resolveService " + serviceInfo.getServiceName());
            nsdManager.resolveService(serviceInfo, this);
        }

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            if (DEBUG) {
                Log.d(TAG, "onResolveFailed " + serviceInfo.getServiceName() + " errorCode=" +
                        errorCode + " (" + (System.currentTimeMillis() - mStartTime) + " ms)");
            }
            mMainHandler.post(() -> {
                listener.onResolveFailed(serviceInfo, errorCode);
                mResolveRequests.pop();
                resolveNextRequest();
            });
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            if (DEBUG) {
                Log.d(TAG, "onServiceResolved " + serviceInfo.getServiceName() +
                        " (" + (System.currentTimeMillis() - mStartTime) + " ms)");
            }
            mMainHandler.post(() -> {
                listener.onServiceResolved(serviceInfo);
                mResolveRequests.pop();
                resolveNextRequest();
            });
        }
    }
}