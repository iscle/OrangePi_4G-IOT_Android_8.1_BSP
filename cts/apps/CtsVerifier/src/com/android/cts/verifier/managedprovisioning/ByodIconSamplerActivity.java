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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.UserHandle;
import android.os.Process;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * Activity used to generate sample image for {@link ByodFlowTestActivity} on a reference build.
 *
 * <p>Instructions: After Profile owner installed test has passed, run:
 *  adb shell pm list users
 *  adb shell am start -a com.android.cts.verifier.managedprovisioning.BYOD_SAMPLE_ICON \
 *      --user <MANAGED_USER_ID>
 * The icon can then be copied from /mnt/shell/emulated/<MANAGED_USER_ID>/badged_icon.png.
 */
public class ByodIconSamplerActivity  extends Activity {
    static final String TAG = "ByodIconSamplerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sampleImage();
        // This activity has no UI
        finish();
    }
    /**
     * Writes a badged option of the CTS tests app icon on the sdcard.
     * For test development only: this should be used to regenerate the asset every time we have
     * a new badge.
     */
    private void sampleImage() {
        UserHandle userHandle = Process.myUserHandle();
        Log.d(TAG, "Sampling image for: " + userHandle);
        Drawable drawable = getPackageManager().getUserBadgedIcon(getAppIcon(), userHandle);
        Bitmap bitmap = convertToBitmap(drawable);
        String fileName = Environment.getExternalStorageDirectory().getPath() + "/badged_icon.png";
        FileOutputStream file = null;
        try {
            file = new FileOutputStream(fileName);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, file);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "sampleImage: FileNotFoundException ", e);
        } finally {
            try {
                if (file != null) {
                    file.close();
                    Log.d(TAG, "Wrote badged icon to file: " + fileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Drawable getAppIcon() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(),
                    0 /* flags */);
            if (packageInfo.applicationInfo != null) {
                return getResources().getDrawable(packageInfo.applicationInfo.icon);
            }
        } catch (NameNotFoundException e) {
            // Should not happen
            Log.d(TAG, "getAppIcon: NameNotFoundException", e);
        }
        return null;
    }

    private static Bitmap convertToBitmap(Drawable icon) {
        if (icon == null) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        icon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        icon.draw(canvas);
        return bitmap;
    }
}
