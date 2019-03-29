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

import com.android.compatibility.common.util.TestFilter;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests for {@link SubPlan}
 */
public class SubPlanTest extends TestCase {

    private static final String ABI = "armeabi-v7a";
    private static final String MODULE_A = "ModuleA";
    private static final String MODULE_B = "ModuleB";
    private static final String TEST_1 = "android.test.Foo#test1";
    private static final String TEST_2 = "android.test.Foo#test2";
    private static final String TEST_3 = "android.test.Foo#test3";

    private static final String XML_BASE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<SubPlan version=\"2.0\">\n" +
            "%s\n" +
            "</SubPlan>";
    private static final String XML_ENTRY = "  <Entry %s/>\n";
    private static final String XML_ATTR = "%s=\"%s\"";

    public void testSerialization() throws Exception {
        ISubPlan subPlan = new SubPlan();
        subPlan.addIncludeFilter(new TestFilter(ABI, MODULE_A, TEST_1).toString());
        Set<String> includeFilterSet = new HashSet<String>();
        includeFilterSet.add(new TestFilter(ABI, MODULE_A, TEST_2).toString());
        includeFilterSet.add(new TestFilter(ABI, MODULE_A, TEST_3).toString());
        subPlan.addAllIncludeFilters(includeFilterSet); // add multiple include filters simultaneously
        subPlan.addIncludeFilter(new TestFilter(null, MODULE_B, null).toString());
        subPlan.addExcludeFilter(new TestFilter(null, MODULE_B, TEST_1).toString());
        Set<String> excludeFilterSet = new HashSet<String>();
        excludeFilterSet.add(new TestFilter(null, MODULE_B, TEST_2).toString());
        excludeFilterSet.add(new TestFilter(null, MODULE_B, TEST_3).toString());
        subPlan.addAllExcludeFilters(excludeFilterSet);

        // Serialize to file
        File subPlanFile = FileUtil.createTempFile("test-subPlan-serialization", ".txt");
        try {
            OutputStream subPlanOutputStream = new FileOutputStream(subPlanFile);
            subPlan.serialize(subPlanOutputStream);
            subPlanOutputStream.close();
            // Parse subPlan and assert correctness
            checkSubPlan(subPlanFile);
        } finally {
            FileUtil.deleteFile(subPlanFile);
        }
    }

    public void testParsing() throws Exception {
        File planFile = FileUtil.createTempFile("test-plan-parsing", ".txt");
        FileWriter writer = new FileWriter(planFile);
        try {
            Set<String> entries = new HashSet<String>();
            entries.add(generateEntryXml(ABI, MODULE_A, TEST_1, true)); // include format 1
            entries.add(generateEntryXml(ABI, MODULE_A, TEST_2, true));
            entries.add(generateEntryXml(null, null,
                    new TestFilter(ABI, MODULE_A, TEST_3).toString(), true)); // include format 2
            entries.add(generateEntryXml(null, MODULE_B, null, true));
            entries.add(generateEntryXml(null, null,
                    new TestFilter(null, MODULE_B, TEST_1).toString(), false));
            entries.add(generateEntryXml(null, null,
                    new TestFilter(null, MODULE_B, TEST_2).toString(), false));
            entries.add(generateEntryXml(null, null,
                    new TestFilter(null, MODULE_B, TEST_3).toString(), false));
            String xml = String.format(XML_BASE, String.join("\n", entries));
            writer.write(xml);
            writer.flush();
            checkSubPlan(planFile);
        } finally {
            writer.close();
            FileUtil.deleteFile(planFile);
        }
    }

    private void checkSubPlan(File subPlanFile) throws Exception {
        InputStream subPlanInputStream = new FileInputStream(subPlanFile);
        ISubPlan subPlan = new SubPlan();
        subPlan.parse(subPlanInputStream);
        Set<String> subPlanIncludes = subPlan.getIncludeFilters();
        Set<String> subPlanExcludes = subPlan.getExcludeFilters();
        assertEquals("Expected 4 includes", 4, subPlanIncludes.size());
        assertTrue("Missing expected test include", subPlanIncludes.contains(
                new TestFilter(ABI, MODULE_A, TEST_1).toString()));
        assertTrue("Missing expected test include", subPlanIncludes.contains(
                new TestFilter(ABI, MODULE_A, TEST_2).toString()));
        assertTrue("Missing expected test include", subPlanIncludes.contains(
                new TestFilter(ABI, MODULE_A, TEST_3).toString()));
        assertTrue("Missing expected module include", subPlanIncludes.contains(
                new TestFilter(null, MODULE_B, null).toString()));

        assertEquals("Expected 3 excludes", 3, subPlanExcludes.size());
        assertTrue("Missing expected exclude", subPlanExcludes.contains(
                new TestFilter(null, MODULE_B, TEST_1).toString()));
        assertTrue("Missing expected exclude", subPlanExcludes.contains(
                new TestFilter(null, MODULE_B, TEST_2).toString()));
        assertTrue("Missing expected exclude", subPlanExcludes.contains(
                new TestFilter(null, MODULE_B, TEST_3).toString()));
    }

    // Helper for generating Entry XML tags
    private String generateEntryXml(String abi, String name, String filter, boolean include) {
        String filterType = (include) ? "include" : "exclude";
        Set<String> attributes = new HashSet<String>();
        if (filter != null) {
            attributes.add(String.format(XML_ATTR, filterType, filter));
        }
        if (name != null) {
            attributes.add(String.format(XML_ATTR, "name", name));
        }
        if (abi != null) {
            attributes.add(String.format(XML_ATTR, "abi", abi));
        }
        return String.format(XML_ENTRY, String.join(" ", attributes));
    }
}
