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

package com.android.cts.storageapp;

import static com.android.cts.storageapp.Utils.TAG;
import static com.android.cts.storageapp.Utils.makeUniqueFile;

import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStatVfs;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Utility that tries its hardest to use all possible disk resources.
 */
public class Hoarder {
    private static final String BLOCKS = "blocks";
    private static final String INODES = "inodes";

    public static void main(String[] args) {
        final File dir = new File(args[2]);
        try {
            switch (args[0]) {
                case BLOCKS:
                    doBlocks(dir, false);
                    break;
                case INODES:
                    doInodes(dir, false);
                    break;
            }
        } catch (IOException e) {
            System.out.println("Failed to allocate: " + e.getMessage());
            System.exit(1);
        }

        Log.d(TAG, "Directory gone; giving up");
        System.exit(0);
    }

    public static void doBlocks(File dir, boolean oneshot) throws IOException {
        int failures = 0;
        while (dir.exists()) {
            final File target = makeUniqueFile(dir);
            FileDescriptor fd = null;
            try {
                final long size;
                if (failures == 0) {
                    final StructStatVfs stat = Os.statvfs(dir.getAbsolutePath());
                    size = (stat.f_bfree * stat.f_frsize) / 2;
                } else {
                    size = 65536;
                }
                Log.d(TAG, "Attempting to allocate " + size);

                fd = Os.open(target.getAbsolutePath(),
                        OsConstants.O_RDWR | OsConstants.O_CREAT, 0700);
                Os.posix_fallocate(fd, 0, size);
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.ENOSPC || e.errno == OsConstants.EDQUOT) {
                    failures = Math.min(failures + 1, 32);
                    if (oneshot && failures >= 4) {
                        Log.d(TAG, "Full!");
                        return;
                    } else {
                        Log.d(TAG, "Failed to allocate; trying again");
                        SystemClock.sleep(1000);
                    }
                } else {
                    throw new IOException(e);
                }
            } finally {
                try {
                    if (fd != null) Os.close(fd);
                } catch (ErrnoException ignored) {
                }
            }
        }
    }

    public static void doInodes(File dir, boolean oneshot) throws IOException {
        int failures = 0;
        while (dir.exists()) {
            try {
                final int size = (failures == 0) ? 512 : 16;
                Log.d(TAG, "Attempting to allocate " + size + " inodes");

                final File a = makeUniqueFile(dir);
                Os.mkdir(a.getAbsolutePath(), 0700);
                for (int i = 0; i < size; i++) {
                    final File b = makeUniqueFile(a);
                    Os.mkdir(b.getAbsolutePath(), 0700);
                }
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.ENOSPC || e.errno == OsConstants.EDQUOT) {
                    failures = Math.min(failures + 1, 32);
                    if (oneshot && failures >= 4) {
                        Log.d(TAG, "Full!");
                        return;
                    } else {
                        Log.d(TAG, "Failed to allocate; trying again");
                        SystemClock.sleep(1000);
                    }
                } else {
                    throw new IOException(e);
                }
            }
        }
    }
}
