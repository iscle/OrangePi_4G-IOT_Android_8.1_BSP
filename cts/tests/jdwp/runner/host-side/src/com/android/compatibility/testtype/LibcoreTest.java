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

package com.android.compatibility.testtype;

import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A specialized test type for Libcore tests that provides the ability to specify a set of
 * expectation files. Expectation files are used to indicate tests that are expected to fail,
 * often because they come from upstream projects, and should not be run under CTS.
 */
public class LibcoreTest extends AndroidJUnitTest {

    private static final String INSTRUMENTATION_ARG_NAME = "core-expectations";

    @Option(name = "core-expectation", description = "Provides failure expectations for libcore "
            + "tests via the specified file; the path must be absolute and will be resolved to "
            + "matching bundled resource files; this parameter should be repeated for each "
            + "expectation file")
    private List<String> mCoreExpectations = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        if (!mCoreExpectations.isEmpty()) {
            addInstrumentationArg(INSTRUMENTATION_ARG_NAME, ArrayUtil.join(",", mCoreExpectations));
        }
        super.run(listener);
    }
}
