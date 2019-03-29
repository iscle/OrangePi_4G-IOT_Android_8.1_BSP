/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.os.cts;

import android.os.Environment;
import android.system.Os;
import android.system.StructStatVfs;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class EnvironmentTest extends TestCase {
    public void testEnvironment() {
        new Environment();
        assertNotNull(Environment.getExternalStorageState());
        assertTrue(Environment.getExternalStorageDirectory().isDirectory());
        assertTrue(Environment.getRootDirectory().isDirectory());
        assertTrue(Environment.getDownloadCacheDirectory().isDirectory());
        assertTrue(Environment.getDataDirectory().isDirectory());
    }

    /**
     * TMPDIR being set prevents apps from asking to have temporary files
     * placed in their own storage, instead forcing their location to
     * something OS-defined. If TMPDIR points to a global shared directory,
     * this could compromise the security of the files.
     */
    public void testNoTmpDir() {
        assertNull("environment variable TMPDIR should not be set",
                System.getenv("TMPDIR"));
    }

    /**
     * Verify that all writable block filesystems are mounted "noatime" to avoid
     * unnecessary flash churn.
     */
    public void testNoAtime() throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/mounts"))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String[] fields = line.split(" ");
                final String source = fields[0];
                final String options = fields[3];

                if (source.startsWith("/dev/block/") && !options.startsWith("ro,")
                        && !options.contains("noatime")) {
                    fail("Found device mounted at " + source + " without 'noatime' option, "
                            + "which can cause unnecessary flash churn; please update your fstab.");
                }
            }
        }
    }

    /**
     * Verify that all writable block filesystems are mounted with "resgid" to
     * mitigate disk-full trouble.
     */
    public void testSaneInodes() throws Exception {
        final File file = Environment.getDataDirectory();
        final StructStatVfs stat = Os.statvfs(file.getAbsolutePath());

        // By default ext4 creates one inode per 16KiB; we're okay with a much
        // wider range, but we want to make sure the device isn't going totally
        // crazy; too few inodes can result in system instability, and too many
        // inodes can result in wasted space.
        final long maxsize = stat.f_blocks * stat.f_frsize;
        final long maxInodes = maxsize / 4096;
        final long minsize = stat.f_bavail * stat.f_frsize;
        final long minInodes = minsize / 32768;

        if (stat.f_ffree >= minInodes && stat.f_ffree <= maxInodes) {
            // Sweet, sounds great!
        } else {
            fail("Number of inodes " + stat.f_ffree + " not within sane range for partition of "
                    + minsize + "," + maxsize + " bytes; expected [" + minInodes + "," + maxInodes + "]");
        }
    }
}
