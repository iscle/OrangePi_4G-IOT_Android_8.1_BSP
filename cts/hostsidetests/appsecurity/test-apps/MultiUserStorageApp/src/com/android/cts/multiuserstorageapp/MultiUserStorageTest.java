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

package com.android.cts.multiuserstorageapp;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertFileNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getAllPackageSpecificPathsExceptObb;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.readInt;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.writeInt;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Test multi-user emulated storage environment, ensuring that each user has
 * isolated storage minus shared OBB directory.
 */
public class MultiUserStorageTest extends AndroidTestCase {
    private static final String TAG = "MultiUserStorageTest";

    private static final String FILE_PREFIX = "MUST_";

    private final String FILE_SINGLETON = FILE_PREFIX + "singleton";
    private final String FILE_OBB_SINGLETON = FILE_PREFIX + "obb_singleton";
    private final String FILE_OBB_API_SINGLETON = FILE_PREFIX + "obb_api_singleton";
    private final String FILE_MY_UID = FILE_PREFIX + android.os.Process.myUid();

    private static final int OBB_API_VALUE = 0xcafe;
    private static final int OBB_VALUE = 0xf00d;

    private void wipeTestFiles(File dir) {
        dir.mkdirs();
        final File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.getName().startsWith(FILE_PREFIX)) {
                Log.d(TAG, "Wiping " + file);
                file.delete();
            }
        }
    }

    public void testCleanIsolatedStorage() throws Exception {
        wipeTestFiles(Environment.getExternalStorageDirectory());
    }

    public void testWriteIsolatedStorage() throws Exception {
        final int uid = android.os.Process.myUid();

        writeInt(buildApiPath(FILE_SINGLETON), uid);
        writeInt(buildApiPath(FILE_MY_UID), uid);

        // Write to every external path we think we have access to
        for (File path : getAllPackageSpecificPathsExceptObb(getContext())) {
            assertNotNull("Valid media must be inserted during CTS", path);
            assertEquals("Valid media must be inserted during CTS", Environment.MEDIA_MOUNTED,
                    Environment.getExternalStorageState(path));

            writeInt(new File(path, FILE_SINGLETON), uid);
        }
    }

    public void testReadIsolatedStorage() throws Exception {
        final int uid = android.os.Process.myUid();

        // Expect that the value we wrote earlier is still valid and wasn't
        // overwritten by us running as another user.
        assertEquals("Failed to read singleton file from API path", uid,
                readInt(buildApiPath(FILE_SINGLETON)));
        assertEquals("Failed to read singleton file from env path", uid,
                readInt(buildEnvPath(FILE_SINGLETON)));
        assertEquals("Failed to read singleton file from raw path", uid,
                readInt(buildRawPath(FILE_SINGLETON)));

        assertEquals("Failed to read UID file from API path", uid,
                readInt(buildApiPath(FILE_MY_UID)));

        for (File path : getAllPackageSpecificPathsExceptObb(getContext())) {
            assertNotNull("Valid media must be inserted during CTS", path);
            assertEquals("Valid media must be inserted during CTS", Environment.MEDIA_MOUNTED,
                    Environment.getExternalStorageState(path));

            assertEquals("Unexpected value in singleton file at " + path, uid,
                    readInt(new File(path, FILE_SINGLETON)));
        }
    }

    public void testCleanObbStorage() throws Exception {
        wipeTestFiles(getContext().getObbDir());
    }

    public void testWriteObbStorage() throws Exception {
        writeInt(buildApiObbPath(FILE_OBB_API_SINGLETON), OBB_API_VALUE);
        writeInt(buildEnvObbPath(FILE_OBB_SINGLETON), OBB_VALUE);
    }

    public void testReadObbStorage() throws Exception {
        assertEquals("Failed to read OBB file from API path", OBB_API_VALUE,
                readInt(buildApiObbPath(FILE_OBB_API_SINGLETON)));

        assertEquals("Failed to read OBB file from env path", OBB_VALUE,
                readInt(buildEnvObbPath(FILE_OBB_SINGLETON)));
        assertEquals("Failed to read OBB file from raw path", OBB_VALUE,
                readInt(buildRawObbPath(FILE_OBB_SINGLETON)));
    }

    /**
     * Verify that we can't poke at storage of other users.
     */
    public void testUserIsolation() throws Exception {
        final File myPath = Environment.getExternalStorageDirectory();
        final int myId = android.os.Process.myUid() / 100000;
        assertEquals(String.valueOf(myId), myPath.getName());

        Log.d(TAG, "My path is " + myPath);
        final File basePath = myPath.getParentFile();
        for (int i = 0; i < 128; i++) {
            if (i == myId) continue;

            final File otherPath = new File(basePath, String.valueOf(i));
            assertDirNoAccess(otherPath);
        }
    }

    /**
     * Verify that files cannot be accessed through media provider.
     */
    public void testMediaProviderUserIsolation() throws Exception {
        final File myPath = Environment.getExternalStorageDirectory();
        final int myId = android.os.Process.myUid() / 100000;
        assertEquals(String.valueOf(myId), myPath.getName());

        Log.d(TAG, "My path is " + myPath + " user id " + myId);
        final File basePath = myPath.getParentFile();
        for (int i = 0; i < 128; i++) {
            if (i == myId) continue;
            final File otherPath = new File(basePath,i + "/" + FILE_SINGLETON);
            assertFileNoAccess(otherPath);

            final String URI_MEDIA_STRING = "content://media/external/audio/media/";
            ContentResolver contentResolver = mContext.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put("_data", otherPath.getAbsolutePath());
            contentResolver.insert(Uri.parse(URI_MEDIA_STRING), cv);

            try {
                mContext.getContentResolver().openInputStream(Uri.parse(URI_MEDIA_STRING));
                fail("Accessing through media provider should not be allowed. Path " + myPath);
            } catch (FileNotFoundException expected) {
                // OK
            }
        }
    }

    private File buildApiObbPath(String file) {
        return new File(getContext().getObbDir(), file);
    }

    private File buildEnvObbPath(String file) {
        return new File(new File(System.getenv("EXTERNAL_STORAGE"), "Android/obb"), file);
    }

    private File buildRawObbPath(String file) {
        return new File("/sdcard/Android/obb/", file);
    }

    private static File buildApiPath(String file) {
        return new File(Environment.getExternalStorageDirectory(), file);
    }

    private static File buildEnvPath(String file) {
        return new File(System.getenv("EXTERNAL_STORAGE"), file);
    }

    private static File buildRawPath(String file) {
        return new File("/sdcard/", file);
    }
}
