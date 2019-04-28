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
import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Downloader class which loads a resource URI into an image view or triggers a callback
 * <p>
 * This class adds a LRU cache over DrawableLoader.
 * <p>
 * Calling getBitmap() or loadBitmap() will return a RefcountBitmapDrawable with initial refcount =
 * 2 by the cache table and by caller.  You must call releaseRef() when you are done with the resource.
 * The most common way is using RefcountImageView, and releaseRef() for you.  Once both RefcountImageView
 * and LRUCache removes the refcount, the underlying bitmap will be used for decoding new bitmap.
 * <p>
 * If the URI does not point to a bitmap (e.g. point to a drawable xml, we won't cache it and we
 * directly return a regular Drawable).
 */
public class DrawableDownloader {

    private static final String TAG = "DrawableDownloader";

    private static final boolean DEBUG = false;

    private static final int CORE_POOL_SIZE = 5;

    // thread pool for loading non android-resources such as http,  content
    private static final Executor BITMAP_DOWNLOADER_THREAD_POOL_EXECUTOR =
            Executors.newFixedThreadPool(CORE_POOL_SIZE);

    private static final int CORE_RESOURCE_POOL_SIZE = 1;

    // thread pool for loading android resources,  we use separate thread pool so
    // that network loading will not block local android icons
    private static final Executor BITMAP_RESOURCE_DOWNLOADER_THREAD_POOL_EXECUTOR =
            Executors.newFixedThreadPool(CORE_RESOURCE_POOL_SIZE);

    // 1/4 of max memory is used for bitmap mem cache
    private static final int MEM_TO_CACHE = 4;

    // hard limit for bitmap mem cache in MB
    private static final int CACHE_HARD_LIMIT = 32;

    /**
     * bitmap cache item structure saved in LruCache
     */
    private static class BitmapItem {
        int mOriginalWidth;
        int mOriginalHeight;
        ArrayList<BitmapDrawable> mBitmaps = new ArrayList<BitmapDrawable>(3);
        int mByteCount;
        public BitmapItem(int originalWidth, int originalHeight) {
            mOriginalWidth = originalWidth;
            mOriginalHeight = originalHeight;
        }

        // get bitmap from the list
        BitmapDrawable findDrawable(BitmapWorkerOptions options) {
            for (int i = 0, c = mBitmaps.size(); i < c; i++) {
                BitmapDrawable d = mBitmaps.get(i);
                // use drawable with original size
                if (d.getIntrinsicWidth() == mOriginalWidth
                        && d.getIntrinsicHeight() == mOriginalHeight) {
                    return d;
                }
                // if specified width/height in options and is smaller than
                // cached one, we can use this cached drawable
                if (options.getHeight() != BitmapWorkerOptions.MAX_IMAGE_DIMENSION_PX) {
                    if (options.getHeight() <= d.getIntrinsicHeight()) {
                        return d;
                    }
                } else if (options.getWidth() != BitmapWorkerOptions.MAX_IMAGE_DIMENSION_PX) {
                    if (options.getWidth() <= d.getIntrinsicWidth()) {
                        return d;
                    }
                }
            }
            return null;
        }

        @SuppressWarnings("unused")
        BitmapDrawable findLargestDrawable(BitmapWorkerOptions options) {
            return mBitmaps.size() == 0 ? null : mBitmaps.get(0);
        }

        void addDrawable(BitmapDrawable d) {
            int i = 0, c = mBitmaps.size();
            for (; i < c; i++) {
                BitmapDrawable drawable = mBitmaps.get(i);
                if (drawable.getIntrinsicHeight() < d.getIntrinsicHeight()) {
                    break;
                }
            }
            mBitmaps.add(i, d);
            mByteCount += RecycleBitmapPool.getSize(d.getBitmap());
        }

        void clear() {
            for (int i = 0, c = mBitmaps.size(); i < c; i++) {
                BitmapDrawable d = mBitmaps.get(i);
                if (d instanceof RefcountBitmapDrawable) {
                    ((RefcountBitmapDrawable) d).getRefcountObject().releaseRef();
                }
            }
            mBitmaps.clear();
            mByteCount = 0;
        }
    }

    public static abstract class BitmapCallback {
        SoftReference<DrawableLoader> mTask;

