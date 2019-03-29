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
 * limitations under the License
 */

package android.server.cts;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public abstract class AbstractLifecycleLogActivity extends Activity {

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i(getTag(), "onCreate");
    }

    @Override
    protected void onStart() {
        super.onResume();
        Log.i(getTag(), "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(getTag(), "onResume");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(getTag(), "onConfigurationChanged");
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode, Configuration newConfig) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig);
        Log.i(getTag(), "onMultiWindowModeChanged");
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        Log.i(getTag(), "onPictureInPictureModeChanged");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(getTag(), "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(getTag(), "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(getTag(), "onDestroy");
    }

    protected abstract String getTag();

    protected void dumpConfiguration(Configuration config) {
        Log.i(getTag(), "Configuration: " + config);
    }

    protected void dumpDisplaySize(Configuration config) {
        // Dump the display size as seen by this Activity.
        final WindowManager wm = getSystemService(WindowManager.class);
        final Display display = wm.getDefaultDisplay();
        final Point point = new Point();
        display.getSize(point);
        final DisplayMetrics metrics = getResources().getDisplayMetrics();

        final String line = "config"
                + " size=" + buildCoordString(config.screenWidthDp, config.screenHeightDp)
                + " displaySize=" + buildCoordString(point.x, point.y)
                + " metricsSize=" + buildCoordString(metrics.widthPixels, metrics.heightPixels)
                + " smallestScreenWidth=" + config.smallestScreenWidthDp
                + " densityDpi=" + config.densityDpi
                + " orientation=" + config.orientation;

        Log.i(getTag(), line);
    }

    protected static String buildCoordString(int x, int y) {
        return "(" + x + "," + y + ")";
    }
}
