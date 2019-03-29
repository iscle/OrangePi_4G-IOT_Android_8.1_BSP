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

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.CameraStreamManager;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.DrawParameters;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ShaderHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Shows the camera preview as an opengl texture.
 */
public class CameraPreviewRenderable extends Renderable {
    private static final String TAG = "CameraPreviewRenderable";
    private final int TEXTURE_COORDINATE_DATA_SIZE = 2;
    private static final float[] CAMERA_TEXTURE_DATA = {
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
    };
    private static final float[] CAMERA_PREVIEW_POSITION = {0.0f, 0.0f, -3.0f};

    private FloatBuffer mPositionBuffer;
    private FloatBuffer mTextureBuffer;

    private int mTextureUniformHandle;
    private int mTextureCoordinateHandle;

    protected int mCameraTextureId = -1;

    private SurfaceTexture mCameraSurfaceTexture;
    private Context mContext;
    private CameraStreamManager mCameraStreamManager;
    private boolean mInvertAxis;

    public CameraPreviewRenderable() {
        // Reset the model matrix to the identity and move it so the OpenGL camera is looking at it.
        Matrix.setIdentityM(getModelMatrix(), 0);
        Matrix.translateM(getModelMatrix(), 0,
                CAMERA_PREVIEW_POSITION[X], CAMERA_PREVIEW_POSITION[Y], CAMERA_PREVIEW_POSITION[Z]);
    }

    public void initialiseCameraPreview(float[] cameraPreviewPositionData, boolean invertAxis, Context context) {
        // float count / floats per vertex.
        mVertexCount = cameraPreviewPositionData.length / POSITION_DATA_SIZE;

        // Initialize the buffers.
        mPositionBuffer = ByteBuffer.allocateDirect(cameraPreviewPositionData.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        mTextureBuffer = ByteBuffer.allocateDirect(CAMERA_TEXTURE_DATA.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        mPositionBuffer.put(cameraPreviewPositionData).position(0);
        mTextureBuffer.put(CAMERA_TEXTURE_DATA).position(0);

        final String vertexShader = ShaderHelper.getCameraPreviewVertexShader();
        final String fragmentShader = ShaderHelper.getCameraPreviewFragmentShader();

        final int vertexShaderHandle =
                ShaderHelper.compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        final int fragmentShaderHandle =
                ShaderHelper.compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        mProgramHandle = ShaderHelper.createAndLinkProgram(vertexShaderHandle, fragmentShaderHandle,
                new String[]{"a_Position", "a_TexCoordinate"});

        mContext = context;
        mInvertAxis = invertAxis;
        connectCamera();
    }

    @Override
    public void draw(DrawParameters drawParameters) {
        GLES20.glUseProgram(mProgramHandle);

        // Set program handles for camera preview drawing.
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVPMatrix");
        mMVMatrixHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_MVMatrix");
        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgramHandle, "u_Texture");
        mPositionHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_Position");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgramHandle, "a_TexCoordinate");

        // Set the active texture unit to texture unit 0.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind the texture to this unit.
        if (mCameraTextureId != -1) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureId);
        }

        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        // Compose the model, view, and projection matrices into a single m-v-p matrix
        updateMvpMatrix(drawParameters.getViewMatrix(), drawParameters.getProjectionMatrix());

        drawCameraPreview();
    }

    /**
     * Draws a camera preview.
     */
    private void drawCameraPreview() {
        // Pass in the position information
        mPositionBuffer.position(0);
        GLES20.glVertexAttribPointer(mPositionHandle, POSITION_DATA_SIZE, GLES20.GL_FLOAT, false,
                0, mPositionBuffer);

        GLES20.glEnableVertexAttribArray(mPositionHandle);

        // Pass in the texture coordinate information
        mTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, TEXTURE_COORDINATE_DATA_SIZE, GLES20.GL_FLOAT, false,
                0, mTextureBuffer);

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);

        // Pass in the modelview matrix.
        GLES20.glUniformMatrix4fv(mMVMatrixHandle, 1, false, getMvMatrix(), 0);

        // Pass in the combined matrix.
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, getMvpMatrix(), 0);

        // Draw the camera preview.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mVertexCount);
    }

    /**
     * Updates the texture with the latest camera data.
     *
     * @return the timestamp of the RGB image rendered into the texture.
     */
    public synchronized double updateTexture() {
        double latestCameraFrameTimestamp = -1.0;
        if (mCameraTextureId != -1) {
            // Copy the camera frame from the camera to the OpenGL texture
            mCameraSurfaceTexture.updateTexImage();
            latestCameraFrameTimestamp = mCameraSurfaceTexture.getTimestamp();
        }
        return latestCameraFrameTimestamp;
    }

    /**
     * Connects the camera to the OpenGl context
     */
    public void connectCamera() {
        this.mCameraTextureId = connectCameraTexture();
    }

    public void disconnectCamera() {
        mCameraStreamManager.onStopCameraStream();
    }

    /**
     * Connects a texture to an Android camera
     *
     * @return textureId of texture with camera attached/
     */
    private int connectCameraTexture() {
        if (mCameraTextureId == -1) {
            mCameraTextureId = createEmptyTexture();
            mCameraSurfaceTexture = new SurfaceTexture(mCameraTextureId);
            int width = mInvertAxis ? 1080 : 1920;
            int height = mInvertAxis ? 1920 : 1080;
            mCameraStreamManager = new CameraStreamManager(mContext, mCameraSurfaceTexture, width, height);
            mCameraStreamManager.onStartCameraStream();
        }
        return mCameraTextureId;
    }

    /**
     * Creates an empty texture.
     *
     * @return textureId of empty texture.
     */
    public static int createEmptyTexture() {
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            return textureHandle[0];
        }

        return -1;
    }

    @Override
    public void destroy() {
        if (mCameraStreamManager != null) {
            mCameraStreamManager.onStopCameraStream();
            mCameraStreamManager = null;
        }

        mPositionBuffer = null;
        mTextureBuffer = null;
    }
}
