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

package android.view.cts;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;

/**
 * A minimal activity for testing {@link android.opengl.GLSurfaceView}.
 * Also accepts non-blank renderers to allow its use for more complex tests.
 */
public abstract class GLSurfaceViewCtsActivity extends Activity {
    protected GLSurfaceView mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mView = new GLSurfaceView(this);
        configureGLSurfaceView();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(mView);
    }

    protected abstract void configureGLSurfaceView();

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
