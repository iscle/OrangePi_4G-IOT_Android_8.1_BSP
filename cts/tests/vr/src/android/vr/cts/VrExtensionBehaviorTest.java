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

import android.content.Intent;
import android.opengl.EGL14;
import android.opengl.GLES32;
import android.test.ActivityInstrumentationTestCase2;

public class VrExtensionBehaviorTest extends ActivityInstrumentationTestCase2<OpenGLESActivity> {
    static {
        System.loadLibrary("ctsvrextensions_jni");
    }

    private static final int EGL_CONTEXT_PRIORITY_HIGH_IMG = 0x3101;
    private static final int EGL_CONTEXT_PRIORITY_MEDIUM_IMG = 0x3102;
    private static final int EGL_CONTEXT_PRIORITY_LOW_IMG = 0x3103;

    private static final int EGL_FRONT_BUFFER_AUTO_REFRESH_ANDROID = 0x314C;

    private OpenGLESActivity mActivity;

    public VrExtensionBehaviorTest() {
        super(OpenGLESActivity.class);
    }

    private OpenGLESActivity getGlEsActivity(int viewIndex, int createProtected,
        int priorityAttribute, int mutableAttribute) {
        Intent intent = new Intent();
        intent.putExtra(OpenGLESActivity.EXTRA_VIEW_INDEX, viewIndex);
        intent.putExtra(OpenGLESActivity.EXTRA_PROTECTED, createProtected);
        intent.putExtra(OpenGLESActivity.EXTRA_PRIORITY, priorityAttribute);
        intent.putExtra(OpenGLESActivity.EXTRA_MUTABLE, mutableAttribute);
        setActivityIntent(intent);
        OpenGLESActivity activity = getActivity();
        assertTrue(activity.waitForFrameDrawn());
        return activity;
    }

    /**
     * Tests that protected content contexts and surfaces can be created.
     */
    public void testProtectedContent() throws Throwable {
        mActivity = getGlEsActivity(OpenGLESActivity.RENDERER_BASIC, 1, 0, 0);
        if (!mActivity.supportsVrHighPerformance())
            return;

        int error = mActivity.glGetError();
        assertEquals(GLES32.GL_NO_ERROR, error);

        mActivity.runOnGlThread(new Runnable() {
            public void run() {
                // Check that both the context and surface have the right attribute set.
                assertTrue("Unable to validate protected context",
                    OpenGLESActivity.contextHasAttributeWithValue(
                        OpenGLESActivity.EGL_PROTECTED_CONTENT_EXT, EGL14.EGL_TRUE));
                assertTrue("Unable to validate protected surface",
                    OpenGLESActivity.surfaceHasAttributeWithValue(
                        OpenGLESActivity.EGL_PROTECTED_CONTENT_EXT, EGL14.EGL_TRUE));
            }
        });
    }

    /**
     * Tests that textures can be marked as protected.
     */
    public void testProtectedTextures() throws Throwable {
        mActivity = getGlEsActivity(OpenGLESActivity.RENDERER_PROTECTEDTEXTURES, 1, 0, 0);
        if (!mActivity.supportsVrHighPerformance())
            return;

        int error = mActivity.glGetError();
        assertEquals(GLES32.GL_NO_ERROR, error);

        mActivity.runOnGlThread(new Runnable() {
            public void run() {
                // Check that both the context and surface have the right attribute set.
                int[] values = new int[1];
                GLES32.glGetIntegerv(GLES32.GL_CONTEXT_FLAGS, values, 0);
                assertTrue("Context is not a protected context",
                    (values[0] & RendererProtectedTexturesTest.GL_CONTEXT_FLAG_PROTECTED_CONTENT_BIT) != 0);

                values[0] = 0;
                GLES32.glGetTexParameteriv(GLES32.GL_TEXTURE_2D,
                    RendererProtectedTexturesTest.GL_TEXTURE_PROTECTED_EXT, values, 0);
                assertEquals("Texture is not marked as protected", GLES32.GL_TRUE, values[0]);
            }
        });
    }

    /**
     * Tests that context priority can be set to high.
     */
    public void testContextPriorityHigh() throws Throwable {
        runContextPriorityTest(EGL_CONTEXT_PRIORITY_HIGH_IMG);
    }

