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

import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;

import junit.framework.TestCase;

import org.easymock.EasyMock;

/**
 * Tests for {@link SettingsPreparer}
 */
public class SettingsPreparerTest extends TestCase {

    private SettingsPreparer mSettingsPreparer;
    private IBuildInfo mMockBuildInfo;
    private ITestDevice mMockDevice;
    private OptionSetter mOptionSetter;

    private static final String SHELL_CMD_GET = "settings get GLOBAL stay_on_while_plugged_in";
    private static final String SHELL_CMD_PUT_7 = "settings put GLOBAL stay_on_while_plugged_in 7";
    private static final String SHELL_CMD_PUT_8 = "settings put GLOBAL stay_on_while_plugged_in 8";

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mSettingsPreparer = new SettingsPreparer();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        EasyMock.expect(mMockDevice.getDeviceDescriptor()).andReturn(null).anyTimes();
        mMockBuildInfo = new BuildInfo("0", "", "");
        mOptionSetter = new OptionSetter(mSettingsPreparer);
        mOptionSetter.setOptionValue("device-setting", "stay_on_while_plugged_in");
        mOptionSetter.setOptionValue("setting-type", "global");
    }

    public void testCorrectOneExpected() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_GET)).andReturn("\n3\n").once();
        mOptionSetter.setOptionValue("expected-values", "3");
        EasyMock.replay(mMockDevice);
        mSettingsPreparer.run(mMockDevice, mMockBuildInfo);
    }

    public void testCorrectManyExpected() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_GET)).andReturn("\n3\n").once();
        mOptionSetter.setOptionValue("expected-values", "2");
        mOptionSetter.setOptionValue("expected-values", "3");
        mOptionSetter.setOptionValue("expected-values", "6");
        mOptionSetter.setOptionValue("expected-values", "7");
        EasyMock.replay(mMockDevice);
        mSettingsPreparer.run(mMockDevice, mMockBuildInfo);
    }

    public void testIncorrectOneExpected() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_GET)).andReturn("\n0\n").once();
        mOptionSetter.setOptionValue("expected-values", "3");
        EasyMock.replay(mMockDevice);
        try {
            mSettingsPreparer.run(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            //Expected
        }
    }

    public void testIncorrectManyExpected() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_GET)).andReturn("\n0\n").once();
        mOptionSetter.setOptionValue("expected-values", "2");
        mOptionSetter.setOptionValue("expected-values", "3");
        mOptionSetter.setOptionValue("expected-values", "6");
        mOptionSetter.setOptionValue("expected-values", "7");
        EasyMock.replay(mMockDevice);
        try {
            mSettingsPreparer.run(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            //Expected
        }
    }

    public void testCommandRun() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_PUT_7)).andReturn("\n");
        mOptionSetter.setOptionValue("set-value", "7");
        EasyMock.replay(mMockDevice);
        mSettingsPreparer.run(mMockDevice, mMockBuildInfo);
    }

    public void testCommandRunWrongSetValue() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_PUT_8)).andReturn("\n");
        mOptionSetter.setOptionValue("set-value", "8");
        mOptionSetter.setOptionValue("expected-values", "7");
        EasyMock.replay(mMockDevice);
        try {
            mSettingsPreparer.run(mMockDevice, mMockBuildInfo);
            fail("TargetSetupError expected");
        } catch (TargetSetupError e) {
            //Expected
        }
    }

    public void testIncorrectOneExpectedCommandRun() throws Exception {
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_GET)).andReturn("\n0\n").once();
        EasyMock.expect(mMockDevice.executeShellCommand(SHELL_CMD_PUT_7)).andReturn("\n");
        mOptionSetter.setOptionValue("set-value", "7");
        mOptionSetter.setOptionValue("expected-values", "7");
    }

}
