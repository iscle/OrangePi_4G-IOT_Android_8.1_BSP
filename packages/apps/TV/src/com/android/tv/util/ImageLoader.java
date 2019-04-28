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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.util.BitmapUtils.ScaledBitmapInfo;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class wraps up completing some arbitrary long running work when loading a bitmap. It
 * handles things like using a memory cache, running the work in a background thread.
 */
public final class ImageLoader {
    private static final String TAG = "ImageLoader";
    private static final boolean DEBUG = false;

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    // We want at least 2 threads and at most 4 threads in the core pool,
    // preferring to have 1 less than the CPU count to avoid saturating
    // the CPU with background work
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final ThreadFactory sThreadFactory = new NamedThreadFactory("ImageLoader");

    private static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<>(128);

    /**
     * An private {@link Executor} that can be used to execute tasks in parallel.
     *
     * <p>{@code IMAGE_THREAD_POOL_EXECUTOR} setting are copied from {@link AsyncTask}
     * Since we do a lot of concurrent image loading we can exhaust a thread pool.
     * ImageLoader catches the error, and just leaves the image blank.
     * However other tasks will fail and crash the application.
     *
     * <p>Using a separate thread pool prevents image loading from causing other tasks to fail.
     */
    private static final Executor IMAGE_THREAD_POOL_EXECUTOR;

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE,
                MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, sPoolWorkQueue,
                sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        IMAGE_THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    private static Handler sMainHandler;

    /**
     * Handles when image loading is finished.
     *
     * <p>Use this to prevent leaking an Activity or other Context while image loading is
     *  still pending. When you extend this class you <strong>MUST NOT</strong> use a non static
     *  inner class, or the containing object will still be leaked.
     */
    @UiThread
    public static abstract class ImageLoaderCallback<T> {
        private final WeakReference<T> mWeakReference;

        /**
         * Creates an callback keeping a weak reference to {@code referent}.
         *
         * <p> If the "referent" is no longer valid, it no longer makes sense to run the
         * callback. The referent is the View, or Activity or whatever that actually needs to
         * receive the Bitmap.  If the referent has been GC, then no need to run the callback.
         */
        public ImageLoaderCallback(T referent) {
            mWeakReference = new WeakReference<>(referent);
        }

        /**
         * Called when bitmap is loaded.
         */
        private void onBitmapLoaded(@Nullable Bitmap bitmap) {
            T referent = mWeakReference.get();
            if (referent != null) {
                onBitmapLoaded(referent, bitmap);
            } else {
                if (DEBUG) Log.d(TAG, "onBitmapLoaded not called because weak reference is gone");
            }
        }

        /**
         * Called when bitmap is loaded if the weak reference is still valid.
         */
        public abstract void onBitmapLoaded(T referent, @Nullable Bitmap bitmap);
    }

    private static final Map<String, LoadBitmapTask> sPendingListMap = new HashMap<>();

    /**
     * Preload a bitmap image into the cache.
     *
     * <p>Not to make heavy CPU load, AsyncTask.SERIAL_EXECUTOR is used for the image loading.
     * <p>This method is thread safe.
     */
    public static void prefetchBitmap(Context context, final String uriString, final int maxWidth,
            final int maxHeight) {
        if (DEBUG) Log.d(TAG, "prefetchBitmap() " + uriString);
        if (Looper.getMainLooper() == Looper.myLooper()) {
            doLoadBitmap(context, uriString, maxWidth, maxHeight, null, AsyncTask.SERIAL_EXECUTOR);
        } else {
            final Context appContext = context.getApplicationContext();
            getMainHandler().post(new Runnable() {
                @Override
                @MainThread
                public void run() {
                    // Calling from the main thread prevents a ConcurrentModificationException
                    // in LoadBitmapTask.onPostExecute
                    doLoadBitmap(appContext, uriString, maxWidth, maxHeight, null,
                            AsyncTask.SERIAL_EXECUTOR);
                }
            });
        }
    }

    /**
     * Load a bitmap image with the cache using a ContentResolver.
     *
     * <p><b>Note</b> that the callback will be called synchronously if the bitmap already is in
     * the cache.
     *
     * @return {@code true} if the load is complete and the callback is executed.
     */
    @UiThread
    public static boolean loadBitmap(Context context, String uriString,
            ImageLoaderCallback callback) {
        return loadBitmap(context, uriString, Integer.MAX_VALUE, Integer.MAX_VALUE, callback);
    }

