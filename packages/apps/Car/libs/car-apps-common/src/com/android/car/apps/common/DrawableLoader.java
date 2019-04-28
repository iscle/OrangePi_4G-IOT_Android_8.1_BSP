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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;

/**
 * AsyncTask which loads a bitmap.
 * <p>
 * The source of this can be another package (via a resource), a URI (content provider), or
 * a file path.
 * @see BitmapWorkerOptions
 * @hide
 */
class DrawableLoader extends AsyncTask<BitmapWorkerOptions, Void, Drawable> {

    private static final String TAG = "DrawableLoader";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private static final boolean DEBUG = false;

    private static final int SOCKET_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    private final WeakReference<ImageView> mImageView;
    private int mOriginalWidth;
    private int mOriginalHeight;
    private final RecycleBitmapPool mRecycledBitmaps;

    private final RefcountObject.RefcountListener mRefcountListener =
            new RefcountObject.RefcountListener() {
        @Override
        @SuppressWarnings("rawtypes")
        public void onRefcountZero(RefcountObject object) {
            mRecycledBitmaps.addRecycledBitmap((Bitmap) object.getObject());
        }
    };


    DrawableLoader(ImageView imageView, RecycleBitmapPool recycledBitmapPool) {
        mImageView = new WeakReference<ImageView>(imageView);
        mRecycledBitmaps = recycledBitmapPool;
    }

    public int getOriginalWidth() {
        return mOriginalWidth;
    }

    public int getOriginalHeight() {
        return mOriginalHeight;
    }

    @Override
    protected Drawable doInBackground(BitmapWorkerOptions... params) {

        return retrieveDrawable(params[0]);
    }

