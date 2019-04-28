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
package com.android.documentsui;

import static android.provider.DocumentsContract.Document.FLAG_SUPPORTS_SETTINGS;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.provider.DocumentsContract.Document;
import java.io.FileNotFoundException;

/**
 * Content Provider for testing the Document Inspector.
 *
 *  Structure of the provider.
 *
 *         Top ------------> Middle  ------> Bottom -------> Dummy21 50B
 *         openInProvider    Dummy1 50B      Dummy11 50B     Dummy22 150B
 *         test.txt          Dummy2 150B     Dummy12 150B    Dummy23 100B
 *         update.txt        Dummy3 100B     Dummy13 100B
 */
public class InspectorProvider extends TestRootProvider {

    public static final String AUTHORITY = "com.android.documentsui.inspectorprovider";
    public static final String OPEN_IN_PROVIDER_TEST = "OpenInProviderTest";
    public static final String ROOT_ID = "inspector-root";

    private static final String ROOT_DOC_ID = "root0";
    private static final int ROOT_FLAGS = 0;

    public InspectorProvider() {
        super("Inspector Root", ROOT_ID, ROOT_FLAGS, ROOT_DOC_ID);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {

        if (OPEN_IN_PROVIDER_TEST.equals(documentId)) {
            MatrixCursor c = createDocCursor(projection);
            addFile(c, OPEN_IN_PROVIDER_TEST, FLAG_SUPPORTS_SETTINGS);
            return c;
        }

        MatrixCursor c = createDocCursor(projection);
        addFolder(c, documentId);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String s, String[] projection, String s1)
            throws FileNotFoundException {

        if("Top".equals(s)) {
            MatrixCursor c = createDocCursor(projection);
            addFolder(c, "Middle");
            addFileWithSize(c, "dummy1", 50);
            addFileWithSize(c, "dummy2", 150);
            addFileWithSize(c, "dummy3", 100);
            return c;
        }
        else if("Middle".equals(s)) {
            MatrixCursor c = createDocCursor(projection);
            addFolder(c, "Bottom");
            addFileWithSize(c, "dummy11", 50);
            addFileWithSize(c, "dummy12", 150);
            addFileWithSize(c, "dummy13", 100);
            return c;
        }
        else if("Bottom".equals(s)) {
            MatrixCursor c = createDocCursor(projection);
            addFileWithSize(c, "dummy21", 50);
            addFileWithSize(c, "dummy22", 150);
            addFileWithSize(c, "dummy23", 100);
            return c;
        }
        else {
            MatrixCursor c = createDocCursor(projection);
            addFolder(c, "Top");
            addFile(c, OPEN_IN_PROVIDER_TEST, FLAG_SUPPORTS_SETTINGS);
            addFile(c, "test.txt");
            addFile(c, "update.txt");
            return c;
        }
    }

    private void addFileWithSize(MatrixCursor c, String id, long size) {
        final RowBuilder row = c.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id);
        row.add(Document.COLUMN_DISPLAY_NAME, id);
        row.add(Document.COLUMN_SIZE, size);
        row.add(Document.COLUMN_MIME_TYPE, "text/plain");
        row.add(Document.COLUMN_FLAGS, 0);
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
    }



}
