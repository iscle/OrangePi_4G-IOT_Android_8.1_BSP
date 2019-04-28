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
 * limitations under the License.
 */

package android.system.helpers;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;

/**
 * Implement common helper methods for permissions.
 */
public class PermissionHelper {
    public static final String TEST_TAG = "PermissionTest";
    public static final String SETTINGS_PACKAGE = "com.android.settings";
    public static final int REQUESTED_PERMISSION_FLAG_GRANTED = 3;
    public static final int REQUESTED_PERMISSION_FLAG_DENIED = 1;
    public final int TIMEOUT = 500;
    public static PermissionHelper mInstance = null;
    private UiDevice mDevice = null;
    private Context mContext = null;
    private static UiAutomation mUiAutomation = null;
    public static Hashtable<String, List<String>> mPermissionGroupInfo = null;
    ILauncherStrategy mLauncherStrategy = null;

    /** Supported operations on permission */
    public enum PermissionOp {
        GRANT, REVOKE;
    }

    /** Available permission status */
    public enum PermissionStatus {
        ON, OFF;
    }

    private PermissionHelper() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = InstrumentationRegistry.getTargetContext();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mLauncherStrategy = LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
    }

    public static PermissionHelper getInstance() {
        if (mInstance == null) {
            mInstance = new PermissionHelper();
            PermissionHelper.populateDangerousPermissionGroupInfo();
        }
        return mInstance;
    }

    /**
     * Populates a list of all dangerous permission of the system
     * Dangerous permissions are higher-risk permissoins that grant requesting applications
     * access to private user data or control over the device that can negatively impact
     * the user
     */
    private static void populateDangerousPermissionGroupInfo() {
        ParcelFileDescriptor pfd = mUiAutomation.executeShellCommand("pm list permissions -g -d");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(pfd.getFileDescriptor())))) {
            String line;
            List<String> permissions = new ArrayList<String>();
            String groupName = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("group")) {
                    if (mPermissionGroupInfo == null) {
                        mPermissionGroupInfo = new Hashtable<String, List<String>>();
                    } else {
                        mPermissionGroupInfo.put(groupName, permissions);
                        permissions = new ArrayList<String>();
                    }
                    groupName = line.split(":")[1];
                } else if (line.startsWith("  permission:")) {
                    permissions.add(line.split(":")[1]);
                }
            }
            mPermissionGroupInfo.put(groupName, permissions);
        } catch (IOException e) {
            Log.e(TEST_TAG, e.getMessage());
        }
    }

    /**
     * Returns list of granted/denied permission asked by package
     * @param packageName : PackageName for which permission list to be returned
     * @param permitted : set 'true' for normal and default granted dangerous permissions, 'false'
     * for permissions currently denied by package
     * @return
     */
    public List<String> getPermissionByPackage(String packageName, Boolean permitted) {
        List<String> selectedPermissions = new ArrayList<String>();
        String[] requestedPermissions = null;
        int[] requestedPermissionFlags = null;
        PackageInfo packageInfo = null;
        try {
            packageInfo = mContext.getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            throw new RuntimeException(String.format("%s package isn't found", packageName));
        }

        requestedPermissions = packageInfo.requestedPermissions;
        requestedPermissionFlags = packageInfo.requestedPermissionsFlags;
        for (int i = 0; i < requestedPermissions.length; ++i) {
            // requestedPermissionFlags 1 = Denied, 3 = Granted
            if (permitted && requestedPermissionFlags[i]
                == REQUESTED_PERMISSION_FLAG_GRANTED) {
                selectedPermissions.add(requestedPermissions[i]);
            } else if (!permitted && requestedPermissionFlags[i]
                == REQUESTED_PERMISSION_FLAG_DENIED) {
                selectedPermissions.add(requestedPermissions[i]);
            }
        }
        return selectedPermissions;
    }

    /**
     * Verify any dangerous permission not mentioned in manifest aren't granted
     * @param packageName
     * @param permittedGroups
     */
    public void verifyExtraDangerousPermissionNotGranted(String packageName,
            String[] permittedGroups) {
        List<String> allPermittedDangerousPermsList = getAllDangerousPermissionsByPermGrpNames(
                permittedGroups);
        List<String> allPermissionsForPackageList = getPermissionByPackage(packageName,
                Boolean.TRUE);
        List<String> allPlatformDangerousPermissionList = getPlatformDangerousPermissionGroupNames();
        allPermissionsForPackageList.retainAll(allPlatformDangerousPermissionList);
        allPermissionsForPackageList.removeAll(allPermittedDangerousPermsList);
        Assert.assertTrue(
                String.format("For package %s some extra dangerous permissions have been granted",
                        packageName),
                allPermissionsForPackageList.isEmpty());
    }

    /**
     * Verify any dangerous permission mentioned in manifest that is not default for privileged app
     * isn't granted. Example: Location permission for Camera app
     * @param packageName
     * @param notPermittedGroups
     */
    public void verifyNotPermittedDangerousPermissionDenied(String packageName,
            String[] notPermittedGroups) {
        List<String> allNotPermittedDangerousPermsList = getAllDangerousPermissionsByPermGrpNames(
                notPermittedGroups);
        List<String> allPermissionsForPackageList = getPermissionByPackage(packageName,
                Boolean.TRUE);
        int allNotPermittedDangerousPermsCount = allNotPermittedDangerousPermsList.size();
        allNotPermittedDangerousPermsList.removeAll(allPermissionsForPackageList);
        Assert.assertTrue(
                String.format("For package %s not permissible dangerous permissions been granted",
                        packageName),
                allNotPermittedDangerousPermsList.size() == allNotPermittedDangerousPermsCount);
    }

    /**
     * Verify any normal permission mentioned in manifest is auto granted
     * @param packageName
     */
    public void verifyNormalPermissionsAutoGranted(String packageName) {
        List<String> allDeniedPermissionsForPackageList = getPermissionByPackage(packageName,
                Boolean.FALSE);
        List<String> allPlatformDangerousPermissionList = getPlatformDangerousPermissionGroupNames();
        allDeniedPermissionsForPackageList.removeAll(allPlatformDangerousPermissionList);
        if (!allDeniedPermissionsForPackageList.isEmpty()) {
            for (int i = 0; i < allDeniedPermissionsForPackageList.size(); ++i) {
                Log.d(TEST_TAG, String.format("%s should have been auto granted",
                        allDeniedPermissionsForPackageList.get(i)));
            }
        }
        Assert.assertTrue(
                String.format("For package %s few normal permission have been denied", packageName),
                allDeniedPermissionsForPackageList.isEmpty());
    }

    /**
     * Verifies via UI that a permission is set/unset for an app
     * @param appName
     * @param permission
     * @param expected : 'ON' or 'OFF'
     * @return
     */
    public Boolean verifyPermissionSettingStatus(String appName, String permission,
            PermissionStatus expected) {
        if (!expected.equals(PermissionStatus.ON) && !expected.equals(PermissionStatus.OFF)) {
            throw new RuntimeException(String.format("%s isn't valid permission status", expected));
        }
        openAppPermissionView(appName);
        UiObject2 permissionView = mDevice
                .wait(Until.findObject(By.res("android:id/list_container")), TIMEOUT);
        List<UiObject2> permissionsList = permissionView.getChildren().get(0).getChildren();
        for (UiObject2 permDesc : permissionsList) {
            if (permDesc.getChildren().get(1).getChildren().get(0).getText().equals(permission)) {
                String status = permDesc.getChildren().get(2).getChildren().get(0).getText();
                return status.equals(expected.toString().toUpperCase());
            }
        }
        Assert.fail("Permission is not found");
        return Boolean.FALSE;
    }

    /**
     * Verify default dangerous permission mentioned in manifest for system privileged apps are auto
     * permitted Example: Camera permission for Camera app
     * @param packageName
     * @param permittedGroups
     */
    public void verifyDefaultDangerousPermissionGranted(String packageName,
            String[] permittedGroups,
            Boolean byGroup) {
        List<String> allPermittedDangerousPermsList = new ArrayList<String>();
        if (byGroup) {
            allPermittedDangerousPermsList = getAllDangerousPermissionsByPermGrpNames(
                    permittedGroups);
        } else {
            allPermittedDangerousPermsList.addAll(Arrays.asList(permittedGroups));
        }

        List<String> allPermissionsForPackageList = getPermissionByPackage(packageName,
                Boolean.TRUE);
        allPermittedDangerousPermsList.removeAll(allPermissionsForPackageList);
        for (String permission : allPermittedDangerousPermsList) {
            Log.d(TEST_TAG,
                    String.format("%s - > %s hasn't been granted yet", packageName, permission));
        }
        Assert.assertTrue(String.format("For %s some Permissions aren't granted yet", packageName),
                allPermittedDangerousPermsList.isEmpty());
    }

    /**
     * For a given app, opens the permission settings window settings -> apps -> permissions
     * @param appName
     */
    public void openAppPermissionView(String appName) {
        mDevice.pressHome();
        if (!mDevice.hasObject(By.pkg(SETTINGS_PACKAGE).depth(0))) {
            mLauncherStrategy.launch("Settings", SETTINGS_PACKAGE);
        }
        UiObject2 app = null;
        UiObject2 view = null;
        int maxAttempt = 5;
        while ((maxAttempt-- > 0)
                && ((app = mDevice.wait(Until.findObject(By.res("android:id/title").text("Apps")),
                TIMEOUT)) == null)) {
            view = mDevice.wait(Until.findObject(By.res(SETTINGS_PACKAGE, "main_content")),
                    TIMEOUT);
            // todo scroll may be different for device and build
            view.scroll(Direction.DOWN, 1.0f);
        }

        mDevice.wait(Until.findObject(By.res("android:id/title").text("Apps")),
                TIMEOUT)
                .clickAndWait(Until.newWindow(), TIMEOUT);
        app = null;
        view = null;
        maxAttempt = 10;
        while ((maxAttempt-- > 0)
                && ((app = mDevice.wait(Until.findObject(By.res("android:id/title").text(appName)),
                TIMEOUT)) == null)) {
            view = mDevice.wait(Until.findObject(By.res("com.android.settings:id/main_content")),
                    TIMEOUT);
            // todo scroll may be different for device and build
            view.scroll(Direction.DOWN, 1.0f);
        }
        app.clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.wait(Until.findObject(By.res("android:id/title").text("Permissions")),
                TIMEOUT).clickAndWait(Until.newWindow(), TIMEOUT);
    }

    /**
     * Toggles permission for an app via UI
     * @param appName
     * @param permission
     * @param toBeSet
     */
    public void togglePermissionSetting(String appName, String permission, Boolean toBeSet) {
        openAppPermissionView(appName);
        UiObject2 permissionView = mDevice
                .wait(Until.findObject(By.res("android:id/list_container")), TIMEOUT);
        List<UiObject2> permissionsList = permissionView.getChildren().get(0).getChildren();
        for (UiObject2 obj : permissionsList) {
            if (obj.getChildren().get(1).getChildren().get(0).getText().equals(permission)) {
                String status = obj.getChildren().get(2).getChildren().get(0).getText();
                if ((toBeSet && !status.equals(PermissionStatus.ON.toString()))
                        || (!toBeSet && status.equals(PermissionStatus.ON.toString()))) {
                    obj.getChildren().get(2).getChildren().get(0).click();
                    mDevice.waitForIdle();
                }
                break;
            }
        }
    }

    /**
     * Grant or revoke permission via adb command
     * @param packageName
     * @param permissionName
     * @param permissionOp : Accepted values are 'grant' and 'revoke'
     */
    public void grantOrRevokePermissionViaAdb(String packageName, String permissionName,
            PermissionOp permissionOp) {
        if (permissionOp == null) {
            throw new RuntimeException("null operation can't be executed");
        }
        String command = String.format("pm %s %s %s", permissionOp.toString().toLowerCase(),
                packageName, permissionName);
        Log.d(TEST_TAG, String.format("executing - %s", command));
        mUiAutomation.executeShellCommand(command);
        mDevice.waitForIdle();
    }

    /**
     * returns list of specific permissions in a dangerous permission group
     * @param permissionGroupsToCheck
     * @return
     */
    public List<String> getAllDangerousPermissionsByPermGrpNames(String[] permissionGroupsToCheck) {
        List<String> allDangerousPermissions = new ArrayList<String>();
        for (String s : permissionGroupsToCheck) {
            String grpName = String.format("android.permission-group.%s", s.toUpperCase());
            if (PermissionHelper.mPermissionGroupInfo.keySet().contains(grpName)) {
                allDangerousPermissions.addAll(PermissionHelper.mPermissionGroupInfo.get(grpName));
            }
        }

        return allDangerousPermissions;
    }

    /**
     * Returns platform dangerous permission group names
     * @return
     */
    public List<String> getPlatformDangerousPermissionGroupNames() {
        List<String> allDangerousPermissions = new ArrayList<String>();
        for (List<String> prmsList : PermissionHelper.mPermissionGroupInfo.values()) {
            allDangerousPermissions.addAll(prmsList);
        }
        return allDangerousPermissions;
    }

    /**
     * Set package permissions to ensure that all default dangerous permissions
     * mentioned in manifest are granted for any privileged app
     * @param packageName
     * @param granted
     * @param denied
     */
    public void ensureAppHasDefaultPermissions(String packageName, String[] granted,
            String[] denied) {
        List<String> defaultGranted = getAllDangerousPermissionsByPermGrpNames(granted);
        List<String> currentGranted = getPermissionByPackage(packageName, Boolean.TRUE);
        List<String> defaultDenied = getAllDangerousPermissionsByPermGrpNames(denied);
        List<String> currentDenied = getPermissionByPackage(packageName, Boolean.FALSE);
        defaultGranted.removeAll(currentGranted);
        for (String permission : defaultGranted) {
            grantOrRevokePermissionViaAdb(packageName, permission, PermissionOp.GRANT);
        }
        defaultDenied.removeAll(currentDenied);
        for (String permission : defaultDenied) {
            grantOrRevokePermissionViaAdb(packageName, permission, PermissionOp.REVOKE);
        }
    }

    /**
     * Get permission description via UI
     * @param appName
     * @return
     */
    public List<String> getPermissionDescGroupNames(String appName) {
        List<String> groupNames = new ArrayList<String>();
        openAppPermissionView(appName);
        mDevice.wait(Until.findObject(By.desc("More options")), TIMEOUT).click();
        mDevice.wait(Until.findObject(By.text("All permissions")), TIMEOUT).click();
        UiObject2 permissionsListView = mDevice
                .wait(Until.findObject(By.res("android:id/list_container")), TIMEOUT);
        List<UiObject2> permissionList = permissionsListView
                .findObjects(By.clazz("android.widget.TextView"));
        for (UiObject2 obj : permissionList) {
            if (obj.getText() != null && obj.getText() != "" && obj.getVisibleBounds().left == 0) {
                if (obj.getText().equals("Other app capabilities"))
                    break;
                groupNames.add(obj.getText().toUpperCase());
            }
        }
        return groupNames;
    }
}
