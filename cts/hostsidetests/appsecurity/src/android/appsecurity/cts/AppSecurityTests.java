/*
 * Copyright (C) 2009 The Android Open Source Project
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
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Set of tests that verify various security checks involving multiple apps are
 * properly enforced.
 */
public class AppSecurityTests extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {

    // testSharedUidDifferentCerts constants
    private static final String SHARED_UI_APK = "CtsSharedUidInstall.apk";
    private static final String SHARED_UI_PKG = "com.android.cts.shareuidinstall";
    private static final String SHARED_UI_DIFF_CERT_APK = "CtsSharedUidInstallDiffCert.apk";
    private static final String SHARED_UI_DIFF_CERT_PKG =
        "com.android.cts.shareuidinstalldiffcert";

    // testAppUpgradeDifferentCerts constants
    private static final String SIMPLE_APP_APK = "CtsSimpleAppInstall.apk";
    private static final String SIMPLE_APP_PKG = "com.android.cts.simpleappinstall";
    private static final String SIMPLE_APP_DIFF_CERT_APK = "CtsSimpleAppInstallDiffCert.apk";

    // testAppFailAccessPrivateData constants
    private static final String APP_WITH_DATA_APK = "CtsAppWithData.apk";
    private static final String APP_WITH_DATA_PKG = "com.android.cts.appwithdata";
    private static final String APP_WITH_DATA_CLASS =
            "com.android.cts.appwithdata.CreatePrivateDataTest";
    private static final String APP_WITH_DATA_CREATE_METHOD =
            "testCreatePrivateData";
    private static final String APP_WITH_DATA_CHECK_NOEXIST_METHOD =
            "testEnsurePrivateDataNotExist";
    private static final String APP_ACCESS_DATA_APK = "CtsAppAccessData.apk";
    private static final String APP_ACCESS_DATA_PKG = "com.android.cts.appaccessdata";

    // testInstrumentationDiffCert constants
    private static final String TARGET_INSTRUMENT_APK = "CtsTargetInstrumentationApp.apk";
    private static final String TARGET_INSTRUMENT_PKG = "com.android.cts.targetinstrumentationapp";
    private static final String INSTRUMENT_DIFF_CERT_APK = "CtsInstrumentationAppDiffCert.apk";
    private static final String INSTRUMENT_DIFF_CERT_PKG =
        "com.android.cts.instrumentationdiffcertapp";

    // testPermissionDiffCert constants
    private static final String DECLARE_PERMISSION_APK = "CtsPermissionDeclareApp.apk";
    private static final String DECLARE_PERMISSION_PKG = "com.android.cts.permissiondeclareapp";
    private static final String DECLARE_PERMISSION_COMPAT_APK = "CtsPermissionDeclareAppCompat.apk";
    private static final String DECLARE_PERMISSION_COMPAT_PKG = "com.android.cts.permissiondeclareappcompat";

    private static final String PERMISSION_DIFF_CERT_APK = "CtsUsePermissionDiffCert.apk";
    private static final String PERMISSION_DIFF_CERT_PKG =
        "com.android.cts.usespermissiondiffcertapp";

    private static final String LOG_TAG = "AppSecurityTests";

    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private File getTestAppFile(String fileName) throws FileNotFoundException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        return buildHelper.getTestFile(fileName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        assertNotNull(mCtsBuild);
    }

    /**
     * Test that an app that declares the same shared uid as an existing app, cannot be installed
     * if it is signed with a different certificate.
     */
    public void testSharedUidDifferentCerts() throws Exception {
        Log.i(LOG_TAG, "installing apks with shared uid, but different certs");
        try {
            // cleanup test apps that might be installed from previous partial test run
            getDevice().uninstallPackage(SHARED_UI_PKG);
            getDevice().uninstallPackage(SHARED_UI_DIFF_CERT_PKG);

            String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
            String installResult = getDevice().installPackage(getTestAppFile(SHARED_UI_APK),
                    false, options);
            assertNull(String.format("failed to install shared uid app, Reason: %s", installResult),
                    installResult);
            installResult = getDevice().installPackage(getTestAppFile(SHARED_UI_DIFF_CERT_APK),
                    false, options);
            assertNotNull("shared uid app with different cert than existing app installed " +
                    "successfully", installResult);
            assertEquals("INSTALL_FAILED_SHARED_USER_INCOMPATIBLE",
                    installResult.substring(0, installResult.indexOf(':')));
        } finally {
            getDevice().uninstallPackage(SHARED_UI_PKG);
            getDevice().uninstallPackage(SHARED_UI_DIFF_CERT_PKG);
        }
    }

