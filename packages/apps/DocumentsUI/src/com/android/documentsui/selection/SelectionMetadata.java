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

package com.android.documentsui.selection;

import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import com.android.documentsui.MenuManager;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.roots.RootCursorWrapper;

import java.util.function.Function;

/**
 * A class that holds metadata
 */
public class SelectionMetadata
        implements MenuManager.SelectionDetails, SelectionManager.ItemCallback {

    private static final String TAG = "SelectionMetadata";
    private final static int FLAG_CAN_DELETE =
            Document.FLAG_SUPPORTS_REMOVE | Document.FLAG_SUPPORTS_DELETE;

    private final Function<String, Cursor> mDocFinder;

    private int mDirectoryCount = 0;
    private int mFileCount = 0;

    // Partial files are files that haven't been fully downloaded.
    private int mPartialCount = 0;
    private int mWritableDirectoryCount = 0;
    private int mNoDeleteCount = 0;
    private int mNoRenameCount = 0;
    private int mInArchiveCount = 0;
    private boolean mSupportsSettings = false;

    public SelectionMetadata(Function<String, Cursor> docFinder) {
        mDocFinder = docFinder;
    }

    @Override
    public void onItemStateChanged(String modelId, boolean selected) {
        final Cursor cursor = mDocFinder.apply(modelId);
        if (cursor == null) {
            Log.w(TAG, "Model returned null cursor for document: " + modelId
                    + ". Ignoring state changed event.");
            return;
        }

        final int delta = selected ? 1 : -1;

        final String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
        if (MimeTypes.isDirectoryType(mimeType)) {
            mDirectoryCount += delta;
        } else {
            mFileCount += delta;
        }

        final int docFlags = getCursorInt(cursor, Document.COLUMN_FLAGS);
        if ((docFlags & Document.FLAG_PARTIAL) != 0) {
            mPartialCount += delta;
        }
        if ((docFlags & Document.FLAG_DIR_SUPPORTS_CREATE) != 0) {
            mWritableDirectoryCount += delta;
        }
        if ((docFlags & FLAG_CAN_DELETE) == 0) {
            mNoDeleteCount += delta;
        }
        if ((docFlags & Document.FLAG_SUPPORTS_RENAME) == 0) {
            mNoRenameCount += delta;
        }
        if ((docFlags & Document.FLAG_PARTIAL) != 0) {
            mPartialCount += delta;
        }
        mSupportsSettings = (docFlags & Document.FLAG_SUPPORTS_SETTINGS) != 0 &&
                (mFileCount + mDirectoryCount) == 1;


        final String authority = getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY);
        if (ArchivesProvider.AUTHORITY.equals(authority)) {
            mInArchiveCount += delta;
        }
    }

    @Override
    public void onSelectionReset() {
        mFileCount = 0;
        mDirectoryCount = 0;
        mPartialCount = 0;
        mWritableDirectoryCount = 0;
        mNoDeleteCount = 0;
        mNoRenameCount = 0;
    }

    @Override
    public boolean containsDirectories() {
        return mDirectoryCount > 0;
    }

    @Override
    public boolean containsFiles() {
        return mFileCount > 0;
    }

    @Override
    public int size() {
        return mDirectoryCount + mFileCount;
    }

    @Override
    public boolean containsPartialFiles() {
        return mPartialCount > 0;
    }

    @Override
    public boolean containsFilesInArchive() {
        return mInArchiveCount > 0;
    }

    @Override
    public boolean canDelete() {
        return size() > 0 && mNoDeleteCount == 0;
    }

    @Override
    public boolean canExtract() {
        return size() > 0 && mInArchiveCount == size();
    }

    @Override
    public boolean canRename() {
        return mNoRenameCount == 0 && size() == 1;
    }

    @Override
    public boolean canViewInOwner() {
        return mSupportsSettings;
    }

    @Override
    public boolean canPasteInto() {
        return mDirectoryCount == 1 && mWritableDirectoryCount == 1 && size() == 1;
    }

    @Override
    public boolean canOpenWith() {
        return size() == 1 && mDirectoryCount == 0 && mInArchiveCount == 0 && mPartialCount == 0;
    }
}
