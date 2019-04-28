/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.archives;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor.RowBuilder;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.documentsui.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.Lock;

/**
 * Provides basic implementation for creating, extracting and accessing
 * files within archives exposed by a document provider.
 *
 * <p>This class is thread safe. All methods can be called on any thread without
 * synchronization.
 */
public class ArchivesProvider extends DocumentsProvider {
    public static final String AUTHORITY = "com.android.documentsui.archives";

    private static final String[] DEFAULT_ROOTS_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS,
            Root.COLUMN_ICON };
    private static final String TAG = "ArchivesProvider";
    private static final String METHOD_ACQUIRE_ARCHIVE = "acquireArchive";
    private static final String METHOD_RELEASE_ARCHIVE = "releaseArchive";
    private static final String[] ZIP_MIME_TYPES = {
            "application/zip", "application/x-zip", "application/x-zip-compressed"
    };

    @GuardedBy("mArchives")
    private final Map<Key, Loader> mArchives = new HashMap<Key, Loader>();

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_ACQUIRE_ARCHIVE.equals(method)) {
            acquireArchive(arg);
            return null;
        }

        if (METHOD_RELEASE_ARCHIVE.equals(method)) {
            releaseArchive(arg);
            return null;
        }

        return super.call(method, arg, extras);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        // No roots provided.
        return new MatrixCursor(projection != null ? projection : DEFAULT_ROOTS_PROJECTION);
    }

    @Override
    public Cursor queryChildDocuments(String documentId, @Nullable String[] projection,
            @Nullable String sortOrder)
            throws FileNotFoundException {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        final Loader loader = getLoaderOrThrow(documentId);
        final int status = loader.getStatus();
        // If already loaded, then forward the request to the archive.
        if (status == Loader.STATUS_OPENED) {
            return loader.get().queryChildDocuments(documentId, projection, sortOrder);
        }

        final MatrixCursor cursor = new MatrixCursor(
                projection != null ? projection : Archive.DEFAULT_PROJECTION);
        final Bundle bundle = new Bundle();

        switch (status) {
            case Loader.STATUS_OPENING:
                bundle.putBoolean(DocumentsContract.EXTRA_LOADING, true);
                break;

            case Loader.STATUS_FAILED:
                // Return an empty cursor with EXTRA_LOADING, which shows spinner
                // in DocumentsUI. Once the archive is loaded, the notification will
                // be sent, and the directory reloaded.
                bundle.putString(DocumentsContract.EXTRA_ERROR,
                        getContext().getString(R.string.archive_loading_failed));
                break;
        }

        cursor.setExtras(bundle);
        cursor.setNotificationUri(getContext().getContentResolver(),
                buildUriForArchive(archiveId.mArchiveUri, archiveId.mAccessMode));
        return cursor;
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        if (archiveId.mPath.equals("/")) {
            return Document.MIME_TYPE_DIR;
        }

        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().getDocumentType(documentId);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().isChildDocument(parentDocumentId, documentId);
    }

    @Override
    public Cursor queryDocument(String documentId, @Nullable String[] projection)
            throws FileNotFoundException {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        if (archiveId.mPath.equals("/")) {
            try (final Cursor archiveCursor = getContext().getContentResolver().query(
                    archiveId.mArchiveUri,
                    new String[] { Document.COLUMN_DISPLAY_NAME },
                    null, null, null, null)) {
                if (archiveCursor == null || !archiveCursor.moveToFirst()) {
                    throw new FileNotFoundException(
                            "Cannot resolve display name of the archive.");
                }
                final String displayName = archiveCursor.getString(
                        archiveCursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME));

                final MatrixCursor cursor = new MatrixCursor(
                        projection != null ? projection : Archive.DEFAULT_PROJECTION);
                final RowBuilder row = cursor.newRow();
                row.add(Document.COLUMN_DOCUMENT_ID, documentId);
                row.add(Document.COLUMN_DISPLAY_NAME, displayName);
                row.add(Document.COLUMN_SIZE, 0);
                row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
                return cursor;
            }
        }

        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().queryDocument(documentId, projection);
    }

    @Override
    public String createDocument(
            String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        final Loader loader = getLoaderOrThrow(parentDocumentId);
        return loader.get().createDocument(parentDocumentId, mimeType, displayName);
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, final CancellationSignal signal)
            throws FileNotFoundException {
        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().openDocument(documentId, mode, signal);
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(
            String documentId, Point sizeHint, final CancellationSignal signal)
            throws FileNotFoundException {
        final Loader loader = getLoaderOrThrow(documentId);
        return loader.get().openDocumentThumbnail(documentId, sizeHint, signal);
    }

    /**
     * Returns true if the passed mime type is supported by the helper.
     */
    public static boolean isSupportedArchiveType(String mimeType) {
        for (final String zipMimeType : ZIP_MIME_TYPES) {
            if (zipMimeType.equals(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a Uri for accessing an archive with the specified access mode.
     *
     * @see ParcelFileDescriptor#MODE_READ
     * @see ParcelFileDescriptor#MODE_WRITE
     */
    public static Uri buildUriForArchive(Uri externalUri, int accessMode) {
        return DocumentsContract.buildDocumentUri(AUTHORITY,
                new ArchiveId(externalUri, accessMode, "/").toDocumentId());
    }

    /**
     * Acquires an archive.
     */
    public static void acquireArchive(ContentProviderClient client, Uri archiveUri) {
        Archive.MorePreconditions.checkArgumentEquals(AUTHORITY, archiveUri.getAuthority(),
                "Mismatching authority. Expected: %s, actual: %s.");
        final String documentId = DocumentsContract.getDocumentId(archiveUri);

        try {
            client.call(METHOD_ACQUIRE_ARCHIVE, documentId, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire archive.", e);
        }
    }

    /**
     * Releases an archive.
     */
    public static void releaseArchive(ContentProviderClient client, Uri archiveUri) {
        Archive.MorePreconditions.checkArgumentEquals(AUTHORITY, archiveUri.getAuthority(),
                "Mismatching authority. Expected: %s, actual: %s.");
        final String documentId = DocumentsContract.getDocumentId(archiveUri);

        try {
            client.call(METHOD_RELEASE_ARCHIVE, documentId, null);
        } catch (Exception e) {
            Log.w(TAG, "Failed to release archive.", e);
        }
    }

    /**
     * The archive won't close until all clients release it.
     */
    private void acquireArchive(String documentId) {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        synchronized (mArchives) {
            final Key key = Key.fromArchiveId(archiveId);
            Loader loader = mArchives.get(key);
            if (loader == null) {
                // TODO: Pass parent Uri so the loader can acquire the parent's notification Uri.
                loader = new Loader(getContext(), archiveId.mArchiveUri, archiveId.mAccessMode,
                        null);
                mArchives.put(key, loader);
            }
            loader.acquire();
            mArchives.put(key, loader);
        }
    }

    /**
     * If all clients release the archive, then it will be closed.
     */
    private void releaseArchive(String documentId) {
        final ArchiveId archiveId = ArchiveId.fromDocumentId(documentId);
        final Key key = Key.fromArchiveId(archiveId);
        synchronized (mArchives) {
            final Loader loader = mArchives.get(key);
            loader.release();
            final int status = loader.getStatus();
            if (status == Loader.STATUS_CLOSED || status == Loader.STATUS_CLOSING) {
                mArchives.remove(key);
            }
        }
    }

    private Loader getLoaderOrThrow(String documentId) {
        final ArchiveId id = ArchiveId.fromDocumentId(documentId);
        final Key key = Key.fromArchiveId(id);
        synchronized (mArchives) {
            final Loader loader = mArchives.get(key);
            if (loader == null) {
                throw new IllegalStateException("Archive not acquired.");
            }
            return loader;
        }
    }

    private static class Key {
        Uri archiveUri;
        int accessMode;

        public Key(Uri archiveUri, int accessMode) {
            this.archiveUri = archiveUri;
            this.accessMode = accessMode;
        }

        public static Key fromArchiveId(ArchiveId id) {
            return new Key(id.mArchiveUri, id.mAccessMode);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            return archiveUri.equals(((Key) other).archiveUri) &&
                accessMode == ((Key) other).accessMode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(archiveUri, accessMode);
        }
    }
}
