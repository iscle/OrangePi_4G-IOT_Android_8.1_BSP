/*
 * Copyright 2014 The Android Open Source Project
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

package android.media.cts;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.cts.CodecImage;
import android.media.Image;
import android.media.MediaCodec.BufferInfo;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class CodecUtils  {
    private static final String TAG = "CodecUtils";

    /** Load jni on initialization */
    static {
        Log.i(TAG, "before loadlibrary");
        System.loadLibrary("ctsmediacodec_jni");
        Log.i(TAG, "after loadlibrary");
    }

    private static class ImageWrapper extends CodecImage {
        private final Image mImage;
        private final Plane[] mPlanes;

        private ImageWrapper(Image image) {
            mImage = image;
            Image.Plane[] planes = mImage.getPlanes();

            mPlanes = new Plane[planes.length];
            for (int i = 0; i < planes.length; i++) {
                mPlanes[i] = new PlaneWrapper(planes[i]);
            }
        }

        public static ImageWrapper createFromImage(Image image) {
            return new ImageWrapper(image);
        }

        @Override
        public int getFormat() {
            return mImage.getFormat();
        }

        @Override
        public int getWidth() {
            return mImage.getWidth();
        }

        @Override
        public int getHeight() {
            return mImage.getHeight();
        }

        @Override
        public long getTimestamp() {
            return mImage.getTimestamp();
        }

        @Override
        public Plane[] getPlanes() {
            return mPlanes;
        }

        @Override
        public void close() {
            mImage.close();
        }

        private static class PlaneWrapper extends CodecImage.Plane {
            private final Image.Plane mPlane;

            PlaneWrapper(Image.Plane plane) {
                mPlane = plane;
            }

            @Override
            public int getRowStride() {
                return mPlane.getRowStride();
            }

           @Override
            public int getPixelStride() {
               return mPlane.getPixelStride();
            }

            @Override
            public ByteBuffer getBuffer() {
                return mPlane.getBuffer();
            }
        }
    }

    /* two native image checksum functions */
    public native static int getImageChecksumAdler32(CodecImage image);
    public native static String getImageChecksumMD5(CodecImage image);

    public native static void copyFlexYUVImage(CodecImage target, CodecImage source);

    public static void copyFlexYUVImage(Image target, CodecImage source) {
        copyFlexYUVImage(ImageWrapper.createFromImage(target), source);
    }
    public static void copyFlexYUVImage(Image target, Image source) {
        copyFlexYUVImage(
                ImageWrapper.createFromImage(target),
                ImageWrapper.createFromImage(source));
    }

    public native static void fillImageRectWithYUV(
            CodecImage image, Rect area, int y, int u, int v);

    public static void fillImageRectWithYUV(Image image, Rect area, int y, int u, int v) {
        fillImageRectWithYUV(ImageWrapper.createFromImage(image), area, y, u, v);
    }

    public native static long[] getRawStats(CodecImage image, Rect area);

    public static long[] getRawStats(Image image, Rect area) {
        return getRawStats(ImageWrapper.createFromImage(image), area);
    }

    public native static float[] getYUVStats(CodecImage image, Rect area);

    public static float[] getYUVStats(Image image, Rect area) {
        return getYUVStats(ImageWrapper.createFromImage(image), area);
    }

    public native static float[] Raw2YUVStats(long[] rawStats);

    /**
     * This method reads the binarybar code on the top row of a bitmap. Each 16x16
     * block is one digit, with black=0 and white=1. LSB is on the left.
     */
    public static int readBinaryCounterFromBitmap(Bitmap bitmap) {
        int numDigits = bitmap.getWidth() / 16;
        int counter = 0;
        for (int i = 0; i < numDigits; i++) {
            int rgb = bitmap.getPixel(i * 16 + 8, 8);
            if (Color.red(rgb) > 128) {
                counter |= (1 << i);
            }
        }
        return counter;
    }

    public static void saveBitmapToFile(Bitmap bitmap, String filename) {
        try {
            File outputFile = new File(Environment.getExternalStorageDirectory(), filename);

            Log.d(TAG, "Saving bitmap to: " + outputFile);
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch(Exception e) {
            Log.e(TAG, "Failed to save to file: " + e);
        }
    }
}

