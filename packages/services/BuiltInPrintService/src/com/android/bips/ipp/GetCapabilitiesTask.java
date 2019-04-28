/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.ipp;

import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.android.bips.jni.BackendConstants;
import com.android.bips.jni.LocalPrinterCapabilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** A background task that queries a specific URI for its complete capabilities */
class GetCapabilitiesTask extends AsyncTask<Void, Void, LocalPrinterCapabilities> {
    private static final String TAG = GetCapabilitiesTask.class.getSimpleName();
    private static final boolean DEBUG = false;

    /** Lock to ensure we don't issue multiple simultaneous capability requests */
    private static final Lock sJniLock = new ReentrantLock();

    private final Backend mBackend;
    private final Uri mUri;
    private final long mTimeout;

    GetCapabilitiesTask(Backend backend, Uri uri, long timeout) {
        mUri = uri;
        mBackend = backend;
        mTimeout = timeout;
    }

    private boolean isDeviceOnline(Uri uri) {
        try (Socket socket = new Socket()) {
            InetSocketAddress a = new InetSocketAddress(uri.getHost(), uri.getPort());
            socket.connect(a, (int) mTimeout);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    protected LocalPrinterCapabilities doInBackground(Void... dummy) {
        long start = System.currentTimeMillis();

        LocalPrinterCapabilities printerCaps = new LocalPrinterCapabilities();
        try {
            printerCaps.inetAddress = InetAddress.getByName(mUri.getHost());
        } catch (UnknownHostException e) {
            return null;
        }

        boolean online = isDeviceOnline(mUri);
        if (DEBUG) {
            Log.d(TAG, "isDeviceOnline uri=" + mUri + " online=" + online +
                    " (" + (System.currentTimeMillis() - start) + "ms)");
        }

        if (!online || isCancelled()) return null;

        // Do not permit more than a single call to this API or crashes may result
        sJniLock.lock();
        int status = -1;
        start = System.currentTimeMillis();
        try {
            if (isCancelled()) return null;
            status = mBackend.nativeGetCapabilities(Backend.getIp(mUri.getHost()),
                    mUri.getPort(), mUri.getPath(), mUri.getScheme(), mTimeout, printerCaps);
        } finally {
            sJniLock.unlock();
        }

        if (DEBUG) {
            Log.d(TAG, "callNativeGetCapabilities uri=" + mUri + " status=" + status +
                    " (" + (System.currentTimeMillis() - start) + "ms)");
        }

        return status == BackendConstants.STATUS_OK ? printerCaps : null;
    }
}