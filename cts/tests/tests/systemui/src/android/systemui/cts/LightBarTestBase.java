/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.systemui.cts;

import static android.support.test.InstrumentationRegistry.getInstrumentation;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;

public class LightBarTestBase {

    private static final String TAG = "LightBarTestBase";

    public static final String DUMP_PATH = "/sdcard/lightstatustest.png";

    protected Bitmap takeStatusBarScreenshot(LightBarBaseActivity activity) {
        Bitmap fullBitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        return Bitmap.createBitmap(fullBitmap, 0, 0, activity.getWidth(), activity.getTop());
    }

    protected Bitmap takeNavigationBarScreenshot(LightBarBaseActivity activity) {
        Bitmap fullBitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        return Bitmap.createBitmap(fullBitmap, 0, activity.getBottom(), activity.getWidth(),
                fullBitmap.getHeight() - activity.getBottom());
    }

    protected void dumpBitmap(Bitmap bitmap) {
        Log.e(TAG, "Dumping failed bitmap to " + DUMP_PATH);
        FileOutputStream fileStream = null;
        try {
            fileStream = new FileOutputStream(DUMP_PATH);
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
            fileStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "Dumping bitmap failed.", e);
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
