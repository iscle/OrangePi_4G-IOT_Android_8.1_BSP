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

package com.android.tv.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.TrafficStats;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public final class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final boolean DEBUG = false;

    // The value of 64K, for MARK_READ_LIMIT, is chosen to be eight times the default buffer size
    // of BufferedInputStream (8K) allowing it to double its buffers three times. Also it is a
    // fairly reasonable value, not using too much memory and being large enough for most cases.
    private static final int MARK_READ_LIMIT = 64 * 1024;  // 64K

    private static final int CONNECTION_TIMEOUT_MS_FOR_URLCONNECTION = 3000;  // 3 sec
    private static final int READ_TIMEOUT_MS_FOR_URLCONNECTION = 10000;  // 10 sec

    private BitmapUtils() { /* cannot be instantiated */ }

    public static Bitmap scaleBitmap(Bitmap bm, int maxWidth, int maxHeight) {
        Rect rect = calculateNewSize(bm, maxWidth, maxHeight);
        return Bitmap.createScaledBitmap(bm, rect.right, rect.bottom, false);
    }

    public static Bitmap getScaledMutableBitmap(Bitmap bm, int maxWidth, int maxHeight) {
        Bitmap scaledBitmap = scaleBitmap(bm, maxWidth, maxHeight);
        return scaledBitmap.isMutable() ? scaledBitmap
                : scaledBitmap.copy(Bitmap.Config.ARGB_8888, true);
    }

    private static Rect calculateNewSize(Bitmap bm, int maxWidth, int maxHeight) {
        final double ratio = maxHeight / (double) maxWidth;
        final double bmRatio = bm.getHeight() / (double) bm.getWidth();
        Rect rect = new Rect();
        if (ratio > bmRatio) {
            rect.right = maxWidth;
            rect.bottom = Math.round((float) bm.getHeight() * maxWidth / bm.getWidth());
        } else {
            rect.right = Math.round((float) bm.getWidth() * maxHeight / bm.getHeight());
            rect.bottom = maxHeight;
        }
        return rect;
    }

    public static ScaledBitmapInfo createScaledBitmapInfo(String id, Bitmap bm, int maxWidth,
            int maxHeight) {
        return new ScaledBitmapInfo(id, scaleBitmap(bm, maxWidth, maxHeight),
                calculateInSampleSize(bm.getWidth(), bm.getHeight(), maxWidth, maxHeight));
    }

    /**
     * Decode large sized bitmap into requested size.
     */
    public static ScaledBitmapInfo decodeSampledBitmapFromUriString(Context context,
            String uriString, int reqWidth, int reqHeight) {
        if (TextUtils.isEmpty(uriString)) {
            return null;
        }

        Uri uri = Uri.parse(uriString).normalizeScheme();
        boolean isResourceUri = isContentResolverUri(uri);
        URLConnection urlConnection = null;
        InputStream inputStream = null;
        final int oldTag = TrafficStats.getThreadStatsTag();
        TrafficStats.setThreadStatsTag(NetworkTrafficTags.LOGO_FETCHER);
        try {
            if (isResourceUri) {
                inputStream = context.getContentResolver().openInputStream(uri);
            } else {
                // If the URLConnection is HttpURLConnection, disconnect() should be called
                // explicitly.
                urlConnection = getUrlConnection(uriString);
                inputStream = urlConnection.getInputStream();
            }
            inputStream = new BufferedInputStream(inputStream);
            inputStream.mark(MARK_READ_LIMIT);

            // Check the bitmap dimensions.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream, null, options);

            // Rewind the stream in order to restart bitmap decoding.
            try {
                inputStream.reset();
            } catch (IOException e) {
                if (DEBUG) Log.i(TAG, "Failed to rewind stream: " + uriString, e);

                // Failed to rewind the stream, try to reopen it.
                close(inputStream, urlConnection);
                if (isResourceUri) {
                    inputStream = context.getContentResolver().openInputStream(uri);
                } else {
                    urlConnection = getUrlConnection(uriString);
                    inputStream = urlConnection.getInputStream();
                }
            }

            // Decode the bitmap possibly resizing it.
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            if (bitmap == null) {
                return null;
            }
            return new ScaledBitmapInfo(uriString, bitmap, options.inSampleSize);
        } catch (IOException e) {
            if (DEBUG) {
                // It can happens in normal cases like when a channel doesn't have any logo.
                Log.w(TAG, "Failed to open stream: " + uriString, e);
            }
            return null;
        } catch (SQLiteException e) {
            Log.e(TAG, "Failed to open stream: " + uriString, e);
            return null;
        } finally {
            close(inputStream, urlConnection);
            TrafficStats.setThreadStatsTag(oldTag);
        }
    }

    private static URLConnection getUrlConnection(String uriString) throws IOException {
        URLConnection urlConnection = new URL(uriString).openConnection();
        urlConnection.setConnectTimeout(CONNECTION_TIMEOUT_MS_FOR_URLCONNECTION);
        urlConnection.setReadTimeout(READ_TIMEOUT_MS_FOR_URLCONNECTION);
        return urlConnection;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth,
            int reqHeight) {
        return calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight);
    }

    private static int calculateInSampleSize(int width, int height, int reqWidth, int reqHeight) {
        // Calculates the largest inSampleSize that, is a power of two and, keeps either width or
        // height larger or equal to the requested width and height.
        int ratio = Math.max(width / reqWidth, height / reqHeight);
        return Math.max(1, Integer.highestOneBit(ratio));
    }

    private static boolean isContentResolverUri(Uri uri) {
        String scheme = uri.getScheme();
        return ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme);
    }

    private static void close(Closeable closeable, URLConnection urlConnection) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Log and continue.
                Log.w(TAG,"Error closing " + closeable, e);
            }
        }
        if (urlConnection instanceof HttpURLConnection) {
            ((HttpURLConnection) urlConnection).disconnect();
        }
    }

    /**
     * A wrapper class which contains the loaded bitmap and the scaling information.
     */
    public static class ScaledBitmapInfo {
        /**
         * The id of  bitmap,  usually this is the URI of the original.
         */
        @NonNull
        public final String id;

        /**
         * The loaded bitmap object.
         */
        @NonNull
        public final Bitmap bitmap;

        /**
         * The scaling factor to the original bitmap. It should be an positive integer.
         *
         * @see android.graphics.BitmapFactory.Options#inSampleSize
         */
        public final int inSampleSize;

        /**
         * A constructor.
         *
         * @param bitmap The loaded bitmap object.
         * @param inSampleSize The sampling size.
         *        See {@link android.graphics.BitmapFactory.Options#inSampleSize}
         */
        public ScaledBitmapInfo(@NonNull String id, @NonNull Bitmap bitmap, int inSampleSize) {
            this.id = id;
            this.bitmap = bitmap;
            this.inSampleSize = inSampleSize;
        }

        /**
         * Checks if the bitmap needs to be reloaded. The scaling is performed by power 2.
         * The bitmap can be reloaded only if the required width or height is greater then or equal
         * to the existing bitmap.
         * If the full sized bitmap is already loaded, returns {@code false}.
         *
         * @see android.graphics.BitmapFactory.Options#inSampleSize
         */
        public boolean needToReload(int reqWidth, int reqHeight) {
            if (inSampleSize <= 1) {
                if (DEBUG) Log.d(TAG, "Reload not required " + this + " already full size.");
                return false;
            }
            Rect size = calculateNewSize(this.bitmap, reqWidth, reqHeight);
            boolean reload = (size.right >= bitmap.getWidth() * 2
                    || size.bottom >= bitmap.getHeight() * 2);
            if (DEBUG) {
                Log.d(TAG, "needToReload(" + reqWidth + ", " + reqHeight + ")=" + reload
                        + " because the new size would be " + size + " for " + this);
            }
            return reload;
        }

        /**
         * Returns {@code true} if a request the size of {@code other} would need a reload.
         */
        public boolean needToReload(ScaledBitmapInfo other){
            return needToReload(other.bitmap.getWidth(), other.bitmap.getHeight());
        }

        @Override
        public String toString() {
            return "ScaledBitmapInfo[" + id + "](in=" + inSampleSize + ", w=" + bitmap.getWidth()
                    + ", h=" + bitmap.getHeight() + ")";
        }
    }

    /**
     * Applies a color filter to the {@code drawable}. The color filter is made with the given
     * {@code color} and {@link android.graphics.PorterDuff.Mode#SRC_ATOP}.
     *
     * @see Drawable#setColorFilter
     */
    public static void setColorFilterToDrawable(int color, Drawable drawable) {
        if (drawable != null) {
            drawable.mutate().setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
    }
}
