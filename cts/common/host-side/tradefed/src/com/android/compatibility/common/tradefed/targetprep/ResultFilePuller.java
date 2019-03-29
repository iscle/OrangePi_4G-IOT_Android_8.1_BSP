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
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.TargetSetupError;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls files from the device after a test run and puts them into the result folder.
 */
@OptionClass(alias="result-file-puller")
public class ResultFilePuller implements ITargetCleaner {

    @Option(name="clear", description = "Whether to clear the src files and dirs before running the test")
    private boolean mClearSrc = true;

    @Option(name="src-file", description = "The file to copy to the results dir")
    private List<String> mSrcFiles = new ArrayList<>();

    @Option(name="src-dir", description = "The directory to copy to the results dir")
    private List<String> mSrcDirs = new ArrayList<>();

    @Option(name = "dest-dir", description = "The directory under the result to store the files")
    private String mDestDir;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError, BuildError,
            DeviceNotAvailableException {
        if (mClearSrc) {
            for (String file : mSrcFiles) {
                delete(device, file);
            }
            for (String dir : mSrcDirs) {
                delete(device, dir);
            }
        }
    }

    private void delete(ITestDevice device, String file) throws DeviceNotAvailableException {
        device.executeShellCommand(String.format("rm -rf %s", file));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
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
            String resultPath = resultDir.getAbsolutePath();
            for (String file : mSrcFiles) {
                pull(device, file, resultPath);
            }
            for (String dir : mSrcDirs) {
                pull(device, dir, resultPath);
            }
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    private void pull(ITestDevice device, String src, String dest) {
        String command = String.format("adb -s %s pull %s %s", device.getSerialNumber(), src, dest);
        try {
            Process p = Runtime.getRuntime().exec(new String[] {"/bin/bash", "-c", command});
            if (p.waitFor() != 0) {
                CLog.e("Failed to run %s", command);
            }
        } catch (Exception e) {
            CLog.e("Caught exception during pull.");
            CLog.e(e);
        }
    }
}
