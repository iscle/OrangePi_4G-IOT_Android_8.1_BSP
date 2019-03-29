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
package android.vr.cts;

import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;

import java.util.concurrent.CountDownLatch;

public class RendererProtectedTexturesTest extends RendererBasicTest {

    private String vertexShaderCode = "attribute vec4 vPosition; \n"
            + "void main(){              \n"
            +    "gl_Position = vPosition; \n"
            + "}                         \n";

    private String fragmentShaderCode = "precision mediump float;  \n"
            + "uniform sampler2D protectedTexture;\n"
            + "void main(){              \n"
            + " gl_FragColor = texture2D(protectedTexture, vec2(0.76953125, 0.22265625)); \n"
            + "}  \n";

    public static int GL_CONTEXT_FLAG_PROTECTED_CONTENT_BIT = 0x00000010;
    public static int GL_TEXTURE_PROTECTED_EXT = 0x8BFA;

    private IntBuffer mTexture;

    public RendererProtectedTexturesTest(CountDownLatch latch) {
        super(latch);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f);
        initShapes();
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(mProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
           //do nothing
        }

        // Create and bind a protected texture.
        mTexture = IntBuffer.allocate(1);
        GLES20.glGenTextures(1, mTexture);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexture.get(0));
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GL_TEXTURE_PROTECTED_EXT, GLES20.GL_TRUE);
        int loc = GLES20.glGetUniformLocation(mProgram, "protectedTexture");
        GLES20.glUniform1i(loc, 2);
    }
}
