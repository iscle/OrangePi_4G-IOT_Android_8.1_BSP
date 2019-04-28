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

import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import com.android.documentsui.DirectoryResult;
import com.android.documentsui.Model;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.roots.RootCursorWrapper;

import libcore.net.MimeUtils;

import java.util.Random;

public class TestModel extends Model {

    static final String[] COLUMNS = new String[]{
        RootCursorWrapper.COLUMN_AUTHORITY,
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_FLAGS,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_MIME_TYPE
    };

    private final String mAuthority;
    private int mLastId = 0;
    private Random mRand = new Random();
    private MatrixCursor mCursor;

    public TestModel(String authority, Features features) {
        super(features);
        mAuthority = authority;
        reset();
    }

    public void reset() {
        mLastId = 0;
        mCursor = new MatrixCursor(COLUMNS);
    }

    public void update() {
        DirectoryResult r = new DirectoryResult();
        r.cursor = mCursor;
        super.update(r);
    }

    public void setCursorExtras(Bundle bundle) {
        mCursor.setExtras(bundle);
    }

    public DocumentInfo createFile(String name) {
        return createFile(
                name,
                Document.FLAG_SUPPORTS_WRITE
                        | Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_RENAME);
    }

    public DocumentInfo createFile(String name, int flags) {
        return createDocument(
                name,
                guessMimeType(name),
                flags);
    }

    public DocumentInfo createFolder(String name) {
        return createFolder(
                name,
                Document.FLAG_SUPPORTS_WRITE
                        | Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_REMOVE
                        | Document.FLAG_DIR_SUPPORTS_CREATE);
    }

    public DocumentInfo createFolder(String name, int flags) {
        return createDocument(
                name,
                DocumentsContract.Document.MIME_TYPE_DIR,
                flags);
    }

    public DocumentInfo createDocument(String name, String mimeType, int flags) {
        DocumentInfo doc = new DocumentInfo();
        doc.authority = mAuthority;
        doc.documentId = Integer.toString(++mLastId);
        doc.derivedUri = DocumentsContract.buildDocumentUri(doc.authority, doc.documentId);
        doc.displayName = name;
        doc.mimeType = mimeType;
        doc.flags = flags;
        doc.size = mRand.nextInt();

        addToCursor(doc);

        return doc;
    }

    private void addToCursor(DocumentInfo doc) {
        MatrixCursor.RowBuilder row = mCursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, doc.documentId);
        row.add(RootCursorWrapper.COLUMN_AUTHORITY, doc.authority);
        row.add(Document.COLUMN_DISPLAY_NAME, doc.displayName);
        row.add(Document.COLUMN_MIME_TYPE, doc.mimeType);
        row.add(Document.COLUMN_FLAGS, doc.flags);
        row.add(Document.COLUMN_SIZE, doc.size);
    }

    private static String guessMimeType(String name) {
        int i = name.indexOf('.');

        while(i != -1) {
            name = name.substring(i + 1);
            String type = MimeUtils.guessMimeTypeFromExtension(name);
            if (type != null) {
                return type;
            }
            i = name.indexOf('.');
        }

        return "text/plain";
    }
}
