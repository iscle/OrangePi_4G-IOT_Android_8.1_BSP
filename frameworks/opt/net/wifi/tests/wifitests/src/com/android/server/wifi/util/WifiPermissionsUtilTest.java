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

package com.android.server.wifi.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.net.NetworkScoreManager;
import android.net.NetworkScorerAppData;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import com.android.server.wifi.BinderUtil;
import com.android.server.wifi.FakeWifiLog;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiSettingsStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.HashMap;

/** Unit tests for {@link WifiPermissionsUtil}. */
@RunWith(JUnit4.class)
public class WifiPermissionsUtilTest {
    public static final String TAG = "WifiPermissionsUtilTest";

    // Mock objects for testing
    @Mock private WifiPermissionsWrapper mMockPermissionsWrapper;
    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPkgMgr;
    @Mock private ApplicationInfo mMockApplInfo;
    @Mock private AppOpsManager mMockAppOps;
    @Mock private UserInfo mMockUserInfo;
    @Mock private UserManager mMockUserManager;
    @Mock private WifiSettingsStore mMockWifiSettingsStore;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private NetworkScoreManager mMockNetworkScoreManager;
    @Mock private WifiInjector mMockWifiInjector;
    @Mock private FrameworkFacade mMockFrameworkFacade;
    @Mock private WifiConfiguration mMockWifiConfig;
    @Spy private FakeWifiLog mWifiLog;

    private static final String TEST_PACKAGE_NAME = "com.google.somePackage";
    private static final String INVALID_PACKAGE  = "BAD_PACKAGE";
    private static final int MANAGED_PROFILE_UID = 1100000;
    private static final int OTHER_USER_UID = 1200000;

    private final int mCallingUser = UserHandle.USER_CURRENT_OR_SELF;
    private final String mMacAddressPermission = "android.permission.PEERS_MAC_ADDRESS";
    private final String mInteractAcrossUsersFullPermission =
            "android.permission.INTERACT_ACROSS_USERS_FULL";
    private final String mManifestStringCoarse =
            Manifest.permission.ACCESS_COARSE_LOCATION;

    // Test variables
    private int mWifiScanAllowApps;
    private int mUid;
    private int mCoarseLocationPermission;
    private int mAllowCoarseLocationApps;
    private String mPkgNameOfTopActivity;
    private int mCurrentUser;
    private int mLocationModeSetting;
    private boolean mThrowSecurityException;
    private int mTargetVersion;
    private boolean mActiveNwScorer;
    private Answer<Integer> mReturnPermission;
    private HashMap<String, Integer> mPermissionsList = new HashMap<String, Integer>();
    private String mUseOpenWifiPackage;
    private NetworkScorerAppData mNetworkScorerAppData;
    private boolean mGetActiveScorerThrowsSecurityException;
    private boolean mConfigIsOpen;

