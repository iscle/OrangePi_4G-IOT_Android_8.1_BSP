/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui;

import static com.android.documentsui.base.Shared.VERBOSE;

import android.annotation.Nullable;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.android.documentsui.ProviderExecutor.Preemptable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 *  Loads a Thumbnails asynchronously then animates from the mime icon to the thumbnail
 */
public final class ThumbnailLoader extends AsyncTask<Uri, Void, Bitmap> implements Preemptable {

    private static final String TAG = ThumbnailLoader.class.getCanonicalName();

    /**
     * Two animations applied to image views. The first is used to switch mime icon and thumbnail.
     * The second is used when we need to update thumbnail.
     */
    public static final BiConsumer<View, View> ANIM_FADE_IN = (mime, thumb) -> {
        float alpha = mime.getAlpha();
        mime.animate().alpha(0f).start();
        thumb.setAlpha(0f);
        thumb.animate().alpha(alpha).start();
    };
    public static final BiConsumer<View, View> ANIM_NO_OP = (mime, thumb) -> {};

    private final ImageView mIconThumb;
    private final Point mThumbSize;
    private final Uri mUri;
    private final long mLastModified;
    private final Consumer<Bitmap> mCallback;
    private final boolean mAddToCache;
    private final CancellationSignal mSignal;

    /**
     * @param uri - to a thumbnail.
     * @param iconThumb - ImageView to display the thumbnail.
     * @param thumbSize - size of the thumbnail.
     * @param lastModified - used for updating thumbnail caches.
     * @param addToCache - flag that determines if the loader saves the thumbnail to the cache.
     */
    public ThumbnailLoader(Uri uri, ImageView iconThumb, Point thumbSize, long lastModified,
        Consumer<Bitmap> callback, boolean addToCache) {

        mUri = uri;
        mIconThumb = iconThumb;
        mThumbSize = thumbSize;
        mLastModified = lastModified;
        mCallback = callback;
        mAddToCache = addToCache;
        mSignal = new CancellationSignal();
        mIconThumb.setTag(this);

        if (VERBOSE) Log.v(TAG, "Starting icon loader task for " + mUri);
    }

    @Override
    public void preempt() {
        if (VERBOSE) Log.v(TAG, "Icon loader task for " + mUri + " was cancelled.");
        cancel(false);
        mSignal.cancel();
    }

    @Override
    protected Bitmap doInBackground(Uri... params) {
        if (isCancelled()) {
            return null;
        }

        final Context context = mIconThumb.getContext();
        final ContentResolver resolver = context.getContentResolver();

        ContentProviderClient client = null;
        Bitmap result = null;
        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(
                resolver, mUri.getAuthority());
            result = DocumentsContract.getDocumentThumbnail(client, mUri, mThumbSize, mSignal);
            if (result != null && mAddToCache) {
                final ThumbnailCache cache = DocumentsApplication.getThumbnailCache(context);
                cache.putThumbnail(mUri, mThumbSize, result, mLastModified);
            }
        } catch (Exception e) {
            if (!(e instanceof OperationCanceledException)) {
                Log.w(TAG, "Failed to load thumbnail for " + mUri + ": " + e);
            }
        } finally {
            ContentProviderClient.releaseQuietly(client);
        }
        return result;
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        if (VERBOSE) Log.v(TAG, "Loader task for " + mUri + " completed");

        if (mIconThumb.getTag() == this) {
            mIconThumb.setTag(null);
            mCallback.accept(result);
        }
    }
}