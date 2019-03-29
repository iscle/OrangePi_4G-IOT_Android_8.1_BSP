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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks that a given setting on the device is one of the given values
 */
@OptionClass(alias="settings-preparer")
public class SettingsPreparer extends PreconditionPreparer {

    public enum SettingType {
        SECURE,
        GLOBAL,
        SYSTEM;
    }

    /* This option must be defined, but is not explicitly marked mandatory, as subclasses of
     * the SettingsPreparer class can define mSettingName at runtime */
    @Option(name = "device-setting", description = "The setting on the device to be checked")
    protected String mSettingName = null;

    /* This option must be defined, but is not explicitly marked mandatory, as subclasses of
     * the SettingsPreparer class can define mSettingType at runtime */
    @Option(name = "setting-type",
            description = "If the setting is 'secure', 'global', or 'system'")
    protected SettingType mSettingType = null;

    @Option(name = "set-value", description = "The value to be set for the setting")
    protected String mSetValue = null;

    @Option(name = "expected-values", description = "The set of expected values of the setting")
    protected List<String> mExpectedSettingValues = new ArrayList<String>();

    @Option(name = "failure-message", description = "The text printed for an unexpected value")
    protected String mFailureMessage = null;

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {

        if (mSettingName == null) {
            throw new TargetSetupError("The \"device-setting\" option must be defined for the " +
                    "SettingsPreparer class", device.getDeviceDescriptor());
        }

        if (mSettingType == null) {
            throw new TargetSetupError("The \"setting-type\" option must be defined for the " +
                    "SettingsPreparer class", device.getDeviceDescriptor());
        }

        /* At least one of the options "set-value" and "expected-values" must be set */
        if (mSetValue == null && mExpectedSettingValues.isEmpty()) {
            throw new TargetSetupError("At least one of the options \"set-value\" and " +
                    "\"expected-values\" must be set", device.getDeviceDescriptor());
        }

        String shellCmdGet = (!mExpectedSettingValues.isEmpty()) ?
                String.format("settings get %s %s", mSettingType, mSettingName) : "";
        String shellCmdPut = (mSetValue != null) ?
                String.format("settings put %s %s %s", mSettingType, mSettingName, mSetValue) : "";


        /* Case 1: Both expected-values and set-value are given */
        if (mSetValue != null && !mExpectedSettingValues.isEmpty()) {
            // first ensure that the set-value given can be found in expected-values
            if (!mExpectedSettingValues.contains(mSetValue)) {
                throw new TargetSetupError(String.format(
                        "set-value for %s is %s, but value not found in expected-values: %s",
                        mSettingName, mSetValue, mExpectedSettingValues.toString()),
                        device.getDeviceDescriptor());
            }
            String currentSettingValue = device.executeShellCommand(shellCmdGet).trim();
            // only change unexpected setting value
            if (!mExpectedSettingValues.contains(currentSettingValue)) {
                CLog.d("Changing value for %s from %s to %s",
                        mSettingName, currentSettingValue, mSetValue);
                device.executeShellCommand(shellCmdPut);
            }
            return;
        }

        /* Case 2: Only set-value given */
        if (mSetValue != null) {
            CLog.d("Setting %s to value %s", mSettingName, mSetValue);
            device.executeShellCommand(shellCmdPut);
            return;
        }

        /* Case 3: Only expected-values given */
        String currentSettingValue = device.executeShellCommand(shellCmdGet).trim();
        if (!mExpectedSettingValues.contains(currentSettingValue)) {
            if (mFailureMessage == null) {
                mFailureMessage = String.format(
                        "Device setting \"%s\" returned \"%s\", not found in %s",
                        mSettingName, currentSettingValue, mExpectedSettingValues.toString());
            }
            throw new TargetSetupError(mFailureMessage, device.getDeviceDescriptor());
        }
    }

}