    /**
     * Load a bitmap image with the cache and resize it with given params.
     *
     * <p><b>Note</b> that the callback will be called synchronously if the bitmap already is in
     * the cache.
     *
     * @return {@code true} if the load is complete and the callback is executed.
     */
    @UiThread
    public static boolean loadBitmap(Context context, String uriString, int maxWidth, int maxHeight,
            ImageLoaderCallback callback) {
        if (DEBUG) {
            Log.d(TAG, "loadBitmap() " + uriString);
        }
        return doLoadBitmap(context, uriString, maxWidth, maxHeight, callback,
                IMAGE_THREAD_POOL_EXECUTOR);
    }

    private static boolean doLoadBitmap(Context context, String uriString,
            int maxWidth, int maxHeight, ImageLoaderCallback callback, Executor executor) {
        // Check the cache before creating a Task.  The cache will be checked again in doLoadBitmap
        // but checking a cache is much cheaper than creating an new task.
        ImageCache imageCache = ImageCache.getInstance();
        ScaledBitmapInfo bitmapInfo = imageCache.get(uriString);
        if (bitmapInfo != null && !bitmapInfo.needToReload(maxWidth, maxHeight)) {
            if (callback != null) {
                callback.onBitmapLoaded(bitmapInfo.bitmap);
            }
            return true;
        }
        return doLoadBitmap(callback, executor,
                new LoadBitmapFromUriTask(context, imageCache, uriString, maxWidth, maxHeight));
    }

    /**
     * Load a bitmap image with the cache and resize it with given params.
     *
     * <p>The LoadBitmapTask will be executed on a non ui thread.
     *
     * @return {@code true} if the load is complete and the callback is executed.
     */
    @UiThread
    public static boolean loadBitmap(ImageLoaderCallback callback, LoadBitmapTask loadBitmapTask) {
        if (DEBUG) {
            Log.d(TAG, "loadBitmap() " + loadBitmapTask);
        }
        return doLoadBitmap(callback, IMAGE_THREAD_POOL_EXECUTOR, loadBitmapTask);
    }

    /**
     * @return {@code true} if the load is complete and the callback is executed.
     */
    @UiThread
    private static boolean doLoadBitmap(ImageLoaderCallback callback, Executor executor,
            LoadBitmapTask loadBitmapTask) {
        ScaledBitmapInfo bitmapInfo = loadBitmapTask.getFromCache();
        boolean needToReload = loadBitmapTask.isReloadNeeded();
        if (bitmapInfo != null && !needToReload) {
            if (callback != null) {
                callback.onBitmapLoaded(bitmapInfo.bitmap);
            }
            return true;
        }
        LoadBitmapTask existingTask = sPendingListMap.get(loadBitmapTask.getKey());
        if (existingTask != null && !loadBitmapTask.isReloadNeeded(existingTask)) {
            // The image loading is already scheduled and is large enough.
            if (callback != null) {
                existingTask.mCallbacks.add(callback);
            }
        } else {
            if (callback != null) {
                loadBitmapTask.mCallbacks.add(callback);
            }
            sPendingListMap.put(loadBitmapTask.getKey(), loadBitmapTask);
            try {
                loadBitmapTask.executeOnExecutor(executor);
            } catch (RejectedExecutionException e) {
                Log.e(TAG, "Failed to create new image loader", e);
                sPendingListMap.remove(loadBitmapTask.getKey());
            }
        }
        return false;
    }

    /**
     * Loads and caches a a possibly scaled down version of a bitmap.
     *
     * <p>Implement {@link #doGetBitmapInBackground} to do the actual loading.
     */
    public static abstract class LoadBitmapTask extends AsyncTask<Void, Void, ScaledBitmapInfo> {
        protected final Context mAppContext;
        protected final int mMaxWidth;
        protected final int mMaxHeight;
        private final Set<ImageLoaderCallback> mCallbacks = new ArraySet<>();
        private final ImageCache mImageCache;
        private final String mKey;

        /**
         * Returns true if a reload is needed compared to current results in the cache or false if
         * there is not match in the cache.
         */
        private boolean isReloadNeeded() {
            ScaledBitmapInfo bitmapInfo = getFromCache();
            boolean needToReload = bitmapInfo != null && bitmapInfo
                    .needToReload(mMaxWidth, mMaxHeight);
            if (DEBUG) {
                if (needToReload) {
                    Log.d(TAG, "Bitmap needs to be reloaded. {"
                            + "originalWidth=" + bitmapInfo.bitmap.getWidth()
                            + ", originalHeight=" + bitmapInfo.bitmap.getHeight()
                            + ", reqWidth=" + mMaxWidth
                            + ", reqHeight=" + mMaxHeight
                            + "}");
                }
            }
            return needToReload;
        }

