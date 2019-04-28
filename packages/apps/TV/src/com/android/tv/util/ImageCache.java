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

import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.util.LruCache;

import com.android.tv.common.MemoryManageable;
import com.android.tv.util.BitmapUtils.ScaledBitmapInfo;

/**
 * A convenience class for caching bitmap.
 */
public class ImageCache implements MemoryManageable {
    private static final float MAX_CACHE_SIZE_PERCENT = 0.8f;
    private static final float MIN_CACHE_SIZE_PERCENT = 0.05f;
    private static final float DEFAULT_CACHE_SIZE_PERCENT = 0.1f;
    private static final boolean DEBUG = false;
    private static final String TAG = "ImageCache";
    private static final int MIN_CACHE_SIZE_KBYTES = 1024;

    private final LruCache<String, ScaledBitmapInfo> mMemoryCache;

    /**
     * Creates a new ImageCache object with a given cache size percent.
     *
     * @param memCacheSizePercent The cache size as a percent of available app memory.
     */
    private ImageCache(float memCacheSizePercent) {
        int memCacheSize = calculateMemCacheSize(memCacheSizePercent);

        // Set up memory cache
        if (DEBUG) {
            Log.d(TAG, "Memory cache created (size = " + memCacheSize + " Kbytes)");
        }
        mMemoryCache = new LruCache<String, ScaledBitmapInfo>(memCacheSize) {
            /**
             * Measure item size in kilobytes rather than units which is more practical for a bitmap
             * cache
             */
            @Override
            protected int sizeOf(String key, ScaledBitmapInfo bitmapInfo) {
                return (bitmapInfo.bitmap.getByteCount() + 1023) / 1024;
            }
        };
    }

    private static ImageCache sImageCache;

    /**
     * Returns an existing ImageCache, if it doesn't exist, a new one is created using the supplied
     * param.
     *
     * @param memCacheSizePercent The cache size as a percent of available app memory. Should be in
     *                            range of MIN_CACHE_SIZE_PERCENT(0.05) ~ MAX_CACHE_SIZE_PERCENT(0.8).
     * @return An existing retained ImageCache object or a new one if one did not exist
     */
    public static synchronized ImageCache getInstance(float memCacheSizePercent) {
        if (sImageCache == null) {
            sImageCache = newInstance(memCacheSizePercent);
        }
        return sImageCache;
    }

    @VisibleForTesting
    static ImageCache newInstance(float memCacheSizePercent) {
        return new ImageCache(memCacheSizePercent);
    }


    /**
     * Returns an existing ImageCache, if it doesn't exist, a new one is created using
     * DEFAULT_CACHE_SIZE_PERCENT (0.1).
     *
     * @return An existing retained ImageCache object or a new one if one did not exist
     */
    public static ImageCache getInstance() {
        return getInstance(DEFAULT_CACHE_SIZE_PERCENT);
    }

    /**
     * Adds a bitmap to memory cache.
     *
     * <p>If there is an existing bitmap only replace it if
     * {@link ScaledBitmapInfo#needToReload(ScaledBitmapInfo)} is true.
     *
     * @param bitmapInfo The {@link ScaledBitmapInfo} object to store
     */
    public void putIfNeeded(ScaledBitmapInfo bitmapInfo) {
        if (bitmapInfo == null || bitmapInfo.id == null) {
            throw new IllegalArgumentException("Neither bitmap nor bitmap.id should be null.");
        }
        String key = bitmapInfo.id;
        // Add to memory cache
        synchronized (mMemoryCache) {
            ScaledBitmapInfo old = mMemoryCache.put(key, bitmapInfo);
            if (old != null && !old.needToReload(bitmapInfo)) {
                mMemoryCache.put(key, old);
                if (DEBUG) {
                    Log.d(TAG,
                            "Kept original " + old + " in memory cache because it was larger than "
                                    + bitmapInfo + ".");
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Add " + bitmapInfo + " to memory cache. Current size is " +
                            mMemoryCache.size() + " / " + mMemoryCache.maxSize() + " Kbytes");
                }
            }
        }
    }

    /**
     * Get from memory cache.
     *
     * @param key Unique identifier for which item to get
     * @return The bitmap if found in cache, null otherwise
     */
    public ScaledBitmapInfo get(String key) {
        ScaledBitmapInfo memBitmapInfo = mMemoryCache.get(key);
        if (DEBUG) {
            int hit = mMemoryCache.hitCount();
            int miss = mMemoryCache.missCount();
            String result = memBitmapInfo == null ? "miss" : "hit";
            double ratio = ((double) hit) / (hit + miss) * 100;
            Log.d(TAG, "Memory cache " + result + " for  " + key);
            Log.d(TAG, "Memory cache " + hit + "h:" + miss + "m " + ratio + "%");
        }
        return memBitmapInfo;
    }

    /**
     * Remove from memory cache.
     *
     * @param key Unique identifier for which item to remove
     * @return The previous bitmap mapped by key
     */
    public ScaledBitmapInfo remove(String key) {
        return mMemoryCache.remove(key);
    }

    /**
     * Calculates the memory cache size based on a percentage of the max available VM memory. Eg.
     * setting percent to 0.2 would set the memory cache to one fifth of the available memory.
     * Throws {@link IllegalArgumentException} if percent is < 0.05 or > .8. memCacheSize is stored
     * in kilobytes instead of bytes as this will eventually be passed to construct a LruCache
     * which takes an int in its constructor. This value should be chosen carefully based on a
     * number of factors Refer to the corresponding Android Training class for more discussion:
     * http://developer.android.com/training/displaying-bitmaps/
     *
     * @param percent Percent of available app memory to use to size memory cache.
     */
    public static int calculateMemCacheSize(float percent) {
        if (percent < MIN_CACHE_SIZE_PERCENT || percent > MAX_CACHE_SIZE_PERCENT) {
            throw new IllegalArgumentException("setMemCacheSizePercent - percent must be "
                    + "between 0.05 and 0.8 (inclusive)");
        }
        return Math.max(MIN_CACHE_SIZE_KBYTES,
                Math.round(percent * Runtime.getRuntime().maxMemory() / 1024));
    }

    @Override
    public void performTrimMemory(int level) {
        mMemoryCache.evictAll();
    }
}
