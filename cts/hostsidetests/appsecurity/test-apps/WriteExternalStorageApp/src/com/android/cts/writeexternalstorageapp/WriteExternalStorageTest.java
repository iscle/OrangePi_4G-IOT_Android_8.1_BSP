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

package com.android.cts.writeexternalstorageapp;

import static android.test.MoreAsserts.assertNotEqual;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_NONE;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.TAG;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirNoWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirReadOnlyAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.buildCommonChildDirs;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.buildProbeFile;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.deleteContents;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getAllPackageSpecificPathsExceptMedia;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getMountPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getPrimaryPackageSpecificPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getSecondaryPackageSpecificPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.readInt;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.writeInt;

import android.os.Environment;
import android.os.SystemClock;
import android.system.Os;
import android.test.AndroidTestCase;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.cts.externalstorageapp.CommonExternalStorageTest;

import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * Test external storage from an application that has
 * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE}.
 */
public class WriteExternalStorageTest extends AndroidTestCase {

    private static final File TEST_FILE = new File(
            Environment.getExternalStorageDirectory(), "meow");

    /**
     * Set of file paths that should all refer to the same location to verify
     * support for legacy paths.
     */
    private static final File[] IDENTICAL_FILES = {
            new File("/sdcard/caek"),
            new File(System.getenv("EXTERNAL_STORAGE"), "caek"),
            new File(Environment.getExternalStorageDirectory(), "caek"),
    };

    @Override
    protected void tearDown() throws Exception {
        try {
            TEST_FILE.delete();
            for (File file : IDENTICAL_FILES) {
                file.delete();
            }
        } finally {
            super.tearDown();
        }
    }

    private void assertExternalStorageMounted() {
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    public void testReadExternalStorage() throws Exception {
        assertExternalStorageMounted();
        Environment.getExternalStorageDirectory().list();
    }

    public void testWriteExternalStorage() throws Exception {
        assertExternalStorageMounted();

        // Write a value and make sure we can read it back
        writeInt(TEST_FILE, 32);
        assertEquals(readInt(TEST_FILE), 32);
    }

    public void testWriteExternalStorageDirs() throws Exception {
        final File probe = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "100CTS");

        assertFalse(probe.exists());
        assertTrue(probe.mkdirs());

        assertDirReadWriteAccess(probe);
    }

    /**
     * Verify that legacy filesystem paths continue working, and that they all
     * point to same location.
     */
    public void testLegacyPaths() throws Exception {
        final Random r = new Random();
        for (File target : IDENTICAL_FILES) {
            // Ensure we're starting with clean slate
            for (File file : IDENTICAL_FILES) {
                file.delete();
            }

            // Write value to our current target
            final int value = r.nextInt();
            writeInt(target, value);

            // Ensure that identical files all contain the value
            for (File file : IDENTICAL_FILES) {
                assertEquals(readInt(file), value);
            }
        }
    }

    public void testPrimaryReadWrite() throws Exception {
        assertDirReadWriteAccess(Environment.getExternalStorageDirectory());
    }

