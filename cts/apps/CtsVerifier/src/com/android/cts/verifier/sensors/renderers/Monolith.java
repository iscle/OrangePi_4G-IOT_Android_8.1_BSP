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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * Rectangular block that is rotated by {@link GLRotationGuideRenderer}.
 */
class Monolith {
    private static final int NUM_VERTICES = 8;
    private static final int NUM_INDICES = 36;

    private final FloatBuffer mVertexBuffer;
    private final ShortBuffer mIndexBuffer;

    public Monolith() {
        mVertexBuffer = ByteBuffer.allocateDirect(NUM_VERTICES * 3 * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        float[] coordinates = {
                -0.65f, -1, 0.2f,
                -0.65f, 1, 0.2f,
                0.65f, 1, 0.2f,
                0.65f, -1, 0.2f,

                -0.65f, -1, -0.2f,
                -0.65f, 1, -0.2f,
                0.65f, 1, -0.2f,
                0.65f, -1, -0.2f,
        };

        for (int i = 0; i < coordinates.length; i++) {
            mVertexBuffer.put(coordinates[i]);
        }

        mIndexBuffer = ByteBuffer.allocateDirect(NUM_INDICES * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();

        // Front
        mIndexBuffer.put((short) 0);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 2);
        mIndexBuffer.put((short) 0);
        mIndexBuffer.put((short) 2);
        mIndexBuffer.put((short) 3);

        // Back
        mIndexBuffer.put((short) 7);
        mIndexBuffer.put((short) 6);
        mIndexBuffer.put((short) 5);
        mIndexBuffer.put((short) 7);
        mIndexBuffer.put((short) 5);
        mIndexBuffer.put((short) 4);

        // Right
        mIndexBuffer.put((short) 3);
        mIndexBuffer.put((short) 2);
        mIndexBuffer.put((short) 6);
        mIndexBuffer.put((short) 3);
        mIndexBuffer.put((short) 6);
        mIndexBuffer.put((short) 7);

        // Left
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 5);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 0);

        // Top
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 5);
        mIndexBuffer.put((short) 6);
        mIndexBuffer.put((short) 1);
        mIndexBuffer.put((short) 6);
        mIndexBuffer.put((short) 2);

        // Bottom
        mIndexBuffer.put((short) 3);
        mIndexBuffer.put((short) 7);
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 3);
        mIndexBuffer.put((short) 4);
        mIndexBuffer.put((short) 0);

        mVertexBuffer.position(0);
        mIndexBuffer.position(0);
    }

    public void draw(GL10 gl) {
        gl.glColor4f(0.5f, 0.5f, 0.5f, 1f);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mVertexBuffer);
        gl.glDrawElements(GL10.GL_TRIANGLES, NUM_INDICES, GL10.GL_UNSIGNED_SHORT, mIndexBuffer);
    }
}
