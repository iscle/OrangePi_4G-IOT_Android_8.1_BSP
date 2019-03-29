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

import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.Colour;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ConeModelMatrixCalculator;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.DrawParameters;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ObjImporter;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ShaderHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Object that needs to be collected by user in last test.
 */
public class ConeRenderable extends Renderable {
    private static final int BYTES_PER_INT = 4;
    public static final float[] CONE_OFFSET = new float[]{
            0.2f, -0.1f, -1.0f}; // Offset from camera position.

    private FloatBuffer mPositionBuffer;
    private IntBuffer mIndicesBuffer;
    private FloatBuffer mNormalsBuffer;
    private FloatBuffer mColorBuffer;
    private int mColorHandle;
    private int mLightPosHandle;
    private int mLightStrengthHandle;
    private int mNormalHandle;

    private ConeModelMatrixCalculator mModelMatrixCalculator;

    public ConeRenderable(int toRotate, float[] upVector) {
        mModelMatrixCalculator = new ConeModelMatrixCalculator(toRotate, upVector);
    }

    public void initialise(ObjImporter.ObjectData coneData) {
        mVertexCount = coneData.getIndicesData().length;

        int colourCount = mVertexCount * COLOUR_DATA_SIZE; // Vertex count * rgba
        float[] colours = new float[colourCount];

        for (int i = 0; i < colourCount; i++) {
            int index = i % COLOUR_DATA_SIZE;
            colours[i] = Colour.WHITE[index];
        }

        // Initialize the buffers.
        mPositionBuffer = ByteBuffer.allocateDirect(coneData.getVertexData().length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mColorBuffer = ByteBuffer.allocateDirect(colours.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mNormalsBuffer = ByteBuffer.allocateDirect(coneData.getNormalsData().length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mIndicesBuffer = ByteBuffer.allocateDirect(coneData.getIndicesData().length * BYTES_PER_INT)
                .order(ByteOrder.nativeOrder()).asIntBuffer();

        mPositionBuffer.put(coneData.getVertexData()).position(0);
        mColorBuffer.put(colours).position(0);
        mNormalsBuffer.put(coneData.getNormalsData()).position(0);
        mIndicesBuffer.put(coneData.getIndicesData()).position(0);

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
    public float[] getModelMatrix() {
        // We want a model matrix that has camera extrinsics taken into account.
        return mModelMatrixCalculator.getModelMatrix();
    }

    public void updateModelMatrix(float[] translation, float[] rotation, float[] lookAtPosition) {
        mModelMatrixCalculator.updateModelMatrix(translation, rotation, lookAtPosition);
    }

    public void setDevice2IMUMatrix(float[] translation, float[] quaternion) {
        mModelMatrixCalculator.setDevice2IMUMatrix(translation, quaternion);
    }

    public void setColorCamera2IMUMatrix(float[] translation, float[] quaternion) {
        mModelMatrixCalculator.setColorCamera2IMUMatrix(translation, quaternion);
    }

    @Override
    public void draw(DrawParameters drawParameters) {
        GLES20.glUseProgram(mProgramHandle);

        // Set program handles for cone drawing.
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
        drawCone();
    }

    private void drawCone() {
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

        // Draw the cone.
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