        /**
         * Checks if a reload would be needed if the results of other was available.
         */
        private boolean isReloadNeeded(LoadBitmapTask other) {
            return (other.mMaxHeight != Integer.MAX_VALUE && mMaxHeight >= other.mMaxHeight * 2)
                    || (other.mMaxWidth != Integer.MAX_VALUE && mMaxWidth >= other.mMaxWidth * 2);
        }

        @Nullable
        public final ScaledBitmapInfo getFromCache() {
            return mImageCache.get(mKey);
        }

        public LoadBitmapTask(Context context, ImageCache imageCache, String key, int maxHeight,
                int maxWidth) {
            if (maxWidth == 0 || maxHeight == 0) {
                throw new IllegalArgumentException(
                        "Image size should not be 0. {width=" + maxWidth + ", height=" + maxHeight
                                + "}");
            }
            mAppContext = context.getApplicationContext();
            mKey = key;
            mImageCache = imageCache;
            mMaxHeight = maxHeight;
            mMaxWidth = maxWidth;
        }

        /**
         * Loads the bitmap returning a possibly scaled down version.
         */
        @Nullable
        @WorkerThread
        public abstract ScaledBitmapInfo doGetBitmapInBackground();

        @Override
        @Nullable
        public final ScaledBitmapInfo doInBackground(Void... params) {
            ScaledBitmapInfo bitmapInfo = getFromCache();
            if (bitmapInfo != null && !isReloadNeeded()) {
                return bitmapInfo;
            }
            bitmapInfo = doGetBitmapInBackground();
            if (bitmapInfo != null) {
                mImageCache.putIfNeeded(bitmapInfo);
            }
            return bitmapInfo;
        }

        @Override
        public final void onPostExecute(ScaledBitmapInfo scaledBitmapInfo) {
            if (DEBUG) Log.d(ImageLoader.TAG, "Bitmap is loaded " + mKey);

            for (ImageLoader.ImageLoaderCallback callback : mCallbacks) {
                callback.onBitmapLoaded(scaledBitmapInfo == null ? null : scaledBitmapInfo.bitmap);
            }
            ImageLoader.sPendingListMap.remove(mKey);
        }

        public final String getKey() {
            return mKey;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + "(" + mKey + " " + mMaxWidth + "x" + mMaxHeight
                    + ")";
        }
    }

    private static final class LoadBitmapFromUriTask extends LoadBitmapTask {
        private LoadBitmapFromUriTask(Context context, ImageCache imageCache, String uriString,
                int maxWidth, int maxHeight) {
            super(context, imageCache, uriString, maxHeight, maxWidth);
        }

        @Override
        @Nullable
        public final ScaledBitmapInfo doGetBitmapInBackground() {
            return BitmapUtils
                    .decodeSampledBitmapFromUriString(mAppContext, getKey(), mMaxWidth, mMaxHeight);
        }
    }

    /**
     * Loads and caches the logo for a given {@link TvInputInfo}
     */
    public static final class LoadTvInputLogoTask extends LoadBitmapTask {
        private final TvInputInfo mInfo;

        public LoadTvInputLogoTask(Context context, ImageCache cache, TvInputInfo info) {
            super(context,
                    cache,
                    getTvInputLogoKey(info.getId()),
                    context.getResources()
                            .getDimensionPixelSize(R.dimen.channel_banner_input_logo_size),
                    context.getResources()
                            .getDimensionPixelSize(R.dimen.channel_banner_input_logo_size)
            );
            mInfo = info;
        }

        @Nullable
        @Override
        public ScaledBitmapInfo doGetBitmapInBackground() {
            Drawable drawable = mInfo.loadIcon(mAppContext);
            if (!(drawable instanceof BitmapDrawable)) {
                return null;
            }
            Bitmap original = ((BitmapDrawable) drawable).getBitmap();
            if (original == null) {
                return null;
            }
            return BitmapUtils.createScaledBitmapInfo(getKey(), original, mMaxWidth, mMaxHeight);
        }

        /**
         * Returns key of TV input logo.
         */
        public static String getTvInputLogoKey(String inputId) {
            return inputId + "-logo";
        }
    }

    private static synchronized Handler getMainHandler() {
        if (sMainHandler == null) {
            sMainHandler = new Handler(Looper.getMainLooper());
        }
        return sMainHandler;
    }

    private ImageLoader() {
    }
}
