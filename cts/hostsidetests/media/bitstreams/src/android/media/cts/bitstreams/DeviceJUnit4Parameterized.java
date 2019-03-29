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
package android.media.cts.bitstreams;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.ISetOptionReceiver;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.Parameterized;

/**
 * Custom JUnit4 parameterized test runner that also accommodate {@link IDeviceTest}.
 */
public class DeviceJUnit4Parameterized extends Parameterized
        implements IDeviceTest, IBuildReceiver, IAbiReceiver, ISetOptionReceiver {

    @Option(
        name = HostTest.SET_OPTION_NAME,
        description = HostTest.SET_OPTION_DESC
    )
    private Set<String> mKeyValueOptions = new HashSet<>();

    private ITestDevice mDevice;
    private List<Runner> mRunners;

    public DeviceJUnit4Parameterized(Class<?> klass) throws Throwable {
        super(klass);
        mRunners = super.getChildren();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        for (Runner runner : mRunners) {
            if (runner instanceof IBuildReceiver) {
                ((IBuildReceiver)runner).setBuild(buildInfo);
            }
        }
    }

    @Override
    public void setAbi(IAbi abi) {
        for (Runner runner : mRunners) {
            if (runner instanceof IAbiReceiver) {
                ((IAbiReceiver)runner).setAbi(abi);
            }
        }
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
        for (Runner runner : mRunners) {
            if (runner instanceof IDeviceTest) {
                ((IDeviceTest)runner).setDevice(device);
            }
        }
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }


    @Override
    protected List<Runner> getChildren() {
        return mRunners;
    }

    @Override
    protected void runChild(Runner runner, RunNotifier notifier) {
        try {
            OptionSetter setter = new OptionSetter(runner);
            for (String kv : mKeyValueOptions) {
                setter.setOptionValue(HostTest.SET_OPTION_NAME, kv);
            }
        } catch (ConfigurationException e) {
            CLog.w(e);
        }
        super.runChild(runner, notifier);
    }

}
