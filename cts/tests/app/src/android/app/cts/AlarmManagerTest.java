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

import com.android.compatibility.common.util.ApiLevelUtil;

import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.PendingIntent;
import android.app.stubs.MockAlarmReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.util.Log;

import com.android.compatibility.common.util.PollingCheck;

public class AlarmManagerTest extends AndroidTestCase {
    public static final String MOCKACTION = "android.app.AlarmManagerTest.TEST_ALARMRECEIVER";
    public static final String MOCKACTION2 = "android.app.AlarmManagerTest.TEST_ALARMRECEIVER2";

    private AlarmManager mAm;
    private Intent mIntent;
    private PendingIntent mSender;
    private Intent mIntent2;
    private PendingIntent mSender2;

    /*
     *  The default snooze delay: 5 seconds
     */
    private static final long SNOOZE_DELAY = 5 * 1000L;
    private long mWakeupTime;
    private MockAlarmReceiver mMockAlarmReceiver;
    private MockAlarmReceiver mMockAlarmReceiver2;

    private static final int TIME_DELTA = 1000;
    private static final int TIME_DELAY = 10000;
    private static final int REPEAT_PERIOD = 60000;

    // Receiver registration/unregistration between tests races with the system process, so
    // we add a little buffer time here to allow the system to process before we proceed.
    // This value is in milliseconds.
    private static final long REGISTER_PAUSE = 250;

    // Constants used for validating exact vs inexact alarm batching immunity.  We run a few
    // trials of an exact alarm that is placed within an inexact alarm's window of opportunity,
    // and mandate that the average observed delivery skew between the two be statistically
    // significant -- i.e. that the two alarms are not being coalesced.  We also place an
    // additional exact alarm only a short time after the inexact alarm's nominal trigger time.
    // If exact alarms are allowed to batch with inexact ones this will tend to have no effect,
    // but in the correct behavior -- inexact alarms not permitted to batch with exact ones --
    // this additional exact alarm will have the effect of guaranteeing that the inexact alarm
    // must fire no later than it -- i.e. a considerable time before the significant, later
    // exact alarm.
    //
    // The test essentially amounts to requiring that the inexact MOCKACTION alarm and
    // the much later exact MOCKACTION2 alarm fire far apart, always; with an implicit
    // insistence that alarm batches are delivered at the head of their window.
    private static final long TEST_WINDOW_LENGTH = 5 * 1000L;
    private static final long TEST_ALARM_FUTURITY = 6 * 1000L;
    private static final long FAIL_DELTA = 50;
    private static final long NUM_TRIALS = 5;
    private static final long MAX_NEAR_DELIVERIES = 2;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mAm = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        mIntent = new Intent(MOCKACTION)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mSender = PendingIntent.getBroadcast(mContext, 0, mIntent, 0);
        mMockAlarmReceiver = new MockAlarmReceiver(mIntent.getAction());

        mIntent2 = new Intent(MOCKACTION2)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mSender2 = PendingIntent.getBroadcast(mContext, 0, mIntent2, 0);
        mMockAlarmReceiver2 = new MockAlarmReceiver(mIntent2.getAction());

        IntentFilter filter = new IntentFilter(mIntent.getAction());
        mContext.registerReceiver(mMockAlarmReceiver, filter);

        IntentFilter filter2 = new IntentFilter(mIntent2.getAction());
        mContext.registerReceiver(mMockAlarmReceiver2, filter2);

        Thread.sleep(REGISTER_PAUSE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mContext.unregisterReceiver(mMockAlarmReceiver);
        mContext.unregisterReceiver(mMockAlarmReceiver2);

        Thread.sleep(REGISTER_PAUSE);
    }

    public void testSetTypes() throws Exception {
        // TODO: try to find a way to make device sleep then test whether
        // AlarmManager perform the expected way

        // test parameter type is RTC_WAKEUP
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = System.currentTimeMillis() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.RTC_WAKEUP, mWakeupTime, mSender);
        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertEquals(mMockAlarmReceiver.rtcTime, mWakeupTime, TIME_DELTA);

