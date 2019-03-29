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

package android.appsecurity.cts;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestLoggerReceiver;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.StreamUtil;

/**
 * Creates secondary and tertiary users for use during a test suite.
 */
public class AppSecurityPreparer implements ITargetPreparer, ITargetCleaner, ITestLoggerReceiver {

    private ITestLogger mLogger;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        // Clean up any lingering users from other tests to ensure that we have
        // best shot at creating the users we need below.
        removeSecondaryUsers(device);

        final int maxUsers = device.getMaxNumberOfUsersSupported();
        try {
            if (maxUsers > 1) {
                CLog.logAndDisplay(LogLevel.INFO,
                        "Created secondary user " + device.createUser("CTS_" + System.nanoTime()));
            }
            if (maxUsers > 2) {
                CLog.logAndDisplay(LogLevel.INFO,
                        "Created secondary user " + device.createUser("CTS_" + System.nanoTime()));
            }
        } catch (IllegalStateException e) {
            InputStreamSource logcat = device.getLogcatDump();
            try {
                mLogger.testLog("AppSecurityPrep_failed_create_user", LogDataType.LOGCAT, logcat);
            } finally {
                StreamUtil.cancel(logcat);
            }
            throw new TargetSetupError("Failed to create user.", e, device.getDeviceDescriptor());
        }
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable throwable)
            throws DeviceNotAvailableException {
        removeSecondaryUsers(device);
    }

    private void removeSecondaryUsers(ITestDevice device) throws DeviceNotAvailableException {
        final int[] userIds = Utils.getAllUsers(device);
        for (int i = 1; i < userIds.length; i++) {
            device.removeUser(userIds[i]);
            CLog.logAndDisplay(LogLevel.INFO, "Destroyed secondary user " + userIds[i]);
        }
    }

    @Override
    public void setTestLogger(ITestLogger testLogger) {
        mLogger = testLogger;
    }
}
