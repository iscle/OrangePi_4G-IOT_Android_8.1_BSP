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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests common functionality that should be supported regardless of external
 * storage status.
 */
public class CommonExternalStorageTest extends AndroidTestCase {
    public static final String TAG = "CommonExternalStorageTest";

    public static final String PACKAGE_NONE = "com.android.cts.externalstorageapp";
    public static final String PACKAGE_READ = "com.android.cts.readexternalstorageapp";
    public static final String PACKAGE_WRITE = "com.android.cts.writeexternalstorageapp";

    /**
     * Dump helpful debugging details.
     */
    public void testDumpDebug() throws Exception {
        logCommand("/system/bin/id");
        logCommand("/system/bin/cat", "/proc/self/mountinfo");
    }

    /**
     * Primary storage must always be mounted.
     */
    public void testExternalStorageMounted() {
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    /**
     * Verify that single path is always first item in multiple.
     */
    public void testMultipleCacheDirs() throws Exception {
        final File single = getContext().getExternalCacheDir();
        assertNotNull("Primary storage must always be available", single);
        final File firstMultiple = getContext().getExternalCacheDirs()[0];
        assertEquals(single, firstMultiple);
    }

    /**
     * Verify that single path is always first item in multiple.
     */
    public void testMultipleFilesDirs() throws Exception {
        final File single = getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        assertNotNull("Primary storage must always be available", single);
        final File firstMultiple = getContext()
                .getExternalFilesDirs(Environment.DIRECTORY_PICTURES)[0];
        assertEquals(single, firstMultiple);
    }

    /**
     * Verify that single path is always first item in multiple.
     */
    public void testMultipleObbDirs() throws Exception {
        final File single = getContext().getObbDir();
        assertNotNull("Primary storage must always be available", single);
        final File firstMultiple = getContext().getObbDirs()[0];
        assertEquals(single, firstMultiple);
    }

    /**
     * Verify we can write to our own package dirs.
     */
    public void testAllPackageDirsWritable() throws Exception {
        final long testValue = 12345000;
        final List<File> paths = getAllPackageSpecificPaths(getContext());
        for (File path : paths) {
            assertNotNull("Valid media must be inserted during CTS", path);
            assertEquals("Valid media must be inserted during CTS", Environment.MEDIA_MOUNTED,
                    Environment.getExternalStorageState(path));

            assertDirReadWriteAccess(path);

            final File directChild = new File(path, "directChild");
            final File subdir = new File(path, "subdir");
            final File subdirChild = new File(path, "subdirChild");

            writeInt(directChild, 32);
            subdir.mkdirs();
            assertDirReadWriteAccess(subdir);
            writeInt(subdirChild, 64);

            assertEquals(32, readInt(directChild));
            assertEquals(64, readInt(subdirChild));

            assertTrue("Must be able to set last modified", directChild.setLastModified(testValue));
            assertTrue("Must be able to set last modified", subdirChild.setLastModified(testValue));

            assertEquals(testValue, directChild.lastModified());
            assertEquals(testValue, subdirChild.lastModified());
        }

        for (File path : paths) {
            deleteContents(path);
        }
    }

    /**
     * Return a set of several package-specific external storage paths.
     */
    public static List<File> getAllPackageSpecificPaths(Context context) {
        final List<File> paths = new ArrayList<File>();
        Collections.addAll(paths, context.getExternalCacheDirs());
        Collections.addAll(paths, context.getExternalFilesDirs(null));
        Collections.addAll(paths, context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES));
        Collections.addAll(paths, context.getExternalMediaDirs());
        Collections.addAll(paths, context.getObbDirs());
        return paths;
    }

