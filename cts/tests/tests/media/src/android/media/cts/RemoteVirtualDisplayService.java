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

package android.media.cts;

import android.app.Presentation;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;

public class RemoteVirtualDisplayService extends Service {
    private static final String TAG = "RemoteVirtualDisplayService";
    private static final boolean DBG = false;
    /** argument: Surface, int w, int h, return none */
    private static final int BINDER_CMD_START = IBinder.FIRST_CALL_TRANSACTION;
    /** argument: int color, return none */
    private static final int BINDER_CMD_RENDER = IBinder.FIRST_CALL_TRANSACTION + 1;
    private final Handler mHandlerForRunOnMain = new Handler(Looper.getMainLooper());;
    private IBinder mBinder;
    private VirtualDisplayPresentation mPresentation;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        mBinder = new Binder() {
            @Override
            protected boolean onTransact(int code, Parcel data, Parcel reply,
                    int flags) throws RemoteException {
                switch(code) {
                    case BINDER_CMD_START: {
                        Surface surface = Surface.CREATOR.createFromParcel(data);
                        int w = data.readInt();
                        int h = data.readInt();
                        start(surface, w, h);
                        break;
                    }
                    case BINDER_CMD_RENDER: {
                        int color = data.readInt();
                        render(color);
                        break;
                    }
                    default:
                        Log.e(TAG, "unrecognized binder command " + code);
                        return false;
                }
                return true;
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return mBinder;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        if (mPresentation != null) {
            mPresentation.dismissPresentation();
            mPresentation.destroyVirtualDisplay();
            mPresentation = null;
        }
    }

    private void start(Surface surface, int w, int h) {
        Log.i(TAG, "start");
        mPresentation = new VirtualDisplayPresentation(this, surface, w, h);
        mPresentation.createVirtualDisplay();
        mPresentation.createPresentation();
    }

    private void render(int color) {
        if (DBG) {
            Log.i(TAG, "render " + Integer.toHexString(color));
        }
        mPresentation.doRendering(color);
    }

    private class VirtualDisplayPresentation {
        private Context mContext;
        private Surface mSurface;
        private int mWidth;
        private int mHeight;
        private final DisplayManager mDisplayManager;
        private VirtualDisplay mVirtualDisplay;
        private TestPresentation mPresentation;

        VirtualDisplayPresentation(Context context, Surface surface, int w, int h) {
            mContext = context;
            mSurface = surface;
            mWidth = w;
            mHeight = h;
            mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        }

        void createVirtualDisplay() {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mVirtualDisplay = mDisplayManager.createVirtualDisplay(
                            TAG, mWidth, mHeight, 200, mSurface, 0);
                }
            });
        }

        void destroyVirtualDisplay() {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
        }

        void createPresentation() {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mPresentation = new TestPresentation(RemoteVirtualDisplayService.this,
                            mVirtualDisplay.getDisplay());
                    mPresentation.show();
                }
            });
        }

        void dismissPresentation() {
            if (mPresentation != null) {
                mPresentation.dismiss();
            }
        }

        public void doRendering(final int color) {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mPresentation.doRendering(color);
                }
            });
        }

        private class TestPresentation extends Presentation {
            private ImageView mImageView;

            public TestPresentation(Context outerContext, Display display) {
                // This theme is required to prevent an extra view from obscuring the presentation
                super(outerContext, display,
                        android.R.style.Theme_Holo_Light_NoActionBar_TranslucentDecor);
                getWindow().setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
            }

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                mImageView = new ImageView(getContext());
                mImageView.setImageDrawable(new ColorDrawable(0));
                mImageView.setLayoutParams(new LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
                setContentView(mImageView);
            }

            public void doRendering(int color) {
                mImageView.setImageDrawable(new ColorDrawable(color));
            }
        }
    }

    private void runOnMainSync(Runnable runner) {
        SyncRunnable sr = new SyncRunnable(runner);
        mHandlerForRunOnMain.post(sr);
        sr.waitForComplete();
    }

    private static final class SyncRunnable implements Runnable {
        private final Runnable mTarget;
        private boolean mComplete;

        public SyncRunnable(Runnable target) {
            mTarget = target;
        }

        public void run() {
            mTarget.run();
            synchronized (this) {
                mComplete = true;
                notifyAll();
            }
        }

        public void waitForComplete() {
            synchronized (this) {
                while (!mComplete) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
            }
        }
    }
}