        // test parameter type is RTC
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = System.currentTimeMillis() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.RTC, mWakeupTime, mSender);
        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertEquals(mMockAlarmReceiver.rtcTime, mWakeupTime, TIME_DELTA);

        // test parameter type is ELAPSED_REALTIME
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = SystemClock.elapsedRealtime() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.ELAPSED_REALTIME, mWakeupTime, mSender);
        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertEquals(mMockAlarmReceiver.elapsedTime, mWakeupTime, TIME_DELTA);

        // test parameter type is ELAPSED_REALTIME_WAKEUP
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = SystemClock.elapsedRealtime() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, mWakeupTime, mSender);
        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertEquals(mMockAlarmReceiver.elapsedTime, mWakeupTime, TIME_DELTA);
    }

    public void testAlarmTriggersImmediatelyIfSetTimeIsNegative() throws Exception {
        // An alarm with a negative wakeup time should be triggered immediately.
        // This exercises a workaround for a limitation of the /dev/alarm driver
        // that would instead cause such alarms to never be triggered.
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = -1000;
        mAm.set(AlarmManager.RTC, mWakeupTime, mSender);
        new PollingCheck(TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
    }

    public void testExactAlarmBatching() throws Exception {
        int deliveriesTogether = 0;
        for (int i = 0; i < NUM_TRIALS; i++) {
            final long now = System.currentTimeMillis();
            final long windowStart = now + TEST_ALARM_FUTURITY;
            final long exactStart = windowStart + TEST_WINDOW_LENGTH - 1;

            mMockAlarmReceiver.setAlarmedFalse();
            mMockAlarmReceiver2.setAlarmedFalse();
            mAm.setWindow(AlarmManager.RTC_WAKEUP, windowStart, TEST_WINDOW_LENGTH, mSender);
            mAm.setExact(AlarmManager.RTC_WAKEUP, exactStart, mSender2);

            // Wait until a half-second beyond its target window, just to provide a
            // little safety slop.
            new PollingCheck(TEST_WINDOW_LENGTH + (windowStart - now) + 500) {
                @Override
                protected boolean check() {
                    return mMockAlarmReceiver.alarmed;
                }
            }.run();

            // Now wait until 1 sec beyond the expected exact alarm fire time, or for at
            // least one second if we're already past the nominal exact alarm fire time
            long timeToExact = Math.max(exactStart - System.currentTimeMillis() + 1000, 1000);
            new PollingCheck(timeToExact) {
                @Override
                protected boolean check() {
                    return mMockAlarmReceiver2.alarmed;
                }
            }.run();

            // Success when we observe that the exact and windowed alarm are not being often
            // delivered close together -- that is, when we can be confident that they are not
            // being coalesced.
            final long delta = Math.abs(mMockAlarmReceiver2.rtcTime - mMockAlarmReceiver.rtcTime);
            Log.i("TEST", "[" + i + "]  delta = " + delta);
            if (delta < FAIL_DELTA) {
                deliveriesTogether++;
                assertTrue("Exact alarms appear to be coalescing with inexact alarms",
                        deliveriesTogether <= MAX_NEAR_DELIVERIES);
            }
        }
    }

    public void testSetRepeating() throws Exception {
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = System.currentTimeMillis() + TEST_ALARM_FUTURITY;
        mAm.setRepeating(AlarmManager.RTC_WAKEUP, mWakeupTime, REPEAT_PERIOD, mSender);

        // wait beyond the initial alarm's possible delivery window to verify that it fires the first time
        new PollingCheck(TEST_ALARM_FUTURITY + REPEAT_PERIOD) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertTrue(mMockAlarmReceiver.alarmed);

        // Now reset the receiver and wait for the intended repeat alarm to fire as expected
        mMockAlarmReceiver.setAlarmedFalse();
        new PollingCheck(REPEAT_PERIOD*2) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertTrue(mMockAlarmReceiver.alarmed);

        mAm.cancel(mSender);
    }

    public void testCancel() throws Exception {
        mMockAlarmReceiver.setAlarmedFalse();
        mMockAlarmReceiver2.setAlarmedFalse();

        // set two alarms
        final long when1 = System.currentTimeMillis() + TEST_ALARM_FUTURITY;
        mAm.setExact(AlarmManager.RTC_WAKEUP, when1, mSender);
        final long when2 = when1 + TIME_DELTA; // will fire after when1's target time
        mAm.setExact(AlarmManager.RTC_WAKEUP, when2, mSender2);

        // cancel the earlier one
        mAm.cancel(mSender);

        // and verify that only the later one fired
        new PollingCheck(TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver2.alarmed;
            }
        }.run();

        assertFalse(mMockAlarmReceiver.alarmed);
        assertTrue(mMockAlarmReceiver2.alarmed);
    }

    public void testSetInexactRepeating() throws Exception {
        mAm.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, mSender);
        SystemClock.setCurrentTimeMillis(System.currentTimeMillis()
                + AlarmManager.INTERVAL_FIFTEEN_MINUTES);
        // currently there is no way to write Android system clock. When try to
        // write the system time, there will be log as
        // " Unable to open alarm driver: Permission denied". But still fail
        // after tried many permission.
    }

    public void testSetAlarmClock() throws Exception {
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.LOLLIPOP)) {
            mMockAlarmReceiver.setAlarmedFalse();
            mMockAlarmReceiver2.setAlarmedFalse();

            // Set first alarm clock.
            final long wakeupTimeFirst = System.currentTimeMillis()
                    + 2 * TEST_ALARM_FUTURITY;
            mAm.setAlarmClock(new AlarmClockInfo(wakeupTimeFirst, null), mSender);

            // Verify getNextAlarmClock returns first alarm clock.
            AlarmClockInfo nextAlarmClock = mAm.getNextAlarmClock();
            assertEquals(wakeupTimeFirst, nextAlarmClock.getTriggerTime());
            assertNull(nextAlarmClock.getShowIntent());

            // Set second alarm clock, earlier than first.
            final long wakeupTimeSecond = System.currentTimeMillis()
                    + TEST_ALARM_FUTURITY;
            PendingIntent showIntentSecond = PendingIntent.getBroadcast(getContext(), 0,
                    new Intent(getContext(), AlarmManagerTest.class).setAction("SHOW_INTENT"), 0);
            mAm.setAlarmClock(new AlarmClockInfo(wakeupTimeSecond, showIntentSecond),
                    mSender2);

            // Verify getNextAlarmClock returns second alarm clock now.
            nextAlarmClock = mAm.getNextAlarmClock();
            assertEquals(wakeupTimeSecond, nextAlarmClock.getTriggerTime());
            assertEquals(showIntentSecond, nextAlarmClock.getShowIntent());

            // Cancel second alarm.
            mAm.cancel(mSender2);

            // Verify getNextAlarmClock returns first alarm clock again.
            nextAlarmClock = mAm.getNextAlarmClock();
            assertEquals(wakeupTimeFirst, nextAlarmClock.getTriggerTime());
            assertNull(nextAlarmClock.getShowIntent());

            // Wait for first alarm to trigger.
            assertFalse(mMockAlarmReceiver.alarmed);
            new PollingCheck(2 * TEST_ALARM_FUTURITY + TIME_DELAY) {
                @Override
                protected boolean check() {
                    return mMockAlarmReceiver.alarmed;
                }
            }.run();

            // Verify first alarm fired at the right time.
            assertEquals(mMockAlarmReceiver.rtcTime, wakeupTimeFirst, TIME_DELTA);

            // Verify second alarm didn't fire.
            assertFalse(mMockAlarmReceiver2.alarmed);

            // Verify the next alarm is not returning neither the first nor the second alarm.
            nextAlarmClock = mAm.getNextAlarmClock();
            MoreAsserts.assertNotEqual(wakeupTimeFirst, nextAlarmClock != null
                    ? nextAlarmClock.getTriggerTime()
                    : null);
            MoreAsserts.assertNotEqual(wakeupTimeSecond, nextAlarmClock != null
                    ? nextAlarmClock.getTriggerTime()
                    : null);
        }
    }
}
