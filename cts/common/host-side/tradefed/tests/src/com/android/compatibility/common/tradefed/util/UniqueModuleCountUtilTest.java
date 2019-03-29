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
package com.android.compatibility.common.tradefed.util;

import static org.junit.Assert.assertEquals;

import com.android.compatibility.common.tradefed.testtype.IModuleDef;
import com.android.compatibility.common.tradefed.testtype.ModuleDef;
import com.android.compatibility.common.tradefed.testtype.TestStub;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.Abi;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link UniqueModuleCountUtil}.
 */
public class UniqueModuleCountUtilTest {

    @Test
    public void testCountEmptyList() {
        List<IModuleDef> emptyList = new ArrayList<>();
        assertEquals(0, UniqueModuleCountUtil.countUniqueModules(emptyList));
    }

    @Test
    public void testCount_2uniquesModules() {
        List<IModuleDef> list = new ArrayList<>();
        list.add(new ModuleDef("moduleA", new Abi("arm64", "64"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleA", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        assertEquals(2, UniqueModuleCountUtil.countUniqueModules(list));
    }

    @Test
    public void testCount_2subModules() {
        List<IModuleDef> list = new ArrayList<>();
        list.add(new ModuleDef("moduleA", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleA", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        assertEquals(1, UniqueModuleCountUtil.countUniqueModules(list));
    }

    @Test
    public void testCount_mix() {
        List<IModuleDef> list = new ArrayList<>();
        list.add(new ModuleDef("moduleA", new Abi("arm64", "64"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleA", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleC", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleB", new Abi("arm64", "64"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleB", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleC", new Abi("arm64", "64"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleA", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        list.add(new ModuleDef("moduleC", new Abi("arm32", "32"), new TestStub(),
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor()));
        assertEquals(6, UniqueModuleCountUtil.countUniqueModules(list));
    }
}
