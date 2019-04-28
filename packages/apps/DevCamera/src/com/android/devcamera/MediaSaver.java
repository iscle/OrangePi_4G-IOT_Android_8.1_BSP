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
package com.android.devcamera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * This class has methods required to save a JPEG to disk as well as update the
 * MediaStore database.
 */


public class MediaSaver {
    private static final String TAG = "Snappy_MediaSaver";
    private static final String MY_PREFS_NAME = "SnappyPrefs";

    // MediaStore is slow/broken
    private static final boolean UDPATE_MEDIA_STORE = true;


    public static int getNextInt(Context context, String id) {
        SharedPreferences prefs = context.getSharedPreferences(MY_PREFS_NAME, Context.MODE_PRIVATE);
        int i = prefs.getInt(id, 1);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(id, i+1);
        editor.commit();
        return i;
    }

    /**
     * @param context Application context.
     * @param depthCloudData Depth cloud byte buffer.
     */
    public static String saveDepth(Context context, ByteBuffer depthCloudData) {
        String filename = "";
        try {
            File file;
            int i = getNextInt(context, "depthCounter");
            filename = String.format("/sdcard/DCIM/Depth_%05d.img", i);
            file = new File(filename);
            if (!file.createNewFile()) {
                throw new IOException(filename);
            }

            long t0 = SystemClock.uptimeMillis();
            FileOutputStream fos = new FileOutputStream(file);
            FileChannel channel = fos.getChannel();
            int bytesWritten = 0;
            int byteCount = 0;
            while (depthCloudData.hasRemaining()) {
                byteCount = channel.write(depthCloudData);
                if (0 == byteCount) {
                    throw new IOException(filename);
                } else {
                    bytesWritten += byteCount;
                }
            }
            channel.close();
            fos.flush();
            fos.close();
            long t1 = SystemClock.uptimeMillis();

            Log.v(TAG, String.format("Wrote Depth %d bytes as %s in %.3f seconds",
                    bytesWritten, file, (t1 - t0) * 0.001));
        } catch (IOException e) {
            Log.e(TAG, "Error creating new file: ", e);
        }
        return filename;
    }

    /**
     * @param context Application context.
     * @param jpegData JPEG byte stream.
     */
    public static String saveJpeg(Context context, byte[] jpegData, ContentResolver resolver) {
        String filename = "";
        try {
            File file;
            while (true) {
                int i = getNextInt(context, "counter");
                filename = String.format("/sdcard/DCIM/Camera/SNAP_%05d.JPG", i);
                file = new File(filename);
                if (file.createNewFile()) {
                    break;
                }
            }

            long t0 = SystemClock.uptimeMillis();
            OutputStream os = new FileOutputStream(file);
            os.write(jpegData);
            os.flush();
            os.close();
            long t1 = SystemClock.uptimeMillis();

            // update MediaStore so photos apps can find photos right away.
            if (UDPATE_MEDIA_STORE) {
                // really slow for some reason: MediaStore.Images.Media.insertImage(resolver, file.getAbsolutePath(), file.getName(), file.getName());
                insertImage(resolver, file);
            }
            long t2 = SystemClock.uptimeMillis();

            Log.v(TAG, String.format("Wrote JPEG %d bytes as %s in %.3f seconds; mediastore update = %.3f secs",
                    jpegData.length, file, (t1 - t0) * 0.001, (t2 - t1) * 0.001)    );
        } catch (IOException e) {
            Log.e(TAG, "Error creating new file: ", e);
        }
        return filename;
    }


    // We use this instead of MediaStore.Images.Media.insertImage() because we want to add date metadata
    public static void insertImage(ContentResolver cr, File file) {

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, file.getName());
        values.put(MediaStore.Images.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Images.Media.DESCRIPTION, file.getName());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
        // Add the date meta data to ensure the image is added at the front of the gallery
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

        try {
            cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Exception e) {
            Log.w(TAG, "Error updating media store for  " + file, e);
        }
    }

}
