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

package android.hardware.cts;

import android.hardware.HardwareBuffer;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.opengl.EGL14.EGL_HEIGHT;
import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.EGL_WIDTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test {@link HardwareBuffer}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HardwareBufferTest {
    private static boolean sHasFloatBuffers;

    @SuppressWarnings("SameParameterValue")
    private static native HardwareBuffer nativeCreateHardwareBuffer(int width, int height,
            int format, int layers, long usage);
    private static native void nativeReleaseHardwareBuffer(HardwareBuffer hardwareBufferObj);

    static {
        System.loadLibrary("ctshardware_jni");
    }

    @BeforeClass
    public static void hasFloatBuffers() {
        EGLDisplay eglDisplay = null;
        EGLContext eglContext = null;
        EGLSurface eglSurface = null;
        try {
            eglDisplay = Egl14Utils.createEglDisplay();
            EGLConfig eglConfig = Egl14Utils.getEglConfig(eglDisplay, 2);
            eglContext = Egl14Utils.createEglContext(eglDisplay, eglConfig, 2);

            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, new int[] {
                    EGL_WIDTH, 1,
                    EGL_HEIGHT, 1,
                    EGL_NONE
            }, 0);
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

            sHasFloatBuffers = GlUtils.getMajorVersion() >= 3 ||
                    GlUtils.hasExtensions("GL_OES_texture_half_float",
                            "GL_OES_texture_half_float_linear");

            EGL14.eglMakeCurrent(eglDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(eglDisplay, eglSurface);

            Egl14Utils.destroyEglContext(eglDisplay, eglContext);
            Egl14Utils.releaseAndTerminate(eglDisplay);

            eglDisplay = null;
            eglContext = null;
            eglSurface = null;
        } finally {
            if (eglDisplay != null) {
                if (eglContext != null) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext);
                }
                if (eglSurface != null) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface);
                }
                EGL14.eglTerminate(eglDisplay);
            }
        }
    }

    @Test
    public void testCreate() {
        HardwareBuffer buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertTrue(buffer != null);
        assertEquals(2, buffer.getWidth());
        assertEquals(4, buffer.getHeight());
        assertEquals(HardwareBuffer.RGBA_8888, buffer.getFormat());
        assertEquals(1, buffer.getLayers());
        assertEquals(HardwareBuffer.USAGE_CPU_READ_RARELY, buffer.getUsage());

        buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBX_8888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.RGBX_8888, buffer.getFormat());
        buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGB_888, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.RGB_888, buffer.getFormat());
        buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGB_565, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.RGB_565, buffer.getFormat());
        buffer = HardwareBuffer.create(2, 1, HardwareBuffer.BLOB, 1,
                HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertEquals(HardwareBuffer.BLOB, buffer.getFormat());

        if (sHasFloatBuffers) {
            buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_FP16, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
            assertEquals(HardwareBuffer.RGBA_FP16, buffer.getFormat());
            buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGBA_1010102, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
            assertEquals(HardwareBuffer.RGBA_1010102, buffer.getFormat());
        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Test
    public void testCreateFailsWithInvalidArguments() {
        HardwareBuffer buffer = null;
        try {
            buffer = HardwareBuffer.create(0, 4, HardwareBuffer.RGB_888, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 0, HardwareBuffer.RGB_888, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 4, 0, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 4, HardwareBuffer.RGB_888, -1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);
        try {
            buffer = HardwareBuffer.create(2, 2, HardwareBuffer.BLOB, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        } catch (IllegalArgumentException e) {}
        assertEquals(null, buffer);

        if (sHasFloatBuffers) {
            try {
                buffer = HardwareBuffer.create(0, 4, HardwareBuffer.RGBA_FP16, 1,
                        HardwareBuffer.USAGE_CPU_READ_RARELY);
            } catch (IllegalArgumentException e) {
            }
            assertEquals(null, buffer);
            try {
                buffer = HardwareBuffer.create(0, 4, HardwareBuffer.RGBA_1010102, 1,
                        HardwareBuffer.USAGE_CPU_READ_RARELY);
            } catch (IllegalArgumentException e) {
            }
            assertEquals(null, buffer);
        }
    }

    @Test
    public void testCreateFromNativeObject() {
        HardwareBuffer buffer = nativeCreateHardwareBuffer(2, 4, HardwareBuffer.RGBA_8888, 1,
                    HardwareBuffer.USAGE_CPU_READ_RARELY);
        assertTrue(buffer != null);
        assertEquals(2, buffer.getWidth());
        assertEquals(4, buffer.getHeight());
        assertEquals(HardwareBuffer.RGBA_8888, buffer.getFormat());
        assertEquals(1, buffer.getLayers());
        assertEquals(HardwareBuffer.USAGE_CPU_READ_RARELY, buffer.getUsage());
        nativeReleaseHardwareBuffer(buffer);
    }
}
