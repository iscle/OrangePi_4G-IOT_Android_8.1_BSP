package com.android.cts.verifier.jobscheduler;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.BatteryManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.View;

import com.android.cts.verifier.R;
import com.android.cts.verifier.TimerProgressBar;

/**
 *  This activity runs the following tests:
 *     - Ask the tester to ensure the phone is plugged in, and verify that jobs with charging
 *      constraints are run.
 *     - Ask the tester to unplug the phone, and verify that jobs with charging constraints will
 *      not run.
 */
@TargetApi(21)
public class ChargingConstraintTestActivity extends ConstraintTestActivity {

    private static final int ON_CHARGING_JOB_ID =
            ChargingConstraintTestActivity.class.hashCode() + 0;
    private static final int OFF_CHARGING_JOB_ID =
            ChargingConstraintTestActivity.class.hashCode() + 1;

    // Time in milliseconds to wait after power is connected for the phone
    // to get into charging mode.
    private static final long WAIT_FOR_CHARGING_DURATION = 3 * 60 * 1000;

    private static final int STATE_NOT_RUNNING = 0;
    private static final int STATE_WAITING_TO_START_ON_CHARGING_TEST = 1;
    private static final int STATE_ON_CHARGING_TEST_PASSED = 2;

    private int mTestState;
    TimerProgressBar mWaitingForChargingProgressBar;
    TextView mWaitingForChargingTextView;
    TextView mProblemWithChargerTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the UI.
        setContentView(R.layout.js_charging);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.js_charging_test, R.string.js_charging_instructions, -1);
        mStartButton = (Button) findViewById(R.id.js_charging_start_test_button);
        mWaitingForChargingProgressBar = (TimerProgressBar) findViewById(
            R.id.js_waiting_for_charging_progress_bar);
        mWaitingForChargingTextView = (TextView) findViewById(
            R.id.js_waiting_for_charging_text_view);
        mProblemWithChargerTextView = (TextView) findViewById(
            R.id.js_problem_with_charger_text_view);

        if (isDevicePluggedIn()){
            mStartButton.setEnabled(true);
        }

        if (!deviceHasBattery()) {
            // This device has hardwired power, so do not prompt about connecting
            // or disconnecting the charger, and ignore the "no power" test.
            findViewById(R.id.charger_prompt).setVisibility(View.GONE);
            findViewById(R.id.unplug_prompt).setVisibility(View.GONE);
            findViewById(R.id.unplug_test_description).setVisibility(View.GONE);
        }

        hideWaitingForStableChargingViews();

        mTestState = STATE_NOT_RUNNING;

        mJobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);

        // Register receiver for connected/disconnected power events.
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        intentFilter.addAction(BatteryManager.ACTION_CHARGING);
        intentFilter.addAction(BatteryManager.ACTION_DISCHARGING);

        registerReceiver(mChargingChangedReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mChargingChangedReceiver);
    }

    private boolean deviceHasBattery() {
        final Intent batteryInfo = registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return batteryInfo.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
    }

    private boolean isDevicePluggedIn() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        // 0 indicates device is on battery power
        return status != 0;
    }

    @Override
    public void startTestImpl() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        if (bm.isCharging()) {
            new TestDevicePluggedInConstraint().execute();
        } else if (isDevicePluggedIn()) {
            mTestState = STATE_WAITING_TO_START_ON_CHARGING_TEST;
            showWaitingForStableChargingViews();
        }
    }

    private BroadcastReceiver mChargingChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BatteryManager.ACTION_CHARGING.equals(intent.getAction())) {
                if (mTestState == STATE_WAITING_TO_START_ON_CHARGING_TEST) {
                    mWaitingForChargingProgressBar.forceComplete();
                    hideWaitingForStableChargingViews();
                    new TestDevicePluggedInConstraint().execute();
                }
            } else if (BatteryManager.ACTION_DISCHARGING.equals(intent.getAction())) {
                // ignore this [spurious!] broadcast on non-battery devices
                if (deviceHasBattery()) {
                    if (mTestState == STATE_ON_CHARGING_TEST_PASSED) {
                        new TestDeviceUnpluggedConstraint().execute();
                    }
                }
            } else if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
                if (mTestState == STATE_WAITING_TO_START_ON_CHARGING_TEST) {
                    showWaitingForStableChargingViews();
                } else if (mTestState == STATE_NOT_RUNNING) {
                    mStartButton.setEnabled(true);
                }
                mStartButton.setEnabled(true);
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                hideWaitingForStableChargingViews();
            }
        }
    };

    /** Simple state boolean we use to determine whether to continue with the second test. */
    private boolean mDeviceUnpluggedTestPassed = false;

    /**
     * Test blocks and can't be run on the main thread.
     */
    private void testChargingConstraintFails_notCharging() {
        mTestEnvironment.setUp();

        mTestEnvironment.setExpectedExecutions(0);
        JobInfo runOnCharge = new JobInfo.Builder(OFF_CHARGING_JOB_ID, mMockComponent)
                .setRequiresCharging(true)
                .build();
        mJobScheduler.schedule(runOnCharge);

        boolean testPassed;
        try {
            testPassed = mTestEnvironment.awaitTimeout();
        } catch (InterruptedException e) {
            testPassed = false;
        }
        runOnUiThread(new ChargingConstraintTestResultRunner(OFF_CHARGING_JOB_ID, testPassed));
    }

    /**
     * Test blocks and can't be run on the main thread.
     */
    private void testChargingConstraintExecutes_onCharging() {
        mTestEnvironment.setUp();

        JobInfo delayConstraintAndUnexpiredDeadline =
                new JobInfo.Builder(ON_CHARGING_JOB_ID, mMockComponent)
                        .setRequiresCharging(true)
                        .build();

        mTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(delayConstraintAndUnexpiredDeadline);

        boolean testPassed;
        try {
            testPassed = mTestEnvironment.awaitExecution();
        } catch (InterruptedException e) {
            testPassed = false;
        }

        mTestState = testPassed ? STATE_ON_CHARGING_TEST_PASSED : STATE_NOT_RUNNING;
        runOnUiThread(new ChargingConstraintTestResultRunner(ON_CHARGING_JOB_ID, testPassed));
    }

    /** Run test for when the <bold>device is not connected to power.</bold>. */
    private class TestDeviceUnpluggedConstraint extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            testChargingConstraintFails_notCharging();
            notifyTestCompleted();
            return null;
        }

        @Override
        protected void onPostExecute(Void res) {
            mTestState = STATE_NOT_RUNNING;
            mStartButton.setEnabled(true);
        }
    }

    /** Run test for when the <bold>device is connected to power.</bold> */
    private class TestDevicePluggedInConstraint extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            testChargingConstraintExecutes_onCharging();
            return null;
        }
    }

    private class ChargingConstraintTestResultRunner extends TestResultRunner {
        ChargingConstraintTestResultRunner(int jobId, boolean testPassed) {
            super(jobId, testPassed);
        }

        @Override
        public void run() {
            ImageView view;
            if (mJobId == OFF_CHARGING_JOB_ID) {
                view = (ImageView) findViewById(R.id.charging_off_test_image);
            } else if (mJobId == ON_CHARGING_JOB_ID) {
                view = (ImageView) findViewById(R.id.charging_on_test_image);
            } else {
                noteInvalidTest();
                return;
            }
            view.setImageResource(mTestPassed ? R.drawable.fs_good : R.drawable.fs_error);
        }
    }

    private void showWaitingForStableChargingViews() {
        mWaitingForChargingProgressBar.start(WAIT_FOR_CHARGING_DURATION, 1000,
            new TimerProgressBar.TimerExpiredCallback(){
                @Override
                public void onTimerExpired() {
                    mProblemWithChargerTextView.setVisibility(View.VISIBLE);
                }
            }
        );
        mWaitingForChargingProgressBar.setVisibility(View.VISIBLE);
        mWaitingForChargingTextView.setVisibility(View.VISIBLE);
        mProblemWithChargerTextView.setVisibility(View.GONE);
    }

    private void hideWaitingForStableChargingViews() {
        mWaitingForChargingProgressBar.forceComplete();
        mWaitingForChargingProgressBar.setVisibility(View.GONE);
        mWaitingForChargingTextView.setVisibility(View.GONE);
        mProblemWithChargerTextView.setVisibility(View.GONE);
    }
}