    public static List<File> getAllPackageSpecificPathsExceptMedia(Context context) {
        final List<File> paths = new ArrayList<File>();
        Collections.addAll(paths, context.getExternalCacheDirs());
        Collections.addAll(paths, context.getExternalFilesDirs(null));
        Collections.addAll(paths, context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES));
        Collections.addAll(paths, context.getObbDirs());
        return paths;
    }

    public static List<File> getAllPackageSpecificPathsExceptObb(Context context) {
        final List<File> paths = new ArrayList<File>();
        Collections.addAll(paths, context.getExternalCacheDirs());
        Collections.addAll(paths, context.getExternalFilesDirs(null));
        Collections.addAll(paths, context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES));
        Collections.addAll(paths, context.getExternalMediaDirs());
        return paths;
    }

    /**
     * Return a set of several package-specific external storage paths pointing
     * at "gift" files designed to be exchanged with the target package.
     */
    public static List<File> getAllPackageSpecificGiftPaths(Context context,
            String targetPackageName) {
        final List<File> files = getPrimaryPackageSpecificPaths(context);
        final List<File> targetFiles = new ArrayList<>();
        for (File file : files) {
            final File targetFile = new File(
                    file.getAbsolutePath().replace(context.getPackageName(), targetPackageName));
            targetFiles.add(new File(targetFile, targetPackageName + ".gift"));
        }
        return targetFiles;
    }

    public static List<File> getPrimaryPackageSpecificPaths(Context context) {
        final List<File> paths = new ArrayList<File>();
        Collections.addAll(paths, context.getExternalCacheDir());
        Collections.addAll(paths, context.getExternalFilesDir(null));
        Collections.addAll(paths, context.getExternalFilesDir(Environment.DIRECTORY_PICTURES));
        Collections.addAll(paths, context.getObbDir());
        return paths;
    }

    public static List<File> getSecondaryPackageSpecificPaths(Context context) {
        final List<File> paths = new ArrayList<File>();
        Collections.addAll(paths, dropFirst(context.getExternalCacheDirs()));
        Collections.addAll(paths, dropFirst(context.getExternalFilesDirs(null)));
        Collections.addAll(
                paths, dropFirst(context.getExternalFilesDirs(Environment.DIRECTORY_PICTURES)));
        Collections.addAll(paths, dropFirst(context.getObbDirs()));
        return paths;
    }

    public static List<File> getMountPaths() throws IOException {
        final List<File> paths = new ArrayList<>();
        final BufferedReader br = new BufferedReader(new FileReader("/proc/self/mounts"));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] fields = line.split(" ");
                paths.add(new File(fields[1]));
            }
        } finally {
            br.close();
        }
        return paths;
    }

    private static File[] dropFirst(File[] before) {
        final File[] after = new File[before.length - 1];
        System.arraycopy(before, 1, after, 0, after.length);
        return after;
    }

    public static File buildProbeFile(File dir) {
        return new File(dir, ".probe_" + System.nanoTime());
    }

    public static File[] buildCommonChildDirs(File dir) {
        return new File[] {
                new File(dir, Environment.DIRECTORY_MUSIC),
                new File(dir, Environment.DIRECTORY_PODCASTS),
                new File(dir, Environment.DIRECTORY_ALARMS),
                new File(dir, Environment.DIRECTORY_RINGTONES),
                new File(dir, Environment.DIRECTORY_NOTIFICATIONS),
                new File(dir, Environment.DIRECTORY_PICTURES),
                new File(dir, Environment.DIRECTORY_MOVIES),
                new File(dir, Environment.DIRECTORY_DOWNLOADS),
                new File(dir, Environment.DIRECTORY_DCIM),
                new File(dir, Environment.DIRECTORY_DOCUMENTS),
        };
    }

    public static void assertDirReadOnlyAccess(File path) {
        Log.d(TAG, "Asserting read-only access to " + path);

        assertTrue("exists", path.exists());
        assertTrue("read", path.canRead());
        assertTrue("execute", path.canExecute());
        assertNotNull("list", path.list());

        try {
            final File probe = buildProbeFile(path);
            assertFalse(probe.createNewFile());
            assertFalse(probe.exists());
            assertFalse(probe.delete());
            fail("able to create probe!");
        } catch (IOException e) {
            // expected
        }
    }

    public static void assertDirReadWriteAccess(File path) {
        Log.d(TAG, "Asserting read/write access to " + path);

        assertTrue("exists", path.exists());
        assertTrue("read", path.canRead());
        assertTrue("execute", path.canExecute());
        assertNotNull("list", path.list());

        try {
            final File probe = buildProbeFile(path);
            assertTrue(probe.createNewFile());
            assertTrue(probe.exists());
            assertTrue(probe.delete());
            assertFalse(probe.exists());
        } catch (IOException e) {
            fail("failed to create probe!");
        }
    }

    public static void assertDirNoAccess(File path) {
        Log.d(TAG, "Asserting no access to " + path);

        assertFalse("read", path.canRead());
        assertNull("list", path.list());

        try {
            final File probe = buildProbeFile(path);
            assertFalse(probe.createNewFile());
            assertFalse(probe.exists());
            assertFalse(probe.delete());
            fail("able to create probe!");
        } catch (IOException e) {
            // expected
        }
    }

    public static void assertDirNoWriteAccess(File[] paths) {
        for (File path : paths) {
            assertDirNoWriteAccess(path);
        }
    }

    public static void assertDirNoWriteAccess(File path) {
        Log.d(TAG, "Asserting no write access to " + path);

        try {
            final File probe = buildProbeFile(path);
            assertFalse(probe.createNewFile());
            assertFalse(probe.exists());
            assertFalse(probe.delete());
            fail("able to create probe!");
        } catch (IOException e) {
            // expected
        }
    }

    public static void assertFileReadOnlyAccess(File path) {
        try {
            new FileInputStream(path).close();
        } catch (IOException e) {
            fail("failed to read!");
        }

        try {
            new FileOutputStream(path, true).close();
            fail("able to write!");
        } catch (IOException e) {
            // expected
        }
    }

    public static void assertFileReadWriteAccess(File path) {
        try {
            new FileInputStream(path).close();
        } catch (IOException e) {
            fail("failed to read!");
        }

        try {
            new FileOutputStream(path, true).close();
        } catch (IOException e) {
            fail("failed to write!");
        }
    }

    public static void assertFileNoAccess(File path) {
        try {
            new FileInputStream(path).close();
            fail("able to read!");
        } catch (IOException e) {
            // expected
        }

        try {
            new FileOutputStream(path, true).close();
            fail("able to write!");
        } catch (IOException e) {
            // expected
        }
    }

    public static void assertMediaNoAccess(ContentResolver resolver, boolean legacyApp)
            throws Exception {
        final ContentValues values = new ContentValues();
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.DATA,
                buildProbeFile(Environment.getExternalStorageDirectory()).getAbsolutePath());

        try {
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (legacyApp) {
                // For legacy apps we do not crash - just make the operation do nothing
                assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        .buildUpon().appendPath("0").build().toString(), uri.toString());
            } else {
                fail("Expected access to be blocked");
            }
        } catch (Exception expected) {
        }
    }

    public static void assertMediaReadWriteAccess(ContentResolver resolver) throws Exception {
        final ContentValues values = new ContentValues();
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.DATA,
                buildProbeFile(Environment.getExternalStorageDirectory()).getAbsolutePath());

        final Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        try {
            resolver.openFileDescriptor(uri, "rw").close();
            resolver.openFileDescriptor(uri, "w").close();
            resolver.openFileDescriptor(uri, "r").close();
        } finally {
            resolver.delete(uri, null, null);
        }
    }

    private static boolean isWhiteList(File file) {
        final String[] whiteLists = {
                "autorun.inf", ".android_secure", "android_secure"
        };
        if (file.getParentFile().getAbsolutePath().equals(
                Environment.getExternalStorageDirectory().getAbsolutePath())) {
            for (String whiteList : whiteLists) {
                if (file.getName().equalsIgnoreCase(whiteList)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static File[] removeWhiteList(File[] files) {
        List<File> fileList = new ArrayList<File>();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (!isWhiteList(file)) {
                fileList.add(file);
            }
        }
        return fileList.toArray(new File[fileList.size()]);
    }

    public static void deleteContents(File dir) throws IOException {
        File[] files = dir.listFiles();
        files = removeWhiteList(files);
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteContents(file);
                }
                assertTrue(file.delete());
            }

            File[] dirs = removeWhiteList(dir.listFiles());
            if (dirs.length != 0) {
                fail("Expected wiped storage but found: " + Arrays.toString(dirs));
            }
        }
    }

    public static void writeInt(File file, int value) throws IOException {
        final DataOutputStream os = new DataOutputStream(new FileOutputStream(file));
        try {
            os.writeInt(value);
        } finally {
            os.close();
        }
    }

    public static int readInt(File file) throws IOException {
        final DataInputStream is = new DataInputStream(new FileInputStream(file));
        try {
            return is.readInt();
        } finally {
            is.close();
        }
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
