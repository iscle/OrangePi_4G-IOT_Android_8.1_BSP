/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.app.cts;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Instrumentation.ActivityResult;
import android.app.PendingIntent;
import android.app.stubs.ActivityManagerRecentOneActivity;
import android.app.stubs.ActivityManagerRecentTwoActivity;
import android.app.stubs.MockApplicationActivity;
import android.app.stubs.MockService;
import android.app.stubs.ScreenOnActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ConfigurationInfo;
import android.content.res.Resources;
import android.platform.test.annotations.RestrictedBuildTest;
import android.test.InstrumentationTestCase;

import java.util.ArrayList;
import java.util.List;

public class ActivityManagerTest extends InstrumentationTestCase {
    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final int WAITFOR_MSEC = 5000;
    private static final String SERVICE_NAME = "android.app.stubs.MockService";
    private static final int WAIT_TIME = 2000;
    // A secondary test activity from another APK.
    static final String SIMPLE_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    static final String SIMPLE_ACTIVITY = ".SimpleActivity";
    static final String SIMPLE_ACTIVITY_IMMEDIATE_EXIT = ".SimpleActivityImmediateExit";
    static final String SIMPLE_ACTIVITY_CHAIN_EXIT = ".SimpleActivityChainExit";
    static final String SIMPLE_RECEIVER = ".SimpleReceiver";
    static final String SIMPLE_REMOTE_RECEIVER = ".SimpleRemoteReceiver";
    // The action sent back by the SIMPLE_APP after a restart.
    private static final String ACTIVITY_LAUNCHED_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";
    // The action sent back by the SIMPLE_APP_IMMEDIATE_EXIT when it terminates.
    private static final String ACTIVITY_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.EXIT_ACTION";
    // The action sent back by the SIMPLE_APP_CHAIN_EXIT when the task chain ends. 
    private static final String ACTIVITY_CHAIN_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.CHAIN_EXIT_ACTION";
    // The action sent to identify the time track info.
    private static final String ACTIVITY_TIME_TRACK_INFO = "com.android.cts.TIME_TRACK_INFO";
    // Return states of the ActivityReceiverFilter.
    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;
    public static final int RESULT_TIMEOUT = 3;

