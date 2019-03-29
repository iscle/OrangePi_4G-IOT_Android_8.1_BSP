/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package android.app.usage.cts;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.util.SparseLongArray;

import junit.framework.AssertionFailedError;

import org.junit.Ignore;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Test the UsageStats API. It is difficult to test the entire surface area
 * of the API, as a lot of the testing depends on what data is already present
 * on the device and for how long that data has been aggregating.
 *
 * These tests perform simple checks that each interval is of the correct duration,
 * and that events do appear in the event log.
 *
 * Tests to add that are difficult to add now:
 * - Invoking a device configuration change and then watching for it in the event log.
 * - Changing the system time and verifying that all data has been correctly shifted
 *   along with the new time.
 * - Proper eviction of old data.
 */
public class UsageStatsTest extends InstrumentationTestCase {
    private static final String APPOPS_SET_SHELL_COMMAND = "appops set {0} " +
            AppOpsManager.OPSTR_GET_USAGE_STATS + " {1}";

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;
    private static final long TIME_DIFF_THRESHOLD = 200;

    private UiDevice mUiDevice;
    private UsageStatsManager mUsageStatsManager;
    private String mTargetPackage;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUiDevice = UiDevice.getInstance(getInstrumentation());
        mUsageStatsManager = (UsageStatsManager) getInstrumentation().getContext()
                .getSystemService(Context.USAGE_STATS_SERVICE);
        mTargetPackage = getInstrumentation().getContext().getPackageName();

