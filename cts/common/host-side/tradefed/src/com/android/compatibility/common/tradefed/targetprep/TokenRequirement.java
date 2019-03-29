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
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;

import java.util.HashSet;
import java.util.Set;

/**
 * An {@link ITargetPreparer} that allows a test module to specify tokens that a device must have
 * to run the tests contained.
 *
 * A token is string that is required by a test module and given to a device by the user, they are
 * used by the scheduler to ensure tests are scheduled on the correct devices. Eg if the user is
 * sharding the innvocation across 10 devices, they will not want to put a SIM card in every device,
 * instead they can use a single SIM card and use tokens to tell the scheduler which device should
 * be used to run the SIM card tests.
 */
public class TokenRequirement implements ITargetPreparer {

    @Option(name = "token", description = "The token a device must have to run this module")
    private Set<String> mTokens = new HashSet<>();

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        CLog.e("TokenRequirement is not expected to run");
    }

    /**
     * @return the {@link Set} of tokens required by this module.
     */
    public Set<String> getTokens() {
        return mTokens;
    }
}
