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

package android.view.cts;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class TextureViewCtsActivity extends Activity implements SurfaceTextureListener {
    private final static long TIME_OUT_MS = 10000;
    private final Object mLock = new Object();

    private View mPreview;
    private TextureView mTextureView;
    private HandlerThread mGLThreadLooper;
    private Handler mGLThread;
    private CountDownLatch mEnterAnimationFence = new CountDownLatch(1);

    private SurfaceTexture mSurface;
    private int mSurfaceUpdatedCount;

    static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    static final int EGL_OPENGL_ES2_BIT = 4;

    private EGL10 mEgl;
    private EGLDisplay mEglDisplay;
    private EGLConfig mEglConfig;
    private EGLContext mEglContext;
    private EGLSurface mEglSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mGLThreadLooper == null) {
            mGLThreadLooper = new HandlerThread("GLThread");
            mGLThreadLooper.start();
            mGLThread = new Handler(mGLThreadLooper.getLooper());
        }

        View preview = new View(this);
        preview.setBackgroundColor(Color.WHITE);
        mPreview = preview;
        mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(this);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.BLACK);
        content.addView(mTextureView,
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        content.addView(mPreview,
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        setContentView(content);
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        mEnterAnimationFence.countDown();
    }

    public void waitForEnterAnimationComplete() throws TimeoutException, InterruptedException {
        if (!mEnterAnimationFence.await(TIME_OUT_MS, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException();
        }
    }

    private class RunSignalAndCatch implements Runnable {
        public Throwable error;
        private Runnable mRunnable;
        private CountDownLatch mFence;

        RunSignalAndCatch(Runnable run, CountDownLatch fence) {
            mRunnable = run;
            mFence = fence;
        }

        @Override
        public void run() {
            try {
                mRunnable.run();
            } catch (Throwable t) {
                error = t;
            } finally {
                mFence.countDown();
            }
        }
    }

    private void runOnGLThread(Runnable r) throws Throwable {
        CountDownLatch fence = new CountDownLatch(1);
        RunSignalAndCatch wrapper = new RunSignalAndCatch(r, fence);
        mGLThread.post(wrapper);
        if (!fence.await(TIME_OUT_MS, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException();
        }
        if (wrapper.error != null) {
            throw wrapper.error;
        }
    }

    public void waitForSurface() throws InterruptedException {
        synchronized (mLock) {
            while (mSurface == null) {
                mLock.wait(TIME_OUT_MS);
            }
        }
    }

    public void initGl() throws Throwable {
        if (mEglSurface != null) return;
        runOnGLThread(mDoInitGL);
    }

    public void drawColor(int color) throws Throwable {
        runOnGLThread(() -> {
            glClearColor(Color.red(color) / 255.0f,
                    Color.green(color) / 255.0f,
                    Color.blue(color) / 255.0f,
                    Color.alpha(color) / 255.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            if (!mEgl.eglSwapBuffers(mEglDisplay, mEglSurface)) {
                throw new RuntimeException("Cannot swap buffers");
            }
        });
    }

    public void finishGL() throws Throwable {
        if (mEglSurface == null) return;
        runOnGLThread(() -> doFinishGL());
    }

    public int waitForSurfaceUpdateCount(int updateCount) throws InterruptedException {
        synchronized (mLock) {
            while (updateCount > mSurfaceUpdatedCount) {
                mLock.wait(TIME_OUT_MS);
            }
            return mSurfaceUpdatedCount;
        }
    }

    public void removeCover() {
        mPreview.setVisibility(View.GONE);
    }

    private void doFinishGL() {
        mEgl.eglDestroyContext(mEglDisplay, mEglContext);
        mEglContext = null;
        mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        mEglSurface = null;
        mEgl.eglTerminate(mEglDisplay);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        synchronized (mLock) {
            mSurface = surface;
            mLock.notifyAll();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        synchronized (mLock) {
            mSurface = null;
            mLock.notifyAll();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        synchronized (mLock) {
            mSurfaceUpdatedCount++;
            mLock.notifyAll();
        }
    }

    private Runnable mDoInitGL = new Runnable() {
        @Override
        public void run() {
            mEgl = (EGL10) EGLContext.getEGL();

            mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (mEglDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("eglGetDisplay failed "
                        + GLUtils.getEGLErrorString(mEgl.eglGetError()));
            }

            int[] version = new int[2];
            if (!mEgl.eglInitialize(mEglDisplay, version)) {
                throw new RuntimeException("eglInitialize failed " +
                        GLUtils.getEGLErrorString(mEgl.eglGetError()));
            }

            mEglConfig = chooseEglConfig();
            if (mEglConfig == null) {
                throw new RuntimeException("eglConfig not initialized");
            }

            mEglContext = createContext(mEgl, mEglDisplay, mEglConfig);

            mEglSurface = mEgl.eglCreateWindowSurface(mEglDisplay, mEglConfig,
                    mSurface, null);

            if (mEglSurface == null || mEglSurface == EGL10.EGL_NO_SURFACE) {
                int error = mEgl.eglGetError();
                throw new RuntimeException("createWindowSurface failed "
                        + GLUtils.getEGLErrorString(error));
            }

            if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
                throw new RuntimeException("eglMakeCurrent failed "
                        + GLUtils.getEGLErrorString(mEgl.eglGetError()));
            }
        }
    };

    EGLContext createContext(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig) {
        int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
        return egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
    }

    private EGLConfig chooseEglConfig() {
        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = getConfig();
        if (!mEgl.eglChooseConfig(mEglDisplay, configSpec, configs, 1, configsCount)) {
            throw new IllegalArgumentException("eglChooseConfig failed " +
                    GLUtils.getEGLErrorString(mEgl.eglGetError()));
        } else if (configsCount[0] > 0) {
            return configs[0];
        }
        return null;
    }

    private int[] getConfig() {
        return new int[] {
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };
    }
}
