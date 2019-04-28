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

package com.android.documentsui.archives;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import android.support.annotation.VisibleForTesting;

import libcore.io.IoUtils;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Provides basic implementation for creating archives.
 *
 * <p>This class is thread safe.
 */
public class WriteableArchive extends Archive {
    private static final String TAG = "WriteableArchive";

    @GuardedBy("mEntries")
    private final Set<String> mPendingEntries = new HashSet<>();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    @GuardedBy("mEntries")
    private final ZipOutputStream mZipOutputStream;
    private final AutoCloseOutputStream mOutputStream;

    /**
     * Takes ownership of the passed file descriptor.
     */
    private WriteableArchive(
            Context context,
            ParcelFileDescriptor fd,
            Uri archiveUri,
            int accessMode,
            @Nullable Uri notificationUri)
            throws IOException {
        super(context, archiveUri, accessMode, notificationUri);
        if (!supportsAccessMode(accessMode)) {
            throw new IllegalStateException("Unsupported access mode.");
        }

        addEntry(null /* no parent */, new ZipEntry("/"));  // Root entry.
        mOutputStream = new AutoCloseOutputStream(fd);
        mZipOutputStream = new ZipOutputStream(mOutputStream);
    }

    private void addEntry(@Nullable ZipEntry parentEntry, ZipEntry entry) {
        final String entryPath = getEntryPath(entry);
        synchronized (mEntries) {
            if (entry.isDirectory()) {
                if (!mTree.containsKey(entryPath)) {
                    mTree.put(entryPath, new ArrayList<ZipEntry>());
                }
            }
            mEntries.put(entryPath, entry);
            if (parentEntry != null) {
                mTree.get(getEntryPath(parentEntry)).add(entry);
            }
        }
    }

    /**
     * @see ParcelFileDescriptor
     */
    public static boolean supportsAccessMode(int accessMode) {
        return accessMode == ParcelFileDescriptor.MODE_WRITE_ONLY;
    }

    /**
     * Creates a DocumentsArchive instance for writing into an archive file passed
     * as a file descriptor.
     *
     * This method takes ownership for the passed descriptor. The caller must
     * not use it after passing.
     *
     * @param context Context of the provider.
     * @param descriptor File descriptor for the archive's contents.
     * @param archiveUri Uri of the archive document.
     * @param accessMode Access mode for the archive {@see ParcelFileDescriptor}.
     * @param Uri notificationUri Uri for notifying that the archive file has changed.
     */
    @VisibleForTesting
    public static WriteableArchive createForParcelFileDescriptor(
            Context context, ParcelFileDescriptor descriptor, Uri archiveUri, int accessMode,
            @Nullable Uri notificationUri)
            throws IOException {
        try {
            return new WriteableArchive(context, descriptor, archiveUri, accessMode,
                    notificationUri);
        } catch (Exception e) {
            // Since the method takes ownership of the passed descriptor, close it
            // on exception.
            IoUtils.closeQuietly(descriptor);
            throw e;
        }
    }

