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

package android.media.session.cts;

import static android.media.cts.MediaSessionTestHelperConstants.FLAG_CREATE_MEDIA_SESSION;
import static android.media.cts.MediaSessionTestHelperConstants.FLAG_SET_MEDIA_SESSION_ACTIVE;
import static android.media.cts.MediaSessionTestHelperConstants.MEDIA_SESSION_TEST_HELPER_APK;
import static android.media.cts.MediaSessionTestHelperConstants.MEDIA_SESSION_TEST_HELPER_PKG;

import android.media.cts.BaseMultiUserTest;
import android.media.cts.MediaSessionTestHelperConstants;

import android.platform.test.annotations.RequiresDevice;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.List;


/**
 * Host-side test for the media session manager that installs and runs device-side tests after the
 * proper device setup.
 * <p>Corresponding device-side tests are written in the {@link #DEVICE_SIDE_TEST_CLASS}
 * which is in the {@link #DEVICE_SIDE_TEST_APK}.
 */
public class MediaSessionManagerHostTest extends BaseMultiUserTest {
    /**
     * Package name of the device-side tests.
     */
    private static final String DEVICE_SIDE_TEST_PKG = "android.media.session.cts";
    /**
     * Package file name (.apk) for the device-side tests.
     */
    private static final String DEVICE_SIDE_TEST_APK = "CtsMediaSessionHostTestApp.apk";
    /**
     * Fully qualified class name for the device-side tests.
     */
    private static final String DEVICE_SIDE_TEST_CLASS =
            "android.media.session.cts.MediaSessionManagerTest";

    private final List<Integer> mNotificationListeners = new ArrayList<>();

    private boolean mNotificationListenerDisabled;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Ensure that the previously running media session test helper app doesn't exist.
        getDevice().uninstallPackage(MEDIA_SESSION_TEST_HELPER_PKG);
        mNotificationListeners.clear();
        mNotificationListenerDisabled = "true".equals(getDevice().getProperty("ro.config.low_ram"));
    }

    @Override
    public void tearDown() throws Exception {
        // Cleanup
        for (int userId : mNotificationListeners) {
            setAllowGetActiveSessionForTest(false, userId);
        }
        super.tearDown();
    }

    /**
     * Tests {@link MediaSessionManager#getActiveSessions} with the primary user.
     */
    @RequiresDevice
    public void testGetActiveSessions_primaryUser() throws Exception {
        if (mNotificationListenerDisabled) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "NotificationListener is disabled. Test won't run.");
            return;
        }
        int primaryUserId = getDevice().getPrimaryUserId();

        setAllowGetActiveSessionForTest(true, primaryUserId);
        installAppAsUser(DEVICE_SIDE_TEST_APK, primaryUserId);
        runTest("testGetActiveSessions_noMediaSessionFromMediaSessionTestHelper");

        installAppAsUser(MEDIA_SESSION_TEST_HELPER_APK, primaryUserId);
        sendControlCommand(primaryUserId, FLAG_CREATE_MEDIA_SESSION);
        runTest("testGetActiveSessions_noMediaSessionFromMediaSessionTestHelper");

        sendControlCommand(primaryUserId, FLAG_SET_MEDIA_SESSION_ACTIVE);
        runTest("testGetActiveSessions_hasMediaSessionFromMediaSessionTestHelper");
    }

    /**
     * Tests {@link MediaSessionManager#getActiveSessions} with additional users.
     */
    @RequiresDevice
    public void testGetActiveSessions_additionalUser() throws Exception {
        if (!canCreateAdditionalUsers(1)) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "Cannot create a new user. Skipping multi-user test cases.");
            return;
        }
        if (mNotificationListenerDisabled) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "NotificationListener is disabled. Test won't run.");
            return;
        }

        // Test if another user can get the session.
        int newUser = createAndStartUser();
        installAppAsUser(DEVICE_SIDE_TEST_APK, newUser);
        setAllowGetActiveSessionForTest(true, newUser);
        runTestAsUser("testGetActiveSessions_noMediaSession", newUser);
        removeUser(newUser);
    }

    /**
     * Tests {@link MediaSessionManager#getActiveSessions} with restricted profiles.
     */
    @RequiresDevice
    public void testGetActiveSessions_restrictedProfiles() throws Exception {
        if (!canCreateAdditionalUsers(1)) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "Cannot create a new user. Skipping multi-user test cases.");
            return;
        }
        if (mNotificationListenerDisabled) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "NotificationListener is disabled. Test won't run.");
            return;
        }

        // Test if another restricted profile can get the session.
        // Remove the created user first not to exceed system's user number limit.
        int newUser = createAndStartRestrictedProfile(getDevice().getPrimaryUserId());
        installAppAsUser(DEVICE_SIDE_TEST_APK, newUser);
        setAllowGetActiveSessionForTest(true, newUser);
        runTestAsUser("testGetActiveSessions_noMediaSession", newUser);
        removeUser(newUser);
    }

    /**
     * Tests {@link MediaSessionManager#getActiveSessions} with managed profiles.
     */
    @RequiresDevice
    public void testGetActiveSessions_managedProfiles() throws Exception {
        if (!hasDeviceFeature("android.software.managed_users")) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "Device doesn't support managed profiles. Test won't run.");
            return;
        }
        if (mNotificationListenerDisabled) {
            CLog.logAndDisplay(LogLevel.INFO,
                    "NotificationListener is disabled. Test won't run.");
            return;
        }

        // Test if another managed profile can get the session.
        // Remove the created user first not to exceed system's user number limit.
        int newUser = createAndStartManagedProfile(getDevice().getPrimaryUserId());
        installAppAsUser(DEVICE_SIDE_TEST_APK, newUser);
        setAllowGetActiveSessionForTest(true, newUser);
        runTestAsUser("testGetActiveSessions_noMediaSession", newUser);
        removeUser(newUser);
    }

    private void runTest(String testMethodName) throws DeviceNotAvailableException {
        runTestAsUser(testMethodName, getDevice().getPrimaryUserId());
    }

    private void runTestAsUser(String testMethodName, int userId)
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(DEVICE_SIDE_TEST_PKG, DEVICE_SIDE_TEST_CLASS,
                testMethodName, userId);
    }

    /**
     * Sets to allow or disallow the {@link #DEVICE_SIDE_TEST_CLASS}
     * to call {@link MediaSessionManager#getActiveSessions} for testing.
     * <p>{@link MediaSessionManager#getActiveSessions} bypasses the permission check if the
     * caller is the enabled notification listener. This method uses the behavior by allowing
     * this class as the notification listener service.
     * <p>Note that the device-side test {@link android.media.cts.MediaSessionManagerTest} already
     * covers the test for failing {@link MediaSessionManager#getActiveSessions} without the
     * permission nor the notification listener.
     */
    private void setAllowGetActiveSessionForTest(boolean allow, int userId) throws Exception {
        String notificationListener = DEVICE_SIDE_TEST_PKG + "/" + DEVICE_SIDE_TEST_CLASS;
        String command = "cmd notification "
                + ((allow) ? "allow_listener " : "disallow_listener ")
                + notificationListener + " " + userId;
        executeShellCommand(command);
        if (allow) {
            mNotificationListeners.add(userId);
        }
    }

    private void sendControlCommand(int userId, int flag) throws Exception {
        executeShellCommand(MediaSessionTestHelperConstants.buildControlCommand(userId, flag));
    }
}
