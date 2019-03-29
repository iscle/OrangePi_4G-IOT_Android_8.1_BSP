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

package android.server.cts;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.AnimationBackgroundTests
 */
public class AnimationBackgroundTests extends ActivityManagerTestBase {

    public void testAnimationBackground_duringAnimation() throws Exception {
        launchActivity(LAUNCHING_ACTIVITY);
        getLaunchActivityBuilder()
                .setTargetActivityName("AnimationTestActivity")
                .setWaitForLaunched(false)
                .execute();

        // Make sure we are in the middle of the animation.
        mAmWmState.waitForWithWmState(mDevice, state -> state
                .getStack(FULLSCREEN_WORKSPACE_STACK_ID)
                        .isWindowAnimationBackgroundSurfaceShowing(),
                "***Waiting for animation background showing");
        assertTrue("window animation background needs to be showing", mAmWmState.getWmState()
                .getStack(FULLSCREEN_WORKSPACE_STACK_ID)
                .isWindowAnimationBackgroundSurfaceShowing());
    }

    public void testAnimationBackground_gone() throws Exception {
        launchActivity(LAUNCHING_ACTIVITY);
        getLaunchActivityBuilder().setTargetActivityName("AnimationTestActivity").execute();
        mAmWmState.computeState(mDevice, new String[] { "AnimationTestActivity "});
        assertFalse("window animation background needs to be gone", mAmWmState.getWmState()
                .getStack(FULLSCREEN_WORKSPACE_STACK_ID)
                .isWindowAnimationBackgroundSurfaceShowing());
    }
}
