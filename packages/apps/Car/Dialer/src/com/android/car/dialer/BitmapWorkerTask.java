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
package com.android.car.dialer;

import com.android.car.dialer.telecom.TelecomUtils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

/**
 * AsyncTask for handling getting contact photo from number.
 */
public class BitmapWorkerTask extends AsyncTask<Void, Void, Bitmap> {
    private final WeakReference<ImageView> imageViewReference;
    private final WeakReference<ContentResolver> contentResolverReference;
    private final String mNumber;
    private final BitmapRunnable mRunnable;

    public BitmapWorkerTask(
            ContentResolver contentResolver, ImageView imageView,
            String number, BitmapRunnable runnable) {
        imageViewReference = new WeakReference<>(imageView);
        contentResolverReference = new WeakReference<>(contentResolver);
        mNumber = number;
        mRunnable = runnable;
    }

    @Override
    protected Bitmap doInBackground(Void... voids) {
        final ContentResolver contentResolver = contentResolverReference.get();
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

        final ImageView imageView = imageViewReference.get();
        if (imageView != null) {
            final BitmapWorkerTask bitmapWorkerTask = (BitmapWorkerTask) imageView.getTag();
            if (this == bitmapWorkerTask) {
                mRunnable.setBitmap(bitmap);
                mRunnable.setImageView(imageView);
                mRunnable.setNumber(mNumber);
                mRunnable.run();
            }
        }
    }

    public static void loadBitmap(
            ContentResolver contentResolver, ImageView imageView,
            String number, BitmapRunnable runnable) {
        if (cancelPotentialWork(number, imageView)) {
            final BitmapWorkerTask task =
                    new BitmapWorkerTask(contentResolver, imageView, number, runnable);
            imageView.setTag(task);
            imageView.setImageResource(0);
            task.execute();
        }
    }

    private static boolean cancelPotentialWork(String number, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = (BitmapWorkerTask) imageView.getTag();
        if (bitmapWorkerTask != null) {
            if (bitmapWorkerTask.mNumber != number) {
                bitmapWorkerTask.cancel(true);
            } else {
                // The same work is already in progress
                return false;
            }
        }

        return true;
    }

    /**
     * Generate interface for handling logic after getting bitmap.
     */
    public static abstract class BitmapRunnable implements Runnable {
        protected String mNumber;
        protected Bitmap mBitmap;
        protected ImageView mImageView;

        public void setBitmap(Bitmap bitmap) {
            mBitmap = bitmap;
        }

        public void setImageView(ImageView imageView) {
            mImageView = imageView;
        }

        public void setNumber(String number) {
            mNumber = number;
        }
    }
}
