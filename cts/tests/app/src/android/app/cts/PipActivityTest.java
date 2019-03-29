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
import android.app.stubs.PipActivity;
import android.test.ActivityInstrumentationTestCase2;

import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;

public class PipActivityTest extends ActivityInstrumentationTestCase2<PipActivity> {

    private Instrumentation mInstrumentation;
    private PipActivity mActivity;

    public PipActivityTest() {
        super("android.app.stubs", PipActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
    }

    public void testLaunchPipActivity() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                final boolean supportsPip =
                        mActivity.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE);
                if (supportsPip) {
                    mActivity.enterPictureInPictureMode();

                    // Entering PIP mode is not synchronous, so waiting for completion of all work
                    // on UI thread.
                    mInstrumentation.waitForIdle(new Runnable() {
                        @Override
                        public void run() {
                            assertTrue(mActivity.isInMultiWindowMode());
                            assertTrue(mActivity.isInPictureInPictureMode());
                        }
                    });
                } else {
                    boolean pipSupportDisabled = false;
                    try {
                        mActivity.enterPictureInPictureMode();
                    } catch (IllegalStateException e) {
                        // Pip not supported
                        pipSupportDisabled = true;
                    }
                    assertTrue(pipSupportDisabled);

                    // Entering PIP mode is not synchronous, so waiting for completion of all work
                    // on UI thread.
                    mInstrumentation.waitForIdle(new Runnable() {
                        @Override
                        public void run() {
                            assertFalse(mActivity.isInMultiWindowMode());
                            assertFalse(mActivity.isInPictureInPictureMode());
                        }
                    });
                }
            }
        });
        mInstrumentation.waitForIdleSync();
    }
}
