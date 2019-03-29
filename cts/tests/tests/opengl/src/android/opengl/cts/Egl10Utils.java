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
import android.opengl.EGLExt;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

final class Egl10Utils {
    private Egl10Utils() {
    }

    static EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, int[] configSpec) {
        int[] value = new int[1];
        if (!egl.eglChooseConfig(display, configSpec, null, 0,
                value)) {
            throw new IllegalArgumentException("eglChooseConfig failed");
        }

        int numConfigs = value[0];
        if (numConfigs <= 0) {
            throw new IllegalArgumentException("No configs match configSpec");
        }

        EGLConfig[] configs = new EGLConfig[numConfigs];
        if (!egl.eglChooseConfig(display, configSpec, configs, numConfigs, value)) {
            throw new IllegalArgumentException("eglChooseConfig#2 failed");
        }
        EGLConfig config = chooseConfig(egl, display, configs, configSpec);
        if (config == null) {
            throw new IllegalArgumentException("No config chosen");
        }
        return config;
    }

    static int[] filterConfigSpec(int[] configSpec, int version) {
        if (version != 2 && version != 3) {
            return configSpec;
        }

        int len = configSpec.length;
        int[] newConfigSpec = new int[len + 2];
        System.arraycopy(configSpec, 0, newConfigSpec, 0, len-1);

        newConfigSpec[len-1] = EGL10.EGL_RENDERABLE_TYPE;
        if (version == 2) {
            newConfigSpec[len] = EGL14.EGL_OPENGL_ES2_BIT;  /* EGL_OPENGL_ES2_BIT */
        } else {
            newConfigSpec[len] = EGLExt.EGL_OPENGL_ES3_BIT_KHR; /* EGL_OPENGL_ES3_BIT_KHR */
        }
        newConfigSpec[len + 1] = EGL10.EGL_NONE;

        return newConfigSpec;
    }

    private static EGLConfig chooseConfig(EGL10 egl, EGLDisplay display,
            EGLConfig[] configs, int[] configSpec) {

        int redSize = findValue(configSpec, EGL10.EGL_RED_SIZE);
        int greenSize = findValue(configSpec, EGL10.EGL_GREEN_SIZE);
        int blueSize = findValue(configSpec, EGL10.EGL_BLUE_SIZE);
        int alphaSize = findValue(configSpec, EGL10.EGL_ALPHA_SIZE);
        int depthSize = findValue(configSpec, EGL10.EGL_DEPTH_SIZE);
        int stencilSize = findValue(configSpec, EGL10.EGL_STENCIL_SIZE);

        for (EGLConfig config : configs) {
            int d = findConfigAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE);
            int s = findConfigAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE);
            if ((d >= depthSize) && (s >= stencilSize)) {
                int r = findConfigAttrib(egl, display, config, EGL10.EGL_RED_SIZE);
                int g = findConfigAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE);
                int b = findConfigAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE);
                int a = findConfigAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE);
                if ((r == redSize) && (g == greenSize) && (b == blueSize) && (a == alphaSize)) {
                    return config;
                }
            }
        }
        return null;
    }

    private static int findValue(int[] configSpec, int name) {
        for (int i = 0; i < configSpec.length; i += 2) {
            if (configSpec[i] == name) {
                return configSpec[i + 1];
            }
        }
        return 0;
    }

    private static int findConfigAttrib(EGL10 egl, EGLDisplay display,
            EGLConfig config, int attribute) {
        int[] value = new int[1];
        if (egl.eglGetConfigAttrib(display, config, attribute, value)) {
            return value[0];
        }
        return 0;
    }
}
