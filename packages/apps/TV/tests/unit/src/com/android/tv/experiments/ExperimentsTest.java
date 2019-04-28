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
 * limitations under the License
 */

package com.android.tv.experiments;

import static org.junit.Assert.assertEquals;

import android.support.test.filters.SmallTest;

import com.android.tv.common.BuildConfig;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link Experiments}.
 */
@SmallTest
public class ExperimentsTest {
    @Before
    public void setUp() {
        ExperimentFlag.initForTest();
    }


    @Test
    public void testEngOnlyDefault() {
        assertEquals("ENABLE_DEVELOPER_FEATURES", Boolean.valueOf(BuildConfig.ENG),
                Experiments.ENABLE_DEVELOPER_FEATURES.get());
    }


}
