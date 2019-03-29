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
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.TargetSetupError;

/**
 * Checks that a device property is as expected
 */
@OptionClass(alias="property-check")
public class PropertyCheck extends PreconditionPreparer {

    @Option(name = "property-name", description = "The name of the property to check",
            mandatory = true)
    protected String mPropertyName = null;

    @Option(name = "expected-value", description = "The expected value of the property",
            mandatory = true)
    protected String mExpectedPropertyValue = null;

    @Option(name = "throw-error",
            description = "Whether to throw an error for an unexpected property value")
    protected boolean mThrowError = false;

    @Override
    public void run(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {

        String propertyValue = device.getProperty(mPropertyName);
        if (propertyValue == null) {
            logWarning("Property \"%s\" not found on device, cannot verify value \"%s\" ",
                    mPropertyName, mExpectedPropertyValue);
            return;
        }

        if (!mExpectedPropertyValue.equalsIgnoreCase(propertyValue)) {
            String msg = String.format("Expected \"%s\" but found \"%s\" for property: %s",
                    mExpectedPropertyValue, propertyValue, mPropertyName);
            // Handle unexpected property value with either exception or warning
            if(mThrowError) {
                throw new TargetSetupError(msg, device.getDeviceDescriptor());
            } else {
                logWarning(msg);
            }
        }
    }

}
