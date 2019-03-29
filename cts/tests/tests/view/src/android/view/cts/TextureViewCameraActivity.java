/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import junit.framework.Assert;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class TextureViewCameraActivity extends Activity implements
        TextureView.SurfaceTextureListener {
    private static final String TAG = "TextureViewCameraActivity";

    private static final int CAPTURE_SCREEN_INTERVAL = 10;
    private static final float SCREEN_ROTATION_RATE = 1.0f;
    private static final int MAX_FRAME_UPDATE = 40;

    private Camera mCamera;
    private TextureView mTextureView;
    private int mUpdateCounter = 0;
    private int mWidth;
    private int mHeight;
    private float mRotation = 0f;
    private final CountDownLatch mLatch = new CountDownLatch(1);


    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Assert.assertTrue(mTextureView.getLayerType() == View.LAYER_TYPE_HARDWARE);
        Assert.assertTrue(mTextureView.isAvailable());
        Assert.assertNotNull(mTextureView.getSurfaceTexture());
        Assert.assertTrue(mTextureView.getSurfaceTextureListener() == this);
        Assert.assertTrue(mTextureView.isOpaque());
        mWidth = width;
        mHeight = height;
        if (Camera.getNumberOfCameras() > 0) {
            Log.d(TAG, "opening camera...");
            mCamera = Camera.open(0);
            Log.d(TAG, "surface texture available, opening a camera");
        } else {
            // no camera, and no frame update, so just complete here.
            Log.d(TAG, "no camera, test aborting");
            mLatch.countDown();
            return;
        }

        try {
            mCamera.setPreviewTexture(surface);
            mCamera.startPreview();
        } catch (IOException ioe) {
            // Something bad happened
            Assert.fail();
        }
    }

    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "surface texture size change " + width + " x " + height);
        mWidth = width;
        mHeight = height;
    }

    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "surface texture destroyed");
        if (mCamera != null) {
            Log.d(TAG, "stopping camera");
            mCamera.stopPreview();
            mCamera.release();
        }
        return true;
    }

    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.d(TAG, "surface texture updated");
        mUpdateCounter++;
        if (mUpdateCounter % CAPTURE_SCREEN_INTERVAL == 0) {
            Bitmap bitmap = mTextureView.getBitmap();
            Log.d(TAG, "acquired bitmap");
            Assert.assertEquals(mHeight, bitmap.getHeight());
            Assert.assertEquals(mWidth, bitmap.getWidth());
            bitmap.recycle();
            if (mUpdateCounter >= MAX_FRAME_UPDATE) {
                Log.d(TAG, "seen " + MAX_FRAME_UPDATE + " frames, test complete");
                mLatch.countDown();
            }
        }
        Matrix transformMatrix =  mTextureView.getTransform(null);
        mRotation += SCREEN_ROTATION_RATE;
        transformMatrix.setRotate(mRotation, mWidth / 2, mHeight / 2);
        mTextureView.setTransform(transformMatrix);
    }

    public boolean waitForCompletion(long timeoutInSecs) throws InterruptedException {
        return mLatch.await(timeoutInSecs, TimeUnit.SECONDS);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setOpaque(true);
        setContentView(mTextureView);
    }
}
