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
import static org.junit.Assert.fail;

import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertDirReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertMediaNoAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.assertMediaReadWriteAccess;
import static com.android.cts.externalstorageapp.CommonExternalStorageTest.logCommand;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Environment;
import org.junit.Before;
import org.junit.Test;

/**
 * Runtime permission behavior tests for apps targeting API 23
 */
public class UsePermissionTest23 extends BasePermissionsTest {
    private static final int REQUEST_CODE_PERMISSIONS = 42;

    private boolean mLeanback;
    private boolean mWatch;

    @Before
    public void initialize() {
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        mLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        mWatch = pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    public void testFail() throws Exception {
        fail("Expected");
    }

    @Test
    public void testKill() throws Exception {
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Test
    public void testDefault() throws Exception {
        logCommand("/system/bin/cat", "/proc/self/mountinfo");

        // New permission model is denied by default
        assertAllPermissionsRevoked();

        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
        assertDirNoAccess(Environment.getExternalStorageDirectory());
        assertDirReadWriteAccess(getInstrumentation().getContext().getExternalCacheDir());
        assertMediaNoAccess(getInstrumentation().getContext().getContentResolver(), false);
    }

    @Test
    public void testGranted() throws Exception {
        logCommand("/system/bin/cat", "/proc/self/mountinfo");
        grantPermission(Manifest.permission.READ_EXTERNAL_STORAGE);
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
        assertDirReadWriteAccess(Environment.getExternalStorageDirectory());
        assertDirReadWriteAccess(getInstrumentation().getContext().getExternalCacheDir());
        assertMediaReadWriteAccess(getInstrumentation().getContext().getContentResolver());
    }

    @Test
    public void testInteractiveGrant() throws Exception {
        logCommand("/system/bin/cat", "/proc/self/mountinfo");

        // Start out without permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE));
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
        assertDirNoAccess(Environment.getExternalStorageDirectory());
        assertDirReadWriteAccess(getInstrumentation().getContext().getExternalCacheDir());
        assertMediaNoAccess(getInstrumentation().getContext().getContentResolver(), false);

        // Go through normal grant flow
        BasePermissionActivity.Result result = requestPermissions(new String[] {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class,
                () -> {
                    try {
                        clickAllowButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        assertEquals(REQUEST_CODE_PERMISSIONS, result.requestCode);
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, result.permissions[0]);
        assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, result.permissions[1]);
        assertEquals(PackageManager.PERMISSION_GRANTED, result.grantResults[0]);
        assertEquals(PackageManager.PERMISSION_GRANTED, result.grantResults[1]);

        logCommand("/system/bin/cat", "/proc/self/mountinfo");

        // We should have permission now!
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
        assertDirReadWriteAccess(Environment.getExternalStorageDirectory());
        assertDirReadWriteAccess(getInstrumentation().getContext().getExternalCacheDir());
        assertMediaReadWriteAccess(getInstrumentation().getContext().getContentResolver());
    }

    @Test
    public void testRuntimeGroupGrantSpecificity() throws Exception {
        // Start out without permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CONTACTS));
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CONTACTS));

        String[] permissions = new String[] {Manifest.permission.WRITE_CONTACTS};

        // request only one permission from the 'contacts' permission group
        BasePermissionActivity.Result result = requestPermissions(permissions,
                REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class,
                () -> {
                    try {
                        clickAllowButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is granted
        assertPermissionRequestResult(result, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {true});

        // Make sure no undeclared as used permissions are granted
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CONTACTS));
    }

    @Test
    public void testRuntimeGroupGrantExpansion() throws Exception {
        // Start out without permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.RECEIVE_SMS));
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.SEND_SMS));

        String[] permissions = new String[] {Manifest.permission.RECEIVE_SMS};

        // request only one permission from the 'SMS' permission group at runtime,
        // but two from this group are <uses-permission> in the manifest
        // request only one permission from the 'contacts' permission group
        BasePermissionActivity.Result result = requestPermissions(permissions,
                REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class,
                () -> {
                    try {
                        clickAllowButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is granted
        assertPermissionRequestResult(result, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {true});

        // We should now have been granted both of the permissions from this group.
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.SEND_SMS));
    }

    @Test
    public void testCancelledPermissionRequest() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CONTACTS));

        String[] permissions = new String[] {Manifest.permission.WRITE_CONTACTS};

        // Request the permission and cancel the request
        BasePermissionActivity.Result result = requestPermissions(permissions,
                REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class,
                () -> {
                    try {
                        clickDenyButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is not granted
        assertPermissionRequestResult(result, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {false});
    }

    @Test
    public void testRequestGrantedPermission() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CONTACTS));

        String[] permissions = new String[] {Manifest.permission.WRITE_CONTACTS};

