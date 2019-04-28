/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.platform.systemui.tests.jank;

import android.app.Notification.Action;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.timeresulthelper.TimeResultLogger;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.system.helpers.LockscreenHelper;
import android.system.helpers.OverviewHelper;
import android.widget.Button;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SystemUiJankTests extends JankTestBase {

    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final BySelector RECENTS = By.res(SYSTEMUI_PACKAGE, "recents_view");
    private static final String LOG_TAG = SystemUiJankTests.class.getSimpleName();
    private static final int SWIPE_MARGIN = 5;
    private static final int DEFAULT_FLING_STEPS = 5;
    private static final int DEFAULT_SCROLL_STEPS = 15;
    private static final int BRIGHTNESS_SCROLL_STEPS = 30;

    // short transitions should be repeated within the test function, otherwise frame stats
    // captured are not really meaningful in a statistical sense
    private static final int INNER_LOOP = 3;
    private static final int[] ICONS = new int[] {
            android.R.drawable.stat_notify_call_mute,
            android.R.drawable.stat_notify_chat,
            android.R.drawable.stat_notify_error,
            android.R.drawable.stat_notify_missed_call,
            android.R.drawable.stat_notify_more,
            android.R.drawable.stat_notify_sdcard,
            android.R.drawable.stat_notify_sdcard_prepare,
            android.R.drawable.stat_notify_sdcard_usb,
            android.R.drawable.stat_notify_sync,
            android.R.drawable.stat_notify_sync_noanim,
            android.R.drawable.stat_notify_voicemail,
    };
    private static final String NOTIFICATION_TEXT = "Lorem ipsum dolor sit amet";
    private static final String REPLY_TEXT = "REPLY";
    private static final File TIMESTAMP_FILE = new File(Environment.getExternalStorageDirectory()
            .getAbsolutePath(), "autotester.log");
    private static final File RESULTS_FILE = new File(Environment.getExternalStorageDirectory()
            .getAbsolutePath(), "results.log");
    private static final String GMAIL_PACKAGE_NAME = "com.google.android.gm";
    private static final String DISABLE_COMMAND = "pm disable-user ";
    private static final String ENABLE_COMMAND = "pm enable ";
    private static final String PULSE_COMMAND = "am broadcast -a com.android.systemui.doze.pulse";
    private static final String PIN = "1234";

    /**
     * Group mode: Let the system auto-group our notifications. This is required so we don't screw
     * up jank numbers for our existing notification list pull test.
     */
    private static final int GROUP_MODE_LEGACY = 0;

    /**
     * Group mode: Group the notifications.
     */
    private static final int GROUP_MODE_GROUPED = 1;

    /**
     * Group mode: All notifications should be separate
     */
    private static final int GROUP_MODE_UNGROUPED = 2;

    private UiDevice mDevice;
    private ArrayList<String> mLaunchedPackages;
    private NotificationManager mNotificationManager;

    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("failed to freeze device orientaion", e);
        }
        mNotificationManager = getInstrumentation().getContext().getSystemService(
                NotificationManager.class);
        InstrumentationRegistry.registerInstance(getInstrumentation(), getArguments());
        blockNotifications();
    }

    public void goHome() {
        mDevice.pressHome();
        mDevice.waitForIdle();
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        unblockNotifications();
        super.tearDown();
    }

    public void populateRecentApps() throws IOException {
        mLaunchedPackages = OverviewHelper.getInstance().populateManyRecentApps();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void forceStopPackages(Bundle metrics) throws IOException {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        OverviewHelper.getInstance().forceStopPackages(mLaunchedPackages);
        goHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    public void resetRecentsToBottom() {
        // Rather than trying to scroll back to the bottom, just re-open the recents list
        mDevice.pressHome();
        mDevice.waitForIdle();
        try {
            mDevice.pressRecentApps();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        // use a long timeout to wait until recents populated
        mDevice.wait(Until.findObject(RECENTS), 10000);
        mDevice.waitForIdle();
    }

    public void prepareNotifications(int groupMode) throws Exception {
        goHome();
        mDevice.openNotification();
        SystemClock.sleep(100);
        mDevice.waitForIdle();

        // CLEAR ALL might not be visible in case we don't have any clearable notifications.
        UiObject clearAll =
                mDevice.findObject(new UiSelector().className(Button.class).text("CLEAR ALL"));
        if (clearAll.exists()) {
            clearAll.click();
        }
        mDevice.pressHome();
        mDevice.waitForIdle();
        postNotifications(groupMode);
        mDevice.waitForIdle();
    }

    private void postNotifications(int groupMode) {
        postNotifications(groupMode, 100, -1);
    }

    private void postNotifications(int groupMode, int sleepBetweenDuration, int maxCount) {
        Builder builder = new Builder(getInstrumentation().getTargetContext())
                .setContentTitle(NOTIFICATION_TEXT);
        if (groupMode == GROUP_MODE_GROUPED) {
            builder.setGroup("key");
        }
        boolean first = true;
        for (int i = 0; i < ICONS.length; i++) {
            if (maxCount != -1 && i >= maxCount) {
                break;
            }
            int icon = ICONS[i];
            if (first && groupMode == GROUP_MODE_GROUPED) {
                builder.setGroupSummary(true);
            } else {
                builder.setGroupSummary(false);
            }
            if (groupMode == GROUP_MODE_UNGROUPED) {
                builder.setGroup(Integer.toString(icon));
            }
            builder.setContentText(Integer.toHexString(icon))
                    .setSmallIcon(icon);
            mNotificationManager.notify(icon, builder.build());
            SystemClock.sleep(sleepBetweenDuration);
            first = false;
        }
    }

    private void postInlineReplyNotification() {
        RemoteInput remoteInput = new RemoteInput.Builder("reply")
                .setLabel(NOTIFICATION_TEXT)
                .build();
        Context context = getInstrumentation().getTargetContext();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0 , new Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Icon icon = Icon.createWithResource(context, ICONS[0]);
        Action action = new Action.Builder(icon, REPLY_TEXT, pendingIntent)
                .addRemoteInput(remoteInput)
                .build();
        Builder builder = new Builder(getInstrumentation().getTargetContext())
                .setContentTitle(NOTIFICATION_TEXT)
                .setContentText(NOTIFICATION_TEXT)
                .setSmallIcon(ICONS[0])
                .addAction(action);
        mNotificationManager.notify(0, builder.build());
    }

    private void cancelNotifications(int sleepBetweenDuration) {
        for (int icon : ICONS) {
            mNotificationManager.cancel(icon);
            SystemClock.sleep(sleepBetweenDuration);
        }
    }

    public void blockNotifications() throws Exception {
        mDevice.executeShellCommand(DISABLE_COMMAND + GMAIL_PACKAGE_NAME);
    }

    public void unblockNotifications() throws Exception {
        mDevice.executeShellCommand(ENABLE_COMMAND + GMAIL_PACKAGE_NAME);
    }

    public void cancelNotifications() throws Exception {
        mNotificationManager.cancelAll();
    }

    /** Starts from the bottom of the recent apps list and measures jank while flinging up. */
    @JankTest(beforeTest = "populateRecentApps", beforeLoop = "resetRecentsToBottom",
            afterTest = "forceStopPackages", expectedFrames = 100, defaultIterationCount = 5)
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testRecentAppsFling() {
        UiObject2 recents = mDevice.findObject(RECENTS);
        Rect r = recents.getVisibleBounds();
        // decide the top & bottom edges for scroll gesture
        int top = r.top + r.height() / 4; // top edge = top + 25% height
        int bottom = r.bottom - 200; // bottom edge = bottom & shift up 200px
        for (int i = 0; i < INNER_LOOP; i++) {
            mDevice.swipe(r.width() / 2, top, r.width() / 2, bottom, DEFAULT_FLING_STEPS);
            mDevice.waitForIdle();
            mDevice.swipe(r.width() / 2, bottom, r.width() / 2, top, DEFAULT_FLING_STEPS);
            mDevice.waitForIdle();
        }
    }

    /**
     * Measures jank when dismissing a task in recents.
     */
    @JankTest(beforeTest = "populateRecentApps", beforeLoop = "resetRecentsToBottom",
            afterTest = "forceStopPackages", expectedFrames = 10, defaultIterationCount = 5)
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testRecentAppsDismiss() {
        // Wait until dismiss views are fully faded in.
        mDevice.findObject(new UiSelector().resourceId("com.android.systemui:id/dismiss_task"))
                .waitForExists(5000);
        for (int i = 0; i < INNER_LOOP; i++) {
            List<UiObject2> dismissViews = mDevice.findObjects(
                    By.res(SYSTEMUI_PACKAGE, "dismiss_task"));
            if (dismissViews.size() == 0) {
                fail("Unable to find dismiss view");
            }
            dismissViews.get(dismissViews.size() - 1).click();
            mDevice.waitForIdle();
            SystemClock.sleep(500);
        }
    }

    private void swipeDown() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
                SWIPE_MARGIN, mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() - SWIPE_MARGIN,
                DEFAULT_SCROLL_STEPS);
    }

    private void swipeUp() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() - SWIPE_MARGIN,
                mDevice.getDisplayWidth() / 2,
                SWIPE_MARGIN,
                DEFAULT_SCROLL_STEPS);
    }

    public void beforeNotificationListPull() throws Exception {
        prepareNotifications(GROUP_MODE_LEGACY);
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterNotificationListPull(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        cancelNotifications();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /** Measures jank while pulling down the notification list */
    @JankTest(expectedFrames = 100,
            defaultIterationCount = 5,
            beforeTest = "beforeNotificationListPull", afterTest = "afterNotificationListPull")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testNotificationListPull() {
        for (int i = 0; i < INNER_LOOP; i++) {
            swipeDown();
            mDevice.waitForIdle();
            swipeUp();
            mDevice.waitForIdle();
        }
    }

    public void beforeNotificationListPull_manyNotifications() throws Exception {
        prepareNotifications(GROUP_MODE_UNGROUPED);
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    /** Measures jank while pulling down the notification list with many notifications */
    @JankTest(expectedFrames = 100,
            defaultIterationCount = 5,
            beforeTest = "beforeNotificationListPull_manyNotifications",
            afterTest = "afterNotificationListPull")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testNotificationListPull_manyNotifications() {
        for (int i = 0; i < INNER_LOOP; i++) {
            swipeDown();
            mDevice.waitForIdle();
            swipeUp();
            mDevice.waitForIdle();
        }
    }

    public void beforeQuickSettings() throws Exception {

        // Make sure we have some notifications.
        prepareNotifications(GROUP_MODE_UNGROUPED);
        mDevice.openNotification();
        SystemClock.sleep(100);
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterQuickSettings(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        cancelNotifications();
        mDevice.pressHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /** Measures jank while pulling down the quick settings */
    @JankTest(expectedFrames = 100,
            defaultIterationCount = 5,
            beforeTest = "beforeQuickSettings", afterTest = "afterQuickSettings")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testQuickSettingsPull() throws Exception {
        UiObject quickSettingsButton = mDevice.findObject(
                new UiSelector().className(ImageView.class)
                        .descriptionContains("quick settings"));
        for (int i = 0; i < INNER_LOOP; i++) {
            quickSettingsButton.click();
            mDevice.waitForIdle();
            quickSettingsButton.click();
            mDevice.waitForIdle();
        }
    }

    public void beforeUnlock() throws Exception {

        // Make sure we have some notifications.
        prepareNotifications(GROUP_MODE_UNGROUPED);
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterUnlock(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        cancelNotifications();
        mDevice.pressHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measure jank while unlocking the phone.
     */
    @JankTest(expectedFrames = 100,
            defaultIterationCount = 5,
            beforeTest = "beforeUnlock", afterTest = "afterUnlock")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testUnlock() throws Exception {
        for (int i = 0; i < INNER_LOOP; i++) {
            mDevice.sleep();
            // Make sure we don't trigger the camera launch double-tap shortcut
            SystemClock.sleep(300);
            mDevice.wakeUp();
            swipeUp();
            mDevice.waitForIdle();
        }
    }

    public void beforeExpand() throws Exception {
        prepareNotifications(GROUP_MODE_GROUPED);
        mDevice.openNotification();
        SystemClock.sleep(100);
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterExpand(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        cancelNotifications();
        mDevice.pressHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank while expending a group notification.
     */
    @JankTest(expectedFrames = 100,
            defaultIterationCount = 5,
            beforeTest = "beforeExpand", afterTest = "afterExpand")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testExpandGroup() throws Exception {
        UiObject expandButton = mDevice.findObject(
                new UiSelector().resourceId("android:id/expand_button"));
        for (int i = 0; i < INNER_LOOP; i++) {
            expandButton.click();
            mDevice.waitForIdle();
            expandButton.click();
            mDevice.waitForIdle();
        }
    }

    private void scrollDown() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() / 2,
                mDevice.getDisplayWidth() / 2,
                SWIPE_MARGIN,
                DEFAULT_SCROLL_STEPS);
    }

    public void beforeClearAll() throws Exception {
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void beforeClearAllLoop() throws Exception {
        postNotifications(GROUP_MODE_UNGROUPED);
        mDevice.openNotification();
        SystemClock.sleep(100);
        mDevice.waitForIdle();
    }

    public void afterClearAll(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when clicking the "clear all" button in the notification shade.
     */
    @JankTest(expectedFrames = 10,
            defaultIterationCount = 5,
            beforeTest = "beforeClearAll",
            beforeLoop = "beforeClearAllLoop",
            afterTest = "afterClearAll")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testClearAll() throws Exception {
        UiObject clearAll =
                mDevice.findObject(new UiSelector().className(Button.class).text("CLEAR ALL"));
        while (!clearAll.exists()) {
            scrollDown();
        }
        clearAll.click();
        mDevice.waitForIdle();
    }

    public void beforeChangeBrightness() throws Exception {
        mDevice.openQuickSettings();

        // Wait until animation is starting.
        SystemClock.sleep(200);
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterChangeBrightness(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        mDevice.pressHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when changing screen brightness
     */
    @JankTest(expectedFrames = 10,
            defaultIterationCount = 5,
            beforeTest = "beforeChangeBrightness",
            afterTest = "afterChangeBrightness")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testChangeBrightness() throws Exception {
        UiObject2 brightness = mDevice.findObject(By.res(SYSTEMUI_PACKAGE, "slider"));
        Rect bounds = brightness.getVisibleBounds();
        for (int i = 0; i < INNER_LOOP; i++) {
            mDevice.swipe(bounds.left, bounds.centerY(),
                    bounds.right, bounds.centerY(), BRIGHTNESS_SCROLL_STEPS);

            // Make sure animation is completing.
            SystemClock.sleep(500);
            mDevice.waitForIdle();
        }
    }

    public void beforeNotificationAppear() throws Exception {
        mDevice.openNotification();

        // Wait until animation is starting.
        SystemClock.sleep(200);
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterNotificationAppear(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        mDevice.pressHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when a notification is appearing.
     */
    @JankTest(expectedFrames = 10,
            defaultIterationCount = 5,
            beforeTest = "beforeNotificationAppear",
            afterTest = "afterNotificationAppear")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testNotificationAppear() throws Exception {
        for (int i = 0; i < INNER_LOOP; i++) {
            postNotifications(GROUP_MODE_UNGROUPED, 250, 5);
            mDevice.waitForIdle();
            cancelNotifications(250);
            mDevice.waitForIdle();
        }
    }

    public void beforeCameraFromLockscreen() throws Exception {
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void beforeCameraFromLockscreenLoop() throws Exception {
        mDevice.pressHome();
        mDevice.sleep();
        // Make sure we don't trigger the camera launch double-tap shortcut
        SystemClock.sleep(300);
        mDevice.wakeUp();
        mDevice.waitForIdle();
    }

    public void afterCameraFromLockscreen(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        mDevice.pressHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when launching the camera from lockscreen.
     */
    @JankTest(expectedFrames = 10,
            defaultIterationCount = 5,
            beforeTest = "beforeCameraFromLockscreen",
            afterTest = "afterCameraFromLockscreen",
            beforeLoop = "beforeCameraFromLockscreenLoop")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testCameraFromLockscreen() throws Exception {
        mDevice.swipe(mDevice.getDisplayWidth() - SWIPE_MARGIN,
                mDevice.getDisplayHeight() - SWIPE_MARGIN, SWIPE_MARGIN, SWIPE_MARGIN,
                DEFAULT_SCROLL_STEPS);
        mDevice.waitForIdle();
    }

    public void beforeAmbientWakeUp() throws Exception {
        postNotifications(GROUP_MODE_UNGROUPED);
        mDevice.sleep();
        SystemClock.sleep(1000);
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterAmbientWakeUp(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        cancelNotifications();
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.pressMenu();
        mDevice.waitForIdle();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when waking up from ambient (doze) display.
     */
    @JankTest(expectedFrames = 30,
            defaultIterationCount = 5,
            beforeTest = "beforeAmbientWakeUp",
            afterTest = "afterAmbientWakeUp")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testAmbientWakeUp() throws Exception {
        for (int i = 0; i < INNER_LOOP; i++) {
            mDevice.executeShellCommand(PULSE_COMMAND);
            SystemClock.sleep(100);
            mDevice.waitForIdle();
            mDevice.wakeUp();
            mDevice.waitForIdle();
            mDevice.sleep();
            SystemClock.sleep(1000);
            mDevice.waitForIdle();
        }
    }

    public void beforeGoToFullShade() throws Exception {
        postNotifications(GROUP_MODE_UNGROUPED);
        mDevice.sleep();

        // Don't trigger camera launch gesture
        SystemClock.sleep(300);
        mDevice.wakeUp();
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterGoToFullShade(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        mDevice.pressMenu();
        mDevice.waitForIdle();
        cancelNotifications();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when tragging down on a notification on the lockscreen to go to the full shade.
     */
    @JankTest(expectedFrames = 100,
            defaultIterationCount = 5,
            beforeTest = "beforeGoToFullShade",
            afterTest = "afterGoToFullShade")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testGoToFullShade() throws Exception {
        for (int i = 0; i < INNER_LOOP; i++) {
            mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2,
                    mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() - SWIPE_MARGIN,
                    DEFAULT_SCROLL_STEPS);
            mDevice.waitForIdle();
            mDevice.click(mDevice.getDisplayWidth() / 4, mDevice.getDisplayHeight() - SWIPE_MARGIN);
            mDevice.waitForIdle();
        }
    }

    public void beforeInlineReply() throws Exception {
        postInlineReplyNotification();
        mDevice.openNotification();

        // Wait until animation kicks in
        SystemClock.sleep(100);
        mDevice.waitForIdle();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterInlineReply(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        cancelNotifications();
        goHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when clicking "reply" on a notification that supports inline reply.
     */
    @JankTest(expectedFrames = 50,
            defaultIterationCount = 5,
            beforeTest = "beforeInlineReply",
            afterTest = "afterInlineReply")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testInlineReply() throws Exception {
        UiObject2 replyButton = mDevice.findObject(By.clazz(Button.class).text(REPLY_TEXT));
        for (int i = 0; i < INNER_LOOP; i++) {
            replyButton.click();
            mDevice.waitForIdle();
            Thread.sleep(1000);
            mDevice.pressBack();
            mDevice.waitForIdle();
            mDevice.pressBack();
            mDevice.waitForIdle();
        }
    }

    public void beforePinAppearance() throws Exception {
        LockscreenHelper.getInstance().setScreenLockViaShell(PIN, LockscreenHelper.MODE_PIN);
        goHome();
        mDevice.sleep();
        SystemClock.sleep(300);
        mDevice.wakeUp();
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterPinAppearanceLoop() throws Exception {
        mDevice.pressBack();
        mDevice.waitForIdle();
    }

    public void afterPinAppearance(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        LockscreenHelper.getInstance().unlockScreen(PIN);
        LockscreenHelper.getInstance().removeScreenLockViaShell(PIN);
        mDevice.pressHome();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when launching the camera from lockscreen.
     */
    @JankTest(expectedFrames = 30,
            defaultIterationCount = 5,
            beforeTest = "beforePinAppearance",
            afterTest = "afterPinAppearance",
            afterLoop = "afterPinAppearanceLoop")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testPinAppearance() throws Exception {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() - SWIPE_MARGIN,
                mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2,
                DEFAULT_SCROLL_STEPS);
        mDevice.waitForIdle();
        String command = String.format("%s %s %s", "input", "text", PIN);
        mDevice.executeShellCommand(command);
        mDevice.waitForIdle();
    }

    public void beforeLaunchSettings() throws Exception {
        prepareNotifications(GROUP_MODE_UNGROUPED);
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void beforeLaunchSettingsLoop() throws Exception {
        mDevice.openNotification();

        // Wait until animation kicks in
        SystemClock.sleep(100);
        mDevice.waitForIdle();
    }

    public void afterLaunchSettingsLoop() throws Exception {
        mDevice.executeShellCommand("am force-stop " + SETTINGS_PACKAGE);
        mDevice.pressHome();
        mDevice.waitForIdle();
    }

    public void afterLaunchSettings(Bundle metrics) throws Exception {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        cancelNotifications();
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /**
     * Measures jank when launching settings from notification shade
     */
    @JankTest(expectedFrames = 30,
            defaultIterationCount = 5,
            beforeTest = "beforeLaunchSettings",
            afterTest = "afterLaunchSettings",
            beforeLoop = "beforeLaunchSettingsLoop",
            afterLoop = "afterLaunchSettingsLoop")
    @GfxMonitor(processName = SYSTEMUI_PACKAGE)
    public void testLaunchSettings() throws Exception {
        mDevice.findObject(By.res(SYSTEMUI_PACKAGE, "settings_button")).click();
        // Wait until animation kicks in
        SystemClock.sleep(100);
        mDevice.waitForIdle();
    }
}
