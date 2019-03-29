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
 * limitations under the License
 */

package android.app.cts;

import android.app.Instrumentation;
import android.app.stubs.PipNotSupportedActivity;
import android.test.ActivityInstrumentationTestCase2;

public class PipNotSupportedActivityTest
        extends ActivityInstrumentationTestCase2<PipNotSupportedActivity> {

    private Instrumentation mInstrumentation;
    private PipNotSupportedActivity mActivity;

    public PipNotSupportedActivityTest() {
        super("android.app.stubs", PipNotSupportedActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
    }

    public void testLaunchPipNotSupportedActivity() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                boolean pipSupportDisabled = false;
                try {
                    mActivity.enterPictureInPictureMode();
                } catch (IllegalStateException e) {
                    // Pip not supported
                    pipSupportDisabled = true;
                } catch (IllegalArgumentException e) {
                    // Pip not supported
                    pipSupportDisabled = true;
                }
                assertTrue(pipSupportDisabled);
                assertFalse(mActivity.isInPictureInPictureMode());
            }
        });
        mInstrumentation.waitForIdleSync();
    }
}
