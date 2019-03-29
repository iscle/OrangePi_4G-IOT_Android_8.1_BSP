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
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * A minimal activity for testing {@link android.opengl.GLSurfaceView}.
 * Also accepts non-blank renderers to allow its use for more complex tests.
 */
public class GLSurfaceViewCtsActivity extends Activity {

    private static class Renderer implements GLSurfaceView.Renderer {

        public void onDrawFrame(GL10 gl) {
            // Do nothing.
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            // Do nothing.
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // Do nothing.
        }
    }

    private GLSurfaceView mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mView = new GLSurfaceView(this);
        mView.setRenderer(new Renderer());
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(mView);
    }

    public GLSurfaceView getView() {
        return mView;
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
}
