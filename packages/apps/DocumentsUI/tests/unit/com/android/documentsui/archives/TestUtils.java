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

package com.android.documentsui.archives;

import com.android.documentsui.archives.Archive;
import com.android.documentsui.tests.R;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

import android.util.Log;

public class TestUtils {
    public static final Uri ARCHIVE_URI = Uri.parse("content://i/love/strawberries");
    public static final String NOTIFICATION_URI = "content://notification-uri";

    public final Context mTargetContext;
    public final Context mTestContext;
    public final ExecutorService mExecutor;

    public TestUtils(Context targetContext, Context testContext, ExecutorService executor) {
        mTargetContext = targetContext;
        mTestContext = testContext;
        mExecutor = executor;
    }

    /**
     * Creates an empty temporary file.
     */
    public File createTemporaryFile() throws IOException {
        return File.createTempFile("com.android.documentsui.archives.tests{",
                "}.zip", mTargetContext.getCacheDir());
    }

    /**
     * Opens a resource and returns the contents via file descriptor to a local
     * snapshot file.
     */
    public ParcelFileDescriptor getSeekableDescriptor(int resource) {
        // Extract the file from resources.
        File file = null;
        try {
            file = File.createTempFile("com.android.documentsui.archives.tests{",
                    "}.zip", mTargetContext.getCacheDir());
            try (
                final FileOutputStream outputStream =
                        new ParcelFileDescriptor.AutoCloseOutputStream(
                                ParcelFileDescriptor.open(
                                        file, ParcelFileDescriptor.MODE_WRITE_ONLY));
                final InputStream inputStream =
                        mTestContext.getResources().openRawResource(resource);
            ) {
                final byte[] buffer = new byte[32 * 1024];
                int bytes;
                while ((bytes = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytes);
                }
                outputStream.flush();
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            throw new IllegalStateException("Creating a snapshot failed. ", e);
        } finally {
            // On UNIX the file will be still available for processes which opened it, even
            // after deleting it. Remove it ASAP, as it won't be used by anyone else.
            if (file != null) {
                file.delete();
            }
        }
    }

    /**
     * Opens a resource and returns the contents via a pipe.
     */
    public ParcelFileDescriptor getNonSeekableDescriptor(int resource) {
        ParcelFileDescriptor[] pipe = null;
        try {
            pipe = ParcelFileDescriptor.createPipe();
            final ParcelFileDescriptor finalOutputPipe = pipe[1];
            mExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            try (
                                final ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                                        new ParcelFileDescriptor.
                                                AutoCloseOutputStream(finalOutputPipe);
                                final InputStream inputStream =
                                        mTestContext.getResources().openRawResource(resource);
                            ) {
                                final byte[] buffer = new byte[32 * 1024];
                                int bytes;
                                while ((bytes = inputStream.read(buffer)) != -1) {
                                    outputStream.write(buffer, 0, bytes);
                                }
                            } catch (IOException e) {
                              throw new IllegalStateException("Piping resource failed.", e);
                            }
                        }
                    });
            return pipe[0];
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create a pipe.", e);
        }
    }
}
