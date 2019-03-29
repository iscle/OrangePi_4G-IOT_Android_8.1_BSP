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
package com.android.compatibility.common.tradefed.testtype.suite;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.tradefed.testtype.ISubPlan;
import com.android.compatibility.common.tradefed.testtype.SubPlan;
import com.android.compatibility.common.util.TestFilter;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.suite.ITestSuite;
import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Test for running Compatibility Test Suite with new suite system.
 */
@OptionClass(alias = "compatibility")
public class CompatibilityTestSuite extends ITestSuite {

    private static final String INCLUDE_FILTER_OPTION = "include-filter";
    private static final String EXCLUDE_FILTER_OPTION = "exclude-filter";
    private static final String SUBPLAN_OPTION = "subplan";
    private static final String MODULE_OPTION = "module";
    private static final String TEST_OPTION = "test";
    private static final String MODULE_ARG_OPTION = "module-arg";
    private static final String TEST_ARG_OPTION = "test-arg";
    private static final String ABI_OPTION = "abi";
    private static final String SKIP_HOST_ARCH_CHECK = "skip-host-arch-check";
    private static final String PRIMARY_ABI_RUN = "primary-abi-only";
    private static final String PRODUCT_CPU_ABI_KEY = "ro.product.cpu.abi";

    // TODO: remove this option when CompatibilityTest goes away
    @Option(name = CompatibilityTest.RETRY_OPTION,
            shortName = 'r',
            description = "Copy of --retry from CompatibilityTest to prevent using it.")
    private Integer mRetrySessionId = null;

    @Option(name = SUBPLAN_OPTION,
            description = "the subplan to run",
            importance = Importance.IF_UNSET)
    private String mSubPlan;

    @Option(name = INCLUDE_FILTER_OPTION,
            description = "the include module filters to apply.",
            importance = Importance.ALWAYS)
    private Set<String> mIncludeFilters = new HashSet<>();

    @Option(name = EXCLUDE_FILTER_OPTION,
            description = "the exclude module filters to apply.",
            importance = Importance.ALWAYS)
    private Set<String> mExcludeFilters = new HashSet<>();

    @Option(name = MODULE_OPTION,
            shortName = 'm',
            description = "the test module to run.",
            importance = Importance.IF_UNSET)
    private String mModuleName = null;

    @Option(name = TEST_OPTION,
            shortName = 't',
            description = "the test to run.",
            importance = Importance.IF_UNSET)
    private String mTestName = null;

