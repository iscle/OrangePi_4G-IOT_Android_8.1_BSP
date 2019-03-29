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

package android.server.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

/**
 * Ensure that compatibility dialog is shown when launching an application with
 * an unsupported smallest width.
 *
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.DisplaySizeTest
 */
public class DisplaySizeTest extends DeviceTestCase {
    private static final String DENSITY_PROP_DEVICE = "ro.sf.lcd_density";
    private static final String DENSITY_PROP_EMULATOR = "qemu.sf.lcd_density";

    private static final String AM_START_COMMAND = "am start -n %s/%s.%s";
    private static final String AM_FORCE_STOP = "am force-stop %s";

    private static final int ACTIVITY_TIMEOUT_MILLIS = 1000;
    private static final int WINDOW_TIMEOUT_MILLIS = 1000;

    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = getDevice();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        try {
            resetDensity();

            // Ensure app process is stopped.
            forceStopPackage("android.displaysize.app");
            forceStopPackage("android.server.cts");
        } catch (DeviceNotAvailableException e) {
            // Do nothing.
        }
    }

    public void testCompatibilityDialog() throws Exception {
        // Launch some other app (not to perform density change on launcher).
        startActivity("android.server.cts", "TestActivity");
        verifyWindowDisplayed("TestActivity", ACTIVITY_TIMEOUT_MILLIS);

        setUnsupportedDensity();

        // Launch target app.
        startActivity("android.displaysize.app", "SmallestWidthActivity");
        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);
        verifyWindowDisplayed("UnsupportedDisplaySizeDialog", WINDOW_TIMEOUT_MILLIS);
    }

    public void testCompatibilityDialogWhenFocused() throws Exception {
        startActivity("android.displaysize.app", "SmallestWidthActivity");
        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);

        setUnsupportedDensity();

        verifyWindowDisplayed("UnsupportedDisplaySizeDialog", WINDOW_TIMEOUT_MILLIS);
    }

    public void testCompatibilityDialogAfterReturn() throws Exception {
        // Launch target app.
        startActivity("android.displaysize.app", "SmallestWidthActivity");
        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);
        // Launch another activity.
        startOtherActivityOnTop("android.displaysize.app", "SmallestWidthActivity");
        verifyWindowDisplayed("TestActivity", ACTIVITY_TIMEOUT_MILLIS);

        setUnsupportedDensity();

        // Go back.
        mDevice.executeShellCommand("input keyevent 4");

        verifyWindowDisplayed("SmallestWidthActivity", ACTIVITY_TIMEOUT_MILLIS);
        verifyWindowDisplayed("UnsupportedDisplaySizeDialog", WINDOW_TIMEOUT_MILLIS);
    }

    private void setUnsupportedDensity() throws DeviceNotAvailableException {
        // Set device to 0.85 zoom. It doesn't matter that we're zooming out
        // since the feature verifies that we're in a non-default density.
        final int stableDensity = getStableDensity();
        final int targetDensity = (int) (stableDensity * 0.85);
        setDensity(targetDensity);
    }

    private int getStableDensity() {
        try {
            final String densityProp;
            if (mDevice.getSerialNumber().startsWith("emulator-")) {
                densityProp = DENSITY_PROP_EMULATOR;
            } else {
                densityProp = DENSITY_PROP_DEVICE;
            }

            return Integer.parseInt(mDevice.getProperty(densityProp));
        } catch (DeviceNotAvailableException e) {
            return 0;
        }
    }

    private void setDensity(int targetDensity) throws DeviceNotAvailableException {
        mDevice.executeShellCommand("wm density " + targetDensity);

        // Verify that the density is changed.
        final String output = mDevice.executeShellCommand("wm density");
        final boolean success = output.contains("Override density: " + targetDensity);

        assertTrue("Failed to set density to " + targetDensity, success);
    }

    private void resetDensity() throws DeviceNotAvailableException {
        mDevice.executeShellCommand("wm density reset");
    }

    private void forceStopPackage(String packageName) throws DeviceNotAvailableException {
        final String forceStopCmd = String.format(AM_FORCE_STOP, packageName);
        mDevice.executeShellCommand(forceStopCmd);
    }

    private void startActivity(String packageName, String activityName)
            throws DeviceNotAvailableException {
        mDevice.executeShellCommand(getStartCommand(packageName, activityName));
    }

    private void startOtherActivityOnTop(String packageName, String activityName)
            throws DeviceNotAvailableException {
        final String startCmd = getStartCommand(packageName, activityName)
                + " -f 0x20000000 --ez launch_another_activity true";
        mDevice.executeShellCommand(startCmd);
    }

    private String getStartCommand(String packageName, String activityName) {
        return String.format(AM_START_COMMAND, packageName, packageName, activityName);
    }

    private void verifyWindowDisplayed(String windowName, long timeoutMillis)
            throws DeviceNotAvailableException {
        boolean success = false;

        // Verify that compatibility dialog is shown within 1000ms.
        final long timeoutTimeMillis = System.currentTimeMillis() + timeoutMillis;
        while (!success && System.currentTimeMillis() < timeoutTimeMillis) {
            final String output = mDevice.executeShellCommand("dumpsys window");
            success = output.contains(windowName);
        }

        assertTrue(windowName + " was not displayed", success);
    }
}
