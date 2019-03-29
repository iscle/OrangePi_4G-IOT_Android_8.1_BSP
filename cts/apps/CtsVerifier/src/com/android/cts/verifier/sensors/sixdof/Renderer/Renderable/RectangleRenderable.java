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
package com.android.cts.verifier.sensors.sixdof.Renderer.Renderable;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.Colour;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.DrawParameters;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ShaderHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Rotating Rectangle for the robustness test.
 */
public class RectangleRenderable extends Renderable {
    private static final int LINE_WIDTH = 8;
    protected static final float[] RECTANGLE_POSITION = {0.0f, 0.0f, -2.99f};

    private FloatBuffer mPositionBuffer;
    private FloatBuffer mColorBuffer;
    private int mColorHandle;

    private float[] mRectanglePositionData;

    public RectangleRenderable() {
        // Reset the model matrix to the identity and move it infront of the camera preview
        Matrix.setIdentityM(getModelMatrix(), 0);
        Matrix.translateM(getModelMatrix(), 0,
                RECTANGLE_POSITION[X], RECTANGLE_POSITION[Y], RECTANGLE_POSITION[Z]);
    }

    public void initialiseRectangle(float[] rectanglePositionData) {
        mRectanglePositionData = rectanglePositionData;

        // Initialize the buffers.
        mPositionBuffer = ByteBuffer.allocateDirect(mRectanglePositionData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mPositionBuffer.put(mRectanglePositionData).position(0);

        // float count / floats per vertex
        mVertexCount = mRectanglePositionData.length / POSITION_DATA_SIZE;
        int colourCount = mVertexCount * COLOUR_DATA_SIZE; // Vertex count * rgba
        float[] colours = new float[colourCount];

        for (int i = 0; i < colourCount; i++) {
            int index = i % COLOUR_DATA_SIZE;
            colours[i] = Colour.GREEN[index];
        }

        mColorBuffer = ByteBuffer.allocateDirect(colours.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mColorBuffer.put(colours).position(0);

        final String vertexShader = ShaderHelper.getRectangleVertexShader();
        final String fragmentShader = ShaderHelper.getRectangleFragmentShader();

        final int vertexShaderHandle =
                ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle =
                ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"a_Position", "a_Color"});
    }

    @Override
    public void draw(DrawParameters drawParameters) {
        GLES20.glUseProgram(mProgramHandle);

        // Set program handles for camera preview drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Color");

        updateMvpMatrix(drawParameters.getViewMatrix(), drawParameters.getProjectionMatrix());
        drawRectangle();
    }

    private void drawRectangle() {
        // Pass in the position information
        mPositionBuffer.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, POSITION_DATA_SIZE, GLES20.GL_FLOAT, false,
                0, mPositionBuffer);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, getMvMatrix(), 0);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, getMvpMatrix(), 0);

        synchronized (this) {
            // Pass in the color information
            mColorBuffer.position(0);
            GLES20.glVertexAttribPointer(mColorHandle, COLOUR_DATA_SIZE, GLES20.GL_FLOAT, false,
                    0, mColorBuffer);

            GLES20.glEnableVertexAttribArray(mColorHandle);
        }

        // Draw the rectangle.
        GLES20.glLineWidth(LINE_WIDTH);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, mVertexCount); // 2 points per line * 4 lines = 8
    }

    public void setLineColor(float[] newColor) {
        synchronized (this) {
            // float count / floats per vertex
            int vertexCount = mRectanglePositionData.length / POSITION_DATA_SIZE;
            int colourCount = vertexCount * COLOUR_DATA_SIZE; // Vertex count * rgba
            float[] colours = new float[colourCount];

            for (int i = 0; i < colourCount; i++) {
                int index = i % COLOUR_DATA_SIZE;
                colours[i] = newColor[index];
            }

            mColorBuffer.put(colours).position(0);
        }
    }

    @Override
    public void destroy() {
        mPositionBuffer = null;
        mColorBuffer = null;
    }
}