    /**
     * Test that an app update cannot be installed over an existing app if it has a different
     * certificate.
     */
    public void testAppUpgradeDifferentCerts() throws Exception {
        Log.i(LOG_TAG, "installing app upgrade with different certs");
        try {
            // cleanup test app that might be installed from previous partial test run
            getDevice().uninstallPackage(SIMPLE_APP_PKG);

            String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
            String installResult = getDevice().installPackage(getTestAppFile(SIMPLE_APP_APK),
                    false, options);
            assertNull(String.format("failed to install simple app. Reason: %s", installResult),
                    installResult);
            installResult = getDevice().installPackage(getTestAppFile(SIMPLE_APP_DIFF_CERT_APK),
                    true /* reinstall */, options);
            assertNotNull("app upgrade with different cert than existing app installed " +
                    "successfully", installResult);
            assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE",
                    installResult.substring(0, installResult.indexOf(':')));
        } finally {
            getDevice().uninstallPackage(SIMPLE_APP_PKG);
        }
    }

    /**
     * Test that an app cannot access another app's private data.
     */
    public void testAppFailAccessPrivateData() throws Exception {
        Log.i(LOG_TAG, "installing app that attempts to access another app's private data");
        try {
            // cleanup test app that might be installed from previous partial test run
            getDevice().uninstallPackage(APP_WITH_DATA_PKG);
            getDevice().uninstallPackage(APP_ACCESS_DATA_PKG);

            String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
            String installResult = getDevice().installPackage(getTestAppFile(APP_WITH_DATA_APK),
                    false, options);
            assertNull(String.format("failed to install app with data. Reason: %s", installResult),
                    installResult);
            // run appwithdata's tests to create private data
            runDeviceTests(APP_WITH_DATA_PKG, APP_WITH_DATA_CLASS, APP_WITH_DATA_CREATE_METHOD);

            installResult = getDevice().installPackage(getTestAppFile(APP_ACCESS_DATA_APK),
                    false, options);
            assertNull(String.format("failed to install app access data. Reason: %s",
                    installResult), installResult);
            // run appaccessdata's tests which attempt to access appwithdata's private data
            runDeviceTests(APP_ACCESS_DATA_PKG);
        } finally {
            getDevice().uninstallPackage(APP_WITH_DATA_PKG);
            getDevice().uninstallPackage(APP_ACCESS_DATA_PKG);
        }
    }

    /**
     * Test that uninstall of an app removes its private data.
     */
    public void testUninstallRemovesData() throws Exception {
        Log.i(LOG_TAG, "Uninstalling app, verifying data is removed.");
        try {
            // cleanup test app that might be installed from previous partial test run
            getDevice().uninstallPackage(APP_WITH_DATA_PKG);

            String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
            String installResult = getDevice().installPackage(getTestAppFile(APP_WITH_DATA_APK),
                    false, options);
            assertNull(String.format("failed to install app with data. Reason: %s", installResult),
                    installResult);
            // run appwithdata's tests to create private data
            runDeviceTests(APP_WITH_DATA_PKG, APP_WITH_DATA_CLASS, APP_WITH_DATA_CREATE_METHOD);

            getDevice().uninstallPackage(APP_WITH_DATA_PKG);

            installResult = getDevice().installPackage(getTestAppFile(APP_WITH_DATA_APK),
                    false, options);
            assertNull(String.format("failed to install app with data second time. Reason: %s",
                    installResult), installResult);
            // run appwithdata's 'check if file exists' test
            runDeviceTests(APP_WITH_DATA_PKG, APP_WITH_DATA_CLASS,
                    APP_WITH_DATA_CHECK_NOEXIST_METHOD);
        } finally {
            getDevice().uninstallPackage(APP_WITH_DATA_PKG);
        }
    }

    /**
     * Test that an app cannot instrument another app that is signed with different certificate.
     */
    public void testInstrumentationDiffCert() throws Exception {
        Log.i(LOG_TAG, "installing app that attempts to instrument another app");
        try {
            // cleanup test app that might be installed from previous partial test run
            getDevice().uninstallPackage(TARGET_INSTRUMENT_PKG);
            getDevice().uninstallPackage(INSTRUMENT_DIFF_CERT_PKG);

            String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
            String installResult = getDevice().installPackage(
                    getTestAppFile(TARGET_INSTRUMENT_APK), false, options);
            assertNull(String.format("failed to install target instrumentation app. Reason: %s",
                    installResult), installResult);

            // the app will install, but will get error at runtime when starting instrumentation
            installResult = getDevice().installPackage(getTestAppFile(INSTRUMENT_DIFF_CERT_APK),
                    false, options);
            assertNull(String.format(
                    "failed to install instrumentation app with diff cert. Reason: %s",
                    installResult), installResult);
            // run INSTRUMENT_DIFF_CERT_PKG tests
            // this test will attempt to call startInstrumentation directly and verify
            // SecurityException is thrown
            runDeviceTests(INSTRUMENT_DIFF_CERT_PKG);
        } finally {
            getDevice().uninstallPackage(TARGET_INSTRUMENT_PKG);
            getDevice().uninstallPackage(INSTRUMENT_DIFF_CERT_PKG);
        }
    }

    /**
     * Test that an app cannot use a signature-enforced permission if it is signed with a different
     * certificate than the app that declared the permission.
     */
    public void testPermissionDiffCert() throws Exception {
        Log.i(LOG_TAG, "installing app that attempts to use permission of another app");
        try {
            // cleanup test app that might be installed from previous partial test run
            getDevice().uninstallPackage(DECLARE_PERMISSION_PKG);
            getDevice().uninstallPackage(DECLARE_PERMISSION_COMPAT_PKG);
            getDevice().uninstallPackage(PERMISSION_DIFF_CERT_PKG);

            String[] options = {AbiUtils.createAbiFlag(mAbi.getName())};
            String installResult = getDevice().installPackage(
                    getTestAppFile(DECLARE_PERMISSION_APK), false, options);
            assertNull(String.format("failed to install declare permission app. Reason: %s",
                    installResult), installResult);

            installResult = getDevice().installPackage(
                    getTestAppFile(DECLARE_PERMISSION_COMPAT_APK), false, options);
            assertNull(String.format("failed to install declare permission compat app. Reason: %s",
                    installResult), installResult);

            // the app will install, but will get error at runtime
            installResult = getDevice().installPackage(getTestAppFile(PERMISSION_DIFF_CERT_APK),
                    false, options);
            assertNull(String.format("failed to install permission app with diff cert. Reason: %s",
                    installResult), installResult);
            // run PERMISSION_DIFF_CERT_PKG tests which try to access the permission
            runDeviceTests(PERMISSION_DIFF_CERT_PKG);
        } finally {
            getDevice().uninstallPackage(DECLARE_PERMISSION_PKG);
            getDevice().uninstallPackage(DECLARE_PERMISSION_COMPAT_PKG);
            getDevice().uninstallPackage(PERMISSION_DIFF_CERT_PKG);
        }
    }

    /**
     * Tests that an arbitrary file cannot be installed using the 'cmd' command.
     */
    public void testAdbInstallFile() throws Exception {
        String output = getDevice().executeShellCommand(
                "cmd package install -S 1024 /data/local/tmp/foo.apk");
        assertEquals("Error text", "Error: APK content must be streamed\n", output);
    }

    private void runDeviceTests(String packageName) throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName);
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
