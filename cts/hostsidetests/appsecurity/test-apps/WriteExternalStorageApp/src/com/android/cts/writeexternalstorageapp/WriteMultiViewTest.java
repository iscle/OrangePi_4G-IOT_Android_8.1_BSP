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

package com.android.cts.writeexternalstorageapp;

import static android.test.MoreAsserts.assertNotEqual;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_READ;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertFileReadWriteAccess;

import android.system.Os;

import android.test.AndroidTestCase;


import android.util.Log;

import java.io.File;
import java.util.List;

public class WriteMultiViewTest extends AndroidTestCase {
    /**
     * Move PACKAGE_READ's cache to our cache
     */
    public void testMoveAway() throws Exception {
        final File ourCache = getContext().getExternalCacheDir();
        final File otherCache = new File(ourCache.getAbsolutePath()
                .replace(getContext().getPackageName(), PACKAGE_READ));
        final File ourTestDir = new File(ourCache, "testDir");
        final File otherTestDir = new File(otherCache, "testDir");
        final File beforeFile = new File(otherTestDir, "test.probe");
        final File afterFile = new File(ourTestDir, "test.probe");

        Os.rename(otherTestDir.getAbsolutePath(), ourTestDir.getAbsolutePath());

        assertEquals(Os.getuid(), Os.stat(ourCache.getAbsolutePath()).st_uid);
        assertEquals(Os.getuid(), Os.stat(ourTestDir.getAbsolutePath()).st_uid);
        assertEquals(Os.getuid(), Os.stat(afterFile.getAbsolutePath()).st_uid);
    }

    /**
     * Move our cache to PACKAGE_READ's cache
     */
    public void testMoveBack() throws Exception {
        final File ourCache = getContext().getExternalCacheDir();
        final File otherCache = new File(ourCache.getAbsolutePath()
                .replace(getContext().getPackageName(), PACKAGE_READ));
        final File ourTestDir = new File(ourCache, "testDir");
        final File otherTestDir = new File(otherCache, "testDir");
        final File beforeFile = new File(ourTestDir, "test.probe");
        final File afterFile = new File(otherTestDir, "test.probe");

        Os.rename(ourTestDir.getAbsolutePath(), otherTestDir.getAbsolutePath());

        assertNotEqual(Os.getuid(), Os.stat(otherCache.getAbsolutePath()).st_uid);
        assertNotEqual(Os.getuid(), Os.stat(otherTestDir.getAbsolutePath()).st_uid);
        assertNotEqual(Os.getuid(), Os.stat(afterFile.getAbsolutePath()).st_uid);
    }
}