        setAppOpsMode("allow");
    }

    private static void assertLessThan(long left, long right) {
        if (left >= right) {
            throw new AssertionFailedError("Expected " + left + " to be less than " + right);
        }
    }

    private static void assertLessThanOrEqual(long left, long right) {
        if (left > right) {
            throw new AssertionFailedError("Expected " + left + " to be less than or equal to "
                    + right);
        }
    }

    private void setAppOpsMode(String mode) throws Exception {
        final String command = MessageFormat.format(APPOPS_SET_SHELL_COMMAND,
                getInstrumentation().getContext().getPackageName(), mode);
        mUiDevice.executeShellCommand(command);
    }

    private void launchSubActivity(Class<? extends Activity> clazz) {
        final Context context = getInstrumentation().getContext();
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mTargetPackage, clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.clazz(clazz)), TIMEOUT);
    }

    private void launchSubActivities(Class<? extends Activity>[] activityClasses) {
        for (Class<? extends Activity> clazz : activityClasses) {
            launchSubActivity(clazz);
        }
    }

    public void testOrderedActivityLaunchSequenceInEventLog() throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends Activity>[] activitySequence = new Class[] {
                Activities.ActivityOne.class,
                Activities.ActivityTwo.class,
                Activities.ActivityThree.class,
        };

        final long startTime = System.currentTimeMillis() - MINUTE;

        // Launch the series of Activities.
        launchSubActivities(activitySequence);

        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        // Consume all the events.
        ArrayList<UsageEvents.Event> eventList = new ArrayList<>();
        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            eventList.add(event);
        }

        // Find the last Activity's MOVE_TO_FOREGROUND event.
        int end = eventList.size();
        while (end > 0) {
            UsageEvents.Event event = eventList.get(end - 1);
            if (event.getClassName().equals(activitySequence[activitySequence.length - 1].getName())
                    && event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                break;
            }
            end--;
        }

        // We expect 2 events per Activity launched (foreground + background)
        // except for the last Activity, which was in the foreground when
        // we queried the event log.
        final int start = end - ((activitySequence.length * 2) - 1);
        assertTrue("Not enough events", start >= 0);

        final int activityCount = activitySequence.length;
        for (int i = 0; i < activityCount; i++) {
            final int index = start + (i * 2);

            // Check for foreground event.
            UsageEvents.Event event = eventList.get(index);
            assertEquals(mTargetPackage, event.getPackageName());
            assertEquals(activitySequence[i].getName(), event.getClassName());
            assertEquals(UsageEvents.Event.MOVE_TO_FOREGROUND, event.getEventType());

            // Only check for the background event if this is not the
            // last activity.
            if (i < activityCount - 1) {
                event = eventList.get(index + 1);
                assertEquals(mTargetPackage, event.getPackageName());
                assertEquals(activitySequence[i].getName(), event.getClassName());
                assertEquals(UsageEvents.Event.MOVE_TO_BACKGROUND, event.getEventType());
            }
        }
    }

    /**
     * We can't run this test because we are unable to change the system time.
     * It would be nice to add a shell command or other to allow the shell user
     * to set the time, thereby allowing this test to set the time using the UIAutomator.
     */
    @Ignore
    public void ignore_testStatsAreShiftedInTimeWhenSystemTimeChanges() throws Exception {
        launchSubActivity(Activities.ActivityOne.class);
        launchSubActivity(Activities.ActivityThree.class);

        long endTime = System.currentTimeMillis();
        long startTime = endTime - MINUTE;
        Map<String, UsageStats> statsMap = mUsageStatsManager.queryAndAggregateUsageStats(startTime,
                endTime);
        assertFalse(statsMap.isEmpty());
        assertTrue(statsMap.containsKey(mTargetPackage));
        final UsageStats before = statsMap.get(mTargetPackage);

        SystemClock.setCurrentTimeMillis(System.currentTimeMillis() - (DAY / 2));
        try {
            endTime = System.currentTimeMillis();
            startTime = endTime - MINUTE;
            statsMap = mUsageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
            assertFalse(statsMap.isEmpty());
            assertTrue(statsMap.containsKey(mTargetPackage));
            final UsageStats after = statsMap.get(mTargetPackage);
            assertEquals(before.getPackageName(), after.getPackageName());

            long diff = before.getFirstTimeStamp() - after.getFirstTimeStamp();
            assertLessThan(Math.abs(diff - (DAY / 2)), TIME_DIFF_THRESHOLD);

            assertEquals(before.getLastTimeStamp() - before.getFirstTimeStamp(),
                    after.getLastTimeStamp() - after.getFirstTimeStamp());
            assertEquals(before.getLastTimeUsed() - before.getFirstTimeStamp(),
                    after.getLastTimeUsed() - after.getFirstTimeStamp());
            assertEquals(before.getTotalTimeInForeground(), after.getTotalTimeInForeground());
        } finally {
            SystemClock.setCurrentTimeMillis(System.currentTimeMillis() + (DAY / 2));
        }
    }

    public void testUsageEventsParceling() throws Exception {
        final long startTime = System.currentTimeMillis() - MINUTE;

        // Ensure some data is in the UsageStats log.
        @SuppressWarnings("unchecked")
        Class<? extends Activity>[] activityClasses = new Class[] {
                Activities.ActivityTwo.class,
                Activities.ActivityOne.class,
                Activities.ActivityThree.class,
        };
        launchSubActivities(activityClasses);

        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);
        assertTrue(events.getNextEvent(new UsageEvents.Event()));

        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        events.writeToParcel(p, 0);
        p.setDataPosition(0);

        UsageEvents reparceledEvents = UsageEvents.CREATOR.createFromParcel(p);

        UsageEvents.Event e1 = new UsageEvents.Event();
        UsageEvents.Event e2 = new UsageEvents.Event();
        while (events.hasNextEvent() && reparceledEvents.hasNextEvent()) {
            events.getNextEvent(e1);
            reparceledEvents.getNextEvent(e2);
            assertEquals(e1.getPackageName(), e2.getPackageName());
            assertEquals(e1.getClassName(), e2.getClassName());
            assertEquals(e1.getConfiguration(), e2.getConfiguration());
            assertEquals(e1.getEventType(), e2.getEventType());
            assertEquals(e1.getTimeStamp(), e2.getTimeStamp());
        }

        assertEquals(events.hasNextEvent(), reparceledEvents.hasNextEvent());
    }

    public void testPackageUsageStatsIntervals() throws Exception {
        final long beforeTime = System.currentTimeMillis();

        // Launch an Activity.
        launchSubActivity(Activities.ActivityFour.class);
        launchSubActivity(Activities.ActivityThree.class);

        final long endTime = System.currentTimeMillis();

        final SparseLongArray intervalLengths = new SparseLongArray();
        intervalLengths.put(UsageStatsManager.INTERVAL_DAILY, DAY);
        intervalLengths.put(UsageStatsManager.INTERVAL_WEEKLY, WEEK);
        intervalLengths.put(UsageStatsManager.INTERVAL_MONTHLY, MONTH);
        intervalLengths.put(UsageStatsManager.INTERVAL_YEARLY, YEAR);

        final int intervalCount = intervalLengths.size();
        for (int i = 0; i < intervalCount; i++) {
            final int intervalType = intervalLengths.keyAt(i);
            final long intervalDuration = intervalLengths.valueAt(i);
            final long startTime = endTime - (2 * intervalDuration);
            final List<UsageStats> statsList = mUsageStatsManager.queryUsageStats(intervalType,
                    startTime, endTime);
            assertFalse(statsList.isEmpty());

            boolean foundPackage = false;
            for (UsageStats stats : statsList) {
                // Verify that each period is a day long.
                assertLessThanOrEqual(stats.getLastTimeStamp() - stats.getFirstTimeStamp(),
                        intervalDuration);
                if (stats.getPackageName().equals(mTargetPackage) &&
                        stats.getLastTimeUsed() >= beforeTime - TIME_DIFF_THRESHOLD) {
                    foundPackage = true;
                }
            }

            assertTrue("Did not find package " + mTargetPackage + " in interval " + intervalType,
                    foundPackage);
        }
    }

    public void testNoAccessSilentlyFails() throws Exception {
        final long startTime = System.currentTimeMillis() - MINUTE;

        launchSubActivity(Activities.ActivityOne.class);
        launchSubActivity(Activities.ActivityThree.class);

        final long endTime = System.currentTimeMillis();
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                startTime, endTime);
        assertFalse(stats.isEmpty());

        // We set the mode to ignore because our package has the PACKAGE_USAGE_STATS permission,
        // and default would allow in this case.
        setAppOpsMode("ignore");

        stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                startTime, endTime);
        assertTrue(stats.isEmpty());
    }
}
