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

package android.opengl.cts;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLES31;
import android.opengl.GLES31Ext;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * {@link Activity} that queries the device's display attributes to determine what version of
 * OpenGL ES is supported and returns what the GL version string reports.
 */
public class OpenGlEsVersionCtsActivity extends Activity {
    private static String TAG = "OpenGlEsVersionCtsActivity";

    private static final String EGL_CONTEXT_CLIENT_VERSION = "eglContextClientVersion";

    /** Timeout to wait for the surface to be created and the version queried. */
    private static final int TIMEOUT_SECONDS = 10;

    /** Version string reported by glGetString. */
    private String mVersionString;

    /** Extensions string reported by glGetString. */
    private String mExtensionsString;

    /** Whether GL_ANDROID_extension_pack_es31a is correctly supported. */
    private boolean mAepEs31Support = false;

    /** Latch that is unlocked when the activity is done finding the version. */
    private CountDownLatch mSurfaceCreatedLatch = new CountDownLatch(1);

    private GLSurfaceView mView;

    public static Intent createIntent(int eglContextClientVersion) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.putExtra(EGL_CONTEXT_CLIENT_VERSION, eglContextClientVersion);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mView = new GLSurfaceView(this);

        Intent intent = getIntent();
        int eglContextClientVersion = intent.getIntExtra(EGL_CONTEXT_CLIENT_VERSION, -1);
        if (eglContextClientVersion > 0) {
            mView.setEGLContextClientVersion(eglContextClientVersion);
        }

