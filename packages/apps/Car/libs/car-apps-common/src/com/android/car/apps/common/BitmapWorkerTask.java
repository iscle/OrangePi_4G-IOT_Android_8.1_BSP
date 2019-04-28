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
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * AsyncTask which loads a bitmap.
 * <p>
 * The source of this can be another package (via a resource), a URI (content provider), or
 * a file path.
 *
 * @see BitmapWorkerOptions
 * @hide
 */
public class BitmapWorkerTask extends AsyncTask<BitmapWorkerOptions, Void, Bitmap> {

    private static final String TAG = "BitmapWorker";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private static final boolean DEBUG = false;

    private static final int SOCKET_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;

    private final WeakReference<ImageView> mImageView;
    // a flag for if the bitmap is scaled from original source
    protected boolean mScaled;

    public BitmapWorkerTask(ImageView imageView) {
        mImageView = new WeakReference<ImageView>(imageView);
        mScaled = false;
    }

    @Override
    protected Bitmap doInBackground(BitmapWorkerOptions... params) {

        return retrieveBitmap(params[0]);
    }

    protected Bitmap retrieveBitmap(BitmapWorkerOptions workerOptions) {
        try {
            if (workerOptions.getIconResource() != null) {
                return getBitmapFromResource(workerOptions.getContext(),
                        workerOptions.getIconResource(),
                        workerOptions);
            } else if (workerOptions.getResourceUri() != null) {
                if (UriUtils.isAndroidResourceUri(workerOptions.getResourceUri())
                        || UriUtils.isShortcutIconResourceUri(workerOptions.getResourceUri())) {
                    // Make an icon resource from this.
                    return getBitmapFromResource(workerOptions.getContext(),
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
    protected void onPostExecute(Bitmap bitmap) {
        if (mImageView != null) {
            final ImageView imageView = mImageView.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    private Bitmap getBitmapFromResource(Context context, ShortcutIconResource iconResource,
            BitmapWorkerOptions outputOptions) throws IOException {
        if (DEBUG) {
            Log.d(TAG, "Loading " + iconResource.toString());
        }
        try {
            Object drawable = loadDrawable(context, iconResource);
            if (drawable instanceof InputStream) {
                // Most of these are bitmaps, so resize properly.
                return decodeBitmap((InputStream) drawable, outputOptions);
            } else if (drawable instanceof Drawable){
                return createIconBitmap((Drawable) drawable, outputOptions);
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

    public final boolean isScaled() {
        return mScaled;
    }

    private Bitmap decodeBitmap(InputStream in, BitmapWorkerOptions options)
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

            float heightScale = (float) bitmapOptions.outHeight / options.getHeight();
            float widthScale = (float) bitmapOptions.outWidth / options.getWidth();
            // We take the smaller value because we will crop the result later.
            float scale = heightScale < widthScale ? heightScale : widthScale;

            bitmapOptions.inJustDecodeBounds = false;
            if (scale >= 4) {
                // Sampling looks really, really bad. So sample part way and then smooth
                // the result with bilinear interpolation.
                bitmapOptions.inSampleSize = (int) scale / 2;
            }
            // Reset buffer to original position and disable the overrideMarkLimit
            bufferedStream.reset();
            bufferedStream.setOverrideMarkLimit(0);

            Bitmap bitmap = BitmapFactory.decodeStream(bufferedStream, null, bitmapOptions);
            // We need to check isCancelled() and throw away the result if we are cancelled because
            // if we're loading over HTTP, canceling an AsyncTask causes the HTTP library to throw
            // an exception, which the bitmap library then eats and returns a partially decoded
            // bitmap. This behavior no longer will happen in lmp-mr1.
            if (bitmap == null || isCancelled()) {
                return null;
            }

            Bitmap result = bitmap;
            if (options.getWidth() < bitmap.getWidth()
                    || options.getHeight() < bitmap.getHeight()) {
                result = BitmapUtils.scaleBitmap(bitmap, options.getWidth(), options.getHeight());
                mScaled = true;
            }

            if (result != bitmap) {
                bitmap.recycle();
            }
            return result;

        } finally {
            if (bitmapOptions != null) {
                ByteArrayPool.get16KBPool().releaseChunk(bitmapOptions.inTempStorage);
            }
            if (bufferedStream != null) {
                bufferedStream.close();
            }
        }
    }

    private Bitmap getBitmapFromHttp(BitmapWorkerOptions options) throws IOException {
        URL url = new URL(options.getResourceUri().toString());
        if (DEBUG) {
            Log.d(TAG, "Loading " + url);
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(SOCKET_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            InputStream in = new BufferedInputStream(connection.getInputStream());
            return decodeBitmap(in, options);
        } finally {
            if (DEBUG) {
                Log.d(TAG, "loading done "+url);
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Bitmap getBitmapFromContent(BitmapWorkerOptions options) throws IOException {
        InputStream bitmapStream =
                options.getContext().getContentResolver().openInputStream(options.getResourceUri());
        if (bitmapStream != null) {
            return decodeBitmap(bitmapStream, options);
        } else {
            Log.w(TAG, "Content provider returned a null InputStream when trying to " +
                    "open resource.");
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

    private static Bitmap createIconBitmap(Drawable drawable, BitmapWorkerOptions workerOptions) {
        // Some drawables have an intrinsic width and height of -1. In that case
        // size it to our output.
        int width = drawable.getIntrinsicWidth();
        if (width == -1) {
            width = workerOptions.getWidth();
        }
        int height = drawable.getIntrinsicHeight();
        if (height == -1) {
            height = workerOptions.getHeight();
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static Drawable getDrawable(Context context, ShortcutIconResource iconResource)
            throws NameNotFoundException {
        Resources resources =
                context.getPackageManager().getResourcesForApplication(iconResource.packageName);
        if (resources == null) {
            return null;
        }
        resources.updateConfiguration(context.getResources().getConfiguration(),
                context.getResources().getDisplayMetrics());
        int id = resources.getIdentifier(iconResource.resourceName, null, null);
        if (id == 0) {
            throw new NameNotFoundException();
        }

        return resources.getDrawable(id);
    }

    @SuppressWarnings("deprecation")
    private Bitmap getAccountImage(BitmapWorkerOptions options) {
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
                    return BitmapDownloader.getInstance(context)
                            .loadBitmapBlocking(optionBuilder.build());
                }
                return null;
            }
        }
        return null;
    }
}
