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
import android.content.res.AssetFileDescriptor;
import android.graphics.Point;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.jar.StrictJarFile;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import libcore.io.IoUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;

/**
 * Provides basic implementation for extracting and accessing
 * files within archives exposed by a document provider.
 *
 * <p>This class is thread safe.
 */
public class ReadableArchive extends Archive {
    private static final String TAG = "ReadableArchive";

    private final StorageManager mStorageManager;
    private final StrictJarFile mZipFile;

    private ReadableArchive(
            Context context,
            @Nullable File file,
            @Nullable FileDescriptor fd,
            Uri archiveUri,
            int accessMode,
            @Nullable Uri notificationUri)
            throws IOException {
        super(context, archiveUri, accessMode, notificationUri);
        if (!supportsAccessMode(accessMode)) {
            throw new IllegalStateException("Unsupported access mode.");
        }

        mStorageManager = mContext.getSystemService(StorageManager.class);

        mZipFile = file != null ?
                new StrictJarFile(file.getPath(), false /* verify */,
                        false /* signatures */) :
                new StrictJarFile(fd, false /* verify */, false /* signatures */);

        ZipEntry entry;
        String entryPath;
        final Iterator<ZipEntry> it = mZipFile.iterator();
        final Stack<ZipEntry> stack = new Stack<>();
        while (it.hasNext()) {
            entry = it.next();
            if (entry.isDirectory() != entry.getName().endsWith("/")) {
                throw new IOException(
                        "Directories must have a trailing slash, and files must not.");
            }
            entryPath = getEntryPath(entry);
            if (mEntries.containsKey(entryPath)) {
                throw new IOException("Multiple entries with the same name are not supported.");
            }
            mEntries.put(entryPath, entry);
            if (entry.isDirectory()) {
                mTree.put(entryPath, new ArrayList<ZipEntry>());
            }
            if (!"/".equals(entryPath)) { // Skip root, as it doesn't have a parent.
                stack.push(entry);
            }
        }

        int delimiterIndex;
        String parentPath;
        ZipEntry parentEntry;
        List<ZipEntry> parentList;

        // Go through all directories recursively and build a tree structure.
        while (stack.size() > 0) {
            entry = stack.pop();

            entryPath = getEntryPath(entry);
            delimiterIndex = entryPath.lastIndexOf('/', entry.isDirectory()
                    ? entryPath.length() - 2 : entryPath.length() - 1);
            parentPath = entryPath.substring(0, delimiterIndex) + "/";

            parentList = mTree.get(parentPath);

            if (parentList == null) {
                // The ZIP file doesn't contain all directories leading to the entry.
                // It's rare, but can happen in a valid ZIP archive. In such case create a
                // fake ZipEntry and add it on top of the stack to process it next.
                parentEntry = new ZipEntry(parentPath);
                parentEntry.setSize(0);
                parentEntry.setTime(entry.getTime());
                mEntries.put(parentPath, parentEntry);

                if (!"/".equals(parentPath)) {
                    stack.push(parentEntry);
                }

                parentList = new ArrayList<>();
                mTree.put(parentPath, parentList);
            }

            parentList.add(entry);
        }
    }

    /**
     * @see ParcelFileDescriptor
     */
    public static boolean supportsAccessMode(int accessMode) {
        return accessMode == ParcelFileDescriptor.MODE_READ_ONLY;
    }

