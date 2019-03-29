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

package com.android.compatibility.common.tradefed.targetprep;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.util.CollectorUtil;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * An {@link ITargetCleaner} that prepares and pulls report logs.
 */
public class ReportLogCollector implements ITargetCleaner {

    @Option(name= "src-dir", description = "The directory to copy to the results dir")
    private String mSrcDir;

    @Option(name = "dest-dir", description = "The directory under the result to store the files")
    private String mDestDir;

    @Option(name = "temp-dir", description = "The temp directory containing host-side report logs")
    private String mTempReportFolder;

    @Option(name = "device-dir", description = "Create unique directory for each device")
    private boolean mDeviceDir;

    public ReportLogCollector() {
    }

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        prepareReportLogContainers(device, buildInfo);
    }

    private void prepareReportLogContainers(ITestDevice device, IBuildInfo buildInfo) {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        try {
            File resultDir = buildHelper.getResultDir();
            if (mDestDir != null) {
                resultDir = new File(resultDir, mDestDir);
            }
            resultDir.mkdirs();
            if (!resultDir.isDirectory()) {
                CLog.e("%s is not a directory", resultDir.getAbsolutePath());
                return;
            }
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e) {
        // Pull report log files from device.
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(buildInfo);
        try {
            File resultDir = buildHelper.getResultDir();
            if (mDestDir != null) {
                resultDir = new File(resultDir, mDestDir);
            }
            resultDir.mkdirs();
            if (!resultDir.isDirectory()) {
                CLog.e("%s is not a directory", resultDir.getAbsolutePath());
                return;
            }
            String tmpDirName = mTempReportFolder;
            if (mDeviceDir) {
                tmpDirName = tmpDirName.replaceAll("/$", "");
                tmpDirName += "-" + device.getSerialNumber();
            }
            final File hostReportDir = FileUtil.createNamedTempDir(tmpDirName);
            if (!hostReportDir.isDirectory()) {
                CLog.e("%s is not a directory", hostReportDir.getAbsolutePath());
                return;
            }
            String resultPath = resultDir.getAbsolutePath();
            CollectorUtil.pullFromDevice(device, mSrcDir, resultPath);
            CollectorUtil.pullFromHost(hostReportDir, resultDir);
            CollectorUtil.reformatRepeatedStreams(resultDir);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
