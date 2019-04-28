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

package com.android.documentsui.testing;

import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;

import com.android.documentsui.base.DocumentInfo;

import java.io.FileNotFoundException;

/**
 * Test doubles of {@link DocumentsProvider} to isolate document providers. This is not registered
 * or exposed through AndroidManifest, but only used locally.
 */
public class TestDocumentsProvider extends DocumentsProvider {

    private String[] DOCUMENTS_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SUMMARY,
            Document.COLUMN_SIZE,
            Document.COLUMN_ICON
    };

    private Cursor mNextChildDocuments;
    private Cursor mNextRecentDocuments;

    public TestDocumentsProvider(String authority) {
        ProviderInfo info = new ProviderInfo();
        info.authority = authority;
        attachInfoForTesting(null, info);
    }

    @Override
    public boolean refresh(Uri url, Bundle args, CancellationSignal signal) {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        return null;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        return null;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
            String sortOrder) throws FileNotFoundException {
        return mNextChildDocuments;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        return null;
    }

    @Override
    public Cursor queryRecentDocuments(String rootId, String[] projection) {
        return mNextRecentDocuments;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Sets the next return value for {@link #queryChildDocuments(String, String[], String)}.
     * @param docs docs to return for next query.
     */
    public void setNextChildDocumentsReturns(DocumentInfo... docs) {
        mNextChildDocuments = createDocumentsCursor(docs);
    }

    public void setNextRecentDocumentsReturns(DocumentInfo... docs) {
        mNextRecentDocuments = createDocumentsCursor(docs);
    }

    private Cursor createDocumentsCursor(DocumentInfo... docs) {
        MatrixCursor cursor = new MatrixCursor(DOCUMENTS_PROJECTION);
        for (DocumentInfo doc : docs) {
            cursor.newRow()
                    .add(Document.COLUMN_DOCUMENT_ID, doc.documentId)
                    .add(Document.COLUMN_MIME_TYPE, doc.mimeType)
                    .add(Document.COLUMN_DISPLAY_NAME, doc.displayName)
                    .add(Document.COLUMN_LAST_MODIFIED, doc.lastModified)
                    .add(Document.COLUMN_FLAGS, doc.flags)
                    .add(Document.COLUMN_SUMMARY, doc.summary)
                    .add(Document.COLUMN_SIZE, doc.size)
                    .add(Document.COLUMN_ICON, doc.icon);
        }

        return cursor;
    }
}