    /**
     * Verify that above our package directories (on primary storage) we always
     * have write access.
     */
    public void testPrimaryWalkingUpTreeReadWrite() throws Exception {
        final List<File> paths = getPrimaryPackageSpecificPaths(getContext());
        final String packageName = getContext().getPackageName();

        for (File path : paths) {
            assertNotNull("Valid media must be inserted during CTS", path);
            assertEquals("Valid media must be inserted during CTS", Environment.MEDIA_MOUNTED,
                    Environment.getExternalStorageState(path));

            assertTrue(path.getAbsolutePath().contains(packageName));

            // Walk until we leave device, writing the whole way
            while (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(path))) {
                assertDirReadWriteAccess(path);
                path = path.getParentFile();
            }
        }
    }

    /**
     * Verify that we have write access in other packages on primary external
     * storage.
     */
    public void testPrimaryOtherPackageWriteAccess() throws Exception {
        final File ourCache = getContext().getExternalCacheDir();
        final File otherCache = new File(ourCache.getAbsolutePath()
                .replace(getContext().getPackageName(), PACKAGE_NONE));
        deleteContents(otherCache);

        assertTrue(otherCache.mkdirs());
        assertDirReadWriteAccess(otherCache);
    }

    /**
     * Verify we have valid mount status until we leave the device.
     */
    public void testMountStatusWalkingUpTree() {
        final File top = Environment.getExternalStorageDirectory();
        File path = getContext().getExternalCacheDir();

        int depth = 0;
        while (depth++ < 32) {
            assertDirReadWriteAccess(path);
            assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState(path));

            if (path.getAbsolutePath().equals(top.getAbsolutePath())) {
                break;
            }

            path = path.getParentFile();
        }

        // Make sure we hit the top
        assertEquals(top.getAbsolutePath(), path.getAbsolutePath());

        // And going one step further should be outside our reach
        path = path.getParentFile();
        assertDirNoWriteAccess(path);
        assertEquals(Environment.MEDIA_UNKNOWN, Environment.getExternalStorageState(path));
    }

    /**
     * Verify mount status for random paths.
     */
    public void testMountStatus() {
        assertEquals(Environment.MEDIA_UNKNOWN,
                Environment.getExternalStorageState(new File("/meow-should-never-exist")));

        // Internal data isn't a mount point
        assertEquals(Environment.MEDIA_UNKNOWN,
                Environment.getExternalStorageState(getContext().getCacheDir()));
    }

    /**
     * Verify that we have write access in our package-specific directories on
     * secondary storage devices, but it becomes read-only access above them.
     */
    public void testSecondaryWalkingUpTreeReadOnly() throws Exception {
        final List<File> paths = getSecondaryPackageSpecificPaths(getContext());
        final String packageName = getContext().getPackageName();

        for (File path : paths) {
            assertNotNull("Valid media must be inserted during CTS", path);
            assertEquals("Valid media must be inserted during CTS", Environment.MEDIA_MOUNTED,
                    Environment.getExternalStorageState(path));

            assertTrue(path.getAbsolutePath().contains(packageName));

            // Walk up until we drop our package
            while (path.getAbsolutePath().contains(packageName)) {
                assertDirReadWriteAccess(path);
                path = path.getParentFile();
            }

            // Walk all the way up to root
            while (path != null) {
                if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(path))) {
                    assertDirReadOnlyAccess(path);
                } else {
                    assertDirNoWriteAccess(path);
                }
                assertDirNoWriteAccess(buildCommonChildDirs(path));
                path = path.getParentFile();
            }
        }
    }

    /**
     * Verify that .nomedia is created correctly.
     */
    public void testVerifyNoMediaCreated() throws Exception {
        for (File file : getAllPackageSpecificPathsExceptMedia(getContext())) {
            deleteContents(file);
        }
        final List<File> paths = getAllPackageSpecificPathsExceptMedia(getContext());

        // Require that .nomedia was created somewhere above each dir
        for (File path : paths) {
            assertNotNull("Valid media must be inserted during CTS", path);
            assertEquals("Valid media must be inserted during CTS", Environment.MEDIA_MOUNTED,
                    Environment.getExternalStorageState(path));

            final File start = path;

            boolean found = false;
            while (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(path))) {
                final File test = new File(path, ".nomedia");
                if (test.exists()) {
                    found = true;
                    break;
                }
                path = path.getParentFile();
            }

            if (!found) {
                fail("Missing .nomedia file above package-specific directory " + start
                        + "; gave up at " + path);
            }
        }
    }

    /**
     * Secondary external storage mount points must always be read-only, per
     * CDD, <em>except</em> for the package specific directories tested by
     * {@link CommonExternalStorageTest#testAllPackageDirsWritable()}.
     */
    public void testSecondaryMountPointsNotWritable() throws Exception {
        // Probe path could be /storage/emulated/0 or /storage/1234-5678
        final File probe = buildProbeFile(Environment.getExternalStorageDirectory());
        assertTrue(probe.createNewFile());

        final String userId = Integer.toString(android.os.Process.myUid() / 100000);
        final List<File> mountPaths = getMountPaths();
        for (File path : mountPaths) {
            // Mount points could be multi-user aware, so try probing both top
            // level and user-specific directory.
            final File userPath = new File(path, userId);

            final File testProbe = new File(path, probe.getName());
            final File testUserProbe = new File(userPath, probe.getName());

            if (testProbe.exists() || testUserProbe.exists()) {
                Log.d(TAG, "Primary external mountpoint " + path);
            } else {
                // This mountpoint is not primary external storage; we must
                // not be able to write.
                Log.d(TAG, "Other mountpoint " + path);
                assertDirNoWriteAccess(path);
                assertDirNoWriteAccess(userPath);
            }
        }
    }

    /**
     * Verify that moving around package-specific directories causes permissions
     * to be updated.
     */
    public void testMovePackageSpecificPaths() throws Exception {
        final File before = getContext().getExternalCacheDir();
        final File beforeFile = new File(before, "test.probe");
        assertTrue(beforeFile.createNewFile());
        assertEquals(Os.getuid(), Os.stat(before.getAbsolutePath()).st_uid);
        assertEquals(Os.getuid(), Os.stat(beforeFile.getAbsolutePath()).st_uid);

        final File after = new File(before.getAbsolutePath()
                .replace(getContext().getPackageName(), "com.example.does.not.exist"));
        after.getParentFile().mkdirs();

        Os.rename(before.getAbsolutePath(), after.getAbsolutePath());

        // Sit around long enough for VFS cache to expire
        SystemClock.sleep(15 * DateUtils.SECOND_IN_MILLIS);

        final File afterFile = new File(after, "test.probe");
        assertNotEqual(Os.getuid(), Os.stat(after.getAbsolutePath()).st_uid);
        assertNotEqual(Os.getuid(), Os.stat(afterFile.getAbsolutePath()).st_uid);
    }
}
