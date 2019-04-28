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
 * limitations under the License.
 */

package com.android.systemmetrics.functional;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_DELAY_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_DEVICE_UPTIME_SECONDS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_PROCESS_RUNNING;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_REPORTED_DRAWN;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_REPORTED_DRAWN_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_STARTING_WINDOW_DELAY_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.APP_TRANSITION_WINDOWS_DRAWN_DELAY_MS;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.FIELD_CLASS_NAME;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.TYPE_TRANSITION_COLD_LAUNCH;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.TYPE_TRANSITION_REPORTED_DRAWN_NO_BUNDLE;
import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.TYPE_TRANSITION_WARM_LAUNCH;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.Intent;
import android.metrics.LogMaker;
import android.metrics.MetricsReader;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.metricshelper.MetricsAsserts;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.text.TextUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Queue;

/**
 * runtest --path platform_testing/tests/functional/systemmetrics/
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class AppStartTests {
    private static final String LOG_TAG = AppStartTests.class.getSimpleName();
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final int LONG_TIMEOUT_MS = 2000;
    private UiDevice mDevice = null;
    private Context mContext;
    private MetricsReader mMetricsReader;
    private int mPreUptime;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice.setOrientationNatural();
        mMetricsReader = new MetricsReader();
        mMetricsReader.checkpoint(); // clear out old logs
        mPreUptime = (int) (SystemClock.uptimeMillis() / 1000);
    }

    @After
    public void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        mContext.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        mDevice.waitForIdle();
    }

    @Test
    public void testStartApp() throws Exception {
        Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(SETTINGS_PACKAGE);

        // Clear out any previous instances
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);

        assertNotNull("component name is null", intent.getComponent());
        String className = intent.getComponent().getClassName();
        String packageName = intent.getComponent().getPackageName();
        assertTrue("className is empty", !TextUtils.isEmpty(className));
        assertTrue("packageName is empty", !TextUtils.isEmpty(packageName));


        mContext.startActivity(intent);
        mDevice.wait(Until.hasObject(By.pkg(SETTINGS_PACKAGE).depth(0)), LONG_TIMEOUT_MS);

        int postUptime = (int) (SystemClock.uptimeMillis() / 1000);

        Queue<LogMaker> startLogs = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(APP_TRANSITION));
        boolean found = false;
        for (LogMaker log : startLogs) {
            String actualClassName = (String) log.getTaggedData(
                    FIELD_CLASS_NAME);
            String actualPackageName = log.getPackageName();
            if (className.equals(actualClassName) && packageName.equals(actualPackageName)) {
                found = true;
                int startUptime = ((Number)
                        log.getTaggedData(APP_TRANSITION_DEVICE_UPTIME_SECONDS))
                        .intValue();
                assertTrue("must be either cold or warm launch",
                        TYPE_TRANSITION_COLD_LAUNCH == log.getType()
                                || TYPE_TRANSITION_WARM_LAUNCH == log.getType());
                assertTrue("reported uptime should be after the app was started",
                        mPreUptime <= startUptime);
                assertTrue("reported uptime should be before assertion time",
                        startUptime <= postUptime);
                assertNotNull("log should have delay",
                        log.getTaggedData(APP_TRANSITION_DELAY_MS));
                assertEquals("transition should be started because of starting window",
                        1 /* APP_TRANSITION_STARTING_WINDOW */, log.getSubtype());
                assertNotNull("log should have starting window delay",
                        log.getTaggedData(APP_TRANSITION_STARTING_WINDOW_DELAY_MS));
                assertNotNull("log should have windows drawn delay",
                        log.getTaggedData(APP_TRANSITION_WINDOWS_DRAWN_DELAY_MS));
            }
        }
        assertTrue("did not find the app start start log for: "
                + intent.getComponent().flattenToShortString(), found);
    }

    @Test
    public void testReportedDrawn() throws Exception {
        Intent intent = new Intent(mContext, ReportedDrawnActivity.class).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mDevice.wait(Until.hasObject(By.pkg(mContext.getPackageName()).depth(0)), LONG_TIMEOUT_MS);

        String className = intent.getComponent().getClassName();
        String packageName = intent.getComponent().getPackageName();

        // Sleep until activity under test has reported drawn (after 500ms)
        SystemClock.sleep(1000);
        Queue<LogMaker> startLogs = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(APP_TRANSITION_REPORTED_DRAWN));
        boolean found = false;
        for (LogMaker log : startLogs) {
            String actualClassName = (String) log.getTaggedData(
                    FIELD_CLASS_NAME);
            String actualPackageName = log.getPackageName();
            if (className.equals(actualClassName) && packageName.equals(actualPackageName)) {
                found = true;
                assertTrue((long) log.getTaggedData(APP_TRANSITION_REPORTED_DRAWN_MS) > 500L);
                assertEquals((int) log.getTaggedData(APP_TRANSITION_PROCESS_RUNNING), 1);
                assertEquals(TYPE_TRANSITION_REPORTED_DRAWN_NO_BUNDLE, log.getType());
            }
        }
        assertTrue("did not find the app start start log for: "
                    + intent.getComponent().flattenToShortString(), found);
    }
}
