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

package android.view.animation.cts;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LayoutAnimationController;

import com.android.compatibility.common.util.PollingCheck;

/**
 * The utility methods for animation test.
 */
public final class AnimationTestUtils {
    /** timeout delta when wait in case the system is sluggish */
    private static final long TIMEOUT_DELTA = 1000;

    /**
     * no public constructor since this is a utility class
     */
    private AnimationTestUtils() {

    }

    /**
     * Assert run an animation successfully. Timeout is duration of animation.
     *
     * @param instrumentation to run animation.
     * @param activityTestRule to run animation.
     * @param view view window to run animation.
     * @param animation will be run.
     * @throws Throwable
     */
    public static void assertRunAnimation(final Instrumentation instrumentation,
            final ActivityTestRule activityTestRule, final View view, final Animation animation)
            throws Throwable {
        assertRunAnimation(instrumentation, activityTestRule, view, animation,
                animation.getDuration());
    }

    /**
     * Assert run an animation successfully.
     *
     * @param instrumentation to run animation.
     * @param activityTestRule to run animation.
     * @param view window to run animation.
     * @param animation will be run.
     * @param duration in milliseconds.
     * @throws Throwable
     */
    public static void assertRunAnimation(final Instrumentation instrumentation,
            final ActivityTestRule activityTestRule, final View view, final Animation animation,
            final long duration) throws Throwable {

        activityTestRule.runOnUiThread(() -> view.startAnimation(animation));

        // check whether it has started
        PollingCheck.waitFor(animation::hasStarted);

        // check whether it has ended after duration
        PollingCheck.waitFor(duration + TIMEOUT_DELTA, animation::hasEnded);

        instrumentation.waitForIdleSync();
    }

    /**
     * Assert run an view with LayoutAnimationController successfully.
     * @throws Throwable
     */
    public static void assertRunController(final ActivityTestRule activityTestRule,
            final ViewGroup view, final LayoutAnimationController controller,
            final long duration) throws Throwable {

        activityTestRule.runOnUiThread(() -> {
            view.setLayoutAnimation(controller);
            view.requestLayout();
        });

        // LayoutAnimationController.isDone() always returns true, it's no use for stopping
        // the running, so just using sleeping fixed time instead. we reported issue 1799434 for it.
        SystemClock.sleep(duration + TIMEOUT_DELTA);
    }
}
