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
import android.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class ReadableFileReceiverActivity extends Activity {
    public static final String ACTION_CONFIRM_READ_SUCCESS
        = "android.content.cts.action.CONFIRM_READ_SUCCESS";
    private static final String TAG = ReadableUriExtraToClipDataTest.TAG;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();

        // Check action.
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)
                || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            readFilesFromClipDataUri(intent);
        }

        finish();
    }

    // Sends ACTION_FILE_READY intent when read from clipdata uri is succesful
    // and read data matches the data written by the test.
    private void readFilesFromClipDataUri(Intent intent) {
        if ((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {

            // Note: since this activity is in the same package as the test we can read from the
            // file regardless of this permission, but in general this permission is required.
            Log.e(TAG, "Intent.FLAG_GRANT_READ_URI_PERMISSION was not granted.");
            return;
        }

        List<File> files = getFilesFromIntent(intent);
        if (files == null) {
            Log.e(TAG, "Could not get files from clipdata.");
            return;
        }
        for (File file : files) {
            if (!testFileContents(file)) {
                Log.e(TAG, "File contents of " + file.getPath()
                        + " is incorrect or could not be verified.");
                return;
            }
        }
        Intent confirmIntent = new Intent(ACTION_CONFIRM_READ_SUCCESS);
        sendBroadcast(confirmIntent);
    }

    private ArrayList<File> getFilesFromIntent(Intent intent) {
        ClipData clipData = intent.getClipData();
        if (clipData == null) {
            Log.e(TAG, "ClipData missing.");
            return null;
        }
        if (clipData.getItemCount() == 0) {
            Log.e(TAG, "Uri missing in ClipData.");
            return null;
        }

        ArrayList<File> result = new ArrayList<File>();
        for (int i = 0; i < clipData.getItemCount(); i++) {
            Uri filePath = clipData.getItemAt(i).getUri();
            if (filePath == null) {
                Log.e(TAG, "Uri missing in ClipData.");
                return null;
            }
            try {
                result.add(new File(filePath.getPath()));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Cannot get file at Uri.");
                return null;
            }
        }
        return result;
    }

    private boolean testFileContents(File file) {
        char[] buffer = new char[ReadableUriExtraToClipDataTest.TEST_INPUT.length()];
        try {
            FileReader reader = new FileReader(file);
            reader.read(buffer);
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Error while reading file " + file.getPath() + ".");
            return false;
        }
        String fileContents = new String(buffer);
        return ReadableUriExtraToClipDataTest.TEST_INPUT.equals(fileContents);
    }
}