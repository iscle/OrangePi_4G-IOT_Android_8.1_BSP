/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.opengl.cts;

import android.opengl.GLSurfaceView;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the GLSurfaceView class.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class GLSurfaceViewTest {

    private static final int NUM_PAUSE_RESUME_ITERATIONS_WITHOUT_DELAY = 1000;

    private static final int NUM_PAUSE_RESUME_ITERATIONS_WITH_DELAY = 100;

    private static final int PAUSE_RESUME_DELAY = 10;

    private static final boolean LOG_PAUSE_RESUME = false;

    private static final String TAG = "GLSurfaceViewTest";

    private GLSurfaceViewCtsActivity mActivity;

    @Rule
    public ActivityTestRule<GLSurfaceViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(GLSurfaceViewCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    /**
     * Test repeated pausing and resuming of a GLSurfaceView with a delay
     * between iterations.
     * <p>
     * This test simply verifies that the system is able to perform multiple
     * pause/resume sequences without crashing. The delay is used to allow
     * asynchronous events to occur in between the pause and resume operations.
     * </p>
     */
    @UiThreadTest
    @Test
    public void testPauseResumeWithDelay() throws InterruptedException {
        GLSurfaceView view = mActivity.getView();
        for (int i = 0; i < NUM_PAUSE_RESUME_ITERATIONS_WITH_DELAY; i++) {
            Thread.sleep(PAUSE_RESUME_DELAY);
            if (LOG_PAUSE_RESUME) {
                Log.w(TAG, "Pause/Resume (w/ delay) step " + i + " - pause");
            }
            view.onPause();
            Thread.sleep(PAUSE_RESUME_DELAY);
            if (LOG_PAUSE_RESUME) {
                Log.w(TAG, "Pause/Resume (w/ delay) step " + i + " - resume");
            }
            view.onResume();
        }
    }

    /**
     * Test repeated pausing and resuming of a GLSurfaceView.
     * <p>
     * This test simply verifies that the system is able to perform multiple
     * pause/resume sequences without crashing. No delay is used so that a
     * larger number of iterations can be done in a short amount of time.
     * </p>
     */
    @UiThreadTest
    @Test
    public void testPauseResumeWithoutDelay() {
        GLSurfaceView view = mActivity.getView();
        for (int i = 0; i < NUM_PAUSE_RESUME_ITERATIONS_WITHOUT_DELAY; i++) {
            if (LOG_PAUSE_RESUME) {
                Log.w(TAG, "Pause/Resume (no delay) step " + i + " - pause");
            }
            view.onPause();
            if (LOG_PAUSE_RESUME) {
                Log.w(TAG, "Pause/Resume (no delay) step " + i + " - resume");
            }
            view.onResume();
        }
    }
}
