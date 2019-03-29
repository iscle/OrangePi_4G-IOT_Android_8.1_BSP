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
package com.android.compatibility.common.tradefed.presubmit;

import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.log.LogUtil.CLog;

import junit.framework.TestCase;

/**
 * Tests that validate the CTS presubmit setup to ensure no CL will break the presubmit setup
 * itself.
 */
public class PresubmitSetupValidation extends TestCase {
    private static final String PRESUBMIT_CTS_UNIT_TESTS = "cts-unit-tests";

    /**
     * Test that the base cts unit tests configuration is still working, and has a reporter
     * template placeholder.
     */
    public void testCtsPresubmit_unit_tests() {
        IConfigurationFactory factory = ConfigurationFactory.getInstance();
        String[] presubmitCommand = {PRESUBMIT_CTS_UNIT_TESTS, "--template:map", "reporters=empty"};
        try {
            factory.createConfigurationFromArgs(presubmitCommand);
        } catch (ConfigurationException e) {
            CLog.e(e);
            fail(String.format("ConfigException '%s': One of your change is breaking the presubmit "
                    + "CTS unit tests configuration.", e.getMessage()));
        }
    }

    /**
     * Test to ensure that Zip dependency on the Apache Commons Compress coming from TradeFed is
     * properly setup. This dependency is required for some utilities of TradeFed to work.
     */
    public void testDependencyCommonsCompress() throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        // This will throw an exception if dependency isn't met.
        loader.loadClass("org.apache.commons.compress.archivers.zip.ZipFile");
    }
}
