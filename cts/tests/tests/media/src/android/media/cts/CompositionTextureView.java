/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.cts;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

public class CompositionTextureView extends TextureView
    implements TextureView.SurfaceTextureListener {
    private static final String TAG = "CompositionTextureView";
    private static final boolean DBG = true;
    private static final boolean DBG_VERBOSE = false;

    private final Semaphore mInitWaitSemaphore = new Semaphore(0);
    private Surface mSurface;

    public CompositionTextureView(Context context) {
        super(context);
        if (DBG) {
            Log.i(TAG, "CompositionTextureView");
        }
    }

    public CompositionTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (DBG) {
            Log.i(TAG, "CompositionTextureView");
        }
    }

    public CompositionTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (DBG) {
            Log.i(TAG, "CompositionTextureView");
        }
    }

    public void startListening() {
        if (DBG) {
            Log.i(TAG, "startListening");
        }
        setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture arg0, int arg1, int arg2) {
        if (DBG) {
            Log.i(TAG, "onSurfaceTextureAvailable");
        }
        recreateSurface(arg0);
        mInitWaitSemaphore.release();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture arg0) {
        if (DBG) {
            Log.i(TAG, "onSurfaceTextureDestroyed");
        }
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture arg0, int arg1, int arg2) {
        if (DBG) {
            Log.i(TAG, "onSurfaceTextureSizeChanged");
        }
        recreateSurface(arg0);
        mInitWaitSemaphore.release();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture arg0) {
        if (DBG_VERBOSE) {
            Log.i(TAG, "onSurfaceTextureUpdated");
        }
        //ignore
    }

    public boolean waitForSurfaceReady(long timeoutMs) throws Exception {
        if (isSurfaceTextureAvailable()) {
            return true;
        }
        if (mInitWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            return true;
        }
        return isSurfaceTextureAvailable();
    }

    private boolean isSurfaceTextureAvailable() {
        SurfaceTexture surfaceTexture = getSurfaceTexture();
        if (mSurface == null && surfaceTexture != null) {
            recreateSurface(surfaceTexture);
        }
        return (mSurface != null);
    }

    private synchronized void recreateSurface(SurfaceTexture surfaceTexture) {
        if (mSurface != null) {
            mSurface.release();
        }
        mSurface = new Surface(surfaceTexture);
    }

    public Surface getSurface() {
        return mSurface;
    }

}
