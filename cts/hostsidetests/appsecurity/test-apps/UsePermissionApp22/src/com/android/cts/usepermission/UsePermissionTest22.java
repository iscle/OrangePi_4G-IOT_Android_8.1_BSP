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

package com.android.cts.usepermission;

import static junit.framework.Assert.assertEquals;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertMediaNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertMediaReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.getAllPackageSpecificPaths;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.logCommand;
import static junit.framework.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Process;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

/**
 * Runtime permission behavior tests for apps targeting API 22
 */
public class UsePermissionTest22 extends BasePermissionsTest {
    private static final int REQUEST_CODE_PERMISSIONS = 42;

    @Test
    public void testCompatDefault() throws Exception {
        final Context context = getInstrumentation().getContext();
        logCommand("/system/bin/cat", "/proc/self/mountinfo");

        // Legacy permission model is granted by default
        assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        Process.myPid(), Process.myUid()));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Process.myPid(), Process.myUid()));
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
        assertDirReadWriteAccess(Environment.getExternalStorageDirectory());
        for (File path : getAllPackageSpecificPaths(context)) {
            if (path != null) {
                assertDirReadWriteAccess(path);
            }
        }
        assertMediaReadWriteAccess(getInstrumentation().getContext().getContentResolver());
    }

    @Test
    public void testCompatRevoked_part1() throws Exception {
        // Revoke the permission
        revokePermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, true);
    }

    @Test
    public void testCompatRevoked_part2() throws Exception {
        final Context context = getInstrumentation().getContext();
        logCommand("/system/bin/cat", "/proc/self/mountinfo");

        // Legacy permission model appears granted, but storage looks and
        // behaves like it's ejected
        assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        Process.myPid(), Process.myUid()));
        assertEquals(PackageManager.PERMISSION_GRANTED,
                context.checkPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Process.myPid(), Process.myUid()));
        assertEquals(Environment.MEDIA_UNMOUNTED, Environment.getExternalStorageState());

        assertDirNoAccess(Environment.getExternalStorageDirectory());
        for (File dir : getAllPackageSpecificPaths(context)) {
            if (dir != null) {
                assertDirNoAccess(dir);
            }
        }
        assertMediaNoAccess(getInstrumentation().getContext().getContentResolver(), true);

        // Just to be sure, poke explicit path
        assertDirNoAccess(new File(Environment.getExternalStorageDirectory(),
                "/Android/data/" + getInstrumentation().getContext().getPackageName()));
    }

    @Test
    public void testAllPermissionsGrantedByDefault() throws Exception {
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.SEND_SMS));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.RECEIVE_SMS));
        // The APK does not request because of other tests Manifest.permission.READ_CONTACTS
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CONTACTS));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CALENDAR));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CALENDAR));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_SMS));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.RECEIVE_WAP_PUSH));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.RECEIVE_MMS));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission("android.permission.READ_CELL_BROADCASTS"));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_PHONE_STATE));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.CALL_PHONE));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CALL_LOG));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CALL_LOG));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.ADD_VOICEMAIL));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.USE_SIP));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.PROCESS_OUTGOING_CALLS));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.CAMERA));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.BODY_SENSORS));
    }

    @Test
    public void testNoRuntimePrompt() throws Exception {
        // Request the permission and do nothing
        BasePermissionActivity.Result result = requestPermissions(
                new String[] {Manifest.permission.SEND_SMS}, REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class, null);

        // Expect the permission is not granted
        assertEquals(REQUEST_CODE_PERMISSIONS, result.requestCode);
        assertTrue(Arrays.equals(result.permissions, new String[0]));
        assertTrue(Arrays.equals(result.grantResults, new int[0]));
    }

    @Test
    public void testRevokePropagatedOnUpgradeOldToNewModel_part1() throws Exception {
        // Revoke a permission
        revokePermissions(new String[] {Manifest.permission.WRITE_CALENDAR}, true);
    }
}
