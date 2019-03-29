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

package android.content.cts;

import android.appsecurity.cts.Utils;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Set of tests that verify behavior of the content framework.
 */
public class SyncAdapterAccountAccessHostTest extends DeviceTestCase
        implements IAbiReceiver, IBuildReceiver {
    private static final String ACCOUNT_ACCESS_TESTS_OTHER_CERT_APK =
            "CtsSyncAccountAccessOtherCertTestCases.apk";
    private static final String ACCOUNT_ACCESS_TESTS_OTHER_CERT_PKG =
            "com.android.cts.content";

    private static final String ACCOUNT_ACCESS_TESTS_SAME_CERT_APK =
            "CtsSyncAccountAccessSameCertTestCases.apk";
    private static final String ACCOUNT_ACCESS_TESTS_SAME_CERT_PKG =
            "com.android.cts.content";

    private static final String STUBS_APK =
            "CtsSyncAccountAccessStubs.apk";
    private static final String STUBS_PKG =
            "com.android.cts.stub";

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
        getDevice().uninstallPackage(STUBS_PKG);

        assertNull(getDevice().installPackage(mBuildHelper
                .getTestFile(STUBS_APK), false, false));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getDevice().uninstallPackage(STUBS_PKG);
    }

    public void testSameCertAuthenticatorCanSeeAccount() throws Exception {
        getDevice().uninstallPackage(ACCOUNT_ACCESS_TESTS_SAME_CERT_PKG);

        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                ACCOUNT_ACCESS_TESTS_SAME_CERT_APK), false, false));
        try {
            runDeviceTests(ACCOUNT_ACCESS_TESTS_SAME_CERT_PKG,
                    "com.android.cts.content.CtsSyncAccountAccessSameCertTestCases",
                    "testAccountAccess_sameCertAsAuthenticatorCanSeeAccount");
        } finally {
            getDevice().uninstallPackage(ACCOUNT_ACCESS_TESTS_SAME_CERT_PKG);
        }
    }

    public void testOtherCertAuthenticatorCanSeeAccount() throws Exception {
        getDevice().uninstallPackage(ACCOUNT_ACCESS_TESTS_OTHER_CERT_PKG);

        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                ACCOUNT_ACCESS_TESTS_OTHER_CERT_APK), false, false));
        try {
            runDeviceTests(ACCOUNT_ACCESS_TESTS_OTHER_CERT_PKG,
                    "com.android.cts.content.CtsSyncAccountAccessOtherCertTestCases",
                    "testAccountAccess_otherCertAsAuthenticatorCanNotSeeAccount");
        } finally {
            getDevice().uninstallPackage(ACCOUNT_ACCESS_TESTS_OTHER_CERT_PKG);
        }
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
