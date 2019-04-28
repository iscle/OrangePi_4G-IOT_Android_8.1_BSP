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

package com.android.documentsui.picker;

import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.design.widget.Snackbar;
import android.util.Log;

import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.ui.Snackbars;

import java.util.function.Consumer;

/**
 * Task that creates a new document in the background.
 */
class CreatePickedDocumentTask extends PairedTask<Activity, Void, Uri> {
    private final LastAccessedStorage mLastAccessed;
    private final DocumentsAccess mDocs;
    private final DocumentStack mStack;
    private final String mMimeType;
    private final String mDisplayName;
    private final BooleanConsumer mInProgressStateListener;
    private final Consumer<Uri> mCallback;

    CreatePickedDocumentTask(
            Activity activity,
            DocumentsAccess docs,
            LastAccessedStorage lastAccessed,
            DocumentStack stack,
            String mimeType,
            String displayName,
            BooleanConsumer inProgressStateListener,
            Consumer<Uri> callback) {
        super(activity);
        mLastAccessed = lastAccessed;
        mDocs = docs;
        mStack = stack;
        mMimeType = mimeType;
        mDisplayName = displayName;
        mInProgressStateListener = inProgressStateListener;
        mCallback = callback;
    }

    @Override
    protected void prepare() {
        mInProgressStateListener.accept(true);
    }

    @Override
    protected Uri run(Void... params) {
        DocumentInfo cwd = mStack.peek();

        Uri childUri = mDocs.createDocument(cwd, mMimeType, mDisplayName);

        if (childUri != null) {
            mLastAccessed.setLastAccessed(mOwner, mStack);
        }

        return childUri;
    }

    @Override
    protected void finish(Uri result) {
        if (result != null) {
            mCallback.accept(result);
        } else {
            Snackbars.makeSnackbar(
                    mOwner, R.string.save_error, Snackbar.LENGTH_SHORT).show();
        }

        mInProgressStateListener.accept(false);
    }
}
