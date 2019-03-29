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

package android.content.cts;

import android.appsecurity.cts.Utils;
import android.platform.test.annotations.SecurityTest;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Tests that invalid authorities are cleared from the sync data files on boot.
 * Otherwise a malicious app can effectively DOS the filesystem and the user can only get out of it
 * via a factory reset.
 */
@SecurityTest
public class InvalidSyncAuthoritiesHostTest extends DeviceTestCase implements IBuildReceiver {

    private static final String DEVICE_TEST_PACKAGE = "android.content.sync.cts";
    private static final String DEVICE_TEST_CLASS = ".InvalidSyncAuthoritiesDeviceTest";
    private static final String DEVICE_TEST_APK = "CtsSyncInvalidAccountAuthorityTestCases.apk";

    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setBuild(IBuildInfo iBuildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(iBuildInfo);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getDevice().uninstallPackage(DEVICE_TEST_PACKAGE);

        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(DEVICE_TEST_APK), false,
                false));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            runDeviceTests(DEVICE_TEST_PACKAGE, DEVICE_TEST_CLASS, "removeTestAccount");
        } catch (AssertionError exc) {
            // Test account got left on the device. This test should still pass.
            LogUtil.CLog.w("removeTestAccount failed: " + exc.getMessage());
        }
        getDevice().uninstallPackage(DEVICE_TEST_PACKAGE);
    }

    public void testInvalidEntriesClearedOnBoot() throws Exception {
        runDeviceTests(DEVICE_TEST_PACKAGE, DEVICE_TEST_CLASS, "populateAndTestSyncAutomaticallyBeforeReboot");
        getDevice().reboot();
        runDeviceTests(DEVICE_TEST_PACKAGE, DEVICE_TEST_CLASS, "testSyncAutomaticallyAfterReboot");
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
