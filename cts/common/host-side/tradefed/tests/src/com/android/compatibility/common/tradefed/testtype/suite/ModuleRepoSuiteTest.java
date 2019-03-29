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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.util.MultiMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link ModuleRepoSuite}.
 */
@RunWith(JUnit4.class)
public class ModuleRepoSuiteTest {

    private static final MultiMap<String, String> METADATA_INCLUDES = new MultiMap<>();
    private static final MultiMap<String, String> METADATA_EXCLUDES = new MultiMap<>();
    private ModuleRepoSuite mRepo;

    @Before
    public void setUp() {
        mRepo = new ModuleRepoSuite();
    }

    /**
     * When there are no metadata based filters specified, config should be included.
     */
    @Test
    public void testMetadataFilter_emptyFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        assertTrue("config not included when metadata filters are empty",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, METADATA_EXCLUDES));
    }

    /**
     * When inclusion filter is specified, config matching the filter is included.
     */
    @Test
    public void testMetadataFilter_matchInclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When inclusion filter is specified, config not matching the filter is excluded
     */
    @Test
    public void testMetadataFilter_noMatchInclude_mismatchValue() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "bar");
        assertFalse("config not excluded with mismatching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When inclusion filter is specified, config not matching the filter is excluded.
     */
    @Test
    public void testMetadataFilter_noMatchInclude_mismatchKey() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("group", "bar");
        assertFalse("config not excluded with mismatching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filter is specified, config matching the filter is excluded.
     */
    @Test
    public void testMetadataFilter_matchExclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When exclusion filter is specified, config not matching the filter is included.
     */
    @Test
    public void testMetadataFilter_noMatchExclude_mismatchKey() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "bar");
        assertTrue("config not included with mismatching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When exclusion filter is specified, config not matching the filter is included.
     */
    @Test
    public void testMetadataFilter_noMatchExclude_mismatchValue() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("group", "bar");
        assertTrue("config not included with mismatching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion filter is specified, config with one of the metadata field matching the filter
     * is included.
     */
    @Test
    public void testMetadataFilter_matchInclude_multipleMetadataField() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("component", "bar");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filter is specified, config with one of the metadata field matching the filter
     * is excluded.
     */
    @Test
    public void testMetadataFilter_matchExclude_multipleMetadataField() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("component", "bar");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion filters are specified, config with metadata field matching one of the filter
     * is included.
     */
    @Test
    public void testMetadataFilter_matchInclude_multipleFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        includeFilter.put("component", "bar");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filters are specified, config with metadata field matching one of the filter
     * is excluded.
     */
    @Test
    public void testMetadataFilter_matchExclude_multipleFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo");
        excludeFilter.put("component", "bar");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion filters are specified, config with metadata field matching one of the filter
     * is included.
     */
    @Test
    public void testMetadataFilter_matchInclude_multipleMetadataAndFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo1");
        metadata.put("group", "bar1");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo1");
        includeFilter.put("group", "bar2");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filters are specified, config with metadata field matching one of the filter
     * is excluded.
     */
    @Test
    public void testMetadataFilter_matchExclude_multipleMetadataAndFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo1");
        metadata.put("group", "bar1");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo1");
        excludeFilter.put("group", "bar2");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion and exclusion filters are both specified, config can pass through the filters
     * as expected.
     */
    @Test
    public void testMetadataFilter_includeAndExclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("group", "bar1");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("group", "bar2");
        assertTrue("config not included with matching inclusion and mismatching exclusion filters",
                mRepo.filterByConfigMetadata(config, includeFilter, excludeFilter));
    }

    /**
     * When inclusion and exclusion filters are both specified, config be excluded as specified
     */
    @Test
    public void testMetadataFilter_includeThenExclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("group", "bar");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("group", "bar");
        assertFalse("config not excluded with matching inclusion and exclusion filters",
                mRepo.filterByConfigMetadata(config, includeFilter, excludeFilter));
    }

    public static class TestInject implements IRemoteTest {
        @Option(name = "simple-string")
        public String test = null;
        @Option(name = "list-string")
        public List<String> testList = new ArrayList<>();
        @Option(name = "map-string")
        public Map<String, String> testMap = new HashMap<>();

        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        }
    }

    /**
     * Test that the different format for module-arg and test-arg can properly be passed to the
     * configuration.
     */
    @Test
    public void testInjectConfig() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        TestInject checker = new TestInject();
        config.setTest(checker);
        Map<String, List<String>> optionMap = new HashMap<String, List<String>>();
        List<String> option1 = new ArrayList<>();
        option1.add("value1");
        optionMap.put("simple-string", option1);

        List<String> option2 = new ArrayList<>();
        option2.add("value2");
        option2.add("value3");
        option2.add("set-option:moreoption");
        optionMap.put("list-string", option2);

        List<String> option3 = new ArrayList<>();
        option3.add("set-option:=moreoption");
        optionMap.put("map-string", option3);

        mRepo.injectOptionsToConfig(optionMap, config);

        assertEquals("value1", checker.test);
        assertEquals(option2, checker.testList);
        Map<String, String> resMap = new HashMap<>();
        resMap.put("set-option", "moreoption");
        assertEquals(resMap, checker.testMap);
    }
}