    private Context mContext;
    private ActivityManager mActivityManager;
    private Intent mIntent;
    private List<Activity> mStartedActivityList;
    private int mErrorProcessID;
    private Instrumentation mInstrumentation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mStartedActivityList = new ArrayList<Activity>();
        mErrorProcessID = -1;
        startSubActivity(ScreenOnActivity.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mIntent != null) {
            mInstrumentation.getContext().stopService(mIntent);
        }
        for (int i = 0; i < mStartedActivityList.size(); i++) {
            mStartedActivityList.get(i).finish();
        }
        if (mErrorProcessID != -1) {
            android.os.Process.killProcess(mErrorProcessID);
        }
    }

    public void testGetRecentTasks() throws Exception {
        int maxNum = 0;
        int flags = 0;

        List<RecentTaskInfo> recentTaskList;
        // Test parameter: maxNum is set to 0
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertNotNull(recentTaskList);
        assertTrue(recentTaskList.size() == 0);
        // Test parameter: maxNum is set to 50
        maxNum = 50;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertNotNull(recentTaskList);
        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);
        Thread.sleep(WAIT_TIME);
        // start recent2_activity
        startSubActivity(ActivityManagerRecentTwoActivity.class);
        Thread.sleep(WAIT_TIME);
        /*
         * assert both recent1_activity and recent2_activity exist in the recent
         * tasks list. Moreover,the index of the recent2_activity is smaller
         * than the index of recent1_activity
         */
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int indexRecentOne = -1;
        int indexRecentTwo = -1;
        int i = 0;
        for (RecentTaskInfo rti : recentTaskList) {
            if (rti.baseIntent.getComponent().getClassName().equals(
                    ActivityManagerRecentOneActivity.class.getName())) {
                indexRecentOne = i;
            } else if (rti.baseIntent.getComponent().getClassName().equals(
                    ActivityManagerRecentTwoActivity.class.getName())) {
                indexRecentTwo = i;
            }
            i++;
        }
        assertTrue(indexRecentOne != -1 && indexRecentTwo != -1);
        assertTrue(indexRecentTwo < indexRecentOne);

        try {
            mActivityManager.getRecentTasks(-1, 0);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception
        }
    }

    public void testGetRecentTasksLimitedToCurrentAPK() throws Exception {
        int maxNum = 0;
        int flags = 0;

        // Check the number of tasks at this time.
        List<RecentTaskInfo>  recentTaskList;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesFirstRun = recentTaskList.size();

        // Start another activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityReceiverFilter receiver = new ActivityReceiverFilter(ACTIVITY_LAUNCHED_ACTION);
        mContext.startActivity(intent);

        // Make sure the activity has really started.
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        receiver.close();

        // There shouldn't be any more tasks in this list at this time.
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesSecondRun = recentTaskList.size();
        assertTrue(numberOfEntriesSecondRun == numberOfEntriesFirstRun);

        // Tell the activity to finalize.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("finish", true);
        mContext.startActivity(intent);
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private String mActivityToFilter;
        private int result = RESULT_TIMEOUT;
        public long mTimeUsed = 0;
        private static final int TIMEOUT_IN_MS = 1000;

        // Create the filter with the intent to look for.
        public ActivityReceiverFilter(String activityToFilter) {
            mActivityToFilter = activityToFilter;
            IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mInstrumentation.getTargetContext().registerReceiver(this, filter);
        }

        // Turn off the filter.
        public void close() {
            mInstrumentation.getTargetContext().unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                synchronized(this) {
                   result = RESULT_PASS;
                   if (mActivityToFilter.equals(ACTIVITY_TIME_TRACK_INFO)) {
                       mTimeUsed = intent.getExtras().getLong(
                               ActivityOptions.EXTRA_USAGE_TIME_REPORT);
                   }
                   notifyAll();
                }
            }
        }

        public int waitForActivity() {
            synchronized(this) {
                try {
                    wait(TIMEOUT_IN_MS);
                } catch (InterruptedException e) {
                }
            }
            return result;
        }
    }

    private final <T extends Activity> void startSubActivity(Class<T> activityClass) {
        final Instrumentation.ActivityResult result = new ActivityResult(0, new Intent());
        final ActivityMonitor monitor = new ActivityMonitor(activityClass.getName(), result, false);
        mInstrumentation.addMonitor(monitor);
        launchActivity(STUB_PACKAGE_NAME, activityClass, null);
        mStartedActivityList.add(monitor.waitForActivity());
    }

    public void testGetRunningTasks() {
        // Test illegal parameter
        List<RunningTaskInfo> runningTaskList;
        runningTaskList = mActivityManager.getRunningTasks(-1);
        assertTrue(runningTaskList.size() == 0);

        runningTaskList = mActivityManager.getRunningTasks(0);
        assertTrue(runningTaskList.size() == 0);

        runningTaskList = mActivityManager.getRunningTasks(20);
        int taskSize = runningTaskList.size();
        assertTrue(taskSize >= 0 && taskSize <= 20);

        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);
        // start recent2_activity
        startSubActivity(ActivityManagerRecentTwoActivity.class);

        /*
         * assert both recent1_activity and recent2_activity exist in the
         * running tasks list. Moreover,the index of the recent2_activity is
         * smaller than the index of recent1_activity
         */
        runningTaskList = mActivityManager.getRunningTasks(20);
        int indexRecentOne = -1;
        int indexRecentTwo = -1;
        int i = 0;
        for (RunningTaskInfo rti : runningTaskList) {
            if (rti.baseActivity.getClassName().equals(
                    ActivityManagerRecentOneActivity.class.getName())) {
                indexRecentOne = i;
            } else if (rti.baseActivity.getClassName().equals(
                    ActivityManagerRecentTwoActivity.class.getName())) {
                indexRecentTwo = i;
            }
            i++;
        }
        assertTrue(indexRecentOne != -1 && indexRecentTwo != -1);
        assertTrue(indexRecentTwo < indexRecentOne);
    }

    public void testGetRunningServices() throws Exception {
        // Test illegal parameter
        List<RunningServiceInfo> runningServiceInfo;
        runningServiceInfo = mActivityManager.getRunningServices(-1);
        assertTrue(runningServiceInfo.isEmpty());

        runningServiceInfo = mActivityManager.getRunningServices(0);
        assertTrue(runningServiceInfo.isEmpty());

        runningServiceInfo = mActivityManager.getRunningServices(5);
        assertTrue(runningServiceInfo.size() <= 5);

        Intent intent = new Intent();
        intent.setClass(mInstrumentation.getTargetContext(), MockService.class);
        intent.putExtra(MockService.EXTRA_NO_STOP, true);
        mInstrumentation.getTargetContext().startService(intent);
        MockService.waitForStart(WAIT_TIME);

        runningServiceInfo = mActivityManager.getRunningServices(Integer.MAX_VALUE);
        boolean foundService = false;
        for (RunningServiceInfo rs : runningServiceInfo) {
            if (rs.service.getClassName().equals(SERVICE_NAME)) {
                foundService = true;
                break;
            }
        }
        assertTrue(foundService);
        MockService.prepareDestroy();
        mContext.stopService(intent);
        boolean destroyed = MockService.waitForDestroy(WAIT_TIME);
        assertTrue(destroyed);
    }

    public void testGetMemoryInfo() {
        ActivityManager.MemoryInfo outInfo = new ActivityManager.MemoryInfo();
        mActivityManager.getMemoryInfo(outInfo);
        assertTrue(outInfo.lowMemory == (outInfo.availMem <= outInfo.threshold));
    }

    public void testGetRunningAppProcesses() throws Exception {
        List<RunningAppProcessInfo> list = mActivityManager.getRunningAppProcesses();
        assertNotNull(list);
        final String SYSTEM_PROCESS = "system";
        boolean hasSystemProcess = false;
        // The package name is also the default name for the application process
        final String TEST_PROCESS = STUB_PACKAGE_NAME;
        boolean hasTestProcess = false;
        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals(SYSTEM_PROCESS)) {
                hasSystemProcess = true;

                // Make sure the importance is a sane value.
                assertTrue(ra.importance >= RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
                assertTrue(ra.importance < RunningAppProcessInfo.IMPORTANCE_GONE);
            } else if (ra.processName.equals(TEST_PROCESS)) {
                hasTestProcess = true;
            }
        }

        // For security reasons the system process is not exposed.
        assertFalse(hasSystemProcess);
        assertTrue(hasTestProcess);

        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                fail("should be no process named android.app.stubs:remote");
            }
        }
        // start a new process
        // XXX would be a lot cleaner to bind instead of start.
        mIntent = new Intent("android.app.REMOTESERVICE");
        mIntent.setPackage("android.app.stubs");
        mInstrumentation.getTargetContext().startService(mIntent);
        Thread.sleep(WAITFOR_MSEC);

        List<RunningAppProcessInfo> listNew = mActivityManager.getRunningAppProcesses();
        mInstrumentation.getTargetContext().stopService(mIntent);

        for (RunningAppProcessInfo ra : listNew) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                return;
            }
        }
        fail("android.app.stubs:remote process should be available");
    }

    public void testGetMyMemoryState() {
        final RunningAppProcessInfo ra = new RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(ra);

        assertEquals(mContext.getApplicationInfo().processName, ra.processName);
        assertEquals(android.os.Process.myUid(), ra.uid);

        // When an instrumentation test is running, the importance is high.
        assertEquals(RunningAppProcessInfo.IMPORTANCE_FOREGROUND, ra.importance);
    }

    public void testGetProcessInErrorState() throws Exception {
        List<ActivityManager.ProcessErrorStateInfo> errList = null;
        errList = mActivityManager.getProcessesInErrorState();
    }

    public void testGetDeviceConfigurationInfo() {
        ConfigurationInfo conInf = mActivityManager.getDeviceConfigurationInfo();
        assertNotNull(conInf);
    }

    /**
     * Simple test for {@link ActivityManager#isUserAMonkey()} - verifies its false.
     *
     * TODO: test positive case
     */
    public void testIsUserAMonkey() {
        assertFalse(ActivityManager.isUserAMonkey());
    }

    /**
     * Verify that {@link ActivityManager#isRunningInTestHarness()} is false.
     */
    @RestrictedBuildTest
    public void testIsRunningInTestHarness() {
        assertFalse("isRunningInTestHarness must be false in production builds",
                ActivityManager.isRunningInTestHarness());
    }

    /**
     * Go back to the home screen since running applications can interfere with application
     * lifetime tests.
     */
    private void launchHome() throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        Thread.sleep(WAIT_TIME);
    }

   /**
    * Gets the value of com.android.internal.R.bool.config_noHomeScreen.
    * @return true if no home screen is supported, false otherwise.
    */
   private boolean noHomeScreen() {
       try {
           return getInstrumentation().getContext().getResources().getBoolean(
                   Resources.getSystem().getIdentifier("config_noHomeScreen", "bool", "android"));
       } catch (Resources.NotFoundException e) {
           // Assume there's a home screen.
           return false;
       }
    }

    /**
     * Verify that the TimeTrackingAPI works properly when starting and ending an activity.
     */
    public void testTimeTrackingAPI_SimpleStartExit() throws Exception {
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_IMMEDIATE_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO);
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context,
                0, receiveIntent, PendingIntent.FLAG_CANCEL_CURRENT));

        // The application finished tracker.
        ActivityReceiverFilter appEndReceiver = new ActivityReceiverFilter(ACTIVITY_EXIT_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appEndReceiver.waitForActivity());
        appEndReceiver.close();

        if (!noHomeScreen()) {
            // At this time the timerReceiver should not fire, even though the activity has shut
            // down, because we are back to the home screen. Going to the home screen does not
            // qualify as the user leaving the activity's flow. The time tracking is considered
            // complete only when the user switches to another activity that is not part of the
            // tracked flow.
            assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
            assertTrue(timeReceiver.mTimeUsed == 0);
        } else {
            // With platforms that have no home screen, focus is returned to something else that is
            // considered a completion of the tracked activity flow, and hence time tracking is
            // triggered.
            assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        }

        // Issuing now another activity will trigger the timing information release.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }

    /**
     * Verify that the TimeTrackingAPI works properly when switching away from the monitored task.
     */
    public void testTimeTrackingAPI_SwitchAwayTriggers() throws Exception {
        launchHome();

        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO);
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context,
                0, receiveIntent, PendingIntent.FLAG_CANCEL_CURRENT));

        // The application started tracker.
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appStartedReceiver.waitForActivity());
        appStartedReceiver.close();

        // At this time the timerReceiver should not fire since our app is running.
        assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
        assertTrue(timeReceiver.mTimeUsed == 0);

        // Starting now another activity will put ours into the back hence releasing the timing.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }

    /**
     * Verify that the TimeTrackingAPI works properly when handling an activity chain gets started
     * and ended.
     */
    public void testTimeTrackingAPI_ChainedActivityExit() throws Exception {
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_CHAIN_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO);
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context,
                0, receiveIntent, PendingIntent.FLAG_CANCEL_CURRENT));

        // The application finished tracker.
        ActivityReceiverFilter appEndReceiver = new ActivityReceiverFilter(
                ACTIVITY_CHAIN_EXIT_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appEndReceiver.waitForActivity());
        appEndReceiver.close();

        if (!noHomeScreen()) {
            // At this time the timerReceiver should not fire, even though the activity has shut
            // down, because we are back to the home screen. Going to the home screen does not
            // qualify as the user leaving the activity's flow. The time tracking is considered
            // complete only when the user switches to another activity that is not part of the
            // tracked flow.
            assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
            assertTrue(timeReceiver.mTimeUsed == 0);
        } else {
            // With platforms that have no home screen, focus is returned to something else that is
            // considered a completion of the tracked activity flow, and hence time tracking is
            // triggered.
            assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        }

        // Issue another activity so that the timing information gets released.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }
}
