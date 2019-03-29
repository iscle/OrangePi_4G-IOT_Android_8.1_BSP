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

package android.backup.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * The activity could be invoked to create a test file.
 *
 * Here is an example of a call:
 *
 * am start -a android.intent.action.MAIN \
 * -c android.intent.category.LAUNCHER \
 * -n android.backup.app/android.backup.app.MainActivity \
 * -e file_size 10000000
 *
 * "File created!" string is printed in logcat when file is created.
 */
public class MainActivity extends Activity {
    public static final String TAG = "BackupCTSApp";
    public static final String FILE_NAME = "file_name";

    private static final String FILE_SIZE_EXTRA = "file_size";
    private static final int DATA_CHUNK_SIZE = 1024 * 1024;

    @Override
    protected void onCreate(Bundle savedInstanceActivity) {
        super.onCreate(savedInstanceActivity);
        if (getIntent().hasExtra(FILE_SIZE_EXTRA)) {
            int fileSize = Integer.parseInt(getIntent().getStringExtra(FILE_SIZE_EXTRA));
            Log.i(TAG, "Creating file of size: " + fileSize);
            try {
                createFile(fileSize);
                Log.d(TAG, "File created!");
            } catch (IOException e) {
                Log.e(TAG, "IOException: " + e);
            }
        } else {
            Log.d(TAG, "No file size was provided");
        }
        finish();
    }

    private void createFile(int size) throws IOException {
        byte[] bytes = new byte[DATA_CHUNK_SIZE];
        new Random().nextBytes(bytes);
        File f = new File(getFilesDir().getAbsolutePath(), FILE_NAME);
        f.getParentFile().mkdirs();
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(f))) {
            while (size > 0) {
                int bytesToWrite = Math.min(size, DATA_CHUNK_SIZE);
                bos.write(bytes, 0, bytesToWrite);
                size -= bytesToWrite;
            }
        }
    }
}
