/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.system.helpers;

import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.view.WindowManager;
import android.util.DisplayMetrics;

/**
 * Implement common helper methods for device.
 */
public class DeviceHelper {

    public static final String PIXEL_XL = "Pixel XL";
    public static final String PIXEL = "Pixel";
    public static final String RYU = "Pixel C";
    // 600dp is the threshold value for 7-inch tablets.
    private static final int TABLET_DP_THRESHOLD = 600;
    public static final int LONG_TIMEOUT = 2000;
    private static DeviceHelper sInstance = null;
    private Context mContext = null;
    private UiDevice mDevice = null;

    public DeviceHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public static DeviceHelper getInstance() {
        if (sInstance == null) {
            sInstance = new DeviceHelper();
        }
        return sInstance;
    }

    /** Returns true if the device is a tablet */
    public boolean isTablet() {
        // Get screen density & screen size from window manager
        WindowManager wm = (WindowManager) mContext.getSystemService(
                Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        // Determines the smallest screen width DP which is
        // calculated as ( pixels * density - independent pixel unit ) / density.
        // http://developer.android.com/guide/practices/screens_support.html.
        int screenDensity = metrics.densityDpi;
        int screenWidth = Math.min(
                metrics.widthPixels, metrics.heightPixels);
        int screenHeight = Math.max(
                metrics.widthPixels, metrics.heightPixels);
        int smallestScreenWidthDp = (Math.min(screenWidth, screenHeight)
                * DisplayMetrics.DENSITY_DEFAULT) / screenDensity;
        return smallestScreenWidthDp >= TABLET_DP_THRESHOLD;
    }

    public boolean isNexusExperienceDevice() {
        // Get device model
        String result = Build.MODEL;
        if (result.trim().equalsIgnoreCase(PIXEL) || result.trim().equalsIgnoreCase(PIXEL_XL)) {
            return true;
        }
        return false;
    }

    public boolean isRyuDevice() {
        return Build.MODEL.trim().equalsIgnoreCase(RYU);
    }

    /**
     * Device sleep and wake up
     * @throws RemoteException, InterruptedException
     */
    public void sleepAndWakeUpDevice() throws RemoteException, InterruptedException {
        mDevice.sleep();
        Thread.sleep(LONG_TIMEOUT);
        mDevice.wakeUp();
    }
}
