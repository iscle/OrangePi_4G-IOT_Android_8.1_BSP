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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.DEBUG;

import android.annotation.Nullable;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Log;

import com.android.documentsui.base.ApplicationScope;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.CheckedTask;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.State;

/**
 * A {@link CheckedTask} that calls
 * {@link ContentResolver#refresh(Uri, android.os.Bundle, android.os.CancellationSignal)} on the
 * current directory, and then calls the supplied callback with the refresh return value.
 */
public class RefreshTask extends TimeoutTask<Void, Boolean> {

    private final static String TAG = "RefreshTask";

    private final @ApplicationScope Context mContext;
    private final Features mFeatures;
    private final State mState;
    private final DocumentInfo mDoc;
    private final BooleanConsumer mCallback;
    private final CancellationSignal mSignal;


    public RefreshTask(Features features, State state, DocumentInfo doc, long timeout,
            @ApplicationScope Context context, Check check, BooleanConsumer callback) {
        super(check, timeout);
        mFeatures = features;
        mState = state;
        mDoc = doc;
        mContext = context;
        mCallback = callback;
        mSignal = new CancellationSignal();
    }

    @Override
    public @Nullable Boolean run(Void... params) {
        if (mDoc == null) {
            Log.w(TAG, "Ignoring attempt to refresh due to null DocumentInfo.");
            return false;
        }

        if (mState.stack.isEmpty()) {
            Log.w(TAG, "Ignoring attempt to refresh due to empty stack.");
            return false;
        }

        if (!mDoc.derivedUri.equals(mState.stack.peek().derivedUri)) {
            Log.w(TAG, "Ignoring attempt to refresh on a non-top-level uri.");
            return false;
        }

        // API O introduces ContentResolver#refresh, and if available and the ContentProvider
        // supports it, the ContentProvider will automatically send a content updated notification
        // and we will update accordingly. Else, we just tell the callback that Refresh is not
        // supported.
        if (!mFeatures.isContentRefreshEnabled()) {
            Log.w(TAG, "Ignoring attempt to call Refresh on an older Android platform.");
            return false;
        }

        final ContentResolver resolver = mContext.getContentResolver();
        final String authority = mDoc.authority;
        boolean refreshSupported = false;
        ContentProviderClient client = null;
        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority);
            refreshSupported = client.refresh(mDoc.derivedUri, null, mSignal);
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh", e);
        } finally {
            ContentProviderClient.releaseQuietly(client);
        }
        return refreshSupported;
    }

    @Override
    protected void onTimeout() {
        mSignal.cancel();
        Log.w(TAG, "Provider taking too long to respond. Cancelling.");
    }

    @Override
    public void finish(Boolean refreshSupported) {
        if (DEBUG) {
            // In case of timeout, refreshSupported is null.
            if (Boolean.TRUE.equals(refreshSupported)) {
                Log.v(TAG, "Provider supports refresh and has refreshed");
            } else {
                Log.v(TAG, "Provider does not support refresh and did not refresh");
            }
        }
        mCallback.accept(refreshSupported != null ? refreshSupported : Boolean.FALSE);
    }
}
