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

package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Set of tests that verify behavior of runtime permissions, including both
 * dynamic granting and behavior of legacy apps.
 */
public class UsesLibraryHostTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String PKG = "com.android.cts.useslibrary";

    private static final String APK = "CtsUsesLibraryApp.apk";
    private static final String APK_COMPAT = "CtsUsesLibraryAppCompat.apk";

    private IAbi mAbi;
    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        assertNotNull(mAbi);
        assertNotNull(mBuildHelper);

        getDevice().uninstallPackage(PKG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(PKG);
    }

    public void testUsesLibrary() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK), false, false));
        runDeviceTests(PKG, ".UsesLibraryTest", "testUsesLibrary");
    }

    public void testMissingLibrary() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK), false, false));
        runDeviceTests(PKG, ".UsesLibraryTest", "testMissingLibrary");
    }

    public void testDuplicateLibrary() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK), false, false));
        runDeviceTests(PKG, ".UsesLibraryTest", "testDuplicateLibrary");
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
