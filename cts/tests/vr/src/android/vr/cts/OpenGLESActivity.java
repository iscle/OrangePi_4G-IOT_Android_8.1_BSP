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
package android.vr.cts;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import java.lang.InterruptedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class OpenGLESActivity extends Activity {
    private static final String TAG = "OpenGLESActivity";

    public static final String EXTRA_VIEW_INDEX = "viewIndex";
    public static final String EXTRA_PROTECTED = "protected";
    public static final String EXTRA_PRIORITY = "priority";
    public static final String EXTRA_MUTABLE = "mutable";
    public static final String EXTRA_LATCH_COUNT = "latchCount";


    public static final int EGL_PROTECTED_CONTENT_EXT = 0x32C0;

    // EGL extension enums are not exposed in Java.
    public static final int EGL_CONTEXT_PRIORITY_LEVEL_IMG = 0x3100;
    public static final int EGL_MUTABLE_RENDER_BUFFER_BIT = 0x1000;
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x40;

    public static final int RENDERER_BASIC = 1;
    public static final int RENDERER_PROTECTEDTEXTURES = 2;
    public static final int RENDERER_REFRESHRATE = 3;

    OpenGLES20View mView;
    Renderer mRenderer;
    int mRendererType;
    private CountDownLatch mLatch;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        int viewIndex = getIntent().getIntExtra(EXTRA_VIEW_INDEX, -1);
        int protectedAttribute = getIntent().getIntExtra(EXTRA_PROTECTED, -1);
        int priorityAttribute = getIntent().getIntExtra(EXTRA_PRIORITY, -1);
        int mutableAttribute =  getIntent().getIntExtra(EXTRA_MUTABLE, 0);
        int latchCount = getIntent().getIntExtra(EXTRA_LATCH_COUNT, 1);
        mLatch = new CountDownLatch(latchCount);
        mView = new OpenGLES20View(this, viewIndex, protectedAttribute, priorityAttribute,
            mutableAttribute, mLatch);

        setContentView(mView);
    }

    public int glGetError() {
        return ((RendererBasicTest)mRenderer).mError;
    }

    public static void checkEglError(String msg) {
        boolean failed = false;
        int error;
        while ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            Log.e(TAG, msg + ": EGL error: 0x" + Integer.toHexString(error));
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("EGL error encountered (EGL error: 0x" +
                Integer.toHexString(error) + ")");
        }
    }

    public void runOnGlThread(Runnable r) throws Throwable {
        CountDownLatch fence = new CountDownLatch(1);
        RunSignalAndCatch wrapper = new RunSignalAndCatch(r, fence);

        mView.queueEvent(wrapper);
        fence.await(5000, TimeUnit.MILLISECONDS);
        if (wrapper.error != null) {
            throw wrapper.error;
        }
    }

    public static boolean contextHasAttributeWithValue(int attribute, int value) {
        int[] values = new int[1];
        EGL14.eglQueryContext(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
            EGL14.eglGetCurrentContext(), attribute, values, 0);
        checkEglError("eglQueryContext");
        return values[0] == value;
    }

    public static boolean surfaceHasAttributeWithValue(int attribute, int value) {
        int[] values = new int[1];
        EGL14.eglQuerySurface(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), attribute, values, 0);
        checkEglError("eglQuerySurface");
        return values[0] == value;
    }

    public static void setSurfaceAttribute(int attribute, int value) {
        int[] values = new int[1];
        EGL14.eglSurfaceAttrib(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW), attribute, value);
        checkEglError("eglSurfaceAttrib");
    }

    public boolean waitForFrameDrawn() {
        boolean result = false;
        try {
            result = mLatch.await(2L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Ignore the exception and return false below.
        }
        return result;
    }

    public boolean supportsVrHighPerformance() {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mView != null) {
            mView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mView != null) {
            mView.onResume();
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

    class OpenGLES20View extends GLSurfaceView {

        public OpenGLES20View(Context context, int renderer, int protectedAttribute,
            int priorityAttribute, int mutableAttribute, CountDownLatch latch) {
            super(context);
            setEGLContextClientVersion(2);

            if (protectedAttribute == 1) {
                setEGLContextFactory(new ProtectedContextFactory());
                setEGLWindowSurfaceFactory(new ProtectedWindowSurfaceFactory());
            } else if (priorityAttribute != 0) {
                setEGLContextFactory(new PriorityContextFactory(priorityAttribute));
            } else if (mutableAttribute != 0 && supportsVrHighPerformance()) {
                setEGLConfigChooser(new MutableEGLConfigChooser());
            }


            if (renderer == RENDERER_BASIC) {
                mRenderer = new RendererBasicTest(latch);
            } else if (renderer == RENDERER_PROTECTEDTEXTURES) {
                mRenderer = new RendererProtectedTexturesTest(latch);
            } else if (renderer == RENDERER_REFRESHRATE) {
                mRenderer = new RendererRefreshRateTest(latch);
            } else {
                throw new RuntimeException();
            }
            setRenderer(mRenderer);
        }

        @Override
        public void setEGLContextClientVersion(int version) {
            super.setEGLContextClientVersion(version);
        }
    }

    private class PriorityContextFactory implements GLSurfaceView.EGLContextFactory {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private int mEGLContextClientVersion = 2;

        private int mPriority;

        PriorityContextFactory(int priorityAttribute) {
            super();
            mPriority = priorityAttribute;
        }

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            boolean highPerf = supportsVrHighPerformance();
            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion,
                highPerf ? EGL_CONTEXT_PRIORITY_LEVEL_IMG : EGL10.EGL_NONE,
                highPerf ? mPriority : EGL10.EGL_NONE, EGL10.EGL_NONE };

            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                attrib_list);
            if (context == EGL10.EGL_NO_CONTEXT) {
                Log.e(TAG, "Error creating EGL context.");
            }
            checkEglError("eglCreateContext");
            return context;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
                Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            }
          }
    }

    private class ProtectedContextFactory implements GLSurfaceView.EGLContextFactory {
        private int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private int mEGLContextClientVersion = 2;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            boolean highPerf = supportsVrHighPerformance();
            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, mEGLContextClientVersion,
                highPerf ? EGL_PROTECTED_CONTENT_EXT : EGL10.EGL_NONE,
                highPerf ? EGL14.EGL_TRUE : EGL10.EGL_NONE, EGL10.EGL_NONE };

            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT,
                attrib_list);
            if (context == EGL10.EGL_NO_CONTEXT) {
              Log.e(TAG, "Error creating EGL context.");
            }
            checkEglError("eglCreateContext");
            return context;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            if (!egl.eglDestroyContext(display, context)) {
              Log.e("DefaultContextFactory", "display:" + display + " context: " + context);
            }
          }
    }

    private class ProtectedWindowSurfaceFactory implements GLSurfaceView.EGLWindowSurfaceFactory {

        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display,
                                              EGLConfig config, Object nativeWindow) {
          EGLSurface result = null;
          try {
              boolean highPerf = supportsVrHighPerformance();
              int[] attrib_list = { highPerf ? EGL_PROTECTED_CONTENT_EXT : EGL10.EGL_NONE,
                      highPerf ? EGL14.EGL_TRUE : EGL10.EGL_NONE, EGL10.EGL_NONE };
              result = egl.eglCreateWindowSurface(display, config, nativeWindow, attrib_list);
              checkEglError("eglCreateWindowSurface");
          } catch (IllegalArgumentException e) {
              Log.e(TAG, "eglCreateWindowSurface", e);
          }
          return result;
        }

        public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
        }
    }

    private class MutableEGLConfigChooser implements GLSurfaceView.EGLConfigChooser {
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] configSpec = new int[] {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 16,
                EGL10.EGL_STENCIL_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT | EGL_MUTABLE_RENDER_BUFFER_BIT,
                EGL10.EGL_NONE
            };

            int[] num_config = new int[1];
            if (!egl.eglChooseConfig(display, configSpec, null, 0, num_config)) {
                throw new IllegalArgumentException("eglChooseConfig failed");
            }

            int numConfigs = num_config[0];
            if (numConfigs <= 0) {
                throw new IllegalArgumentException("No configs match configSpec");
            }

            EGLConfig[] configs = new EGLConfig[numConfigs];
            if (!egl.eglChooseConfig(display, configSpec, configs, numConfigs,
                    num_config)) {
                throw new IllegalArgumentException("eglChooseConfig#2 failed");
            }
            EGLConfig config = chooseConfig(egl, display, configs);
            if (config == null) {
                throw new IllegalArgumentException("No config chosen");
            }
            return config;
        }
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display,
                EGLConfig[] configs) {
            for (EGLConfig config : configs) {
                int d = findConfigAttrib(egl, display, config,
                        EGL10.EGL_DEPTH_SIZE, 0);
                int s = findConfigAttrib(egl, display, config,
                        EGL10.EGL_STENCIL_SIZE, 0);

                // We need at least mDepthSize and mStencilSize bits
                if (d < 16 || s < 8)
                    continue;

                // We want an *exact* match for red/green/blue/alpha
                int r = findConfigAttrib(egl, display, config,
                        EGL10.EGL_RED_SIZE, 0);
                int g = findConfigAttrib(egl, display, config,
                            EGL10.EGL_GREEN_SIZE, 0);
                int b = findConfigAttrib(egl, display, config,
                            EGL10.EGL_BLUE_SIZE, 0);
                int a = findConfigAttrib(egl, display, config,
                        EGL10.EGL_ALPHA_SIZE, 0);

                int mask = findConfigAttrib(egl, display, config,
                        EGL10.EGL_SURFACE_TYPE, 0);

                if (r == 8 && g == 8 && b == 8 && a == 8 &&
                        (mask & EGL_MUTABLE_RENDER_BUFFER_BIT) != 0)
                    return config;
            }
            return null;
        }

        private int findConfigAttrib(EGL10 egl, EGLDisplay display,
                EGLConfig config, int attribute, int defaultValue) {

            int[] value = new int[1];
            if (egl.eglGetConfigAttrib(display, config, attribute, value)) {
                return value[0];
            }
            return defaultValue;
        }
    }
}
