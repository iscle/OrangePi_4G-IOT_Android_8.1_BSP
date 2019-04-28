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

package com.android.documentsui.clipping;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.PersistableBundle;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * ClipboardManager wrapper class providing higher level logical
 * support for dealing with Documents.
 */
final class RuntimeDocumentClipper implements DocumentClipper {

    private static final String TAG = "DocumentClipper";
    private static final String SRC_PARENT_KEY = "clipper:srcParent";
    private static final String OP_TYPE_KEY = "clipper:opType";

    private final Context mContext;
    private final ClipStore mClipStore;
    private final ClipboardManager mClipboard;

    RuntimeDocumentClipper(Context context, ClipStore clipStore) {
        mContext = context;
        mClipStore = clipStore;
        mClipboard = context.getSystemService(ClipboardManager.class);
    }

    @Override
    public boolean hasItemsToPaste() {
        if (mClipboard.hasPrimaryClip()) {
            ClipData clipData = mClipboard.getPrimaryClip();

            int count = clipData.getItemCount();
            if (count > 0) {
                for (int i = 0; i < count; ++i) {
                    ClipData.Item item = clipData.getItemAt(i);
                    Uri uri = item.getUri();
                    if (isDocumentUri(uri)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isDocumentUri(@Nullable Uri uri) {
        return uri != null && DocumentsContract.isDocumentUri(mContext, uri);
    }

    @Override
    public ClipData getClipDataForDocuments(
        Function<String, Uri> uriBuilder, Selection selection, @OpType int opType) {

        assert(selection != null);

        if (selection.isEmpty()) {
            Log.w(TAG, "Attempting to clip empty selection. Ignoring.");
            return null;
        }

        final List<Uri> uris = new ArrayList<>(selection.size());
        for (String id : selection) {
            uris.add(uriBuilder.apply(id));
        }
        return getClipDataForDocuments(uris, opType);
    }

    @Override
    public ClipData getClipDataForDocuments(
            List<Uri> uris, @OpType int opType, DocumentInfo parent) {
        ClipData clipData = getClipDataForDocuments(uris, opType);
        clipData.getDescription().getExtras().putString(
                SRC_PARENT_KEY, parent.derivedUri.toString());
        return clipData;
    }

    @Override
    public ClipData getClipDataForDocuments(List<Uri> uris, @OpType int opType) {
        return (uris.size() > Shared.MAX_DOCS_IN_INTENT)
                ? createJumboClipData(uris, opType)
                : createStandardClipData(uris, opType);
    }

    /**
     * Returns ClipData representing the selection.
     */
    private ClipData createStandardClipData(List<Uri> uris, @OpType int opType) {

        assert(!uris.isEmpty());
        assert(uris.size() <= Shared.MAX_DOCS_IN_INTENT);

        final ContentResolver resolver = mContext.getContentResolver();
        final ArrayList<ClipData.Item> clipItems = new ArrayList<>();
        final Set<String> clipTypes = new HashSet<>();

        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(OP_TYPE_KEY, opType);

        for (Uri uri : uris) {
            DocumentInfo.addMimeTypes(resolver, uri, clipTypes);
            clipItems.add(new ClipData.Item(uri));
        }

        ClipDescription description = new ClipDescription(
                "", // Currently "label" is not displayed anywhere in the UI.
                clipTypes.toArray(new String[0]));
        description.setExtras(bundle);

        return createClipData(description, clipItems);
    }

    /**
     * Returns ClipData representing the list of docs
     */
    private ClipData createJumboClipData(List<Uri> uris, @OpType int opType) {

        assert(!uris.isEmpty());
        assert(uris.size() > Shared.MAX_DOCS_IN_INTENT);

        final int capacity = Math.min(uris.size(), Shared.MAX_DOCS_IN_INTENT);
        final ArrayList<ClipData.Item> clipItems = new ArrayList<>(capacity);

        // Set up mime types for the first Shared.MAX_DOCS_IN_INTENT
        final ContentResolver resolver = mContext.getContentResolver();
        final Set<String> clipTypes = new HashSet<>();
        int docCount = 0;
        for (Uri uri : uris) {
            if (docCount++ < Shared.MAX_DOCS_IN_INTENT) {
                DocumentInfo.addMimeTypes(resolver, uri, clipTypes);
                clipItems.add(new ClipData.Item(uri));
            }
        }

        // Prepare metadata
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(OP_TYPE_KEY, opType);
        bundle.putInt(OP_JUMBO_SELECTION_SIZE, uris.size());

        // Persists clip items and gets the slot they were saved under.
        int tag = mClipStore.persistUris(uris);
        bundle.putInt(OP_JUMBO_SELECTION_TAG, tag);

        ClipDescription description = new ClipDescription(
                "", // Currently "label" is not displayed anywhere in the UI.
                clipTypes.toArray(new String[0]));
        description.setExtras(bundle);

        return createClipData(description, clipItems);
    }

    @Override
    public void clipDocumentsForCopy(Function<String, Uri> uriBuilder, Selection selection) {
        ClipData data =
                getClipDataForDocuments(uriBuilder, selection, FileOperationService.OPERATION_COPY);
        assert(data != null);

        mClipboard.setPrimaryClip(data);
    }

    @Override
    public void clipDocumentsForCut(
            Function<String, Uri> uriBuilder, Selection selection, DocumentInfo parent) {
        assert(!selection.isEmpty());
        assert(parent.derivedUri != null);

        ClipData data = getClipDataForDocuments(uriBuilder, selection,
                FileOperationService.OPERATION_MOVE);
        assert(data != null);

        PersistableBundle bundle = data.getDescription().getExtras();
        bundle.putString(SRC_PARENT_KEY, parent.derivedUri.toString());

        mClipboard.setPrimaryClip(data);
    }


    @Override
    public void copyFromClipboard(
            DocumentInfo destination,
            DocumentStack docStack,
            FileOperations.Callback callback) {

        copyFromClipData(destination, docStack, mClipboard.getPrimaryClip(), callback);
    }

    @Override
    public void copyFromClipboard(
            DocumentStack docStack,
            FileOperations.Callback callback) {

        copyFromClipData(docStack, mClipboard.getPrimaryClip(), callback);
    }

    @Override
    public void copyFromClipData(
            DocumentInfo destination,
            DocumentStack docStack,
            @Nullable ClipData clipData,
            FileOperations.Callback callback) {

        DocumentStack dstStack = new DocumentStack(docStack, destination);
        copyFromClipData(dstStack, clipData, callback);
    }

    @Override
    public void copyFromClipData(
            DocumentStack dstStack,
            ClipData clipData,
            @OpType int opType,
            FileOperations.Callback callback) {

        clipData.getDescription().getExtras().putInt(OP_TYPE_KEY, opType);
        copyFromClipData(dstStack, clipData, callback);
    }

    @Override
    public void copyFromClipData(
            DocumentStack dstStack,
            @Nullable ClipData clipData,
            FileOperations.Callback callback) {

        if (clipData == null) {
            Log.i(TAG, "Received null clipData. Ignoring.");
            return;
        }

        PersistableBundle bundle = clipData.getDescription().getExtras();
        @OpType int opType = getOpType(bundle);
        try {
            if (!canCopy(dstStack.peek())) {
                callback.onOperationResult(
                        FileOperations.Callback.STATUS_REJECTED, getOpType(clipData), 0);
                return;
            }

            UrisSupplier uris = UrisSupplier.create(clipData, mClipStore);
            if (uris.getItemCount() == 0) {
                callback.onOperationResult(
                        FileOperations.Callback.STATUS_ACCEPTED, opType, 0);
                return;
            }

            String srcParentString = bundle.getString(SRC_PARENT_KEY);
            Uri srcParent = srcParentString == null ? null : Uri.parse(srcParentString);

            FileOperation operation = new FileOperation.Builder()
                    .withOpType(opType)
                    .withSrcParent(srcParent)
                    .withDestination(dstStack)
                    .withSrcs(uris)
                    .build();

            FileOperations.start(mContext, operation, callback, FileOperations.createJobId());
        } catch (IOException e) {
            Log.e(TAG, "Cannot create uris supplier.", e);
            callback.onOperationResult(FileOperations.Callback.STATUS_REJECTED, opType, 0);
            return;
        }
    }

    /**
     * Returns true if the list of files can be copied to destination. Note that this
     * is a policy check only. Currently the method does not attempt to verify
     * available space or any other environmental aspects possibly resulting in
     * failure to copy.
     *
     * @return true if the list of files can be copied to destination.
     */
    private static boolean canCopy(@Nullable DocumentInfo dest) {
        return dest != null && dest.isDirectory() && dest.isCreateSupported();
    }

    private @OpType int getOpType(ClipData data) {
        PersistableBundle bundle = data.getDescription().getExtras();
        return getOpType(bundle);
    }

    private @OpType int getOpType(PersistableBundle bundle) {
        return bundle.getInt(OP_TYPE_KEY);
    }

    private static ClipData createClipData(
            ClipDescription description, ArrayList<ClipData.Item> clipItems) {

        // technically we want to check >= O, but we'd need to patch back the O version code :|
        if (Features.OMC_RUNTIME) {
            return new ClipData(description, clipItems);
        }

        ClipData clip = new ClipData(description, clipItems.get(0));
        for (int i = 1; i < clipItems.size(); i++) {
            clip.addItem(clipItems.get(i));
        }
        return clip;
    }
}
