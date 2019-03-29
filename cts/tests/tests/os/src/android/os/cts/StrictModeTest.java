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

package android.os.cts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.StrictMode;
import android.os.StrictMode.ViolationListener;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link StrictMode}
 */
public class StrictModeTest extends InstrumentationTestCase {
    private static final String TAG = "StrictModeTest";

    private StrictMode.ThreadPolicy mThreadPolicy;
    private StrictMode.VmPolicy mVmPolicy;

    private Context getContext() {
        return getInstrumentation().getContext();
    }

    @Override
    protected void setUp() {
        mThreadPolicy = StrictMode.getThreadPolicy();
        mVmPolicy = StrictMode.getVmPolicy();
    }

    @Override
    protected void tearDown() {
        StrictMode.setThreadPolicy(mThreadPolicy);
        StrictMode.setVmPolicy(mVmPolicy);
    }

    public interface ThrowingRunnable {
        public void run() throws Exception;
    }

    /**
     * Insecure connection should be detected
     */
    public void testCleartextNetwork() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testCleartextNetwork() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectCleartextNetwork()
                .penaltyLog()
                .build());

        assertViolation("Detected cleartext network traffic from UID", () -> {
            ((HttpURLConnection) new URL("http://example.com/").openConnection())
                    .getResponseCode();
        });
    }

    /**
     * Secure connection should be ignored
     */
    public void testEncryptedNetwork() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testEncryptedNetwork() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectCleartextNetwork()
                .penaltyLog()
                .build());

        assertNoViolation(() -> {
            ((HttpURLConnection) new URL("https://example.com/").openConnection())
                    .getResponseCode();
        });
    }

    public void testFileUriExposure() throws Exception {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectFileUriExposure()
                .penaltyLog()
                .build());

        final Uri badUri = Uri.fromFile(new File("/sdcard/meow.jpg"));
        assertViolation(badUri + " exposed beyond app", () -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(badUri, "image/jpeg");
            getContext().startActivity(intent);
        });

        final Uri goodUri = Uri.parse("content://com.example/foobar");
        assertNoViolation(() -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(goodUri, "image/jpeg");
            getContext().startActivity(intent);
        });
    }

    public void testContentUriWithoutPermission() throws Exception {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectContentUriWithoutPermission()
                .penaltyLog()
                .build());

        final Uri uri = Uri.parse("content://com.example/foobar");
        assertViolation(uri + " exposed beyond app", () -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "image/jpeg");
            getContext().startActivity(intent);
        });

        assertNoViolation(() -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setDataAndType(uri, "image/jpeg");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getContext().startActivity(intent);
        });
    }

    public void testUntaggedSocketsHttp() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testUntaggedSockets() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectUntaggedSockets()
                .penaltyLog()
                .build());

        assertViolation("Untagged socket detected", () -> {
            ((HttpURLConnection) new URL("http://example.com/").openConnection())
                    .getResponseCode();
        });

        assertNoViolation(() -> {
            TrafficStats.setThreadStatsTag(0xDECAFBAD);
            try {
                ((HttpURLConnection) new URL("http://example.com/").openConnection())
                        .getResponseCode();
            } finally {
                TrafficStats.clearThreadStatsTag();
            }
        });
    }

    public void testUntaggedSocketsRaw() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testUntaggedSockets() ignored on device without Internet");
            return;
        }

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectUntaggedSockets()
                .penaltyLog()
                .build());

        assertNoViolation(() -> {
            TrafficStats.setThreadStatsTag(0xDECAFBAD);
            try (Socket socket = new Socket("example.com", 80)) {
                socket.getOutputStream().close();
            } finally {
                TrafficStats.clearThreadStatsTag();
            }
        });

        assertViolation("Untagged socket detected", () -> {
            try (Socket socket = new Socket("example.com", 80)) {
                socket.getOutputStream().close();
            }
        });
    }

    public void testRead() throws Exception {
        final File test = File.createTempFile("foo", "bar");
        final File dir = test.getParentFile();

        final FileInputStream is = new FileInputStream(test);
        final FileDescriptor fd = Os.open(test.getAbsolutePath(), OsConstants.O_RDONLY, 0600);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .penaltyLog()
                .build());

        assertViolation("StrictModeDiskReadViolation", () -> {
            test.exists();
        });
        assertViolation("StrictModeDiskReadViolation", () -> {
            test.length();
        });
        assertViolation("StrictModeDiskReadViolation", () -> {
            dir.list();
        });
        assertViolation("StrictModeDiskReadViolation", () -> {
            new FileInputStream(test);
        });
        assertViolation("StrictModeDiskReadViolation", () -> {
            is.read();
        });
        assertViolation("StrictModeDiskReadViolation", () -> {
            Os.open(test.getAbsolutePath(), OsConstants.O_RDONLY, 0600);
        });
        assertViolation("StrictModeDiskReadViolation", () -> {
            Os.read(fd, new byte[10], 0, 1);
        });
    }

    public void testWrite() throws Exception {
        File file = File.createTempFile("foo", "bar");

        final FileOutputStream os = new FileOutputStream(file);
        final FileDescriptor fd = Os.open(file.getAbsolutePath(), OsConstants.O_RDWR, 0600);

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskWrites()
                .penaltyLog()
                .build());

        assertViolation("StrictModeDiskWriteViolation", () -> {
            File.createTempFile("foo", "bar");
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            file.delete();
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            file.createNewFile();
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            new FileOutputStream(file);
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            os.write(32);
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            Os.open(file.getAbsolutePath(), OsConstants.O_RDWR, 0600);
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            Os.write(fd, new byte[10], 0, 1);
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            Os.fsync(fd);
        });
        assertViolation("StrictModeDiskWriteViolation", () -> {
            file.renameTo(new File(file.getParent(), "foobar"));
        });
    }

    public void testNetwork() throws Exception {
        if (!hasInternetConnection()) {
            Log.i(TAG, "testUntaggedSockets() ignored on device without Internet");
            return;
        }

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyLog()
                .build());

        assertViolation("StrictModeNetworkViolation", () -> {
            try (Socket socket = new Socket("example.com", 80)) {
                socket.getOutputStream().close();
            }
        });

        assertViolation("StrictModeNetworkViolation", () -> {
            ((HttpURLConnection) new URL("http://example.com/").openConnection())
                    .getResponseCode();
        });
    }

    private static void assertViolation(String expected, ThrowingRunnable r) throws Exception {
        final LinkedBlockingQueue<String> violations = new LinkedBlockingQueue<>();
        StrictMode.setViolationListener(new ViolationListener() {
            @Override
            public void onViolation(String message) {
                violations.add(message);
            }
        });

        try {
            r.run();
            while (true) {
                final String violation = violations.poll(5, TimeUnit.SECONDS);
                if (violation == null) {
                    fail("Expected violation not found: " + expected);
                } else if (violation.contains(expected)) {
                    return;
                }
            }
        } finally {
            StrictMode.setViolationListener(null);
        }
    }

    private static void assertNoViolation(ThrowingRunnable r) throws Exception {
        final LinkedBlockingQueue<String> violations = new LinkedBlockingQueue<>();
        StrictMode.setViolationListener(new ViolationListener() {
            @Override
            public void onViolation(String message) {
                violations.add(message);
            }
        });

        try {
            r.run();
            while (true) {
                final String violation = violations.poll(5, TimeUnit.SECONDS);
                if (violation == null) {
                    return;
                } else {
                    fail("Unexpected violation found: " + violation);
                }
            }
        } finally {
            StrictMode.setViolationListener(null);
        }
    }

    private boolean hasInternetConnection() {
        final PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || pm.hasSystemFeature(PackageManager.FEATURE_WIFI)
                || pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET);
    }
}
