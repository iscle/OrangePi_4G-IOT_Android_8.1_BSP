/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.testtype.CompatibilityHostTestBase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Tests that exercise various storage APIs.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class StorageHostTest extends CompatibilityHostTestBase {
    private static final String PKG_STATS = "com.android.cts.storagestatsapp";
    private static final String PKG_A = "com.android.cts.storageapp_a";
    private static final String PKG_B = "com.android.cts.storageapp_b";
    private static final String APK_STATS = "CtsStorageStatsApp.apk";
    private static final String APK_A = "CtsStorageAppA.apk";
    private static final String APK_B = "CtsStorageAppB.apk";
    private static final String CLASS_STATS = "com.android.cts.storagestatsapp.StorageStatsTest";
    private static final String CLASS = "com.android.cts.storageapp.StorageTest";

    private int[] mUsers;

    @Before
    public void setUp() throws Exception {
        mUsers = Utils.prepareMultipleUsers(getDevice());

        installPackage(APK_STATS);
        installPackage(APK_A);
        installPackage(APK_B);

        for (int user : mUsers) {
            getDevice().executeShellCommand("appops set --user " + user + " " + PKG_STATS
                    + " android:get_usage_stats allow");
        }

        waitForIdle();
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(PKG_STATS);
        getDevice().uninstallPackage(PKG_A);
        getDevice().uninstallPackage(PKG_B);
    }

    @Test
    public void testVerifyQuota() throws Exception {
        Utils.runDeviceTests(getDevice(), PKG_STATS, CLASS_STATS, "testVerifyQuota");
    }

    @Test
    public void testVerifyAppStats() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);
        }

        // TODO: remove this once 34723223 is fixed
        getDevice().executeShellCommand("sync");

        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifySpaceManual", user);
            runDeviceTests(PKG_A, CLASS, "testVerifySpaceApi", user);
        }
    }

    @Test
    public void testVerifyAppQuota() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifyQuotaApi", user);
        }
    }

    @Test
    public void testVerifyAppAllocate() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifyAllocateApi", user);
        }
    }

    @Test
    public void testVerifySummary() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifySummary", user);
        }
    }

    @Test
    public void testVerifyStats() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStats", user);
        }
    }

    @Test
    public void testVerifyStatsMultiple() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);

            runDeviceTests(PKG_B, CLASS, "testAllocate", user);
        }

        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsMultiple", user);
        }
    }

    @Test
    public void testVerifyStatsExternal() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsExternal", user);
        }
    }

    @Test
    public void testVerifyStatsExternalConsistent() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsExternalConsistent", user);
        }
    }

    @Test
    public void testVerifyCategory() throws Exception {
        for (int user : mUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyCategory", user);
        }
    }

    @Test
    public void testCache() throws Exception {
        // To make the cache clearing logic easier to verify, ignore any cache
        // and low space reserved space.
        getDevice().executeShellCommand("settings put global sys_storage_threshold_max_bytes 0");
        getDevice().executeShellCommand("settings put global sys_storage_cache_max_bytes 0");
        getDevice().executeShellCommand("svc data disable");
        getDevice().executeShellCommand("svc wifi disable");
        try {
            waitForIdle();
            for (int user : mUsers) {
                // Clear all other cached data to give ourselves a clean slate
                getDevice().executeShellCommand("pm trim-caches 4096G");
                runDeviceTests(PKG_STATS, CLASS_STATS, "testCacheClearing", user);

                getDevice().executeShellCommand("pm trim-caches 4096G");
                runDeviceTests(PKG_STATS, CLASS_STATS, "testCacheBehavior", user);
            }
        } finally {
            getDevice().executeShellCommand("settings delete global sys_storage_threshold_max_bytes");
            getDevice().executeShellCommand("settings delete global sys_storage_cache_max_bytes");
            getDevice().executeShellCommand("svc data enable");
            getDevice().executeShellCommand("svc wifi enable");
        }
    }

    @Test
    public void testFullDisk() throws Exception {
        // Clear all other cached and external storage data to give ourselves a
        // clean slate to test against
        getDevice().executeShellCommand("pm trim-caches 4096G");
        getDevice().executeShellCommand("rm -rf /sdcard/*");

        // We're interested in any crashes while disk full
        final String lastEvent = getDevice().executeShellCommand("logcat -d -b events -t 1");
        final String sinceTime = lastEvent.trim().substring(0, 18);

        try {
            // Try our hardest to fill up the entire disk
            Utils.runDeviceTests(getDevice(), PKG_A, CLASS, "testFullDisk");
        } catch (Throwable t) {
            // If we had trouble filling the disk, don't bother going any
            // further; we failed because we either don't have quota support, or
            // because disk was more than 10% full.
            return;
        }

        // Tweak something that causes PackageManager to persist data
        Utils.runDeviceTests(getDevice(), PKG_A, CLASS, "testTweakComponent");

        // Try poking around a couple of settings apps
        getDevice().executeShellCommand("input keyevent KEY_HOME");
        Thread.sleep(1000);
        getDevice().executeShellCommand("am start -a android.settings.SETTINGS");
        Thread.sleep(2000);
        getDevice().executeShellCommand("input keyevent KEY_BACK");
        Thread.sleep(1000);
        getDevice().executeShellCommand("am start -a android.os.storage.action.MANAGE_STORAGE");
        Thread.sleep(2000);
        getDevice().executeShellCommand("input keyevent KEY_BACK");
        Thread.sleep(1000);

        // Our misbehaving app above shouldn't have caused anything else to
        // think the disk was full
        String troubleLogs = getDevice().executeShellCommand(
                "logcat -d -t '" + sinceTime + "' -e '(ENOSPC|No space left on device)'");

        if (troubleLogs == null) troubleLogs = "";
        troubleLogs = troubleLogs.trim().replaceAll("\\-+ beginning of [a-z]+", "");

        if (troubleLogs.length() > 4) {
            throw new AssertionFailedError("Unexpected crashes while disk full: " + troubleLogs);
        }
    }

    public void waitForIdle() throws Exception {
        // Try getting all pending events flushed out
        for (int i = 0; i < 4; i++) {
            getDevice().executeShellCommand("am wait-for-broadcast-idle");
            Thread.sleep(500);
        }
    }

    public void runDeviceTests(String packageName, String testClassName, String testMethodName,
            int userId) throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName, userId, null,
                20L, TimeUnit.MINUTES);
    }
}
