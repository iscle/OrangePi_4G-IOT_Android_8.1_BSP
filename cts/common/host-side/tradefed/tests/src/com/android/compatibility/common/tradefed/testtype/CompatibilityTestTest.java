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
package com.android.compatibility.common.tradefed.testtype;

import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.result.ByteArrayInputStreamSource;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.suite.checker.ISystemStatusChecker;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.util.AbiUtils;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Test class for {@link CompatibilityTest}
 */
public class CompatibilityTestTest extends TestCase {

    private static final String FAKE_HOST_ARCH = "arm";
    private CompatibilityTest mTest;
    private ITestDevice mMockDevice;
    private ITestLogger mMockLogger;
    private ITestInvocationListener mMockListener;

    @Override
    public void setUp() throws Exception {
        mTest = new CompatibilityTest() {
            @Override
            protected Set<String> getAbisForBuildTargetArch() {
                return AbiUtils.getAbisForArch(FAKE_HOST_ARCH);
            }
        };
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mTest.setDevice(mMockDevice);
        mMockLogger = EasyMock.createMock(ITestLogger.class);
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    /**
     * Test that {@link CompatibilityTest#getAbis()} is returning a proper intersection of CTS
     * supported architectures and Device supported architectures.
     */
    public void testGetAbis() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.product.cpu.abilist")))
                .andReturn("arm64-v8a,armeabi-v7a,armeabi");
        Set<String> expectedAbis = new HashSet<>();
        expectedAbis.add("arm64-v8a");
        expectedAbis.add("armeabi-v7a");
        EasyMock.replay(mMockDevice);
        Set<IAbi> res = mTest.getAbis();
        assertEquals(2, res.size());
        for (IAbi abi : res) {
            assertTrue(expectedAbis.contains(abi.getName()));
        }
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test that {@link CompatibilityTest#getAbis()} is throwing an exception when none of the
     * CTS build supported abi match the device abi.
     */
    public void testGetAbis_notSupported() throws DeviceNotAvailableException {
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.product.cpu.abilist")))
                .andReturn("armeabi");
        EasyMock.replay(mMockDevice);
        try {
            mTest.getAbis();
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("None of the abi supported by this CTS build ('[armeabi-v7a, arm64-v8a]')"
                    + " are supported by the device ('[armeabi]').", e.getMessage());
        }
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test that {@link CompatibilityTest#getAbis()} is returning only the device primary abi.
     */
    public void testGetAbis_primaryAbiOnly() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue(CompatibilityTest.PRIMARY_ABI_RUN, "true");
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.product.cpu.abi")))
                .andReturn("arm64-v8a");
        Set<String> expectedAbis = new HashSet<>();
        expectedAbis.add("arm64-v8a");
        EasyMock.replay(mMockDevice);
        Set<IAbi> res = mTest.getAbis();
        assertEquals(1, res.size());
        for (IAbi abi : res) {
            assertTrue(expectedAbis.contains(abi.getName()));
        }
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test that {@link CompatibilityTest#getAbis()} is throwing an exception if the primary
     * abi is not supported.
     */
    public void testGetAbis_primaryAbiOnly_NotSupported() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue(CompatibilityTest.PRIMARY_ABI_RUN, "true");
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.product.cpu.abi")))
                .andReturn("armeabi");
        EasyMock.replay(mMockDevice);
        try {
            mTest.getAbis();
            fail("Should have thrown an exception");
        } catch (IllegalArgumentException e) {
            assertEquals("Your CTS hasn't been built with abi 'armeabi' support, "
                    + "this CTS currently supports '[armeabi-v7a, arm64-v8a]'.", e.getMessage());
        }
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test that {@link CompatibilityTest#getAbis()} is returning the list of abi supported by
     * Compatibility and the device, and not the particular CTS build.
     */
    public void testGetAbis_skipCtsArchCheck() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue(CompatibilityTest.SKIP_HOST_ARCH_CHECK, "true");
        EasyMock.expect(mMockDevice.getProperty(EasyMock.eq("ro.product.cpu.abilist")))
                .andReturn("x86_64,x86,armeabi");
        Set<String> expectedAbis = new HashSet<>();
        expectedAbis.add("x86_64");
        expectedAbis.add("x86");
        EasyMock.replay(mMockDevice);
        Set<IAbi> res = mTest.getAbis();
        assertEquals(2, res.size());
        for (IAbi abi : res) {
            assertTrue(expectedAbis.contains(abi.getName()));
        }
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link CompatibilityTest#getAbis()} when we skip the Cts side architecture check and
     * want to run x86 abi.
     */
    public void testGetAbis_skipCtsArchCheck_abiSpecified() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue(CompatibilityTest.SKIP_HOST_ARCH_CHECK, "true");
        setter.setOptionValue(CompatibilityTest.ABI_OPTION, "x86");
        Set<String> expectedAbis = new HashSet<>();
        expectedAbis.add("x86");
        EasyMock.replay(mMockDevice);
        Set<IAbi> res = mTest.getAbis();
        assertEquals(1, res.size());
        for (IAbi abi : res) {
            assertTrue(expectedAbis.contains(abi.getName()));
        }
        EasyMock.verify(mMockDevice);
    }

    /**
     * Test {@link CompatibilityTest#split()} when a shard number is specified.
     */
    public void testSplit() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("shards", "4");
        assertEquals(4, mTest.split().size());
    }

    /**
     * Test {@link CompatibilityTest#split()} when no shard number is specified.
     */
    public void testSplit_notShardable() throws Exception {
        assertNull(mTest.split());
    }

    /**
     * Test {@link CompatibilityTest#runPreModuleCheck(String, List, ITestDevice, ITestLogger)}
     * is successful when no system checker fails.
     */
    public void testRunPreModuleCheck() throws Exception {
        List<ISystemStatusChecker> systemCheckers = new ArrayList<>();
        // add 2 inop status checkers.
        systemCheckers.add(new ISystemStatusChecker() {});
        systemCheckers.add(new ISystemStatusChecker() {});
        EasyMock.replay(mMockDevice, mMockLogger);
        mTest.runPreModuleCheck("FAKE_MODULE", systemCheckers, mMockDevice, mMockLogger);
        EasyMock.verify(mMockDevice, mMockLogger);
    }

    /**
     * Test {@link CompatibilityTest#runPreModuleCheck(String, List, ITestDevice, ITestLogger)}
     * is failing and log the failure.
     */
    public void testRunPreModuleCheck_failure() throws Exception {
        List<ISystemStatusChecker> systemCheckers = new ArrayList<>();
        // add 2 inop status checkers.
        systemCheckers.add(new ISystemStatusChecker() {});
        systemCheckers.add(new ISystemStatusChecker() {
            @Override
            public boolean preExecutionCheck(ITestDevice device) {
                // fails
                return false;
            }
        });
        InputStreamSource res = new ByteArrayInputStreamSource("fake bugreport".getBytes());
        EasyMock.expect(mMockDevice.getBugreport()).andReturn(res);
        mMockLogger.testLog(EasyMock.eq("bugreport-checker-pre-module-FAKE_MODULE"),
                EasyMock.eq(LogDataType.BUGREPORT),
                EasyMock.same(res));
        EasyMock.replay(mMockDevice, mMockLogger);
        mTest.runPreModuleCheck("FAKE_MODULE", systemCheckers, mMockDevice, mMockLogger);
        EasyMock.verify(mMockDevice, mMockLogger);
    }

    /**
     * Test {@link CompatibilityTest#runPostModuleCheck(String, List, ITestDevice, ITestLogger)}
     * is successful when no system checker fails.
     */
    public void testRunPostModuleCheck() throws Exception {
        List<ISystemStatusChecker> systemCheckers = new ArrayList<>();
        // add 2 inop status checkers.
        systemCheckers.add(new ISystemStatusChecker() {});
        systemCheckers.add(new ISystemStatusChecker() {});
        EasyMock.replay(mMockDevice, mMockLogger);
        mTest.runPostModuleCheck("FAKE_MODULE", systemCheckers, mMockDevice, mMockLogger);
        EasyMock.verify(mMockDevice, mMockLogger);
    }

    /**
     * Test {@link CompatibilityTest#runPreModuleCheck(String, List, ITestDevice, ITestLogger)}
     * is failing and log the failure.
     */
    public void testRunPostModuleCheck_failure() throws Exception {
        List<ISystemStatusChecker> systemCheckers = new ArrayList<>();
        // add 2 inop status checkers.
        systemCheckers.add(new ISystemStatusChecker() {});
        systemCheckers.add(new ISystemStatusChecker() {
            @Override
            public boolean postExecutionCheck(ITestDevice device) {
                // fails
                return false;
            }
        });
        InputStreamSource res = new ByteArrayInputStreamSource("fake bugreport".getBytes());
        EasyMock.expect(mMockDevice.getBugreport()).andReturn(res);
        mMockLogger.testLog(EasyMock.eq("bugreport-checker-post-module-FAKE_MODULE"),
                EasyMock.eq(LogDataType.BUGREPORT),
                EasyMock.same(res));
        EasyMock.replay(mMockDevice, mMockLogger);
        mTest.runPostModuleCheck("FAKE_MODULE", systemCheckers, mMockDevice, mMockLogger);
        EasyMock.verify(mMockDevice, mMockLogger);
    }

    /**
     * Test {@link CompatibilityTest#run(ITestInvocationListener)} returns with no further
     * execution when there is no module to run.
     */
    public void testRun_noModules() throws Exception {
        mTest = new CompatibilityTest(1, new ModuleRepo() {
            @Override
            public boolean isInitialized() {
                return true;
            }
            @Override
            public LinkedList<IModuleDef> getModules(String serial, int shardIndex) {
                return new LinkedList<IModuleDef>();
            }
        }, 0);
        mTest.setDevice(mMockDevice);
        EasyMock.expect(mMockDevice.getSerialNumber()).andReturn("FAKE_SERIAL").times(2);
        EasyMock.replay(mMockDevice, mMockListener);
        mTest.run(mMockListener);
        EasyMock.verify(mMockDevice, mMockListener);
    }

    /**
     * Test {@link CompatibilityTest#checkSystemStatusBlackAndWhiteList()} correctly throws
     * if a system status is invalid.
     */
    public void testCheckSystemStatus_throw() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("system-status-check-whitelist", "com.does.not.exit");
        try {
            mTest.checkSystemStatusBlackAndWhiteList();
            fail("should have thrown an exception");
        } catch (RuntimeException expected) {
            // expected.
        }
    }

    /**
     * Test {@link CompatibilityTest#checkSystemStatusBlackAndWhiteList()} does not throw
     * if a system status is valid.
     */
    public void testCheckSystemStatus_pass() throws Exception {
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("skip-system-status-check",
                "com.android.tradefed.suite.checker.KeyguardStatusChecker");
        mTest.checkSystemStatusBlackAndWhiteList();
    }
}