    @Option(name = MODULE_ARG_OPTION,
            description = "the arguments to pass to a module. The expected format is"
                    + "\"<module-name>:<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mModuleArgs = new ArrayList<>();

    @Option(name = TEST_ARG_OPTION,
            description = "the arguments to pass to a test. The expected format is"
                    + "\"<test-class>:<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mTestArgs = new ArrayList<>();

    @Option(name = ABI_OPTION,
            shortName = 'a',
            description = "the abi to test.",
            importance = Importance.IF_UNSET)
    private String mAbiName = null;

    @Option(name = SKIP_HOST_ARCH_CHECK,
            description = "Whether host architecture check should be skipped")
    private boolean mSkipHostArchCheck = false;

    @Option(name = PRIMARY_ABI_RUN,
            description = "Whether to run tests with only the device primary abi. "
                    + "This override the --abi option.")
    private boolean mPrimaryAbiRun = false;

    @Option(name = "module-metadata-include-filter",
            description = "Include modules for execution based on matching of metadata fields: "
                    + "for any of the specified filter name and value, if a module has a metadata "
                    + "field with the same name and value, it will be included. When both module "
                    + "inclusion and exclusion rules are applied, inclusion rules will be "
                    + "evaluated first. Using this together with test filter inclusion rules may "
                    + "result in no tests to execute if the rules don't overlap.")
    private MultiMap<String, String> mModuleMetadataIncludeFilter = new MultiMap<>();

    @Option(name = "module-metadata-exclude-filter",
            description = "Exclude modules for execution based on matching of metadata fields: "
                    + "for any of the specified filter name and value, if a module has a metadata "
                    + "field with the same name and value, it will be excluded. When both module "
                    + "inclusion and exclusion rules are applied, inclusion rules will be "
                    + "evaluated first.")
    private MultiMap<String, String> mModuleMetadataExcludeFilter = new MultiMap<>();

    private ModuleRepoSuite mModuleRepo = new ModuleRepoSuite();
    private CompatibilityBuildHelper mBuildHelper;

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkedHashMap<String, IConfiguration> loadTests() {
        if (mRetrySessionId != null) {
            throw new IllegalArgumentException("--retry cannot be specified with cts-suite.xml. "
                    + "Use 'run cts --retry <session id>' instead.");
        }
        try {
            setupFilters();
            Set<IAbi> abis = getAbis(getDevice());
            // Initialize the repository, {@link CompatibilityBuildHelper#getTestsDir} can
            // throw a {@link FileNotFoundException}
            return mModuleRepo.loadConfigs(mBuildHelper.getTestsDir(),
                    abis, mTestArgs, mModuleArgs, mIncludeFilters,
                    mExcludeFilters, mModuleMetadataIncludeFilter, mModuleMetadataExcludeFilter);
        } catch (DeviceNotAvailableException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        super.setBuild(buildInfo);
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    /**
     * Gets the set of ABIs supported by both Compatibility and the device under test
     *
     * @return The set of ABIs to run the tests on
     * @throws DeviceNotAvailableException
     */
    Set<IAbi> getAbis(ITestDevice device) throws DeviceNotAvailableException {
        Set<IAbi> abis = new LinkedHashSet<>();
        Set<String> archAbis = getAbisForBuildTargetArch();
        if (mPrimaryAbiRun) {
            if (mAbiName == null) {
                // Get the primary from the device and make it the --abi to run.
                mAbiName = device.getProperty(PRODUCT_CPU_ABI_KEY).trim();
            } else {
                CLog.d("Option --%s supersedes the option --%s, using abi: %s", ABI_OPTION,
                        PRIMARY_ABI_RUN, mAbiName);
            }
        }
        if (mAbiName != null) {
            // A particular abi was requested, it still needs to be supported by the build.
            if ((!mSkipHostArchCheck && !archAbis.contains(mAbiName)) ||
                    !AbiUtils.isAbiSupportedByCompatibility(mAbiName)) {
                throw new IllegalArgumentException(String.format("Your CTS hasn't been built with "
                        + "abi '%s' support, this CTS currently supports '%s'.",
                        mAbiName, archAbis));
            } else {
                abis.add(new Abi(mAbiName, AbiUtils.getBitness(mAbiName)));
                return abis;
            }
        } else {
            // Run on all abi in common between the device and CTS.
            List<String> deviceAbis = Arrays.asList(AbiFormatter.getSupportedAbis(device, ""));
            for (String abi : deviceAbis) {
                if ((mSkipHostArchCheck || archAbis.contains(abi)) &&
                        AbiUtils.isAbiSupportedByCompatibility(abi)) {
                    abis.add(new Abi(abi, AbiUtils.getBitness(abi)));
                } else {
                    CLog.d("abi '%s' is supported by device but not by this CTS build (%s), tests "
                            + "will not run against it.", abi, archAbis);
                }
            }
            if (abis.isEmpty()) {
                throw new IllegalArgumentException(String.format("None of the abi supported by this"
                       + " CTS build ('%s') are supported by the device ('%s').",
                       archAbis, deviceAbis));
            }
            return abis;
        }
    }

    /**
     * Return the abis supported by the Host build target architecture.
     * Exposed for testing.
     */
    protected Set<String> getAbisForBuildTargetArch() {
        return AbiUtils.getAbisForArch(TestSuiteInfo.getInstance().getTargetArch());
    }

    /**
     * Sets the include/exclude filters up based on if a module name was given or whether this is a
     * retry run.
     */
    void setupFilters() throws FileNotFoundException {
        if (mSubPlan != null) {
            try {
                File subPlanFile = new File(mBuildHelper.getSubPlansDir(), mSubPlan + ".xml");
                if (!subPlanFile.exists()) {
                    throw new IllegalArgumentException(
                            String.format("Could not retrieve subplan \"%s\"", mSubPlan));
                }
                InputStream subPlanInputStream = new FileInputStream(subPlanFile);
                ISubPlan subPlan = new SubPlan();
                subPlan.parse(subPlanInputStream);
                mIncludeFilters.addAll(subPlan.getIncludeFilters());
                mExcludeFilters.addAll(subPlan.getExcludeFilters());
            } catch (ParseException e) {
                throw new RuntimeException(
                        String.format("Unable to find or parse subplan %s", mSubPlan), e);
            }
        }
        if (mModuleName != null) {
            List<String> modules = ModuleRepoSuite.getModuleNamesMatching(
                    mBuildHelper.getTestsDir(), mModuleName);
            if (modules.size() == 0) {
                throw new IllegalArgumentException(
                        String.format("No modules found matching %s", mModuleName));
            } else if (modules.size() > 1) {
                throw new IllegalArgumentException(String.format(
                        "Multiple modules found matching %s:\n%s\nWhich one did you mean?\n",
                        mModuleName, ArrayUtil.join("\n", modules)));
            } else {
                String moduleName = modules.get(0);
                checkFilters(mIncludeFilters, moduleName);
                checkFilters(mExcludeFilters, moduleName);
                mIncludeFilters.add(new TestFilter(mAbiName, moduleName, mTestName).toString());
            }
        } else if (mTestName != null) {
            throw new IllegalArgumentException(
                    "Test name given without module name. Add --module <module-name>");
        }
    }

    /* Helper method designed to remove filters in a list not applicable to the given module */
    private static void checkFilters(Set<String> filters, String moduleName) {
        Set<String> cleanedFilters = new HashSet<String>();
        for (String filter : filters) {
            if (moduleName.equals(TestFilter.createFrom(filter).getName())) {
                cleanedFilters.add(filter); // Module name matches, filter passes
            }
        }
        filters.clear();
        filters.addAll(cleanedFilters);
    }

    /**
     * Sets include-filters for the compatibility test
     */
    public void setIncludeFilter(Set<String> includeFilters) {
        mIncludeFilters.addAll(includeFilters);
    }

    /**
     * Sets exclude-filters for the compatibility test
     */
    public void setExcludeFilter(Set<String> excludeFilters) {
        mExcludeFilters.addAll(excludeFilters);
    }
}
