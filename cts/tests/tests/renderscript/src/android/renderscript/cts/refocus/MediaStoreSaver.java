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

package android.renderscript.cts.refocus;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class to save images into android image database
 */
public class MediaStoreSaver {
    public static final String savePNG(Bitmap bitmap,
                                       String folderName,
                                       String imageName,
                                       Context mContext) {
        File picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!picDir.exists() && picDir.mkdirs()) {
            // The Pictures directory does not exist on an x86 emulator
            picDir = mContext.getFilesDir();
        }

        File dir = new File(picDir, folderName);
        if (!dir.exists() && !dir.mkdirs()) {
            return "";
        }

        try {
            File file = File.createTempFile(imageName, ".png", dir);
            FileOutputStream fOut = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, fOut);
            android.util.Log.v("RefocusTest", "saved image: " + file.getAbsolutePath());
            fOut.flush();
            fOut.close();
            return file.getAbsolutePath();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }
}
