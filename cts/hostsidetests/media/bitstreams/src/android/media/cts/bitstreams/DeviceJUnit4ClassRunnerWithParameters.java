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
import com.android.tradefed.config.Option;
import com.android.tradefed.device.ITestDevice;
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
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.parameterized.BlockJUnit4ClassRunnerWithParameters;
import org.junit.runners.parameterized.ParametersRunnerFactory;
import org.junit.runners.parameterized.TestWithParameters;

/**
 * Custom JUnit4 parameterized test runner that also accommodate {@link IDeviceTest}.
 */
public class DeviceJUnit4ClassRunnerWithParameters extends BlockJUnit4ClassRunnerWithParameters
        implements IDeviceTest, IBuildReceiver, IAbiReceiver, ISetOptionReceiver {

    @Option(
        name = HostTest.SET_OPTION_NAME,
        description = HostTest.SET_OPTION_DESC
    )
    private Set<String> mKeyValueOptions = new HashSet<>();

    private ITestDevice mDevice;
    private IBuildInfo mBuildInfo;
    private IAbi mAbi;

    public DeviceJUnit4ClassRunnerWithParameters(TestWithParameters test) throws InitializationError {
        super(test);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }


    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }


    @Override
    public Description getDescription() {
        Description desc = Description.createSuiteDescription(getTestClass().getJavaClass());
        for (FrameworkMethod method : getChildren()) {
            desc.addChild(describeChild(method));
        }
        return desc;
    }

    @Override
    protected List<FrameworkMethod> getChildren() {
        List<FrameworkMethod> methods = super.getChildren();
        List<FrameworkMethod> filteredMethods = new ArrayList<>();
        for (FrameworkMethod method : methods) {
            Description desc = describeChild(method);
            if (desc.getAnnotation(Ignore.class) == null) {
                filteredMethods.add(method);
            }
        }
        return filteredMethods;
    }
    /**
     * We override createTest in order to set the device.
     */
    @Override
    public Object createTest() throws Exception {
        Object testObj = super.createTest();
        if (testObj instanceof IDeviceTest) {
            if (mDevice == null) {
                throw new IllegalArgumentException("Missing device");
            }
            ((IDeviceTest) testObj).setDevice(mDevice);
        }
        if (testObj instanceof IBuildReceiver) {
            if (mBuildInfo == null) {
                throw new IllegalArgumentException("Missing build information");
            }
            ((IBuildReceiver) testObj).setBuild(mBuildInfo);
        }
        // We are more flexible about abi information since not always available.
        if (testObj instanceof IAbiReceiver) {
            ((IAbiReceiver) testObj).setAbi(mAbi);
        }
        HostTest.setOptionToLoadedObject(testObj, new ArrayList<String>(mKeyValueOptions));
        return testObj;
    }

    public static class RunnerFactory implements ParametersRunnerFactory {
        @Override
        public Runner createRunnerForTestWithParameters(TestWithParameters test)
                throws InitializationError {
            return new DeviceJUnit4ClassRunnerWithParameters(test);
        }
    }

}