        public abstract void onBitmapRetrieved(Drawable bitmap);
    }

    private Context mContext;
    private LruCache<String, BitmapItem> mMemoryCache;
    private RecycleBitmapPool mRecycledBitmaps;

    private static DrawableDownloader sBitmapDownloader;

    private static final Object sBitmapDownloaderLock = new Object();

    /**
     * get the singleton BitmapDownloader for the application
     */
    public final static DrawableDownloader getInstance(Context context) {
        if (sBitmapDownloader == null) {
            synchronized(sBitmapDownloaderLock) {
                if (sBitmapDownloader == null) {
                    sBitmapDownloader = new DrawableDownloader(context);
                }
            }
        }
        return sBitmapDownloader;
    }

    private static String getBucketKey(String baseKey, Bitmap.Config bitmapConfig) {
        return new StringBuilder(baseKey.length() + 16).append(baseKey)
                         .append(":").append(bitmapConfig == null ? "" : bitmapConfig.ordinal())
                         .toString();
     }

    public static Drawable getDrawable(Context context, ShortcutIconResource iconResource)
            throws NameNotFoundException {
        return DrawableLoader.getDrawable(context, iconResource);
    }

    private DrawableDownloader(Context context) {
        mContext = context;
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
                return bitmap.mByteCount;
            }
            @Override
            protected void entryRemoved(
                    boolean evicted, String key, BitmapItem oldValue, BitmapItem newValue) {
                if (evicted) {
                    oldValue.clear();
                }
            }
        };
        mRecycledBitmaps = new RecycleBitmapPool();
    }

    /**
     * trim memory cache to 0~1 * maxSize
     */
    public void trimTo(float amount) {
        if (amount == 0f) {
            mMemoryCache.evictAll();
        } else {
            mMemoryCache.trimToSize((int) (amount * mMemoryCache.maxSize()));
        }
    }

    /**
     * load bitmap in current thread, will *block* current thread.
     * @deprecated
     */
    @Deprecated
    public final Drawable loadBitmapBlocking(BitmapWorkerOptions options) {
        final boolean hasAccountImageUri = UriUtils.isAccountImageUri(options.getResourceUri());
        Drawable bitmap = null;
        if (hasAccountImageUri) {
            AccountImageChangeObserver.getInstance().registerChangeUriIfPresent(options);
        } else {
            bitmap = getBitmapFromMemCache(options);
        }

        if (bitmap == null) {
            DrawableLoader task = new DrawableLoader(null, mRecycledBitmaps) {
                @Override
                protected Drawable doInBackground(BitmapWorkerOptions... params) {
                    final Drawable bitmap = super.doInBackground(params);
                    if (bitmap != null && !hasAccountImageUri) {
                        addBitmapToMemoryCache(params[0], bitmap, this);
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
    public void loadBitmap(BitmapWorkerOptions options, final ImageView imageView) {
        cancelDownload(imageView);
        final boolean hasAccountImageUri = UriUtils.isAccountImageUri(options.getResourceUri());
        Drawable bitmap = null;
        if (hasAccountImageUri) {
            AccountImageChangeObserver.getInstance().registerChangeUriIfPresent(options);
        } else {
            bitmap = getBitmapFromMemCache(options);
        }

        if (bitmap != null) {
            imageView.setImageDrawable(bitmap);
        } else {
            DrawableLoader task = new DrawableLoader(imageView, mRecycledBitmaps) {
                @Override
                protected Drawable doInBackground(BitmapWorkerOptions... params) {
                    Drawable bitmap = super.doInBackground(params);
                    if (bitmap != null && !hasAccountImageUri) {
                        addBitmapToMemoryCache(params[0], bitmap, this);
                    }
                    return bitmap;
                }
            };
            imageView.setTag(R.id.imageDownloadTask, new SoftReference<DrawableLoader>(task));
            scheduleTask(task, options);
        }
    }

    /**
     * Loads the bitmap.
     * <p>
     * This will be sent back to the callback object.
     */
    public void getBitmap(BitmapWorkerOptions options, final BitmapCallback callback) {
        cancelDownload(callback);
        final boolean hasAccountImageUri = UriUtils.isAccountImageUri(options.getResourceUri());
        final Drawable bitmap = hasAccountImageUri ? null : getBitmapFromMemCache(options);
        if (hasAccountImageUri) {
            AccountImageChangeObserver.getInstance().registerChangeUriIfPresent(options);
        }

        if (bitmap != null) {
            callback.onBitmapRetrieved(bitmap);
            return;
        }
        DrawableLoader task = new DrawableLoader(null, mRecycledBitmaps) {
            @Override
            protected Drawable doInBackground(BitmapWorkerOptions... params) {
                final Drawable bitmap = super.doInBackground(params);
                if (bitmap != null && !hasAccountImageUri) {
                    addBitmapToMemoryCache(params[0], bitmap, this);
                }
                return bitmap;
            }

            @Override
            protected void onPostExecute(Drawable bitmap) {
                callback.onBitmapRetrieved(bitmap);
            }
        };
        callback.mTask = new SoftReference<DrawableLoader>(task);
        scheduleTask(task, options);
    }

    private static void scheduleTask(DrawableLoader task, BitmapWorkerOptions options) {
        if (options.isFromResource()) {
            task.executeOnExecutor(BITMAP_RESOURCE_DOWNLOADER_THREAD_POOL_EXECUTOR, options);
        } else {
            task.executeOnExecutor(BITMAP_DOWNLOADER_THREAD_POOL_EXECUTOR, options);
        }
    }

    /**
     * Cancel download<p>
     * @param key {@link BitmapCallback} or {@link ImageView}
     */
    @SuppressWarnings("unchecked")
    public boolean cancelDownload(Object key) {
        DrawableLoader task = null;
        if (key instanceof ImageView) {
            ImageView imageView = (ImageView)key;
            SoftReference<DrawableLoader> softReference =
                    (SoftReference<DrawableLoader>) imageView.getTag(R.id.imageDownloadTask);
            if (softReference != null) {
                task = softReference.get();
                softReference.clear();
            }
        } else if (key instanceof BitmapCallback) {
            BitmapCallback callback = (BitmapCallback)key;
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

    private void addBitmapToMemoryCache(BitmapWorkerOptions key, Drawable bitmap,
            DrawableLoader loader) {
        if (!key.isMemCacheEnabled()) {
            return;
        }
        if (!(bitmap instanceof BitmapDrawable)) {
            return;
        }
        String bucketKey = getBucketKey(key.getCacheKey(), key.getBitmapConfig());
        BitmapItem bitmapItem = mMemoryCache.get(bucketKey);
        if (DEBUG) {
            Log.d(TAG, "add cache "+bucketKey);
        }
        if (bitmapItem != null) {
            // remove and re-add to update size
            mMemoryCache.remove(bucketKey);
        } else {
            bitmapItem = new BitmapItem(loader.getOriginalWidth(), loader.getOriginalHeight());
        }
        if (bitmap instanceof RefcountBitmapDrawable) {
            RefcountBitmapDrawable refcountDrawable = (RefcountBitmapDrawable) bitmap;
            refcountDrawable.getRefcountObject().addRef();
        }
        bitmapItem.addDrawable((BitmapDrawable) bitmap);
        mMemoryCache.put(bucketKey, bitmapItem);
    }

    private Drawable getBitmapFromMemCache(BitmapWorkerOptions key) {
        String bucketKey =
                getBucketKey(key.getCacheKey(), key.getBitmapConfig());
        BitmapItem item = mMemoryCache.get(bucketKey);
        if (item != null) {
            return createRefCopy(item.findDrawable(key));
        }
        return null;
    }

    public BitmapDrawable getLargestBitmapFromMemCache(BitmapWorkerOptions key) {
        String bucketKey =
                getBucketKey(key.getCacheKey(), key.getBitmapConfig());
        BitmapItem item = mMemoryCache.get(bucketKey);
        if (item != null) {
            return (BitmapDrawable) createRefCopy(item.findLargestDrawable(key));
        }
        return null;
    }

    private Drawable createRefCopy(Drawable d) {
        if (d != null) {
            if (d instanceof RefcountBitmapDrawable) {
                RefcountBitmapDrawable refcountDrawable = (RefcountBitmapDrawable) d;
                refcountDrawable.getRefcountObject().addRef();
                d = new RefcountBitmapDrawable(mContext.getResources(),
                        refcountDrawable);
            }
            return d;
        }
        return null;
    }

}
