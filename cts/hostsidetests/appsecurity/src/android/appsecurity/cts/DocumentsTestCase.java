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

package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Base class for {@link android.provider.DocumentsContract} and related test cases.
 */
abstract class DocumentsTestCase extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    protected static final String CLIENT_PKG = "com.android.cts.documentclient";
    protected static final String CLIENT_APK = "CtsDocumentClient.apk";

    protected IAbi mAbi;
    protected IBuildInfo mCtsBuild;

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

        reinstallClientPackage();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(CLIENT_PKG);
    }

    public void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }

    protected void reinstallClientPackage() throws Exception {
        getDevice().uninstallPackage(CLIENT_PKG);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(CLIENT_APK), false));
    }
}
