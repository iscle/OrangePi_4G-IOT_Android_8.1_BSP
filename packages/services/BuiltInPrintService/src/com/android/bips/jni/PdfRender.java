/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2015-2016 Mopria Alliance, Inc.
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

package com.android.bips.jni;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.android.bips.render.IPdfRender;
import com.android.bips.render.PdfRenderService;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Renders pages of a PDF into an RGB buffer. For security, relies on a remote rendering service.
 */
public class PdfRender {
    private static final String TAG = PdfRender.class.getSimpleName();
    private static final boolean DEBUG = false;

    /** The current singleton instance */
    private static PdfRender sInstance;

    private final Context mContext;
    private final Intent mIntent;
    private IPdfRender mService;
    private String mCurrentFile;

    /**
     * Returns the PdfRender singleton, creating it if necessary.
     */
    public static PdfRender getInstance(Context context) {
        // Native code might call this without a context
        if (sInstance == null && context != null) {
            synchronized(PdfRender.class) {
                sInstance = new PdfRender(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if (DEBUG) Log.d(TAG, "service connected");
            mService = IPdfRender.Stub.asInterface(service);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "PdfRender service unexpectedly disconnected, reconnecting");
            mService = null;
            mContext.bindService(mIntent, this, Context.BIND_AUTO_CREATE);
        }
    };

    private PdfRender(Context context) {
        mContext = context;
        mIntent = new Intent(context, PdfRenderService.class);
        context.bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /** Shut down the PDF renderer */
    public void close() {
        mContext.unbindService(mConnection);
        mService = null;
        sInstance = null;
    }

    /**
     * Opens the specified document, returning the page count or 0 on error. (Called by native
     * code.)
     */
    private int openDocument(String fileName) {
        if (DEBUG) Log.d(TAG, "openDocument() " + fileName);
        if (mService == null) return 0;

        if (mCurrentFile != null && !mCurrentFile.equals(fileName)) {
            closeDocument();
        }

        try {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(new File(fileName),
                    ParcelFileDescriptor.MODE_READ_ONLY);
            return mService.openDocument(pfd);
        } catch (RemoteException | FileNotFoundException ex) {
            Log.w(TAG, "Failed to open " + fileName, ex);
            return 0;
        }
    }

    /**
     * Returns the size of the specified page or null on error. (Called by native code.)
     * @param page 0-based page
     * @return width and height of page in points (1/72")
     */
    public SizeD getPageSize(int page) {
        if (DEBUG) Log.d(TAG, "getPageSize() page=" + page);
        if (mService == null) return null;

        try {
            return mService.getPageSize(page - 1);
        } catch (RemoteException | IllegalArgumentException ex) {
            Log.w(TAG, "getPageWidth failed", ex);
            return null;
        }
    }

    /**
     * Renders the content of the page. (Called by native code.)
     * @param page 0-based page
     * @param y y-offset onto page
     * @param width width of area to render
     * @param height height of area to render
     * @param zoomFactor zoom factor to use when rendering data
     * @param target target byte buffer to fill with results
     * @return true if rendering was successful
     */
    public boolean renderPageStripe(int page, int y, int width, int height,
            double zoomFactor, ByteBuffer target) {
        if (DEBUG) {
            Log.d(TAG, "renderPageStripe() page=" + page + " y=" + y + " w=" + width +
                    " h=" + height + " zoom=" + zoomFactor);
        }
        if (mService == null) return false;

        try {
            long start = System.currentTimeMillis();
            ParcelFileDescriptor input = mService.renderPageStripe(page - 1, y, width, height,
                    zoomFactor);

            // Copy received data into the ByteBuffer
            int expectedSize = width * height * 3;
            byte[] readBuffer = new byte[128 * 1024];
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(input)) {
                int length;
                while((length = in.read(readBuffer, 0, readBuffer.length)) > 0) {
                    target.put(readBuffer, 0, length);
                }
            }
            if (target.position() != expectedSize) {
                Log.w(TAG, "Render failed: expected " + target.position() + ", got " +
                        expectedSize + " bytes");
                return false;
            }

            if (DEBUG) Log.d(TAG, "Received (" + (System.currentTimeMillis() - start) + "ms)");
            return true;
        } catch (RemoteException | IOException | IllegalArgumentException | OutOfMemoryError ex) {
            Log.w(TAG, "Render failed", ex);
            return false;
        }
    }

    /**
     * Releases any open resources for the current document and page.
     */
    public void closeDocument() {
        if (DEBUG) Log.d(TAG, "closeDocument()");
        if (mService == null) return;

        try {
            mService.closeDocument();
        } catch (RemoteException ignored) {
        }
    }
}