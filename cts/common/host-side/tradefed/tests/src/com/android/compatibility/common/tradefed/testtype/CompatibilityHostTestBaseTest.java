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

package com.android.compatibility.common.tradefed.testtype;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.JUnit4ResultForwarder;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;

import junit.framework.TestCase;

import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;

import java.util.Collections;

/**
 * Tests for the CompatibilityHostTestBase class.
 */
public class CompatibilityHostTestBaseTest extends TestCase {

    private static final String DEVICE_TEST_PKG = "com.android.foo";

    @RunWith(DeviceJUnit4ClassRunner.class)
    public static class MockTest extends CompatibilityHostTestBase {

        @Test
        public void testRunDeviceTests() throws Exception {
            runDeviceTests(DEVICE_TEST_PKG, null, null);
        }

        @Override
        protected CollectingTestListener createCollectingListener() {
            return new CollectingTestListener() {
                @Override
                public TestRunResult getCurrentRunResults() {
                    TestRunResult result = new TestRunResult();
                    TestIdentifier t1 = new TestIdentifier("class1", "test1");
                    result.testStarted(t1);
                    result.testEnded(t1, Collections.emptyMap());
                    return result;
                }
            };
        }

    }

    public void testRunMockDeviceTests() throws Exception {
        final TestIdentifier testRunDeviceTests =
                new TestIdentifier(MockTest.class.getName(), "testRunDeviceTests");

        ITestInvocationListener listener = EasyMock.createStrictMock(ITestInvocationListener.class);
        ITestDevice device = EasyMock.createMock(ITestDevice.class);

        listener.testStarted(testRunDeviceTests);
        EasyMock.expect(device.getIDevice()).andReturn(EasyMock.createMock(IDevice.class)).once();
        EasyMock.expect(device.runInstrumentationTests((RemoteAndroidTestRunner)
                EasyMock.anyObject(), (CollectingTestListener) EasyMock.anyObject())).andReturn(
                true).once();
        listener.testEnded(testRunDeviceTests, Collections.emptyMap());
        EasyMock.replay(listener, device);

        JUnitCore runnerCore = new JUnitCore();
        runnerCore.addListener(new JUnit4ResultForwarder(listener));
        Runner checkRunner = Request.aClass(MockTest.class).getRunner();
        ((IDeviceTest) checkRunner).setDevice(device);
        ((IBuildReceiver) checkRunner).setBuild(EasyMock.createMock(IBuildInfo.class));
        ((IAbiReceiver) checkRunner).setAbi(EasyMock.createMock(IAbi.class));
        runnerCore.run(checkRunner);
        EasyMock.verify(listener, device);
    }

}