    /**
     * Creates a DocumentsArchive instance for opening, browsing and accessing
     * documents within the archive passed as a file descriptor.
     *
     * If the file descriptor is not seekable, then a snapshot will be created.
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
    public static ReadableArchive createForParcelFileDescriptor(
            Context context, ParcelFileDescriptor descriptor, Uri archiveUri, int accessMode,
            @Nullable Uri notificationUri)
            throws IOException {
        FileDescriptor fd = null;
        try {
            if (canSeek(descriptor)) {
                fd = new FileDescriptor();
                fd.setInt$(descriptor.detachFd());
                return new ReadableArchive(context, null, fd, archiveUri, accessMode,
                        notificationUri);
            }

            // Fallback for non-seekable file descriptors.
            File snapshotFile = null;
            try {
                // Create a copy of the archive, as ZipFile doesn't operate on streams.
                // Moreover, ZipInputStream would be inefficient for large files on
                // pipes.
                snapshotFile = File.createTempFile("com.android.documentsui.snapshot{",
                        "}.zip", context.getCacheDir());

                try (
                    final FileOutputStream outputStream =
                            new ParcelFileDescriptor.AutoCloseOutputStream(
                                    ParcelFileDescriptor.open(
                                            snapshotFile, ParcelFileDescriptor.MODE_WRITE_ONLY));
                    final ParcelFileDescriptor.AutoCloseInputStream inputStream =
                            new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                ) {
                    final byte[] buffer = new byte[32 * 1024];
                    int bytes;
                    while ((bytes = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytes);
                    }
                    outputStream.flush();
                }
                return new ReadableArchive(context, snapshotFile, null, archiveUri, accessMode,
                        notificationUri);
            } finally {
                // On UNIX the file will be still available for processes which opened it, even
                // after deleting it. Remove it ASAP, as it won't be used by anyone else.
                if (snapshotFile != null) {
                    snapshotFile.delete();
                }
            }
        } catch (Exception e) {
            // Since the method takes ownership of the passed descriptor, close it
            // on exception.
            IoUtils.closeQuietly(descriptor);
            IoUtils.closeQuietly(fd);
            throw e;
        }
    }

    @Override
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, @Nullable final CancellationSignal signal)
            throws FileNotFoundException {
        MorePreconditions.checkArgumentEquals("r", mode,
                "Invalid mode. Only reading \"r\" supported, but got: \"%s\".");
        final ArchiveId parsedId = ArchiveId.fromDocumentId(documentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");

        final ZipEntry entry = mEntries.get(parsedId.mPath);
        if (entry == null) {
            throw new FileNotFoundException();
        }

        try {
            return mStorageManager.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY, new Proxy(mZipFile, entry));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(
            String documentId, Point sizeHint, final CancellationSignal signal)
            throws FileNotFoundException {
        final ArchiveId parsedId = ArchiveId.fromDocumentId(documentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");
        Preconditions.checkArgument(getDocumentType(documentId).startsWith("image/"),
                "Thumbnails only supported for image/* MIME type.");

        final ZipEntry entry = mEntries.get(parsedId.mPath);
        if (entry == null) {
            throw new FileNotFoundException();
        }

        InputStream inputStream = null;
        try {
            inputStream = mZipFile.getInputStream(entry);
            final ExifInterface exif = new ExifInterface(inputStream);
            if (exif.hasThumbnail()) {
                Bundle extras = null;
                switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        extras = new Bundle(1);
                        extras.putInt(DocumentsContract.EXTRA_ORIENTATION, 90);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        extras = new Bundle(1);
                        extras.putInt(DocumentsContract.EXTRA_ORIENTATION, 180);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        extras = new Bundle(1);
                        extras.putInt(DocumentsContract.EXTRA_ORIENTATION, 270);
                        break;
                }
                final long[] range = exif.getThumbnailRange();
                return new AssetFileDescriptor(
                        openDocument(documentId, "r", signal), range[0], range[1], extras);
            }
        } catch (IOException e) {
            // Ignore the exception, as reading the EXIF may legally fail.
            Log.e(TAG, "Failed to obtain thumbnail from EXIF.", e);
        } finally {
            IoUtils.closeQuietly(inputStream);
        }

        return new AssetFileDescriptor(
                openDocument(documentId, "r", signal), 0, entry.getSize(), null);
    }

    /**
     * Closes an archive.
     *
     * <p>This method does not block until shutdown. Once called, other methods should not be
     * called. Any active pipes will be terminated.
     */
    @Override
    public void close() {
        try {
            mZipFile.close();
        } catch (IOException e) {
            // Silent close.
        }
    }
};
