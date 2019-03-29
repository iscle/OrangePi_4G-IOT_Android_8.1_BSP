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

package com.android.cts.tv;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;

public class TvInputManagerHostTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String TEST_APK = "CtsHostsideTvInputApp.apk";
    private static final String TEST_APK2 = "CtsHostsideTvInputMonitor.apk";
    private static final String TEST_PKG = "com.android.cts.tv.hostside";
    private static final String TEST_PKG2 = "com.android.cts.tv.hostside.app2";
    private static final String CLASS = "TvViewMonitorActivity";
    private static final String INPUT_UPDATED_STRING = "HOST_SIDE_TEST_TV_INTPUT_IS_UPDATED";
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", TEST_PKG2, TEST_PKG2, CLASS);
    private static final String FEATURE_LIVE_TV = "android.software.live_tv";

    private boolean mHasFeatureLiveTv;
    private IAbi mAbi;
    private IBuildInfo mCtsBuildInfo;
    private HashSet<String> mAvailableFeatures;

    private void installPackage(String apk) throws FileNotFoundException,
            DeviceNotAvailableException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), true));
    }

    private void uninstallPackage(String packageName, boolean shouldSucceed)
            throws DeviceNotAvailableException {
        final String result = getDevice().uninstallPackage(packageName);
        if (shouldSucceed) {
            assertNull("uninstallPackage(" + packageName + ") failed: " + result, result);
        }
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuildInfo = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertNotNull(mAbi);
        assertNotNull(mCtsBuildInfo);
        mHasFeatureLiveTv = hasDeviceFeature(FEATURE_LIVE_TV);
        if (mHasFeatureLiveTv) {
            uninstallPackage(TEST_PKG, false);
            uninstallPackage(TEST_PKG2, false);
            installPackage(TEST_APK);
            installPackage(TEST_APK2);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mHasFeatureLiveTv) {
            uninstallPackage(TEST_PKG, true);
            uninstallPackage(TEST_PKG2, true);
        }
    }

    public void testInputUpdated() throws Exception {
        if (!mHasFeatureLiveTv) {
            return;
        }
        ITestDevice device = getDevice();
        device.executeAdbCommand("logcat", "-c");
        device.executeShellCommand(START_COMMAND);
        // Re-install the input app so the monitoring app can get the onInputUpdated callback.
        installPackage(TEST_APK);
        String testString = "";
        for (int i = 0; i < 5; ++i) {
            // Try 5 times as this sometimes fails.
            String logs = device.executeAdbCommand(
                    "logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
            Scanner in = new Scanner(logs);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.contains(INPUT_UPDATED_STRING)) {
                    testString = line.split(":")[1].trim();
                }
            }
            in.close();
            if (!testString.isEmpty()) {
                break;
            }
            // Wait for the system service to handle the installation.
            Thread.currentThread().sleep(100);
        }
        assertEquals("Incorrect test string", INPUT_UPDATED_STRING, testString);
    }

    private boolean hasDeviceFeature(String requiredFeature) throws DeviceNotAvailableException {
        if (mAvailableFeatures == null) {
            // TODO: Move this logic to ITestDevice.
            String command = "pm list features";
            String commandOutput = getDevice().executeShellCommand(command);
            CLog.i("Output for command " + command + ": " + commandOutput);

            // Extract the id of the new user.
            mAvailableFeatures = new HashSet<>();
            for (String feature: commandOutput.split("\\s+")) {
                // Each line in the output of the command has the format "feature:{FEATURE_VALUE}".
                String[] tokens = feature.split(":");
                assertTrue("\"" + feature + "\" expected to have format feature:{FEATURE_VALUE}",
                        tokens.length > 1);
                assertEquals(feature, "feature", tokens[0]);
                mAvailableFeatures.add(tokens[1]);
            }
        }
        boolean result = mAvailableFeatures.contains(requiredFeature);
        if (!result) {
            CLog.logAndDisplay(LogLevel.INFO, "Device doesn't have required feature "
            + requiredFeature + ". Test won't run.");
        }
        return result;
    }
}
