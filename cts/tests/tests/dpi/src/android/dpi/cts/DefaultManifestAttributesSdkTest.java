/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.dpi.cts;

import android.os.Build;
import android.platform.test.annotations.RestrictedBuildTest;

/**
 * This class actually tests the manifest attributes from
 * DefaultManifestAttributesTest for the current sdk
 */
public class DefaultManifestAttributesSdkTest extends DefaultManifestAttributesTest {
    protected String getPackageName() {
        return "android.dpi.cts";
    }

    /**
     * Sanity test to make sure that we're instrumenting the proper package
     */
    @RestrictedBuildTest
    public void testPackageHasExpectedSdkVersion() {
        assertEquals(Build.VERSION.SDK_INT, getAppInfo().targetSdkVersion);
    }
}
