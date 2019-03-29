/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.cts.verifier.sensors;

// ----------------------------------------------------------------------

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;
import java.lang.Math;

/** Camera preview class */
public class RVCVCameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final String TAG = "RVCVCameraPreview";
    private static final boolean LOCAL_LOGD = true;

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private float mAspect;
    private int mRotation;

    /**
     * Constructor
     * @param context Activity context
     */
    public RVCVCameraPreview(Context context) {
        super(context);
        mCamera = null;
        initSurface();
    }

    /**
     * Constructor
     * @param context Activity context
     * @param attrs
     */
    public RVCVCameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void init(Camera camera, float aspectRatio, int rotation)  {
        this.mCamera = camera;
        mAspect = aspectRatio;
        mRotation = rotation;
        initSurface();
    }

    private void initSurface() {
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);

        // deprecated
        // TODO: update this code to match new API level.
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    /**
     *  SurfaceHolder.Callback
     *  Surface is created, it is OK to start the camera preview now.
     */
    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.

        if (mCamera == null) {
            // preview camera does not exist
            return;
        }

        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
            int v_height = getHeight();
            int v_width = getWidth();
            ViewGroup.LayoutParams layout = getLayoutParams();
            if ( (float)v_height/v_width  >
                    mAspect) {
                layout.height = (int)Math.round(v_width * mAspect);
                layout.width = v_width;
            }else {
                layout.width = (int)Math.round(v_height / mAspect);
                layout.height = v_height;
            }
            Log.d(TAG, String.format("Layout (%d, %d) -> (%d, %d)", v_width, v_height,
                    layout.width, layout.height));
            setLayoutParams(layout);
        } catch (IOException e) {
            if (LOCAL_LOGD) Log.d(TAG, "Error when starting camera preview: " + e.getMessage());
        }
    }
    /**
     *  SurfaceHolder.Callback
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    /**
     *  SurfaceHolder.Callback
     *  Restart camera preview if surface changed
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {

        if (mHolder.getSurface() == null || mCamera == null){
            // preview surface or camera does not exist
            return;
        }

        // stop preview before making changes
        mCamera.stopPreview();

        mCamera.setDisplayOrientation(mRotation);

        //do the same as if it is created again
        surfaceCreated(holder);
    }
}
