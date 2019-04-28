
package com.android.notification.functional;

import android.app.AppGlobals;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.provider.Settings.SettingNotFoundException;
import android.service.notification.StatusBarNotification;
import android.service.notification.ZenModeConfig;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import com.android.server.notification.NotificationManagerService;
import com.android.server.notification.ConditionProviders;
import com.android.server.notification.ManagedServices.UserProfiles;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import com.android.server.notification.ZenModeHelper;
import com.android.server.notification.NotificationRecord;
import com.android.server.notification.ZenModeFiltering;

public class NotificationDNDTests extends InstrumentationTestCase {
    private static final String LOG_TAG = NotificationDNDTests.class.getSimpleName();
    private static final int SHORT_TIMEOUT = 1000;
    private static final int LONG_TIMEOUT = 2000;
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CONTENT_TEXT = "INLINE REPLY TEST";
    private NotificationManager mNotificationManager;
    private UiDevice mDevice = null;
    private Context mContext;
    private NotificationHelper mHelper;
    private ContentResolver mResolver;
    private ZenModeHelper mZenHelper;
    private boolean isGranted = false;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getTargetContext();
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mHelper = new NotificationHelper(mDevice, getInstrumentation(), mNotificationManager);
        mResolver = mContext.getContentResolver();
        ConditionProviders cps = new ConditionProviders(mContext,
                new UserProfiles(), AppGlobals.getPackageManager());
        Callable<ZenModeHelper> callable = new Callable<ZenModeHelper>() {
            @Override
            public ZenModeHelper call() throws Exception {
                return new ZenModeHelper(mContext, Looper.getMainLooper(), cps);
            }
        };
        FutureTask<ZenModeHelper> task = new FutureTask<>(callable);
        getInstrumentation().runOnMainSync(task);
        mZenHelper = task.get();
        mDevice.setOrientationNatural();
        mHelper.unlockScreen();
        mDevice.pressHome();
        mNotificationManager.cancelAll();
        isGranted = mNotificationManager.isNotificationPolicyAccessGranted();
    }

    @Override
    public void tearDown() throws Exception {
        mNotificationManager.cancelAll();
        mDevice.pressHome();
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    @LargeTest
    public void testDND() throws Exception {
        if (!isGranted) {
            grantPolicyAccess(true);
        }
        int setting = mNotificationManager.getCurrentInterruptionFilter();
        try {
            mNotificationManager
                    .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID, true);
            Thread.sleep(LONG_TIMEOUT);
            NotificationRecord nr = new NotificationRecord(mContext,
                    mHelper.getStatusBarNotification(NOTIFICATION_ID), mHelper.getDefaultChannel());
            ZenModeConfig mConfig = mZenHelper.getConfig();
            ZenModeFiltering zF = new ZenModeFiltering(mContext);
            assertTrue(zF.shouldIntercept(mNotificationManager.getZenMode(), mConfig, nr));
        } finally {
            mNotificationManager.setInterruptionFilter(setting);
            if (!isGranted) {
                grantPolicyAccess(false);
            }
        }
    }

    @LargeTest
    public void testPriority() throws Exception {
        int setting = mNotificationManager.getCurrentInterruptionFilter();
        if (!isGranted) {
            grantPolicyAccess(true);
        }
        mNotificationManager
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
        try {
            mHelper.showAppNotificationSettings(mContext);
            mDevice.wait(Until.findObject(By.textContains("Miscellaneous")), LONG_TIMEOUT)
                    .click();
            mDevice.wait(Until.findObject(By.textContains("Override Do Not Disturb")), LONG_TIMEOUT)
                    .click();
            Thread.sleep(LONG_TIMEOUT);
            mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID, true);
            Thread.sleep(LONG_TIMEOUT);
            NotificationRecord nr = new NotificationRecord(mContext,
                    mHelper.getStatusBarNotification(NOTIFICATION_ID), mHelper.getDefaultChannel());
            ZenModeConfig mConfig = mZenHelper.getConfig();
            ZenModeFiltering zF = new ZenModeFiltering(mContext);
            assertFalse(zF.shouldIntercept(mZenHelper.getZenMode(), mConfig, nr));
        } finally {
            mHelper.showAppNotificationSettings(mContext);
            mDevice.wait(Until.findObject(By.textContains("Miscellaneous")), LONG_TIMEOUT)
                    .click();
            mDevice.wait(Until.findObject(By.textContains("Override Do Not Disturb")), LONG_TIMEOUT)
                    .click();
            mNotificationManager.setInterruptionFilter(setting);
            if (!isGranted) {
                grantPolicyAccess(false);
            }
        }
    }

    @LargeTest
    public void testBlockNotification() throws Exception {
        try {
            mHelper.showAppNotificationSettings(mContext);
            mDevice.wait(Until.findObject(By.textContains("Block all")), LONG_TIMEOUT).click();
            Thread.sleep(LONG_TIMEOUT);
            mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID, true);
            Thread.sleep(LONG_TIMEOUT);
            if (mHelper.checkNotificationExistence(NOTIFICATION_ID, true)) {
                fail(String.format("Notification %s has not been blocked", NOTIFICATION_ID));
            }
        } finally {
            mHelper.showAppNotificationSettings(mContext);
            mDevice.wait(Until.findObject(By.textContains("Block all")), LONG_TIMEOUT).click();
        }
    }

    private void grantPolicyAccess(boolean isGranted) throws Exception {
        NotificationHelper.launchSettingsPage(mContext,
                android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        Thread.sleep(LONG_TIMEOUT);
        if (isGranted) {
            mDevice.wait(Until.findObject(By.text("OFF")), LONG_TIMEOUT).click();
            Thread.sleep(SHORT_TIMEOUT);
            mDevice.wait(Until.findObject(By.text("ALLOW")), LONG_TIMEOUT).click();
        } else {
            mDevice.wait(Until.findObject(By.text("ON")), LONG_TIMEOUT).click();
            Thread.sleep(SHORT_TIMEOUT);
            mDevice.wait(Until.findObject(By.text("OK")), LONG_TIMEOUT).click();
        }
        Thread.sleep(LONG_TIMEOUT);
        mDevice.pressHome();
    }
}
