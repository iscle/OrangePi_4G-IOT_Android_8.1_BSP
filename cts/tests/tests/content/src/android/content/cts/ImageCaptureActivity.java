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
package android.content.cts;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

public class ImageCaptureActivity extends Activity {
    public static final String ACTION_FILE_READY = "android.content.cts.action.file_ready";
    private static final String TAG = ImageCaptureUriExtraToClipDataTest.TAG;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        // Check action.
        String action = intent.getAction();
        if ((MediaStore.ACTION_IMAGE_CAPTURE.equals(action)
                        || MediaStore.ACTION_IMAGE_CAPTURE_SECURE.equals(action)
                        || MediaStore.ACTION_VIDEO_CAPTURE.equals(action))) {
            writeToClipDataUri(intent);
        }

        finish();
    }

    // Sends ACTION_FILE_READY intent when write to clipdata uri is succesful.
    private void writeToClipDataUri(Intent intent) {
        if ((intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {

            // Note: since this activity is in the same package as the test we can write to the file
            // regardless of this permission, but in general this permission is required.
            Log.e(TAG, "Intent.FLAG_GRANT_WRITE_URI_PERMISSION was not granted.");
            return;
        }

        File file = getFileFromIntent(intent);
        if (file == null) {
            Log.e(TAG, "Could not get file from clipdata.");
            return;
        }
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(ImageCaptureUriExtraToClipDataTest.TEST_INPUT);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "File IO failure while writing.");
            return;
        }
        Intent fileReady = new Intent(ACTION_FILE_READY);
        sendBroadcast(fileReady);
    }

    private File getFileFromIntent(Intent intent) {
        ClipData clipData = intent.getClipData();
        if (clipData == null) {
            Log.e(TAG, "ClipData missing.");
            return null;
        }
        if (clipData.getItemCount() == 0) {
            Log.e(TAG, "Uri missing in ClipData.");
            return null;
        }

        Uri filePath = clipData.getItemAt(0).getUri();
        if (filePath == null) {
            Log.e(TAG, "Uri missing in ClipData.");
            return null;
        }

        try {
            return new File(filePath.getPath());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Cannot get file at Uri.");
            return null;
        }
    }
}