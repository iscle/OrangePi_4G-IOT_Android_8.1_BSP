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
package com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Contains shader code and helper functions for compiling them.
 */
public class ShaderHelper {
    private static final String TAG = "ShaderHelper";

    public static String getCameraPreviewFragmentShader() {
        return "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "uniform samplerExternalOES u_Texture;\n" +
                "\n" +
                "varying vec3 v_Position;\n" +
                "varying vec2 v_TexCoordinate;\n" +
                "\n" +
                "void main()\n" +
                "{\n" +
                "    gl_FragColor = texture2D(u_Texture, v_TexCoordinate);\n" +
                "}";
    }

    public static String getCameraPreviewVertexShader() {
        return "uniform mat4 u_MVPMatrix;\n" +
                "uniform mat4 u_MVMatrix;\n" +
                "attribute vec4 a_Position;\n" +
                "attribute vec2 a_TexCoordinate;\n" +
                "\n" +
                "varying vec3 v_Position;\n" +
                "varying vec2 v_TexCoordinate;\n" +
                "\n" +
                "void main()\n" +
                "{\n" +
                "   v_Position = vec3(u_MVMatrix * a_Position);\n" +
                "\n" +
                "   v_TexCoordinate = a_TexCoordinate;\n" +
                "\n" +
                "   gl_Position = u_MVPMatrix * a_Position;\n" +
                "}";
    }

    public static String getRectangleFragmentShader() {
        return "precision mediump float;" +
                "varying vec4 v_Color;" +
                "varying vec3 v_Position;" +
                "void main() {" +
                "  gl_FragColor = v_Color;" +
                "}";

    }

    public static String getRectangleVertexShader() {
        return "uniform mat4 u_MVPMatrix;" +
                "uniform mat4 u_MVMatrix;" +
                "varying vec3 v_Position;" +
                "varying vec4 v_Color;" +
                "attribute vec4 a_Position;" +
                "attribute vec4 a_Color;" +
                "void main() {" +
                "   v_Position = vec3(u_MVMatrix * a_Position);" +
                "   v_Color = a_Color;" +
                "   gl_Position = u_MVPMatrix * a_Position;" +
                "}";
    }

    /**
     * Contains lighting information for shadows that enhance AR effect.
     *
     * @return the vertex shader.
     */
    public static String getAugmentedRealityVertexShader() {
        return "uniform mat4 u_MVPMatrix;\n"
                + "uniform mat4 u_MVMatrix;\n"

                + "attribute vec4 a_Position;\n"
                + "attribute vec4 a_Color;\n"
                + "attribute vec3 a_Normal;\n"

                + "varying vec3 v_Position;\n"
                + "varying vec4 v_Color;\n"
                + "varying vec3 v_Normal;\n"

                + "void main()\n"
                + "{\n"
                + "   v_Position = vec3(u_MVMatrix * a_Position);\n"
                + "   v_Color = a_Color;\n"
                + "   v_Normal = vec3(u_MVMatrix * vec4(a_Normal, 0.0));\n"
                + "   gl_Position = u_MVPMatrix * a_Position;\n"
                + "}\n";
    }

    /**
     * Contains lighting information for shadows that enhance AR effect.
     *
     * @return the fragment shader.
     */
    public static String getAugmentedRealityFragmentShader() {
        return "precision mediump float;\n"
                + "uniform vec3 u_LightPos;\n"
                + "uniform float u_LightStrength;\n"
                + "varying vec3 v_Position;\n"
                + "varying vec4 v_Color;\n"
                + "varying vec3 v_Normal;\n"

                + "void main()\n"
                + "{\n"
                + "   float distance = length(u_LightPos - v_Position);\n"
                + "   vec3 lightVector = normalize(u_LightPos - v_Position);\n"
                + "   float diffuse = max(dot(v_Normal, lightVector), 0.25);\n"
                + "   diffuse = diffuse * (u_LightStrength / (1.0 + (0.25 * distance * distance)));\n"
                + "   gl_FragColor = v_Color * diffuse;\n"
                + "}";
    }

    /**
     * Helper function to compile a shader.
     *
     * @param shaderType   The shader type.
     * @param shaderSource The shader source code.
     * @return An OpenGL handle to the shader.
     */
    public static int compileShader(final int shaderType, final String shaderSource) {
        int shaderHandle = GLES20.glCreateShader(shaderType);

        if (shaderHandle != 0) {
            // Pass in the shader source.
            GLES20.glShaderSource(shaderHandle, shaderSource);

            // Compile the shader.
            GLES20.glCompileShader(shaderHandle);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shaderHandle));
                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }

        if (shaderHandle == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shaderHandle;
    }

    /**
     * Helper function to compile and link a program.
     *
     * @param vertexShaderHandle   An OpenGL handle to an already-compiled vertex shader.
     * @param fragmentShaderHandle An OpenGL handle to an already-compiled fragment shader.
     * @param attributes           Attributes that need to be bound to the program.
     * @return An OpenGL handle to the program.
     */
    public static int createAndLinkProgram(final int vertexShaderHandle, final int fragmentShaderHandle, final String[] attributes) {
        int programHandle = GLES20.glCreateProgram();

        if (programHandle != 0) {
            // Bind the vertex shader to the program.
            GLES20.glAttachShader(programHandle, vertexShaderHandle);

            // Bind the fragment shader to the program.
            GLES20.glAttachShader(programHandle, fragmentShaderHandle);

            // Bind attributes
            if (attributes != null) {
                final int size = attributes.length;
                for (int i = 0; i < size; i++) {
                    GLES20.glBindAttribLocation(programHandle, i, attributes[i]);
                }
            }

            // Link the two shaders together into a program.
            GLES20.glLinkProgram(programHandle);

            // Get the link status.
            final int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(programHandle, GLES20.GL_LINK_STATUS, linkStatus, 0);

            // If the link failed, delete the program.
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Error compiling program: " + GLES20.glGetProgramInfoLog(programHandle));
                GLES20.glDeleteProgram(programHandle);
                programHandle = 0;
            }
        }

        if (programHandle == 0) {
            throw new RuntimeException("Error creating program.");
        }

        return programHandle;
    }
}
