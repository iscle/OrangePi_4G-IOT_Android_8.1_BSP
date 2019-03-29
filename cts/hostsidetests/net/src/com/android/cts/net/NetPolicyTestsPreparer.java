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

package com.android.cts.net;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;

public class NetPolicyTestsPreparer implements ITargetPreparer, ITargetCleaner {
    private final static String KEY_PAROLE_DURATION = "parole_duration";
    private final static String DESIRED_PAROLE_DURATION = "0";

    private boolean mAppIdleConstsUpdated;
    private String mOriginalAppIdleConsts;

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws DeviceNotAvailableException {
        updateParoleDuration(device);
        LogUtil.CLog.d("Original app_idle_constants: " + mOriginalAppIdleConsts);
    }

    @Override
    public void tearDown(ITestDevice device, IBuildInfo buildInfo, Throwable throwable)
            throws DeviceNotAvailableException {
        if (mAppIdleConstsUpdated) {
            executeCmd(device, "settings put global app_idle_constants " + mOriginalAppIdleConsts);
        }
    }

    /**
     * Updates parole_duration with the desired value.
     */
    private void updateParoleDuration(ITestDevice device) throws DeviceNotAvailableException {
        mOriginalAppIdleConsts = executeCmd(device, "settings get global app_idle_constants");
        String newAppIdleConstants;
        final String newConstant = KEY_PAROLE_DURATION + "=" + DESIRED_PAROLE_DURATION;
        if (mOriginalAppIdleConsts == null || "null".equals(mOriginalAppIdleConsts)) {
            // app_idle_constants is initially empty, so just assign the desired value.
            newAppIdleConstants = newConstant;
        } else if (mOriginalAppIdleConsts.contains(KEY_PAROLE_DURATION)) {
            // app_idle_constants contains parole_duration, so replace it with the desired value.
            newAppIdleConstants = mOriginalAppIdleConsts.replaceAll(
                    KEY_PAROLE_DURATION + "=\\d+", newConstant);
        } else {
            // app_idle_constants didn't have parole_duration, so append the desired value.
            newAppIdleConstants = mOriginalAppIdleConsts + "," + newConstant;
        }
        executeCmd(device, "settings put global app_idle_constants " + newAppIdleConstants);
        mAppIdleConstsUpdated = true;
    }

    private String executeCmd(ITestDevice device, String cmd)
            throws DeviceNotAvailableException {
        final String output = device.executeShellCommand(cmd).trim();
        LogUtil.CLog.d("Output for '%s': %s", cmd, output);
        return output;
    }
}
