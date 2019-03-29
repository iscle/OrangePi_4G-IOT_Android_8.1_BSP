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
 * limitations under the License
 */

package android.server.cts;

import static android.server.cts.ActivityManagerState.STATE_RESUMED;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test android.server.cts.ActivityManagerAssistantStackTests
 */
public class ActivityManagerAssistantStackTests extends ActivityManagerTestBase {

    private static final String VOICE_INTERACTION_SERVICE = "AssistantVoiceInteractionService";

    private static final String TEST_ACTIVITY = "TestActivity";
    private static final String ANIMATION_TEST_ACTIVITY = "AnimationTestActivity";
    private static final String DOCKED_ACTIVITY = "DockedActivity";
    private static final String ASSISTANT_ACTIVITY = "AssistantActivity";
    private static final String TRANSLUCENT_ASSISTANT_ACTIVITY = "TranslucentAssistantActivity";
    private static final String LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION =
            "LaunchAssistantActivityFromSession";
    private static final String LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK =
            "LaunchAssistantActivityIntoAssistantStack";
    private static final String PIP_ACTIVITY = "PipActivity";

    private static final String EXTRA_ENTER_PIP = "enter_pip";
    private static final String EXTRA_LAUNCH_NEW_TASK = "launch_new_task";
    private static final String EXTRA_FINISH_SELF = "finish_self";
    public static final String EXTRA_IS_TRANSLUCENT = "is_translucent";

    private static final String TEST_ACTIVITY_ACTION_FINISH_SELF =
            "android.server.cts.TestActivity.finish_self";

    public void testLaunchingAssistantActivityIntoAssistantStack() throws Exception {
        // Enable the assistant and launch an assistant activity
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        mAmWmState.waitForValidState(mDevice, ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);

        // Ensure that the activity launched in the fullscreen assistant stack
        assertAssistantStackExists();
        assertTrue("Expected assistant stack to be fullscreen",
                mAmWmState.getAmState().getStackById(ASSISTANT_STACK_ID).isFullscreen());

        disableAssistant();
    }

    public void testAssistantStackZOrder() throws Exception {
        if (!supportsPip() || !supportsSplitScreenMultiWindow()) return;
        // Launch a pinned stack task
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);

