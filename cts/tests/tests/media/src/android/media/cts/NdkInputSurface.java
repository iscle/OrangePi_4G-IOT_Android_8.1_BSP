/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.media.MediaCodec;
import android.util.Log;

public class NdkInputSurface implements InputSurfaceInterface {

    private static final String TAG = NdkInputSurface.class.getName();

    private long mNativeWindow;
    private long mEGLDisplay;
    private long mEGLConfig;
    private long mEGLContext;
    private long mEGLSurface;
    private int mWidth, mHeight;

    static private native long eglGetDisplay();
    static private native long eglChooseConfig(long eglDisplay);
    static private native long eglCreateContext(long eglDisplay, long eglConfig);
    static private native long createEGLSurface(long eglDisplay, long eglConfig, long nativeWindow);
    static private native boolean eglMakeCurrent(long eglDisplay, long eglSurface, long eglContext);
    static private native boolean eglSwapBuffers(long eglDisplay, long eglSurface);
    static private native boolean eglPresentationTimeANDROID(long eglDisplay, long eglSurface, long nsecs);
    static private native int eglGetWidth(long eglDisplay, long eglSurface);
    static private native int eglGetHeight(long eglDisplay, long eglSurface);
    static private native boolean eglDestroySurface(long eglDisplay, long eglSurface);
    static private native void nativeRelease(long eglDisplay, long eglSurface, long eglContext, long nativeWindow);

    public NdkInputSurface(long nativeWindow) {

        mNativeWindow = nativeWindow;

        mEGLDisplay = eglGetDisplay();
        if (mEGLDisplay == 0) {
            throw new RuntimeException("unable to get EGL14 display");
        }

        mEGLConfig = eglChooseConfig(mEGLDisplay);
        if (mEGLConfig == 0) {
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }

        mEGLContext = eglCreateContext(mEGLDisplay, mEGLConfig);
        if (mEGLContext == 0) {
            throw new RuntimeException("null context");
        }

        mEGLSurface = createEGLSurface(mEGLDisplay, mEGLConfig, mNativeWindow);
        if (mEGLSurface == 0) {
            throw new RuntimeException("surface was null");
        }

        mWidth = eglGetWidth(mEGLDisplay, mEGLSurface);
        mHeight = eglGetHeight(mEGLDisplay, mEGLSurface);

    }

    @Override
    public void makeCurrent() {
        if (!eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    @Override
    public boolean swapBuffers() {
        return eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }

    @Override
    public void setPresentationTime(long nsecs) {
        eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
    }

    @Override
    public void configure(MediaCodec codec) {
        throw new UnsupportedOperationException(codec.toString());
    }

    @Override
    public void configure(NdkMediaCodec codec) {
        codec.setInputSurface(mNativeWindow);
    }

    @Override
    public void updateSize(int width, int height) {
        if (width != mWidth || height != mHeight) {
            Log.d(TAG, "re-create EGLSurface");
            releaseEGLSurface();
            mEGLSurface = createEGLSurface(mEGLDisplay, mEGLConfig, mNativeWindow);
            mWidth = eglGetWidth(mEGLDisplay, mEGLSurface);
            mHeight = eglGetHeight(mEGLDisplay, mEGLSurface);
        }
    }

    private void releaseEGLSurface() {
        if (mEGLDisplay != 0) {
            eglDestroySurface(mEGLDisplay, mEGLSurface);
            mEGLSurface = 0;
        }
    }

    @Override
    public void release() {

        nativeRelease(mEGLDisplay, mEGLSurface, mEGLContext, mNativeWindow);

        mEGLDisplay = 0;
        mEGLContext = 0;
        mEGLSurface = 0;
        mNativeWindow = 0;

    }

}
