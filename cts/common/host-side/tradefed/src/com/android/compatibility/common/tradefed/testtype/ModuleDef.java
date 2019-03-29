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

import com.android.compatibility.common.tradefed.result.IModuleListener;
import com.android.compatibility.common.tradefed.result.ModuleListener;
import com.android.compatibility.common.tradefed.targetprep.DynamicConfigPusher;
import com.android.compatibility.common.tradefed.targetprep.PreconditionPreparer;
import com.android.compatibility.common.tradefed.targetprep.TokenRequirement;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetCleaner;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.AbiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Container for Compatibility test module info.
 */
public class ModuleDef implements IModuleDef {

    private final String mId;
    private final String mName;
    private final IAbi mAbi;
    private final Set<String> mTokens = new HashSet<>();
    private IRemoteTest mTest = null;
    private List<ITargetPreparer> mDynamicConfigPreparers = new ArrayList<>();
    private List<ITargetPreparer> mPreconditions = new ArrayList<>();
    private List<ITargetPreparer> mPreparers = new ArrayList<>();
    private List<ITargetCleaner> mCleaners = new ArrayList<>();
    private IBuildInfo mBuild;
    private ITestDevice mDevice;
    private Set<String> mPreparerWhitelist = new HashSet<>();
    private ConfigurationDescriptor mConfigurationDescriptor;

    public ModuleDef(String name, IAbi abi, IRemoteTest test,
            List<ITargetPreparer> preparers, ConfigurationDescriptor configurationDescriptor) {
        mId = AbiUtils.createId(abi.getName(), name);
        mName = name;
        mAbi = abi;
        mTest = test;
        mConfigurationDescriptor = configurationDescriptor;
        initializePrepareLists(preparers);
    }

    /**
     * Sort preparers into different lists according to their types
     *
     * @param preparers target preparers
     * @throws IllegalArgumentException
     */
    protected void initializePrepareLists(List<ITargetPreparer> preparers)
            throws IllegalArgumentException {
        boolean hasAbiReceiver = false;
        for (ITargetPreparer preparer : preparers) {
            if (preparer instanceof IAbiReceiver) {
                hasAbiReceiver = true;
            }
            // Separate preconditions and dynamicconfigpushers from other target preparers.
            if (preparer instanceof PreconditionPreparer) {
                mPreconditions.add(preparer);
            } else if (preparer instanceof DynamicConfigPusher) {
                mDynamicConfigPreparers.add(preparer);
            } else if (preparer instanceof TokenRequirement) {
                mTokens.addAll(((TokenRequirement) preparer).getTokens());
            } else {
                mPreparers.add(preparer);
            }
            if (preparer instanceof ITargetCleaner) {
                mCleaners.add((ITargetCleaner) preparer);
            }
        }
        // Reverse cleaner order
        Collections.reverse(mCleaners);

        checkRequiredInterfaces(hasAbiReceiver);
    }

