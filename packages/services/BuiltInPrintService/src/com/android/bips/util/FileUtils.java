/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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
package com.android.bips.util;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int BUFFER_SIZE = 8092;

    /** Recursively delete the target (file or directory) and everything beneath it */
    public static void deleteAll(File target) {
        if (DEBUG) Log.d(TAG, "Deleting " + target);
        if (target.isDirectory()) {
            for (File child : target.listFiles()) {
                deleteAll(child);
            }
        }
        target.delete();
    }

    /** Copy files from source to target, closing each stream when done */
    public static void copy(InputStream source, OutputStream target) throws IOException {
        try (InputStream in = source; OutputStream out = target) {
            final byte buffer[] = new byte[BUFFER_SIZE];
            int count;
            while ((count = in.read(buffer)) > 0) {
                if (count > 0) out.write(buffer, 0, count);
            }
        }
    }

    /** Return true if a directory exists or was made at the specified location */
    public static boolean makeDirectory(File dir) {
        if (DEBUG) {
            Log.d(TAG, "Testing file " + dir + " exists=" + dir.exists() +
                    " isDirectory=" + dir.isDirectory());
        }
        if (dir.exists()) return dir.isDirectory();
        return dir.mkdir();
    }
}