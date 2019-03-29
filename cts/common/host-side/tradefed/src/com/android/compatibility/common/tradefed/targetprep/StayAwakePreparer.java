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
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * Modifies the 'Stay Awake' setting of the device, so that the device's screen stays on
 * whenever charging via USB
 */
@OptionClass(alias="stay-awake-preparer")
public class StayAwakePreparer extends SettingsPreparer {

    private static final String STAY_AWAKE_SETTING = "stay_on_while_plugged_in";

    /*
     * Values that are appropriate for the "Stay Awake" setting while running compatibility tests:
     * (the second bit must be 'on' to allow screen to stay awake while charging via USB)
     * 2 - Stay awake while charging via USB
     * 3 - Stay awake while changing via USB or AC
     * 6 - Stay awake while charging via USB or Wireless
     * 7 - Stay awake while charging via USB or AC or Wireless
     */
    private static final String[] STAY_AWAKE_VALUES = new String[] {"2", "3", "6", "7"};
    private static final String DEFAULT_VALUE = "7";

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {

        mSettingName = STAY_AWAKE_SETTING;
        mSettingType = SettingsPreparer.SettingType.GLOBAL;
        mSetValue = DEFAULT_VALUE;
        for (String value : STAY_AWAKE_VALUES) {
            mExpectedSettingValues.add(value);
        }
        super.run(device, buildInfo);
    }

}
