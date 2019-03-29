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
 * limitations under the License
 */

package android.backup.cts;

import android.app.Instrumentation;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Base class for backup instrumentation tests.
 *
 * Ensures that backup is enabled and local transport selected, and provides some utility methods.
 */
public class BaseBackupCtsTest extends InstrumentationTestCase {
    private static final String APP_LOG_TAG = "BackupCTSApp";

    private static final String LOCAL_TRANSPORT =
            "android/com.android.internal.backup.LocalTransport";

    private static final int SMALL_LOGCAT_DELAY = 1000;

    private boolean isBackupSupported;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        PackageManager packageManager = getInstrumentation().getContext().getPackageManager();
        isBackupSupported = packageManager != null
                && packageManager.hasSystemFeature(PackageManager.FEATURE_BACKUP);

        if (isBackupSupported) {
            assertTrue("Backup not enabled", isBackupEnabled());
            assertTrue("LocalTransport not selected", isLocalTransportSelected());
            exec("setprop log.tag." + APP_LOG_TAG +" VERBOSE");
        }
    }

    public boolean isBackupSupported() {
        return isBackupSupported;
    }

    private boolean isBackupEnabled() throws Exception {
        String output = exec("bmgr enabled");
        return output.contains("currently enabled");
    }

    private boolean isLocalTransportSelected() throws Exception {
        String output = exec("bmgr list transports");
        return output.contains("* " + LOCAL_TRANSPORT);
    }

    /**
     * Attempts to clear logcat.
     *
     * Clearing logcat is known to be unreliable, so this methods also output a unique separator
     * that can be used to find this point in the log even if clearing failed.
     * @return a unique separator string
     * @throws Exception
     */
    protected String clearLogcat() throws Exception {
        exec("logcat -c");
        String uniqueString = ":::" + UUID.randomUUID().toString();
        exec("log -t " + APP_LOG_TAG + " " + uniqueString);
        return uniqueString;
    }

    /**
     * Wait for up to maxTimeoutInSeconds for the given strings to appear in the logcat in the given order.
     * By passing the separator returned by {@link #clearLogcat} as the first string you can ensure that only
     * logs emitted after that call to clearLogcat are found.
     *
     * @throws AssertionError if the strings are not found in the given time.
     */
    protected void waitForLogcat(int maxTimeoutInSeconds, String... logcatStrings)
        throws Exception {
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(maxTimeoutInSeconds);
        int stringIndex = 0;
        while (timeout >= System.currentTimeMillis()) {
            FileInputStream fis = executeStreamedShellCommand(getInstrumentation(),
                    "logcat -v brief -d " + APP_LOG_TAG + ":* *:S");
            BufferedReader log = new BufferedReader(new InputStreamReader(fis));
            String line;
            stringIndex = 0;
            while ((line = log.readLine()) != null) {
                if (line.contains(logcatStrings[stringIndex])) {
                    stringIndex++;
                    if (stringIndex >= logcatStrings.length) {
                        drainAndClose(log);
                        return;
                    }
                }
            }
            closeQuietly(log);
            // In case the key has not been found, wait for the log to update before
            // performing the next search.
            Thread.sleep(SMALL_LOGCAT_DELAY);
        }
        fail("Couldn't find " + logcatStrings[stringIndex] +
            (stringIndex > 0 ? " after " + logcatStrings[stringIndex - 1] : "") +
            " within " + maxTimeoutInSeconds + " seconds ");
    }

    protected void createTestFileOfSize(String packageName, int size) throws Exception {
        exec("am start -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER " +
            "-n " + packageName + "/android.backup.app.MainActivity " +
            "-e file_size " + size);
        waitForLogcat(30, "File created!");
    }

    protected String exec(String command) throws Exception {
        try (InputStream in = executeStreamedShellCommand(getInstrumentation(), command)) {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String str;
            StringBuilder out = new StringBuilder();
            while ((str = br.readLine()) != null) {
                out.append(str);
            }
            return out.toString();
        }
    }

    private static FileInputStream executeStreamedShellCommand(Instrumentation instrumentation,
                                                               String command) throws Exception {
        final ParcelFileDescriptor pfd =
                instrumentation.getUiAutomation().executeShellCommand(command);
        return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
    }

    private static void drainAndClose(BufferedReader reader) {
        try {
            while (reader.read() >= 0) {
                // do nothing.
            }
        } catch (IOException ignored) {
        }
        closeQuietly(reader);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }
}
