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

package com.android.documentsui.archives;

import com.android.documentsui.tests.R;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import libcore.io.IoUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ResourcesProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.android.documentsui.archives.resourcesprovider";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_TITLE, Root.COLUMN_DOCUMENT_ID
    };
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
    };

    private static final Map<String, Integer> RESOURCES = new HashMap<>();
    static {
        RESOURCES.put("archive.zip", R.raw.archive);
        RESOURCES.put("empty_dirs.zip", R.raw.empty_dirs);
        RESOURCES.put("no_dirs.zip", R.raw.no_dirs);
        RESOURCES.put("broken.zip", R.raw.broken);
    }

    private ExecutorService mExecutor = null;
    private TestUtils mTestUtils = null;

    @Override
    public boolean onCreate() {
        mExecutor = Executors.newSingleThreadExecutor();
        mTestUtils = new TestUtils(getContext(), getContext(), mExecutor);
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection
                : DEFAULT_ROOT_PROJECTION);
        final RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, "root-id");
        row.add(Root.COLUMN_FLAGS, 0);
        row.add(Root.COLUMN_TITLE, "ResourcesProvider");
        row.add(Root.COLUMN_DOCUMENT_ID, "root-document-id");
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection
                : DEFAULT_DOCUMENT_PROJECTION);
        if ("root-document-id".equals(documentId)) {
            final RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, "root-document-id");
            row.add(Document.COLUMN_FLAGS, 0);
            row.add(Document.COLUMN_DISPLAY_NAME, "ResourcesProvider");
            row.add(Document.COLUMN_SIZE, 0);
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
            return result;
        }

        includeDocument(result, documentId);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        if (!"root-document-id".equals(parentDocumentId)) {
            throw new FileNotFoundException();
        }

        final MatrixCursor result = new MatrixCursor(projection != null ? projection
                : DEFAULT_DOCUMENT_PROJECTION);
        for (String documentId : RESOURCES.keySet()) {
            includeDocument(result, documentId);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String docId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        final Integer resourceId = RESOURCES.get(docId);
        if (resourceId == null) {
            throw new FileNotFoundException();
        }
        return mTestUtils.getSeekableDescriptor(resourceId);
    }

    void includeDocument(MatrixCursor result, String documentId) throws FileNotFoundException {
        final Integer resourceId = RESOURCES.get(documentId);
        if (resourceId == null) {
            throw new FileNotFoundException();
        }

        AssetFileDescriptor fd = null;
        try {
            fd = getContext().getResources().openRawResourceFd(resourceId);
            final RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, documentId);
            row.add(Document.COLUMN_FLAGS, 0);
            row.add(Document.COLUMN_DISPLAY_NAME, documentId);

            final int lastDot = documentId.lastIndexOf('.');
            assert(lastDot > 0);
            final String extension = documentId.substring(lastDot + 1).toLowerCase();
            final String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

            row.add(Document.COLUMN_MIME_TYPE, mimeType);
            row.add(Document.COLUMN_SIZE, fd.getLength());
        }
        finally {
            IoUtils.closeQuietly(fd);
        }
    }
}
