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

import android.hardware.Sensor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * A representation of a 3D triangular wedge or arrowhead shape, suitable for pointing a direction.
 */
class Wedge {
    private final static int VERTS = 6;

    // Storage for the vertices.
    private final FloatBuffer mFVertexBuffer;

    // Storage for the drawing sequence of the vertices. This contains integer indices into the
    // mFVertextBuffer structure.
    private final ShortBuffer mIndexBuffer;

    // Storage for the texture used on the surface of the wedge.
    private final FloatBuffer mTexBuffer;

    public Wedge(int sensorType) {
        // Buffers to be passed to gl*Pointer() functions
        // must be direct & use native ordering

        ByteBuffer vbb = ByteBuffer.allocateDirect(VERTS * 6 * 4);
        vbb.order(ByteOrder.nativeOrder());
        mFVertexBuffer = vbb.asFloatBuffer();

        ByteBuffer tbb = ByteBuffer.allocateDirect(VERTS * 2 * 4);
        tbb.order(ByteOrder.nativeOrder());
        mTexBuffer = tbb.asFloatBuffer();

        ByteBuffer ibb = ByteBuffer.allocateDirect(VERTS * 8 * 2);
        ibb.order(ByteOrder.nativeOrder());
        mIndexBuffer = ibb.asShortBuffer();

        // Coordinates of the vertices making up a simple wedge. Six total vertices, representing
        // two isosceles triangles, side by side, centered on the origin separated by 0.25 units,
        // with elongated ends pointing down the negative Z axis.
        float[] coords;
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            float[] verts = {
                    // X, Y, Z
                    -0.125f, -0.25f, -0.25f,
                    -0.125f, 0.25f, -0.25f,
                    -0.125f, 0.0f, 0.559016994f,
                    0.125f, -0.25f, -0.25f,
                    0.125f, 0.25f, -0.25f,
                    0.125f, 0.0f, 0.559016994f,
            };
            coords = verts.clone();
        } else {
            float[] verts = {
                    // X, Y, Z
                    -0.25f, -0.25f, -0.125f,
                    -0.25f, 0.25f, -0.125f,
                    0.559016994f, 0.0f, -0.125f,
                    -0.25f, -0.25f, 0.125f,
                    -0.25f, 0.25f, 0.125f,
                    0.559016994f, 0.0f, 0.125f,
            };
            coords = verts.clone();
        }

        for (int i = 0; i < VERTS; i++) {
            for (int j = 0; j < 3; j++) {
                mFVertexBuffer.put(coords[i * 3 + j] * 2.0f);
            }
        }

        for (int i = 0; i < VERTS; i++) {
            for (int j = 0; j < 2; j++) {
                mTexBuffer.put(coords[i * 3 + j] * 2.0f + 0.5f);
            }
        }

        // left face
        mIndexBuffer.put((short) 0);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 2);

        // right face
        mIndexBuffer.put((short) 5);
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 3);

        // top side, 2 triangles to make rect
        mIndexBuffer.put((short) 2);
        mIndexBuffer.put((short) 5);
        mIndexBuffer.put((short) 3);
        mIndexBuffer.put((short) 3);
        mIndexBuffer.put((short) 0);
        mIndexBuffer.put((short) 2);

        // bottom side, 2 triangles to make rect
        mIndexBuffer.put((short) 5);
        mIndexBuffer.put((short) 2);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 5);

        // base, 2 triangles to make rect
        mIndexBuffer.put((short) 0);
        mIndexBuffer.put((short) 3);
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 0);

        mFVertexBuffer.position(0);
        mTexBuffer.position(0);
        mIndexBuffer.position(0);
    }

    public void draw(GL10 gl) {
        gl.glFrontFace(GL10.GL_CCW);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTexBuffer);
        gl.glDrawElements(GL10.GL_TRIANGLE_STRIP, 24, GL10.GL_UNSIGNED_SHORT, mIndexBuffer);
    }
}
