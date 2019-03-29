/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Test that gets a list of EGL configurations and tries to use each one in a GLSurfaceView.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class EglConfigTest {
    private static final int EGL_OPENGL_ES_BIT = 0x1;
    private static final int EGL_OPENGL_ES2_BIT = 0x4;

    private Instrumentation mInstrumentation;

    @Rule
    public ActivityTestRule<EglConfigCtsActivity> mActivityRule =
            new ActivityTestRule<>(EglConfigCtsActivity.class, false, false);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void testEglConfigs() throws Exception {
        int[] configIds = getEglConfigIds(EGL_OPENGL_ES_BIT);
        int[] configIds2 = getEglConfigIds(EGL_OPENGL_ES2_BIT);
        assertTrue(configIds.length + configIds2.length > 0);
        runConfigTests(configIds, 1);
        runConfigTests(configIds2, 2);
    }

    private void runConfigTests(int[] configIds, int contextClientVersion)
            throws InterruptedException {
        for (int configId : configIds) {
            Intent intent = new Intent(InstrumentationRegistry.getTargetContext(),
                    EglConfigCtsActivity.class);
            intent.putExtra(EglConfigCtsActivity.CONFIG_ID_EXTRA, configId);
            intent.putExtra(EglConfigCtsActivity.CONTEXT_CLIENT_VERSION_EXTRA,
                    contextClientVersion);
            EglConfigCtsActivity activity = mActivityRule.launchActivity(intent);
            activity.waitToFinishDrawing();
            // TODO(b/30948621): Remove the sleep below once b/30948621 is fixed.
            Thread.sleep(500);
            activity.finish();
            mInstrumentation.waitForIdleSync();
        }
    }

    private static int[] getEglConfigIds(int renderableType) {
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int[] numConfigs = new int[1];

        int[] attributeList = new int[] {
                EGL10.EGL_RENDERABLE_TYPE, renderableType,

                // Avoid configs like RGBA0008 which crash even though they have the window bit set.
                EGL10.EGL_RED_SIZE, 1,
                EGL10.EGL_GREEN_SIZE, 1,
                EGL10.EGL_BLUE_SIZE, 1,

                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE
        };

        if (egl.eglInitialize(display, null)) {
            try {
                if (egl.eglChooseConfig(display, attributeList, null, 0, numConfigs)) {
                    EGLConfig[] configs = new EGLConfig[numConfigs[0]];
                    if (egl.eglChooseConfig(display, attributeList, configs, configs.length,
                            numConfigs)) {
                        int[] configIds = new int[numConfigs[0]];
                        for (int i = 0; i < numConfigs[0]; i++) {
                            int[] value = new int[1];
                            if (egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_CONFIG_ID,
                                    value)) {
                                configIds[i] = value[0];
                            } else {
                              throw new IllegalStateException("Couldn't call eglGetConfigAttrib");
                            }
                        }
                        return configIds;
                    } else {
                        throw new IllegalStateException("Couldn't call eglChooseConfig (1)");
                    }
                } else {
                    throw new IllegalStateException("Couldn't call eglChooseConfig (2)");
                }
            } finally {
                egl.eglTerminate(display);
            }
        } else {
            throw new IllegalStateException("Couldn't initialize EGL.");
        }
    }
}
