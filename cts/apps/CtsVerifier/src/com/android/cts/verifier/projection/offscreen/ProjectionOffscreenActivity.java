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

package com.android.cts.verifier.projection.offscreen;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.projection.IProjectionService;
import com.android.cts.verifier.projection.ProjectionPresentationType;
import com.android.cts.verifier.projection.ProjectionService;

import java.nio.ByteBuffer;

public class ProjectionOffscreenActivity extends PassFailButtons.Activity
        implements ImageReader.OnImageAvailableListener {
    private static String TAG = ProjectionOffscreenActivity.class.getSimpleName();
    private static final int WIDTH = 800;
    private static final int HEIGHT = 480;
    private static final int DENSITY = DisplayMetrics.DENSITY_MEDIUM;
    private static final int TIME_SCREEN_OFF = 5000; // Time screen must remain off for test to run
    private static final int DELAYED_RUNNABLE_TIME = 1000; // Time after screen turned off
                                                           // keyevent is sent
    private static final int RENDERER_DELAY_THRESHOLD = 2000; // Time after keyevent sent that
                                                              // rendering must happen by

    protected ImageReader mReader;
    protected IProjectionService mService;
    protected TextView mStatusView;
    protected int mPreviousColor = Color.BLACK;
    private long mTimeScreenTurnedOff = 0;
    private long mTimeKeyEventSent = 0;
    private enum TestStatus { PASSED, FAILED, RUNNING };
    protected TestStatus mTestStatus = TestStatus.RUNNING;

    private final Runnable sendKeyEventRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                mService.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN));
                mTimeKeyEventSent = SystemClock.elapsedRealtime();
            } catch (RemoteException e) {
                Log.e(TAG, "Error running onKeyEvent", e);
            }
        }
    };

    private final Runnable playNotificationRunnable = new Runnable() {

        @Override
        public void run() {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
            r.play();
            ((Vibrator) getSystemService(VIBRATOR_SERVICE)).vibrate(1000);
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(
                    sendKeyEventRunnable, DELAYED_RUNNABLE_TIME);
            mStatusView.setText("Running test...");
            mTimeScreenTurnedOff = SystemClock.elapsedRealtime();
            // Notify user its safe to turn screen back on after 5s + fudge factor
            handler.postDelayed(playNotificationRunnable, TIME_SCREEN_OFF + 500);
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            if (SystemClock.elapsedRealtime() - mTimeScreenTurnedOff < TIME_SCREEN_OFF) {
                mStatusView.setText("ERROR: Turned on screen too early");
                getPassButton().setEnabled(false);
                mTestStatus = TestStatus.FAILED;
            }
        }
    }

};

    protected final ServiceConnection mConnection = new ServiceConnection() {

            @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = IProjectionService.Stub.asInterface(binder);
            new Handler().post(new Runnable() {

                    @Override
                public void run() {
                    Log.i(TAG, "onServiceConnected thread " + Thread.currentThread());
                    try {
                        mService.startRendering(mReader.getSurface(), WIDTH, HEIGHT, DENSITY,
                                ProjectionPresentationType.OFFSCREEN.ordinal());
                    } catch (RemoteException e) {
                        Log.e(TAG, "Failed to execute startRendering", e);
                    }

                    IntentFilter filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_SCREEN_OFF);
                    filter.addAction(Intent.ACTION_SCREEN_ON);

                    registerReceiver(mReceiver, filter);
                    mStatusView.setText("Please turn off your screen and turn it back on after " +
                            "5 seconds. A sound will be played when it is safe to turn the " +
                            "screen back on");
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

        View view = getLayoutInflater().inflate(R.layout.poa_main, null);
        mStatusView = (TextView) view.findViewById(R.id.poa_status_text);
        mStatusView.setText("Waiting for service to bind...");

        setContentView(view);

        setInfoResources(R.string.poa_test, R.string.poa_info, -1);
        setPassFailButtonClickListeners();
        mReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);
        mReader.setOnImageAvailableListener(this, null);
        bindService(new Intent(this, ProjectionService.class), mConnection,
                Context.BIND_AUTO_CREATE);

        getPassButton().setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        mReader.close();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mTestStatus == TestStatus.FAILED) {
            setTestResultAndFinish(false);
        }
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        Log.i(TAG, "onImageAvailable: " + reader);

        if (mTimeKeyEventSent != 0
                && mTestStatus == TestStatus.RUNNING
                && mTimeKeyEventSent + RENDERER_DELAY_THRESHOLD < SystemClock.elapsedRealtime()) {
            mTestStatus = TestStatus.FAILED;
            mStatusView.setText("Failed: took too long to render");
        }

        Image image = reader.acquireLatestImage();

        // No new images available
        if (image == null) {
            Log.w(TAG, "onImageAvailable called but no image!");
            return;
        }

        if (mTestStatus == TestStatus.RUNNING) {
            int ret = scanImage(image);
            if (ret == -1) {
                mStatusView.setText("Failed: saw unexpected color");
                getPassButton().setEnabled(false);
                mTestStatus = TestStatus.FAILED;
            } else if (ret != mPreviousColor && ret == Color.BLUE) {
                mStatusView.setText("Success: virtual display rendered expected color");
                getPassButton().setEnabled(true);
                mTestStatus = TestStatus.PASSED;
            }
        }
        image.close();
    }

    // modified from the VirtualDisplay Cts test
    /**
     * Gets the color of the image and ensures all the pixels are the same color
     * @param image input image
     * @return The color of the image, or -1 for failure
     */
    private int scanImage(Image image) {
        final Image.Plane plane = image.getPlanes()[0];
        final ByteBuffer buffer = plane.getBuffer();
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int pixelStride = plane.getPixelStride();
        final int rowStride = plane.getRowStride();
        final int rowPadding = rowStride - pixelStride * width;

        Log.d(TAG, "- Scanning image: width=" + width + ", height=" + height
                + ", pixelStride=" + pixelStride + ", rowStride=" + rowStride);

        int offset = 0;
        int blackPixels = 0;
        int bluePixels = 0;
        int otherPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = 0;
                pixel |= (buffer.get(offset) & 0xff) << 16;     // R
                pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
                pixel |= (buffer.get(offset + 2) & 0xff);       // B
                pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
                if (pixel == Color.BLACK || pixel == 0) {
                    blackPixels += 1;
                } else if (pixel == Color.BLUE) {
                    bluePixels += 1;
                } else {
                    otherPixels += 1;
                    if (otherPixels < 10) {
                        Log.d(TAG, "- Found unexpected color: " + Integer.toHexString(pixel));
                    }
                }
                offset += pixelStride;
            }
            offset += rowPadding;
        }

        // Return a color if it represents all of the pixels.
        Log.d(TAG, "- Pixels: " + blackPixels + " black, "
                + bluePixels + " blue, "
                + otherPixels + " other");
        if (blackPixels == width * height) {
            return Color.BLACK;
        } else if (bluePixels == width * height) {
            return Color.BLUE;
        } else {
            return -1;
        }
    }
}
