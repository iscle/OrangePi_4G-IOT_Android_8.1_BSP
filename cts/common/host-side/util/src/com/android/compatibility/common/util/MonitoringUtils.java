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

package com.android.compatibility.common.util;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.util.RunUtil;

/**
 * Utility functions related to device state monitoring during compatibility test.
 */
public class MonitoringUtils {

    private static final long CONNECTIVITY_CHECK_TIME_MS = 20 * 1000;
    private static final long CONNECTIVITY_CHECK_INTERVAL_MS = 5 * 1000;

    public static boolean checkDeviceConnectivity(ITestDevice device)
            throws DeviceNotAvailableException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < CONNECTIVITY_CHECK_TIME_MS) {
            if (device.checkConnectivity()) {
                CLog.i("Connectivity: passed check.");
                return true;
            } else {
                CLog.logAndDisplay(LogLevel.INFO,
                        "Connectivity check failed on %s, retrying in %dms",
                        device.getSerialNumber(),
                        CONNECTIVITY_CHECK_INTERVAL_MS);
                RunUtil.getDefault().sleep(CONNECTIVITY_CHECK_INTERVAL_MS);
            }
        }
        return false;
    }

    public static void checkDeviceConnectivity(ITestDevice device, ITestInvocationListener listener,
            String tag) throws DeviceNotAvailableException {
        if (!checkDeviceConnectivity(device)) {
            CLog.w("Connectivity: check failed.");
            InputStreamSource bugSource = device.getBugreport();
            listener.testLog(String.format("bugreport-connectivity-%s", tag),
                    LogDataType.TEXT, bugSource);
            bugSource.cancel();
        }
    }
}
