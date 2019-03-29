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

package com.android.cts.readexternalstorageapp;

import static android.test.MoreAsserts.assertNotEqual;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_WRITE;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertFileReadOnlyAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertFileReadWriteAccess;

import android.system.Os;

import android.test.AndroidTestCase;

import java.io.File;
import java.util.List;

public class ReadMultiViewTest extends AndroidTestCase {
    /**
     * Create a file in PACKAGE_READ's cache.
     */
    public void testFolderSetup() throws Exception {
        final File ourCache = getContext().getExternalCacheDir();
        final File ourTestDir = new File(ourCache, "testDir");
        final File ourFile = new File(ourTestDir, "test.probe");

        ourFile.getParentFile().mkdirs();
        assertTrue(ourFile.createNewFile());
    }

    /**
     * Verify that we have R/W access to test.probe in our cache.
     */
    public void testRWAccess() throws Exception {
        final File ourCache = getContext().getExternalCacheDir();
        final File ourTestDir = new File(ourCache, "testDir");
        final File testFile = new File(ourTestDir, "test.probe");

        assertFileReadWriteAccess(testFile);
        assertEquals(Os.getuid(), Os.stat(ourCache.getAbsolutePath()).st_uid);
        assertEquals(Os.getuid(), Os.stat(ourTestDir.getAbsolutePath()).st_uid);
        assertEquals(Os.getuid(), Os.stat(testFile.getAbsolutePath()).st_uid);
    }

    /**
     * Verify that we have RO access to test.probe in PACKAGE_WRITE's cache.
     */
    public void testROAccess() throws Exception {
        final File ourCache = getContext().getExternalCacheDir();
        final File otherCache = new File(ourCache.getAbsolutePath()
                .replace(getContext().getPackageName(), PACKAGE_WRITE));
        final File otherTestDir = new File(otherCache, "testDir");
        final File testFile = new File(otherTestDir, "test.probe");

        assertFileReadOnlyAccess(testFile);
        assertNotEqual(Os.getuid(), Os.stat(testFile.getAbsolutePath()).st_uid);
        assertNotEqual(Os.getuid(), Os.stat(otherCache.getAbsolutePath()).st_uid);
        assertNotEqual(Os.getuid(), Os.stat(otherTestDir.getAbsolutePath()).st_uid);
    }
}
