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

import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.Colour;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.DrawParameters;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ObjImporter;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ShaderHelper;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Object that needs to be collected by user in last test.
 */
public class RingRenderable extends Renderable {
    private static final int BYTES_PER_INT = 4;

    private FloatBuffer mPositionBuffer;
    private IntBuffer mIndicesBuffer;
    private FloatBuffer mNormalsBuffer;
    private FloatBuffer mColorBuffer;
    private int mColorHandle;
    private int mLightPosHandle;
    private int mLightStrengthHandle;
    private int mNormalHandle;

    public RingRenderable(float[] position, float[] rotation, float[] upVector) {
        // Reset the model matrix to the identity.
        Matrix.setIdentityM(getModelMatrix(), 0);

        float[] overallTransformation = new float[MathsUtils.MATRIX_4X4];
        Matrix.setIdentityM(overallTransformation, 0);

        // Rotation
        float[] rotationTransformation = new float[MathsUtils.MATRIX_4X4];
        Matrix.setIdentityM(rotationTransformation, 0);
        // The rotation given is relative to the position of the ring, so we have to calculate the
        // rotation as if we where at the origin.
        MathsUtils.setLookAtM(rotationTransformation,
                0.0f, 0.0f, 0.0f,
                rotation[X], rotation[Y], rotation[Z],
                upVector[X], upVector[Y], upVector[Z]);

        Matrix.multiplyMM(overallTransformation, 0, rotationTransformation, 0, overallTransformation, 0);

        // Translation
        float[] translationTransformation = new float[MathsUtils.MATRIX_4X4];
        Matrix.setIdentityM(translationTransformation, 0);
        Matrix.translateM(translationTransformation, 0, position[X], position[Y], position[Z]);

        // Apply translation to rotation.
        Matrix.multiplyMM(overallTransformation, 0, translationTransformation, 0, overallTransformation, 0);
        // Apply transformation to model matrix.
        Matrix.multiplyMM(getModelMatrix(), 0, overallTransformation, 0, getModelMatrix(), 0);
    }

    /**
     * Initialise the ring with data from the .obj file.
     */
    public void initialise(ObjImporter.ObjectData ringData) {
        mVertexCount = ringData.getIndicesData().length;

        int colourCount = mVertexCount * COLOUR_DATA_SIZE; // Vertex count * rgba
        float[] colours = new float[colourCount];

        for (int i = 0; i < colourCount; i++) {
            int index = i % COLOUR_DATA_SIZE;
            colours[i] = Colour.YELLOW[index];
        }

        // Initialize the buffers.
        mPositionBuffer = ByteBuffer.allocateDirect(ringData.getVertexData().length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mColorBuffer = ByteBuffer.allocateDirect(colours.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mNormalsBuffer = ByteBuffer.allocateDirect(ringData.getNormalsData().length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mIndicesBuffer = ByteBuffer.allocateDirect(ringData.getIndicesData().length * BYTES_PER_INT)
                .order(ByteOrder.nativeOrder()).asIntBuffer();

        mPositionBuffer.put(ringData.getVertexData()).position(0);
        mColorBuffer.put(colours).position(0);
        mNormalsBuffer.put(ringData.getNormalsData()).position(0);
        mIndicesBuffer.put(ringData.getIndicesData()).position(0);

        final String vertexShader = ShaderHelper.getAugmentedRealityVertexShader();
        final String fragmentShader = ShaderHelper.getAugmentedRealityFragmentShader();

        final int vertexShaderHandle =
                ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle =
                ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"a_Position", "a_Color", "a_Normal"});
    }

    @Override
    public void draw(DrawParameters drawParameters) {
        GLES20.glUseProgram(mProgramHandle);

        // Set program handles for camera preview drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
        mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mColorHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Color");

        // Used to calculate shadows.
        mLightPosHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_LightPos");
        mLightStrengthHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_LightStrength");
        mNormalHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Normal");

        // Calculate lighting information
        float[] lightPosInEyeSpace = drawParameters.getLight()
                .getPositionInEyeSpace(drawParameters.getViewMatrix());
        GLES20.glUniform3f(mLightPosHandle,
                lightPosInEyeSpace[X], lightPosInEyeSpace[Y], lightPosInEyeSpace[Z]);
        GLES20.glUniform1f(mLightStrengthHandle, drawParameters.getLight().getStrength());

        updateMvpMatrix(drawParameters.getViewMatrix(), drawParameters.getProjectionMatrix());
        drawRing();
    }

    private void drawRing() {
        // Pass in the position information
        mPositionBuffer.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, POSITION_DATA_SIZE, GLES20.GL_FLOAT, false,
                0, mPositionBuffer);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, getMvMatrix(), 0);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, getMvpMatrix(), 0);

        // Pass in the color information
        mColorBuffer.position(0);
        GLES20.glVertexAttribPointer(mColorHandle, COLOUR_DATA_SIZE, GLES20.GL_FLOAT, false,
                0, mColorBuffer);

        GLES20.glEnableVertexAttribArray(mColorHandle);

        // Pass in the normal information
        mNormalsBuffer.position(0);
        GLES20.glVertexAttribPointer(mNormalHandle, NORMAL_DATA_SIZE, GLES20.GL_FLOAT, false,
                0, mNormalsBuffer);

        GLES20.glEnableVertexAttribArray(mNormalHandle);

        // Draw the ring.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mVertexCount);
    }

    @Override
    public void destroy() {
        mPositionBuffer = null;
        mIndicesBuffer = null;
        mNormalsBuffer = null;
        mColorBuffer = null;
    }
}
