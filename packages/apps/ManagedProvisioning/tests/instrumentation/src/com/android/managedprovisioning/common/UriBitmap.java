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
 * limitations under the License.
 */
package com.android.managedprovisioning.common;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Test helper class to simplify testing cases where drawables are handled as Uri's.
 */
public class UriBitmap {
    private final Bitmap mBitmap;
    private final Uri mUri;
    private final File mTempFile;

    private UriBitmap(Bitmap bitmap, Uri uri, File tempFile) {
        mBitmap = bitmap;
        mUri = uri;
        mTempFile = tempFile;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public Uri getUri() {
        return mUri;
    }

    /**
     * @return an instance encapsulating a sample {@link Bitmap} and a {@link Uri} pointing to it
     */
    public static UriBitmap createSimpleInstance() throws IOException {
        Bitmap bitmap = generateSampleBitmap();
        File tempFile = File.createTempFile("tmpImage", ".png");
        Uri uri = bitmapToUri(bitmap, tempFile);
        return new UriBitmap(bitmap, uri, tempFile);
    }

    private static Bitmap generateSampleBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(15, 15, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                bitmap.setPixel(x, y, Color.rgb(x, y, x + y));
            }
        }
        return bitmap;
    }

    private static Uri bitmapToUri(Bitmap bitmap, File tempFile) throws IOException {
        try (FileOutputStream fs = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fs);
            return Uri.fromFile(tempFile);
        }
    }

    /**
     * Deletes the temp file where the image is stored (simple way to generate an Uri-enabled image)
     */
    @Override
    protected void finalize() throws Throwable {
        if (mTempFile != null) {
            //noinspection ResultOfMethodCallIgnored
            mTempFile.delete();
        }
        super.finalize();
    }
}