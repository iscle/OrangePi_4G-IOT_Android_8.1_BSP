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
package com.android.compatibility.common.tradefed.testtype.retry;

import static org.junit.Assert.*;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.tradefed.testtype.suite.CompatibilityTestSuite;
import com.android.compatibility.common.tradefed.util.RetryFilterHelper;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Unit tests for {@link RetryFactoryTest}.
 */
@RunWith(JUnit4.class)
public class RetryFactoryTestTest {

    private RetryFactoryTest mFactory;
    private ITestInvocationListener mMockListener;
    private RetryFilterHelper mSpyFilter;

    /**
     * A {@link CompatibilityTest} that does not run anything.
     */
    @OptionClass(alias = "compatibility")
    public static class VoidCompatibilityTest extends CompatibilityTestSuite {
        @Override
        public LinkedHashMap<String, IConfiguration> loadTests() {
            return new LinkedHashMap<>();
        }

        @Override
        public Collection<IRemoteTest> split(int shardCountHint) {
            List<IRemoteTest> tests = new ArrayList<>();
            for (int i = 0; i < shardCountHint; i++) {
                tests.add(new StubTest());
            }
            return tests;
        }
    }

    @Before
    public void setUp() {
        mSpyFilter = new RetryFilterHelper() {
            @Override
            public void validateBuildFingerprint(ITestDevice device)
                    throws DeviceNotAvailableException {
                // do nothing
            }
            @Override
            public void setCommandLineOptionsFor(Object obj) {
                // do nothing
            }
            @Override
            public void populateFiltersBySubPlan() {
                // do nothing
            }
        };
        mFactory = new RetryFactoryTest() {
            @Override
            RetryFilterHelper createFilterHelper(CompatibilityBuildHelper buildHelper) {
                return mSpyFilter;
            }
            @Override
            CompatibilityTestSuite createTest() {
                return new VoidCompatibilityTest();
            }
        };
        mMockListener = EasyMock.createMock(ITestInvocationListener.class);
    }

    /**
     * Tests that the CompatibilityTest created can receive all the options without throwing.
     */
    @Test
    public void testRetry_receiveOption() throws Exception {
        OptionSetter setter = new OptionSetter(mFactory);
        setter.setOptionValue("retry", "10599");
        EasyMock.replay(mMockListener);
        mFactory.run(mMockListener);
        EasyMock.verify(mMockListener);
    }

    /**
     * Assert that the {@link RetryFactoryTest#split(int)} calls the
     * {@link CompatibilityTestSuite#split(int)} after applying all the filters.
     */
    @Test
    public void testRetry_split() throws Exception {
        EasyMock.replay(mMockListener);
        Collection<IRemoteTest> res = mFactory.split(2);
        assertEquals(2, res.size());
        EasyMock.verify(mMockListener);
    }
}
