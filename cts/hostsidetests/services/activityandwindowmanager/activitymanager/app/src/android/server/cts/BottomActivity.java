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

package android.server.cts;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

public class BottomActivity extends AbstractLifecycleLogActivity {

    private static final String TAG = BottomActivity.class.getSimpleName();

    private int mStopDelay;
    private View mFloatingWindow;

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final boolean useWallpaper = getIntent().getBooleanExtra("USE_WALLPAPER", false);
        if (useWallpaper) {
            setTheme(R.style.WallpaperTheme);
        }
        setContentView(R.layout.main);

        // Delayed stop is for simulating a case where resume happens before
        // activityStopped() is received by AM, and the transition starts without
        // going through fully stopped state (see b/30255354).
        // If enabled, we stall onStop() of BottomActivity, open TopActivity but make
        // it finish before onStop() ends. This will cause BottomActivity to resume before
        // it notifies AM of activityStopped(). We also add a second window of
        // TYPE_BASE_APPLICATION, so that the transition animation could start earlier.
        // Otherwise the main window has to relayout to visible first and the error won't occur.
        // Note that if the test fails, we shouldn't try to change the app here to make
        // it pass. The test app is artificially made to simulate an failure case, but
        // it's not doing anything wrong.
        mStopDelay = getIntent().getIntExtra("STOP_DELAY", 0);
        if (mStopDelay > 0) {
            LayoutInflater inflater = getLayoutInflater();
            mFloatingWindow = inflater.inflate(R.layout.floating, null);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
            params.setTitle("Floating");
            getWindowManager().addView(mFloatingWindow, params);
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume() E");
        super.onResume();

        if (mStopDelay > 0) {
            // Refresh floating window
            Log.d(TAG, "Scheuling invalidate Floating Window in onResume()");
            mFloatingWindow.invalidate();
        }

        Log.d(TAG, "onResume() X");
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mStopDelay > 0) {
            try {
                Log.d(TAG, "Stalling onStop() by " + mStopDelay + " ms...");
                Thread.sleep(mStopDelay);
            } catch(InterruptedException e) {}

            // Refresh floating window
            Log.d(TAG, "Scheuling invalidate Floating Window in onStop()");
            mFloatingWindow.invalidate();
        }
    }
}
