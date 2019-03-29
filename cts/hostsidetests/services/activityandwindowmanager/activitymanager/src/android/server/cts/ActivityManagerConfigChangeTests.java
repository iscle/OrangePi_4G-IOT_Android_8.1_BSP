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

import android.platform.test.annotations.Presubmit;

import static android.server.cts.ActivityManagerState.STATE_RESUMED;
import static android.server.cts.StateLogger.logE;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerConfigChangeTests
 */
public class ActivityManagerConfigChangeTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String NO_RELAUNCH_ACTIVITY_NAME = "NoRelaunchActivity";

    private static final String FONT_SCALE_ACTIVITY_NAME = "FontScaleActivity";
    private static final String FONT_SCALE_NO_RELAUNCH_ACTIVITY_NAME =
            "FontScaleNoRelaunchActivity";

    private static final float EXPECTED_FONT_SIZE_SP = 10.0f;

    public void testRotation90Relaunch() throws Exception{
        // Should relaunch on every rotation and receive no onConfigurationChanged()
        testRotation(TEST_ACTIVITY_NAME, 1, 1, 0);
    }

    public void testRotation90NoRelaunch() throws Exception {
        // Should receive onConfigurationChanged() on every rotation and no relaunch
        testRotation(NO_RELAUNCH_ACTIVITY_NAME, 1, 0, 1);
    }

    public void testRotation180Relaunch() throws Exception {
        // Should receive nothing
        testRotation(TEST_ACTIVITY_NAME, 2, 0, 0);
    }

    public void testRotation180NoRelaunch() throws Exception {
        // Should receive nothing
        testRotation(NO_RELAUNCH_ACTIVITY_NAME, 2, 0, 0);
    }

    @Presubmit
    public void testChangeFontScaleRelaunch() throws Exception {
        // Should relaunch and receive no onConfigurationChanged()
        testChangeFontScale(FONT_SCALE_ACTIVITY_NAME, true /* relaunch */);
    }

    @Presubmit
    public void testChangeFontScaleNoRelaunch() throws Exception {
        // Should receive onConfigurationChanged() and no relaunch
        testChangeFontScale(FONT_SCALE_NO_RELAUNCH_ACTIVITY_NAME, false /* relaunch */);
    }

    private void testRotation(
            String activityName, int rotationStep, int numRelaunch, int numConfigChange)
                    throws Exception {
        launchActivity(activityName);

        final String[] waitForActivitiesVisible = new String[] {activityName};
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);

        final int initialRotation = 4 - rotationStep;
        setDeviceRotation(initialRotation);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        final int actualStackId = mAmWmState.getAmState().getTaskByActivityName(
                activityName).mStackId;
        final int displayId = mAmWmState.getAmState().getStackById(actualStackId).mDisplayId;
        final int newDeviceRotation = getDeviceRotation(displayId);
        if (newDeviceRotation == INVALID_DEVICE_ROTATION) {
            CLog.logAndDisplay(LogLevel.WARN, "Got an invalid device rotation value. "
                    + "Continuing the test despite of that, but it is likely to fail.");
        } else if (newDeviceRotation != initialRotation) {
            CLog.logAndDisplay(LogLevel.INFO, "This device doesn't support user rotation "
                    + "mode. Not continuing the rotation checks.");
            return;
        }

        for (int rotation = 0; rotation < 4; rotation += rotationStep) {
            final String logSeparator = clearLogcat();
            setDeviceRotation(rotation);
            mAmWmState.computeState(mDevice, waitForActivitiesVisible);
            assertRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange, logSeparator);
        }
    }

    private void testChangeFontScale(
            String activityName, boolean relaunch) throws Exception {
        launchActivity(activityName);
        final String[] waitForActivitiesVisible = new String[] {activityName};
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);

        setFontScale(1.0f);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);

        final int densityDpi = getGlobalDensityDpi();

        for (float fontScale = 0.85f; fontScale <= 1.3f; fontScale += 0.15f) {
            final String logSeparator = clearLogcat();
            setFontScale(fontScale);
            mAmWmState.computeState(mDevice, waitForActivitiesVisible);
            assertRelaunchOrConfigChanged(activityName, relaunch ? 1 : 0, relaunch ? 0 : 1,
                    logSeparator);

            // Verify that the display metrics are updated, and therefore the text size is also
            // updated accordingly.
            assertExpectedFontPixelSize(activityName,
                    scaledPixelsToPixels(EXPECTED_FONT_SIZE_SP, fontScale, densityDpi),
                    logSeparator);
        }
    }

    /**
     * Test updating application info when app is running. An activity with matching package name
     * must be recreated and its asset sequence number must be incremented.
     */
    public void testUpdateApplicationInfo() throws Exception {
        final String firstLogSeparator = clearLogcat();

        // Launch an activity that prints applied config.
        launchActivity(TEST_ACTIVITY_NAME);
        final int assetSeq = readAssetSeqNumber(TEST_ACTIVITY_NAME, firstLogSeparator);

        final String logSeparator = clearLogcat();
        // Update package info.
        executeShellCommand("am update-appinfo all " + componentName);
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            // Wait for activity to be resumed and asset seq number to be updated.
            try {
                return readAssetSeqNumber(TEST_ACTIVITY_NAME, logSeparator) == assetSeq + 1
                        && amState.hasActivityState(TEST_ACTIVITY_NAME, STATE_RESUMED);
            } catch (Exception e) {
                logE("Error waiting for valid state: " + e.getMessage());
                return false;
            }
        }, "Waiting asset sequence number to be updated and for activity to be resumed.");

        // Check if activity is relaunched and asset seq is updated.
        assertRelaunchOrConfigChanged(TEST_ACTIVITY_NAME, 1 /* numRelaunch */,
                0 /* numConfigChange */, logSeparator);
        final int newAssetSeq = readAssetSeqNumber(TEST_ACTIVITY_NAME, logSeparator);
        assertEquals("Asset sequence number must be incremented.", assetSeq + 1, newAssetSeq);
    }

    private static final Pattern sConfigurationPattern = Pattern.compile(
            "(.+): Configuration: \\{(.*) as.(\\d+)(.*)\\}");

    /** Read asset sequence number in last applied configuration from logs. */
    private int readAssetSeqNumber(String activityName, String logSeparator) throws Exception {
        final String[] lines = getDeviceLogsForComponent(activityName, logSeparator);
        for (int i = lines.length - 1; i >= 0; i--) {
            final String line = lines[i].trim();
            final Matcher matcher = sConfigurationPattern.matcher(line);
            if (matcher.matches()) {
                final String assetSeqNumber = matcher.group(3);
                try {
                    return Integer.valueOf(assetSeqNumber);
                } catch (NumberFormatException e) {
                    // Ignore, asset seq number is not printed when not set.
                }
            }
        }
        return 0;
    }

    // Calculate the scaled pixel size just like the device is supposed to.
    private static int scaledPixelsToPixels(float sp, float fontScale, int densityDpi) {
        final int DEFAULT_DENSITY = 160;
        float f = densityDpi * (1.0f / DEFAULT_DENSITY) * fontScale * sp;
        return (int) ((f >= 0) ? (f + 0.5f) : (f - 0.5f));
    }

    private static Pattern sDeviceDensityPattern =
            Pattern.compile(".*?-(l|m|tv|h|xh|xxh|xxxh|\\d+)dpi-.*?");

    private int getGlobalDensityDpi() throws Exception {
        final String result = getDevice().executeShellCommand("am get-config");
        final String[] lines = result.split("\n");
        if (lines.length < 1) {
            throw new IllegalStateException("Invalid config returned from device: " + result);
        }

        final Matcher matcher = sDeviceDensityPattern.matcher(lines[0]);
        if (!matcher.matches()) {
            throw new IllegalStateException("Invalid config returned from device: " + lines[0]);
        }
        switch (matcher.group(1)) {
            case "l": return 120;
            case "m": return 160;
            case "tv": return 213;
            case "h": return 240;
            case "xh": return 320;
            case "xxh": return 480;
            case "xxxh": return 640;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static final Pattern sFontSizePattern = Pattern.compile("^(.+): fontPixelSize=(.+)$");

    /** Read the font size in the last log line. */
    private void assertExpectedFontPixelSize(String activityName, int fontPixelSize,
            String logSeparator) throws Exception {
        final String[] lines = getDeviceLogsForComponent(activityName, logSeparator);
        for (int i = lines.length - 1; i >= 0; i--) {
            final String line = lines[i].trim();
            final Matcher matcher = sFontSizePattern.matcher(line);
            if (matcher.matches()) {
                assertEquals("Expected font pixel size does not match", fontPixelSize,
                        Integer.parseInt(matcher.group(2)));
                return;
            }
        }
        fail("No fontPixelSize reported from activity " + activityName);
    }
}
