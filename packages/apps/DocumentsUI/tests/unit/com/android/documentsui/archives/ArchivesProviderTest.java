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

package com.android.documentsui.archives;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ArchivesProviderTest {

    private Context mContext;
    private ExecutorService mExecutor = null;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mExecutor = Executors.newSingleThreadExecutor();
    }

    @After
    public void tearDown() throws Exception {
        mExecutor.shutdown();
        assertTrue(mExecutor.awaitTermination(3 /* timeout */, TimeUnit.SECONDS));
    }

    @Test
    public void testQueryRoots() throws InterruptedException, RemoteException {
        final ContentResolver resolver = mContext.getContentResolver();
        final Uri rootsUri = DocumentsContract.buildRootsUri(ArchivesProvider.AUTHORITY);
        try (final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                rootsUri)) {
            final Cursor cursor = client.query(rootsUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null.", cursor);
            assertEquals(0, cursor.getCount());
        }
    }

    @Test
    public void testOpen_Success() throws InterruptedException {
        final Uri sourceUri = DocumentsContract.buildDocumentUri(
                ResourcesProvider.AUTHORITY, "archive.zip");
        final Uri archiveUri = ArchivesProvider.buildUriForArchive(sourceUri,
                ParcelFileDescriptor.MODE_READ_ONLY);

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUri(
                ArchivesProvider.AUTHORITY, DocumentsContract.getDocumentId(archiveUri));

        final ContentResolver resolver = mContext.getContentResolver();
        final CountDownLatch latch = new CountDownLatch(1);

        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                archiveUri);
        ArchivesProvider.acquireArchive(client, archiveUri);

        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(0, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(true, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertNull(extras.getString(DocumentsContract.EXTRA_ERROR));

            final Uri notificationUri = cursor.getNotificationUri();
            assertNotNull(notificationUri);

            resolver.registerContentObserver(notificationUri, false, new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    latch.countDown();
                }
            });
        }

        latch.await(3, TimeUnit.SECONDS);
        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(3, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(false, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertNull(extras.getString(DocumentsContract.EXTRA_ERROR));
        }

        ArchivesProvider.releaseArchive(client, archiveUri);
        client.release();
    }

    @Test
    public void testOpen_Failure() throws InterruptedException {
        final Uri sourceUri = DocumentsContract.buildDocumentUri(
                ResourcesProvider.AUTHORITY, "broken.zip");
        final Uri archiveUri = ArchivesProvider.buildUriForArchive(sourceUri,
                ParcelFileDescriptor.MODE_READ_ONLY);

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUri(
                ArchivesProvider.AUTHORITY, DocumentsContract.getDocumentId(archiveUri));

        final ContentResolver resolver = mContext.getContentResolver();
        final CountDownLatch latch = new CountDownLatch(1);

        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                archiveUri);
        ArchivesProvider.acquireArchive(client, archiveUri);

        {
            // TODO: Close this and any other cursor in this file.
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(0, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(true, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertNull(extras.getString(DocumentsContract.EXTRA_ERROR));

            final Uri notificationUri = cursor.getNotificationUri();
            assertNotNull(notificationUri);

            resolver.registerContentObserver(notificationUri, false, new ContentObserver(null) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    latch.countDown();
                }
            });
        }

        latch.await(3, TimeUnit.SECONDS);
        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);

            assertEquals(0, cursor.getCount());
            final Bundle extras = cursor.getExtras();
            assertEquals(false, extras.getBoolean(DocumentsContract.EXTRA_LOADING, false));
            assertFalse(TextUtils.isEmpty(extras.getString(DocumentsContract.EXTRA_ERROR)));
        }

        ArchivesProvider.releaseArchive(client, archiveUri);
        client.release();
    }

    @Test
    public void testOpen_ClosesOnRelease() throws InterruptedException {
        final Uri sourceUri = DocumentsContract.buildDocumentUri(
                ResourcesProvider.AUTHORITY, "archive.zip");
        final Uri archiveUri = ArchivesProvider.buildUriForArchive(sourceUri,
                ParcelFileDescriptor.MODE_READ_ONLY);

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUri(
                ArchivesProvider.AUTHORITY, DocumentsContract.getDocumentId(archiveUri));

        final ContentResolver resolver = mContext.getContentResolver();

        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                archiveUri);

        // Acquire twice to ensure that the refcount works correctly.
        ArchivesProvider.acquireArchive(client, archiveUri);
        ArchivesProvider.acquireArchive(client, archiveUri);

        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);
        }

        ArchivesProvider.releaseArchive(client, archiveUri);

        {
            final Cursor cursor = resolver.query(childrenUri, null, null, null, null, null);
            assertNotNull("Cursor must not be null. File not found?", cursor);
        }

        ArchivesProvider.releaseArchive(client, archiveUri);

        try {
            resolver.query(childrenUri, null, null, null, null, null);
            fail("The archive was expected to be invalid on the last release call.");
        } catch (IllegalStateException e) {
            // Expected.
        }

        client.release();
    }

    @Test
    public void testNoNotificationAfterAllReleased() throws InterruptedException, RemoteException {
        final Uri sourceUri = DocumentsContract.buildDocumentUri(
                ResourcesProvider.AUTHORITY, "archive.zip");
        final Uri archiveUri = ArchivesProvider.buildUriForArchive(sourceUri,
                ParcelFileDescriptor.MODE_READ_ONLY);

        final Uri childrenUri = DocumentsContract.buildChildDocumentsUri(
                ArchivesProvider.AUTHORITY, DocumentsContract.getDocumentId(archiveUri));

        final ContentResolver resolver = mContext.getContentResolver();

        final ContentProviderClient client = resolver.acquireUnstableContentProviderClient(
                archiveUri);

        ArchivesProvider.acquireArchive(client, archiveUri);
        final Cursor cursor = client.query(childrenUri, null, null, null, null, null);
        final Bundle extra = cursor.getExtras();
        assertTrue(extra.getBoolean(DocumentsContract.EXTRA_LOADING, false));
        final Uri notificationUri = cursor.getNotificationUri();

        ArchivesProvider.releaseArchive(client, archiveUri);
        final CountDownLatch latch = new CountDownLatch(1);
        resolver.registerContentObserver(notificationUri, false, new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                latch.countDown();
            }
        });

        // Assert that there is no notification if no one has acquired this archive and this wait
        // times out.
        assertFalse(latch.await(1, TimeUnit.SECONDS));

        client.release();
    }
}
