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

import android.graphics.Bitmap;
import android.util.Log;
import android.util.SparseArray;

import java.lang.ref.SoftReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;


/**
 * This class saves recycle bitmap as SoftReference,  which is too vulnerable to
 * be garbage collected due to other part of application is re-allocating lots of
 * memory,  we will lose all SoftReference in a GC run.  We should maintain
 * certain amount of recycled bitmaps in memory, we may also need remove bitmap from LRUCache
 * if we are not able to get a recycled bitmap here.
 *
 * @hide
 */
public class RecycleBitmapPool {

    private static final String TAG = "RecycleBitmapPool";
    private static final boolean DEBUG = false;
    // allow reuse bitmap with larger bytes, set to 0 to disable different byte size
    private static final int LARGER_BITMAP_ALLOWED_REUSE = 0;
    private static final boolean LARGER_BITMAP_ALLOWED = LARGER_BITMAP_ALLOWED_REUSE > 0;

    private static Method sGetAllocationByteCount;

    static {
        try {
            // KLP or later
            sGetAllocationByteCount = Bitmap.class.getMethod("getAllocationByteCount");
        } catch (NoSuchMethodException e) {
        }
    }

    private final SparseArray<ArrayList<SoftReference<Bitmap>>> mRecycled8888 =
            new SparseArray<ArrayList<SoftReference<Bitmap>>>();

    public RecycleBitmapPool() {
    }

    public static int getSize(Bitmap bitmap) {
        if (sGetAllocationByteCount != null) {
            try {
                return (Integer) sGetAllocationByteCount.invoke(bitmap);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "getAllocationByteCount() failed", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "getAllocationByteCount() failed", e);
            } catch (InvocationTargetException e) {
                Log.e(TAG, "getAllocationByteCount() failed", e);
            }
            sGetAllocationByteCount = null;
        }
        return bitmap.getByteCount();
    }

    private static int getSize(int width, int height) {
        if (width >= 2048 || height >= 2048) {
            return 0;
        }
        return width * height * 4;
    }

    public void addRecycledBitmap(Bitmap bitmap) {
        if (bitmap.isRecycled()) {
            return;
        }
        Bitmap.Config config = bitmap.getConfig();
        if (config != Bitmap.Config.ARGB_8888) {
            return;
        }
        int key = getSize(bitmap);
        if (key == 0) {
            return;
        }
        synchronized (mRecycled8888) {
            ArrayList<SoftReference<Bitmap>> list = mRecycled8888.get(key);
            if (list == null) {
                list = new ArrayList<SoftReference<Bitmap>>();
                mRecycled8888.put(key, list);
            }
            list.add(new SoftReference<Bitmap>(bitmap));
            if (DEBUG) {
                Log.d(TAG, list.size() + " add bitmap " + bitmap.getWidth() + " "
                        + bitmap.getHeight());
            }
        }
    }

    public Bitmap getRecycledBitmap(int width, int height) {
        int key = getSize(width, height);
        if (key == 0) {
            return null;
        }
        synchronized (mRecycled8888) {
            // for the new version with getAllocationByteCount(), we allow larger size
            // to be reused for the bitmap,  otherwise we just looks for same size
            Bitmap bitmap = getRecycledBitmap(mRecycled8888.get(key));
            if (sGetAllocationByteCount == null || bitmap != null) {
                return bitmap;
            }
            if (LARGER_BITMAP_ALLOWED) {
                for (int i = 0, c = mRecycled8888.size(); i < c; i++) {
                    int k = mRecycled8888.keyAt(i);
                    if (k > key && k <= key * LARGER_BITMAP_ALLOWED_REUSE) {
                        bitmap = getRecycledBitmap(mRecycled8888.valueAt(i));
                        if (bitmap != null) {
                            return bitmap;
                        }
                    }
                }
            }
        }
        if (DEBUG) {
            Log.d(TAG, "not avaialbe for " + width + "," + height);
        }
        return null;
    }

    private static Bitmap getRecycledBitmap(ArrayList<SoftReference<Bitmap>> list) {
        if (list != null && !list.isEmpty()) {
            while (!list.isEmpty()) {
                SoftReference<Bitmap> ref = list.remove(list.size() - 1);
                Bitmap bitmap = ref.get();
                if (bitmap != null && !bitmap.isRecycled()) {
                    if (DEBUG) {
                        Log.d(TAG, "reuse " + bitmap.getWidth() + " " + bitmap.getHeight());
                    }
                    return bitmap;
                } else {
                    if (DEBUG) {
                        Log.d(TAG, " we lost SoftReference to bitmap");
                    }
                }
            }
        }
        return null;
    }
}
