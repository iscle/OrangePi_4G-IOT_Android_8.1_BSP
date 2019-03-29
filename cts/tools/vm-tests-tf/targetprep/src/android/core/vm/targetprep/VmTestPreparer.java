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
package android.core.vm.targetprep;

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
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

/**
 * Configures the device to run VM tests.
 */
@OptionClass(alias="vm-test-preparer")
public class VmTestPreparer implements ITargetCleaner {

    private static final String JAR_FILE = "android.core.vm-tests-tf.jar";
    private static final String TEMP_DIR = "/data/local/tmp";
    private static final String VM_TEMP_DIR = TEMP_DIR +"/vm-tests";

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        CompatibilityBuildHelper helper = new CompatibilityBuildHelper(buildInfo);
        if (!installVmPrereqs(device, helper)) {
            throw new RuntimeException(String.format(
                    "Failed to install vm-tests prereqs on device %s",
                    device.getSerialNumber()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable e)
            throws DeviceNotAvailableException {
        cleanupDeviceFiles(device);
    }

    /**
     * Install pre-requisite jars for running vm-tests, creates temp directories for test.
     *
     * @param device the {@link ITestDevice}
     * @param ctsBuild the {@link CompatibilityBuildHelper}
     * @throws DeviceNotAvailableException
     * @return true if test jar files are extracted and pushed to device successfully
     */
    private boolean installVmPrereqs(ITestDevice device, CompatibilityBuildHelper ctsBuild)
            throws DeviceNotAvailableException {
        cleanupDeviceFiles(device);
        // Creates temp directory recursively. We also need to create the dalvik-cache directory
        // which is used by the dalvikvm to optimize things. Without the dalvik-cache, there will be
        // a sigsev thrown by the vm.
        createRemoteDir(device, VM_TEMP_DIR + "/dalvik-cache" );
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            File localTmpDir = FileUtil.createTempDir("cts-vm", tmpDir);
            File jarFile = ctsBuild.getTestFile(JAR_FILE);
            if (!jarFile.exists()) {
                CLog.e("Missing jar file %s", jarFile.getPath());
                return false;
            }
            ZipFile zipFile = new ZipFile(jarFile);
            FileUtil.extractZip(zipFile, localTmpDir);
            File localTestTmpDir = new File(localTmpDir, "tests");
            if (!device.pushDir(localTestTmpDir, VM_TEMP_DIR)) {
                CLog.e("Failed to push vm test files");
                return false;
            }
            FileUtil.recursiveDelete(localTmpDir);
        } catch (IOException e) {
            CLog.e("Failed to extract jar file %s and sync it to device %s.",
                    JAR_FILE, device.getSerialNumber());
            return false;
        }
        return true;
    }

    /**
     * Removes temporary file directory from device
     *
     * @param device
     * @throws DeviceNotAvailableException
     */
    private void cleanupDeviceFiles(ITestDevice device) throws DeviceNotAvailableException {
        if (device.doesFileExist(VM_TEMP_DIR)) {
            device.executeShellCommand(String.format("rm -r %s", VM_TEMP_DIR));
        }
    }

    /**
     * Creates the file directory recursively in the device.
     *
     * @param device the {@link ITestDevice}
     * @param remoteFilePath the absolute path.
     * @throws DeviceNotAvailableException
     */
    private void createRemoteDir(ITestDevice device, String remoteFilePath)
            throws DeviceNotAvailableException {
        if (device.doesFileExist(remoteFilePath)) {
            return;
        }
        if (!(device.doesFileExist(TEMP_DIR))) {
            CLog.e("Error: %s does not exist", TEMP_DIR);
        }
        device.executeShellCommand(String.format("mkdir %s", VM_TEMP_DIR));
        device.executeShellCommand(String.format("mkdir %s", remoteFilePath));
    }
}
