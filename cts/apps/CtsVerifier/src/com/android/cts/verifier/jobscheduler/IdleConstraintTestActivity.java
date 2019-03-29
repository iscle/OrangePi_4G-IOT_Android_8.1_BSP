/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.jobscheduler;

import com.android.cts.verifier.R;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 *  Idle constraints:
 *      The framework doesn't support turning the screen off. Use the manual tester to
 *      turn off the screen to run to run tests that require idle mode to be on.
 */
@TargetApi(21)
public class IdleConstraintTestActivity extends ConstraintTestActivity {
    private static final String TAG = "IdleModeTestActivity";
    /**
     * It takes >1hr for idle mode to be triggered. We'll use this secret broadcast to force the
     * scheduler into idle. It's not a protected broadcast so that's alright.
     */
    private static final String ACTION_EXPEDITE_IDLE_MODE =
            "com.android.server.task.controllers.IdleController.ACTION_TRIGGER_IDLE";

    /**
     * Id for the job that we schedule when the device is not in idle mode. This job is expected
     * to not execute. Executing means that the verifier test should fail.
     */
    private static final int IDLE_OFF_JOB_ID = IdleConstraintTestActivity.class.hashCode() + 0;
    /**
     * Id for the job that we schedule when the device *is* in idle mode. This job is expected to
     * execute. Not executing means that the verifier test should fail.
     */
    private static final int IDLE_ON_JOB_ID = IdleConstraintTestActivity.class.hashCode() + 1;

    private static final int IDLE_ON_TEST_STATE_NOT_IN_PROGRESS = 0;
    private static final int IDLE_ON_TEST_STATE_WAITING_FOR_SCREEN_OFF = 1;

    /**
     * mTestState stores the state of the tests. It is used to ensure that we only run
     * the 'idle on' test if screen is turned off after the user has started tests.
     */
    private int mTestState = IDLE_ON_TEST_STATE_NOT_IN_PROGRESS;

    private PowerManager mPowerManager;
    private TextView mContinueInstructionTextView;

    /**
     * Listens for screen off event. Starts an async task to force device into
     * idle mode and run the 'idle on' test.
     */
    private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (mTestState == IDLE_ON_TEST_STATE_WAITING_FOR_SCREEN_OFF) {
                    mContinueInstructionTextView.setVisibility(View.GONE);
                    PowerManager.WakeLock wl = mPowerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK, TAG);
                    wl.acquire();
                    new TestIdleModeTaskIdle().execute(wl);
                }
            } else {
                Log.e(TAG, "Invalid broadcast received, was expecting SCREEN_OFF");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the UI.
        setContentView(R.layout.js_idle);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.js_idle_test, R.string.js_idle_instructions, -1);
        mStartButton = (Button) findViewById(R.id.js_idle_start_test_button);
        mContinueInstructionTextView = (TextView) findViewById(
            R.id.js_idle_continue_instruction_view);

        // Register receiver for screen off event.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        registerReceiver(mScreenOffReceiver, intentFilter);

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Enable start button only if tests are not in progress.
        if (mTestState == IDLE_ON_TEST_STATE_NOT_IN_PROGRESS) {
            mStartButton.setEnabled(true);
            mContinueInstructionTextView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mScreenOffReceiver);
    }

    @Override
    protected void startTestImpl() {
        mStartButton.setEnabled(false);
        new TestIdleModeTaskNotIdle().execute();
    }

    /** Background task that will run the 'not idle' test. */
    private class TestIdleModeTaskNotIdle extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            testIdleConstraintFails_notIdle();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mTestState = IDLE_ON_TEST_STATE_WAITING_FOR_SCREEN_OFF;
            mContinueInstructionTextView.setVisibility(View.VISIBLE);
        }
    }

    /** Background task that will run the 'idle' test. */
    private class TestIdleModeTaskIdle extends AsyncTask<PowerManager.WakeLock, Void, Void> {

        private PowerManager.WakeLock mPartialWakeLock;

        @Override
        protected Void doInBackground(PowerManager.WakeLock... wakeLocks) {
            mPartialWakeLock = wakeLocks[0];

            if (!sendBroadcastAndBlockForResult(new Intent(ACTION_EXPEDITE_IDLE_MODE))) {
                runOnUiThread(new IdleTestResultRunner(IDLE_ON_JOB_ID, false));
            } else {
                testIdleConstraintExecutes_onIdle();
            }
            notifyTestCompleted();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // Reset test state
            mTestState = IDLE_ON_TEST_STATE_NOT_IN_PROGRESS;

            PowerManager.WakeLock fullWakeLock = mPowerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP
                | PowerManager.ON_AFTER_RELEASE, TAG);
            // Turn on screen and release both locks
            fullWakeLock.acquire();
            fullWakeLock.release();
            mPartialWakeLock.release();
        }
    }

    /**
     * The user has just pressed the "Start Test" button, so we know that the device can't be idle.
     * Schedule a job with an idle constraint and verify that it doesn't execute.
     */
    private void testIdleConstraintFails_notIdle() {
        mTestEnvironment.setUp();
        mJobScheduler.cancelAll();

        mTestEnvironment.setExpectedExecutions(0);

        mJobScheduler.schedule(
                new JobInfo.Builder(IDLE_OFF_JOB_ID, mMockComponent)
                        .setRequiresDeviceIdle(true)
                        .build());

        boolean testPassed;
        try {
            testPassed = mTestEnvironment.awaitTimeout();
        } catch (InterruptedException e) {
            // We'll just indicate that it failed, not why.
            testPassed = false;
        }
        runOnUiThread(new IdleTestResultRunner(IDLE_OFF_JOB_ID, testPassed));
    }

    /**
     * Called after screen is switched off and device is forced into idle mode.
     * Schedule a job with an idle constraint and verify that it executes.
     */
    private void testIdleConstraintExecutes_onIdle() {
        mTestEnvironment.setUp();
        mJobScheduler.cancelAll();

        mTestEnvironment.setExpectedExecutions(1);

        mJobScheduler.schedule(
                new JobInfo.Builder(IDLE_ON_JOB_ID, mMockComponent)
                .setRequiresDeviceIdle(true)
                .build());

        boolean testPassed;
        try {
            testPassed = mTestEnvironment.awaitExecution();
        } catch (InterruptedException e) {
            // We'll just indicate that it failed, not why.
            testPassed = false;
        }

        runOnUiThread(new IdleTestResultRunner(IDLE_ON_JOB_ID, testPassed));
    }

    /**
     * Runnable to update the UI with the outcome of the test. This class only runs two tests, so
     * the argument passed into the constructor will indicate which of the tests we are reporting
     * for.
     */
    protected class IdleTestResultRunner extends TestResultRunner {

        IdleTestResultRunner(int jobId, boolean testPassed) {
            super(jobId, testPassed);
        }

        @Override
        public void run() {
            ImageView view;
            if (mJobId == IDLE_OFF_JOB_ID) {
                view = (ImageView) findViewById(R.id.idle_off_test_image);
            } else if (mJobId == IDLE_ON_JOB_ID) {
                view = (ImageView) findViewById(R.id.idle_on_test_image);
            } else {
                noteInvalidTest();
                return;
            }
            view.setImageResource(mTestPassed ? R.drawable.fs_good : R.drawable.fs_error);
        }
    }
}
