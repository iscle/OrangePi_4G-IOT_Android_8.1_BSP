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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class ShardableTestStub implements IRemoteTest, IShardableTest, IBuildReceiver,
        IAbiReceiver, IRuntimeHintProvider, ITestCollector, ITestFilterReceiver,
        IStrictShardableTest {

    @Option(name = "module")
    String mModule;
    @Option(name = "foo")
    String mFoo;
    @Option(name = "blah")
    String mBlah;

    public IBuildInfo mBuildInfo = null;

    Collection<IRemoteTest> mShards;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        mShards = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            mShards.add(new ShardableTestStub());
        }
        return mShards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IRemoteTest getTestShard(int shardCount, int shardIndex) {
        return new ShardableTestStub();
    }

    @Override
    public void setAbi(IAbi abi) {
        // Do nothing
    }

    @Override
    public IAbi getAbi() {
        return null;
    }

    @Override
    public long getRuntimeHint() {
        return 1L;
    }

    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        // Do nothing
    }

    @Override
    public void addIncludeFilter(String filter) {

    }

    @Override
    public void addAllIncludeFilters(Set<String> filters) {

    }

    @Override
    public void addExcludeFilter(String filter) {

    }

    @Override
    public void addAllExcludeFilters(Set<String> filters) {

    }
}
