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
package com.android.car.apps.common;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.ref.SoftReference;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Downloader class which loads a resource URI into an image view.
 * <p>
 * This class adds a cache over BitmapWorkerTask.
 */
public class BitmapDownloader {

    private static final String TAG = "BitmapDownloader";

    private static final boolean DEBUG = false;

    private static final int CORE_POOL_SIZE = 5;

    private static final Executor BITMAP_DOWNLOADER_THREAD_POOL_EXECUTOR =
            Executors.newFixedThreadPool(CORE_POOL_SIZE);

    // 1/4 of max memory is used for bitmap mem cache
    private static final int MEM_TO_CACHE = 4;

    // hard limit for bitmap mem cache in MB
    private static final int CACHE_HARD_LIMIT = 32;

    /**
     * bitmap cache item structure saved in LruCache
     */
    private static class BitmapItem {
        /**
         * cached bitmap
         */
        Bitmap mBitmap;
        /**
         * indicate if the bitmap is scaled down from original source (never scale up)
         */
        boolean mScaled;

        public BitmapItem(Bitmap bitmap, boolean scaled) {
            mBitmap = bitmap;
            mScaled = scaled;
        }
    }

    private LruCache<String, BitmapItem> mMemoryCache;

    private static BitmapDownloader sBitmapDownloader;

    private static final Object sBitmapDownloaderLock = new Object();

    // Bitmap cache also uses size of Bitmap as part of key.
    // Bitmap cache is divided into following buckets by height:
    // TODO: Pano currently is caring more about height, what about width in key?
    // height <= 128, 128 < height <= 512, height > 512
    // Different bitmap cache buckets save different bitmap cache items.
    // Bitmaps within same bucket share the largest cache item.
    private static final int[] SIZE_BUCKET = new int[]{128, 512, Integer.MAX_VALUE};

    public static abstract class BitmapCallback {
        SoftReference<BitmapWorkerTask> mTask;

        public abstract void onBitmapRetrieved(Bitmap bitmap);
    }

    /**
     * get the singleton BitmapDownloader for the application
     */
    public final static BitmapDownloader getInstance(Context context) {
        if (sBitmapDownloader == null) {
            synchronized(sBitmapDownloaderLock) {
                if (sBitmapDownloader == null) {
                    sBitmapDownloader = new BitmapDownloader(context);
                }
            }
        }
        return sBitmapDownloader;
    }