    protected Drawable retrieveDrawable(BitmapWorkerOptions workerOptions) {
        try {
            if (workerOptions.getIconResource() != null) {
                return getBitmapFromResource(workerOptions.getIconResource(), workerOptions);
            } else if (workerOptions.getResourceUri() != null) {
                if (UriUtils.isAndroidResourceUri(workerOptions.getResourceUri())
                        || UriUtils.isShortcutIconResourceUri(workerOptions.getResourceUri())) {
                    // Make an icon resource from this.
                    return getBitmapFromResource(
                            UriUtils.getIconResource(workerOptions.getResourceUri()),
                            workerOptions);
                } else if (UriUtils.isWebUri(workerOptions.getResourceUri())) {
                    return getBitmapFromHttp(workerOptions);
                } else if (UriUtils.isContentUri(workerOptions.getResourceUri())) {
                    return getBitmapFromContent(workerOptions);
                } else if (UriUtils.isAccountImageUri(workerOptions.getResourceUri())) {
                    return getAccountImage(workerOptions);
                } else {
                    Log.e(TAG, "Error loading bitmap - unknown resource URI! "
                            + workerOptions.getResourceUri());
                }
            } else {
                Log.e(TAG, "Error loading bitmap - no source!");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading url " + workerOptions.getResourceUri(), e);
            return null;
        } catch (RuntimeException e) {
            Log.e(TAG, "Critical Error loading url " + workerOptions.getResourceUri(), e);
            return null;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Drawable bitmap) {
        if (mImageView != null) {
            final ImageView imageView = mImageView.get();
            if (imageView != null) {
                imageView.setImageDrawable(bitmap);
            }
        }
    }

    @Override
    protected void onCancelled(Drawable result) {
        if (result instanceof RefcountBitmapDrawable) {
            // Remove the extra refcount created by us,  DrawableDownloader LruCache
            // still holds one to the bitmap
            RefcountBitmapDrawable d = (RefcountBitmapDrawable) result;
            d.getRefcountObject().releaseRef();
        }
    }

    private Drawable getBitmapFromResource(ShortcutIconResource iconResource,
            BitmapWorkerOptions outputOptions) throws IOException {
        if (DEBUG) {
            Log.d(TAG, "Loading " + iconResource.toString());
        }
        try {
            Object drawable = loadDrawable(outputOptions.getContext(), iconResource);
            if (drawable instanceof InputStream) {
                // Most of these are bitmaps, so resize properly.
                return decodeBitmap((InputStream)drawable, outputOptions);
            } else if (drawable instanceof Drawable){
                Drawable d = (Drawable) drawable;
                mOriginalWidth = d.getIntrinsicWidth();
                mOriginalHeight = d.getIntrinsicHeight();
                return d;
            } else {
                Log.w(TAG, "getBitmapFromResource failed, unrecognized resource: " + drawable);
                return null;
            }
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Could not load package: " + iconResource.packageName + "! NameNotFound");
            return null;
        } catch (NotFoundException e) {
            Log.w(TAG, "Could not load resource: " + iconResource.resourceName + "! NotFound");
            return null;
        }
    }

    private Drawable decodeBitmap(InputStream in, BitmapWorkerOptions options)
            throws IOException {
        CachedInputStream bufferedStream = null;
        BitmapFactory.Options bitmapOptions = null;
        try {
            bufferedStream = new CachedInputStream(in);
            // Let the bufferedStream be able to mark unlimited bytes up to full stream length.
            // The value that BitmapFactory uses (1024) is too small for detecting bounds
            bufferedStream.setOverrideMarkLimit(Integer.MAX_VALUE);
            bitmapOptions = new BitmapFactory.Options();
            bitmapOptions.inJustDecodeBounds = true;
            if (options.getBitmapConfig() != null) {
                bitmapOptions.inPreferredConfig = options.getBitmapConfig();
            }
            bitmapOptions.inTempStorage = ByteArrayPool.get16KBPool().allocateChunk();
            bufferedStream.mark(Integer.MAX_VALUE);
            BitmapFactory.decodeStream(bufferedStream, null, bitmapOptions);

            mOriginalWidth = bitmapOptions.outWidth;
            mOriginalHeight = bitmapOptions.outHeight;
            int heightScale = 1;
            int height = options.getHeight();
            if (height > 0) {
                heightScale = bitmapOptions.outHeight / height;
            }

            int widthScale = 1;
            int width = options.getWidth();
            if (width > 0) {
                widthScale = bitmapOptions.outWidth / width;
            }

            int scale = heightScale > widthScale ? heightScale : widthScale;
            if (scale <= 1) {
                scale = 1;
            } else {
                int shift = 0;
                do {
                    scale >>= 1;
                    shift++;
                } while (scale != 0);
                scale = 1 << (shift - 1);
            }

            if (DEBUG) {
                Log.d("BitmapWorkerTask", "Source bitmap: (" + bitmapOptions.outWidth + "x"
                        + bitmapOptions.outHeight + ").  Max size: (" + options.getWidth() + "x"
                        + options.getHeight() + ").  Chosen scale: " + scale + " -> " + scale);
            }

            // Reset buffer to original position and disable the overrideMarkLimit
            bufferedStream.reset();
            bufferedStream.setOverrideMarkLimit(0);
            Bitmap bitmap = null;
            try {
                bitmapOptions.inJustDecodeBounds = false;
                bitmapOptions.inSampleSize = scale;
                bitmapOptions.inMutable = true;
                bitmapOptions.inBitmap = mRecycledBitmaps.getRecycledBitmap(
                        mOriginalWidth / scale, mOriginalHeight / scale);
                bitmap = BitmapFactory.decodeStream(bufferedStream, null, bitmapOptions);
            } catch (RuntimeException ex) {
                Log.e(TAG, "RuntimeException" + ex + ", trying decodeStream again");
                bufferedStream.reset();
                bufferedStream.setOverrideMarkLimit(0);
                bitmapOptions.inBitmap = null;
                bitmap = BitmapFactory.decodeStream(bufferedStream, null, bitmapOptions);
            }
            if (bitmap == null) {
                Log.d(TAG, "bitmap was null");
                return null;
            }
            RefcountObject<Bitmap> object = new RefcountObject<Bitmap>(bitmap);
            object.addRef();
            object.setRefcountListener(mRefcountListener);
            RefcountBitmapDrawable d = new RefcountBitmapDrawable(
                    options.getContext().getResources(), object);
            return d;
        } finally {
            Log.w(TAG, "couldn't load bitmap, releasing resources");
            if (bitmapOptions != null) {
                ByteArrayPool.get16KBPool().releaseChunk(bitmapOptions.inTempStorage);
            }
            if (bufferedStream != null) {
                bufferedStream.close();
            }
        }
    }

    private Drawable getBitmapFromHttp(BitmapWorkerOptions options) throws IOException {
        URL url = new URL(options.getResourceUri().toString());
        if (DEBUG) {
            Log.d(TAG, "Loading " + url);
        }
        try {
            // TODO use volley for better disk cache
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(SOCKET_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            InputStream in = connection.getInputStream();
            return decodeBitmap(in, options);
        } catch (SocketTimeoutException e) {
            Log.e(TAG, "loading " + url + " timed out");
        }
        return null;
    }

    private Drawable getBitmapFromContent(BitmapWorkerOptions options)
            throws IOException {
        Uri resourceUri = options.getResourceUri();
        if (resourceUri != null) {
            try {
                InputStream bitmapStream =
                        options.getContext().getContentResolver().openInputStream(resourceUri);

                if (bitmapStream != null) {
                    return decodeBitmap(bitmapStream, options);
                } else {
                    Log.w(TAG, "Content provider returned a null InputStream when trying to " +
                            "open resource.");
                    return null;
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "FileNotFoundException during openInputStream for uri: "
                        + resourceUri.toString());
                return null;
            }
        } else {
            Log.w(TAG, "Get null resourceUri from BitmapWorkerOptions.");
            return null;
        }
    }

    /**
     * load drawable for non-bitmap resource or InputStream for bitmap resource without
     * caching Bitmap in Resources.  So that caller can maintain a different caching
     * storage with less memory used.
     * @return  either {@link Drawable} for xml and ColorDrawable <br>
     *          or {@link InputStream} for Bitmap resource
     */
    private static Object loadDrawable(Context context, ShortcutIconResource r)
            throws NameNotFoundException {
        Resources resources = context.getPackageManager()
                .getResourcesForApplication(r.packageName);
        if (resources == null) {
            return null;
        }
        resources.updateConfiguration(context.getResources().getConfiguration(),
                context.getResources().getDisplayMetrics());
        final int id = resources.getIdentifier(r.resourceName, null, null);
        if (id == 0) {
            Log.e(TAG, "Couldn't get resource " + r.resourceName + " in resources of "
                    + r.packageName);
            return null;
        }
        TypedValue value = new TypedValue();
        resources.getValue(id, value, true);
        if ((value.type == TypedValue.TYPE_STRING && value.string.toString().endsWith(".xml")) || (
                value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT)) {
            return resources.getDrawable(id);
        }
        return resources.openRawResource(id, value);
    }

    public static Drawable getDrawable(Context context, ShortcutIconResource iconResource)
            throws NameNotFoundException {
        Resources resources =
                context.getPackageManager().getResourcesForApplication(iconResource.packageName);
        resources.updateConfiguration(context.getResources().getConfiguration(),
                context.getResources().getDisplayMetrics());
        int id = resources.getIdentifier(iconResource.resourceName, null, null);
        if (id == 0) {
            throw new NameNotFoundException();
        }
        return resources.getDrawable(id);
    }

    @SuppressWarnings("deprecation")
    private Drawable getAccountImage(BitmapWorkerOptions options) {
        String accountName = UriUtils.getAccountName(options.getResourceUri());
        Context context = options.getContext();

        if (accountName != null && context != null) {
            Account thisAccount = null;
            for (Account account : AccountManager.get(context).
                    getAccountsByType(GOOGLE_ACCOUNT_TYPE)) {
                if (account.name.equals(accountName)) {
                    thisAccount = account;
                    break;
                }
            }
            if (thisAccount != null) {
                String picUriString = AccountImageHelper.getAccountPictureUri(context, thisAccount);
                if (picUriString != null) {
                    BitmapWorkerOptions.Builder optionBuilder =
                            new BitmapWorkerOptions.Builder(context)
                            .width(options.getWidth())
                                    .height(options.getHeight())
                                    .cacheFlag(options.getCacheFlag())
                                    .bitmapConfig(options.getBitmapConfig())
                                    .resource(Uri.parse(picUriString));
                    return DrawableDownloader.getInstance(context)
                            .loadBitmapBlocking(optionBuilder.build());
                }
                return null;
            }
        }
        return null;
    }
}
