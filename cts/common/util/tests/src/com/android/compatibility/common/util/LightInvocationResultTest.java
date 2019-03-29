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

package com.android.compatibility.common.util;

import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;

/**
 * Unit tests for {@link LightInvocationResult}
 */
public class LightInvocationResultTest extends TestCase {

    private File resultsDir;

    @Override
    public void setUp() throws Exception {
        resultsDir = FileUtil.createTempDir("results");
    }

    @Override
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(resultsDir);
    }

    public void testLightInvocationResultInstatiate() throws Exception {
        File resultDir = ResultHandlerTest.writeResultDir(resultsDir);
        IInvocationResult fullResult = ResultHandler.getResultFromDir(resultDir);
        LightInvocationResult lightResult = new LightInvocationResult(fullResult);
        // Ensure that light result implementation does not use a reference to the full result
        fullResult = null;
        ResultHandlerTest.checkLightResult(lightResult);
    }
}
