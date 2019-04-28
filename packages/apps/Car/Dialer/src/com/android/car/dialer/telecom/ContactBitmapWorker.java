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
package com.android.car.dialer.telecom;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * Helper task that retrieves a Contact photo from the local Contacts store. The loading task
 * is tied to an ImageView that allows a lightweight management of the task upon update of the view.
 *
 * TODO(mcrico): Using a View's TAG to store and manage Async task loading is pretty brittle.
 * Vanagon is not depending on this logic and projected shouldn't either.
 */
public class ContactBitmapWorker extends AsyncTask<Void, Void, Bitmap> {
    private final WeakReference<ImageView> mImageViewReference;
    private final WeakReference<ContentResolver> mContentResolverReference;
    private final String mNumber;
    private final BitmapWorkerListener mListener;

    /** Interface to receive updates from this worker */
    public interface BitmapWorkerListener {
        /** Called in the main thread upon bitmap load finish */
        @MainThread
        void onBitmapLoaded(@Nullable Bitmap bitmap);
    }

    /**
     * @return A worker task if a new one was needed to load the bitmap.
     */
    @Nullable public static ContactBitmapWorker loadBitmap(
            ContentResolver contentResolver,
            ImageView imageView,
            String number,
            BitmapWorkerListener listener) {

        // This work may be underway already.
        if (!cancelPotentialWork(number, imageView)) {
            return null;
        }

        ContactBitmapWorker task =
                new ContactBitmapWorker(contentResolver, imageView, number, listener);
        imageView.setTag(task);
        imageView.setImageResource(0);
        task.execute();
        return task;
    }

    /** Use {@link #loadBitmap} instead, as it guarantees de-duplication of work */
    private ContactBitmapWorker(
            ContentResolver contentResolver,
            ImageView imageView,
            String number,
            BitmapWorkerListener listener) {
        mImageViewReference = new WeakReference<>(imageView);
        mContentResolverReference = new WeakReference<>(contentResolver);
        mNumber = number;
        mListener = listener;
    }

    @Override
    protected Bitmap doInBackground(Void... voids) {
        final ContentResolver contentResolver = mContentResolverReference.get();
        if (contentResolver != null) {
            return TelecomUtils.getContactPhotoFromNumber(contentResolver, mNumber);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (isCancelled()) {
            return;
        }

        if (mImageViewReference.get() != null) {
            mListener.onBitmapLoaded(bitmap);
        }
    }

    /**
     * @return Whether a new Bitmap loading should continue for this imageView.
     */
    private static boolean cancelPotentialWork(String number, ImageView imageView) {
        final ContactBitmapWorker bitmapWorkerTask = (ContactBitmapWorker) imageView.getTag();
        if (bitmapWorkerTask != null) {
            if (bitmapWorkerTask.mNumber != number) {
                bitmapWorkerTask.cancel(true);
                imageView.setTag(null);
            } else {
                // The same work is already in progress
                return false;
            }
        }

        return true;
    }
}
