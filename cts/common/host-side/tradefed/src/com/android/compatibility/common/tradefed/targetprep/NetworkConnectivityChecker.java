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

package com.android.compatibility.common.tradefed.targetprep;

import com.android.compatibility.common.util.MonitoringUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.suite.checker.ISystemStatusChecker;

/**
 * Checks network connectivity status on device after module execution.
 */
public class NetworkConnectivityChecker implements ISystemStatusChecker {

    // Only report is as failed (capture bugreport) when status goes from pass-> fail
    private boolean mIsFailed = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean postExecutionCheck(ITestDevice device) throws DeviceNotAvailableException {
        if (!MonitoringUtils.checkDeviceConnectivity(device)) {
            if (mIsFailed) {
                CLog.w("NetworkConnectivityChecker is still failing on %s.",
                        device.getSerialNumber());
                return true;
            }
            mIsFailed = true;
            return false;
        }
        mIsFailed = false;
        return true;
    }
}
