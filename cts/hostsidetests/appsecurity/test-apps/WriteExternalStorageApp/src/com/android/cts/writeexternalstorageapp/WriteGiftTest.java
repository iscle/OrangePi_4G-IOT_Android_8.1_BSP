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

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_NONE;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_READ;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.PACKAGE_WRITE;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertFileReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getAllPackageSpecificGiftPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.readInt;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.writeInt;

import android.test.AndroidTestCase;

import java.io.File;
import java.util.List;

public class WriteGiftTest extends AndroidTestCase {
    /**
     * Leave gifts for other packages in their primary external cache dirs.
     */
    public void testGifts() throws Exception {
        final List<File> noneList = getAllPackageSpecificGiftPaths(getContext(), PACKAGE_NONE);
        for (File none : noneList) {
            none.getParentFile().mkdirs();
            none.createNewFile();
            assertFileReadWriteAccess(none);

            writeInt(none, 100);
            assertEquals(100, readInt(none));
        }

        final List<File> readList = getAllPackageSpecificGiftPaths(getContext(), PACKAGE_READ);
        for (File read : readList) {
            read.getParentFile().mkdirs();
            read.createNewFile();
            assertFileReadWriteAccess(read);

            writeInt(read, 101);
            assertEquals(101, readInt(read));
        }

        final List<File> writeList = getAllPackageSpecificGiftPaths(getContext(), PACKAGE_WRITE);
        for (File write : writeList) {
            write.getParentFile().mkdirs();
            write.createNewFile();
            assertFileReadWriteAccess(write);

            writeInt(write, 102);
            assertEquals(102, readInt(write));
        }
    }
}
