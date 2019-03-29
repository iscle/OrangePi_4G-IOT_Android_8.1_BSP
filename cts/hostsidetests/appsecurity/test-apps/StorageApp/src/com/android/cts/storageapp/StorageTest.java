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

import static com.android.cts.storageapp.Utils.CACHE_ALL;
import static com.android.cts.storageapp.Utils.CACHE_EXT;
import static com.android.cts.storageapp.Utils.CACHE_INT;
import static com.android.cts.storageapp.Utils.DATA_EXT;
import static com.android.cts.storageapp.Utils.DATA_INT;
import static com.android.cts.storageapp.Utils.MB_IN_BYTES;
import static com.android.cts.storageapp.Utils.PKG_B;
import static com.android.cts.storageapp.Utils.assertMostlyEquals;
import static com.android.cts.storageapp.Utils.getSizeManual;
import static com.android.cts.storageapp.Utils.makeUniqueFile;
import static com.android.cts.storageapp.Utils.shouldHaveQuota;
import static com.android.cts.storageapp.Utils.useSpace;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.system.Os;
import android.test.InstrumentationTestCase;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Client app for verifying storage behaviors.
 */
public class StorageTest extends InstrumentationTestCase {
    private Context getContext() {
        return getInstrumentation().getContext();
    }

    public void testAllocate() throws Exception {
        useSpace(getContext());
    }

    public void testFullDisk() throws Exception {
        if (shouldHaveQuota(Os.uname())) {
            final File dataDir = getContext().getDataDir();

            // Pre-flight to see if we have enough disk space to test with
            final long total = dataDir.getTotalSpace();
            final long free = dataDir.getFreeSpace();
            final long required = ((total * 9) / 10) + MB_IN_BYTES;
            if (free < required) {
                fail("Skipping full disk test; only found " + free + " free out of " + total);
            }

            Hoarder.doBlocks(dataDir, true);
        } else {
            fail("Skipping full disk test due to missing quota support");
        }
    }

    public void testTweakComponent() throws Exception {
        getContext().getPackageManager().setComponentEnabledSetting(
                new ComponentName(getContext().getPackageName(), UtilsReceiver.class.getName()),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    /**
     * Measure ourselves manually.
     */
    public void testVerifySpaceManual() throws Exception {
        assertMostlyEquals(DATA_INT,
                getSizeManual(getContext().getDataDir()));
        assertMostlyEquals(DATA_EXT,
                getSizeManual(getContext().getExternalCacheDir().getParentFile()));
    }

    /**
     * Measure ourselves using platform APIs.
     */
    public void testVerifySpaceApi() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);

        final long cacheSize = sm.getCacheSizeBytes(
                sm.getUuidForPath(getContext().getCacheDir()));
        final long extCacheSize = sm.getCacheSizeBytes(
                sm.getUuidForPath(getContext().getExternalCacheDir()));
        if (cacheSize == extCacheSize) {
            assertMostlyEquals(CACHE_ALL, cacheSize);
        } else {
            assertMostlyEquals(CACHE_INT, cacheSize);
            assertMostlyEquals(CACHE_EXT, extCacheSize);
        }

        // Verify APIs that don't require any special permissions
        assertTrue(stats.getTotalBytes(StorageManager.UUID_DEFAULT) >= Environment
                .getDataDirectory().getTotalSpace());
        assertTrue(stats.getFreeBytes(StorageManager.UUID_DEFAULT) >= Environment
                .getDataDirectory().getUsableSpace());

        // Verify that we can see our own stats, and that they look sane
        ApplicationInfo ai = getContext().getApplicationInfo();
        final StorageStats pstats = stats.queryStatsForPackage(ai.storageUuid, ai.packageName,
                UserHandle.getUserHandleForUid(ai.uid));
        final StorageStats ustats = stats.queryStatsForUid(ai.storageUuid, ai.uid);
        assertEquals(cacheSize, pstats.getCacheBytes());
        assertEquals(cacheSize, ustats.getCacheBytes());

        // Verify that other packages are off-limits
        ai = getContext().getPackageManager().getApplicationInfo(PKG_B, 0);
        try {
            stats.queryStatsForPackage(ai.storageUuid, ai.packageName,
                    UserHandle.getUserHandleForUid(ai.uid));
            fail("Unexpected access");
        } catch (SecurityException expected) {
        }
        try {
            stats.queryStatsForUid(ai.storageUuid, ai.uid);
            fail("Unexpected access");
        } catch (SecurityException expected) {
        }
        try {
            stats.queryExternalStatsForUser(StorageManager.UUID_DEFAULT,
                    android.os.Process.myUserHandle());
            fail("Unexpected access");
        } catch (SecurityException expected) {
        }
    }

