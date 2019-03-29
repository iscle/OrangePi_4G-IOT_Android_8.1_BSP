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

package com.android.cts.verifier.sensors.base;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;
import com.android.cts.verifier.sensors.helpers.SensorFeaturesDeactivator;
import com.android.cts.verifier.sensors.reporting.SensorTestDetails;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.cts.helpers.ActivityResultMultiplexedLatch;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
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

import junit.framework.Assert;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
 * - BaseSensorTestActivity                 : provides the platform to execute Sensor tests inside
 *      |                                     CtsVerifier, and logging support
 *      |
 *      -- SensorCtsTestActivity            : an activity that can be inherited from to wrap a CTS
 *      |                                     sensor test, and execute it inside CtsVerifier
 *      |                                     these tests do not require any operator interaction
 *      |
 *      -- SensorCtsVerifierTestActivity    : an activity that can be inherited to write sensor
 *                                            tests that require operator interaction
 */
public abstract class BaseSensorTestActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener, Runnable, ISensorTestStateContainer {
    @Deprecated
    protected static final String LOG_TAG = "SensorTest";

    protected final Class mTestClass;

    private final int mLayoutId;
    private final SensorFeaturesDeactivator mSensorFeaturesDeactivator;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private final SensorTestLogger mTestLogger = new SensorTestLogger();
    private final ActivityResultMultiplexedLatch mActivityResultMultiplexedLatch =
            new ActivityResultMultiplexedLatch();
    private final ArrayList<CountDownLatch> mWaitForUserLatches = new ArrayList<CountDownLatch>();

    private ScrollView mLogScrollView;
    private LinearLayout mLogLayout;
    private Button mNextButton;
    private Button mPassButton;
    private Button mFailButton;

    private GLSurfaceView mGLSurfaceView;
    private boolean mUsingGlSurfaceView;

    /**
     * Constructor to be used by subclasses.
     *
     * @param testClass The class that contains the tests. It is dependant on test executor
     *                  implemented by subclasses.
     */
    protected BaseSensorTestActivity(Class testClass) {
        this(testClass, R.layout.sensor_test);
    }

