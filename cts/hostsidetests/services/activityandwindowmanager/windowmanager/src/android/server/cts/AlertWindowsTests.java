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

package android.server.cts;

import android.platform.test.annotations.Presubmit;
import com.android.tradefed.device.DeviceNotAvailableException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsWindowManagerHostTestCases android.server.cts.AlertWindowsTests
 */
@Presubmit
public class AlertWindowsTests extends ActivityManagerTestBase {

    private static final String PACKAGE_NAME = "android.server.alertwindowapp";
    private static final String ACTIVITY_NAME = "AlertWindowTestActivity";
    private static final String SDK_25_PACKAGE_NAME = "android.server.alertwindowappsdk25";
    private static final String SDK_25_ACTIVITY_NAME = "AlertWindowTestActivitySdk25";

    // From WindowManager.java
    private static final int TYPE_BASE_APPLICATION      = 1;
    private static final int FIRST_SYSTEM_WINDOW        = 2000;

    private static final int TYPE_PHONE                 = FIRST_SYSTEM_WINDOW + 2;
    private static final int TYPE_SYSTEM_ALERT          = FIRST_SYSTEM_WINDOW + 3;
    private static final int TYPE_SYSTEM_OVERLAY        = FIRST_SYSTEM_WINDOW + 6;
    private static final int TYPE_PRIORITY_PHONE        = FIRST_SYSTEM_WINDOW + 7;
    private static final int TYPE_SYSTEM_ERROR          = FIRST_SYSTEM_WINDOW + 10;
    private static final int TYPE_APPLICATION_OVERLAY   = FIRST_SYSTEM_WINDOW + 38;

    private static final int TYPE_STATUS_BAR            = FIRST_SYSTEM_WINDOW;
    private static final int TYPE_INPUT_METHOD          = FIRST_SYSTEM_WINDOW + 11;
    private static final int TYPE_NAVIGATION_BAR        = FIRST_SYSTEM_WINDOW + 19;

    private final List<Integer> mAlertWindowTypes = Arrays.asList(
            TYPE_PHONE,
            TYPE_PRIORITY_PHONE,
            TYPE_SYSTEM_ALERT,
            TYPE_SYSTEM_ERROR,
            TYPE_SYSTEM_OVERLAY,
            TYPE_APPLICATION_OVERLAY);
    private final List<Integer> mSystemWindowTypes = Arrays.asList(
            TYPE_STATUS_BAR,
            TYPE_INPUT_METHOD,
            TYPE_NAVIGATION_BAR);

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            setAlertWindowPermission(PACKAGE_NAME, false);
            setAlertWindowPermission(SDK_25_PACKAGE_NAME, false);
            executeShellCommand("am force-stop " + PACKAGE_NAME);
            executeShellCommand("am force-stop " + SDK_25_PACKAGE_NAME);
        } catch (DeviceNotAvailableException e) {
        }
    }

    public void testAlertWindowAllowed() throws Exception {
        runAlertWindowTest(PACKAGE_NAME, ACTIVITY_NAME, true /* hasAlertWindowPermission */,
                true /* atLeastO */);
    }

    public void testAlertWindowDisallowed() throws Exception {
        runAlertWindowTest(PACKAGE_NAME, ACTIVITY_NAME, false /* hasAlertWindowPermission */,
                true /* atLeastO */);
    }

    public void testAlertWindowAllowedSdk25() throws Exception {
        runAlertWindowTest(SDK_25_PACKAGE_NAME, SDK_25_ACTIVITY_NAME,
                true /* hasAlertWindowPermission */, false /* atLeastO */);
    }

    public void testAlertWindowDisallowedSdk25() throws Exception {
        runAlertWindowTest(SDK_25_PACKAGE_NAME, SDK_25_ACTIVITY_NAME,
                false /* hasAlertWindowPermission */, false /* atLeastO */);
    }

    private void runAlertWindowTest(String packageName, String activityName,
            boolean hasAlertWindowPermission, boolean atLeastO) throws Exception {
        setComponentName(packageName);
        setAlertWindowPermission(packageName, hasAlertWindowPermission);

        executeShellCommand(getAmStartCmd(activityName));
        mAmWmState.computeState(mDevice, new String[] { activityName });
        mAmWmState.assertVisibility(activityName, true);

        assertAlertWindows(packageName, hasAlertWindowPermission, atLeastO);
    }

    private void assertAlertWindows(String packageName, boolean hasAlertWindowPermission,
            boolean atLeastO) {
        final WindowManagerState wMState = mAmWmState.getWmState();

        final ArrayList<WindowManagerState.WindowState> alertWindows = new ArrayList();
        wMState.getWindowsByPackageName(packageName, mAlertWindowTypes, alertWindows);

        if (!hasAlertWindowPermission) {
            assertTrue("Should be empty alertWindows=" + alertWindows, alertWindows.isEmpty());
            return;
        }

        if (atLeastO) {
            // Assert that only TYPE_APPLICATION_OVERLAY was created.
            for (WindowManagerState.WindowState win : alertWindows) {
                assertTrue("Can't create win=" + win + " on SDK O or greater",
                        win.getType() == TYPE_APPLICATION_OVERLAY);
            }
        }

        final WindowManagerState.WindowState mainAppWindow =
                wMState.getWindowByPackageName(packageName, TYPE_BASE_APPLICATION);

        assertNotNull(mainAppWindow);

        wMState.sortWindowsByLayer(alertWindows);
        final WindowManagerState.WindowState lowestAlertWindow = alertWindows.get(0);
        final WindowManagerState.WindowState highestAlertWindow =
                alertWindows.get(alertWindows.size() - 1);

        // Assert that the alert windows have higher z-order than the main app window
        assertTrue("lowestAlertWindow=" + lowestAlertWindow + " less than mainAppWindow="
                        + mainAppWindow, lowestAlertWindow.getLayer() > mainAppWindow.getLayer());

        // Assert that legacy alert windows have a lower z-order than the new alert window layer.
        final WindowManagerState.WindowState appOverlayWindow =
                wMState.getWindowByPackageName(packageName, TYPE_APPLICATION_OVERLAY);
        if (appOverlayWindow != null && highestAlertWindow != appOverlayWindow) {
            assertTrue("highestAlertWindow=" + highestAlertWindow
                    + " greater than appOverlayWindow=" + appOverlayWindow,
                    highestAlertWindow.getLayer() < appOverlayWindow.getLayer());
        }

        // Assert that alert windows are below key system windows.
        final ArrayList<WindowManagerState.WindowState> systemWindows = new ArrayList();
        wMState.getWindowsByPackageName(packageName, mSystemWindowTypes, systemWindows);
        if (!systemWindows.isEmpty()) {
            wMState.sortWindowsByLayer(systemWindows);
            final WindowManagerState.WindowState lowestSystemWindow = alertWindows.get(0);
            assertTrue("highestAlertWindow=" + highestAlertWindow
                    + " greater than lowestSystemWindow=" + lowestSystemWindow,
                    highestAlertWindow.getLayer() < lowestSystemWindow.getLayer());
        }
    }

    private void setAlertWindowPermission(String packageName, boolean allow) throws Exception {
        executeShellCommand("appops set " + packageName + " android:system_alert_window "
                + (allow ? "allow" : "deny"));
    }
}
