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
package android.graphics.cts;

import static android.opengl.EGL14.EGL_ALPHA_SIZE;
import static android.opengl.EGL14.EGL_BLUE_SIZE;
import static android.opengl.EGL14.EGL_CONFIG_CAVEAT;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_DEPTH_SIZE;
import static android.opengl.EGL14.EGL_GREEN_SIZE;
import static android.opengl.EGL14.EGL_HEIGHT;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_OPENGL_ES2_BIT;
import static android.opengl.EGL14.EGL_RED_SIZE;
import static android.opengl.EGL14.EGL_RENDERABLE_TYPE;
import static android.opengl.EGL14.EGL_SURFACE_TYPE;
import static android.opengl.EGL14.EGL_WIDTH;
import static android.opengl.EGL14.EGL_WINDOW_BIT;
import static android.opengl.EGL14.eglChooseConfig;
import static android.opengl.EGL14.eglCreateContext;
import static android.opengl.EGL14.eglCreatePbufferSurface;
import static android.opengl.EGL14.eglGetDisplay;
import static android.opengl.EGL14.eglInitialize;
import static android.opengl.EGL14.eglMakeCurrent;
import static android.opengl.GLES20.glDeleteTextures;
import static android.opengl.GLES20.glGenTextures;

import android.graphics.SurfaceTexture;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.Surface;

public final class RenderTarget {
    private static final int SETUP_THREAD = 1;
    private static final int CREATE_SINK = 2;
    private static final int DESTROY_SINK = 3;
    private static final int UPDATE_TEX_IMAGE = 4;

    private static final Handler sHandler;
    static {
        HandlerThread thread = new HandlerThread("RenderTarget-GL");
        thread.start();
        sHandler = new Handler(thread.getLooper(), new RenderTargetThread());
        sHandler.sendEmptyMessage(SETUP_THREAD);
    }

    public static RenderTarget create() {
        GenericFuture<RenderTarget> future = new GenericFuture<>();
        Message.obtain(sHandler, CREATE_SINK, future).sendToTarget();
        try {
            return future.get();
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to createSink()", e);
        }
    }

    private final SurfaceTexture mSurfaceTexture;
    private final int mGlTexId;
    private Surface mSurface;

    private RenderTarget(SurfaceTexture surfaceTexture, int glTexId) {
        mSurfaceTexture = surfaceTexture;
        mGlTexId = glTexId;
        mSurface = new Surface(mSurfaceTexture);
    }

    public Surface getSurface() {
        return mSurface;
    }

    public void setDefaultSize(int width, int height) {
        mSurfaceTexture.setDefaultBufferSize(width, height);
    }

    public void destroy() {
        mSurface = null;
        Message.obtain(sHandler, DESTROY_SINK, this).sendToTarget();
    }

    private static class RenderTargetThread implements Handler.Callback,
            SurfaceTexture.OnFrameAvailableListener {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case SETUP_THREAD:
                    setupThread();
                    return true;
                case CREATE_SINK:
                    createSink((GenericFuture<RenderTarget>) msg.obj);
                    return true;
                case DESTROY_SINK:
                    destroySink((RenderTarget) msg.obj);
                    return true;
                case UPDATE_TEX_IMAGE:
                    updateTexImage((SurfaceTexture) msg.obj);
                default:
                    return false;
            }
        }

        private void createSink(GenericFuture<RenderTarget> sinkFuture) {
            int[] tex = new int[1];
            glGenTextures(1, tex, 0);
            SurfaceTexture texture = new SurfaceTexture(tex[0]);
            texture.setOnFrameAvailableListener(this);
            sinkFuture.setResult(new RenderTarget(texture, tex[0]));
        }

        private void destroySink(RenderTarget sink) {
            sHandler.removeMessages(UPDATE_TEX_IMAGE, sink.mSurfaceTexture);
            sink.mSurfaceTexture.setOnFrameAvailableListener(null);
            sink.mSurfaceTexture.release();
            glDeleteTextures(1, new int[] { sink.mGlTexId }, 0);
        }

        private void updateTexImage(SurfaceTexture texture) {
            texture.updateTexImage();
        }

        private void setupThread() {
            EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
            if (display == null) {
                throw new IllegalStateException("eglGetDisplay failed");
            }
            int[] version = new int[2];
            if (!eglInitialize(display, version, 0, version, 1)) {
                throw new IllegalStateException("eglInitialize failed");
            }
            final int[] egl_attribs = new int[] {
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL_RED_SIZE, 8,
                    EGL_GREEN_SIZE, 8,
                    EGL_BLUE_SIZE, 8,
                    EGL_ALPHA_SIZE, 8,
                    EGL_DEPTH_SIZE, 0,
                    EGL_CONFIG_CAVEAT, EGL_NONE,
                    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                    EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] num_configs = new int[1];
            if (!eglChooseConfig(display, egl_attribs, 0, configs, 0, 1, num_configs, 0)
                    || num_configs[0] <= 0 || configs[0] == null) {
                throw new IllegalStateException("eglChooseConfig failed");
            }
            EGLConfig config = configs[0];
            final int[] gl_attribs = new int[] {
                    EGL_CONTEXT_CLIENT_VERSION, 2,
                    EGL_NONE
            };
            EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, gl_attribs, 0);
            if (context == null) {
                throw new IllegalStateException("eglCreateContext failed");
            }
            final int[] pbuffer_attribs = new int[] { EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE };
            EGLSurface pbuffer = eglCreatePbufferSurface(display, config, pbuffer_attribs, 0);
            if (pbuffer == null) {
                throw new IllegalStateException("create pbuffer surface failed");
            }
            if (!eglMakeCurrent(display, pbuffer, pbuffer, context)) {
                throw new IllegalStateException("Failed to make current");
            }
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            Message.obtain(sHandler, UPDATE_TEX_IMAGE, surfaceTexture).sendToTarget();
        }
    }

    private static class GenericFuture<T> {
        private boolean mHasResult = false;
        private T mResult;
        public void setResult(T result) {
            synchronized (this) {
                if (mHasResult) {
                    throw new IllegalStateException("Result already set");
                }
                mHasResult = true;
                mResult = result;
                notifyAll();
            }
        }

        public T get() throws InterruptedException {
            synchronized (this) {
                while (!mHasResult) {
                    wait();
                }
                return mResult;
            }
        }
    }
}