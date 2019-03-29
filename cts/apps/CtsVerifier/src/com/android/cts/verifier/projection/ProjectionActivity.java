/*
 * Copyright (C) 2014 The Android Open Source Project
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


package com.android.cts.verifier.projection;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnTouchListener;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Base activity for each projection test case. Handles the service connection and TextureView
 * listeners
 */
public abstract class ProjectionActivity extends PassFailButtons.Activity
        implements TextureView.SurfaceTextureListener {
    private static final String TAG = ProjectionActivity.class.getSimpleName();
    protected Intent mStartIntent;
    protected TextureView mTextureView;
    protected volatile Surface mSurface;
    protected int mWidth;
    protected int mHeight;
    protected ProjectionPresentationType mType;

    protected IProjectionService mService;
    protected final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = IProjectionService.Stub.asInterface(binder);
            new Handler().post(new Runnable() {

                @Override
                public void run() {
                    Log.i(TAG, "onServiceConnected thread " + Thread.currentThread());
                    DisplayMetrics metrics = ProjectionActivity.this.getResources().getDisplayMetrics();
                    try {
                        mService.startRendering(mSurface, mWidth, mHeight, metrics.densityDpi, mType.ordinal());
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to execute startRendering", e);
                    }
                }

            });

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        mStartIntent = new Intent(this, ProjectionService.class);

    }

    protected View setContentViewAndInfoResources(int layoutId, int titleId, int infoId) {
        View view = getLayoutInflater().inflate(layoutId, null);
        setContentView(view);

        mTextureView = (TextureView) view.findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (mService != null) {
                    try {
                        mService.onTouchEvent(event);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to execute onTouchEvent", e);
                    }
                }
                return true;
            }

        });

        setInfoResources(titleId, infoId, -1);
        setPassFailButtonClickListeners();
        return view;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable " + "w: " + width + " h: " + height);
        mSurface = new Surface(surface);
        mWidth = width;
        mHeight = height;
        if (mService == null) {
            bindService(mStartIntent, mConnection, Context.BIND_AUTO_CREATE);
        }

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureDestroyed");
        if (mService != null) {
            try {
                mService.stopRendering();
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to execute stopRendering", e);
            }
        }
        mSurface.release();
        mSurface = null;

        unbindService(mConnection);
        mService = null;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureSizeChanged " + surface.toString() + "w: " + width + " h: "
                + height);
        mWidth = width;
        mHeight = height;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        Log.i(TAG, "onSurfaceTextureUpdated " + surface.toString());
    }

}
