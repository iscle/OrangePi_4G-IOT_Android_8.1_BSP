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
 * limitations under the License.
 */

package android.jobscheduler.cts;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that have battery constraints.
 */
@TargetApi(26)
public class BatteryConstraintTest extends ConstraintTest {
    private static final String TAG = "BatteryConstraintTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int BATTERY_JOB_ID = BatteryConstraintTest.class.hashCode();

    private JobInfo.Builder mBuilder;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(BATTERY_JOB_ID, kJobServiceComponent);
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery on");
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(BATTERY_JOB_ID);
        // Put battery service back in to normal operation.
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery off");
        SystemUtil.runShellCommand(getInstrumentation(), "cmd battery reset");
    }

    void setBatteryState(boolean plugged, int level) throws Exception {
        if (plugged) {
            SystemUtil.runShellCommand(getInstrumentation(), "cmd battery set ac 1");
        } else {
            SystemUtil.runShellCommand(getInstrumentation(), "cmd battery unplug");
        }
        int seq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                "cmd battery set -f level " + level).trim());
        long startTime = SystemClock.elapsedRealtime();

        // Wait for the battery update to be processed by job scheduler before proceeding.
        int curSeq;
        boolean curCharging;
        do {
            Thread.sleep(50);
            curSeq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd jobscheduler get-battery-seq").trim());
            // The job scheduler actually looks at the charging/discharging state,
            // which is currently determined by battery stats in response to the low-level
            // plugged/unplugged events.  So we can get this updated after the last seq
            // is received, so we need to make sure that has correctly changed.
            curCharging = Boolean.parseBoolean(SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd jobscheduler get-battery-charging").trim());
            if (curSeq == seq && curCharging == plugged) {
                return;
            }
        } while ((SystemClock.elapsedRealtime()-startTime) < 5000);

        fail("Timed out waiting for job scheduler: expected seq=" + seq + ", cur=" + curSeq
                + ", plugged=" + plugged + " curCharging=" + curCharging);
    }

    void verifyChargingState(boolean charging) throws Exception {
        boolean curCharging = Boolean.parseBoolean(SystemUtil.runShellCommand(getInstrumentation(),
                "cmd jobscheduler get-battery-charging").trim());
        assertEquals(charging, curCharging);
    }

    void verifyBatteryNotLowState(boolean notLow) throws Exception {
        boolean curNotLow = Boolean.parseBoolean(SystemUtil.runShellCommand(getInstrumentation(),
                "cmd jobscheduler get-battery-not-low").trim());
        assertEquals(notLow, curNotLow);
    }

    String getJobState() throws Exception {
        return getJobState(BATTERY_JOB_ID);
    }

    void assertJobReady() throws Exception {
        assertJobReady(BATTERY_JOB_ID);
    }

    void assertJobWaiting() throws Exception {
        assertJobWaiting(BATTERY_JOB_ID);
    }

    void assertJobNotReady() throws Exception {
        assertJobNotReady(BATTERY_JOB_ID);
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device is charging, when the battery reports it is
     * plugged in.
     */
    public void testChargingConstraintExecutes() throws Exception {
        setBatteryState(true, 100);
        verifyChargingState(true);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresCharging(true).build());
        assertJobReady();
        kTestEnvironment.readyToRun();

        assertTrue("Job with charging constraint did not fire on power.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires the device is not critical, when the battery reports it is
     * plugged in.
     */
    public void testBatteryNotLowConstraintExecutes_withPower() throws Exception {
        setBatteryState(true, 100);
        verifyChargingState(true);
        verifyBatteryNotLowState(true);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());
        assertJobReady();
        kTestEnvironment.readyToRun();

        assertTrue("Job with battery not low constraint did not fire on power.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires the device is not critical, when the battery reports it is
     * not plugged in but has sufficient power.
     */
    public void testBatteryNotLowConstraintExecutes_withoutPower() throws Exception {
        setBatteryState(false, 100);
        verifyChargingState(false);
        verifyBatteryNotLowState(true);

        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());
        assertJobReady();
        kTestEnvironment.readyToRun();

        assertTrue("Job with battery not low constraint did not fire on power.",
                kTestEnvironment.awaitExecution());
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device is charging, and assert if failed when
     * the device is not on power.
     */
    public void testChargingConstraintFails() throws Exception {
        setBatteryState(false, 100);
        verifyChargingState(false);

        kTestEnvironment.setExpectedExecutions(0);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresCharging(true).build());
        assertJobWaiting();
        assertJobNotReady();
        kTestEnvironment.readyToRun();

        assertFalse("Job with charging constraint fired while not on power.",
                kTestEnvironment.awaitExecution(250));
        assertJobWaiting();
        assertJobNotReady();

        // Ensure the job runs once the device is plugged in.
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        kTestEnvironment.setContinueAfterStart();
        setBatteryState(true, 100);
        verifyChargingState(true);
        kTestEnvironment.setExpectedStopped();
        assertJobReady();
        kTestEnvironment.readyToRun();
        assertTrue("Job with charging constraint did not fire on power.",
                kTestEnvironment.awaitExecution());

        // And check that the job is stopped if the device is unplugged while it is running.
        setBatteryState(false, 100);
        verifyChargingState(false);
        assertTrue("Job with charging constraint did not stop when power removed.",
                kTestEnvironment.awaitStopped());
    }

    /**
     * Schedule a job that requires the device is not critical, and assert it failed when
     * the battery level is critical and not on power.
     */
    public void testBatteryNotLowConstraintFails_withoutPower() throws Exception {
        setBatteryState(false, 15);
        verifyChargingState(false);
        verifyBatteryNotLowState(false);

        kTestEnvironment.setExpectedExecutions(0);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());
        assertJobWaiting();
        assertJobNotReady();
        kTestEnvironment.readyToRun();

        assertFalse("Job with battery not low constraint fired while level critical.",
                kTestEnvironment.awaitExecution(250));
        assertJobWaiting();
        assertJobNotReady();

        // Ensure the job runs once the device's battery level is not low.
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        kTestEnvironment.setContinueAfterStart();
        setBatteryState(false, 50);
        verifyChargingState(false);
        verifyBatteryNotLowState(true);
        kTestEnvironment.setExpectedStopped();
        assertJobReady();
        kTestEnvironment.readyToRun();
        assertTrue("Job with not low constraint did not fire when charge increased.",
                kTestEnvironment.awaitExecution());

        // And check that the job is stopped if battery goes low again.
        setBatteryState(false, 15);
        setBatteryState(false, 14);
        verifyChargingState(false);
        verifyBatteryNotLowState(false);
        assertTrue("Job with not low constraint did not stop when battery went low.",
                kTestEnvironment.awaitStopped());
    }
}
