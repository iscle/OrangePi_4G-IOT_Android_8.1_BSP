/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.provider.cts;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for provider cts tests.
 */
public class ProviderTestUtils {

    private static final int BACKUP_TIMEOUT_MILLIS = 4000;
    private static final Pattern BMGR_ENABLED_PATTERN = Pattern.compile(
            "^Backup Manager currently (enabled|disabled)$");

    static void setDefaultSmsApp(boolean setToSmsApp, String packageName, UiAutomation uiAutomation)
            throws Exception {
        String command = String.format(
                "appops set %s WRITE_SMS %s", packageName, setToSmsApp ? "allow" : "default");
        executeShellCommand(command, uiAutomation);
    }

    static String executeShellCommand(String command, UiAutomation uiAutomation)
            throws IOException {
        ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(command.toString());
        BufferedReader br = null;
        try (InputStream in = new FileInputStream(pfd.getFileDescriptor());) {
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String str = null;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    static String setBackupTransport(String transport, UiAutomation uiAutomation) throws Exception {
        String output = executeShellCommand("bmgr transport " + transport, uiAutomation);
        Pattern pattern = Pattern.compile("\\(formerly (.*)\\)$");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new Exception("non-parsable output setting bmgr transport: " + output);
        }
    }

    static boolean setBackupEnabled(boolean enable, UiAutomation uiAutomation) throws Exception {
        // Check to see the previous state of the backup service
        boolean previouslyEnabled = false;
        String output = executeShellCommand("bmgr enabled", uiAutomation);
        Matcher matcher = BMGR_ENABLED_PATTERN.matcher(output.trim());
        if (matcher.find()) {
            previouslyEnabled = "enabled".equals(matcher.group(1));
        } else {
            throw new RuntimeException("Backup output format changed.  No longer matches"
                    + " expected regex: " + BMGR_ENABLED_PATTERN + "\nactual: '" + output + "'");
        }

        executeShellCommand("bmgr enable " + enable, uiAutomation);
        return previouslyEnabled;
    }

    static boolean hasBackupTransport(String transport, UiAutomation uiAutomation)
            throws Exception {
        String output = executeShellCommand("bmgr list transports", uiAutomation);
        for (String t : output.split(" ")) {
            if ("*".equals(t)) {
                // skip the current selection marker.
                continue;
            } else if (Objects.equals(transport, t)) {
                return true;
            }
        }
        return false;
    }

    static void runBackup(String packageName, UiAutomation uiAutomation) throws Exception {
        executeShellCommand("bmgr backupnow " + packageName, uiAutomation);
        Thread.sleep(BACKUP_TIMEOUT_MILLIS);
    }

    static void runRestore(String packageName, UiAutomation uiAutomation) throws Exception {
        executeShellCommand("bmgr restore 1 " + packageName, uiAutomation);
        Thread.sleep(BACKUP_TIMEOUT_MILLIS);
    }

    static void wipeBackup(String backupTransport, String packageName, UiAutomation uiAutomation)
            throws Exception {
        executeShellCommand("bmgr wipe " + backupTransport + " " + packageName, uiAutomation);
    }
}