    /**
     * Check whether required interfaces are implemented.
     *
     * @param hasAbiReceiver whether at lease one of the preparers is AbiReceiver
     * @throws IllegalArgumentException
     */
    protected void checkRequiredInterfaces(boolean hasAbiReceiver) throws IllegalArgumentException {
        // Required interfaces:
        if (!hasAbiReceiver && !(mTest instanceof IAbiReceiver)) {
            throw new IllegalArgumentException(mTest + "does not implement IAbiReceiver"
                    + " - for multi-abi testing (64bit)");
        } else if (!(mTest instanceof IRuntimeHintProvider)) {
            throw new IllegalArgumentException(mTest + " does not implement IRuntimeHintProvider"
                    + " - to provide estimates of test invocation time");
        } else if (!(mTest instanceof ITestCollector)) {
            throw new IllegalArgumentException(mTest + " does not implement ITestCollector"
                    + " - for test list collection");
        } else if (!(mTest instanceof ITestFilterReceiver)) {
            throw new IllegalArgumentException(mTest + " does not implement ITestFilterReceiver"
                    + " - to allow tests to be filtered");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mName;
    }

    /**
     * @return the mPreparerWhitelist
     */
    protected Set<String> getPreparerWhitelist() {
        return mPreparerWhitelist;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getTokens() {
        return mTokens;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRuntimeHint() {
        if (mTest instanceof IRuntimeHintProvider) {
            return ((IRuntimeHintProvider) mTest).getRuntimeHint();
        }
        return TimeUnit.MINUTES.toMillis(1); // Default 1 minute.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IRemoteTest getTest() {
        return mTest;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPreparerWhitelist(Set<String> preparerWhitelist) {
        mPreparerWhitelist.addAll(preparerWhitelist);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IModuleDef moduleDef) {
        return getName().compareTo(moduleDef.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo build) {
        mBuild = build;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        CLog.d("Running module %s", toString());
        runPreparerSetups();

        CLog.d("Test: %s", mTest.getClass().getSimpleName());
        prepareTestClass();

        IModuleListener moduleListener = new ModuleListener(this, listener);
        // Guarantee events testRunStarted and testRunEnded in case underlying test runner does not
        ModuleFinisher moduleFinisher = new ModuleFinisher(moduleListener);
        mTest.run(moduleFinisher);
        moduleFinisher.finish();

        // Tear down
        runPreparerTeardowns();
    }

    /**
     * Run preparers' teardown functions.
     */
    protected void runPreparerTeardowns() throws DeviceNotAvailableException {
        for (ITargetCleaner cleaner : mCleaners) {
            CLog.d("Cleaner: %s", cleaner.getClass().getSimpleName());
            cleaner.tearDown(mDevice, mBuild, null);
        }
    }

    /**
     * Run preparers' setup functions.
     *
     * @throws DeviceNotAvailableException
     */
    protected void runPreparerSetups() throws DeviceNotAvailableException {
        // Run DynamicConfigPusher setup once more, in case cleaner has previously
        // removed dynamic config file from the target (see b/32877809)
        for (ITargetPreparer preparer : mDynamicConfigPreparers) {
            runPreparerSetup(preparer);
        }
        // Setup
        for (ITargetPreparer preparer : mPreparers) {
            runPreparerSetup(preparer);
        }
    }

    /**
     * Set test classes attributes according to their interfaces.
     */
    protected void prepareTestClass() {
        if (mTest instanceof IAbiReceiver) {
            ((IAbiReceiver) mTest).setAbi(mAbi);
        }
        if (mTest instanceof IBuildReceiver) {
            ((IBuildReceiver) mTest).setBuild(mBuild);
        }
        if (mTest instanceof IDeviceTest) {
            ((IDeviceTest) mTest).setDevice(mDevice);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean prepare(boolean skipPrep, List<String> preconditionArgs)
            throws DeviceNotAvailableException {
        for (ITargetPreparer preparer : mDynamicConfigPreparers) {
            runPreparerSetup(preparer);
        }
        for (ITargetPreparer preparer : mPreconditions) {
            setOption(preparer, CompatibilityTest.SKIP_PRECONDITIONS_OPTION,
                    Boolean.toString(skipPrep));
            for (String preconditionArg : preconditionArgs) {
                setOption(preparer, CompatibilityTest.PRECONDITION_ARG_OPTION, preconditionArg);
            }
            try {
                runPreparerSetup(preparer);
            } catch (RuntimeException e) {
                CLog.e("Precondition class %s failed", preparer.getClass().getCanonicalName());
                return false;
            }
        }
        return true;
    }

    private void runPreparerSetup(ITargetPreparer preparer) throws DeviceNotAvailableException {
        String preparerName = preparer.getClass().getCanonicalName();
        if (!mPreparerWhitelist.isEmpty() && !mPreparerWhitelist.contains(preparerName)) {
            CLog.w("Skipping Preparer: %s since it is not in the whitelist %s",
                    preparerName, mPreparerWhitelist);
            return;
        }
        CLog.d("Preparer: %s", preparer.getClass().getSimpleName());
        if (preparer instanceof IAbiReceiver) {
            ((IAbiReceiver) preparer).setAbi(mAbi);
        }
        try {
            preparer.setUp(mDevice, mBuild);
        } catch (BuildError e) {
            // This should only happen for flashing new build
            CLog.e("Unexpected BuildError from preparer: %s",
                    preparer.getClass().getCanonicalName());
            throw new RuntimeException(e);
        } catch (TargetSetupError e) {
            // log preparer class then rethrow & let caller handle
            CLog.e("TargetSetupError in preparer: %s",
                    preparer.getClass().getCanonicalName());
            throw new RuntimeException(e);
        }
    }

    private void setOption(Object target, String option, String value) {
        try {
            OptionSetter setter = new OptionSetter(target);
            setter.setOptionValue(option, value);
        } catch (ConfigurationException e) {
            CLog.e(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCollectTestsOnly(boolean collectTestsOnly) {
        ((ITestCollector) mTest).setCollectTestsOnly(collectTestsOnly);
    }

    /*
     * ResultForwarder that tracks whether method testRunStarted() has been called for its
     * listener. If not, invoking finish() will call testRunStarted with 0 tests for this module,
     * as well as testRunEnded with 0 ms elapsed.
     */
    private class ModuleFinisher extends ResultForwarder {

        private boolean mFinished;
        private ITestInvocationListener mListener;

        public ModuleFinisher(ITestInvocationListener listener) {
            super(listener);
            mListener = listener;
            mFinished = false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void testRunStarted(String name, int numTests) {
            mListener.testRunStarted(name, numTests);
            mFinished = true;
        }

        public void finish() {
            if (!mFinished) {
                mListener.testRunStarted(mId, 0);
                mListener.testRunEnded(0, Collections.emptyMap());
            }
        }
    }

    @Override
    public ConfigurationDescriptor getConfigurationDescriptor() {
        return mConfigurationDescriptor;
    }
}