    /**
    * Set up Mockito tests
    */
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        initTestVars();
    }

    private void setupTestCase() throws Exception {
        setupMocks();
        setupMockInterface();
    }

    /**
     * Verify we return true when the UID does have the override config permission
     */
    @Test
    public void testCheckConfigOverridePermissionApproved() throws Exception {
        mUid = MANAGED_PROFILE_UID;  // do not really care about this value
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        when(mMockPermissionsWrapper.getOverrideWifiConfigPermission(anyInt()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        assertTrue(codeUnderTest.checkConfigOverridePermission(mUid));
    }

    /**
     * Verify we return false when the UID does not have the override config permission.
     */
    @Test
    public void testCheckConfigOverridePermissionDenied() throws Exception {
        mUid = OTHER_USER_UID;  // do not really care about this value
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        when(mMockPermissionsWrapper.getOverrideWifiConfigPermission(anyInt()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertFalse(codeUnderTest.checkConfigOverridePermission(mUid));
    }

    /**
     * Verify we return false when the override config permission check throws a RemoteException.
     */
    @Test
    public void testCheckConfigOverridePermissionWithException() throws Exception {
        mUid = OTHER_USER_UID;  // do not really care about this value
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        doThrow(new RemoteException("Failed to check permissions for " + mUid))
                .when(mMockPermissionsWrapper).getOverrideWifiConfigPermission(mUid);
        assertFalse(codeUnderTest.checkConfigOverridePermission(mUid));
    }

    /**
     * Test case setting: Package is valid
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User is current
     * Validate result is true
     * - User has all the permissions
     */
    @Test
    public void testCanReadPeersMacAddressCurrentUserAndAllPermissions() throws Exception {
        boolean output = false;
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, true);
    }

    /**
     * Test case setting: Package is valid
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User profile is current
     * Validate result is true
     * - User has all the permissions
     */
    @Test
    public void testCanReadPeersMacAddressCurrentProfileAndAllPermissions() throws Exception {
        boolean output = false;
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mMockUserInfo.id = mCallingUser;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, true);
    }

    /**
     * Test case setting: Package is valid
     *                    Caller can read peers mac address
     * Validate result is false
     * - This App doesn't have permission to request Wifi Scan
     */
    @Test
    public void testCannotAccessScanResult_AppNotAllowed() throws Exception {
        boolean output = true;
        mThrowSecurityException = false;
        mPermissionsList.put(mMacAddressPermission, mUid);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, false);
    }

    /**
     * Test case setting: Package is valid
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User or profile is not current but the uid has
     *                    permission to INTERACT_ACROSS_USERS_FULL
     * Validate result is true
     * - User has all the permissions
     */
    @Test
    public void testCanAccessScanResults_UserOrProfileNotCurrent() throws Exception {
        boolean output = false;
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, true);
    }

    /**
     * Test case setting: Package is valid
     *                    Caller can read peers mac address
     *                    This App has permission to request WIFI_SCAN
     *                    User or profile is not Current
     * Validate result is false
     * - Calling uid doesn't have INTERACT_ACROSS_USERS_FULL permission
     */
    @Test
    public void testCannotAccessScanResults_NoInteractAcrossUsersFullPermission() throws Exception {
        boolean output = true;
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mMacAddressPermission, mUid);
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, false);
    }

    /**
     * Test case setting: Package is valid
     *                    Caller is active network scorer
     *                    This App has permission to request WIFI_SCAN
     *                    User is current
     * Validate result is true
     */
    @Test
    public void testCanAccessScanResults_CallerIsActiveNwScorer() throws Exception {
        boolean output = false;
        mThrowSecurityException = false;
        mActiveNwScorer = true;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, true);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User is current
     *                    The current config is for an open network.
     * Validate result is true
     */
    @Test
    public void testCanAccessFullConnectionInfo_PackageIsUseOpenWifiPackage() throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        ComponentName useOpenWifiComponent = new ComponentName(TEST_PACKAGE_NAME, "TestClass");
        mNetworkScorerAppData = new NetworkScorerAppData(0 /*packageUid*/,
                null /*recommendationServiceComp*/, null /*recommendationServiceLabel*/,
                useOpenWifiComponent, null /*networkAvailableNotificationChannelId*/);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(true, output);
    }

    /**
     * Test case setting: Package is valid because the caller has access to scan results.
     *                    Location mode is ON
     *                    User is current
     *                    The current config is not for an open network.
     * Validate result is true
     */
    @Test
    public void testCanAccessFullConnectionInfo_HasAccessToScanResults() throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.GINGERBREAD;
        mLocationModeSetting = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
        mCoarseLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mConfigIsOpen = false;

        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(true, output);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User or profile is not current but the uid has
     *                    permission to INTERACT_ACROSS_USERS_FULL
     *                    The current config is for an open network.
     * Validate result is true
     */
    @Test
    public void testCanAccessFullConnectionInfo_UserNotCurrentButHasInteractAcrossUsers()
            throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mPermissionsList.put(mInteractAcrossUsersFullPermission, mUid);
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        ComponentName useOpenWifiComponent = new ComponentName(TEST_PACKAGE_NAME, "TestClass");
        mNetworkScorerAppData = new NetworkScorerAppData(0 /*packageUid*/,
                null /*recommendationServiceComp*/, null /*recommendationServiceLabel*/,
                useOpenWifiComponent, null /*networkAvailableNotificationChannelId*/);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(true, output);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User or profile is NOT current
     *                    INTERACT_ACROSS_USERS_FULL NOT granted
     *                    The current config is for an open network.
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_UserNotCurrentNoInteractAcrossUsers()
            throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mUid = MANAGED_PROFILE_UID;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User is current
     *                    The current config is NULL.
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_WiFiConfigIsNull() throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        ComponentName useOpenWifiComponent = new ComponentName(TEST_PACKAGE_NAME, "TestClass");
        mNetworkScorerAppData = new NetworkScorerAppData(0 /*packageUid*/,
                null /*recommendationServiceComp*/, null /*recommendationServiceLabel*/,
                useOpenWifiComponent, null /*networkAvailableNotificationChannelId*/);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(null /*config*/, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User is current
     *                    The current config is not for an open network.
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_WiFiConfigIsNotOpen() throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        ComponentName useOpenWifiComponent = new ComponentName(TEST_PACKAGE_NAME, "TestClass");
        mNetworkScorerAppData = new NetworkScorerAppData(0 /*packageUid*/,
                null /*recommendationServiceComp*/, null /*recommendationServiceLabel*/,
                useOpenWifiComponent, null /*networkAvailableNotificationChannelId*/);
        mConfigIsOpen = false;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User is current
     *                    The current config is for an open network.
     *                    There is no active scorer
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_UseOpenWifiPackageIsSetButNoActiveScorer()
            throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        mNetworkScorerAppData = null; // getActiveScorer() will return null
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User is current
     *                    The current config is for an open network.
     *                    The scorer is active but the useOpenWiFi component name doesn't match
     *                    the provided package.
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_MismatchBetweenUseOpenWifiPackages()
            throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        ComponentName useOpenWifiComponent =
                new ComponentName(mUseOpenWifiPackage + ".nomatch", "TestClass");
        mNetworkScorerAppData = new NetworkScorerAppData(0 /*packageUid*/,
                null /*recommendationServiceComp*/, null /*recommendationServiceLabel*/,
                useOpenWifiComponent, null /*networkAvailableNotificationChannelId*/);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: Package is valid because it matches the USE_OPEN_WIFI_PACKAGE.
     *                    User is current
     *                    The current config is for an open network.
     *                    The scorer is active but the useOpenWiFi component name is null.
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_UseOpenWifiPackageFromScorerIsNull()
            throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        mNetworkScorerAppData = new NetworkScorerAppData(0 /*packageUid*/,
                null /*recommendationServiceComp*/, null /*recommendationServiceLabel*/,
                null /*useOpenWifiComponent*/, null /*networkAvailableNotificationChannelId*/);
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: Package is invalid because USE_OPEN_WIFI_PACKAGE is an empty string.
     *                    Location mode is ON
     *                    User is current
     *                    The current config is for an open network.
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_UseOpenWifiPackageIsEmpty() throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mLocationModeSetting = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = "";
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: Package is invalid because it does not match the USE_OPEN_WIFI_PACKAGE.
     *                    User is current
     *                    The current config is for an open network.
     * Validate result is false
     */
    @Test
    public void testCanAccessFullConnectionInfo_DoesNotMatchUseOpenWifiPackage() throws Exception {
        final boolean output;
        mThrowSecurityException = false;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME + ".nomatch";
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        output = codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME,
                mUid, mTargetVersion);

        assertEquals(false, output);
    }

    /**
     * Test case setting: The caller is invalid because its UID does not match the provided package.
     *
     * Validate a SecurityException is thrown.
     */
    @Test
    public void testCanAccessFullConnectionInfo_UidPackageCheckFails() throws Exception {
        mThrowSecurityException = true;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        try {
            codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME, mUid,
                    mTargetVersion);
            fail("SecurityException not thrown.");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Test case setting: The getActiveScorer() call fails with a SecurityException.
     *
     * Validate a SecurityException is thrown.
     */
    @Test
    public void testCanAccessFullConnectionInfo_GetActiveScorerFails() throws Exception {
        mThrowSecurityException = false;
        mGetActiveScorerThrowsSecurityException = true;
        mLocationModeSetting = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        mUseOpenWifiPackage = TEST_PACKAGE_NAME;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);

        try {
            codeUnderTest.canAccessFullConnectionInfo(mMockWifiConfig, TEST_PACKAGE_NAME, mUid,
                    mTargetVersion);
            fail("SecurityException not thrown.");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Test case Setting: Package is valid
     *                    Legacy App
     *                    Foreground
     *                    This App has permission to request WIFI_SCAN
     *                    User is current
     *  Validate result is true - has all permissions
     */
    @Test
    public void testLegacyForegroundAppAndAllPermissions() throws Exception {
        boolean output = false;
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.GINGERBREAD;
        mPkgNameOfTopActivity = TEST_PACKAGE_NAME;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        mCurrentUser = UserHandle.USER_CURRENT_OR_SELF;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, true);
    }

    /**
     * Test case Setting: Package is valid
     *                    Legacy App
     *                    Location Mode Enabled
     *                    Coarse Location Access
     *                    This App has permission to request WIFI_SCAN
     *                    User profile is current
     *  Validate result is true - has all permissions
     */
    @Test
    public void testLegacyAppHasLocationAndAllPermissions() throws Exception {
        boolean output = false;
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.GINGERBREAD;
        mLocationModeSetting = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
        mCoarseLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        mMockUserInfo.id = mCallingUser;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, true);
    }

    /**
     * Test case setting: Package is valid
     *                    Location Mode Enabled
     * Validate result is false
     * - Doesn't have Peer Mac Address read permission
     * - Uid is not an active network scorer
     * - Location Mode is enabled but the uid
     * - doesn't have Coarse Location Access
     * - which implies No Location Permission
     */
    @Test
    public void testCannotAccessScanResults_NoCoarseLocationPermission() throws Exception {
        boolean output = true;
        mThrowSecurityException = false;
        mLocationModeSetting = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
        assertEquals(output, false);
    }

    /**
     * Test case setting: Invalid Package
     * Expect a securityException
     */
    @Test (expected = SecurityException.class)
    public void testInvalidPackage() throws Exception {
        boolean output = false;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        try {
            output = codeUnderTest.canAccessScanResults(TEST_PACKAGE_NAME, mUid, mTargetVersion);
        } catch (SecurityException e) {
            throw e;
        }
    }

    /**
     * Test case setting: caller does have Location permission.
     * A SecurityException should not be thrown.
     */
    @Test
    public void testEnforceLocationPermission() throws Exception {
        mThrowSecurityException = false;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.GINGERBREAD;
        mLocationModeSetting = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
        mCoarseLocationPermission = PackageManager.PERMISSION_GRANTED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ALLOWED;
        mWifiScanAllowApps = AppOpsManager.MODE_ALLOWED;
        mUid = MANAGED_PROFILE_UID;
        mMockUserInfo.id = mCallingUser;
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        codeUnderTest.enforceLocationPermission(TEST_PACKAGE_NAME, mUid);
    }

    /**
     * Test case setting: caller does not have Location permission.
     * Expect a SecurityException
     */
    @Test(expected = SecurityException.class)
    public void testEnforceLocationPermissionExpectSecurityException() throws Exception {
        setupTestCase();
        WifiPermissionsUtil codeUnderTest = new WifiPermissionsUtil(mMockPermissionsWrapper,
                mMockContext, mMockWifiSettingsStore, mMockUserManager, mMockNetworkScoreManager,
                mMockWifiInjector);
        codeUnderTest.enforceLocationPermission(TEST_PACKAGE_NAME, mUid);
    }

    private Answer<Integer> createPermissionAnswer() {
        return new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock invocation) {
                int myUid = (int) invocation.getArguments()[1];
                String myPermission = (String) invocation.getArguments()[0];
                mPermissionsList.get(myPermission);
                if (mPermissionsList.containsKey(myPermission)) {
                    int uid = mPermissionsList.get(myPermission);
                    if (myUid == uid) {
                        return PackageManager.PERMISSION_GRANTED;
                    }
                }
                return PackageManager.PERMISSION_DENIED;
            }
        };
    }

    private void setupMocks() throws Exception {
        when(mMockPkgMgr.getApplicationInfo(TEST_PACKAGE_NAME, 0))
            .thenReturn(mMockApplInfo);
        when(mMockContext.getPackageManager()).thenReturn(mMockPkgMgr);
        when(mMockAppOps.noteOp(AppOpsManager.OP_WIFI_SCAN, mUid, TEST_PACKAGE_NAME))
            .thenReturn(mWifiScanAllowApps);
        when(mMockAppOps.noteOp(AppOpsManager.OP_COARSE_LOCATION, mUid, TEST_PACKAGE_NAME))
            .thenReturn(mAllowCoarseLocationApps);
        if (mThrowSecurityException) {
            doThrow(new SecurityException("Package " + TEST_PACKAGE_NAME + " doesn't belong"
                    + " to application bound to user " + mUid))
                    .when(mMockAppOps).checkPackage(mUid, TEST_PACKAGE_NAME);
        }
        when(mMockContext.getSystemService(Context.APP_OPS_SERVICE))
            .thenReturn(mMockAppOps);
        when(mMockUserManager.getProfiles(mCurrentUser))
            .thenReturn(Arrays.asList(mMockUserInfo));
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getSystemService(Context.USER_SERVICE))
            .thenReturn(mMockUserManager);
        when(mMockWifiInjector.makeLog(anyString())).thenReturn(mWifiLog);
        when(mMockWifiInjector.getFrameworkFacade()).thenReturn(mMockFrameworkFacade);
        if (mGetActiveScorerThrowsSecurityException) {
            when(mMockNetworkScoreManager.getActiveScorer()).thenThrow(
                    new SecurityException("Caller is neither the system process nor a "
                            + "score requester."));
        } else {
            when(mMockNetworkScoreManager.getActiveScorer()).thenReturn(mNetworkScorerAppData);
        }
    }

    private void initTestVars() {
        mPermissionsList.clear();
        mReturnPermission = createPermissionAnswer();
        mWifiScanAllowApps = AppOpsManager.MODE_ERRORED;
        mUid = OTHER_USER_UID;
        mThrowSecurityException = true;
        mMockUserInfo.id = UserHandle.USER_NULL;
        mMockApplInfo.targetSdkVersion = Build.VERSION_CODES.M;
        mTargetVersion = Build.VERSION_CODES.M;
        mPkgNameOfTopActivity = INVALID_PACKAGE;
        mLocationModeSetting = Settings.Secure.LOCATION_MODE_OFF;
        mCurrentUser = UserHandle.USER_SYSTEM;
        mCoarseLocationPermission = PackageManager.PERMISSION_DENIED;
        mAllowCoarseLocationApps = AppOpsManager.MODE_ERRORED;
        mActiveNwScorer = false;
        mUseOpenWifiPackage = null;
        mNetworkScorerAppData = null;
        mGetActiveScorerThrowsSecurityException = false;
        mConfigIsOpen = true;
    }

    private void setupMockInterface() {
        BinderUtil.setUid(mUid);
        doAnswer(mReturnPermission).when(mMockPermissionsWrapper).getUidPermission(
                        anyString(), anyInt());
        doAnswer(mReturnPermission).when(mMockPermissionsWrapper).getUidPermission(
                        anyString(), anyInt());
        when(mMockPermissionsWrapper.getCallingUserId(mUid)).thenReturn(mCallingUser);
        when(mMockPermissionsWrapper.getCurrentUser()).thenReturn(mCurrentUser);
        when(mMockNetworkScoreManager.isCallerActiveScorer(mUid)).thenReturn(mActiveNwScorer);
        when(mMockPermissionsWrapper.getUidPermission(mManifestStringCoarse, mUid))
            .thenReturn(mCoarseLocationPermission);
        when(mMockWifiSettingsStore.getLocationModeSetting(mMockContext))
            .thenReturn(mLocationModeSetting);
        when(mMockPermissionsWrapper.getTopPkgName()).thenReturn(mPkgNameOfTopActivity);
        when(mMockFrameworkFacade.getStringSetting(mMockContext,
                Settings.Global.USE_OPEN_WIFI_PACKAGE)).thenReturn(mUseOpenWifiPackage);
        when(mMockWifiConfig.isOpenNetwork()).thenReturn(mConfigIsOpen);
    }
}
