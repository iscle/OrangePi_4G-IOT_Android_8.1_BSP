/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cts.externalstorageapp;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirNoWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getAllPackageSpecificPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getMountPaths;

import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.text.format.DateUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Test external storage from an application that has no external storage
 * permissions.
 */
public class ExternalStorageTest extends AndroidTestCase {

    public void testPrimaryNoAccess() throws Exception {
        assertDirNoAccess(Environment.getExternalStorageDirectory());
    }

    /**
     * Verify that above our package directories we always have no access.
     */
    public void testAllWalkingUpTreeNoAccess() throws Exception {
        final List<File> paths = getAllPackageSpecificPaths(getContext());
        final String packageName = getContext().getPackageName();

        for (File path : paths) {
            if (path == null) continue;

            assertTrue(path.getAbsolutePath().contains(packageName));

            // Walk up until we drop our package
            while (path.getAbsolutePath().contains(packageName)) {
                assertDirReadWriteAccess(path);
                path = path.getParentFile();
            }

            // Keep walking up until we leave device
            while (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(path))) {
                assertDirNoAccess(path);
                path = path.getParentFile();
            }
        }
    }

    /**
     * Verify that we don't have read access to any storage mountpoints.
     */
    public void testMountPointsNotReadable() throws Exception {
        final String userId = Integer.toString(android.os.Process.myUid() / 100000);
        final List<File> mountPaths = getMountPaths();
        for (File path : mountPaths) {
            if (path.getAbsolutePath().startsWith("/mnt/")
                    || path.getAbsolutePath().startsWith("/storage/")) {
                // Mount points could be multi-user aware, so try probing both
                // top level and user-specific directory.
                final File userPath = new File(path, userId);

                assertDirNoAccess(path);
                assertDirNoAccess(userPath);
            }
        }
    }

    /**
     * Verify that we can't download things outside package directory.
     */
    public void testDownloadManager() throws Exception {
        final DownloadManager dm = getContext().getSystemService(DownloadManager.class);
        try {
            final Uri source = Uri.parse("http://www.example.com");
            final File target = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "meow");
            dm.enqueue(new Request(source).setDestinationUri(Uri.fromFile(target)));
            fail("Unexpected success writing outside package directory");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Verify that we can download things into our package directory.
     */
    public void testDownloadManagerPackage() throws Exception {
        final DownloadManager dm = getContext().getSystemService(DownloadManager.class);
        final DownloadCompleteReceiver receiver = new DownloadCompleteReceiver();
        try {
            IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            mContext.registerReceiver(receiver, intentFilter);

            final Uri source = Uri.parse("http://www.example.com");
            final File target = new File(
                    getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "meow");

            final long id = dm.enqueue(new Request(source).setDestinationUri(Uri.fromFile(target)));
            try {
                receiver.waitForDownloadComplete(30 * DateUtils.SECOND_IN_MILLIS, id);
                assertSuccessfulDownload(id, target);
            } finally {
                dm.remove(id);
            }
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    /**
     * Shamelessly borrowed from DownloadManagerTest.java
     */
    private static class DownloadCompleteReceiver extends BroadcastReceiver {
        private HashSet<Long> mCompleteIds = new HashSet<>();

        public DownloadCompleteReceiver() {
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mCompleteIds) {
                mCompleteIds.add(intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                mCompleteIds.notifyAll();
            }
        }

        private boolean isCompleteLocked(long... ids) {
            for (long id : ids) {
                if (!mCompleteIds.contains(id)) {
                    return false;
                }
            }
            return true;
        }

        public void waitForDownloadComplete(long timeoutMillis, long... waitForIds)
                throws InterruptedException {
            if (waitForIds.length == 0) {
                throw new IllegalArgumentException("Missing IDs to wait for");
            }

            final long startTime = SystemClock.elapsedRealtime();
            do {
                synchronized (mCompleteIds) {
                    mCompleteIds.wait(timeoutMillis);
                    if (isCompleteLocked(waitForIds)) return;
                }
            } while ((SystemClock.elapsedRealtime() - startTime) < timeoutMillis);

            throw new InterruptedException("Timeout waiting for IDs " + Arrays.toString(waitForIds)
                    + "; received " + mCompleteIds.toString()
                    + ".  Make sure you have WiFi or some other connectivity for this test.");
        }
    }

    private void assertSuccessfulDownload(long id, File location) {
        final DownloadManager dm = getContext().getSystemService(DownloadManager.class);

        Cursor cursor = null;
        try {
            cursor = dm.query(new Query().setFilterById(id));
            assertTrue(cursor.moveToNext());
            assertEquals(DownloadManager.STATUS_SUCCESSFUL, cursor.getInt(
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)));
            assertEquals(Uri.fromFile(location).toString(),
                    cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
            assertTrue(location.exists());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
