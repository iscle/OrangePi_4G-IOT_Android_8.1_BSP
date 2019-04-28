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
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.DocumentsContract;

import java.io.FileNotFoundException;

/**
 * Provides data view that has files with FLAG_SUPPORTS_SETTINGS but nothing set to receive
 * the ACTION_DOCUMENT_SETTINGS intent.
 * <p>
 * Do not use this provider for automated testing.
 */
public class BrokenSettingsEnabledProvider extends TestRootProvider {

    private static final String ROOT_ID = "broken-settings-enabled-root";
    private static final String ROOT_DOC_ID = "root0";

    public BrokenSettingsEnabledProvider() {
        super("Broken Settings Enabled Root", ROOT_ID, 0, ROOT_DOC_ID);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        MatrixCursor c = createDocCursor(projection);
        Bundle extras = c.getExtras();
        extras.putString(
                DocumentsContract.EXTRA_INFO,
                "This provider is for feature demos only. Do not use from automated tests.");
        addFolder(c, documentId);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor c = createDocCursor(projection);
        addFile(c, "fred-dog.jpg", DocumentsContract.Document.FLAG_SUPPORTS_SETTINGS);
        return c;
    }
}
