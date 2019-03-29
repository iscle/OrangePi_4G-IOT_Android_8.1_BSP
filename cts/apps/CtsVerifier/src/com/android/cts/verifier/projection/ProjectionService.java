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

import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import com.android.cts.verifier.projection.cube.CubePresentation;
import com.android.cts.verifier.projection.list.ListPresentation;
import com.android.cts.verifier.projection.offscreen.OffscreenPresentation;
import com.android.cts.verifier.projection.touch.TouchPresentation;
import com.android.cts.verifier.projection.video.VideoPresentation;
import com.android.cts.verifier.projection.widgets.WidgetPresentation;

/**
 * Service to handle rendering of views on a virtual display and to forward input events to the
 * display
 */
public class ProjectionService extends Service {
    private final String TAG = ProjectionService.class.getSimpleName();
    private final String DISPLAY_NAME = "CtsVerifier Virtual Display";

    private Handler mUIHandler;

    private ProjectedPresentation createPresentation(int typeOrdinal) {
        ProjectionPresentationType type = ProjectionPresentationType.values()[typeOrdinal];
        switch (type) {
            case TUMBLING_CUBES:
                return new CubePresentation(ProjectionService.this, mDisplay.getDisplay());

            case BASIC_WIDGETS:
                return new WidgetPresentation(ProjectionService.this, mDisplay.getDisplay());

            case SCROLLING_LIST:
                return new ListPresentation(ProjectionService.this, mDisplay.getDisplay());

            case VIDEO_PLAYBACK:
                return new VideoPresentation(ProjectionService.this, mDisplay.getDisplay());

            case MULTI_TOUCH:
                return new TouchPresentation(ProjectionService.this, mDisplay.getDisplay());

            case OFFSCREEN:
                return new OffscreenPresentation(ProjectionService.this, mDisplay.getDisplay());
        }

        return null;
    }

    private class ProjectionServiceBinder extends IProjectionService.Stub {
        @Override
        public void startRendering(final Surface surface, final int width, final int height,
                final int density,
                final int viewType) throws RemoteException {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
                    Log.i(TAG, "Surface " + surface.toString() + ": "
                            + Boolean.toString(surface.isValid()));
                    mDisplay = manager.createVirtualDisplay(DISPLAY_NAME, width, height, density,
                            surface, 0);
                    mPresentation = createPresentation(viewType);
                    if (mPresentation == null) {
                        return;
                    }

                    mPresentation.show();
                }
            });
        }

        @Override
        public void stopRendering() throws RemoteException {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (mPresentation != null) {
                        mPresentation.dismiss();
                        mPresentation = null;
                    }
                    if (mDisplay != null) {
                        mDisplay.release();
                        mDisplay = null;
                    }
                }

            });
        }

        @Override
        public void onTouchEvent(final MotionEvent event) throws RemoteException {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (mPresentation != null) {
                        mPresentation.injectTouchEvent(event);
                    }
                }

            });
        }

        @Override
        public void onKeyEvent(final KeyEvent event) throws RemoteException {
            mUIHandler.post(new Runnable() {

                @Override
                public void run() {
                    if (mPresentation != null) {
                        mPresentation.injectKeyEvent(event);
                    }
                }

            });
        }
    }

    private final IBinder mBinder = new ProjectionServiceBinder();
    private VirtualDisplay mDisplay;
    private ProjectedPresentation mPresentation;

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        mUIHandler = new Handler(Looper.getMainLooper());
        return mBinder;
    }
}
