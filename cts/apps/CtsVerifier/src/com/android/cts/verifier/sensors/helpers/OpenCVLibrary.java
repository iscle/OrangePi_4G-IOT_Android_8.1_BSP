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
package com.android.cts.verifier.sensors.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * OpenCV library loader class
 */
public class OpenCVLibrary {

    private final static String TAG = "OpenCVLibraryProbe";
    private final static int ASYNC_LOAD_TIMEOUT_SEC = 30;
    private static boolean sLoaded = false;

    /**
     * Load OpenCV Library in async mode
     *
     * @param context Activity context.
     * @param allowStatic Allow trying load from local package.
     * @param allowInstall Allow installing package from play store.
     *
     * @return if load succeed return true. Return false otherwise.
     */
    public static boolean load(Context context,
            boolean allowLocal, boolean allowPackage, boolean allowInstall) {
        // only need to load once
        if (!sLoaded) {
            // Try static load first
            if (allowLocal && OpenCVLoader.initDebug()) {
                sLoaded = true;
            } else if (allowPackage) {
                if (allowInstall || probePackage(context)) {
                    final CountDownLatch done = new CountDownLatch(1);
                    // Load the library through async loader
                    OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, context,
                            new BaseLoaderCallback(context) {
                                @Override
                                public void onManagerConnected(int status) {
                                    Log.v(TAG, "New Loading status: " + status);
                                    switch (status) {
                                        case LoaderCallbackInterface.SUCCESS: {
                                            sLoaded = true;
                                        }
                                        break;
                                        default: {
                                            Log.e(TAG, "Connecting OpenCV Manager failed");
                                        }
                                        break;
                                    }
                                    done.countDown();
                                }
                            });
                    try {
                        if (!done.await(ASYNC_LOAD_TIMEOUT_SEC, TimeUnit.SECONDS)) {
                            Log.e(TAG, "Time out when attempt async load");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return sLoaded;
    }

    /**
     * Test if the library is loaded
     * @return a boolean indicates whether the OpenCV library is loaded.
     */
    public static boolean isLoaded() {
        return sLoaded;
    }

    /**
     * Probe if the OpenCV Manager package is installed
     *
     * @return a boolean indicate wheather OpenCV Manager is installed
     */
    private static boolean probePackage(Context context) {
        Intent intent = new Intent("org.opencv.engine.BIND");
        intent.setPackage("org.opencv.engine");

        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // Do nothing
            }
            @Override
            public void onServiceDisconnected(ComponentName className) {
                // Do nothing
            }
        };

        boolean ret = false;
        try {
            if (context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                ret = true;
            }
        } finally {
            context.unbindService(conn);
        }

        return ret;
    }
}
