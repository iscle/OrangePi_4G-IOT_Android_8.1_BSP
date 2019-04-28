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

import android.database.Cursor;
import android.provider.DocumentsContract.Root;

import java.io.FileNotFoundException;

/**
 * Test provider that provides different kinds of broken behaviors DocumentsUI may encounter.
 */
public class BrokenProvider extends TestRootProvider {

    // Root information for a root that throws when querying its root document
    private static final String BROKEN_ROOT_DOCUMENT_ID = "BROKEN_ROOT_DOCUMENT";
    private static final String BROKEN_ROOT_DOCUMENT_TITLE = "Broken Root Doc";

    @Override
    public boolean onCreate() {
        return true;
    }

    public BrokenProvider() {
        super(
                BROKEN_ROOT_DOCUMENT_TITLE,
                BROKEN_ROOT_DOCUMENT_ID,
                Root.FLAG_SUPPORTS_CREATE,
                BROKEN_ROOT_DOCUMENT_ID);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        if (BROKEN_ROOT_DOCUMENT_ID.equals(documentId)) {
            throw new FileNotFoundException();
        }

        return null;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection,
            String sortOrder) throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }
}