        // Dock a task
        launchActivity(TEST_ACTIVITY);
        launchActivityInDockStack(DOCKED_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        // Enable the assistant and launch an assistant activity, ensure it is on top
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        mAmWmState.waitForValidState(mDevice, ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();

        mAmWmState.assertFrontStack("Pinned stack should be on top.", PINNED_STACK_ID);
        mAmWmState.assertFocusedStack("Assistant stack should be focused.", ASSISTANT_STACK_ID);

        disableAssistant();
    }

    public void testAssistantStackLaunchNewTask() throws Exception {
        enableAssistant();
        assertAssistantStackCanLaunchAndReturnFromNewTask();
        disableAssistant();
    }

    public void testAssistantStackLaunchNewTaskWithDockedStack() throws Exception {
        if (!supportsSplitScreenMultiWindow()) return;
        // Dock a task
        launchActivity(TEST_ACTIVITY);
        launchActivityInDockStack(DOCKED_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        enableAssistant();
        assertAssistantStackCanLaunchAndReturnFromNewTask();
        disableAssistant();
    }

    private void assertAssistantStackCanLaunchAndReturnFromNewTask() throws Exception {
        // Enable the assistant and launch an assistant activity which will launch a new task
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_LAUNCH_NEW_TASK, TEST_ACTIVITY);
        disableAssistant();

        // Ensure that the fullscreen stack is on top and the test activity is now visible
        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedActivity("TestActivity should be resumed", TEST_ACTIVITY);
        mAmWmState.assertFrontStack("Fullscreen stack should be on top.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedStack("Fullscreen stack should be focused.",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Now, tell it to finish itself and ensure that the assistant stack is brought back forward
        executeShellCommand("am broadcast -a " + TEST_ACTIVITY_ACTION_FINISH_SELF);
        mAmWmState.waitForFocusedStack(mDevice, ASSISTANT_STACK_ID);
        mAmWmState.assertFrontStack("Assistant stack should be on top.", ASSISTANT_STACK_ID);
        mAmWmState.assertFocusedStack("Assistant stack should be focused.", ASSISTANT_STACK_ID);
    }

    public void testAssistantStackFinishToPreviousApp() throws Exception {
        // Launch an assistant activity on top of an existing fullscreen activity, and ensure that
        // the fullscreen activity is still visible and on top after the assistant activity finishes
        launchActivity(TEST_ACTIVITY);
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_FINISH_SELF, "true");
        disableAssistant();
        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY, STATE_RESUMED);
        mAmWmState.assertFocusedActivity("TestActivity should be resumed", TEST_ACTIVITY);
        mAmWmState.assertFrontStack("Fullscreen stack should be on top.",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedStack("Fullscreen stack should be focused.",
                FULLSCREEN_WORKSPACE_STACK_ID);
    }

    public void testDisallowEnterPiPFromAssistantStack() throws Exception {
        enableAssistant();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_ENTER_PIP, "true");
        disableAssistant();
        mAmWmState.waitForValidState(mDevice, ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        mAmWmState.assertDoesNotContainStack("Must not contain pinned stack.", PINNED_STACK_ID);
    }

    public void testTranslucentAssistantActivityStackVisibility() throws Exception {
        enableAssistant();
        // Go home, launch the assistant and check to see that home is visible
        removeStacks(FULLSCREEN_WORKSPACE_STACK_ID);
        launchHomeActivity();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidState(mDevice, TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertHomeActivityVisible(true);

        // Launch a fullscreen app and then launch the assistant and check to see that it is
        // also visible
        removeStacks(ASSISTANT_STACK_ID);
        launchActivity(TEST_ACTIVITY);
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidState(mDevice, TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        // Go home, launch assistant, launch app into fullscreen with activity present, and go back.
        // Ensure home is visible.
        removeStacks(ASSISTANT_STACK_ID);
        launchHomeActivity();
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true), EXTRA_LAUNCH_NEW_TASK,
                TEST_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertHomeActivityVisible(false);
        pressBackButton();
        mAmWmState.waitForFocusedStack(mDevice, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertHomeActivityVisible(true);

        // Launch a fullscreen and docked app and then launch the assistant and check to see that it
        // is also visible
        if (supportsSplitScreenMultiWindow()) {
            removeStacks(ASSISTANT_STACK_ID);
            launchActivityInDockStack(DOCKED_ACTIVITY);
            launchActivity(TEST_ACTIVITY);
            mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);
            launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                    EXTRA_IS_TRANSLUCENT, String.valueOf(true));
            mAmWmState.waitForValidState(mDevice, TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
            assertAssistantStackExists();
            mAmWmState.assertVisibility(DOCKED_ACTIVITY, true);
            mAmWmState.assertVisibility(TEST_ACTIVITY, true);
        }
        disableAssistant();
    }

    public void testLaunchIntoSameTask() throws Exception {
        enableAssistant();

        // Launch the assistant
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
        mAmWmState.assertFocusedStack("Expected assistant stack focused", ASSISTANT_STACK_ID);
        assertEquals(1, mAmWmState.getAmState().getStackById(ASSISTANT_STACK_ID).getTasks().size());
        final int taskId = mAmWmState.getAmState().getTaskByActivityName(ASSISTANT_ACTIVITY)
                .mTaskId;

        // Launch a new fullscreen activity
        // Using Animation Test Activity because it is opaque on all devices.
        launchActivity(ANIMATION_TEST_ACTIVITY);
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, false);

        // Launch the assistant again and ensure that it goes into the same task
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(ASSISTANT_ACTIVITY, true);
        mAmWmState.assertFocusedStack("Expected assistant stack focused", ASSISTANT_STACK_ID);
        assertEquals(1, mAmWmState.getAmState().getStackById(ASSISTANT_STACK_ID).getTasks().size());
        assertEquals(taskId,
                mAmWmState.getAmState().getTaskByActivityName(ASSISTANT_ACTIVITY).mTaskId);

        disableAssistant();
    }

    public void testPinnedStackWithAssistant() throws Exception {
        if (!supportsPip() || !supportsSplitScreenMultiWindow()) return;

        enableAssistant();

        // Launch a fullscreen activity and a PIP activity, then launch the assistant, and ensure
        // that the test activity is still visible
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        launchActivity(LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK,
                EXTRA_IS_TRANSLUCENT, String.valueOf(true));
        mAmWmState.waitForValidState(mDevice, TRANSLUCENT_ASSISTANT_ACTIVITY, ASSISTANT_STACK_ID);
        assertAssistantStackExists();
        mAmWmState.assertVisibility(TRANSLUCENT_ASSISTANT_ACTIVITY, true);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY, true);

        disableAssistant();
    }

    /**
     * Asserts that the assistant stack exists.
     */
    private void assertAssistantStackExists() throws Exception {
        mAmWmState.assertContainsStack("Must contain assistant stack.", ASSISTANT_STACK_ID);
    }

    /**
     * Asserts that the assistant stack does not exist.
     */
    private void assertAssistantStackDoesNotExist() throws Exception {
        mAmWmState.assertDoesNotContainStack("Must not contain assistant stack.",
                ASSISTANT_STACK_ID);
    }

    /**
     * Sets the system voice interaction service.
     */
    private void enableAssistant() throws Exception {
        executeShellCommand("settings put secure voice_interaction_service " +
                getActivityComponentName(VOICE_INTERACTION_SERVICE));
    }

    /**
     * Resets the system voice interaction service.
     */
    private void disableAssistant() throws Exception {
        executeShellCommand("settings delete secure voice_interaction_service " +
                getActivityComponentName(VOICE_INTERACTION_SERVICE));
    }
}
