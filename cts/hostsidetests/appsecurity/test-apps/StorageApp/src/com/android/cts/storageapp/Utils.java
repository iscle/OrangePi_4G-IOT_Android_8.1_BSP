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

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructUtsname;
import android.util.Log;

import junit.framework.AssertionFailedError;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    public static final String TAG = "StorageApp";

    public static final String PKG_A = "com.android.cts.storageapp_a";
    public static final String PKG_B = "com.android.cts.storageapp_b";

    // You will pry my kibibytes from my cold dead hands! But to make test
    // results easier to debug, we'll use kilobytes...
    public static final long KB_IN_BYTES = 1000;
    public static final long MB_IN_BYTES = KB_IN_BYTES * 1000;
    public static final long GB_IN_BYTES = MB_IN_BYTES * 1000;

    public static final long DATA_INT = (2 + 3 + 5 + 13 + 17 + 19 + 23) * MB_IN_BYTES;
    public static final long DATA_EXT = (7 + 11) * MB_IN_BYTES;
    public static final long DATA_ALL = DATA_INT + DATA_EXT; // 100MB

    public static final long CACHE_INT = (3 + 5 + 17 + 19 + 23) * MB_IN_BYTES;
    public static final long CACHE_EXT = (11) * MB_IN_BYTES;
    public static final long CACHE_ALL = CACHE_INT + CACHE_EXT; // 78MB

    public static final long CODE_ALL = 29 * MB_IN_BYTES;

    public static void useSpace(Context c) throws Exception {
        // We use prime numbers for all values so that we can easily identify
        // which file(s) are missing from broken test results.
        useWrite(makeUniqueFile(c.getFilesDir()), 2 * MB_IN_BYTES);
        useWrite(makeUniqueFile(c.getCodeCacheDir()), 3 * MB_IN_BYTES);
        useWrite(makeUniqueFile(c.getCacheDir()), 5 * MB_IN_BYTES);
        useWrite(makeUniqueFile(c.getExternalFilesDir("meow")), 7 * MB_IN_BYTES);
        useWrite(makeUniqueFile(c.getExternalCacheDir()), 11 * MB_IN_BYTES);

        useFallocate(makeUniqueFile(c.getFilesDir()), 13 * MB_IN_BYTES);
        useFallocate(makeUniqueFile(c.getCodeCacheDir()), 17 * MB_IN_BYTES);
        useFallocate(makeUniqueFile(c.getCacheDir()), 19 * MB_IN_BYTES);
        final File subdir = makeUniqueFile(c.getCacheDir());
        Os.mkdir(subdir.getAbsolutePath(), 0700);
        useFallocate(makeUniqueFile(subdir), 23 * MB_IN_BYTES);

        useWrite(makeUniqueFile(c.getObbDir()), 29 * MB_IN_BYTES);
    }

    public static void assertAtLeast(long expected, long actual) {
        if (actual < expected) {
            throw new AssertionFailedError("Expected at least " + expected + " but was " + actual
                    + " [" + android.os.Process.myUserHandle() + "]");
        }
    }

    public static void assertMostlyEquals(long expected, long actual) {
        assertMostlyEquals(expected, actual, 500 * KB_IN_BYTES);
    }

    public static void assertMostlyEquals(long expected, long actual, long delta) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionFailedError("Expected roughly " + expected + " but was " + actual
                    + " [" + android.os.Process.myUserHandle() + "]");
        }
    }

    public static File makeUniqueFile(File dir) {
        return new File(dir, Long.toString(System.nanoTime()));
    }

    public static File useWrite(File file, long size) throws Exception {
        try (FileOutputStream os = new FileOutputStream(file)) {
            final byte[] buf = new byte[1024];
            while (size > 0) {
                os.write(buf, 0, (int) Math.min(buf.length, size));
                size -= buf.length;
            }
        }
        return file;
    }

    public static File useFallocate(File file, long length, long time) throws Exception {
        final File res = useFallocate(file, length);
        file.setLastModified(time);
        return res;
    }

    public static File useFallocate(File file, long length) throws Exception {
        final FileDescriptor fd = Os.open(file.getAbsolutePath(),
                OsConstants.O_CREAT | OsConstants.O_RDWR | OsConstants.O_TRUNC, 0700);
        try {
            Os.posix_fallocate(fd, 0, length);
        } finally {
            Os.close(fd);
        }
        return file;
    }

    public static long getSizeManual(File dir) throws Exception {
        return getSizeManual(dir, false);
    }

    public static long getSizeManual(File dir, boolean excludeObb) throws Exception {
        long size = getAllocatedSize(dir);
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                if (excludeObb && f.getName().equalsIgnoreCase("obb")
                        && f.getParentFile().getName().equalsIgnoreCase("Android")) {
                    Log.d(TAG, "Ignoring OBB directory " + f);
                } else {
                    size += getSizeManual(f, excludeObb);
                }
            } else {
                size += getAllocatedSize(f);
            }
        }
        return size;
    }

    private static long getAllocatedSize(File f) throws Exception {
        return Os.lstat(f.getAbsolutePath()).st_blocks * 512;
    }

    public static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    success = false;
                }
            }
        }
        return success;
    }

    public static boolean shouldHaveQuota(StructUtsname uname) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] fields = line.split(" ");
                final String target = fields[1];
                final String format = fields[2];

                if (target.equals("/data") && !format.equals("ext4")) {
                    Log.d(TAG, "Assuming no quota support because /data is " + format);
                    return false;
                }
            }
        }

        final Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)").matcher(uname.release);
        if (!matcher.find()) {
            throw new IllegalStateException("Failed to parse version: " + uname.release);
        }
        final int major = Integer.parseInt(matcher.group(1));
        final int minor = Integer.parseInt(matcher.group(2));
        return (major > 3 || (major == 3 && minor >= 18));
    }

    public static void logCommand(String... cmd) throws Exception {
        final Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();

        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        copy(proc.getInputStream(), buf);
        final int res = proc.waitFor();

        Log.d(TAG, Arrays.toString(cmd) + " result " + res + ":");
        Log.d(TAG, buf.toString());
    }

    /** Shamelessly lifted from libcore.io.Streams */
    public static int copy(InputStream in, OutputStream out) throws IOException {
        int total = 0;
        byte[] buffer = new byte[8192];
        int c;
        while ((c = in.read(buffer)) != -1) {
            total += c;
            out.write(buffer, 0, c);
        }
        return total;
    }
}