    /**
     * Tests that context priority can be set to medium.
     */
    public void testContextPriorityMedium() throws Throwable {
        runContextPriorityTest(EGL_CONTEXT_PRIORITY_MEDIUM_IMG);
    }

    /**
     * Tests that context priority can be set to low.
     */
    public void testContextPriorityLow() throws Throwable {
        runContextPriorityTest(EGL_CONTEXT_PRIORITY_LOW_IMG);
    }

    /**
     * Tests that context priority can be set to low.
     */
    public void testMutableRenderBuffer() throws Throwable {

        mActivity = getGlEsActivity(OpenGLESActivity.RENDERER_BASIC, 0, 0, 1);
        if (!mActivity.supportsVrHighPerformance())
            return;

        int error = mActivity.glGetError();
        assertEquals(GLES32.GL_NO_ERROR, error);

        mActivity.runOnGlThread(new Runnable() {
            public void run() {
                OpenGLESActivity.setSurfaceAttribute(EGL14.EGL_RENDER_BUFFER,
                    EGL14.EGL_SINGLE_BUFFER);
                OpenGLESActivity.setSurfaceAttribute(EGL_FRONT_BUFFER_AUTO_REFRESH_ANDROID,
                    EGL14.EGL_TRUE);
                swapBuffers();
                assertTrue("Unable to enable single buffered mode",
                    OpenGLESActivity.surfaceHasAttributeWithValue(
                        EGL14.EGL_RENDER_BUFFER, EGL14.EGL_SINGLE_BUFFER));

                OpenGLESActivity.setSurfaceAttribute(EGL14.EGL_RENDER_BUFFER,
                    EGL14.EGL_BACK_BUFFER);
                swapBuffers();
                assertTrue("Unable to disable single buffered mode",
                    OpenGLESActivity.surfaceHasAttributeWithValue(
                        EGL14.EGL_RENDER_BUFFER, EGL14.EGL_BACK_BUFFER));
            }
        });
    }

    /**
     * Test that a layered EGLImage can be created and attached to a FBO.
     * For more information, see the EGL_image_array spec:
     * https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_EGL_image_array.txt
     */
    public void testEglImageArray() throws Throwable {
        mActivity = getGlEsActivity(OpenGLESActivity.RENDERER_BASIC, 0, 0, 0);
        if (!mActivity.supportsVrHighPerformance())
            return;

        assertEquals(GLES32.GL_NO_ERROR, mActivity.glGetError());

        mActivity.runOnGlThread(new Runnable() {
            public void run() {
              nativeTestEglImageArray();
            }
        });
    }

    /**
     * Test that an external buffer can be created, written to, and read from.
     * For more information, see the GL_EXT_external_buffer spec:
     * https://www.khronos.org/registry/OpenGL/extensions/EXT/EXT_external_buffer.txt
     */
    public void testExternalBuffer() throws Throwable {
        mActivity = getGlEsActivity(OpenGLESActivity.RENDERER_BASIC, 0, 0, 0);
        if (!mActivity.supportsVrHighPerformance())
            return;

        assertEquals(GLES32.GL_NO_ERROR, mActivity.glGetError());

        mActivity.runOnGlThread(new Runnable() {
            public void run() {
              nativeTestExternalBuffer();
            }
        });
    }

    /**
     * Runs a context priority test.
     */
    private void runContextPriorityTest(int priority) throws Throwable {
        mActivity = getGlEsActivity(OpenGLESActivity.RENDERER_BASIC, 0, priority, 0);
        if (!mActivity.supportsVrHighPerformance())
            return;

        int error = mActivity.glGetError();
        assertEquals(GLES32.GL_NO_ERROR, error);

        mActivity.runOnGlThread(new Runnable() {
            public void run() {
                assertTrue("Unable to set context priority to " + Integer.toHexString(priority),
                    OpenGLESActivity.contextHasAttributeWithValue(
                        OpenGLESActivity.EGL_CONTEXT_PRIORITY_LEVEL_IMG, priority));
            }
        });
    }

    /**
     * Swaps EGL buffers.
     */
    private void swapBuffers() {
        EGL14.eglSwapBuffers(EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY),
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW));
    }

    private static native boolean nativeTestEglImageArray();
    private static native boolean nativeTestExternalBuffer();
}
