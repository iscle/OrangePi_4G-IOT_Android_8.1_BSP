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

package com.android.compatibility.common.util;

import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.Window;
import android.view.PixelCopy.OnPixelCopyFinishedListener;
import android.view.SurfaceView;

public class SynchronousPixelCopy implements OnPixelCopyFinishedListener {
    private static Handler sHandler;
    static {
        HandlerThread thread = new HandlerThread("PixelCopyHelper");
        thread.start();
        sHandler = new Handler(thread.getLooper());
    }

    private int mStatus = -1;

    public int request(Surface source, Bitmap dest) {
        synchronized (this) {
            PixelCopy.request(source, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(Surface source, Rect srcRect, Bitmap dest) {
        synchronized (this) {
            PixelCopy.request(source, srcRect, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(SurfaceView source, Bitmap dest) {
        synchronized (this) {
            PixelCopy.request(source, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(SurfaceView source, Rect srcRect, Bitmap dest) {
        synchronized (this) {
            PixelCopy.request(source, srcRect, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(Window source, Bitmap dest) {
        synchronized (this) {
            PixelCopy.request(source, dest, this, sHandler);
            return getResultLocked();
        }
    }

    public int request(Window source, Rect srcRect, Bitmap dest) {
        synchronized (this) {
            PixelCopy.request(source, srcRect, dest, this, sHandler);
            return getResultLocked();
        }
    }

    private int getResultLocked() {
        try {
            this.wait(250);
        } catch (InterruptedException e) {
            fail("PixelCopy request didn't complete within 250ms");
        }
        return mStatus;
    }

    @Override
    public void onPixelCopyFinished(int copyResult) {
        synchronized (this) {
            mStatus = copyResult;
            this.notify();
        }
    }
}
