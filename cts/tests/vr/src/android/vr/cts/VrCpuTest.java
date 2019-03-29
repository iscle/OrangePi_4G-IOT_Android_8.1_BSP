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
package android.vr.cts;

import android.content.pm.PackageManager;
import android.os.Process;
import android.test.ActivityInstrumentationTestCase2;

public class VrCpuTest extends ActivityInstrumentationTestCase2<CtsActivity> {
    private CtsActivity mActivity;

    public VrCpuTest() {
        super(CtsActivity.class);
    }

    public void testHasAtLeastTwoCores() {
        mActivity = getActivity();
        if (mActivity.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
            assertTrue(Runtime.getRuntime().availableProcessors() >= 2);
        }
    }
}
