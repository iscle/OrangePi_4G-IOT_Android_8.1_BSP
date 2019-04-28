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
package com.android.documentsui.testing;

import static junit.framework.Assert.assertEquals;

import android.net.Uri;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.util.Pair;

import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;

import java.util.List;

import javax.annotation.Nullable;

public class TestDocumentsAccess implements DocumentsAccess {

    public @Nullable DocumentInfo nextRootDocument;
    public @Nullable DocumentInfo nextDocument;
    public @Nullable List<DocumentInfo> nextDocuments;

    public boolean nextIsDocumentsUri;
    public @Nullable Path nextPath;

    public TestEventHandler<Uri> lastUri = new TestEventHandler<>();

    private Pair<DocumentInfo, DocumentInfo> mLastCreatedDoc;

    @Override
    public DocumentInfo getRootDocument(RootInfo root) {
        return nextRootDocument;
    }

    @Override
    public DocumentInfo getDocument(Uri uri) {
        return nextDocument;
    }

    @Override
    public List<DocumentInfo> getDocuments(String authority, List<String> docIds) {
        return nextDocuments;
    }

    @Override
    public Uri createDocument(DocumentInfo parentDoc, String mimeType, String displayName) {
        final DocumentInfo child = new DocumentInfo();
        child.authority = parentDoc.authority;
        child.mimeType = mimeType;
        child.displayName = displayName;
        child.documentId = displayName;
        child.derivedUri = DocumentsContract.buildDocumentUri(child.authority, displayName);

        mLastCreatedDoc = Pair.create(parentDoc, child);

        return child.derivedUri;
    }

    @Override
    public DocumentInfo getArchiveDocument(Uri uri) {
        return nextDocument;
    }

    @Override
    public boolean isDocumentUri(Uri uri) {
        return nextIsDocumentsUri;
    }

    @Override
    public Path findDocumentPath(Uri docUri) throws RemoteException {
        lastUri.accept(docUri);
        return nextPath;
    }

    public void assertCreatedDocument(DocumentInfo parent, String mimeType, String displayName) {
        assertEquals(parent, mLastCreatedDoc.first);
        assertEquals(mimeType, mLastCreatedDoc.second.mimeType);
        assertEquals(displayName, mLastCreatedDoc.second.displayName);
    }

    public @Nullable Uri getLastCreatedDocumentUri() {
        return mLastCreatedDoc.second.derivedUri;
    }
}
