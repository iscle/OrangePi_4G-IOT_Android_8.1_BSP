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

package android.theme.app;

import android.Manifest.permission;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager.LayoutParams;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Generates images by iterating through all themes and launching instances of
 * {@link ThemeDeviceActivity}.
 */
public class GenerateImagesActivity extends Activity {
    private static final String TAG = "GenerateImagesActivity";

    private static final String OUT_DIR = "cts-theme-assets";
    private static final int REQUEST_CODE = 1;

    public static final String EXTRA_REASON = "reason";

    private final CountDownLatch mLatch = new CountDownLatch(1);

    private File mOutputDir;
    private File mOutputZip;

    private int mCurrentTheme;
    private String mFinishReason;
    private boolean mFinishSuccess;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_TURN_SCREEN_ON
                | LayoutParams.FLAG_DISMISS_KEYGUARD);

        mOutputDir = new File(Environment.getExternalStorageDirectory(), OUT_DIR);
        ThemeTestUtils.deleteDirectory(mOutputDir);
        mOutputDir.mkdirs();

        if (!mOutputDir.exists()) {
            finish("Failed to create output directory " + mOutputDir.getAbsolutePath(), false);
            return;
        }

        final boolean canDisableKeyguard = checkCallingOrSelfPermission(
                permission.DISABLE_KEYGUARD) == PackageManager.PERMISSION_GRANTED;
        if (!canDisableKeyguard) {
            finish("Not granted permission to disable keyguard", false);
            return;
        }

        new KeyguardCheck(this) {
            @Override
            public void onSuccess() {
                generateNextImage();
            }

            @Override
            public void onFailure() {
                finish("Device is locked", false);
            }
        }.start();
    }

    public boolean isFinishSuccess() {
        return mFinishSuccess;
    }

    public String getFinishReason() {
        return mFinishReason;
    }

    static abstract class KeyguardCheck implements Runnable {
        private static final int MAX_RETRIES = 3;
        private static final int RETRY_DELAY = 500;

        private final Handler mHandler;
        private final KeyguardManager mKeyguard;

        private int mRetries;

        public KeyguardCheck(Context context) {
            mHandler = new Handler(context.getMainLooper());
            mKeyguard = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
        }

        public void start() {
            mRetries = 0;

            mHandler.removeCallbacks(this);
            mHandler.post(this);
        }

        public void cancel() {
            mHandler.removeCallbacks(this);
        }

        @Override
        public void run() {
            if (!mKeyguard.isKeyguardLocked()) {
                onSuccess();
            } else if (mRetries < MAX_RETRIES) {
                mRetries++;
                mHandler.postDelayed(this, RETRY_DELAY);
            } else {
                onFailure();
            }

        }

        public abstract void onSuccess();
        public abstract void onFailure();
    }

    /**
     * Starts the activity to generate the next image.
     */
    private boolean generateNextImage() {
        final ThemeDeviceActivity.Theme theme = ThemeDeviceActivity.THEMES[mCurrentTheme];
        if (theme.apiLevel > VERSION.SDK_INT) {
            Log.v(TAG, "Skipping theme \"" + theme.name
                    + "\" (requires API " + theme.apiLevel + ")");
            return false;
        }

        Log.v(TAG, "Generating images for theme \"" + theme.name + "\"...");

        final Intent intent = new Intent(this, ThemeDeviceActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(ThemeDeviceActivity.EXTRA_THEME, mCurrentTheme);
        intent.putExtra(ThemeDeviceActivity.EXTRA_OUTPUT_DIR, mOutputDir.getAbsolutePath());
        startActivityForResult(intent, REQUEST_CODE);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            finish("Failed to generate images for theme " + mCurrentTheme + " ("
                    + data.getStringExtra(EXTRA_REASON) + ")", false);
            return;
        }

        // Keep trying themes until one works.
        boolean success = false;
        while (++mCurrentTheme < ThemeDeviceActivity.THEMES.length && !success) {
            success = generateNextImage();
        }

        // If we ran out of themes, we're done.
        if (!success) {
            compressOutput();

            finish("Image generation complete!", true);
        }
    }

    private void compressOutput() {
        mOutputZip = new File(mOutputDir.getParentFile(), mOutputDir.getName() + ".zip");

        if (mOutputZip.exists()) {
            // Remove any old test results.
            mOutputZip.delete();
        }

        try {
            ThemeTestUtils.compressDirectory(mOutputDir, mOutputZip);
            ThemeTestUtils.deleteDirectory(mOutputDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void finish(String reason, boolean success) {
        mFinishSuccess = success;
        mFinishReason = reason;

        finish();
    }

    @Override
    public void finish() {
        mLatch.countDown();

        super.finish();
    }

    public File getOutputZip() {
        return mOutputZip;
    }

    public boolean waitForCompletion(long timeoutMillis) throws InterruptedException {
        return mLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}
