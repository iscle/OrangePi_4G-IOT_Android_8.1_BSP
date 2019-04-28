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

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;

/**
 * Simple test provider that provides a single root. Subclasess provider support
 * for returning documents.
 */
abstract class TestRootProvider extends DocumentsProvider {

    static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    };

    static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
    };

    private final String mRootId;
    private final String mRootName;
    private final int mFlags;
    private final String mRootDocId;

    public TestRootProvider(String rootName, String rootId, int flags, String rootDocId) {
        mRootName = rootName;
        mRootId = rootId;
        mFlags = flags;
        mRootDocId = rootDocId;
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        final RowBuilder row = c.newRow();
        row.add(Root.COLUMN_ROOT_ID, mRootId);
        row.add(Root.COLUMN_TITLE, mRootName);
        row.add(Root.COLUMN_FLAGS, mFlags);
        row.add(Root.COLUMN_DOCUMENT_ID, mRootDocId);
        row.add(Root.COLUMN_AVAILABLE_BYTES, 1024 * 1024 * 1000);
        return c;
    }

    protected void addFile(MatrixCursor c, String id) {
        addFile(c, id, 0);
    }

    protected void addFile(MatrixCursor c, String id, int flags) {
        final RowBuilder row = c.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id);
        row.add(Document.COLUMN_DISPLAY_NAME, id);
        row.add(Document.COLUMN_SIZE, 0);
        row.add(Document.COLUMN_MIME_TYPE, "text/plain");
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
    }

    protected void addFolder(MatrixCursor c, String id) {
        addFolder(c, id, 0);
    }

    protected void addFolder(MatrixCursor c, String id, int flags) {
        final RowBuilder row = c.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id);
        row.add(Document.COLUMN_DISPLAY_NAME, id);
        row.add(Document.COLUMN_SIZE, 0);
        row.add(Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
    }

    protected MatrixCursor createDocCursor(String[] projection) {
        MatrixCursor c = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        c.setExtras(new Bundle());
        return c;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        throw new UnsupportedOperationException("Nope!");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

}
