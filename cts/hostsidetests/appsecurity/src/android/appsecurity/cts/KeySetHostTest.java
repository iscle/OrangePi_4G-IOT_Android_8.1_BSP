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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

/**
 * Tests for Keyset based features.
 */
public class KeySetHostTest extends DeviceTestCase implements IBuildReceiver {

    private static final String RUNNER = "android.support.test.runner.AndroidJUnitRunner";

    /* package with device-side tests */
    private static final String KEYSET_TEST_PKG = "com.android.cts.keysets.testapp";
    private static final String KEYSET_TEST_APP_APK = "CtsKeySetTestApp.apk";

    /* plain test apks with different signing and upgrade keysets */
    private static final String KEYSET_PKG = "com.android.cts.keysets";
    private static final String A_SIGNED_NO_UPGRADE =
            "CtsKeySetSigningAUpgradeNone.apk";
    private static final String A_SIGNED_A_UPGRADE =
            "CtsKeySetSigningAUpgradeA.apk";
    private static final String A_SIGNED_B_UPGRADE =
            "CtsKeySetSigningAUpgradeB.apk";
    private static final String A_SIGNED_A_OR_B_UPGRADE =
            "CtsKeySetSigningAUpgradeAOrB.apk";
    private static final String B_SIGNED_A_UPGRADE =
            "CtsKeySetSigningBUpgradeA.apk";
    private static final String B_SIGNED_B_UPGRADE =
            "CtsKeySetSigningBUpgradeB.apk";
    private static final String A_AND_B_SIGNED_A_UPGRADE =
            "CtsKeySetSigningAAndBUpgradeA.apk";
    private static final String A_AND_B_SIGNED_B_UPGRADE =
            "CtsKeySetSigningAAndBUpgradeB.apk";
    private static final String A_AND_C_SIGNED_B_UPGRADE =
            "CtsKeySetSigningAAndCUpgradeB.apk";
    private static final String SHARED_USR_A_SIGNED_B_UPGRADE =
            "CtsKeySetSharedUserSigningAUpgradeB.apk";
    private static final String SHARED_USR_B_SIGNED_B_UPGRADE =
            "CtsKeySetSharedUserSigningBUpgradeB.apk";
    private static final String A_SIGNED_BAD_B_B_UPGRADE =
            "CtsKeySetSigningABadUpgradeB.apk";
    private static final String C_SIGNED_BAD_A_AB_UPGRADE =
            "CtsKeySetSigningCBadAUpgradeAB.apk";
    private static final String A_SIGNED_NO_B_B_UPGRADE =
            "CtsKeySetSigningANoDefUpgradeB.apk";
    private static final String A_SIGNED_EC_A_UPGRADE =
            "CtsKeySetSigningAUpgradeEcA.apk";
    private static final String EC_A_SIGNED_A_UPGRADE =
            "CtsKeySetSigningEcAUpgradeA.apk";

    /* package which defines the KEYSET_PERM_NAME signature permission */
    private static final String KEYSET_PERM_DEF_PKG =
            "com.android.cts.keysets_permdef";

    /* The apks defining and using the permission have both A and B as upgrade keys */
    private static final String PERM_DEF_A_SIGNED =
            "CtsKeySetPermDefSigningA.apk";
    private static final String PERM_DEF_B_SIGNED =
            "CtsKeySetPermDefSigningB.apk";
    private static final String PERM_USE_A_SIGNED =
            "CtsKeySetPermUseSigningA.apk";
    private static final String PERM_USE_B_SIGNED =
            "CtsKeySetPermUseSigningB.apk";

    private static final String PERM_TEST_CLASS =
        "com.android.cts.keysets.KeySetPermissionsTest";

    private static final String LOG_TAG = "AppsecurityHostTests";

