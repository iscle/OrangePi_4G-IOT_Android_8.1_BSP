/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.android.compatibility.common.util.DeviceInfoStore;

/**
 * Screen device info collector.
 */
public final class ScreenDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager =
                (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);

        store.addResult("width_pixels", metrics.widthPixels);
        store.addResult("height_pixels", metrics.heightPixels);
        store.addResult("x_dpi", metrics.xdpi);
        store.addResult("y_dpi", metrics.ydpi);
        store.addResult("density", metrics.density);
        store.addResult("density_dpi", metrics.densityDpi);

        Configuration configuration = getContext().getResources().getConfiguration();
        store.addResult("screen_size", getScreenSize(configuration));
        store.addResult("smallest_screen_width_dp", configuration.smallestScreenWidthDp);
    }

    private static String getScreenSize(Configuration configuration) {
        int screenLayout = configuration.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        String screenSize = String.format("0x%x", screenLayout);
        switch (screenLayout) {
            case Configuration.SCREENLAYOUT_SIZE_SMALL:
                screenSize = "small";
                break;

            case Configuration.SCREENLAYOUT_SIZE_NORMAL:
                screenSize = "normal";
                break;

            case Configuration.SCREENLAYOUT_SIZE_LARGE:
                screenSize = "large";
                break;

            case Configuration.SCREENLAYOUT_SIZE_XLARGE:
                screenSize = "xlarge";
                break;

            case Configuration.SCREENLAYOUT_SIZE_UNDEFINED:
                screenSize = "undefined";
                break;
        }
        return screenSize;
    }
}