    @Override
    @VisibleForTesting
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        final ArchiveId parsedParentId = ArchiveId.fromDocumentId(parentDocumentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedParentId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");

        final boolean isDirectory = Document.MIME_TYPE_DIR.equals(mimeType);
        ZipEntry entry;
        String entryPath;

        synchronized (mEntries) {
            final ZipEntry parentEntry = mEntries.get(parsedParentId.mPath);

            if (parentEntry == null) {
                throw new FileNotFoundException();
            }

            if (displayName.indexOf("/") != -1 || ".".equals(displayName) || "..".equals(displayName)) {
                throw new IllegalStateException("Display name contains invalid characters.");
            }

            if ("".equals(displayName)) {
                throw new IllegalStateException("Display name cannot be empty.");
            }


            assert(parentEntry.getName().endsWith("/"));
            final String parentName = "/".equals(parentEntry.getName()) ? "" : parentEntry.getName();
            final String entryName = parentName + displayName + (isDirectory ? "/" : "");
            entry = new ZipEntry(entryName);
            entryPath = getEntryPath(entry);
            entry.setSize(0);

            if (mEntries.get(entryPath) != null) {
                throw new IllegalStateException("The document already exist: " + entryPath);
            }
            addEntry(parentEntry, entry);
        }

        if (!isDirectory) {
            // For files, the contents will be written via openDocument. Since the contents
            // must be immediately followed by the contents, defer adding the header until
            // openDocument. All pending entires which haven't been written will be added
            // to the ZIP file in close().
            synchronized (mEntries) {
                mPendingEntries.add(entryPath);
            }
        } else {
            try {
                synchronized (mEntries) {
                    mZipOutputStream.putNextEntry(entry);
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to create a file in the archive: " + entryPath, e);
            }
        }

        return createArchiveId(entryPath).toDocumentId();
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, @Nullable final CancellationSignal signal)
            throws FileNotFoundException {
        MorePreconditions.checkArgumentEquals("w", mode,
                "Invalid mode. Only writing \"w\" supported, but got: \"%s\".");
        final ArchiveId parsedId = ArchiveId.fromDocumentId(documentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");

        final ZipEntry entry;
        synchronized (mEntries) {
            entry = mEntries.get(parsedId.mPath);
            if (entry == null) {
                throw new FileNotFoundException();
            }

            if (!mPendingEntries.contains(parsedId.mPath)) {
                throw new IllegalStateException("Files can be written only once.");
            }
            mPendingEntries.remove(parsedId.mPath);
        }

        ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createReliablePipe();
        } catch (IOException e) {
            // Ideally we'd simply throw IOException to the caller, but for consistency
            // with DocumentsProvider::openDocument, converting it to IllegalStateException.
            throw new IllegalStateException("Failed to open the document.", e);
        }
        final ParcelFileDescriptor inputPipe = pipe[0];

        try {
            mExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            try (final ParcelFileDescriptor.AutoCloseInputStream inputStream =
                                    new ParcelFileDescriptor.AutoCloseInputStream(inputPipe)) {
                                try {
                                    synchronized (mEntries) {
                                        mZipOutputStream.putNextEntry(entry);
                                        final byte buffer[] = new byte[32 * 1024];
                                        int bytes;
                                        long size = 0;
                                        while ((bytes = inputStream.read(buffer)) != -1) {
                                            if (signal != null) {
                                                signal.throwIfCanceled();
                                            }
                                            mZipOutputStream.write(buffer, 0, bytes);
                                            size += bytes;
                                        }
                                        entry.setSize(size);
                                        mZipOutputStream.closeEntry();
                                    }
                                } catch (IOException e) {
                                    // Catch the exception before the outer try-with-resource closes
                                    // the pipe with close() instead of closeWithError().
                                    try {
                                        Log.e(TAG, "Failed while writing to a file.", e);
                                        inputPipe.closeWithError("Writing failure.");
                                    } catch (IOException e2) {
                                        Log.e(TAG, "Failed to close the pipe after an error.", e2);
                                    }
                                }
                            } catch (OperationCanceledException e) {
                                // Cancelled gracefully.
                            } catch (IOException e) {
                                // Input stream auto-close error. Close quietly.
                            }
                        }
                    });
        } catch (RejectedExecutionException e) {
            IoUtils.closeQuietly(pipe[0]);
            IoUtils.closeQuietly(pipe[1]);
            throw new IllegalStateException("Failed to initialize pipe.");
        }

        return pipe[1];
    }

    /**
     * Closes the archive. Blocks until all enqueued pipes are completed.
     */
    @Override
    public void close() {
        // Waits until all enqueued pipe requests are completed.
        mExecutor.shutdown();
        try {
            final boolean result = mExecutor.awaitTermination(
                    Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            assert(result);
        } catch (InterruptedException e) {
            Log.e(TAG, "Opened files failed to be fullly written.", e);
        }

        // Flush all pending entries. They will all have empty size.
        synchronized (mEntries) {
            for (final String path : mPendingEntries) {
                try {
                    mZipOutputStream.putNextEntry(mEntries.get(path));
                    mZipOutputStream.closeEntry();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to flush empty entries.", e);
                }
            }

            try {
                mZipOutputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed while closing the ZIP file.", e);
            }
        }

        IoUtils.closeQuietly(mOutputStream);
    }
};
