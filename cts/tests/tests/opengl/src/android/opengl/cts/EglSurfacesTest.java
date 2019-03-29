/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import static junit.framework.Assert.fail;

/**
 * Tests using EGL surfaces.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class EglSurfacesTest {
    @Test
    public void testCreatePixmapSurface() {
        // NOTE: This test must use EGL10, which is why we don't reuse GlTestBase
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        if (display == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }

        int[] version = new int[2];
        if (!egl.eglInitialize(display, version)) {
            throw new RuntimeException("eglInitialize failed");
        }

        EGLConfig config = Egl10Utils.chooseConfig(egl, display,
                Egl10Utils.filterConfigSpec(new int[] {
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 0,
                        EGL10.EGL_DEPTH_SIZE, 0,
                        EGL10.EGL_STENCIL_SIZE, 0,
                        EGL10.EGL_NONE }, 2)
        );

        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
        };

        EGLContext context = egl.eglCreateContext(display, config,
                EGL10.EGL_NO_CONTEXT, contextAttribs);

        boolean unsupported = false;
        try {
            //noinspection deprecation
            egl.eglCreatePixmapSurface(display, config, null, null);
        } catch (UnsupportedOperationException e) {
            unsupported = true;
        }

        egl.eglDestroyContext(display, context);
        egl.eglTerminate(display);

        if (!unsupported) {
            fail("eglCreatePixmapSurface is supported");
        }
    }
}
