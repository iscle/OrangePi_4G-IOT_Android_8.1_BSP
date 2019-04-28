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

import android.app.AuthenticationRequiredException;
import android.app.PendingIntent;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.DocumentsContract;

import java.io.FileNotFoundException;

/**
 * Provides data view that exercises some of the more esoteric functionality...like display of INFO
 * and ERROR messages.
 * <p>
 * Do not use this provider for automated testing.
 */
public class DemoProvider extends TestRootProvider {

    private static final String ROOT_ID = "demo-root";
    private static final String ROOT_DOC_ID = "root0";

    public DemoProvider() {
        super("Demo Root", ROOT_ID, 0, ROOT_DOC_ID);
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
        Bundle extras = c.getExtras();

        switch (parentDocumentId) {
            case "show info":
                extras.putString(
                        DocumentsContract.EXTRA_INFO,
                        "All files in this root support settings from owner.");
                addFolder(c, "folder");
                addFile(c, "zzz");
                for (int i = 0; i < 100; i++) {
                    addFile(c, "" + i, DocumentsContract.Document.FLAG_SUPPORTS_SETTINGS);
                }
                break;

            case "show error":
                extras.putString(
                        DocumentsContract.EXTRA_ERROR,
                        "I'm a synthetic ERROR. Don't judge me.");
                break;

            case "show both error and info":
                extras.putString(
                        DocumentsContract.EXTRA_INFO,
                        "INFO: I'm confused. I've show both ERROR and INFO.");
                extras.putString(
                        DocumentsContract.EXTRA_ERROR,
                        "ERROR: I'm confused. I've show both ERROR and INFO.");
                break;

            case "throw a nice exception":
                throw new RuntimeException();

            case "throw a authentication exception":
                Intent intent = new Intent("com.android.documentsui.test.action.AUTHENTICATE");
                PendingIntent pIntent = PendingIntent.getActivity(getContext(),
                        AbstractActionHandler.CODE_AUTHENTICATION, intent, 0);
                throw new AuthenticationRequiredException(new UnsupportedOperationException(),
                        pIntent);

            default:
                addFolder(c, "show info");
                addFolder(c, "show error");
                addFolder(c, "show both error and info");
                addFolder(c, "throw a nice exception");
                addFolder(c, "throw a authentication exception");
                break;
        }

        return c;
    }
}

