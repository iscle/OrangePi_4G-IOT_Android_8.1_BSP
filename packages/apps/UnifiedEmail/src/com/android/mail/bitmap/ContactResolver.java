/*
 * Copyright (C) 2013 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.bitmap;

import android.content.ContentResolver;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Handler;

import com.android.bitmap.BitmapCache;
import com.android.bitmap.DecodeTask;
import com.android.bitmap.RequestKey;
import com.android.bitmap.ReusableBitmap;
import com.android.ex.photo.util.Trace;
import com.android.mail.ContactInfo;
import com.android.mail.SenderInfoLoader;
import com.android.mail.bitmap.ContactRequest.ContactRequestHolder;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.google.common.collect.ImmutableMap;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Batches up ContactRequests so we can efficiently query the contacts provider. Kicks off a
 * ContactResolverTask to query for contact images in the background.
 */
public class ContactResolver implements Runnable {

    private static final String TAG = LogTag.getLogTag();

    // The maximum size returned from ContactsContract.Contacts.Photo.PHOTO is 96px by 96px.
    private static final int MAXIMUM_PHOTO_SIZE = 96;
    private static final int HALF_MAXIMUM_PHOTO_SIZE = 48;

    protected final ContentResolver mResolver;
    private final BitmapCache mCache;
    /** Insertion ordered set allows us to work from the top down. */
    private final LinkedHashSet<ContactRequestHolder> mBatch;

    private final Handler mHandler = new Handler();
    private ContactResolverTask mTask;


