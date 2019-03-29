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
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests using EGL contexts.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class EglContextTest {
    /**
     * Tests creating then releasing an EGL context.
     */
    @Test
    public void testCreateAndReleaseContext() {
        EGLDisplay eglDisplay = null;
        EGLContext eglContext = null;
        try {
            eglDisplay = Egl14Utils.createEglDisplay();
            eglContext = Egl14Utils.createEglContext(eglDisplay);
            Egl14Utils.destroyEglContext(eglDisplay, eglContext);
            Egl14Utils.releaseAndTerminate(eglDisplay);
            eglDisplay = null;
            eglContext = null;
        } finally {
            if (eglDisplay != null) {
                if (eglContext != null) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext);
                }

                EGL14.eglTerminate(eglDisplay);
            }
        }
    }
}