    public BitmapDownloader(Context context) {
        int memClass = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
                .getMemoryClass();
        memClass = memClass / MEM_TO_CACHE;
        if (memClass > CACHE_HARD_LIMIT) {
            memClass = CACHE_HARD_LIMIT;
        }
        int cacheSize = 1024 * 1024 * memClass;
        mMemoryCache = new LruCache<String, BitmapItem>(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapItem bitmap) {
                return bitmap.mBitmap.getByteCount();
            }
        };
    }

    /**
     * load bitmap in current thread, will *block* current thread.
     * @deprecated
     */
    @Deprecated
    public final Bitmap loadBitmapBlocking(final BitmapWorkerOptions options) {
        final boolean hasAccountImageUri = UriUtils.isAccountImageUri(options.getResourceUri());
        Bitmap bitmap = null;
        if (hasAccountImageUri) {
            AccountImageChangeObserver.getInstance().registerChangeUriIfPresent(options);
        } else {
            bitmap = getBitmapFromMemCache(options);
        }

        if (bitmap == null) {
            BitmapWorkerTask task = new BitmapWorkerTask(null) {
                @Override
                protected Bitmap doInBackground(BitmapWorkerOptions... params) {
                    final Bitmap bitmap = super.doInBackground(params);
                    if (bitmap != null && !hasAccountImageUri) {
                        addBitmapToMemoryCache(params[0], bitmap, isScaled());
                    }
                    return bitmap;
                }
            };

            return task.doInBackground(options);
        }
        return bitmap;
    }

    /**
     * Loads the bitmap into the image view.
     */
    public void loadBitmap(final BitmapWorkerOptions options, final ImageView imageView) {
        cancelDownload(imageView);
        final boolean hasAccountImageUri = UriUtils.isAccountImageUri(options.getResourceUri());
        Bitmap bitmap = null;
        if (hasAccountImageUri) {
            AccountImageChangeObserver.getInstance().registerChangeUriIfPresent(options);
        } else {
            bitmap = getBitmapFromMemCache(options);
        }

        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            BitmapWorkerTask task = new BitmapWorkerTask(imageView) {
                @Override
                protected Bitmap doInBackground(BitmapWorkerOptions... params) {
                    Bitmap bitmap = super.doInBackground(params);
                    if (bitmap != null && !hasAccountImageUri) {
                        addBitmapToMemoryCache(params[0], bitmap, isScaled());
                    }
                    return bitmap;
                }
            };
            imageView.setTag(R.id.imageDownloadTask, new SoftReference<BitmapWorkerTask>(task));
            task.execute(options);
        }
    }

    /**
     * Loads the bitmap.
     * <p>
     * This will be sent back to the callback object.
     */
    public void getBitmap(final BitmapWorkerOptions options, final BitmapCallback callback) {
        cancelDownload(callback);
        final boolean hasAccountImageUri = UriUtils.isAccountImageUri(options.getResourceUri());
        final Bitmap bitmap = hasAccountImageUri ? null : getBitmapFromMemCache(options);
        if (hasAccountImageUri) {
            AccountImageChangeObserver.getInstance().registerChangeUriIfPresent(options);
        }

        BitmapWorkerTask task = new BitmapWorkerTask(null) {
            @Override
            protected Bitmap doInBackground(BitmapWorkerOptions... params) {
                if (bitmap != null) {
                    return bitmap;
                }
                final Bitmap bitmap = super.doInBackground(params);
                if (bitmap != null && !hasAccountImageUri) {
                    addBitmapToMemoryCache(params[0], bitmap, isScaled());
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                callback.onBitmapRetrieved(bitmap);
            }
        };
        callback.mTask = new SoftReference<BitmapWorkerTask>(task);
        task.executeOnExecutor(BITMAP_DOWNLOADER_THREAD_POOL_EXECUTOR, options);
    }

    /**
     * Cancel download<p>
     * @param key {@link BitmapCallback} or {@link ImageView}
     */
    @SuppressWarnings("unchecked")
    public boolean cancelDownload(Object key) {
        BitmapWorkerTask task = null;
        if (key instanceof ImageView) {
            ImageView imageView = (ImageView)key;
            SoftReference<BitmapWorkerTask> softReference =
                    (SoftReference<BitmapWorkerTask>) imageView.getTag(R.id.imageDownloadTask);
            if (softReference != null) {
                task = softReference.get();
                softReference.clear();
            }
        } else if (key instanceof BitmapCallback) {
            BitmapCallback callback = (BitmapCallback) key;
            if (callback.mTask != null) {
                task = callback.mTask.get();
                callback.mTask = null;
            }
        }
        if (task != null) {
            return task.cancel(true);
        }
        return false;
    }

    private static String getBucketKey(String baseKey, Bitmap.Config bitmapConfig, int width) {
        for (int i = 0; i < SIZE_BUCKET.length; i++) {
            if (width <= SIZE_BUCKET[i]) {
                return new StringBuilder(baseKey.length() + 16).append(baseKey)
                        .append(":").append(bitmapConfig == null ? "" : bitmapConfig.ordinal())
                        .append(":").append(SIZE_BUCKET[i]).toString();
            }
        }
        // should never happen because last bucket is Integer.MAX_VALUE
        throw new RuntimeException();
    }

    private void addBitmapToMemoryCache(BitmapWorkerOptions key, Bitmap bitmap, boolean isScaled) {
        if (!key.isMemCacheEnabled()) {
            return;
        }
        String bucketKey = getBucketKey(
                key.getCacheKey(), key.getBitmapConfig(), bitmap.getHeight());
        BitmapItem bitmapItem = mMemoryCache.get(bucketKey);
        if (bitmapItem != null) {
            Bitmap currentBitmap = bitmapItem.mBitmap;
            // If somebody else happened to get a larger one in the bucket, discard our bitmap.
            // TODO: need a better way to prevent current downloading for the same Bitmap
            if (currentBitmap.getWidth() >= bitmap.getWidth() && currentBitmap.getHeight()
                    >= bitmap.getHeight()) {
                return;
            }
        }
        if (DEBUG) {
            Log.d(TAG, "add cache "+bucketKey+" isScaled = "+isScaled);
        }
        bitmapItem = new BitmapItem(bitmap, isScaled);
        mMemoryCache.put(bucketKey, bitmapItem);
    }

    private Bitmap getBitmapFromMemCache(BitmapWorkerOptions key) {
        if (key.getHeight() != BitmapWorkerOptions.MAX_IMAGE_DIMENSION_PX) {
            // 1. find the bitmap in the size bucket
            String bucketKey =
                    getBucketKey(key.getCacheKey(), key.getBitmapConfig(), key.getHeight());
            BitmapItem bitmapItem = mMemoryCache.get(bucketKey);
            if (bitmapItem != null) {
                Bitmap bitmap = bitmapItem.mBitmap;
                // now we have the bitmap in the bucket, use it when the bitmap is not scaled or
                // if the size is larger than or equals to the output size
                if (!bitmapItem.mScaled) {
                    return bitmap;
                }
                if (bitmap.getHeight() >= key.getHeight()) {
                    return bitmap;
                }
            }
            // 2. find un-scaled bitmap in smaller buckets.  If the un-scaled bitmap exists
            // in higher buckets,  we still need to scale it down.  Right now we just
            // return null and let the BitmapWorkerTask to do the same job again.
            // TODO: use the existing unscaled bitmap and we don't need to load it from resource
            // or network again.
            for (int i = SIZE_BUCKET.length - 1; i >= 0; i--) {
                if (SIZE_BUCKET[i] >= key.getHeight()) {
                    continue;
                }
                bucketKey = getBucketKey(key.getCacheKey(), key.getBitmapConfig(), SIZE_BUCKET[i]);
                bitmapItem = mMemoryCache.get(bucketKey);
                if (bitmapItem != null && !bitmapItem.mScaled) {
                    return bitmapItem.mBitmap;
                }
            }
            return null;
        }
        // 3. find un-scaled bitmap if size is not specified
        for (int i = SIZE_BUCKET.length - 1; i >= 0; i--) {
            String bucketKey =
                    getBucketKey(key.getCacheKey(), key.getBitmapConfig(), SIZE_BUCKET[i]);
            BitmapItem bitmapItem = mMemoryCache.get(bucketKey);
            if (bitmapItem != null && !bitmapItem.mScaled) {
                return bitmapItem.mBitmap;
            }
        }
        return null;
    }

    public Bitmap getLargestBitmapFromMemCache(BitmapWorkerOptions key) {
        // find largest bitmap matching the key
        for (int i = SIZE_BUCKET.length - 1; i >= 0; i--) {
            String bucketKey =
                    getBucketKey(key.getCacheKey(), key.getBitmapConfig(), SIZE_BUCKET[i]);
            BitmapItem bitmapItem = mMemoryCache.get(bucketKey);
            if (bitmapItem != null) {
                return bitmapItem.mBitmap;
            }
        }
        return null;
    }
}
