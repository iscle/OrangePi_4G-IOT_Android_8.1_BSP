/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.verifier.sensors.renderers;

import android.opengl.GLSurfaceView;
import android.opengl.GLU;

import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renders a spinning block to indicate how the device should be rotated in the test.
 */
public class GLRotationGuideRenderer implements GLSurfaceView.Renderer {
    public static final int BACKGROUND_BLACK = 0;
    public static final int BACKGROUND_RED = 1;
    public static final int BACKGROUND_GREEN = 2;
    private static final double ANGLE_INCREMENT = 1.0;

    private final Monolith mMonolith = new Monolith();
    private final AtomicInteger mBackgroundColor = new AtomicInteger(BACKGROUND_BLACK);

    private float mAngle;
    private float mRotateX;
    private float mRotateY;
    private float mRotateZ;

    /**
     * Sets the rotation of the monolith.
     */
    public void setRotation(float rotateX, float rotateY, float rotateZ) {
        mRotateX = rotateX;
        mRotateY = rotateY;
        mRotateZ = rotateZ;
    }

    /**
     * Sets the background color.
     */
    public void setBackgroundColor(int value) {
        mBackgroundColor.set(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        clearBackground(gl);
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
        gl.glRotatef(mAngle, mRotateX, mRotateY, mRotateZ);
        mMonolith.draw(gl);
        mAngle += ANGLE_INCREMENT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        float ratio = (float) width / height;
        gl.glFrustumf(-ratio, ratio, -1, 1, 3, 15);
        GLU.gluLookAt(gl, 0, 0, 10, 0, 0, 0, 0, 1, 0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnable(GL10.GL_LIGHTING);
        gl.glEnable(GL10.GL_LIGHT0);
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_AMBIENT, new float[] {0.75f, 0.75f, 0.75f, 1f}, 0);
    }

    private void clearBackground(GL10 gl) {
        switch (mBackgroundColor.get()) {
            case BACKGROUND_GREEN:
                gl.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
                break;

            case BACKGROUND_RED:
                gl.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
                break;

            default:
                gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                break;
        }
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
    }
}