        mView.setRenderer(new Renderer());
        setContentView(mView);
    }

    @Override
    protected void onPause() {
        mView.onPause();
        super.onPause();
    }

    public String getVersionString() throws InterruptedException {
        mSurfaceCreatedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        synchronized (this) {
            return mVersionString;
        }
    }

    public String getExtensionsString() throws InterruptedException {
        mSurfaceCreatedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        synchronized (this) {
            return mExtensionsString;
        }
    }

    public boolean getAepEs31Support() throws InterruptedException {
        mSurfaceCreatedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        synchronized (this) {
            return mAepEs31Support;
        }
    }

    public static boolean hasExtension(String extensions, String name) {
        int start = extensions.indexOf(name);
        while (start >= 0) {
            // check that we didn't find a prefix of a longer extension name
            int end = start + name.length();
            if (end == extensions.length() || extensions.charAt(end) == ' ') {
                return true;
            }
            start = extensions.indexOf(name, end);
        }
        return false;
    }

    private class Renderer implements GLSurfaceView.Renderer {
        /**
         * These shaders test at least one feature of each of the underlying extension, to verify
         * that enabling GL_ANDROID_extension_pack_es31a correctly enables all of them.
         */
        private final String mAepEs31VertexShader =
                "#version 310 es\n" +
                "#extension GL_ANDROID_extension_pack_es31a : require\n" +
                "void main() {\n" +
                "  gl_Position = vec4(1, 0, 0, 1);\n" +
                "}\n";

        private final String mAepEs31TessellationControlShader =
                "#version 310 es\n" +
                "#extension GL_ANDROID_extension_pack_es31a : require\n" +
                "layout(vertices = 3) out;\n" +  // GL_EXT_tessellation_shader
                "void main() {\n" +
                "  gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;\n" +  // GL_EXT_shader_io_blocks
                "  if (gl_InvocationID == 0) {\n" +
                "    gl_BoundingBoxEXT[0] = gl_in[0].gl_Position;\n" +  // GL_EXT_primitive_bounding_box
                "    gl_BoundingBoxEXT[1] = gl_in[1].gl_Position;\n" +
                "  }\n" +
                "}\n";

        private final String mAepEs31TessellationEvaluationShader =
                "#version 310 es\n" +
                "#extension GL_ANDROID_extension_pack_es31a : require\n" +
                "layout(triangles, equal_spacing, cw) in;\n" +
                "void main() {\n" +
                "  gl_Position = gl_in[0].gl_Position * gl_TessCoord.x +\n" +
                "      gl_in[1].gl_Position * gl_TessCoord.y +\n" +
                "      gl_in[2].gl_Position * gl_TessCoord.z;\n" +
                "}\n";

        private final String mAepEs31GeometryShader =
                "#version 310 es\n" +
                "#extension GL_ANDROID_extension_pack_es31a : require\n" +
                "layout(triangles) in;\n" +  // GL_EXT_geometry_shader
                "layout(triangle_strip, max_vertices = 3) out;\n" +
                "sample out vec4 perSampleColor;\n" +
                "void main() {\n" +
                "  for (int i = 0; i < gl_in.length(); ++i) {\n" +
                "    gl_Position = gl_in[i].gl_Position;\n" +
                "    perSampleColor = gl_in[i].gl_Position;\n" +
                "    EmitVertex();\n" +
                "  }\n" +
                "}\n";

        private final String mAepEs31FragmentShader =
                "#version 310 es\n" +
                "#extension GL_ANDROID_extension_pack_es31a : require\n" +
                "precision mediump float;\n" +
                "layout(blend_support_all_equations) out;\n" +  // GL_KHR_blend_equation_advanced
                "sample in vec4 perSampleColor;\n" +  // GL_OES_shader_multisample_interpolation
                "layout(r32ui) coherent uniform mediump uimage2D image;\n" +
                "uniform mediump sampler2DMSArray mySamplerMSArray;\n" +  // GL_OES_texture_storage_multisample_2d_array
                "uniform mediump samplerBuffer mySamplerBuffer;\n" +  // GL_EXT_texture_buffer
                "uniform mediump samplerCubeArray mySamplerCubeArray;\n" +  // GL_EXT_texture_cube_map_array
                "out vec4 color;\n" +
                "void main() {\n" +
                "  imageAtomicAdd(image, ivec2(1, 1), 1u);\n" +  // GL_OES_shader_image_atomic
                "  vec4 color = vec4(gl_SamplePosition.x, 0, 0, 1);\n" +  // GL_OES_sample_variables
                "  vec4 color2 = texelFetch(mySamplerMSArray, ivec3(1, 1, 1), 3);\n" +
                "  vec4 color3 = texelFetch(mySamplerBuffer, 3);\n" +
                "  vec4 color4 = texture(mySamplerCubeArray, vec4(1, 1, 1, 1));\n" +
                "  color = fma(color + color2, color3 + color4, perSampleColor);" +  // GL_EXT_gpu_shader5
                "}\n";

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            synchronized (OpenGlEsVersionCtsActivity.this) {
                try {
                    mVersionString = gl.glGetString(GL10.GL_VERSION);
                    mExtensionsString = gl.glGetString(GL10.GL_EXTENSIONS);
                    if (hasExtension(mExtensionsString, "ANDROID_extension_pack_es31a"))
                        mAepEs31Support = checkAepEs31Support();
                } finally {
                    mSurfaceCreatedLatch.countDown();
                }
            }
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
        }

        public void onDrawFrame(GL10 gl) {
        }

        private boolean compileShaderAndAttach(int program, int shaderType, String source) {
            int shader = GLES31.glCreateShader(shaderType);
            if (shader == 0) {
                Log.e(TAG, "Unable to create shaders of type " + shaderType);
                return false;
            }
            GLES31.glShaderSource(shader, source);
            GLES31.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Unable to compile shader " + shaderType + ":");
                Log.e(TAG, GLES31.glGetShaderInfoLog(shader));
                GLES31.glDeleteShader(shader);
                return false;
            }
            GLES31.glAttachShader(program, shader);
            GLES31.glDeleteShader(shader);
            return true;
        }

        private boolean checkAepEs31Support() {
            final String requiredList[] = {
                "EXT_copy_image",
                "EXT_draw_buffers_indexed",
                "EXT_geometry_shader",
                "EXT_gpu_shader5",
                "EXT_primitive_bounding_box",
                "EXT_shader_io_blocks",
                "EXT_tessellation_shader",
                "EXT_texture_border_clamp",
                "EXT_texture_buffer",
                "EXT_texture_cube_map_array",
                "EXT_texture_sRGB_decode",
                "KHR_blend_equation_advanced",
                "KHR_debug",
                "KHR_texture_compression_astc_ldr",
                "OES_sample_shading",
                "OES_sample_variables",
                "OES_shader_image_atomic",
                "OES_shader_multisample_interpolation",
                "OES_texture_stencil8",
                "OES_texture_storage_multisample_2d_array"
            };

            for (int i = 0; i < requiredList.length; ++i) {
                if (!hasExtension(mExtensionsString, requiredList[i])) {
                    Log.e(TAG,"ANDROID_extension_pack_es31a is present but extension " +
                            requiredList[i] + " is missing");
                    return false;
                }
            }

            int[] value = new int[1];
            GLES31.glGetIntegerv(GLES31.GL_MAX_FRAGMENT_ATOMIC_COUNTER_BUFFERS, value, 0);
            if (value[0] < 1) {
                Log.e(TAG, "ANDROID_extension_pack_es31a is present, but the " +
                        "GL_MAX_FRAGMENT_ATOMIC_COUNTER_BUFFERS value is " + value[0] + " < 1");
                return false;
            }
            GLES31.glGetIntegerv(GLES31.GL_MAX_FRAGMENT_ATOMIC_COUNTERS, value, 0);
            if (value[0] < 8) {
                Log.e(TAG, "ANDROID_extension_pack_es31a is present, but the " +
                        "GL_MAX_FRAGMENT_ATOMIC_COUNTERS value is " + value[0] + " < 8");
                return false;
            }
            GLES31.glGetIntegerv(GLES31.GL_MAX_FRAGMENT_IMAGE_UNIFORMS, value, 0);
            if (value[0] < 4) {
                Log.e(TAG, "ANDROID_extension_pack_es31a is present, but the " +
                        "GL_MAX_FRAGMENT_IMAGE_UNIFORMS value is " + value[0] + " < 4");
                return false;
            }
            GLES31.glGetIntegerv(GLES31.GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS, value, 0);
            if (value[0] < 4) {
                Log.e(TAG, "ANDROID_extension_pack_es31a is present, but the " +
                        "GL_MAX_FRAGMENT_SHADER_STORAGE_BLOCKS value is " + value[0] + " < 4");
                return false;
            }

            int program = GLES31.glCreateProgram();
            try {
                if (!compileShaderAndAttach(program, GLES31.GL_VERTEX_SHADER, mAepEs31VertexShader) ||
                    !compileShaderAndAttach(program, GLES31Ext.GL_TESS_CONTROL_SHADER_EXT, mAepEs31TessellationControlShader) ||
                    !compileShaderAndAttach(program, GLES31Ext.GL_TESS_EVALUATION_SHADER_EXT, mAepEs31TessellationEvaluationShader) ||
                    !compileShaderAndAttach(program, GLES31Ext.GL_GEOMETRY_SHADER_EXT, mAepEs31GeometryShader) ||
                    !compileShaderAndAttach(program, GLES31.GL_FRAGMENT_SHADER, mAepEs31FragmentShader))
                    return false;

                GLES31.glLinkProgram(program);
                GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, value, 0);
                if (value[0] == 0) {
                    Log.e(TAG, "Unable to link program :");
                    Log.e(TAG, GLES31.glGetProgramInfoLog(program));
                    return false;
                }
            } finally {
                GLES31.glDeleteProgram(program);
            }
            return true;
        }
    }
}
