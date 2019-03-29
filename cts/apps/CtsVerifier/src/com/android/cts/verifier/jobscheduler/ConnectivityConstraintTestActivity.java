package com.android.cts.verifier.jobscheduler;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;

import com.android.cts.verifier.R;

/**
 * The majority of the connectivity constraints are done in the device-side test app
 * android.jobscheduler.cts.deviceside. However a manual tester is required to completely turn off
 * connectivity on the device in order to verify that jobs with connectivity constraints will not
 * run in the absence of an internet connection.
 */
@TargetApi(21)
public class ConnectivityConstraintTestActivity extends ConstraintTestActivity {
    private static final String TAG = "ConnectivityConstraintTestActivity";
    private static final int ANY_CONNECTIVITY_JOB_ID =
            ConnectivityConstraintTestActivity.class.hashCode() + 0;
    private static final int UNMETERED_CONNECTIVITY_JOB_ID =
            ConnectivityConstraintTestActivity.class.hashCode() + 1;
    private static final int NO_CONNECTIVITY_JOB_ID =
            ConnectivityConstraintTestActivity.class.hashCode() + 2;
    private static final int NO_CONNECTIVITY_JOB_ID_2 =
            ConnectivityConstraintTestActivity.class.hashCode() + 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the UI.
        setContentView(R.layout.js_connectivity);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.js_connectivity_test, R.string.js_connectivity_instructions, -1);
        mStartButton = (Button) findViewById(R.id.js_connectivity_start_test_button);

        // Disable test start if there is data connectivity.
        mStartButton.setEnabled(isDataUnavailable());
        // Register receiver to listen for connectivity changes.
        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mConnectivityChangedReceiver, intentFilter);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mConnectivityChangedReceiver);
    }

    @Override
    protected void startTestImpl() {
        new TestConnectivityConstraint().execute();
    }

    /** Ensure that there's no connectivity before we allow the test to start. */
    BroadcastReceiver mConnectivityChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "received: " + intent);
            String extras = "";
            for (String name : intent.getExtras().keySet()) {
                extras += " |" + name + " " + intent.getExtras().get(name) + "|";

            }
            Log.d(TAG, "extras: " + extras);
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                // Only enable the test when we know there is no connectivity.
                mStartButton.setEnabled(isDataUnavailable());
            }
        }
    };

    private void testUnmeteredConstraintFails_noConnectivity() {
        testConnectivityConstraintFailsImpl(
                JobInfo.NETWORK_TYPE_UNMETERED, UNMETERED_CONNECTIVITY_JOB_ID);
    }

    private void testAnyConnectivityConstraintFails_noConnectivity() {
        testConnectivityConstraintFailsImpl(JobInfo.NETWORK_TYPE_ANY, ANY_CONNECTIVITY_JOB_ID);
    }

    private void testNoConnectivityConstraintExecutes_noConnectivity() {
        JobInfo testJob1 = new JobInfo.Builder(NO_CONNECTIVITY_JOB_ID, mMockComponent)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setOverrideDeadline(100000L)  // Will not expire.
                .build();
        JobInfo testJob2 = new JobInfo.Builder(NO_CONNECTIVITY_JOB_ID_2, mMockComponent)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
        .setOverrideDeadline(100000L)  // Will not expire.
        .build();

        mTestEnvironment.setUp();
        mTestEnvironment.setExpectedExecutions(2);

        mJobScheduler.schedule(testJob1);
        mJobScheduler.schedule(testJob2);

        boolean testPassed;
        try {
            testPassed = mTestEnvironment.awaitExecution();
        } catch (InterruptedException e) {
            testPassed = false;
        }
        runOnUiThread(
                new ConnectivityConstraintTestResultRunner(NO_CONNECTIVITY_JOB_ID, testPassed));
    }

    private void testConnectivityConstraintFailsImpl(int requiredNetworkType, int jobId) {
        // Use arguments provided to construct job with required connectivity constraint.
        JobInfo testJob = new JobInfo.Builder(jobId, mMockComponent)
                .setRequiredNetworkType(requiredNetworkType)
                .build();

        mTestEnvironment.setUp();
        mTestEnvironment.setExpectedExecutions(0);

        mJobScheduler.schedule(testJob);

        boolean testPassed;
        try {
            testPassed = mTestEnvironment.awaitTimeout();
        } catch (InterruptedException e) {
            testPassed = false;
        }
        runOnUiThread(
                new ConnectivityConstraintTestResultRunner(jobId, testPassed));
    }

    /** Query the active network connection and return if there is no data connection. */
    private boolean isDataUnavailable() {
        final ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        return (activeNetwork == null) ||
                !activeNetwork.isConnectedOrConnecting();
    }

    private class TestConnectivityConstraint extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            testUnmeteredConstraintFails_noConnectivity();
            testAnyConnectivityConstraintFails_noConnectivity();
            testNoConnectivityConstraintExecutes_noConnectivity();

            notifyTestCompleted();
            return null;
        }
    }

    private class ConnectivityConstraintTestResultRunner extends TestResultRunner {
        ConnectivityConstraintTestResultRunner(int jobId, boolean testPassed) {
            super(jobId, testPassed);
        }

        @Override
        public void run() {
            ImageView view;
            if (mJobId == ANY_CONNECTIVITY_JOB_ID) {
                view = (ImageView) findViewById(R.id.connectivity_off_test_any_connectivity_image);
            } else if (mJobId == UNMETERED_CONNECTIVITY_JOB_ID) {
                view = (ImageView) findViewById(R.id.connectivity_off_test_unmetered_image);
            } else if (mJobId == NO_CONNECTIVITY_JOB_ID) {
                view = (ImageView) findViewById(R.id.connectivity_off_test_no_connectivity_image);
            } else {
                noteInvalidTest();
                return;
            }
            view.setImageResource(mTestPassed ? R.drawable.fs_good : R.drawable.fs_error);
        }
    }

}