    /** Size 1 pool mostly to make systrace output traces on one line. */
    private static final Executor SMALL_POOL_EXECUTOR = new ThreadPoolExecutor(1, 1,
            1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static final Executor EXECUTOR = SMALL_POOL_EXECUTOR;

    public interface ContactDrawableInterface {
        public void onDecodeComplete(final RequestKey key, final ReusableBitmap result);
        public int getDecodeWidth();
        public int getDecodeHeight();
    }

    public ContactResolver(final ContentResolver resolver, final BitmapCache cache) {
        mResolver = resolver;
        mCache = cache;
        mBatch = new LinkedHashSet<ContactRequestHolder>();
    }

    @Override
    public void run() {
        // Start to process a new batch.
        if (mBatch.isEmpty()) {
            return;
        }

        if (mTask != null && mTask.getStatus() == Status.RUNNING) {
            LogUtils.d(TAG, "ContactResolver << batch skip");
            return;
        }

        Trace.beginSection("ContactResolver run");
        LogUtils.d(TAG, "ContactResolver >> batch start");

        // Make a copy of the batch.
        LinkedHashSet<ContactRequestHolder> batch = new LinkedHashSet<ContactRequestHolder>(mBatch);

        if (mTask != null) {
            mTask.cancel(true);
        }

        mTask = getContactResolverTask(batch);
        mTask.executeOnExecutor(EXECUTOR);
        Trace.endSection();
    }

    protected ContactResolverTask getContactResolverTask(
            LinkedHashSet<ContactRequestHolder> batch) {
        return new ContactResolverTask(batch, mResolver, mCache, this);
    }

    public BitmapCache getCache() {
        return mCache;
    }

    public void add(final ContactRequest request, final ContactDrawableInterface drawable) {
        mBatch.add(new ContactRequestHolder(request, drawable));
        notifyBatchReady();
    }

    public void remove(final ContactRequest request, final ContactDrawableInterface drawable) {
        mBatch.remove(new ContactRequestHolder(request, drawable));
    }

    /**
     * A layout pass traverses the whole tree during a single iteration of the event loop. That
     * means that every ContactDrawable on the screen will add its ContactRequest to the batch in
     * a single iteration of the event loop.
     *
     * <p/>
     * We take advantage of this by posting a Runnable (happens to be this object) at the end of
     * the event queue. Every time something is added to the batch as part of the same layout pass,
     * the Runnable is moved to the back of the queue. When the next layout pass occurs,
     * it is placed in the event loop behind this Runnable. That allows us to process the batch
     * that was added previously.
     */
    private void notifyBatchReady() {
        LogUtils.d(TAG, "ContactResolver  > batch   %d", mBatch.size());
        mHandler.removeCallbacks(this);
        mHandler.post(this);
    }

    /**
     * This is not a very traditional AsyncTask, in the sense that we do not care about what gets
     * returned in doInBackground(). Instead, we signal traditional "return values" through
     * publishProgress().
     *
     * <p/>
     * The reason we do this is because this task is responsible for decoding an entire batch of
     * ContactRequests. But, we do not want to have to wait to decode all of them before updating
     * any views. So we must do all the work in doInBackground(),
     * but upon finishing each individual task, we need to jump out to the UI thread and update
     * that view.
     */
    public static class ContactResolverTask extends AsyncTask<Void, Result, Void> {

        private final Set<ContactRequestHolder> mContactRequests;
        private final ContentResolver mResolver;
        private final BitmapCache mCache;
        private final ContactResolver mCallback;

        public ContactResolverTask(final Set<ContactRequestHolder> contactRequests,
                final ContentResolver resolver, final BitmapCache cache,
                final ContactResolver callback) {
            mContactRequests = contactRequests;
            mResolver = resolver;
            mCache = cache;
            mCallback = callback;
        }

        @Override
        protected Void doInBackground(final Void... params) {
            Trace.beginSection("set up");
            final Set<String> emails = new HashSet<String>(mContactRequests.size());
            for (ContactRequestHolder request : mContactRequests) {
                final String email = request.getEmail();
                emails.add(email);
            }
            Trace.endSection();

            Trace.beginSection("load contact photo bytes");
            // Query the contacts provider for the current batch of emails.
            final ImmutableMap<String, ContactInfo> contactInfos = loadContactPhotos(emails);
            Trace.endSection();

            for (ContactRequestHolder request : mContactRequests) {
                Trace.beginSection("decode");
                final String email = request.getEmail();
                if (contactInfos == null) {
                    // Query failed.
                    LogUtils.d(TAG, "ContactResolver -- failed  %s", email);
                    publishProgress(new Result(request, null));
                    Trace.endSection();
                    continue;
                }

                final ContactInfo contactInfo = contactInfos.get(email);
                if (contactInfo == null) {
                    // Request skipped. Try again next batch.
                    LogUtils.d(TAG, "ContactResolver  = skipped %s", email);
                    Trace.endSection();
                    continue;
                }

                // Query attempted.
                final byte[] photo = contactInfo.photoBytes;
                if (photo == null) {
                    // No photo bytes found.
                    LogUtils.d(TAG, "ContactResolver -- failed  %s", email);
                    publishProgress(new Result(request, null));
                    Trace.endSection();
                    continue;
                }

                // Query succeeded. Photo bytes found.
                request.contactRequest.bytes = photo;

                // Start decode.
                LogUtils.d(TAG, "ContactResolver ++ found   %s", email);
                // Synchronously decode the photo bytes. We are already in a background
                // thread, and we want decodes to finish in order. The decodes are blazing
                // fast so we don't need to kick off multiple threads.
                final int width = HALF_MAXIMUM_PHOTO_SIZE >= request.destination.getDecodeWidth()
                        ? HALF_MAXIMUM_PHOTO_SIZE : MAXIMUM_PHOTO_SIZE;
                final int height = HALF_MAXIMUM_PHOTO_SIZE >= request.destination.getDecodeHeight()
                        ? HALF_MAXIMUM_PHOTO_SIZE : MAXIMUM_PHOTO_SIZE;
                final DecodeTask.DecodeOptions opts = new DecodeTask.DecodeOptions(
                        width, height, 1 / 2f, DecodeTask.DecodeOptions.STRATEGY_ROUND_NEAREST);
                final ReusableBitmap result = new DecodeTask(request.contactRequest, opts, null,
                        null, mCache).decode();
                request.contactRequest.bytes = null;

                // Decode success.
                publishProgress(new Result(request, result));
                Trace.endSection();
            }

            return null;
        }

        protected ImmutableMap<String, ContactInfo> loadContactPhotos(Set<String> emails) {
            if (mResolver == null) {
                return null;
            }
            return SenderInfoLoader.loadContactPhotos(mResolver, emails, false /* decodeBitmaps */);
        }

        /**
         * We use progress updates to jump to the UI thread so we can decode the batch
         * incrementally.
         */
        @Override
        protected void onProgressUpdate(final Result... values) {
            final ContactRequestHolder request = values[0].request;
            final ReusableBitmap bitmap = values[0].bitmap;

            // DecodeTask does not add null results to the cache.
            if (bitmap == null && mCache != null) {
                // Cache null result.
                mCache.put(request.contactRequest, null);
            }

            request.destination.onDecodeComplete(request.contactRequest, bitmap);
        }

        @Override
        protected void onPostExecute(final Void aVoid) {
            // Batch completed. Start next batch.
            mCallback.notifyBatchReady();
        }
    }

    /**
     * Wrapper for the ContactRequest and its decoded bitmap. This class is used to pass results
     * to onProgressUpdate().
     */
    private static class Result {
        public final ContactRequestHolder request;
        public final ReusableBitmap bitmap;

        private Result(final ContactRequestHolder request, final ReusableBitmap bitmap) {
            this.request = request;
            this.bitmap = bitmap;
        }
    }
}
