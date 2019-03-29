/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.graphics.cts;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * An activity for testing camera output rendering.
 */
public class CameraGpuCtsActivity extends Activity {

    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static final String TAG = "CameraGpuCtsActivity";

    protected GLSurfaceView mView;
    protected long mNativeRenderer;
    private CountDownLatch mFinishedRendering;

    private class Renderer implements GLSurfaceView.Renderer {
        public void onDrawFrame(GL10 gl) {
            if (nDrawFrame(mNativeRenderer) == 0) {
                mFinishedRendering.countDown();
            }
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            // Do nothing.
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            mNativeRenderer = nCreateRenderer();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mView = new GLSurfaceView(this);
        mView.setEGLContextClientVersion(2);
        mView.setRenderer(new Renderer());
        mView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Wait for 100 frames from camera being rendered.
        mFinishedRendering = new CountDownLatch(100);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(mView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nDestroyRenderer(mNativeRenderer);
    }

    public void waitToFinishRendering() throws InterruptedException {
        // Wait long enough so that all frames are captured.
        if (!mFinishedRendering.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Coudn't finish drawing frames!");
        }
    }

    private static native long nCreateRenderer();
    private static native void nDestroyRenderer(long renderer);
    private static native int nDrawFrame(long renderer);
}