        // Request the permission and allow it
        BasePermissionActivity.Result firstResult = requestPermissions(permissions,
                REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class, () -> {
                    try {
                        clickAllowButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is granted
        assertPermissionRequestResult(firstResult, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {true});

        // Request the permission and do nothing
        BasePermissionActivity.Result secondResult = requestPermissions(new String[] {
                Manifest.permission.WRITE_CONTACTS}, REQUEST_CODE_PERMISSIONS + 1,
                BasePermissionActivity.class, null);

        // Expect the permission is granted
        assertPermissionRequestResult(secondResult, REQUEST_CODE_PERMISSIONS + 1,
                permissions, new boolean[] {true});
    }

    @Test
    public void testDenialWithPrejudice() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CONTACTS));

        String[] permissions = new String[] {Manifest.permission.WRITE_CONTACTS};

        // Request the permission and deny it
        BasePermissionActivity.Result firstResult = requestPermissions(
                permissions, REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class, () -> {
                    try {
                        clickDenyButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is not granted
        assertPermissionRequestResult(firstResult, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {false});

        // Request the permission and choose don't ask again
        BasePermissionActivity.Result secondResult = requestPermissions(new String[] {
                        Manifest.permission.WRITE_CONTACTS}, REQUEST_CODE_PERMISSIONS + 1,
                BasePermissionActivity.class, () -> {
                    try {
                        denyWithPrejudice();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is not granted
        assertPermissionRequestResult(secondResult, REQUEST_CODE_PERMISSIONS + 1,
                permissions, new boolean[] {false});

        // Request the permission and do nothing
        BasePermissionActivity.Result thirdResult = requestPermissions(new String[] {
                        Manifest.permission.WRITE_CONTACTS}, REQUEST_CODE_PERMISSIONS + 2,
                BasePermissionActivity.class, null);

        // Expect the permission is not granted
        assertPermissionRequestResult(thirdResult, REQUEST_CODE_PERMISSIONS + 2,
                permissions, new boolean[] {false});
    }

    @Test
    public void testRevokeAffectsWholeGroup_part1() throws Exception {
        // Grant the group
        grantPermission(Manifest.permission.READ_CALENDAR);

        // Make sure we have the permissions
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CALENDAR));
        assertEquals(PackageManager.PERMISSION_GRANTED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CALENDAR));

        // Revoke the group
        revokePermission(Manifest.permission.READ_CALENDAR);

        // We just committed a suicide by revoking the permission. See part2 below...
    }

    @Test
    public void testRevokeAffectsWholeGroup_part2() throws Exception {
        // Make sure we don't have the permissions
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CALENDAR));
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CALENDAR));
    }

    @Test
    public void testGrantPreviouslyRevokedWithPrejudiceShowsPrompt_part1() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CALENDAR));

        String[] permissions = new String[] {Manifest.permission.READ_CALENDAR};

        // Request the permission and deny it
        BasePermissionActivity.Result firstResult = requestPermissions(
                permissions, REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class, () -> {
                    try {
                        clickDenyButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is not granted
        assertPermissionRequestResult(firstResult, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {false});

        // Request the permission and choose don't ask again
        BasePermissionActivity.Result secondResult = requestPermissions(new String[] {
                        Manifest.permission.READ_CALENDAR}, REQUEST_CODE_PERMISSIONS + 1,
                BasePermissionActivity.class, () -> {
                    try {
                        denyWithPrejudice();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Expect the permission is not granted
        assertPermissionRequestResult(secondResult, REQUEST_CODE_PERMISSIONS + 1,
                permissions, new boolean[] {false});

        // Clear the denial with prejudice
        grantPermission(Manifest.permission.READ_CALENDAR);
        revokePermission(Manifest.permission.READ_CALENDAR);

        // We just committed a suicide by revoking the permission. See part2 below...
    }

    @Test
    public void testGrantPreviouslyRevokedWithPrejudiceShowsPrompt_part2() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CALENDAR));

        // Request the permission and allow it
        BasePermissionActivity.Result thirdResult = requestPermissions(new String[] {
                        Manifest.permission.READ_CALENDAR}, REQUEST_CODE_PERMISSIONS + 2,
                BasePermissionActivity.class, () -> {
                    try {
                        clickAllowButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Make sure the permission is granted
        assertPermissionRequestResult(thirdResult, REQUEST_CODE_PERMISSIONS + 2,
                new String[] {Manifest.permission.READ_CALENDAR}, new boolean[] {true});
    }

    @Test
    public void testRequestNonRuntimePermission() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.BIND_PRINT_SERVICE));

        String[] permissions = new String[] {Manifest.permission.BIND_PRINT_SERVICE};

        // Request the permission and do nothing
        BasePermissionActivity.Result result = requestPermissions(permissions,
                REQUEST_CODE_PERMISSIONS, BasePermissionActivity.class, null);

        // Expect the permission is not granted
        assertPermissionRequestResult(result, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {false});
    }

    @Test
    public void testRequestNonExistentPermission() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission("permission.does.not.exist"));

        String[] permissions = new String[] {"permission.does.not.exist"};

        // Request the permission and do nothing
        BasePermissionActivity.Result result = requestPermissions(permissions,
                REQUEST_CODE_PERMISSIONS, BasePermissionActivity.class, null);

        // Expect the permission is not granted
        assertPermissionRequestResult(result, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {false});
    }

    @Test
    public void testRequestPermissionFromTwoGroups() throws Exception {
        // Make sure we don't have the permissions
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CONTACTS));
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.WRITE_CALENDAR));

        String[] permissions = new String[] {
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.WRITE_CALENDAR
        };

        // Request the permission and do nothing
        BasePermissionActivity.Result result = requestPermissions(permissions,
                REQUEST_CODE_PERMISSIONS, BasePermissionActivity.class, () -> {
            try {
                clickAllowButton();
                getUiDevice().waitForIdle();
                clickAllowButton();
                getUiDevice().waitForIdle();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Expect the permission is not granted
        assertPermissionRequestResult(result, REQUEST_CODE_PERMISSIONS,
                permissions, new boolean[] {true, true});
    }

    @Test
    public void testNoResidualPermissionsOnUninstall_part1() throws Exception {
        // Grant all permissions
        grantPermissions(new String[] {
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_SMS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
        });
    }

    @Test
    public void testNoResidualPermissionsOnUninstall_part2() throws Exception {
        // Make no permissions are granted after uninstalling and installing the app
        assertAllPermissionsRevoked();
    }

    @Test
    public void testRevokePropagatedOnUpgradeOldToNewModel_part2() throws Exception {
        assertPermissionsGrantState(new String[] {Manifest.permission.WRITE_CALENDAR},
                PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void testRevokePropagatedOnUpgradeNewToNewModel_part1() throws Exception {
        // Make sure we don't have the permission
        assertEquals(PackageManager.PERMISSION_DENIED, getInstrumentation().getContext()
                .checkSelfPermission(Manifest.permission.READ_CALENDAR));

        // Request the permission and allow it
        BasePermissionActivity.Result thirdResult = requestPermissions(new String[] {
                        Manifest.permission.READ_CALENDAR}, REQUEST_CODE_PERMISSIONS,
                BasePermissionActivity.class, () -> {
                    try {
                        clickAllowButton();
                        getUiDevice().waitForIdle();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        // Make sure the permission is granted
        assertPermissionRequestResult(thirdResult, REQUEST_CODE_PERMISSIONS,
                new String[] {Manifest.permission.READ_CALENDAR}, new boolean[] {true});
    }

    @Test
    public void testRevokePropagatedOnUpgradeNewToNewModel_part2() throws Exception {
        // Make sure the permission is still granted after the upgrade
        assertPermissionsGrantState(new String[] {Manifest.permission.READ_CALENDAR},
                PackageManager.PERMISSION_GRANTED);
        // Also make sure one of the not granted permissions is still not granted
        assertPermissionsGrantState(new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                PackageManager.PERMISSION_DENIED);
    }

    @Test
    public void testAllPermissionsGrantedOnUpgrade() throws Exception {
        assertAllPermissionsGrantState(PackageManager.PERMISSION_GRANTED);
    }

    private void assertAllPermissionsRevoked() {
        assertAllPermissionsGrantState(PackageManager.PERMISSION_DENIED);
    }

    private void assertAllPermissionsGrantState(int grantState) {
        assertPermissionsGrantState(new String[] {
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.RECEIVE_WAP_PUSH,
                Manifest.permission.RECEIVE_MMS,
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.WRITE_CONTACTS,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.WRITE_CALL_LOG,
                Manifest.permission.ADD_VOICEMAIL,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.USE_SIP,
                Manifest.permission.PROCESS_OUTGOING_CALLS,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA,
                Manifest.permission.BODY_SENSORS,
                "android.permission.READ_CELL_BROADCASTS"
        }, grantState);
    }

    private void assertPermissionsGrantState(String[] permissions, int grantState) {
        for (String permission : permissions) {
            assertEquals(grantState, getInstrumentation().getContext()
                    .checkSelfPermission(permission));
        }
    }

    private void denyWithPrejudice() throws Exception {
        if (mLeanback || mWatch) {
            clickDontAskAgainButton();
        } else {
            clickDontAskAgainCheckbox();
            clickDenyButton();
        }
    }
}
