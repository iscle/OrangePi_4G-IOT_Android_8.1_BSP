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

package com.android.managedprovisioning.common;

import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.accounts.AccountManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ParceledListSlice;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link Utils}.
 */
@SmallTest
public class UtilsTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME_1 = "com.test.packagea";
    private static final String TEST_PACKAGE_NAME_2 = "com.test.packageb";
    private static final String TEST_DEVICE_ADMIN_NAME = TEST_PACKAGE_NAME_1 + ".DeviceAdmin";
    // Another DeviceAdmin in package 1
    private static final String TEST_DEVICE_ADMIN_NAME_2 = TEST_PACKAGE_NAME_1 + ".DeviceAdmin2";
    private static final ComponentName TEST_COMPONENT_NAME = new ComponentName(TEST_PACKAGE_NAME_1,
            TEST_DEVICE_ADMIN_NAME);
    private static final ComponentName TEST_COMPONENT_NAME_2 = new ComponentName(TEST_PACKAGE_NAME_1,
            TEST_DEVICE_ADMIN_NAME_2);
    private static final int TEST_USER_ID = 10;
    private static final String TEST_FILE_NAME = "testfile";

    @Mock private Context mockContext;
    @Mock private AccountManager mockAccountManager;
    @Mock private IPackageManager mockIPackageManager;
    @Mock private PackageManager mockPackageManager;
    @Mock private ConnectivityManager mockConnectivityManager;

    private Utils mUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mockContext.getSystemService(Context.ACCOUNT_SERVICE)).thenReturn(mockAccountManager);
        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockContext.getSystemService(Context.CONNECTIVITY_SERVICE))
                .thenReturn(mockConnectivityManager);

        mUtils = new Utils();
    }

    @Override
    public void tearDown() {
        mContext.deleteFile(TEST_FILE_NAME);
    }

    public void testGetCurrentSystemApps() throws Exception {
        // GIVEN two currently installed apps, one of which is system
        List<ApplicationInfo> appList = Arrays.asList(
                createApplicationInfo(TEST_PACKAGE_NAME_1, false),
                createApplicationInfo(TEST_PACKAGE_NAME_2, true));
        when(mockIPackageManager.getInstalledApplications(
                PackageManager.MATCH_UNINSTALLED_PACKAGES, TEST_USER_ID))
                .thenReturn(new ParceledListSlice<ApplicationInfo>(appList));
        // WHEN requesting the current system apps
        Set<String> res = mUtils.getCurrentSystemApps(mockIPackageManager, TEST_USER_ID);
        // THEN the one system app should be returned
        assertEquals(1, res.size());
        assertTrue(res.contains(TEST_PACKAGE_NAME_2));
    }

    public void testSetComponentEnabledSetting() throws Exception {
        // GIVEN a component name and a user id
        // WHEN disabling a component
        mUtils.setComponentEnabledSetting(mockIPackageManager, TEST_COMPONENT_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, TEST_USER_ID);
        // THEN the correct method on mockIPackageManager gets invoked
        verify(mockIPackageManager).setComponentEnabledSetting(eq(TEST_COMPONENT_NAME),
                eq(PackageManager.COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP),
                eq(TEST_USER_ID));
        verifyNoMoreInteractions(mockIPackageManager);
    }

    public void testPackageRequiresUpdate_notPresent() throws Exception {
        // GIVEN that the requested package is not present on the device
        // WHEN checking whether an update is required
        when(mockPackageManager.getPackageInfo(TEST_PACKAGE_NAME_1, 0))
                .thenThrow(new NameNotFoundException());
        // THEN an update is required
        assertTrue(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 0, mockContext));
    }

    public void testPackageRequiresUpdate() throws Exception {
        // GIVEN a package that is installed on the device
        PackageInfo pi = new PackageInfo();
        pi.packageName = TEST_PACKAGE_NAME_1;
        pi.versionCode = 1;
        when(mockPackageManager.getPackageInfo(TEST_PACKAGE_NAME_1, 0)).thenReturn(pi);
        // WHEN checking whether an update is required
        // THEN verify that update required returns the correct result depending on the minimum
        // version code requested.
        assertFalse(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 0, mockContext));
        assertFalse(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 1, mockContext));
        assertTrue(mUtils.packageRequiresUpdate(TEST_PACKAGE_NAME_1, 2, mockContext));
    }

    public void testIsConnectedToNetwork() throws Exception {
        // GIVEN the device is currently connected to mobile network
        setCurrentNetworkMock(ConnectivityManager.TYPE_MOBILE, true);
        // WHEN checking connectivity
        // THEN utils should return true
        assertTrue(mUtils.isConnectedToNetwork(mockContext));

        // GIVEN the device is currently connected to wifi
        setCurrentNetworkMock(ConnectivityManager.TYPE_WIFI, true);
        // WHEN checking connectivity
        // THEN utils should return true
        assertTrue(mUtils.isConnectedToNetwork(mockContext));

        // GIVEN the device is currently disconnected on wifi
        setCurrentNetworkMock(ConnectivityManager.TYPE_WIFI, false);
        // WHEN checking connectivity
        // THEN utils should return false
        assertFalse(mUtils.isConnectedToNetwork(mockContext));
    }

    public void testIsConnectedToWifi() throws Exception {
        // GIVEN the device is currently connected to mobile network
        setCurrentNetworkMock(ConnectivityManager.TYPE_MOBILE, true);
        // WHEN checking whether connected to wifi
        // THEN utils should return false
        assertFalse(mUtils.isConnectedToWifi(mockContext));

        // GIVEN the device is currently connected to wifi
        setCurrentNetworkMock(ConnectivityManager.TYPE_WIFI, true);
        // WHEN checking whether connected to wifi
        // THEN utils should return true
        assertTrue(mUtils.isConnectedToWifi(mockContext));

        // GIVEN the device is currently disconnected on wifi
        setCurrentNetworkMock(ConnectivityManager.TYPE_WIFI, false);
        // WHEN checking whether connected to wifi
        // THEN utils should return false
        assertFalse(mUtils.isConnectedToWifi(mockContext));
    }

    public void testGetActiveNetworkInfo() throws Exception {
        // GIVEN the device is connected to a network.
        final NetworkInfo networkInfo =
                new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, null, null);
        when(mockConnectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
        // THEN calling getActiveNetworkInfo should return the correct network info.
        assertEquals(mUtils.getActiveNetworkInfo(mockContext), networkInfo);
    }

    public void testCurrentLauncherSupportsManagedProfiles_noLauncherSet() throws Exception {
        // GIVEN there currently is no default launcher set
        when(mockPackageManager.resolveActivity(any(Intent.class), anyInt()))
                .thenReturn(null);
        // WHEN checking whether the current launcher support managed profiles
        // THEN utils should return false
        assertFalse(mUtils.currentLauncherSupportsManagedProfiles(mockContext));
    }

    public void testCurrentLauncherSupportsManagedProfiles() throws Exception {
        // GIVEN the current default launcher is built against lollipop
        setLauncherMock(Build.VERSION_CODES.LOLLIPOP);
        // WHEN checking whether the current launcher support managed profiles
        // THEN utils should return true
        assertTrue(mUtils.currentLauncherSupportsManagedProfiles(mockContext));

        // GIVEN the current default launcher is built against kitkat
        setLauncherMock(Build.VERSION_CODES.KITKAT);
        // WHEN checking whether the current launcher support managed profiles
        // THEN utils should return false
        assertFalse(mUtils.currentLauncherSupportsManagedProfiles(mockContext));
    }

    public void testFindDeviceAdmin_ComponentName() throws Exception {
        // GIVEN a package info with more than one device admin
        setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME, TEST_DEVICE_ADMIN_NAME_2);

        // THEN calling findDeviceAdmin returns the correct admin
        assertEquals(TEST_COMPONENT_NAME_2,
                mUtils.findDeviceAdmin(null, TEST_COMPONENT_NAME_2, mockContext));
    }

    public void testFindDeviceAdmin_PackageName() throws Exception {
        // GIVEN a package info with one device admin
        setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME);

        // THEN calling findDeviceAdmin returns the correct admin
        assertEquals(TEST_COMPONENT_NAME,
                mUtils.findDeviceAdmin(TEST_PACKAGE_NAME_1, null, mockContext));
    }

    public void testFindDeviceAdmin_NoPackageName() throws Exception {
        // GIVEN no package info file
        when(mockPackageManager.getPackageInfo(TEST_PACKAGE_NAME_1,
                PackageManager.GET_RECEIVERS | PackageManager.MATCH_DISABLED_COMPONENTS))
                .thenReturn(null);

        // THEN throw IllegalProvisioningArgumentException
        try {
            mUtils.findDeviceAdmin(TEST_PACKAGE_NAME_1, null, mockContext);
            fail();
        } catch (IllegalProvisioningArgumentException e) {
            // expected
        }
    }

    public void testFindDeviceAdmin_AnotherComponentName() throws Exception {
        // GIVEN a package info with one device admin
        setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME);

        // THEN looking another device admin throws IllegalProvisioningArgumentException
        try {
            mUtils.findDeviceAdmin(null, TEST_COMPONENT_NAME_2, mockContext);
            fail();
        } catch (IllegalProvisioningArgumentException e) {
            // expected
        }
    }

    public void testFindDeviceAdminInPackageInfo_Success() throws Exception {
        // GIVEN a package info with one device admin
        PackageInfo packageInfo = setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME);

        // THEN calling findDeviceAdminInPackageInfo returns the correct admin
        assertEquals(TEST_COMPONENT_NAME,
                mUtils.findDeviceAdminInPackageInfo(TEST_PACKAGE_NAME_1, null, packageInfo));
    }

    public void testFindDeviceAdminInPackageInfo_PackageNameMismatch() throws Exception {
        // GIVEN a package info with one device admin
        PackageInfo packageInfo = setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME);

        // THEN calling findDeviceAdminInPackageInfo with the wrong package name return null
        assertNull(mUtils.findDeviceAdminInPackageInfo(TEST_PACKAGE_NAME_2, null, packageInfo));
    }

    public void testFindDeviceAdminInPackageInfo_NoAdmin() throws Exception {
        // GIVEN a package info with no device admin
        PackageInfo packageInfo = setUpPackage(TEST_PACKAGE_NAME_1);

        // THEN calling findDeviceAdminInPackageInfo returns null
        assertNull(mUtils.findDeviceAdminInPackageInfo(TEST_PACKAGE_NAME_1, null, packageInfo));
    }

    public void testFindDeviceAdminInPackageInfo_TwoAdmins() throws Exception {
        // GIVEN a package info with more than one device admin
        PackageInfo packageInfo = setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME,
                TEST_DEVICE_ADMIN_NAME_2);

        // THEN calling findDeviceAdminInPackageInfo returns null
        assertNull(mUtils.findDeviceAdminInPackageInfo(TEST_PACKAGE_NAME_1, null, packageInfo));
    }

    public void testFindDeviceAdminInPackageInfo_TwoAdminsWithComponentName() throws Exception {
        // GIVEN a package info with more than one device admin
        PackageInfo packageInfo = setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME,
                TEST_DEVICE_ADMIN_NAME_2);

        // THEN calling findDeviceAdminInPackageInfo return component 1
        assertEquals(TEST_COMPONENT_NAME, mUtils.findDeviceAdminInPackageInfo(
                TEST_PACKAGE_NAME_1, TEST_COMPONENT_NAME, packageInfo));
    }


    public void testFindDeviceAdminInPackageInfo_InvalidComponentName() throws Exception {
        // GIVEN a package info with component 1
        PackageInfo packageInfo = setUpPackage(TEST_PACKAGE_NAME_1, TEST_DEVICE_ADMIN_NAME);

        // THEN calling findDeviceAdminInPackageInfo with component 2 returns null
        assertNull(mUtils.findDeviceAdminInPackageInfo(
                TEST_PACKAGE_NAME_1, TEST_COMPONENT_NAME_2, packageInfo));
    }

    public void testComputeHashOfByteArray() {
        // GIVEN a byte array
        byte[] bytes = "TESTARRAY".getBytes();
        // GIVEN its Sha256 hash
        byte[] sha256 = new byte[] {100, -45, -118, -68, -104, -15, 63, -60, -84, -44, -13, -63,
                53, -50, 104, -63, 38, 122, 16, -44, -85, -50, 67, 98, 78, 121, 11, 72, 79, 40, 107,
                125};

        // THEN computeHashOfByteArray returns the correct result
        assertTrue(Arrays.equals(sha256, mUtils.computeHashOfByteArray(bytes)));
    }

    public void testComputeHashOfFile() {
        // GIVEN a file with test data
        final String fileLocation = getContext().getFilesDir().toString() + "/" + TEST_FILE_NAME;
        String string = "Hello world!";
        FileOutputStream outputStream;
        try {
            outputStream = getContext().openFileOutput(TEST_FILE_NAME, Context.MODE_PRIVATE);
            outputStream.write(string.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // GIVEN the file's Sha256 hash
        byte[] sha256 = new byte[] {-64, 83, 94, 75, -30, -73, -97, -3, -109, 41, 19, 5, 67, 107,
                -8, -119, 49, 78, 74, 63, -82, -64, 94, -49, -4, -69, 125, -13, 26, -39, -27, 26};
        // GIVEN the file's Sha1 hash
        byte[] sha1 = new byte[] {-45, 72, 106, -23, 19, 110, 120, 86, -68, 66, 33, 35, -123, -22,
                121, 112, -108, 71, 88, 2};

        //THEN the Sha256 hash is correct
        assertTrue(
                Arrays.equals(sha256, mUtils.computeHashOfFile(fileLocation, Utils.SHA256_TYPE)));
        //THEN the Sha1 hash is correct
        assertTrue(Arrays.equals(sha1, mUtils.computeHashOfFile(fileLocation, Utils.SHA1_TYPE)));
    }

    public void testComputeHashOfFile_NotPresent() {
        // GIVEN no file is present
        final String fileLocation = getContext().getFilesDir().toString() + "/" + TEST_FILE_NAME;
        getContext().deleteFile(TEST_FILE_NAME);

        // THEN computeHashOfFile should return null
        assertNull(mUtils.computeHashOfFile(fileLocation, Utils.SHA256_TYPE));
        assertNull(mUtils.computeHashOfFile(fileLocation, Utils.SHA1_TYPE));
    }

    public void testBrightColors() {
        assertTrue(mUtils.isBrightColor(Color.WHITE));
        assertTrue(mUtils.isBrightColor(Color.YELLOW));
        assertFalse(mUtils.isBrightColor(Color.BLACK));
        assertFalse(mUtils.isBrightColor(Color.BLUE));
    }

    public void testCanResolveIntentAsUser() {
        // GIVEN intent is null
        // THEN intent should not be resolved
        assertFalse(mUtils.canResolveIntentAsUser(mockContext, null, TEST_USER_ID));

        // GIVEN a valid intent
        Intent intent = new Intent();

        // WHEN resolve activity as user returns null
        when(mockPackageManager.resolveActivityAsUser(any(Intent.class), anyInt(), anyInt()))
                .thenReturn(null);
        // THEN intent should not be resolved for user
        assertFalse(mUtils.canResolveIntentAsUser(mockContext, intent, TEST_USER_ID));

        // WHEN resolve activity as user returns valid resolve info
        when(mockPackageManager.resolveActivityAsUser(any(Intent.class), anyInt(), anyInt()))
                .thenReturn(new ResolveInfo());
        // THEN intent should be resolved
        assertTrue(mUtils.canResolveIntentAsUser(mockContext, intent, TEST_USER_ID));
    }

    private ApplicationInfo createApplicationInfo(String packageName, boolean system) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        if (system) {
            ai.flags = ApplicationInfo.FLAG_SYSTEM;
        }
        return ai;
    }

    private void setCurrentNetworkMock(int type, boolean connected) {
        NetworkInfo networkInfo = new NetworkInfo(type, 0, null, null);
        networkInfo.setDetailedState(
                connected ? NetworkInfo.DetailedState.CONNECTED
                        : NetworkInfo.DetailedState.DISCONNECTED,
                null, null);
        when(mockConnectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);
    }

    private void setLauncherMock(int targetSdkVersion) throws Exception {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.targetSdkVersion = targetSdkVersion;
        ActivityInfo actInfo = new ActivityInfo();
        actInfo.packageName = TEST_PACKAGE_NAME_1;
        ResolveInfo resInfo = new ResolveInfo();
        resInfo.activityInfo = actInfo;

        when(mockPackageManager.resolveActivity(any(Intent.class), anyInt())).thenReturn(resInfo);
        when(mockPackageManager.getApplicationInfo(TEST_PACKAGE_NAME_1, 0)).thenReturn(appInfo);
    }

    private PackageInfo setUpPackage(String packageName, String... adminNames)
            throws NameNotFoundException {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.receivers = new ActivityInfo[adminNames.length];
        for (int i = 0; i < adminNames.length; i++) {
            ActivityInfo receiver = new ActivityInfo();
            receiver.permission = android.Manifest.permission.BIND_DEVICE_ADMIN;
            receiver.name = adminNames[i];
            packageInfo.receivers[i] = receiver;
        }
        when(mockPackageManager.getPackageInfo(packageName,
                PackageManager.GET_RECEIVERS | PackageManager.MATCH_DISABLED_COMPONENTS))
                .thenReturn(packageInfo);

        return packageInfo;
    }
}
