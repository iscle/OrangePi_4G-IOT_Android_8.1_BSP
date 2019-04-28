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

import android.content.Context;
import android.content.Intent.ShortcutIconResource;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Options for loading bitmap resources from different sources and for scaling to an appropriate
 * resolution.
 */
public class BitmapWorkerOptions {

    /** Max image size handled by android.graphics */
    static final int MAX_IMAGE_DIMENSION_PX = 2048;

    /** flag to force disable memory cache */
    public static final int CACHE_FLAG_MEM_DISABLED = 1;
    /** TODO support disk cache options */
    public static final int CACHE_FLAG_DISK_DISABLED = 2;

    private ShortcutIconResource mIconResource;
    private Uri mResourceUri;

    private int mWidth;
    private int mHeight;
    private Context mContext;
    private int mCacheFlag;
    private Bitmap.Config mBitmapConfig;

    private String mKey;

    /**
     * Builds options for a bitmap worker task.
     */
    public static class Builder {

        private String mPackageName;
        private String mResourceName;
        private Uri mResourceUri;

        private int mWidth;
        private int mHeight;
        private Context mContext;
        private int mCacheFlag;
        private Bitmap.Config mBitmapConfig;

        public Builder(Context context) {
            mWidth = MAX_IMAGE_DIMENSION_PX;
            mHeight = MAX_IMAGE_DIMENSION_PX;
            mContext = context;
            mCacheFlag = 0;
            mBitmapConfig = null;
        }

        public BitmapWorkerOptions build() {
            BitmapWorkerOptions options = new BitmapWorkerOptions();

            if (!TextUtils.isEmpty(mPackageName)) {
                options.mIconResource = new ShortcutIconResource();
                options.mIconResource.packageName = mPackageName;
                options.mIconResource.resourceName = mResourceName;
            }

            final int largestDim = Math.max(mWidth, mHeight);
            if (largestDim > MAX_IMAGE_DIMENSION_PX) {
                double scale = (double) MAX_IMAGE_DIMENSION_PX / largestDim;
                mWidth *= scale;
                mHeight *= scale;
            }

            options.mResourceUri = mResourceUri;
            options.mWidth = mWidth;
            options.mHeight = mHeight;
            options.mContext = mContext;
            options.mCacheFlag = mCacheFlag;
            options.mBitmapConfig = mBitmapConfig;
            if (options.mIconResource == null && options.mResourceUri == null) {
                throw new RuntimeException("Both Icon and ResourceUri are null");
            }
            return options;
        }

        public Builder resource(String packageName, String resourceName) {
            mPackageName = packageName;
            mResourceName = resourceName;
            return this;
        }

        public Builder resource(ShortcutIconResource iconResource) {
            mPackageName = iconResource.packageName;
            mResourceName = iconResource.resourceName;
            return this;
        }

        public Builder resource(Uri resourceUri) {
            mResourceUri = resourceUri;
            return this;
        }

        public Builder width(int width) {
            if (width > 0) {
                mWidth = width;
            } else {
                throw new IllegalArgumentException("Can't set width to " + width);
            }
            return this;
        }

        public Builder height(int height) {
            if (height > 0) {
                mHeight = height;
            } else {
                throw new IllegalArgumentException("Can't set height to " + height);
            }
            return this;
        }

        public Builder cacheFlag(int flag) {
            mCacheFlag = flag;
            return this;
        }

        public Builder bitmapConfig(Bitmap.Config config) {
            mBitmapConfig = config;
            return this;
        }

    }

    /**
     * Private constructor.
     * <p>
     * Use a {@link Builder} to create.
     */
    private BitmapWorkerOptions() {
    }

    public ShortcutIconResource getIconResource() {
        return mIconResource;
    }

    public Uri getResourceUri() {
        return mResourceUri;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public Context getContext() {
        return mContext;
    }

    public boolean isFromResource() {
        return getIconResource() != null ||
                UriUtils.isAndroidResourceUri(getResourceUri())
                || UriUtils.isShortcutIconResourceUri(getResourceUri());
    }

    /**
     * Combination of CACHE_FLAG_MEM_DISABLED and CACHE_FLAG_DISK_DISABLED,
     * 0 for fully cache enabled
     */
    public int getCacheFlag() {
        return mCacheFlag;
    }

    public boolean isMemCacheEnabled() {
        return (mCacheFlag & CACHE_FLAG_MEM_DISABLED) == 0;
    }

    public boolean isDiskCacheEnabled() {
        return (mCacheFlag & CACHE_FLAG_DISK_DISABLED) == 0;
    }

    /**
     * @return  preferred Bitmap config to decode bitmap, null for auto detect.
     * Use {@link Builder#bitmapConfig(Bitmap.Config)} to change it.
     */
    public Bitmap.Config getBitmapConfig() {
        return mBitmapConfig;
    }

    public String getCacheKey() {
        if (mKey == null) {
            mKey = mIconResource != null ? mIconResource.packageName + "/"
                    + mIconResource.resourceName : mResourceUri.toString();
        }
        return mKey;
    }

    @Override
    public String toString() {
        if (mIconResource == null) {
            return "URI: " + mResourceUri;
        } else {
            return "PackageName: " + mIconResource.packageName + " Resource: " + mIconResource
                    + " URI: " + mResourceUri;
        }
    }
}
