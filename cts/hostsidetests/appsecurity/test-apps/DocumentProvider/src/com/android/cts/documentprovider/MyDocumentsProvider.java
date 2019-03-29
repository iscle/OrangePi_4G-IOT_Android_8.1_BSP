/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.documentprovider;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Path;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class MyDocumentsProvider extends DocumentsProvider {
    private static final String TAG = "TestDocumentsProvider";

    private static final String AUTHORITY = "com.android.cts.documentprovider";

    private static final int WEB_LINK_REQUEST_CODE = 321;

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID, Root.COLUMN_AVAILABLE_BYTES,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
    };

    private static String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    private boolean mEjected = false;

    @Override
    public boolean onCreate() {
        resetRoots();
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));

        RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, "local");
        row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY);
        row.add(Root.COLUMN_TITLE, "CtsLocal");
        row.add(Root.COLUMN_SUMMARY, "CtsLocalSummary");
        row.add(Root.COLUMN_DOCUMENT_ID, "doc:local");

        row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, "create");
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(Root.COLUMN_TITLE, "CtsCreate");
        row.add(Root.COLUMN_DOCUMENT_ID, "doc:create");

        if (!mEjected) {
            row = result.newRow();
            row.add(Root.COLUMN_ROOT_ID, "eject");
            row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_EJECT);
            row.add(Root.COLUMN_TITLE, "eject");
            // Reuse local docs, but not used for testing
            row.add(Root.COLUMN_DOCUMENT_ID, "doc:local");
        }

        return result;
    }

    private Map<String, Doc> mDocs = new HashMap<>();

    private Doc mLocalRoot;
    private Doc mCreateRoot;
    private final AtomicInteger mNextDocId = new AtomicInteger(0);

    private Doc buildDoc(String docId, String displayName, String mimeType,
            String[] streamTypes) {
        final Doc doc = new Doc();
        doc.docId = docId;
        doc.displayName = displayName;
        doc.mimeType = mimeType;
        doc.streamTypes = streamTypes;
        mDocs.put(doc.docId, doc);
        return doc;
    }

    public void resetRoots() {
        Log.d(TAG, "resetRoots()");

        mEjected = false;

        mDocs.clear();

        mLocalRoot = buildDoc("doc:local", null, Document.MIME_TYPE_DIR, null);

        mCreateRoot = buildDoc("doc:create", null, Document.MIME_TYPE_DIR, null);
        mCreateRoot.flags = Document.FLAG_DIR_SUPPORTS_CREATE;

        {
            Doc file1 = buildDoc("doc:file1", "FILE1", "mime1/file1", null);
            file1.contents = "fileone".getBytes();
            file1.flags = Document.FLAG_SUPPORTS_WRITE;
            mLocalRoot.children.add(file1);
            mCreateRoot.children.add(file1);
        }

        {
            Doc file2 = buildDoc("doc:file2", "FILE2", "mime2/file2", null);
            file2.contents = "filetwo".getBytes();
            file2.flags = Document.FLAG_SUPPORTS_WRITE;
            mLocalRoot.children.add(file2);
            mCreateRoot.children.add(file2);
        }

        {
            Doc virtualFile = buildDoc("doc:virtual-file", "VIRTUAL_FILE", "application/icecream",
                    new String[] { "text/plain" });
            virtualFile.flags = Document.FLAG_VIRTUAL_DOCUMENT;
            virtualFile.contents = "Converted contents.".getBytes();
            mLocalRoot.children.add(virtualFile);
            mCreateRoot.children.add(virtualFile);
        }

        {
            Doc webLinkableFile = buildDoc("doc:web-linkable-file", "WEB_LINKABLE_FILE",
                    "application/icecream", new String[] { "text/plain" });
            webLinkableFile.flags = Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_WEB_LINKABLE;
            webLinkableFile.contents = "Fake contents.".getBytes();
            mLocalRoot.children.add(webLinkableFile);
            mCreateRoot.children.add(webLinkableFile);
        }

        Doc dir1 = buildDoc("doc:dir1", "DIR1", Document.MIME_TYPE_DIR, null);
        mLocalRoot.children.add(dir1);

        {
            Doc file3 = buildDoc("doc:file3", "FILE3", "mime3/file3", null);
            file3.contents = "filethree".getBytes();
            file3.flags = Document.FLAG_SUPPORTS_WRITE;
            dir1.children.add(file3);
        }

        Doc dir2 = buildDoc("doc:dir2", "DIR2", Document.MIME_TYPE_DIR, null);
        mCreateRoot.children.add(dir2);

        {
            Doc file4 = buildDoc("doc:file4", "FILE4", "mime4/file4", null);
            file4.contents = "filefour".getBytes();
            file4.flags = Document.FLAG_SUPPORTS_WRITE |
                    Document.FLAG_SUPPORTS_COPY |
                    Document.FLAG_SUPPORTS_MOVE |
                    Document.FLAG_SUPPORTS_REMOVE;
            dir2.children.add(file4);

            Doc subDir2 = buildDoc("doc:sub_dir2", "SUB_DIR2", Document.MIME_TYPE_DIR, null);
            dir2.children.add(subDir2);
        }
    }

    private static class Doc {
        public String docId;
        public int flags;
        public String displayName;
        public long size;
        public String mimeType;
        public String[] streamTypes;
        public long lastModified;
        public byte[] contents;
        public List<Doc> children = new ArrayList<>();

        public void include(MatrixCursor result) {
            final RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, docId);
            row.add(Document.COLUMN_DISPLAY_NAME, displayName);
            row.add(Document.COLUMN_SIZE, size);
            row.add(Document.COLUMN_MIME_TYPE, mimeType);
            row.add(Document.COLUMN_FLAGS, flags);
            row.add(Document.COLUMN_LAST_MODIFIED, lastModified);
        }
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        for (Doc doc : mDocs.get(parentDocumentId).children) {
            if (doc.docId.equals(documentId)) {
                return true;
            }
            if (Document.MIME_TYPE_DIR.equals(doc.mimeType)) {
                if (isChildDocument(doc.docId, documentId)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        final String docId = "doc:" + mNextDocId.getAndIncrement();
        final Doc doc = buildDoc(docId, displayName, mimeType, null);
        doc.flags = Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_RENAME;
        mDocs.get(parentDocumentId).children.add(doc);
        return docId;
    }

    @Override
    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        mDocs.get(documentId).displayName = displayName;
        return null;
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        final Doc doc = mDocs.get(documentId);
        mDocs.remove(doc.docId);
        for (Doc parentDoc : mDocs.values()) {
            parentDoc.children.remove(doc);
        }
    }

    @Override
    public void removeDocument(String documentId, String parentDocumentId)
            throws FileNotFoundException {
        // There are no multi-parented documents in this provider, so it's safe to remove the
        // document from mDocs.
        final Doc doc = mDocs.get(documentId);
        mDocs.remove(doc.docId);
        mDocs.get(parentDocumentId).children.remove(doc);
    }

    @Override
    public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        final Doc doc = mDocs.get(sourceDocumentId);
        if (doc.children.size() > 0) {
            throw new UnsupportedOperationException("Recursive copy not supported for tests.");
        }

        final Doc docCopy = buildDoc(doc.docId + "_copy", doc.displayName + "_COPY", doc.mimeType,
                doc.streamTypes);
        mDocs.get(targetParentDocumentId).children.add(docCopy);
        return docCopy.docId;
    }

    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId,
            String targetParentDocumentId)
            throws FileNotFoundException {
        final Doc doc = mDocs.get(sourceDocumentId);
        mDocs.get(sourceParentDocumentId).children.remove(doc);
        mDocs.get(targetParentDocumentId).children.add(doc);
        return doc.docId;
    }

    @Override
    public Path findDocumentPath(String parentDocumentId, String documentId)
            throws FileNotFoundException {
        if (!mDocs.containsKey(documentId)) {
            throw new FileNotFoundException(documentId + " is not found.");
        }

        final Map<String, String> parentMap = new HashMap<>();
        for (Doc doc : mDocs.values()) {
            for (Doc childDoc : doc.children) {
                parentMap.put(childDoc.docId, doc.docId);
            }
        }

        String currentDocId = documentId;
        final LinkedList<String> path = new LinkedList<>();
        while (!currentDocId.equals(parentDocumentId)
                && !currentDocId.equals(mLocalRoot.docId)
                && !currentDocId.equals(mCreateRoot.docId)) {
            path.addFirst(currentDocId);
            currentDocId = parentMap.get(currentDocId);
        }

        if (parentDocumentId != null && !currentDocId.equals(parentDocumentId)) {
            throw new FileNotFoundException(documentId + " is not found under " + parentDocumentId);
        }

        // Add the root doc / parent doc
        path.addFirst(currentDocId);

        String rootId = null;
        if (parentDocumentId == null) {
            rootId = currentDocId.equals(mLocalRoot.docId) ? "local" : "create";
        }
        return new Path(rootId, path);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        mDocs.get(documentId).include(result);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
            String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        for (Doc doc : mDocs.get(parentDocumentId).children) {
            doc.include(result);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        final Doc doc = mDocs.get(documentId);
        if (doc == null) {
            throw new FileNotFoundException();
        }
        if ((doc.flags & Document.FLAG_VIRTUAL_DOCUMENT) != 0) {
            throw new IllegalArgumentException("Tried to open a virtual file.");
        }
        return openDocumentUnchecked(doc, mode, signal);
    }

    private ParcelFileDescriptor openDocumentUnchecked(final Doc doc, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        if (mode.contains("w")) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    synchronized (doc) {
                        try {
                            final InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(
                                    pipe[0]);
                            doc.contents = readFullyNoClose(is);
                            is.close();
                            doc.notifyAll();
                        } catch (IOException e) {
                            Log.w(TAG, "Failed to stream", e);
                        }
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            return pipe[1];
        } else {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    synchronized (doc) {
                        try {
                            final OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(
                                    pipe[1]);
                            while (doc.contents == null) {
                                doc.wait();
                            }
                            os.write(doc.contents);
                            os.close();
                        } catch (IOException e) {
                            Log.w(TAG, "Failed to stream", e);
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Interuppted", e);
                        }
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            return pipe[0];
        }
    }

    @Override
    public String[] getStreamTypes(Uri documentUri, String mimeTypeFilter) {
        // TODO: Add enforceTree(uri); b/27156282
        final String documentId = DocumentsContract.getDocumentId(documentUri);

        if (!"*/*".equals(mimeTypeFilter)) {
            throw new UnsupportedOperationException(
                    "Unsupported MIME type filter supported for tests.");
        }

        final Doc doc = mDocs.get(documentId);
        if (doc == null) {
            return null;
        }

        return doc.streamTypes;
    }

    @Override
    public AssetFileDescriptor openTypedDocument(
            String documentId, String mimeTypeFilter, Bundle opts, CancellationSignal signal)
            throws FileNotFoundException {
        final Doc doc = mDocs.get(documentId);
        if (doc == null) {
            throw new FileNotFoundException();
        }

        if (mimeTypeFilter.contains("*")) {
            throw new UnsupportedOperationException(
                    "MIME type filters with Wildcards not supported for tests.");
        }

        for (String streamType : doc.streamTypes) {
            if (streamType.equals(mimeTypeFilter)) {
                return new AssetFileDescriptor(openDocumentUnchecked(
                        doc, "r", signal), 0, doc.contents.length);
            }
        }

        throw new UnsupportedOperationException("Unsupported MIME type filter for tests.");
    }

    @Override
    public IntentSender createWebLinkIntent(String documentId, Bundle options)
            throws FileNotFoundException {
        final Doc doc = mDocs.get(documentId);
        if (doc == null) {
            throw new FileNotFoundException();
        }
        if ((doc.flags & Document.FLAG_WEB_LINKABLE) == 0) {
            throw new IllegalArgumentException("The file is not web linkable");
        }

        final Intent intent = new Intent(getContext(), WebLinkActivity.class);
        intent.putExtra(WebLinkActivity.EXTRA_DOCUMENT_ID, documentId);
        if (options != null) {
            intent.putExtras(options);
        }

        final PendingIntent pendingIntent = PendingIntent.getActivity(
                getContext(), WEB_LINK_REQUEST_CODE, intent,
                PendingIntent.FLAG_ONE_SHOT);
        return pendingIntent.getIntentSender();
    }

    @Override
    public void ejectRoot(String rootId) {
        if ("eject".equals(rootId)) {
            mEjected = true;
            getContext().getContentResolver()
                    .notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null);
        }

        throw new IllegalStateException("Root " + rootId + " doesn't support ejection.");
    }

    private static byte[] readFullyNoClose(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = in.read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
}
