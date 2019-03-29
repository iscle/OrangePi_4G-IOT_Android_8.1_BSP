/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.verifier.sensors.renderers;

import com.android.cts.verifier.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;

import java.io.IOException;
import java.io.InputStream;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renders a wedge to indicate the direction of sensor data.
 */
public class GLArrowSensorTestRenderer implements GLSurfaceView.Renderer, SensorEventListener {
    // A representation of the Z-axis in vector form.
    private static final float[] Z_AXIS = new float[] {0, 0, 1};

    // The (pseudo)vector around which to rotate to align Z-axis with gravity.
    private final float[] mCrossProd = new float[3];
    private final float[] mRotationMatrix = new float[16];

    private final int mSensorType;
    private final Context mContext;
    private final Wedge mWedge;

    // The angle around mCrossProd to rotate to align Z-axis with gravity.
    private float mAngle;
    private int mTextureID;

    /**
     * It's a constructor. Can you dig it?
     *
     * @param context the Android Context that owns this renderer
     * @param type of arrow. Possible values: 0 = points towards gravity. 1 =
     *            points towards reference North
     */
    public GLArrowSensorTestRenderer(Context context, int type) {
        mContext = context;
        mSensorType = type;
        mWedge = new Wedge(mSensorType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDrawFrame(GL10 gl) {
        // set up the texture for drawing
        gl.glTexEnvx(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glActiveTexture(GL10.GL_TEXTURE0);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextureID);
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);

        // clear the screen and draw
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();

        if (mSensorType == Sensor.TYPE_ACCELEROMETER) {
            gl.glRotatef(
                    -mAngle * 180 / (float) Math.PI,
                    mCrossProd[0],
                    mCrossProd[1],
                    mCrossProd[2]);
        } else {
            gl.glMultMatrixf(mRotationMatrix, 0);
        }
        mWedge.draw(gl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        gl.glViewport(0, 0, w, h);
        float ratio = (float) w / h;
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glFrustumf(-ratio, ratio, -1, 1, 3, 7);
        GLU.gluLookAt(gl, 0, 0, -5, 0f, 0f, 0f, 0f, 1.0f, 0.0f);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // set up general OpenGL config
        gl.glClearColor(0.6f, 0f, 0.4f, 1); // a nice purpley magenta
        gl.glShadeModel(GL10.GL_SMOOTH);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_TEXTURE_2D);

        // create the texture we use on the wedge
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        mTextureID = textures[0];

        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextureID);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_REPLACE);

        InputStream is = mContext.getResources().openRawResource(R.raw.sns_texture);
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeStream(is);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                // Ignore.
            }
        }

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            /*
             * For this test we want *only* accelerometer data, so we can't use
             * the convenience methods on SensorManager; so compute manually.
             */
            normalize(event.values);

            /*
             * Because we need to invert gravity (because the accelerometer
             * vector actually points up), that constitutes a 180-degree
             * rotation around X, which means we need to invert Y.
             */
            event.values[1] *= -1;

            crossProduct(event.values, Z_AXIS, mCrossProd);
            mAngle = (float) Math.acos(dotProduct(event.values, Z_AXIS));
        } else if (type == Sensor.TYPE_ROTATION_VECTOR
                || type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                || type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            float[] rotationMatrixTmp = new float[16];

            SensorManager.getRotationMatrixFromVector(rotationMatrixTmp, event.values);
            SensorManager.remapCoordinateSystem(rotationMatrixTmp,
                    SensorManager.AXIS_MINUS_X, SensorManager.AXIS_Y, mRotationMatrix);
        }

    }

    /**
     * Computes the cross product of two vectors, storing the resulting pseudo-vector in out. All
     * arrays must be length 3 or more, and out is overwritten.
     *
     * @param left the left operand of the cross product
     * @param right the right operand of the cross product
     * @param out the array into which to store the cross-product pseudo-vector's data
     */
    public static void crossProduct(float[] left, float[] right, float[] out) {
        out[0] = left[1] * right[2] - left[2] * right[1];
        out[1] = left[2] * right[0] - left[0] * right[2];
        out[2] = left[0] * right[1] - left[1] * right[0];
    }

    /**
     * Computes the dot product of two vectors.
     *
     * @param left the first dot product operand
     * @param right the second dot product operand
     * @return the dot product of left and right
     */
    public static float dotProduct(float[] left, float[] right) {
        return left[0] * right[0] + left[1] * right[1] + left[2] * right[2];
    }

    /**
     * Normalizes the input vector into a unit vector.
     *
     * @param vector the vector to normalize. Contents are overwritten.
     */
    public static void normalize(float[] vector) {
        double mag = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2]
                * vector[2]);
        vector[0] /= mag;
        vector[1] /= mag;
        vector[2] /= mag;
    }
}
