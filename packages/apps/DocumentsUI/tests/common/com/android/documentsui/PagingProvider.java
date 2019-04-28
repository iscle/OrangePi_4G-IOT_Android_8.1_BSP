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

import android.content.ContentResolver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.DocumentsContract.Root;

import java.io.FileNotFoundException;

/**
 * Test provider w/ support for paging, and various sub-scenarios of paging.
 */
public class PagingProvider extends TestRootProvider {

    /**
     * Pass test result size to inform the provider of the result size. Defaults to 1k.
     */
    private static final String TEST_RECORDSET_COUNT = "test-recordset-size";
    private static final int DEFAULT_RECORDSET_COUNT = 100;
    private static final int UNDETERMINED_RECORDSET_COUNT = -1;

    private static final String ROOT_ID = "paging-root";
    private static final String ROOT_DOC_ID = "root0";
    private static final int ROOT_FLAGS = Root.FLAG_SUPPORTS_SEARCH | Root.FLAG_SUPPORTS_IS_CHILD;

    public PagingProvider() {
        super("Paging Root", ROOT_ID, ROOT_FLAGS, ROOT_DOC_ID);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        MatrixCursor c = createDocCursor(projection);
        addFolder(c, documentId);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, Bundle queryArgs)
            throws FileNotFoundException {

        // TODO: Content notification.

        MatrixCursor c = createDocCursor(projection);
        Bundle extras = c.getExtras();

        int offset = queryArgs.getInt(ContentResolver.QUERY_ARG_OFFSET, 0);
        int limit = queryArgs.getInt(ContentResolver.QUERY_ARG_LIMIT, Integer.MIN_VALUE);
        int recordsetSize = queryArgs.getInt(TEST_RECORDSET_COUNT, DEFAULT_RECORDSET_COUNT);

        // Can be -1 (magic unknown), or 0 or more, but not less than -1.
        assert(recordsetSize > -2);

        // Client may force override the recordset size to -1 which is MAGIC unknown value.
        // Even if, we still need some finite number against to work.
        int size = (recordsetSize == UNDETERMINED_RECORDSET_COUNT)
                ? DEFAULT_RECORDSET_COUNT
                : recordsetSize;

        // Calculate the number of items to include in the cursor.
        int numItems = (limit >= 0)
                ? Math.min(limit, size - offset)
                : size - offset;

        assert(offset >= 0);
        assert(numItems >= 0);
        for (int i = 0; i < numItems; i++) {
            addFile(c, String.format("%05d", offset + i));
        }
        extras.putInt(ContentResolver.EXTRA_TOTAL_COUNT, recordsetSize);
        return c;
    }
}
