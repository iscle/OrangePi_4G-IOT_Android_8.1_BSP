/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.HashMap;

/**
 * Tests that verify installing of various split APKs from host side.
 */
public class SplitTests extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    static final String PKG_NO_RESTART = "com.android.cts.norestart";
    static final String APK_NO_RESTART_BASE = "CtsNoRestartBase.apk";
    static final String APK_NO_RESTART_FEATURE = "CtsNoRestartFeature.apk";

    static final String PKG = "com.android.cts.splitapp";
    static final String CLASS = ".SplitAppTest";

    static final String APK = "CtsSplitApp.apk";

    static final String APK_mdpi = "CtsSplitApp_mdpi-v4.apk";
    static final String APK_hdpi = "CtsSplitApp_hdpi-v4.apk";
    static final String APK_xhdpi = "CtsSplitApp_xhdpi-v4.apk";
    static final String APK_xxhdpi = "CtsSplitApp_xxhdpi-v4.apk";

    private static final String APK_v7 = "CtsSplitApp_v7.apk";
    private static final String APK_fr = "CtsSplitApp_fr.apk";
    private static final String APK_de = "CtsSplitApp_de.apk";

    private static final String APK_x86 = "CtsSplitApp_x86.apk";
    private static final String APK_x86_64 = "CtsSplitApp_x86_64.apk";
    private static final String APK_armeabi_v7a = "CtsSplitApp_armeabi-v7a.apk";
    private static final String APK_armeabi = "CtsSplitApp_armeabi.apk";
    private static final String APK_arm64_v8a = "CtsSplitApp_arm64-v8a.apk";
    private static final String APK_mips64 = "CtsSplitApp_mips64.apk";
    private static final String APK_mips = "CtsSplitApp_mips.apk";

    private static final String APK_DIFF_REVISION = "CtsSplitAppDiffRevision.apk";
    private static final String APK_DIFF_REVISION_v7 = "CtsSplitAppDiffRevision_v7.apk";

    private static final String APK_DIFF_VERSION = "CtsSplitAppDiffVersion.apk";
    private static final String APK_DIFF_VERSION_v7 = "CtsSplitAppDiffVersion_v7.apk";

    private static final String APK_DIFF_CERT = "CtsSplitAppDiffCert.apk";
    private static final String APK_DIFF_CERT_v7 = "CtsSplitAppDiffCert_v7.apk";

    private static final String APK_FEATURE = "CtsSplitAppFeature.apk";
    private static final String APK_FEATURE_v7 = "CtsSplitAppFeature_v7.apk";

    static final HashMap<String, String> ABI_TO_APK = new HashMap<>();

    static {
        ABI_TO_APK.put("x86", APK_x86);
        ABI_TO_APK.put("x86_64", APK_x86_64);
        ABI_TO_APK.put("armeabi-v7a", APK_armeabi_v7a);
        ABI_TO_APK.put("armeabi", APK_armeabi);
        ABI_TO_APK.put("arm64-v8a", APK_arm64_v8a);
        ABI_TO_APK.put("mips64", APK_mips64);
        ABI_TO_APK.put("mips", APK_mips);
    }

    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        assertNotNull(mAbi);
        assertNotNull(mCtsBuild);

        getDevice().uninstallPackage(PKG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(PKG);
        getDevice().uninstallPackage(PKG_NO_RESTART);
    }

    public void testSingleBase() throws Exception {
        new InstallMultiple().addApk(APK).run();
        runDeviceTests(PKG, CLASS, "testSingleBase");
    }

    public void testDensitySingle() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_mdpi).run();
        runDeviceTests(PKG, CLASS, "testDensitySingle");
    }

    public void testDensityAll() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_mdpi).addApk(APK_hdpi).addApk(APK_xhdpi)
                .addApk(APK_xxhdpi).run();
        runDeviceTests(PKG, CLASS, "testDensityAll");
    }

    /**
     * Install first with low-resolution resources, then add a split that offers
     * higher-resolution resources.
     */
    public void testDensityBest() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_mdpi).run();
        runDeviceTests(PKG, CLASS, "testDensityBest1");

        // Now splice in an additional split which offers better resources
        new InstallMultiple().inheritFrom(PKG).addApk(APK_xxhdpi).run();
        runDeviceTests(PKG, CLASS, "testDensityBest2");
    }

    /**
     * Verify that an API-based split can change enabled/disabled state of
     * manifest elements.
     */
    public void testApi() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_v7).run();
        runDeviceTests(PKG, CLASS, "testApi");
    }

    public void testLocale() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_de).addApk(APK_fr).run();
        runDeviceTests(PKG, CLASS, "testLocale");
    }

    /**
     * Install test app with <em>single</em> split that exactly matches the
     * currently active ABI. This also explicitly forces ABI when installing.
     */
    public void testNativeSingle() throws Exception {
        final String abi = mAbi.getName();
        final String apk = ABI_TO_APK.get(abi);
        assertNotNull("Failed to find APK for ABI " + abi, apk);

        new InstallMultiple().addApk(APK).addApk(apk).run();
        runDeviceTests(PKG, CLASS, "testNative");
    }

    /**
     * Install test app with <em>single</em> split that exactly matches the
     * currently active ABI. This variant <em>does not</em> force the ABI when
     * installing, instead exercising the system's ability to choose the ABI
     * through inspection of the installed app.
     */
    public void testNativeSingleNatural() throws Exception {
        final String abi = mAbi.getName();
        final String apk = ABI_TO_APK.get(abi);
        assertNotNull("Failed to find APK for ABI " + abi, apk);

        new InstallMultiple().useNaturalAbi().addApk(APK).addApk(apk).run();
        runDeviceTests(PKG, CLASS, "testNative");
    }

    /**
     * Install test app with <em>all</em> possible ABI splits. This also
     * explicitly forces ABI when installing.
     */
    public void testNativeAll() throws Exception {
        final InstallMultiple inst = new InstallMultiple().addApk(APK);
        for (String apk : ABI_TO_APK.values()) {
            inst.addApk(apk);
        }
        inst.run();
        runDeviceTests(PKG, CLASS, "testNative");
    }

    /**
     * Install test app with <em>all</em> possible ABI splits. This variant
     * <em>does not</em> force the ABI when installing, instead exercising the
     * system's ability to choose the ABI through inspection of the installed
     * app.
     */
    public void testNativeAllNatural() throws Exception {
        final InstallMultiple inst = new InstallMultiple().useNaturalAbi().addApk(APK);
        for (String apk : ABI_TO_APK.values()) {
            inst.addApk(apk);
        }
        inst.run();
        runDeviceTests(PKG, CLASS, "testNative");
    }

    public void testDuplicateBase() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK).runExpectingFailure();
    }

    public void testDuplicateSplit() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_v7).addApk(APK_v7).runExpectingFailure();
    }

    public void testDiffCert() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_DIFF_CERT_v7).runExpectingFailure();
    }

    public void testDiffCertInherit() throws Exception {
        new InstallMultiple().addApk(APK).run();
        new InstallMultiple().inheritFrom(PKG).addApk(APK_DIFF_CERT_v7).runExpectingFailure();
    }

    public void testDiffVersion() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_DIFF_VERSION_v7).runExpectingFailure();
    }

    public void testDiffVersionInherit() throws Exception {
        new InstallMultiple().addApk(APK).run();
        new InstallMultiple().inheritFrom(PKG).addApk(APK_DIFF_VERSION_v7).runExpectingFailure();
    }

    public void testDiffRevision() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_DIFF_REVISION_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_12");
    }

    public void testDiffRevisionInheritBase() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_0");
        new InstallMultiple().inheritFrom(PKG).addApk(APK_DIFF_REVISION_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_12");
    }

    public void testDiffRevisionInheritSplit() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_v7).run();
        runDeviceTests(PKG, CLASS, "testRevision0_0");
        new InstallMultiple().inheritFrom(PKG).addApk(APK_DIFF_REVISION).run();
        runDeviceTests(PKG, CLASS, "testRevision12_0");
    }

    public void testDiffRevisionDowngrade() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_DIFF_REVISION_v7).run();
        new InstallMultiple().inheritFrom(PKG).addApk(APK_v7).runExpectingFailure();
    }

    public void testFeatureBase() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_FEATURE).run();
        runDeviceTests(PKG, CLASS, "testFeatureBase");
    }

    public void testFeatureApi() throws Exception {
        new InstallMultiple().addApk(APK).addApk(APK_FEATURE).addApk(APK_FEATURE_v7).run();
        runDeviceTests(PKG, CLASS, "testFeatureApi");
    }

    public void testInheritUpdatedBase() throws Exception {
        // TODO: flesh out this test
    }

    public void testInheritUpdatedSplit() throws Exception {
        // TODO: flesh out this test
    }

    public void testFeatureWithoutRestart() throws Exception {
        new InstallMultiple().addApk(APK).run();
        new InstallMultiple().addApk(APK_NO_RESTART_BASE).run();
        runDeviceTests(PKG, CLASS, "testBaseInstalled");
        new InstallMultiple()
                .addArg("--dont-kill")
                .inheritFrom(PKG_NO_RESTART)
                .addApk(APK_NO_RESTART_FEATURE)
                .run();
        runDeviceTests(PKG, CLASS, "testFeatureInstalled");
    }

    /**
     * Verify that installing a new version of app wipes code cache.
     */
    public void testClearCodeCache() throws Exception {
        new InstallMultiple().addApk(APK).run();
        runDeviceTests(PKG, CLASS, "testCodeCacheWrite");
        new InstallMultiple().addArg("-r").addApk(APK_DIFF_VERSION).run();
        runDeviceTests(PKG, CLASS, "testCodeCacheRead");
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        public InstallMultiple() {
            super(getDevice(), mCtsBuild, mAbi);
        }
    }

    public void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
