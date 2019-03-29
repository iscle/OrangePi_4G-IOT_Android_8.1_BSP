/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.opengl.EGL14;
import android.opengl.EGLDisplay;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.Log;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Stub activity to collect data from the GlesView */
public final class GlesStubActivity extends Activity {

    private static final String LOG_TAG = "GlesStubActivity";
    private int mVersion = -1;
    private GraphicsDeviceInfo mGraphicsDeviceInfo;
    private CountDownLatch mDone = new CountDownLatch(1);
    private HashSet<String> mOpenGlExtensions = new HashSet<>();
    private HashSet<String> mEglExtensions = new HashSet<>();
    private HashSet<String> mFormats = new HashSet<>();
    private HashMap<String, Object> mImplVariables = new HashMap<>();
    private HashSet<String> mDynamicArrayVariables = new HashSet<>();
    private String mGraphicsVendor;
    private String mGraphicsRenderer;

    @Override
    public void onCreate(Bundle bundle) {
        // Dismiss keyguard and keep screen on while this test is on.
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        super.onCreate(bundle);

        window.setFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo info = activityManager.getDeviceConfigurationInfo();

        mVersion = (info.reqGlEsVersion & 0xffff0000) >> 16;

        new Thread() {
            @Override
            public void run() {
                runIterations(mVersion);
            }
        }.start();
    }

     /**
     * Wait for this activity to finish gathering information
     */
    public void waitForActivityToFinish() {
        try {
            mDone.await();
        } catch (InterruptedException e) {
            // just move on
        }
    }

