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
package com.android.compatibility.common.tradefed.targetprep;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.BusinessLogic;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Pushes business Logic to the host and the test device, for use by test cases in the test suite.
 */
@OptionClass(alias="business-logic-preparer")
public class BusinessLogicPreparer implements ITargetCleaner {

    /* Placeholder in the service URL for the suite to be configured */
    private static final String SUITE_PLACEHOLDER = "{suite-name}";

    /* String for creating files to store the business logic configuration on the host */
    private static final String FILE_LOCATION = "business-logic";
    /* Extension of business logic files */
    private static final String FILE_EXT = ".bl";

    @Option(name = "business-logic-url", description = "The URL to use when accessing the " +
            "business logic service, parameters not included", mandatory = true)
    private String mUrl;

    @Option(name = "business-logic-api-key", description = "The API key to use when accessing " +
            "the business logic service.", mandatory = true)
    private String mApiKey;

    @Option(name = "cleanup", description = "Whether to remove config files from the test " +
            "target after test completion.")
    private boolean mCleanup = true;

    private String mDeviceFilePushed;
    private String mHostFilePushed;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        // Piece together request URL
        String requestString = String.format("%s?key=%s", mUrl.replace(SUITE_PLACEHOLDER,
                TestSuiteInfo.getInstance().getName()), mApiKey);
        // Retrieve business logic string from service
        String businessLogicString = null;
        try {
            URL request = new URL(requestString);
            businessLogicString = StreamUtil.getStringFromStream(request.openStream());
        } catch (IOException e) {
            throw new TargetSetupError(String.format(
                    "Cannot connect to business logic service for suite %s",
                    TestSuiteInfo.getInstance().getName()), e, device.getDeviceDescriptor());
        }
        // Push business logic string to host file
        try {
            File hostFile = FileUtil.createTempFile(FILE_LOCATION, FILE_EXT);
            FileUtil.writeToFile(businessLogicString, hostFile);
            mHostFilePushed = hostFile.getAbsolutePath();
            CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
            buildHelper.setBusinessLogicHostFile(hostFile);
        } catch (IOException e) {
            throw new TargetSetupError(String.format(
                    "Retrieved business logic for suite %s could not be written to host",
                    TestSuiteInfo.getInstance().getName()), device.getDeviceDescriptor());
        }
        // Push business logic string to device file
        removeDeviceFile(device); // remove any existing business logic file from device
        if (device.pushString(businessLogicString, BusinessLogic.DEVICE_FILE)) {
            mDeviceFilePushed = BusinessLogic.DEVICE_FILE;
        } else {
            throw new TargetSetupError(String.format(
                    "Retrieved business logic for suite %s could not be written to device %s",
                    TestSuiteInfo.getInstance().getName(), device.getSerialNumber()), device.getDeviceDescriptor());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        // Clean up host file
        if (mCleanup) {
            if (mHostFilePushed != null) {
                FileUtil.deleteFile(new File(mHostFilePushed));
            }
            if (mDeviceFilePushed != null && !(e instanceof DeviceNotAvailableException)) {
                removeDeviceFile(device);
            }
        }
    }

    /** Remove business logic file from the device */
    private static void removeDeviceFile(ITestDevice device) throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("rm -rf %s", BusinessLogic.DEVICE_FILE));
    }
}
