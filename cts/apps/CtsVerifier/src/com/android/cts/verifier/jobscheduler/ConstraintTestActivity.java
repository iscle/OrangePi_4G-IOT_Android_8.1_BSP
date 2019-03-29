package com.android.cts.verifier.jobscheduler;

import android.annotation.TargetApi;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@TargetApi(21)
public abstract class ConstraintTestActivity extends PassFailButtons.Activity {
    
    protected ComponentName mMockComponent;

    protected MockJobService.TestEnvironment mTestEnvironment;
    protected JobScheduler mJobScheduler;

    /** Avoid cases where user might press "start test" more than once. */
    private boolean mTestInProgress;
    /**
     * Starts the test - set up by subclass, which also controls the logic for how/when the test
     * can be started.
     */
    protected Button mStartButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mJobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        mMockComponent = new ComponentName(this, MockJobService.class);
        mTestEnvironment = MockJobService.TestEnvironment.getTestEnvironment();
    }

    /** OnClickListener for the "Start Test" ({@link #mStartButton}) button */
    public final void startTest(View v) {
        if (mTestInProgress) {
            Toast toast =
                    Toast.makeText(
                            ConstraintTestActivity.this,
                            "Test already in progress",
                            Toast.LENGTH_SHORT);
            toast.show();
            return;
        } else {
            mTestInProgress = true;
            startTestImpl();
        }
    }

    /** Called by subclasses to allow the user to rerun the test if necessary. */
    protected final void notifyTestCompleted() {
        mTestInProgress = false;
    }

    /** Implemented by subclasses to determine logic for running the test. */
    protected abstract void startTestImpl();

    /**
     * Broadcast the provided intent, and register a receiver to notify us after the broadcast has
     * been processed.
     * This function will block until the broadcast comes back, and <bold>cannot</bold> be called
     * on the main thread.
     * @return True if we received the callback, false if not.
     */
    protected boolean sendBroadcastAndBlockForResult(Intent intent) {
        final CountDownLatch latch = new CountDownLatch(1);
        sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        }, null, -1, null, null);
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /** Extended by test activities to report results of a test. */
    protected abstract class TestResultRunner implements Runnable {
        final int mJobId;
        final boolean mTestPassed;

        TestResultRunner(int jobId, boolean testPassed) {
            mJobId = jobId;
            mTestPassed = testPassed;
        }
        protected void noteInvalidTest() {
            final Toast toast =
                    Toast.makeText(
                            ConstraintTestActivity.this,
                            "Invalid result returned from test thread: job=" + mJobId + ", res="
                                    + mTestPassed,
                            Toast.LENGTH_SHORT);
            toast.show();
        }
    }
}
