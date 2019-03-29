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
 * limitations under the License
 */

package com.android.cts.verifier.location.base;

import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;
import com.android.cts.verifier.location.reporting.GnssTestDetails;

import junit.framework.Assert;

import com.android.cts.verifier.PassFailButtons;

import android.content.Context;
import android.content.Intent;
import android.hardware.cts.helpers.ActivityResultMultiplexedLatch;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.test.AndroidTestCase;

/**
 * A base Activity that is used to build different methods to execute tests inside CtsVerifier.
 * i.e. CTS tests, and semi-automated CtsVerifier tests.
 *
 * This class provides access to the following flow:
 *      Activity set up
 *          Execute tests (implemented by sub-classes)
 *      Activity clean up
 *
 * Currently the following class structure is available:
 * - BaseGnssTestActivity                 : provides the platform to execute Gnss tests inside
 *      |                                     CtsVerifier.
 *      |
 *      -- GnssCtsTestActivity            : an activity that can be inherited from to wrap a CTS
 *      |                                     Gnss test, and execute it inside CtsVerifier
 *      |                                     these tests do not require any operator interaction
 */
public abstract class BaseGnssTestActivity extends PassFailButtons.Activity
        implements View.OnClickListener, Runnable, IGnssTestStateContainer {
    @Deprecated
    protected static final String LOG_TAG = "GnssTest";

    protected final Class mTestClass;

    private final int mLayoutId;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private final ActivityResultMultiplexedLatch mActivityResultMultiplexedLatch =
            new ActivityResultMultiplexedLatch();
    private final ArrayList<CountDownLatch> mWaitForUserLatches = new ArrayList<CountDownLatch>();

    private ScrollView mLogScrollView;
    private LinearLayout mLogLayout;
    private Button mNextButton;
    protected TextView mTextView;

    /**
     * Constructor to be used by subclasses.
     *
     * @param testClass The class that contains the tests. It is dependant on test executor
     *                  implemented by subclasses.
     */
    protected BaseGnssTestActivity(Class<? extends AndroidTestCase> testClass) {
        this(testClass, R.layout.gnss_test);
    }

    /**
     * Constructor to be used by subclasses. It allows to provide a custom layout for the test UI.
     *
     * @param testClass The class that contains the tests. It is dependant on test executor
     *                  implemented by subclasses.
     * @param layoutId The Id of the layout to use for the test UI. The layout must contain all the
     *                 elements in the base layout {@code R.layout.gnss_test}.
     */
    protected BaseGnssTestActivity(Class testClass, int layoutId) {
        mTestClass = testClass;
        mLayoutId = layoutId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(mLayoutId);

        mLogScrollView = (ScrollView) findViewById(R.id.log_scroll_view);
        mLogLayout = (LinearLayout) findViewById(R.id.log_layout);
        mNextButton = (Button) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(this);
        mTextView = (TextView) findViewById(R.id.text);

        mTextView.setText(R.string.location_gnss_test_info);

        updateNextButton(false /*not enabled*/);
        mExecutorService.execute(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(View target) {
        synchronized (mWaitForUserLatches) {
            for (CountDownLatch latch : mWaitForUserLatches) {
                latch.countDown();
            }
            mWaitForUserLatches.clear();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mActivityResultMultiplexedLatch.onActivityResult(requestCode, resultCode);
    }

    /**
     * The main execution {@link Thread}.
     *
     * This function executes in a background thread, allowing the test run freely behind the
     * scenes. It provides the following execution hooks:
     *  - Activity SetUp/CleanUp (not available in JUnit)
     *  - executeTests: to implement several execution engines
     */
    @Override
    public void run() {
        long startTimeNs = SystemClock.elapsedRealtimeNanos();
        String testName = getTestClassName();

        GnssTestDetails testDetails;
        try {
            testDetails = new GnssTestDetails(testName, GnssTestDetails.ResultCode.PASS);
        } catch (Throwable e) {
            testDetails = new GnssTestDetails(testName, "DeactivateFeatures", e);
        }

        GnssTestDetails.ResultCode resultCode = testDetails.getResultCode();
        if (resultCode == GnssTestDetails.ResultCode.SKIPPED) {
            // this is an invalid state at this point of the test setup
            throw new IllegalStateException("Deactivation of features cannot skip the test.");
        }
        if (resultCode == GnssTestDetails.ResultCode.PASS) {
            testDetails = executeActivityTests(testName);
        }

        // This set the test UI so the operator can report the result of the test
        updateResult(testDetails);
    }

    /**
     * A general set up routine. It executes only once before the first test case.
     *
     * NOTE: implementers must be aware of the interrupted status of the worker thread, and let
     * {@link InterruptedException} propagate.
     *
     * @throws Throwable An exception that denotes the failure of set up. No tests will be executed.
     */
    protected void activitySetUp() throws Throwable {}

    /**
     * A general clean up routine. It executes upon successful execution of {@link #activitySetUp()}
     * and after all the test cases.
     *
     * NOTE: implementers must be aware of the interrupted status of the worker thread, and handle
     * it in two cases:
     * - let {@link InterruptedException} propagate
     * - if it is invoked with the interrupted status, prevent from showing any UI

     * @throws Throwable An exception that will be logged and ignored, for ease of implementation
     *                   by subclasses.
     */
    protected void activityCleanUp() throws Throwable {}

    /**
     * Performs the work of executing the tests.
     * Sub-classes implementing different execution methods implement this method.
     *
     * @return A {@link GnssTestDetails} object containing information about the executed tests.
     */
    protected abstract GnssTestDetails executeTests() throws InterruptedException;

    @Deprecated
    protected void appendText(String text) {
        TextAppender textAppender = new TextAppender(R.layout.snsr_instruction);
        textAppender.setText(text);
        textAppender.append();
    }

    @Deprecated
    protected void clearText() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLogLayout.removeAllViews();
            }
        });
    }

    /**
     * Waits for the operator to acknowledge a requested action.
     *
     * @param waitMessageResId The action requested to the operator.
     */
    protected void waitForUser(int waitMessageResId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (mWaitForUserLatches) {
            mWaitForUserLatches.add(latch);
        }

        updateNextButton(true);
        latch.await();
        updateNextButton(false);
    }

    /**
     * Waits for the operator to acknowledge to begin execution.
     */
    protected void waitForUserToBegin() throws InterruptedException {
        waitForUser(R.string.snsr_wait_to_begin);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForUserToContinue() throws InterruptedException {
        waitForUser(R.string.snsr_wait_for_user);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int executeActivity(String action) throws InterruptedException {
        return executeActivity(new Intent(action));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int executeActivity(Intent intent) throws InterruptedException {
        ActivityResultMultiplexedLatch.Latch latch = mActivityResultMultiplexedLatch.bindThread();
        startActivityForResult(intent, latch.getRequestCode());
        return latch.await();
    }

    /**
     * Plays a (default) sound as a notification for the operator.
     */
    protected void playSound() throws InterruptedException {
        MediaPlayer player = MediaPlayer.create(this, Settings.System.DEFAULT_NOTIFICATION_URI);
        if (player == null) {
            Log.e(LOG_TAG, "MediaPlayer unavailable.");
            return;
        }
        player.start();
        try {
            Thread.sleep(500);
        } finally {
            player.stop();
        }
    }

    /**
     * Makes the device vibrate for the given amount of time.
     */
    protected void vibrate(int timeInMs) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(timeInMs);
    }

    /**
     * Makes the device vibrate following the given pattern.
     * See {@link Vibrator#vibrate(long[], int)} for more information.
     */
    protected void vibrate(long[] pattern) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        vibrator.vibrate(pattern, -1);
    }

    protected String getTestClassName() {
        if (mTestClass == null) {
            return "<unknown>";
        }
        return mTestClass.getName();
    }

    protected void setLogScrollViewListener(View.OnTouchListener listener) {
        mLogScrollView.setOnTouchListener(listener);
    }

    private void setTestResult(GnssTestDetails testDetails) {
        // the name here, must be the Activity's name because it is what CtsVerifier expects
        String name = super.getClass().getName();
        GnssTestDetails.ResultCode resultCode = testDetails.getResultCode();
        switch(resultCode) {
            case SKIPPED:
                TestResult.setPassedResult(this, name, "");
                break;
            case PASS:
                TestResult.setPassedResult(this, name, "");
                break;
            case FAIL:
                TestResult.setFailedResult(this, name, "");
                break;
            case INTERRUPTED:
                // do not set a result, just return so the test can complete
                break;
            default:
                throw new IllegalStateException("Unknown ResultCode: " + resultCode);
        }
    }

    private GnssTestDetails executeActivityTests(String testName) {
        GnssTestDetails testDetails;
        try {
            activitySetUp();
            testDetails = new GnssTestDetails(testName, GnssTestDetails.ResultCode.PASS);
        } catch (Throwable e) {
            testDetails = new GnssTestDetails(testName, "ActivitySetUp", e);
        }

        GnssTestDetails.ResultCode resultCode = testDetails.getResultCode();
        if (resultCode == GnssTestDetails.ResultCode.PASS) {
            // TODO: implement execution filters:
            //      - execute all tests and report results officially
            //      - execute single test or failed tests only
            try {
                testDetails = executeTests();
            } catch (Throwable e) {
                // we catch and continue because we have to guarantee a proper clean-up sequence
                testDetails = new GnssTestDetails(testName, "TestExecution", e);
            }
        }

        // clean-up executes for all states, even on SKIPPED and INTERRUPTED there might be some
        // intermediate state that needs to be taken care of
        try {
            activityCleanUp();
        } catch (Throwable e) {
            testDetails = new GnssTestDetails(testName, "ActivityCleanUp", e);
        }

        return testDetails;
    }

    private void updateResult(final GnssTestDetails testDetails) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setTestResult(testDetails);
            }
        });
    }

    private void updateNextButton(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mNextButton.setEnabled(enabled);
            }
        });
    }

    private class ViewAppender {
        protected final View mView;

        public ViewAppender(View view) {
            mView = view;
        }

        public void append() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLogLayout.addView(mView);
                    mLogScrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            mLogScrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            });
        }
    }

    private class TextAppender extends ViewAppender{
        private final TextView mTextView;

        public TextAppender(int textViewResId) {
            super(getLayoutInflater().inflate(textViewResId, null /* viewGroup */));
            mTextView = (TextView) mView;
        }

        public void setText(String text) {
            mTextView.setText(text);
        }

        public void setText(int textResId) {
            mTextView.setText(textResId);
        }
    }
}