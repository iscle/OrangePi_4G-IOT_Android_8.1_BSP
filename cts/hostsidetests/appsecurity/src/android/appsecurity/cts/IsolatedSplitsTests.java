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

public class IsolatedSplitsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String PKG = "com.android.cts.isolatedsplitapp";
    private static final String TEST_CLASS = PKG + ".SplitAppTest";

    /* The feature hierarchy looks like this:

        APK_BASE <- APK_FEATURE_A <- APK_FEATURE_B
            ^------ APK_FEATURE_C

     */
    private static final String APK_BASE = "CtsIsolatedSplitApp.apk";
    private static final String APK_BASE_pl = "CtsIsolatedSplitApp_pl.apk";
    private static final String APK_FEATURE_A = "CtsIsolatedSplitAppFeatureA.apk";
    private static final String APK_FEATURE_A_pl = "CtsIsolatedSplitAppFeatureA_pl.apk";
    private static final String APK_FEATURE_B = "CtsIsolatedSplitAppFeatureB.apk";
    private static final String APK_FEATURE_B_pl = "CtsIsolatedSplitAppFeatureB_pl.apk";
    private static final String APK_FEATURE_C = "CtsIsolatedSplitAppFeatureC.apk";
    private static final String APK_FEATURE_C_pl = "CtsIsolatedSplitAppFeatureC_pl.apk";

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

    public void testInstallBase() throws Exception {
        new InstallMultiple().addApk(APK_BASE).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
    }

    public void testInstallBaseAndConfigSplit() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_BASE_pl).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadPolishLocale");
    }

    public void testInstallMissingDependency() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_B).runExpectingFailure();
    }

    public void testInstallOneFeatureSplit() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureADefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureAReceivers");
    }

    public void testInstallOneFeatureSplitAndConfigSplits() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).addApk(APK_BASE_pl)
                .addApk(APK_FEATURE_A_pl).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadPolishLocale");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureAPolishLocale");
    }

    public void testInstallDependentFeatureSplits() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).addApk(APK_FEATURE_B).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureADefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureBDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureAAndBReceivers");
    }

    public void testInstallDependentFeatureSplitsAndConfigSplits() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).addApk(APK_FEATURE_B)
                .addApk(APK_BASE_pl).addApk(APK_FEATURE_A_pl).addApk(APK_FEATURE_B_pl).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadPolishLocale");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureAPolishLocale");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureBPolishLocale");
    }

    public void testInstallAllFeatureSplits() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).addApk(APK_FEATURE_B)
                .addApk(APK_FEATURE_C).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureADefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureBDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureCDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureAAndBAndCReceivers");
    }

    public void testInstallAllFeatureSplitsAndConfigSplits() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addApk(APK_FEATURE_A).addApk(APK_FEATURE_B)
                .addApk(APK_FEATURE_C).addApk(APK_BASE_pl).addApk(APK_FEATURE_A_pl)
                .addApk(APK_FEATURE_C_pl).run();
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureADefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureBDefault");
        Utils.runDeviceTests(getDevice(), PKG, TEST_CLASS, "shouldLoadFeatureCDefault");
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
