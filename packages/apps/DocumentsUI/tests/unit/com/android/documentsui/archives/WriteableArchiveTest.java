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

import com.android.documentsui.archives.WriteableArchive;
import com.android.documentsui.tests.R;

import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.support.test.InstrumentationRegistry;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@MediumTest
public class WriteableArchiveTest extends AndroidTestCase {
    private static final Uri ARCHIVE_URI = Uri.parse("content://i/love/strawberries");
    private static final String NOTIFICATION_URI =
            "content://com.android.documentsui.archives/notification-uri";
    private ExecutorService mExecutor = null;
    private Archive mArchive = null;
    private TestUtils mTestUtils = null;
    private File mFile = null;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mExecutor = Executors.newSingleThreadExecutor();
        mTestUtils = new TestUtils(InstrumentationRegistry.getTargetContext(),
                InstrumentationRegistry.getContext(), mExecutor);
        mFile = mTestUtils.createTemporaryFile();

        mArchive = WriteableArchive.createForParcelFileDescriptor(
                InstrumentationRegistry.getTargetContext(),
                ParcelFileDescriptor.open(mFile, ParcelFileDescriptor.MODE_WRITE_ONLY),
                ARCHIVE_URI,
                ParcelFileDescriptor.MODE_WRITE_ONLY,
                Uri.parse(NOTIFICATION_URI));
    }

    @Override
    public void tearDown() throws Exception {
        mExecutor.shutdown();
        assertTrue(mExecutor.awaitTermination(3 /* timeout */, TimeUnit.SECONDS));
        if (mFile != null) {
            mFile.delete();
        }
        if (mArchive != null) {
            mArchive.close();
        }
        super.tearDown();
    }

    public static ArchiveId createArchiveId(String path) {
        return new ArchiveId(ARCHIVE_URI, ParcelFileDescriptor.MODE_WRITE_ONLY, path);
    }

    public void testCreateDocument() throws IOException {
        final String dirDocumentId = mArchive.createDocument(createArchiveId("/").toDocumentId(),
                Document.MIME_TYPE_DIR, "dir");
        assertEquals(createArchiveId("/dir/").toDocumentId(), dirDocumentId);

        final String documentId = mArchive.createDocument(dirDocumentId, "image/jpeg", "test.jpeg");
        assertEquals(createArchiveId("/dir/test.jpeg").toDocumentId(), documentId);

        try {
            mArchive.createDocument(dirDocumentId,
                    "image/jpeg", "test.jpeg");
            fail("Creating should fail, as the document already exists.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        try {
            mArchive.createDocument(createArchiveId("/").toDocumentId(),
                    "image/jpeg", "test.jpeg/");
            fail("Creating should fail, as the document name is invalid.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        try {
            mArchive.createDocument(createArchiveId("/").toDocumentId(),
                    Document.MIME_TYPE_DIR, "test/");
            fail("Creating should fail, as the document name is invalid.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        try {
            mArchive.createDocument(createArchiveId("/").toDocumentId(),
                    Document.MIME_TYPE_DIR, "..");
            fail("Creating should fail, as the document name is invalid.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        try {
            mArchive.createDocument(createArchiveId("/").toDocumentId(),
                    Document.MIME_TYPE_DIR, ".");
            fail("Creating should fail, as the document name is invalid.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        try {
            mArchive.createDocument(createArchiveId("/").toDocumentId(),
                    Document.MIME_TYPE_DIR, "");
            fail("Creating should fail, as the document name is invalid.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        try {
            mArchive.createDocument(createArchiveId("/").toDocumentId(),
                    "image/jpeg", "a/b.jpeg");
            fail("Creating should fail, as the document name is invalid.");
        } catch (IllegalStateException e) {
            // Expected.
        }
    }

    public void testAddDirectory() throws IOException {
        final String documentId = mArchive.createDocument(createArchiveId("/").toDocumentId(),
                Document.MIME_TYPE_DIR, "dir");

        {
            final Cursor cursor = mArchive.queryDocument(documentId, null);
            assertTrue(cursor.moveToFirst());
            assertEquals(documentId,
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
            assertEquals("dir",
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
            assertEquals(Document.MIME_TYPE_DIR,
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
            assertEquals(0,
                    cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        }

        {
            final Cursor cursor = mArchive.queryChildDocuments(
                    createArchiveId("/").toDocumentId(), null, null);

            assertTrue(cursor.moveToFirst());
            assertEquals(documentId,
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
            assertEquals("dir",
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
            assertEquals(Document.MIME_TYPE_DIR,
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
            assertEquals(0,
                    cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        }

        mArchive.close();

        // Verify archive.
        ZipFile zip = null;
        try {
            zip = new ZipFile(mFile);
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            assertTrue(entries.hasMoreElements());
            final ZipEntry entry = entries.nextElement();
            assertEquals("dir/", entry.getName());
            assertFalse(entries.hasMoreElements());
        } finally {
            if (zip != null) {
                zip.close();
            }
        }
    }

    public void testAddFile() throws IOException, InterruptedException {
        final String documentId = mArchive.createDocument(createArchiveId("/").toDocumentId(),
                "text/plain", "hoge.txt");

        {
            final Cursor cursor = mArchive.queryDocument(documentId, null);
            assertTrue(cursor.moveToFirst());
            assertEquals(documentId,
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
            assertEquals("hoge.txt",
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
            assertEquals("text/plain",
                    cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
            assertEquals(0,
                    cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        }

        try {
            mArchive.openDocument(documentId, "r", null);
            fail("Should fail when opened for reading!");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        final ParcelFileDescriptor fd = mArchive.openDocument(documentId, "w", null);
        try (final ParcelFileDescriptor.AutoCloseOutputStream outputStream =
                new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
            outputStream.write("Hello world!".getBytes());
        }

        try {
            mArchive.openDocument(documentId, "w", null);
            fail("Should fail when opened for the second time!");
        } catch (IllegalStateException e) {
            // Expected.
        }

        // Wait until the pipe thread fully writes all the data from the pipe.
        // TODO: Maybe add some method in WriteableArchive to wait until the executor
        // completes the job?
        Thread.sleep(500);

        {
            final Cursor cursor = mArchive.queryDocument(documentId, null);
            assertTrue(cursor.moveToFirst());
            assertEquals(12,
                    cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        }

        mArchive.close();

        // Verify archive.
        ZipFile zip = null;
        try {
            try {
            zip = new ZipFile(mFile);
            } catch (Exception e) {
                throw new IOException(mFile.getAbsolutePath());
            }
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            assertTrue(entries.hasMoreElements());
            final ZipEntry entry = entries.nextElement();
            assertEquals("hoge.txt", entry.getName());
            assertFalse(entries.hasMoreElements());
            final InputStream inputStream = zip.getInputStream(entry);
            final Scanner scanner = new Scanner(inputStream);
            assertEquals("Hello world!", scanner.nextLine());
            assertFalse(scanner.hasNext());
        } finally {
            if (zip != null) {
                zip.close();
            }
        }
    }

    public void testAddFile_empty() throws IOException, Exception {
        final String documentId = mArchive.createDocument(createArchiveId("/").toDocumentId(),
                "text/plain", "hoge.txt");
        mArchive.close();

        // Verify archive.
        ZipFile zip = null;
        try {
            try {
            zip = new ZipFile(mFile);
            } catch (Exception e) {
                throw new IOException(mFile.getAbsolutePath());
            }
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            assertTrue(entries.hasMoreElements());
            final ZipEntry entry = entries.nextElement();
            assertEquals("hoge.txt", entry.getName());
            assertFalse(entries.hasMoreElements());
            final InputStream inputStream = zip.getInputStream(entry);
            final Scanner scanner = new Scanner(inputStream);
            assertFalse(scanner.hasNext());
        } finally {
            if (zip != null) {
                zip.close();
            }
        }
    }
}
