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

import android.annotation.Nullable;
import android.app.Activity;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.roots.ProvidersAccess;

import java.util.List;

/**
 * Loads {@link DocumentStack} for given document. It provides its best effort to find the path of
 * the given document.
 *
 * If it fails to load correct path it calls callback with different result
 * depending on the nullness of given root. If given root is null it calls callback with null. If
 * given root is not null it calls callback with a {@link DocumentStack} as if the given doc lives
 * under the root doc.
 */
public class LoadDocStackTask extends PairedTask<Activity, Uri, DocumentStack> {
    private static final String TAG = "LoadDocStackTask";

    private final ProvidersAccess mProviders;
    private final DocumentsAccess mDocs;
    private final LoadDocStackCallback mCallback;

    public LoadDocStackTask(
            Activity activity,
            ProvidersAccess providers,
            DocumentsAccess docs,
            LoadDocStackCallback callback) {
        super(activity);
        mProviders = providers;
        mDocs = docs;
        mCallback = callback;
    }

    @Override
    public @Nullable DocumentStack run(Uri... uris) {
        // assert(Features.OMC_RUNTIME);
        if (mDocs.isDocumentUri(uris[0])) {
            final Uri docUri;
            if (DocumentsContract.isTreeUri(uris[0])) {
                // Reconstruct tree URI into a plain document URI so that we can get the full path
                // to the root.
                final String docId = DocumentsContract.getDocumentId(uris[0]);
                docUri = DocumentsContract.buildDocumentUri(uris[0].getAuthority(), docId);
            } else {
                docUri = uris[0];
            }

            try {
                final Path path = mDocs.findDocumentPath(docUri);
                if (path != null) {
                    return buildStack(docUri.getAuthority(), path);
                } else {
                    Log.i(TAG, "Remote provider doesn't support findDocumentPath.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to build document stack for uri: " + docUri, e);
            }
        }

        return null;
    }

    @Override
    public void finish(@Nullable DocumentStack stack){
        mCallback.onDocumentStackLoaded(stack);
    }

    private DocumentStack buildStack(String authority, Path path) throws Exception {
        final String rootId = path.getRootId();
        if (rootId == null) {
            throw new IllegalStateException("Provider doesn't provider root id.");
        }

        RootInfo root = mProviders.getRootOneshot(authority, path.getRootId());
        if (root == null) {
            throw new IllegalStateException("Failed to load root for authority: " + authority +
                    " and root ID: " + path.getRootId() + ".");
        }

        List<DocumentInfo> docs = mDocs.getDocuments(authority, path.getPath());

        return new DocumentStack(root, docs);
    }

    @FunctionalInterface
    public interface LoadDocStackCallback {
        void onDocumentStackLoaded(@Nullable DocumentStack stack);
    }
}
