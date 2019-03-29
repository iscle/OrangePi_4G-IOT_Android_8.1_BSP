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

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to test EGL APIs.
 */
final class Egl14Utils {
    private Egl14Utils() {
    }

    static int getMajorVersion() {
        // Section 6.1.5 of the OpenGL ES specification indicates the GL version
        // string strictly follows this format:
        //
        // OpenGL<space>ES<space><version number><space><vendor-specific information>
        //
        // In addition section 6.1.5 describes the version number thusly:
        //
        // "The version number is either of the form major number.minor number or
        // major number.minor number.release number, where the numbers all have one
        // or more digits. The release number and vendor specific information are
        // optional."
        String version = GLES20.glGetString(GLES20.GL_VERSION);
        Pattern pattern = Pattern.compile("OpenGL ES ([0-9]+)\\.([0-9]+)");
        Matcher matcher = pattern.matcher(version);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 2;
    }

    /**
     * Returns an initialized default display.
     */
    static EGLDisplay createEglDisplay() {
        EGLDisplay eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new IllegalStateException("no EGL display");
        }

        int[] major = new int[1];
        int[] minor = new int[1];
        if (!EGL14.eglInitialize(eglDisplay, major, 0, minor, 0)) {
            throw new IllegalStateException("error in eglInitialize");
        }

        return eglDisplay;
    }

    /**
     * Returns a new GL ES 2.0 context for the specified {@code eglDisplay}.
     */
    static EGLContext createEglContext(EGLDisplay eglDisplay) {
        return createEglContext(eglDisplay, getEglConfig(eglDisplay, 2), 2);
    }

    /**
     * Returns a new GL ES context for the specified display, config and version.
     */
    static EGLContext createEglContext(EGLDisplay eglDisplay, EGLConfig eglConfig, int version) {
        int[] contextAttributes = { EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL14.EGL_NONE };
        return EGL14.eglCreateContext(eglDisplay, eglConfig,
                EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
    }

    /**
     * Destroys the GL context identified by {@code eglDisplay} and {@code eglContext}.
     */
    static void destroyEglContext(EGLDisplay eglDisplay, EGLContext eglContext) {
        EGL14.eglMakeCurrent(eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT);
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("error releasing context: " + error);
        }

        EGL14.eglDestroyContext(eglDisplay, eglContext);
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("error destroying context: " + error);
        }
    }

    static void releaseAndTerminate(EGLDisplay eglDisplay) {
        int error;
        EGL14.eglReleaseThread();
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("error releasing thread: " + error);
        }

        EGL14.eglTerminate(eglDisplay);
        error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            throw new RuntimeException("error terminating display: " + error);
        }
    }

    static EGLConfig getEglConfig(EGLDisplay eglDisplay, int version) {
        // Get an EGLConfig.
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        if (version == 3) {
            renderableType = EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        }
        final int RED_SIZE = 8;
        final int GREEN_SIZE = 8;
        final int BLUE_SIZE = 8;
        final int ALPHA_SIZE = 8;
        final int DEPTH_SIZE = 0;
        final int STENCIL_SIZE = 0;
        final int[] DEFAULT_CONFIGURATION = new int[] {
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_RED_SIZE, RED_SIZE,
                EGL14.EGL_GREEN_SIZE, GREEN_SIZE,
                EGL14.EGL_BLUE_SIZE, BLUE_SIZE,
                EGL14.EGL_ALPHA_SIZE, ALPHA_SIZE,
                EGL14.EGL_DEPTH_SIZE, DEPTH_SIZE,
                EGL14.EGL_STENCIL_SIZE, STENCIL_SIZE,
                EGL14.EGL_NONE};

        int[] configsCount = new int[1];
        EGLConfig[] eglConfigs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(
                eglDisplay, DEFAULT_CONFIGURATION, 0, eglConfigs, 0, 1, configsCount, 0)) {
            throw new RuntimeException("eglChooseConfig failed");
        }
        return eglConfigs[0];
    }

    /**
     * Checks for a GL error using {@link GLES20#glGetError()}.
     *
     * @throws RuntimeException if there is a GL error
     */
    static void checkGlError() {
        int errorCode;
        if ((errorCode = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            throw new RuntimeException("gl error: " + Integer.toHexString(errorCode));
        }
    }
}
