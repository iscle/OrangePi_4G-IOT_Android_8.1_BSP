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
package com.android.compatibility.common.tradefed.presubmit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.targetprep.ApkInstaller;
import com.android.compatibility.common.tradefed.testtype.JarHostTest;
import com.android.tradefed.build.FolderBuildInfo;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.HostTest;
import com.android.tradefed.testtype.IRemoteTest;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test that configuration in CTS can load and have expected properties.
 */
public class CtsConfigLoadingTest {

    private static final String METADATA_COMPONENT = "component";
    private static final Set<String> KNOWN_COMPONENTS = new HashSet<>(Arrays.asList(
            // modifications to the list below must be reviewed
            "abuse",
            "art",
            "auth",
            "auto",
            "backup",
            "bionic",
            "bluetooth",
            "camera",
            "deqp",
            "devtools",
            "framework",
            "graphics",
            "libcore",
            "location",
            "media",
            "metrics",
            "misc",
            "networking",
            "neuralnetworks",
            "renderscript",
            "security",
            "systems",
            "sysui",
            "telecom",
            "tv",
            "uitoolkit",
            "vr",
            "webview"
    ));

    /**
     * Test that configuration shipped in Tradefed can be parsed.
     * -> Exclude deprecated ApkInstaller.
     * -> Check if host-side tests are non empty.
     */
    @Test
    public void testConfigurationLoad() throws Exception {
        String ctsRoot = System.getProperty("CTS_ROOT");
        File testcases = new File(ctsRoot, "/android-cts/testcases/");
        if (!testcases.exists()) {
            fail(String.format("%s does not exists", testcases));
            return;
        }
        File[] listConfig = testcases.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.endsWith(".config")) {
                    return true;
                }
                return false;
            }
        });
        assertTrue(listConfig.length > 0);
        // Create a FolderBuildInfo to similate the CompatibilityBuildProvider
        FolderBuildInfo stubFolder = new FolderBuildInfo("-1", "-1");
        stubFolder.setRootDir(new File(ctsRoot));
        stubFolder.addBuildAttribute(CompatibilityBuildHelper.SUITE_NAME, "CTS");
        // We expect to be able to load every single config in testcases/
        for (File config : listConfig) {
            IConfiguration c = ConfigurationFactory.getInstance()
                    .createConfigurationFromArgs(new String[] {config.getAbsolutePath()});
            // Ensure the deprecated ApkInstaller is not used anymore.
            for (ITargetPreparer prep : c.getTargetPreparers()) {
                if (prep.getClass().isAssignableFrom(ApkInstaller.class)) {
                    throw new ConfigurationException(
                            String.format("%s: Use com.android.tradefed.targetprep.suite."
                                    + "SuiteApkInstaller instead of com.android.compatibility."
                                    + "common.tradefed.targetprep.ApkInstaller, options will be "
                                    + "the same.", config));
                }
            }
            // We can ensure that Host side tests are not empty.
            for (IRemoteTest test : c.getTests()) {
                if (test instanceof HostTest) {
                    HostTest hostTest = (HostTest) test;
                    // We inject a made up folder so that it can find the tests.
                    hostTest.setBuild(stubFolder);
                    int testCount = hostTest.countTestCases();
                    if (testCount == 0) {
                        throw new ConfigurationException(
                                String.format("%s: %s reports 0 test cases.", config, test));
                    }
                }
            }
            ConfigurationDescriptor cd = c.getConfigurationDescription();
            Assert.assertNotNull(config + ": configuration descriptor is null", cd);
            List<String> component = cd.getMetaData(METADATA_COMPONENT);
            Assert.assertNotNull(String.format("Missing module metadata field \"component\", "
                    + "please add the following line to your AndroidTest.xml:\n"
                    + "<option name=\"config-descriptor:metadata\" key=\"component\" "
                    + "value=\"...\" />\nwhere \"value\" must be one of: %s\n"
                    + "config: %s", KNOWN_COMPONENTS, config),
                    component);
            Assert.assertEquals(String.format("Module config contains more than one \"component\" "
                    + "metadata field: %s\nconfig: %s", component, config),
                    1, component.size());
            String cmp = component.get(0);
            Assert.assertTrue(String.format("Module config contains unknown \"component\" metadata "
                    + "field \"%s\", supported ones are: %s\nconfig: %s",
                    cmp, KNOWN_COMPONENTS, config), KNOWN_COMPONENTS.contains(cmp));
            // Check not-shardable: JarHostTest cannot create empty shards so it should never need
            // to be not-shardable.
            if (cd.isNotShardable()) {
                for (IRemoteTest test : c.getTests()) {
                    if (test.getClass().isAssignableFrom(JarHostTest.class)) {
                        throw new ConfigurationException(
                                String.format("config: %s. JarHostTest does not need the "
                                    + "not-shardable option.", config.getName()));
                    }
                }
            }
        }
    }
}
