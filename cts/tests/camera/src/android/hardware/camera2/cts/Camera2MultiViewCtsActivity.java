/*
 * Copyright 2014 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.WindowManager;

import android.camera.cts.R;

public class Camera2MultiViewCtsActivity extends Activity {
    private final static String TAG = "Camera2MultiViewCtsActivity";
    private TextureView[] mTextureView = new TextureView[2];
    private SurfaceView[] mSurfaceView = new SurfaceView[2];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.multi_view);
        mTextureView[0] = (TextureView) findViewById(R.id.texture_view_1);
        mTextureView[1] = (TextureView) findViewById(R.id.texture_view_2);
        mSurfaceView[0] = (SurfaceView) findViewById(R.id.surface_view_1);
        mSurfaceView[1] = (SurfaceView) findViewById(R.id.surface_view_2);

        //Make sure screen is on when this activity window is visible to the user.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public TextureView getTextureView(int index) {
        if (index < 0 || index > 1) {
            throw new IllegalArgumentException("Texture view index must be 0 or 1");
        }
        return mTextureView[index];
    }

    public SurfaceView getSurfaceView(int index) {
        if (index < 0 || index > 1) {
            throw new IllegalArgumentException("Surface view index must be 0 or 1");
        }
        return mSurfaceView[index];
    }
}