    private File getTestAppFile(String fileName) throws FileNotFoundException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        return buildHelper.getTestFile(fileName);
    }

    /**
     * Helper method that checks that all tests in given result passed, and attempts to generate
     * a meaningful error message if they failed.
     *
     * @param result
     */
    private void assertDeviceTestsPass(TestRunResult result) {
        assertFalse(String.format("Failed to successfully run device tests for %s. Reason: %s",
                result.getName(), result.getRunFailureMessage()), result.isRunFailure());

        if (result.hasFailedTests()) {

            /* build a meaningful error message */
            StringBuilder errorBuilder = new StringBuilder("on-device tests failed:\n");
            for (Map.Entry<TestIdentifier, TestResult> resultEntry :
                result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            fail(errorBuilder.toString());
        }
    }

    /**
     * Helper method that checks that all tests in given result passed, and attempts to generate
     * a meaningful error message if they failed.
     *
     * @param result
     */
    private void assertDeviceTestsFail(String msg, TestRunResult result) {
        assertFalse(String.format("Failed to successfully run device tests for %s. Reason: %s",
                result.getName(), result.getRunFailureMessage()), result.isRunFailure());

        if (!result.hasFailedTests()) {
            fail(msg);
        }
    }

    /**
     * Helper method that will run the specified packages tests on device.
     *
     * @param pkgName Android application package for tests
     * @return <code>true</code> if all tests passed.
     * @throws DeviceNotAvailableException if connection to device was lost.
     */
    private boolean runDeviceTests(String pkgName) throws DeviceNotAvailableException {
        return runDeviceTests(pkgName, null, null);
    }

    /**
     * Helper method that will run the specified packages tests on device.
     *
     * @param pkgName Android application package for tests
     * @return <code>true</code> if all tests passed.
     * @throws DeviceNotAvailableException if connection to device was lost.
     */
    private boolean runDeviceTests(String pkgName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        TestRunResult runResult = doRunTests(pkgName, testClassName, testMethodName);
        return !runResult.hasFailedTests();
    }

    /**
     * Helper method to run tests and return the listener that collected the results.
     *
     * @param pkgName Android application package for tests
     * @return the {@link TestRunResult}
     * @throws DeviceNotAvailableException if connection to device was lost.
     */
    private TestRunResult doRunTests(String pkgName, String testClassName,
            String testMethodName) throws DeviceNotAvailableException {

        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(pkgName,
                RUNNER, getDevice().getIDevice());
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        }
        CollectingTestListener listener = new CollectingTestListener();
        getDevice().runInstrumentationTests(testRunner, listener);
        return listener.getCurrentRunResults();
    }

    /**
     * Helper method which installs a package and an upgrade to it.
     *
     * @param pkgName - package name of apk.
     * @param firstApk - first apk to install
     * @param secondApk - apk to which we attempt to upgrade
     * @param expectedResult - null if successful, otherwise expected error.
     */
    private String testPackageUpgrade(String pkgName, String firstApk,
             String secondApk) throws Exception {
        String installResult;
        try {

            /* cleanup test apps that might be installed from previous partial test run */
            mDevice.uninstallPackage(pkgName);

            installResult = mDevice.installPackage(getTestAppFile(firstApk),
                    false);
            /* we should always succeed on first-install */
            assertNull(String.format("failed to install %s, Reason: %s", pkgName,
                       installResult), installResult);

            /* attempt to install upgrade */
            installResult = mDevice.installPackage(getTestAppFile(secondApk),
                    true);
        } finally {
            mDevice.uninstallPackage(pkgName);
        }
        return installResult;
    }
    /**
     * A reference to the device under test.
     */
    private ITestDevice mDevice;

    private IBuildInfo mCtsBuild;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        assertNotNull(mCtsBuild);

        mDevice = getDevice();
    }

    /**
     * Tests for KeySet based key rotation
     */

    /*
     * Check if an apk which does not specify an upgrade-key-set may be upgraded
     * to an apk which does.
     */
    public void testNoKSToUpgradeKS() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_NO_UPGRADE, A_SIGNED_A_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from no specified upgrade-key-set"
                + "to version with specified upgrade-key-set, Reason: %s", installResult),
                installResult);
    }

    /*
     * Check if an apk which does specify an upgrade-key-set may be upgraded
     * to an apk which does not.
     */
    public void testUpgradeKSToNoKS() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_A_UPGRADE, A_SIGNED_NO_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from specified upgrade-key-set"
                + "to version without specified upgrade-key-set, Reason: %s", installResult),
                installResult);
    }

    /*
     * Check if an apk signed by a key other than the upgrade keyset can update
     * an app
     */
    public void testUpgradeKSWithWrongKey() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_A_UPGRADE, B_SIGNED_A_UPGRADE);
        assertNotNull("upgrade to improperly signed app succeeded!", installResult);
    }

    /*
     * Check if an apk signed by its signing key, which is not an upgrade key,
     * can upgrade an app.
     */
    public void testUpgradeKSWithWrongSigningKey() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_B_UPGRADE, A_SIGNED_B_UPGRADE);
         assertNotNull("upgrade to improperly signed app succeeded!",
                 installResult);
    }

    /*
     * Check if an apk signed by its upgrade key, which is not its signing key,
     * can upgrade an app.
     */
    public void testUpgradeKSWithUpgradeKey() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_B_UPGRADE, B_SIGNED_B_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from one signed by key-a"
                 + "to version signed by upgrade-key-set key-b, Reason: %s", installResult),
                 installResult);
    }

    /*
     * Check if an apk signed by its upgrade key, which is its signing key, can
     * upgrade an app.
     */
    public void testUpgradeKSWithSigningUpgradeKey() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_A_UPGRADE, A_SIGNED_A_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from one signed by key-a"
                    + "to version signed by upgrade-key-set key-b, Reason: %s", installResult),
                    installResult);
    }

    /*
     * Check if an apk signed by multiple keys, one of which is its upgrade key,
     * can upgrade an app.
     */
    public void testMultipleUpgradeKSWithUpgradeKey() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_A_UPGRADE,
                A_AND_B_SIGNED_A_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from one signed by key-a"
                + "to version signed by upgrade-key-set key-b, Reason: %s", installResult),
                installResult);
    }

    /*
     * Check if an apk signed by multiple keys, its signing keys,
     * but none of which is an upgrade key, can upgrade an app.
     */
    public void testMultipleUpgradeKSWithSigningKey() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_AND_C_SIGNED_B_UPGRADE,
                A_AND_C_SIGNED_B_UPGRADE);
        assertNotNull("upgrade to improperly signed app succeeded!", installResult);
    }

    /*
     * Check if an apk which defines multiple (two) upgrade keysets is
     * upgrade-able by either.
     */
    public void testUpgradeKSWithMultipleUpgradeKeySetsFirstKey() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_A_OR_B_UPGRADE,
                A_SIGNED_A_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from one signed by key-a"
                + "to one signed by first upgrade keyset key-a, Reason: %s", installResult),
                installResult);
        installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_A_OR_B_UPGRADE,
                B_SIGNED_B_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from one signed by key-a"
                + "to one signed by second upgrade keyset key-b, Reason: %s", installResult),
                installResult);
    }

    /**
     * Helper method which installs a package defining a permission and a package
     * using the permission, and then rotates the signing keys for one of them.
     * A device-side test is then used to ascertain whether or not the permission
     * was appropriately gained or lost.
     *
     * @param permDefApk - apk to install which defines the sig-permissoin
     * @param permUseApk - apk to install which declares it uses the permission
     * @param upgradeApk - apk to install which upgrades one of the first two
     * @param hasPermBeforeUpgrade - whether we expect the consuming app to have
     *        the permission before the upgrade takes place.
     * @param hasPermAfterUpgrade - whether we expect the consuming app to have
     *        the permission after the upgrade takes place.
     */
    private void testKeyRotationPerm(String permDefApk, String permUseApk,
            String upgradeApk, boolean hasPermBeforeUpgrade,
            boolean hasPermAfterUpgrade) throws Exception {
        try {

            /* cleanup test apps that might be installed from previous partial test run */
            mDevice.uninstallPackage(KEYSET_PKG);
            mDevice.uninstallPackage(KEYSET_PERM_DEF_PKG);
            mDevice.uninstallPackage(KEYSET_TEST_PKG);

            /* install PERM_DEF, KEYSET_APP and KEYSET_TEST_APP */
            String installResult = mDevice.installPackage(
                    getTestAppFile(permDefApk), false);
            assertNull(String.format("failed to install keyset perm-def app, Reason: %s",
                       installResult), installResult);
            installResult = getDevice().installPackage(
                    getTestAppFile(permUseApk), false);
            assertNull(String.format("failed to install keyset test app. Reason: %s",
                    installResult), installResult);
            installResult = getDevice().installPackage(
                    getTestAppFile(KEYSET_TEST_APP_APK), false);
            assertNull(String.format("failed to install keyset test app. Reason: %s",
                    installResult), installResult);

            /* verify package does have perm */
            TestRunResult result = doRunTests(KEYSET_TEST_PKG, PERM_TEST_CLASS,
                    "testHasPerm");
            if (hasPermBeforeUpgrade) {
                assertDeviceTestsPass(result);
            } else {
                assertDeviceTestsFail(" has permission permission it should not have.", result);
            }

            /* rotate keys */
            installResult = mDevice.installPackage(getTestAppFile(upgradeApk),
                    true);
            result = doRunTests(KEYSET_TEST_PKG, PERM_TEST_CLASS,
                    "testHasPerm");
            if (hasPermAfterUpgrade) {
                assertDeviceTestsPass(result);
            } else {
                assertDeviceTestsFail(KEYSET_PKG + " has permission it should not have.", result);
            }
        } finally {
            mDevice.uninstallPackage(KEYSET_PKG);
            mDevice.uninstallPackage(KEYSET_PERM_DEF_PKG);
            mDevice.uninstallPackage(KEYSET_TEST_PKG);
        }
    }

    /*
     * Check if an apk gains signature-level permission after changing to a new
     * signature, for which a permission should be granted.
     */
    public void testUpgradeSigPermGained() throws Exception {
        testKeyRotationPerm(PERM_DEF_A_SIGNED, PERM_USE_B_SIGNED, PERM_USE_A_SIGNED,
                false, true);
    }

    /*
     * Check if an apk loses signature-level permission after changing to a new
     * signature, from one for which a permission was previously granted.
     */
    public void testUpgradeSigPermLost() throws Exception {
        testKeyRotationPerm(PERM_DEF_A_SIGNED, PERM_USE_A_SIGNED, PERM_USE_B_SIGNED,
                true, false);
    }

    /*
     * Check if an apk gains signature-level permission after the app defining
     * it rotates to the same signature.
     */
    public void testUpgradeDefinerSigPermGained() throws Exception {
        testKeyRotationPerm(PERM_DEF_A_SIGNED, PERM_USE_B_SIGNED, PERM_DEF_B_SIGNED,
                false, true);
    }

    /*
     * Check if an apk loses signature-level permission after the app defining
     * it rotates to a different signature.
     */
    public void testUpgradeDefinerSigPermLost() throws Exception {
        testKeyRotationPerm(PERM_DEF_A_SIGNED, PERM_USE_A_SIGNED, PERM_DEF_B_SIGNED,
                true, false);
    }

    /*
     * Check if an apk which indicates it uses a sharedUserId and defines an
     * upgrade keyset is allowed to rotate to that keyset.
     */
    public void testUpgradeSharedUser() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, SHARED_USR_A_SIGNED_B_UPGRADE,
                SHARED_USR_B_SIGNED_B_UPGRADE);
        assertNotNull("upgrade allowed for app with shareduserid!", installResult);
    }

    /*
     * Check that an apk with an upgrade key represented by a bad public key
     * fails to install.
     */
    public void testBadUpgradeBadPubKey() throws Exception {
        mDevice.uninstallPackage(KEYSET_PKG);
        String installResult = mDevice.installPackage(getTestAppFile(A_SIGNED_BAD_B_B_UPGRADE),
                false);
        assertNotNull("Installation of apk with upgrade key referring to a bad public key succeeded!",
                installResult);
    }

    /*
     * Check that an apk with an upgrade keyset that includes a bad public key fails to install.
     */
    public void testBadUpgradeMissingPubKey() throws Exception {
        mDevice.uninstallPackage(KEYSET_PKG);
        String installResult = mDevice.installPackage(getTestAppFile(C_SIGNED_BAD_A_AB_UPGRADE),
                false);
        assertNotNull("Installation of apk with upgrade key referring to a bad public key succeeded!",
                installResult);
    }

    /*
     * Check that an apk with an upgrade key that has no corresponding public key fails to install.
     */
    public void testBadUpgradeNoPubKey() throws Exception {
        mDevice.uninstallPackage(KEYSET_PKG);
        String installResult = mDevice.installPackage(getTestAppFile(A_SIGNED_NO_B_B_UPGRADE),
                false);
        assertNotNull("Installation of apk with upgrade key referring to a bad public key succeeded!",
                installResult);
    }

    /*
     * Check if an apk signed by RSA pub key can upgrade to apk signed by EC key.
     */
    public void testUpgradeKSRsaToEC() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, A_SIGNED_EC_A_UPGRADE,
                EC_A_SIGNED_A_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from one signed by RSA key"
                 + "to version signed by EC upgrade-key-set, Reason: %s", installResult),
                 installResult);
    }

    /*
     * Check if an apk signed by EC pub key can upgrade to apk signed by RSA key.
     */
    public void testUpgradeKSECToRSA() throws Exception {
        String installResult = testPackageUpgrade(KEYSET_PKG, EC_A_SIGNED_A_UPGRADE,
                A_SIGNED_EC_A_UPGRADE);
        assertNull(String.format("failed to upgrade keyset app from one signed by EC key"
                 + "to version signed by RSA upgrade-key-set, Reason: %s", installResult),
                 installResult);
    }
}
