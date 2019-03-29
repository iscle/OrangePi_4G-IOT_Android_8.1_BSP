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

package android.permission.cts;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Verify the read system log require specific permissions.
 */
public class NoReadLogsPermissionTest extends AndroidTestCase {
    /**
     * Verify that we'll only get our logs without the READ_LOGS permission.
     *
     * We test this by examining the logs looking for ActivityManager lines.
     * Since ActivityManager runs as a different UID, we shouldn't see
     * any of those log entries.
     *
     * @throws IOException
     */
    @MediumTest
    public void testLogcat() throws IOException {
        Process logcatProc = null;
        BufferedReader reader = null;
        try {
            logcatProc = Runtime.getRuntime().exec(new String[]
                    {"logcat", "-v", "brief", "-d", "ActivityManager:* *:S" });

            reader = new BufferedReader(new InputStreamReader(logcatProc.getInputStream()));

            int lineCt = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("--------- beginning of ")) {
                    lineCt++;
                }
            }

            // no permission get an empty log buffer.
            // Logcat returns only one line:
            // "--------- beginning of <log device>"

            assertEquals("Unexpected logcat entries. Are you running the "
                       + "the latest logger.c from the Android kernel?",
                    0, lineCt);

        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public void testEventsLogSane() throws ErrnoException {
        testLogIsSane("/dev/log/events");
    }

    public void testMainLogSane() throws ErrnoException {
        testLogIsSane("/dev/log/main");
    }

    public void testRadioLogSane() throws ErrnoException {
        testLogIsSane("/dev/log/radio");
    }

    public void testSystemLogSane() throws ErrnoException {
        testLogIsSane("/dev/log/system");
    }

    private static void testLogIsSane(String log) throws ErrnoException {
        try {
            StructStat stat = Os.stat(log);
            assertEquals("not owned by uid=0", 0, stat.st_uid);
            assertEquals("not owned by gid=logs", "log", FileUtils.getGroupName(stat.st_gid));
        } catch (ErrnoException e) {
            if (e.errno != OsConstants.ENOENT && e.errno != OsConstants.EACCES) {
                throw e;
            }
        }
    }
}
