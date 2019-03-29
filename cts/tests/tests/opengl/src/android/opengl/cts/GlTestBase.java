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

package android.opengl.cts;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;

import static android.opengl.EGL14.EGL_HEIGHT;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_NO_DISPLAY;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.EGL_SUCCESS;
import static android.opengl.EGL14.EGL_WIDTH;
import static android.opengl.EGL14.eglCreatePbufferSurface;
import static android.opengl.EGL14.eglDestroySurface;
import static android.opengl.EGL14.eglGetError;
import static android.opengl.EGL14.eglMakeCurrent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * Test base for OpenGL ES 2+ tests. This test class initializes and
 * cleanup EGL before and after each test. Subclasses MUST NOT use
 * the AndroidJUnit4 runner but JUnit's BlockJUnit4ClassRunner to
 * guarantee that all methods are run on the same thread (this would
 * otherwise cause issues with EGL/GL's thread management).
 *
 * This implementation relies on EGL14. Do not use this class to
 * test EGL10.
 */
public class GlTestBase {
    private static EGLDisplay sEglDisplay;

    private EGLContext mEglContext;
    private EGLSurface mEglSurface;

    @Rule
    public Timeout mTimeout = new Timeout(2000);

    @BeforeClass
    public static void initEgl() {
        sEglDisplay = Egl14Utils.createEglDisplay();
        assertNotSame(EGL_NO_DISPLAY, sEglDisplay);
    }

    @AfterClass
    public static void terminateEgl() {
        Egl14Utils.releaseAndTerminate(sEglDisplay);
    }

    @Before
    public void createContext() {
        // Requesting OpenGL ES 2.0 context will return an ES 3.0 context on capable devices
        EGLConfig eglConfig = Egl14Utils.getEglConfig(sEglDisplay, 2);
        assertEquals(EGL_SUCCESS, eglGetError());

        mEglContext = Egl14Utils.createEglContext(sEglDisplay, eglConfig, 2);
        assertNotSame(EGL_NO_CONTEXT, eglConfig);

        mEglSurface = eglCreatePbufferSurface(sEglDisplay, eglConfig, new int[] {
                EGL_WIDTH, 1,
                EGL_HEIGHT, 1,
                EGL_NONE
        }, 0);
        assertNotSame(EGL_NO_SURFACE, mEglSurface);

        eglMakeCurrent(sEglDisplay, mEglSurface, mEglSurface, mEglContext);
        assertEquals(EGL_SUCCESS, eglGetError());
    }

    @After
    public void cleanupContext() {
        eglMakeCurrent(sEglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroySurface(sEglDisplay, mEglSurface);
        Egl14Utils.destroyEglContext(sEglDisplay, mEglContext);
    }
}
