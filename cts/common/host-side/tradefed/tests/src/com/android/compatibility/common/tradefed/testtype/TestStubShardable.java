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
package com.android.compatibility.common.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IStrictShardableTest;

import java.util.ArrayList;

/**
 * A test Stub that can be used to fake some runs.
 */
public class TestStubShardable extends TestStub implements IStrictShardableTest {

    @Override
    public IRemoteTest getTestShard(int shardCount, int shardIndex) {
        TestStubShardable test = new TestStubShardable();
        OptionCopier.copyOptionsNoThrow(this, test);
        test.mShardedTestToRun = new ArrayList<>();
        TestIdentifier tid = new TestIdentifier("TestStub", "test" + shardIndex);
        test.mShardedTestToRun.add(tid);
        if (mIsComplete == false) {
            TestIdentifier tid2 = new TestIdentifier("TestStub", "test" + shardIndex + 100);
            test.mShardedTestToRun.add(tid2);
            test.mIsComplete = false;
        }
        test.mShardIndex = shardIndex;
        return test;
    }
}
