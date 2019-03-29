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
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.nio.file.Paths;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.HashMap;

/**
 * An {@link PreconditionPreparer} that collects one device file.
 */
public class DeviceFileCollector extends PreconditionPreparer {

    @Option(name = CompatibilityTest.SKIP_DEVICE_INFO_OPTION,
            shortName = 'd',
            description = "Whether device info collection should be skipped")
    private boolean mSkipDeviceInfo = false;

    @Option(name = "src-file", description = "The file path to copy to the results dir")
    private String mSrcFile;

    @Option(name = "dest-file", description = "The destination file path under the result")
    private String mDestFile;

    /**
     * If device.getProperty(key) is not value for all properties listed, the collection will be
     * skipped. If this map is empty, mSrcFile will always be collected.
     *
     * This can be specified by the following option:
     * {@code <option name="property" key="foo" value="bar"/>}
     */
    @Option(name = "property", description = "run this test on device with this property value")
    private Map<String, String> mPropertyMap = new HashMap<>();

    private File mResultFile;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) {
        if (mSkipDeviceInfo)
            return;

        if (!matchProperties(device))
            return;

        createResultDir(buildInfo);
        if (mResultFile != null && !mResultFile.isDirectory() &&
            mSrcFile != null && !mSrcFile.isEmpty()) {

            try {
                if (device.doesFileExist(mSrcFile)) {
                    device.pullFile(mSrcFile, mResultFile);
                } else {
                    CLog.w(String.format("File does not exist on device: \"%s\"", mSrcFile));
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e("Caught exception during pull.");
                CLog.e(e);
            }
        }
    }

    private void createResultDir(IBuildInfo buildInfo) {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        try {
            File resultDir = buildHelper.getResultDir();
            if (mDestFile == null || mDestFile.isEmpty()) {
                mDestFile = Paths.get(mSrcFile).getFileName().toString();
            }
            mResultFile = Paths.get(resultDir.getAbsolutePath(), mDestFile).toFile();
            resultDir = mResultFile.getParentFile();
            resultDir.mkdirs();
            if (!resultDir.isDirectory()) {
                CLog.e("%s is not a directory", resultDir.getAbsolutePath());
                return;
            }
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    private boolean matchProperties(ITestDevice device) {
        for (Map.Entry<String, String> propEntry : mPropertyMap.entrySet()) {
            try {
                String actualValue = device.getProperty(propEntry.getKey());
                if (!propEntry.getValue().equals(actualValue)) {
                    CLog.i("Skipping '%s' because property doesn't match. "
                           + "(key=%s, expected=%s, actual=%s)",
                           mSrcFile, propEntry.getKey(), propEntry.getValue(), actualValue);
                    return false;
                }
            } catch (DeviceNotAvailableException e) {
                CLog.e("Caught exception during property check.");
                CLog.e(e);
                return false;
            }
        }
        return true;
    }
}
