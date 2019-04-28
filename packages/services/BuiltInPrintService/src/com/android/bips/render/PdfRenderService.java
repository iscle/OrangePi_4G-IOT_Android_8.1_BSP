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

package com.android.bips.render;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import com.android.bips.jni.SizeD;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Implements a PDF rendering service which can be run in an isolated process
 */
public class PdfRenderService extends Service {
    private static final String TAG = PdfRenderService.class.getSimpleName();
    private static final boolean DEBUG = false;

    /** How large of a chunk of Bitmap data to copy at once to the output stream */
    private static final int MAX_BYTES_PER_CHUNK = 1024 * 1024 * 5;

    private PdfRenderer mRenderer;
    private PdfRenderer.Page mPage;

    /** Lock held to protect against close() of current page during rendering. */
    private final Object mPageOpenLock = new Object();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        closeAll();
        return super.onUnbind(intent);
    }

    private final IPdfRender.Stub mBinder = new IPdfRender.Stub() {
        @Override
        public int openDocument(ParcelFileDescriptor pfd) throws RemoteException {
            if (!open(pfd)) return 0;
            return mRenderer.getPageCount();
        }

        @Override
        public SizeD getPageSize(int page) throws RemoteException {
            if (!openPage(page)) return null;
            return new SizeD(mPage.getWidth(), mPage.getHeight());
        }

        @Override
        public ParcelFileDescriptor renderPageStripe(int page, int y, int width, int height,
                double zoomFactor)
                throws RemoteException {
            if (!openPage(page)) return null;

            // Create a pipe with input and output sides
            ParcelFileDescriptor pipes[];
            try {
                pipes = ParcelFileDescriptor.createPipe();
            } catch (IOException e) {
                return null;
            }

            // Use a thread to spool out the bitmap data
            new RenderThread(mPage, y, width, height, zoomFactor, pipes[1]).start();

            // Return the corresponding input stream.
            return pipes[0];
        }

        @Override
        public void closeDocument() throws RemoteException {
            if (DEBUG) Log.d(TAG, "closeDocument");
            closeAll();
        }

        /**
         * Ensure the specified PDF file is open, closing the old file if necessary, and returning
         * true if successful.
         */
        private boolean open(ParcelFileDescriptor pfd) {
            closeAll();

            try {
                mRenderer = new PdfRenderer(pfd);
            } catch (IOException e) {
                Log.w(TAG, "Could not open file descriptor for rendering", e);
                return false;
            }
            return true;
        }

        /**
         * Ensure the specified PDF file and page are open, closing the old file if necessary, and
         * returning true if successful.
         */
        private boolean openPage(int page) {
            if (mRenderer == null) return false;

            // Close old page if this is a new page
            if (mPage != null && mPage.getIndex() != page) {
                closePage();
            }

            // Open new page if necessary
            if (mPage == null) {
                mPage = mRenderer.openPage(page);
            }
            return true;
        }
    };

    /** Close the current page if one is open */
    private void closePage() {
        if (mPage != null) {
            synchronized (mPageOpenLock) {
                mPage.close();
            }
            mPage = null;
        }
    }

    /**
     * Close the current page and file if open
     */
    private void closeAll() {
        closePage();

        if (mRenderer != null) {
            mRenderer.close();
            mRenderer = null;
        }
    }

    /**
     * Renders page data to RGB bytes and writes them to an output stream
     */
    private class RenderThread extends Thread {
        private final PdfRenderer.Page mPage;
        private final int mWidth;
        private final int mYOffset;
        private final int mHeight;
        private final double mZoomFactor;
        private final int mRowsPerStripe;
        private final ParcelFileDescriptor mOutput;
        private final ByteBuffer mBuffer;

        RenderThread(PdfRenderer.Page page, int y, int width, int height, double zoom,
                ParcelFileDescriptor output) {
            mPage = page;
            mWidth = width;
            mYOffset = y;
            mHeight = height;
            mZoomFactor = zoom;
            mOutput = output;

            // Buffer will temporarily hold RGBA data from Bitmap
            mRowsPerStripe = MAX_BYTES_PER_CHUNK / mWidth / 4;
            mBuffer = ByteBuffer.allocate(mWidth * mRowsPerStripe * 4);
        }

        @Override
        public void run() {
            Bitmap bitmap = null;

            // Make sure nobody closes page while we're using it
            synchronized(mPageOpenLock) {
                try (OutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                        mOutput)) {
                    if (mPage == null) {
                        // If page was closed before we synchronized, this closes the outputStream
                        Log.e(TAG, "Page lost");
                        return;
                    }
                    // Allocate and clear bitmap to white with no transparency
                    bitmap = Bitmap.createBitmap(mWidth, mRowsPerStripe, Bitmap.Config.ARGB_8888);

                    // Render each stripe to output
                    for (int startRow = mYOffset; startRow < mYOffset + mHeight; startRow +=
                            mRowsPerStripe) {
                        int stripeRows = Math.min(mRowsPerStripe, (mYOffset + mHeight) - startRow);
                        renderToBitmap(startRow, bitmap);
                        writeRgb(bitmap, stripeRows, outputStream);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write", e);
                } finally {
                    if (bitmap != null) bitmap.recycle();
                }
            }
        }

        /** From the specified starting row, render from the current page into the target bitmap */
        private void renderToBitmap(int startRow, Bitmap bitmap) {
            Matrix matrix = new Matrix();
            // The scaling matrix increases DPI (default is 72dpi) to page output
            matrix.setScale((float) mZoomFactor, (float) mZoomFactor);
            // The translate specifies adjusts which part of the page we are rendering
            matrix.postTranslate(0, 0 - startRow);
            bitmap.eraseColor(0xFFFFFFFF);

            mPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
        }

        /** Copy rows of RGB bytes from the bitmap to the output stream */
        private void writeRgb(Bitmap bitmap, int rows, OutputStream out)
                throws IOException {
            mBuffer.clear();
            bitmap.copyPixelsToBuffer(mBuffer);
            int alphaPixelSize = mWidth * rows * 4;

            // Chop out the alpha byte
            byte array[] = mBuffer.array();
            int from, to;
            for (from = 0, to = 0; from < alphaPixelSize; from += 4, to += 3) {
                array[to] = array[from];
                array[to + 1] = array[from + 1];
                array[to + 2] = array[from + 2];
            }

            // Write it
            out.write(mBuffer.array(), 0, to);
        }
    }
}