    /**
     * Constructor to be used by subclasses. It allows to provide a custom layout for the test UI.
     *
     * @param testClass The class that contains the tests. It is dependant on test executor
     *                  implemented by subclasses.
     * @param layoutId The Id of the layout to use for the test UI. The layout must contain all the
     *                 elements in the base layout {@code R.layout.sensor_test}.
     */
    protected BaseSensorTestActivity(Class testClass, int layoutId) {
        mTestClass = testClass;
        mLayoutId = layoutId;
        mSensorFeaturesDeactivator = new SensorFeaturesDeactivator(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(mLayoutId);

        mLogScrollView = (ScrollView) findViewById(R.id.log_scroll_view);
        mLogLayout = (LinearLayout) findViewById(R.id.log_layout);
        mNextButton = (Button) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(this);
        mPassButton = (Button) findViewById(R.id.pass_button);
        mFailButton = (Button) findViewById(R.id.fail_button);
        mGLSurfaceView = (GLSurfaceView) findViewById(R.id.gl_surface_view);

        updateNextButton(false /*enabled*/);
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
        if (mUsingGlSurfaceView) {
            mGLSurfaceView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mUsingGlSurfaceView) {
            mGLSurfaceView.onResume();
        }
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

        SensorTestDetails testDetails;
        try {
            mSensorFeaturesDeactivator.requestDeactivationOfFeatures();
            testDetails = new SensorTestDetails(testName, SensorTestDetails.ResultCode.PASS);
        } catch (Throwable e) {
            testDetails = new SensorTestDetails(testName, "DeactivateSensorFeatures", e);
        }

        SensorTestDetails.ResultCode resultCode = testDetails.getResultCode();
        if (resultCode == SensorTestDetails.ResultCode.SKIPPED) {
            // this is an invalid state at this point of the test setup
            throw new IllegalStateException("Deactivation of features cannot skip the test.");
        }
        if (resultCode == SensorTestDetails.ResultCode.PASS) {
            testDetails = executeActivityTests(testName);
        }

        // we consider all remaining states at this point, because we could have been half way
        // deactivating features
        try {
            mSensorFeaturesDeactivator.requestToRestoreFeatures();
        } catch (Throwable e) {
            testDetails = new SensorTestDetails(testName, "RestoreSensorFeatures", e);
        }

        mTestLogger.logTestDetails(testDetails);
        mTestLogger.logExecutionTime(startTimeNs);

        // because we cannot enforce test failures in several devices, set the test UI so the
        // operator can report the result of the test
        promptUserToSetResult(testDetails);
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
     * @return A {@link SensorTestDetails} object containing information about the executed tests.
     */
    protected abstract SensorTestDetails executeTests() throws InterruptedException;

    @Override
    public SensorTestLogger getTestLogger() {
        return mTestLogger;
    }

    @Deprecated
    protected void appendText(int resId) {
        mTestLogger.logInstructions(resId);
    }

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

        mTestLogger.logInstructions(waitMessageResId);
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
        try {
            startActivityForResult(intent, latch.getRequestCode());
        } catch (ActivityNotFoundException e) {
            // handle exception gracefully
            // Among all defined activity results, RESULT_CANCELED offers the semantic closest to
            // represent absent setting activity.
            return RESULT_CANCELED;
        }
        return latch.await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasSystemFeature(String feature) {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(feature);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasActivity(String action) {
        PackageManager pm = getPackageManager();
        return pm.resolveActivity(new Intent(action), PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    /**
     * Initializes and shows the {@link GLSurfaceView} available to tests.
     * NOTE: initialization can be performed only once, usually inside {@link #activitySetUp()}.
     */
    protected void initializeGlSurfaceView(final GLSurfaceView.Renderer renderer) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mGLSurfaceView.setVisibility(View.VISIBLE);
                mGLSurfaceView.setRenderer(renderer);
                mUsingGlSurfaceView = true;
            }
        });
    }

    /**
     * Closes and hides the {@link GLSurfaceView}.
     */
    protected void closeGlSurfaceView() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!mUsingGlSurfaceView) {
                    return;
                }
                mGLSurfaceView.setVisibility(View.GONE);
                mGLSurfaceView.onPause();
                mUsingGlSurfaceView = false;
            }
        });
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

    // TODO: move to sensor assertions
    protected String assertTimestampSynchronization(
            long eventTimestamp,
            long receivedTimestamp,
            long deltaThreshold,
            String sensorName) {
        long timestampDelta = Math.abs(eventTimestamp - receivedTimestamp);
        String timestampMessage = getString(
                R.string.snsr_event_time,
                receivedTimestamp,
                eventTimestamp,
                timestampDelta,
                deltaThreshold,
                sensorName);
        Assert.assertTrue(timestampMessage, timestampDelta < deltaThreshold);
        return timestampMessage;
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

    private void setTestResult(SensorTestDetails testDetails) {
        // the name here, must be the Activity's name because it is what CtsVerifier expects
        String name = super.getClass().getName();
        String summary = mTestLogger.getOverallSummary();
        SensorTestDetails.ResultCode resultCode = testDetails.getResultCode();
        switch(resultCode) {
            case SKIPPED:
                TestResult.setPassedResult(this, name, summary);
                break;
            case PASS:
                TestResult.setPassedResult(this, name, summary);
                break;
            case FAIL:
                TestResult.setFailedResult(this, name, summary);
                break;
            case INTERRUPTED:
                // do not set a result, just return so the test can complete
                break;
            default:
                throw new IllegalStateException("Unknown ResultCode: " + resultCode);
        }
    }

    private SensorTestDetails executeActivityTests(String testName) {
        SensorTestDetails testDetails;
        try {
            activitySetUp();
            testDetails = new SensorTestDetails(testName, SensorTestDetails.ResultCode.PASS);
        } catch (Throwable e) {
            testDetails = new SensorTestDetails(testName, "ActivitySetUp", e);
        }

        SensorTestDetails.ResultCode resultCode = testDetails.getResultCode();
        if (resultCode == SensorTestDetails.ResultCode.PASS) {
            // TODO: implement execution filters:
            //      - execute all tests and report results officially
            //      - execute single test or failed tests only
            try {
                testDetails = executeTests();
            } catch (Throwable e) {
                // we catch and continue because we have to guarantee a proper clean-up sequence
                testDetails = new SensorTestDetails(testName, "TestExecution", e);
            }
        }

        // clean-up executes for all states, even on SKIPPED and INTERRUPTED there might be some
        // intermediate state that needs to be taken care of
        try {
            activityCleanUp();
        } catch (Throwable e) {
            testDetails = new SensorTestDetails(testName, "ActivityCleanUp", e);
        }

        return testDetails;
    }

    private void promptUserToSetResult(SensorTestDetails testDetails) {
        SensorTestDetails.ResultCode resultCode = testDetails.getResultCode();
        if (resultCode == SensorTestDetails.ResultCode.FAIL) {
            mTestLogger.logInstructions(R.string.snsr_test_complete_with_errors);
            enableTestResultButton(
                    mFailButton,
                    R.string.fail_button_text,
                    testDetails.cloneAndChangeResultCode(SensorTestDetails.ResultCode.FAIL));
        } else if (resultCode != SensorTestDetails.ResultCode.INTERRUPTED) {
            mTestLogger.logInstructions(R.string.snsr_test_complete);
            enableTestResultButton(
                    mPassButton,
                    R.string.pass_button_text,
                    testDetails.cloneAndChangeResultCode(SensorTestDetails.ResultCode.PASS));
        }
    }

    private void updateNextButton(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mNextButton.setEnabled(enabled);
            }
        });
    }

    private void enableTestResultButton(
            final Button button,
            final int textResId,
            final SensorTestDetails testDetails) {
        final View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTestResult(testDetails);
                finish();
            }
        };

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mNextButton.setVisibility(View.GONE);
                button.setText(textResId);
                button.setOnClickListener(listener);
                button.setVisibility(View.VISIBLE);
            }
        });
    }

    // a logger available until sensor reporting is in place
    public class SensorTestLogger {
        private static final String SUMMARY_SEPARATOR = " | ";

        private final StringBuilder mOverallSummaryBuilder = new StringBuilder("\n");

        public void logCustomView(View view) {
            new ViewAppender(view).append();
        }

        void logTestStart(String testName) {
            // TODO: log the sensor information and expected execution time of each test
            TextAppender textAppender = new TextAppender(R.layout.snsr_test_title);
            textAppender.setText(testName);
            textAppender.append();
        }

        public void logInstructions(int instructionsResId, Object ... params) {
            TextAppender textAppender = new TextAppender(R.layout.snsr_instruction);
            textAppender.setText(getString(instructionsResId, params));
            textAppender.append();
        }

        public void logMessage(int messageResId, Object ... params) {
            TextAppender textAppender = new TextAppender(R.layout.snsr_message);
            textAppender.setText(getString(messageResId, params));
            textAppender.append();
        }

        public void logWaitForSound() {
            logInstructions(R.string.snsr_test_play_sound);
        }

        public void logTestDetails(SensorTestDetails testDetails) {
            String name = testDetails.getName();
            String summary = testDetails.getSummary();
            SensorTestDetails.ResultCode resultCode = testDetails.getResultCode();
            switch (resultCode) {
                case SKIPPED:
                    logTestSkip(name, summary);
                    break;
                case PASS:
                    logTestPass(name, summary);
                    break;
                case FAIL:
                    logTestFail(name, summary);
                    break;
                case INTERRUPTED:
                    // do nothing, the test was interrupted so do we
                    break;
                default:
                    throw new IllegalStateException("Unknown ResultCode: " + resultCode);
            }
        }

        void logTestPass(String testName, String testSummary) {
            testSummary = getValidTestSummary(testSummary, R.string.snsr_test_pass);
            logTestEnd(R.layout.snsr_success, testSummary);
            Log.d(LOG_TAG, testSummary);
            saveResult(testName, SensorTestDetails.ResultCode.PASS, testSummary);
        }

        public void logTestFail(String testName, String testSummary) {
            testSummary = getValidTestSummary(testSummary, R.string.snsr_test_fail);
            logTestEnd(R.layout.snsr_error, testSummary);
            Log.e(LOG_TAG, testSummary);
            saveResult(testName, SensorTestDetails.ResultCode.FAIL, testSummary);
        }

        void logTestSkip(String testName, String testSummary) {
            testSummary = getValidTestSummary(testSummary, R.string.snsr_test_skipped);
            logTestEnd(R.layout.snsr_warning, testSummary);
            Log.i(LOG_TAG, testSummary);
            saveResult(testName, SensorTestDetails.ResultCode.SKIPPED, testSummary);
        }

        String getOverallSummary() {
            return mOverallSummaryBuilder.toString();
        }

        void logExecutionTime(long startTimeNs) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            long executionTimeNs = SystemClock.elapsedRealtimeNanos() - startTimeNs;
            long executionTimeSec = TimeUnit.NANOSECONDS.toSeconds(executionTimeNs);
            // TODO: find a way to format times with nanosecond accuracy and longer than 24hrs
            String formattedElapsedTime = DateUtils.formatElapsedTime(executionTimeSec);
            logMessage(R.string.snsr_execution_time, formattedElapsedTime);
        }

        private void logTestEnd(int textViewResId, String testSummary) {
            TextAppender textAppender = new TextAppender(textViewResId);
            textAppender.setText(testSummary);
            textAppender.append();
        }

        private String getValidTestSummary(String testSummary, int defaultSummaryResId) {
            if (TextUtils.isEmpty(testSummary)) {
                return getString(defaultSummaryResId);
            }
            return testSummary;
        }

        private void saveResult(
                String testName,
                SensorTestDetails.ResultCode resultCode,
                String summary) {
            mOverallSummaryBuilder.append(testName);
            mOverallSummaryBuilder.append(SUMMARY_SEPARATOR);
            mOverallSummaryBuilder.append(resultCode.name());
            mOverallSummaryBuilder.append(SUMMARY_SEPARATOR);
            mOverallSummaryBuilder.append(summary);
            mOverallSummaryBuilder.append("\n");
        }
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