    private void runIterations(int glVersion) {
        for (int i = 1; i <= glVersion; i++) {
            final CountDownLatch done = new CountDownLatch(1);
            final int version = i;
            GlesStubActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setContentView(new GlesSurfaceView(GlesStubActivity.this, version, done));
                }
            });
            try {
                done.await();
            } catch (InterruptedException e) {
                // just move on
            }
        }
        mDone.countDown();
    }

    int getGlVersion() {
        return mVersion;
    }

    List<String> getOpenGlExtensions() {
        return new ArrayList<>(mOpenGlExtensions);
    }

    void addOpenGlExtension(String openGlExtension) {
        mOpenGlExtensions.add(openGlExtension);
    }

    List<String> getEglExtensions() {
        return new ArrayList<>(mEglExtensions);
    }

    void addEglExtensions(String[] eglExtensions) {
        // NOTE: We may end up here multiple times, using set to avoid dupes.
        mEglExtensions.addAll(Arrays.asList(eglExtensions));
    }

    List<String> getCompressedTextureFormats() {
        return new ArrayList<>(mFormats);
    }

    void addCompressedTextureFormat(String format) {
        mFormats.add(format);
    }

    String getVendor() {
        return mGraphicsVendor;
    }

    void setVendor(String vendor) {
        mGraphicsVendor = vendor;
    }

    String getRenderer() {
        return mGraphicsRenderer;
    }

    void setRenderer(String renderer) {
        mGraphicsRenderer = renderer;
    }

    public Set<String> getImplementationVariableNames() {
        return mImplVariables.keySet();
    }

    public Object getImplementationVariable(String name) {
        return mImplVariables.get(name);
    }

    public boolean isDynamicArrayVariable(String name) {
        return mDynamicArrayVariables.contains(name);
    }

    void addImplementationVariable(String name, Object value, boolean isDynamicArray) {
        mImplVariables.put(name, value);
        if (isDynamicArray) {
            mDynamicArrayVariables.add(name);
        }
    }

    static class GlesSurfaceView extends GLSurfaceView {

        public GlesSurfaceView(GlesStubActivity parent, int glVersion, CountDownLatch done) {
            super(parent);

            if (glVersion > 1) {
                // Default is 1 so only set if bigger than 1
                setEGLContextClientVersion(glVersion);
            }
            setRenderer(new OpenGlesRenderer(parent, glVersion, done));
        }
    }

    static abstract class ImplementationVariable {
        private Field mField;
        public ImplementationVariable(String fieldName) {
            try {
                mField = GLES30.class.getField(fieldName);
            } catch (NoSuchFieldException e) {
                Log.e(LOG_TAG, "Failed to get field reflection", e);
            }
        }

        public String getName() {
            return mField.getName();
        }

        public int getFieldIdValue() throws IllegalAccessException {
            return mField.getInt(null);
        }

        abstract public Object getValue();

        static protected int[] getIntValues(int fieldId, int count) throws IllegalAccessException{
            int[] resultInts = new int[count];
            // The JNI wrapper layer has a piece of code that defines
            // the expected array length. It defaults to 1 and looks
            // like it's missing GLES3 variables. So, we won't be
            // querying if the array has zero lenght.
            if (count > 0) {
                GLES20.glGetIntegerv(fieldId, resultInts, 0);
            }
            return resultInts;
        }
    }

    static class IntVectorValue extends ImplementationVariable {
        private int mCount;

        public IntVectorValue(String fieldName, int count) {
            super(fieldName);
            mCount = count;
        }

        @Override
        public Object getValue() {
            Log.i(LOG_TAG, "Getting : " + this.getName() + " " + mCount);
            try {
                return getIntValues(this.getFieldIdValue(), mCount);
            } catch (IllegalAccessException e) {
                Log.e(LOG_TAG, "Failed to read the GL field", e);
            }
            return null;
        }
    }

    static class DynamicIntVectorValue extends ImplementationVariable {
        private Field mCountField;

        public DynamicIntVectorValue(String fieldName, String countFieldName) {
            super(fieldName);
            try {
                mCountField = GLES30.class.getField(countFieldName);
            } catch (NoSuchFieldException e) {
                Log.e(LOG_TAG, "Failed to get field reflection", e);
            }
        }

        @Override
        public Object getValue() {
            Log.i(LOG_TAG, "Getting : " + this.getName() + " " + mCountField.getName());
            try {
                int[] count = new int[] {0};
                GLES20.glGetIntegerv(mCountField.getInt(null), count, 0);
                Log.i(LOG_TAG, "Getting : " + mCountField.getName() + " " + count[0]);
                return getIntValues(this.getFieldIdValue(), count[0]);
            } catch (IllegalAccessException e) {
                Log.e(LOG_TAG, "Failed to read the GL field", e);
            }
            return null;
        }
    }

    static class FloatVectorValue extends ImplementationVariable {
        private int mCount;

        public FloatVectorValue(String fieldName, int count) {
            super(fieldName);
            mCount = count;
        }

        @Override
        public Object getValue() {
            Log.i(LOG_TAG, "Getting : " + this.getName() + " " + mCount);
            try {
                float[] result = new float[mCount];
                GLES20.glGetFloatv(getFieldIdValue(), result, 0);
                return result;
            } catch (IllegalAccessException e) {
                Log.e(LOG_TAG, "Failed to read the GL field", e);
            }
            return null;
        }
    }

    static class LongVectorValue extends ImplementationVariable {
        private int mCount;

        public LongVectorValue(String fieldName, int count) {
            super(fieldName);
            mCount = count;
        }

        @Override
        public Object getValue() {
            Log.i(LOG_TAG, "Getting : " + this.getName() + " " + mCount);
            try {
                long result[] = new long[mCount];
                GLES30.glGetInteger64v(getFieldIdValue(), result, 0);
                return result;
            } catch (IllegalAccessException e) {
                Log.e(LOG_TAG, "Failed to read the GL field", e);
            }
            return null;
        }
    }

    static class StringValue extends ImplementationVariable {
        public StringValue(String fieldName) {
            super(fieldName);
        }

        @Override
        public Object getValue() {
            Log.i(LOG_TAG, "Getting : " + this.getName());
            String result = null;
            try {
                result = GLES20.glGetString(this.getFieldIdValue());
            } catch (IllegalAccessException e) {
                Log.e(LOG_TAG, "Failed to read the GL field", e);
            }
            return result;
        }
    }

    // NOTE: Changes to the types of the variables will carry over to
    // GraphicsDeviceInfo proto via GraphicsDeviceInfo. See
    // go/edi-userguide for details.
    static ImplementationVariable[] GLES2_IMPLEMENTATION_VARIABLES = {
        new IntVectorValue("GL_SUBPIXEL_BITS", 1),
        new IntVectorValue("GL_MAX_TEXTURE_SIZE", 1),
        new IntVectorValue("GL_MAX_CUBE_MAP_TEXTURE_SIZE", 1),
        new IntVectorValue("GL_MAX_VIEWPORT_DIMS", 2),
        new FloatVectorValue("GL_ALIASED_POINT_SIZE_RANGE", 2),
        new FloatVectorValue("GL_ALIASED_LINE_WIDTH_RANGE", 2),
        new DynamicIntVectorValue("GL_COMPRESSED_TEXTURE_FORMATS", "GL_NUM_COMPRESSED_TEXTURE_FORMATS"),
        new DynamicIntVectorValue("GL_SHADER_BINARY_FORMATS", "GL_NUM_SHADER_BINARY_FORMATS"),
        new IntVectorValue("GL_SHADER_COMPILER", 1),
        new StringValue("GL_SHADING_LANGUAGE_VERSION"),
        new StringValue("GL_VERSION"),
        new IntVectorValue("GL_MAX_VERTEX_ATTRIBS", 1),
        new IntVectorValue("GL_MAX_VERTEX_UNIFORM_VECTORS", 1),
        new IntVectorValue("GL_MAX_VARYING_VECTORS", 1),
        new IntVectorValue("GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS", 1),
        new IntVectorValue("GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS", 1),
        new IntVectorValue("GL_MAX_TEXTURE_IMAGE_UNITS", 1),
        new IntVectorValue("GL_MAX_FRAGMENT_UNIFORM_VECTORS", 1),
        new IntVectorValue("GL_MAX_RENDERBUFFER_SIZE", 1)
    };

    static ImplementationVariable[] GLES3_IMPLEMENTATION_VARIABLES = {
        new LongVectorValue("GL_MAX_ELEMENT_INDEX", 1),
        new IntVectorValue("GL_MAX_3D_TEXTURE_SIZE", 1),
        new IntVectorValue("GL_MAX_ARRAY_TEXTURE_LAYERS", 1),
        new FloatVectorValue("GL_MAX_TEXTURE_LOD_BIAS", 1),
        new IntVectorValue("GL_MAX_DRAW_BUFFERS", 1),
        new IntVectorValue("GL_MAX_COLOR_ATTACHMENTS", 1),
        new IntVectorValue("GL_MAX_ELEMENTS_INDICES", 1),
        new IntVectorValue("GL_MAX_ELEMENTS_VERTICES", 1),
        new DynamicIntVectorValue("GL_PROGRAM_BINARY_FORMATS", "GL_NUM_PROGRAM_BINARY_FORMATS"),
        new LongVectorValue("GL_MAX_SERVER_WAIT_TIMEOUT", 1),
        new IntVectorValue("GL_MAJOR_VERSION", 1),
        new IntVectorValue("GL_MINOR_VERSION", 1),
        new IntVectorValue("GL_MAX_VERTEX_UNIFORM_COMPONENTS", 1),
        new IntVectorValue("GL_MAX_VERTEX_UNIFORM_BLOCKS", 1),
        new IntVectorValue("GL_MAX_VERTEX_OUTPUT_COMPONENTS", 1),
        new IntVectorValue("GL_MAX_FRAGMENT_UNIFORM_COMPONENTS", 1),
        new IntVectorValue("GL_MAX_FRAGMENT_UNIFORM_BLOCKS", 1),
        new IntVectorValue("GL_MAX_FRAGMENT_INPUT_COMPONENTS", 1),
        new IntVectorValue("GL_MIN_PROGRAM_TEXEL_OFFSET", 1),
        new IntVectorValue("GL_MAX_PROGRAM_TEXEL_OFFSET", 1),
        new IntVectorValue("GL_MAX_UNIFORM_BUFFER_BINDINGS", 1),
        new LongVectorValue("GL_MAX_UNIFORM_BLOCK_SIZE", 1),
        new IntVectorValue("GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT", 1),
        new IntVectorValue("GL_MAX_COMBINED_UNIFORM_BLOCKS", 1),
        new LongVectorValue("GL_MAX_COMBINED_VERTEX_UNIFORM_COMPONENTS", 1),
        new LongVectorValue("GL_MAX_COMBINED_FRAGMENT_UNIFORM_COMPONENTS", 1),
        new IntVectorValue("GL_MAX_VARYING_COMPONENTS", 1),
        new IntVectorValue("GL_MAX_TRANSFORM_FEEDBACK_INTERLEAVED_COMPONENTS", 1),
        new IntVectorValue("GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_ATTRIBS", 1),
        new IntVectorValue("GL_MAX_TRANSFORM_FEEDBACK_SEPARATE_COMPONENTS", 1)
    };

    static class OpenGlesRenderer implements GLSurfaceView.Renderer {

        private final GlesStubActivity mParent;
        private final int mGlVersion;
        private final CountDownLatch mDone;

        OpenGlesRenderer(GlesStubActivity parent, int glVersion, CountDownLatch done) {
            mParent = parent;
            mGlVersion = glVersion;
            mDone = done;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            String extensions;
            String vendor;
            String renderer;
            if (mGlVersion == 2) {
                extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
                vendor = GLES20.glGetString(GLES20.GL_VENDOR);
                renderer = GLES20.glGetString(GLES20.GL_RENDERER);
                collectImplementationVariables(GLES2_IMPLEMENTATION_VARIABLES);
            } else if (mGlVersion == 3) {
                extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
                vendor = GLES30.glGetString(GLES30.GL_VENDOR);
                renderer = GLES30.glGetString(GLES30.GL_RENDERER);
                collectImplementationVariables(GLES3_IMPLEMENTATION_VARIABLES);
            } else {
                extensions = gl.glGetString(GL10.GL_EXTENSIONS);
                vendor = gl.glGetString(GL10.GL_VENDOR);
                renderer = gl.glGetString(GL10.GL_RENDERER);
            }
            mParent.setVendor(vendor);
            mParent.setRenderer(renderer);
            Scanner scanner = new Scanner(extensions);
            scanner.useDelimiter(" ");
            while (scanner.hasNext()) {
                String ext = scanner.next();
                mParent.addOpenGlExtension(ext);
                if (ext.contains("texture")) {
                    if (ext.contains("compression") || ext.contains("compressed")) {
                        mParent.addCompressedTextureFormat(ext);
                    }
                }
            }
            scanner.close();

            collectEglExtensions(mParent);

            mDone.countDown();
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {}

        @Override
        public void onDrawFrame(GL10 gl) {}

        private void collectImplementationVariables(ImplementationVariable[] variables) {
            for (int i = 0; i < variables.length; i++) {
                String name = variables[i].getName();
                Object value = variables[i].getValue();
                boolean dynamicArray = variables[i] instanceof DynamicIntVectorValue;
                mParent.addImplementationVariable(name, value, dynamicArray);
            }
        }

        private static void collectEglExtensions(GlesStubActivity collector) {
            EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) {
                Log.e(LOG_TAG, "Failed to init EGL default display: 0x" +
                        Integer.toHexString(EGL14.eglGetError()));
                return;
            }
            String extensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS);
            int error = EGL14.eglGetError();
            if (error != EGL14.EGL_SUCCESS) {
                Log.e(LOG_TAG, "Failed to query extension string: 0x" + Integer.toHexString(error));
                return;
            }
            // Fingers crossed for no extra white space in the extension string.
            collector.addEglExtensions(extensions.split(" "));
        }
    }
}
