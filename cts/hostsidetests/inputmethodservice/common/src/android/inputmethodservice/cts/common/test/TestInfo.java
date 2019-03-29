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

package android.inputmethodservice.cts.common.test;

import android.inputmethodservice.cts.common.DeviceEventConstants;

/**
 * Abstraction of test information on device.
 */
public final class TestInfo {

    public final String testPackage;
    public final String testClass;
    public final String testMethod;

    public TestInfo(final String testPackage, final String testClass, final String testMethod) {
        this.testPackage = testPackage;
        this.testClass = testClass;
        this.testMethod = testMethod;
    }

    /**
     * Get fully qualified test method name that can be used as
     * {@link DeviceEventConstants#EXTRA_EVENT_SENDER}.
     * @return string representation of fully qualified test method name.
     */
    public String getTestName() {
        return testClass + "#" + testMethod;
    }
}
