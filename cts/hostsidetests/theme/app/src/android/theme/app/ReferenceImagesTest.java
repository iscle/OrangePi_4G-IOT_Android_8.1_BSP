/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.theme.app;

import android.test.ActivityInstrumentationTestCase2;

import java.io.File;

/**
 * Activity test case used to instrument generation of reference images.
 */
public class ReferenceImagesTest extends ActivityInstrumentationTestCase2<GenerateImagesActivity> {

    /** Overall test timeout is 30 minutes. Should only take about 5. */
    private static final int TEST_RESULT_TIMEOUT = 30 * 60 * 1000;

    public ReferenceImagesTest() {
        super(GenerateImagesActivity.class);
    }

    public void testGenerateReferenceImages() throws Exception {
        setActivityInitialTouchMode(true);

        final GenerateImagesActivity activity = getActivity();
        assertTrue("Activity failed to complete within " + TEST_RESULT_TIMEOUT + " ms",
                activity.waitForCompletion(TEST_RESULT_TIMEOUT));
        assertTrue(activity.getFinishReason(), activity.isFinishSuccess());

        final File outputZip = activity.getOutputZip();
        assertTrue("Failed to generate reference image ZIP",
                outputZip != null && outputZip.exists());
    }
}
