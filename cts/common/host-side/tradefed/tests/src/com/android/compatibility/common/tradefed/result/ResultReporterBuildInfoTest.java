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

package com.android.compatibility.common.tradefed.result;


import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.InvocationResult;
import junit.framework.TestCase;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link ResultReporter}, focused on ability to override build info.
 */
public class ResultReporterBuildInfoTest extends TestCase {

    public void testOverrideBuildProperties() {
        ResultReporterBuildInfoTester tester = new ResultReporterBuildInfoTester();
        String manufacture = "custom_manufacture";
        String brand = "google";
        String product = "gProduct";
        String device = "gDevice";
        String version = "gVersion";
        String buildId = "123";
        String model = "gModel";
        String fingerprint = brand + "/" + product + "/" + device + ":" +
                version + "/" + buildId + "/userdebug-keys";

        IInvocationResult result = tester.testBuildInfoOverride(fingerprint, manufacture, model);
        Map<String, String> invocationInfo = result.getInvocationInfo();
        assertEquals(invocationInfo.get(ResultReporter.BUILD_ID), buildId);
        assertEquals(invocationInfo.get(ResultReporter.BUILD_BRAND), brand);
        assertEquals(invocationInfo.get(ResultReporter.BUILD_DEVICE), device);
        assertEquals(invocationInfo.get(ResultReporter.BUILD_PRODUCT), product);
        assertEquals(invocationInfo.get(ResultReporter.BUILD_VERSION_RELEASE), version);
        assertEquals(invocationInfo.get(ResultReporter.BUILD_FINGERPRINT), fingerprint);
        assertEquals(invocationInfo.get(ResultReporter.BUILD_MANUFACTURER), manufacture);
        assertEquals(invocationInfo.get(ResultReporter.BUILD_MODEL), model);
    }

    public static class ResultReporterBuildInfoTester extends ResultReporter {

        public ResultReporterBuildInfoTester() {
            mResult = new InvocationResult();
        }

        public IInvocationResult testBuildInfoOverride(String buildFingerprintOverride,
                String manufactureOverride, String modelOverride) {
            addDeviceBuildInfoToResult(
                    buildFingerprintOverride, manufactureOverride, modelOverride);
            return mResult;
        }

        @Override
        protected Map<String, String> mapBuildInfo() {
            Map<String, String> buildProperties = new HashMap<>();
            buildProperties.put(BUILD_ID, BUILD_ID);
            buildProperties.put(BUILD_BRAND, BUILD_BRAND);
            buildProperties.put(BUILD_DEVICE, BUILD_DEVICE);
            buildProperties.put(BUILD_PRODUCT, BUILD_PRODUCT);
            buildProperties.put(BUILD_VERSION_RELEASE, BUILD_VERSION_RELEASE);
            buildProperties.put(BUILD_FINGERPRINT, BUILD_FINGERPRINT);
            buildProperties.put(BUILD_MANUFACTURER, BUILD_MANUFACTURER);
            buildProperties.put(BUILD_MODEL, BUILD_MODEL);
            return buildProperties;
        }
    }
}
