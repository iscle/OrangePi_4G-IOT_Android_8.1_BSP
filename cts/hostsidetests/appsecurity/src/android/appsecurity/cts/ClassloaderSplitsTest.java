/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.appsecurity.cts;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

public class ClassloaderSplitsTest extends DeviceTestCase implements IBuildReceiver {
    private static final String PKG = "com.android.cts.classloadersplitapp";
    private static final String TEST_CLASS = PKG + ".SplitAppTest";

    /* The feature hierarchy looks like this:

        APK_BASE (PathClassLoader)
          ^
          |
        APK_FEATURE_A (DelegateLastClassLoader)
          ^
          |
        APK_FEATURE_B (PathClassLoader)

     */

    private static final String APK_BASE = "CtsClassloaderSplitApp.apk";
    private static final String APK_FEATURE_A = "CtsClassloaderSplitAppFeatureA.apk";
    private static final String APK_FEATURE_B = "CtsClassloaderSplitAppFeatureB.apk";

    private IBuildInfo mBuildInfo;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        getDevice().uninstallPackage(PKG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getDevice().uninstallPackage(PKG);
    }

    public void testBaseClassLoader() throws Exception {
        new InstallMultiple().addApk(APK_BASE).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testBaseClassLoader");
    }

    public void testFeatureAClassLoader() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testBaseClassLoader");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testFeatureAClassLoader");
    }

    public void testFeatureBClassLoader() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).addApk(APK_FEATURE_B).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testBaseClassLoader");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testFeatureAClassLoader");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testFeatureBClassLoader");
    }

    public void testReceiverClassLoaders() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).addApk(APK_FEATURE_B).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testBaseClassLoader");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "testAllReceivers");
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        public InstallMultiple() {
            super(getDevice(), mBuildInfo, null);
        }
    }
}