    public void testVerifyQuotaApi() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final long cacheSize = sm.getCacheQuotaBytes(
                sm.getUuidForPath(getContext().getCacheDir()));
        assertTrue("Apps must have at least 10MB quota", cacheSize > 10 * MB_IN_BYTES);
    }

    public void testVerifyAllocateApi() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File filesDir = getContext().getFilesDir();
        final File extDir = Environment.getExternalStorageDirectory();

        final UUID filesUuid = sm.getUuidForPath(filesDir);
        final UUID extUuid = sm.getUuidForPath(extDir);

        assertTrue("Apps must be able to allocate internal space",
                sm.getAllocatableBytes(filesUuid) > 10 * MB_IN_BYTES);
        assertTrue("Apps must be able to allocate external space",
                sm.getAllocatableBytes(extUuid) > 10 * MB_IN_BYTES);

        // Should always be able to allocate 1MB indirectly
        sm.allocateBytes(filesUuid, 1 * MB_IN_BYTES);

        // Should always be able to allocate 1MB directly
        final File filesFile = makeUniqueFile(filesDir);
        assertEquals(0L, filesFile.length());
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(filesFile,
                ParcelFileDescriptor.parseMode("rwt"))) {
            sm.allocateBytes(pfd.getFileDescriptor(), 1 * MB_IN_BYTES);
        }
        assertEquals(1 * MB_IN_BYTES, filesFile.length());
    }

    public void testBehaviorNormal() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File dir = makeUniqueFile(getContext().getCacheDir());
        dir.mkdir();
        assertFalse(sm.isCacheBehaviorGroup(dir));
        assertFalse(sm.isCacheBehaviorTombstone(dir));

        final File ext = makeUniqueFile(getContext().getExternalCacheDir());
        ext.mkdir();
        try { sm.isCacheBehaviorGroup(ext); fail(); } catch (IOException expected) { }
        try { sm.isCacheBehaviorTombstone(ext); fail(); } catch (IOException expected) { }
    }

    public void testBehaviorGroup() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File dir = makeUniqueFile(getContext().getCacheDir());
        dir.mkdir();
        sm.setCacheBehaviorGroup(dir, true);
        assertTrue(sm.isCacheBehaviorGroup(dir));

        final File ext = makeUniqueFile(getContext().getExternalCacheDir());
        ext.mkdir();
        try { sm.setCacheBehaviorGroup(ext, true); fail(); } catch (IOException expected) { }
        try { sm.setCacheBehaviorGroup(ext, false); fail(); } catch (IOException expected) { }
    }

    public void testBehaviorTombstone() throws Exception {
        final StorageManager sm = getContext().getSystemService(StorageManager.class);

        final File dir = makeUniqueFile(getContext().getCacheDir());
        dir.mkdir();
        sm.setCacheBehaviorTombstone(dir, true);
        assertTrue(sm.isCacheBehaviorTombstone(dir));

        final File ext = makeUniqueFile(getContext().getExternalCacheDir());
        ext.mkdir();
        try { sm.setCacheBehaviorTombstone(ext, true); fail(); } catch (IOException expected) { }
        try { sm.setCacheBehaviorTombstone(ext, false); fail(); } catch (IOException expected) { }
    }
}
