/*
 * Copyright 2017 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static android.opengl.EGL14.*;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.support.test.filters.SmallTest;
import android.view.Surface;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;


@SmallTest
@RunWith(BlockJUnit4ClassRunner.class)
public class ANativeWindowTest {

    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static final String TAG = ANativeWindowTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private EGLDisplay mEglDisplay = EGL_NO_DISPLAY;
    private EGLConfig mEglConfig = null;
    private EGLSurface mEglPbuffer = EGL_NO_SURFACE;
    private EGLContext mEglContext = EGL_NO_CONTEXT;

    @Before
    public void setup() throws Throwable {
        mEglDisplay = EGL14.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (mEglDisplay == EGL_NO_DISPLAY) {
            throw new RuntimeException("no EGL display");
        }
        int[] major = new int[1];
        int[] minor = new int[1];
        if (!EGL14.eglInitialize(mEglDisplay, major, 0, minor, 0)) {
            throw new RuntimeException("error in eglInitialize");
        }

        // If we could rely on having EGL_KHR_surfaceless_context and EGL_KHR_context_no_config, we
        // wouldn't have to create a config or pbuffer at all.

        int[] numConfigs = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        if (!EGL14.eglChooseConfig(mEglDisplay,
                new int[] {
                    EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                    EGL_NONE},
                0, configs, 0, 1, numConfigs, 0)) {
            throw new RuntimeException("eglChooseConfig failed");
        }
        mEglConfig = configs[0];

        mEglPbuffer = EGL14.eglCreatePbufferSurface(mEglDisplay, mEglConfig,
                new int[] {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE}, 0);
        if (mEglPbuffer == EGL_NO_SURFACE) {
            throw new RuntimeException("eglCreatePbufferSurface failed");
        }

        mEglContext = EGL14.eglCreateContext(mEglDisplay, mEglConfig, EGL_NO_CONTEXT,
                new int[] {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE}, 0);
        if (mEglContext == EGL_NO_CONTEXT) {
            throw new RuntimeException("eglCreateContext failed");
        }

        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglPbuffer, mEglPbuffer, mEglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    @Test
    public void testSetBuffersTransform() {
        final int MIRROR_HORIZONTAL_BIT = 0x01;
        final int MIRROR_VERTICAL_BIT   = 0x02;
        final int ROTATE_90_BIT         = 0x04;
        final int ALL_TRANSFORM_BITS    =
                MIRROR_HORIZONTAL_BIT | MIRROR_VERTICAL_BIT | ROTATE_90_BIT;

        // 4x4 GL-style matrices, as returned by SurfaceTexture#getTransformMatrix(). Note they're
        // transforming texture coordinates ([0,1]^2), so the origin for the transforms is
        // (0.5, 0.5), not (0,0).
        final float[] MIRROR_HORIZONTAL_MATRIX = new float[] {
            -1.0f,  0.0f,  0.0f,  0.0f,
             0.0f,  1.0f,  0.0f,  0.0f,
             0.0f,  0.0f,  1.0f,  0.0f,
             1.0f,  0.0f,  0.0f,  1.0f,
        };
        final float[] MIRROR_VERTICAL_MATRIX = new float[] {
             1.0f,  0.0f,  0.0f,  0.0f,
             0.0f, -1.0f,  0.0f,  0.0f,
             0.0f,  0.0f,  1.0f,  0.0f,
             0.0f,  1.0f,  0.0f,  1.0f,
        };
        final float[] ROTATE_90_MATRIX = new float[] {
             0.0f,  1.0f,  0.0f,  0.0f,
            -1.0f,  0.0f,  0.0f,  0.0f,
             0.0f,  0.0f,  1.0f,  0.0f,
             1.0f,  0.0f,  0.0f,  1.0f,
        };

        int[] texId = new int[1];
        GLES20.glGenTextures(1, texId, 0);

        SurfaceTexture consumer = new SurfaceTexture(texId[0]);
        consumer.setDefaultBufferSize(16, 16);
        Surface surface = new Surface(consumer);

        float[] computedTransform = new float[16];
        float[] receivedTransform = new float[16];
        float[] tmp = new float[16];
        for (int transform = 0; transform <= ALL_TRANSFORM_BITS; transform++) {
            nPushBufferWithTransform(surface, transform);

            // The SurfaceTexture texture transform matrix first does a vertical flip so that
            // "first row in memory" corresponds to "texture coordinate v=0".
            System.arraycopy(MIRROR_VERTICAL_MATRIX, 0, computedTransform, 0, 16);

            if ((transform & MIRROR_HORIZONTAL_BIT) != 0) {
                matrixMultiply(computedTransform, computedTransform, MIRROR_HORIZONTAL_MATRIX, tmp);
            }
            if ((transform & MIRROR_VERTICAL_BIT) != 0) {
                matrixMultiply(computedTransform, computedTransform, MIRROR_VERTICAL_MATRIX, tmp);
            }
            if ((transform & ROTATE_90_BIT) != 0) {
                matrixMultiply(computedTransform, computedTransform, ROTATE_90_MATRIX, tmp);
            }

            consumer.updateTexImage();
            consumer.getTransformMatrix(receivedTransform);

            if (DEBUG) {
                Log.d(TAG, String.format(
                        "Transform 0x%x:\n" +
                        "  expected: % 2.0f % 2.0f % 2.0f % 2.0f\n" +
                        "            % 2.0f % 2.0f % 2.0f % 2.0f\n" +
                        "            % 2.0f % 2.0f % 2.0f % 2.0f\n" +
                        "            % 2.0f % 2.0f % 2.0f % 2.0f\n" +
                        "  actual:   % 2.0f % 2.0f % 2.0f % 2.0f\n" +
                        "            % 2.0f % 2.0f % 2.0f % 2.0f\n" +
                        "            % 2.0f % 2.0f % 2.0f % 2.0f\n" +
                        "            % 2.0f % 2.0f % 2.0f % 2.0f\n",
                        transform,
                        computedTransform[ 0], computedTransform[ 1],
                        computedTransform[ 2], computedTransform[ 3],
                        computedTransform[ 4], computedTransform[ 5],
                        computedTransform[ 6], computedTransform[ 7],
                        computedTransform[ 8], computedTransform[ 9],
                        computedTransform[10], computedTransform[11],
                        computedTransform[12], computedTransform[13],
                        computedTransform[14], computedTransform[15],
                        receivedTransform[ 0], receivedTransform[ 1],
                        receivedTransform[ 2], receivedTransform[ 3],
                        receivedTransform[ 4], receivedTransform[ 5],
                        receivedTransform[ 6], receivedTransform[ 7],
                        receivedTransform[ 8], receivedTransform[ 9],
                        receivedTransform[10], receivedTransform[11],
                        receivedTransform[12], receivedTransform[13],
                        receivedTransform[14], receivedTransform[15]));
            }

            for (int i = 0; i < 16; i++) {
                assertEquals(computedTransform[i], receivedTransform[i], 0.0f);
            }
        }
    }

    // Multiply 4x4 matrices result = a*b. result can be the same as either a or b,
    // allowing for result *= b. Another 4x4 matrix tmp must be provided as scratch space.
    private void matrixMultiply(float[] result, float[] a, float[] b, float[] tmp) {
        tmp[ 0] = a[ 0]*b[ 0] + a[ 4]*b[ 1] + a[ 8]*b[ 2] + a[12]*b[ 3];
        tmp[ 1] = a[ 1]*b[ 0] + a[ 5]*b[ 1] + a[ 9]*b[ 2] + a[13]*b[ 3];
        tmp[ 2] = a[ 2]*b[ 0] + a[ 6]*b[ 1] + a[10]*b[ 2] + a[14]*b[ 3];
        tmp[ 3] = a[ 3]*b[ 0] + a[ 7]*b[ 1] + a[11]*b[ 2] + a[15]*b[ 3];

        tmp[ 4] = a[ 0]*b[ 4] + a[ 4]*b[ 5] + a[ 8]*b[ 6] + a[12]*b[ 7];
        tmp[ 5] = a[ 1]*b[ 4] + a[ 5]*b[ 5] + a[ 9]*b[ 6] + a[13]*b[ 7];
        tmp[ 6] = a[ 2]*b[ 4] + a[ 6]*b[ 5] + a[10]*b[ 6] + a[14]*b[ 7];
        tmp[ 7] = a[ 3]*b[ 4] + a[ 7]*b[ 5] + a[11]*b[ 6] + a[15]*b[ 7];

        tmp[ 8] = a[ 0]*b[ 8] + a[ 4]*b[ 9] + a[ 8]*b[10] + a[12]*b[11];
        tmp[ 9] = a[ 1]*b[ 8] + a[ 5]*b[ 9] + a[ 9]*b[10] + a[13]*b[11];
        tmp[10] = a[ 2]*b[ 8] + a[ 6]*b[ 9] + a[10]*b[10] + a[14]*b[11];
        tmp[11] = a[ 3]*b[ 8] + a[ 7]*b[ 9] + a[11]*b[10] + a[15]*b[11];

        tmp[12] = a[ 0]*b[12] + a[ 4]*b[13] + a[ 8]*b[14] + a[12]*b[15];
        tmp[13] = a[ 1]*b[12] + a[ 5]*b[13] + a[ 9]*b[14] + a[13]*b[15];
        tmp[14] = a[ 2]*b[12] + a[ 6]*b[13] + a[10]*b[14] + a[14]*b[15];
        tmp[15] = a[ 3]*b[12] + a[ 7]*b[13] + a[11]*b[14] + a[15]*b[15];

        System.arraycopy(tmp, 0, result, 0, 16);
    }

    private static native void nPushBufferWithTransform(Surface surface, int transform);

}
