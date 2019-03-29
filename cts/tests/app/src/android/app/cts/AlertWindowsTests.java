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
 * limitations under the License
 */

package android.app.cts;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE_PRE_26;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
import static android.content.Context.BIND_ALLOW_OOM_MANAGEMENT;
import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_NOT_FOREGROUND;

import static com.android.app2.AlertWindowService.MSG_ADD_ALERT_WINDOW;
import static com.android.app2.AlertWindowService.MSG_ON_ALERT_WINDOW_ADDED;
import static com.android.app2.AlertWindowService.MSG_ON_ALERT_WINDOW_REMOVED;
import static com.android.app2.AlertWindowService.MSG_REMOVE_ALERT_WINDOW;
import static com.android.app2.AlertWindowService.MSG_REMOVE_ALL_ALERT_WINDOWS;
import static com.android.app2.AlertWindowService.NOTIFICATION_MESSENGER_EXTRA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.app2.AlertWindowService;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Build: mmma -j32 cts/tests/app
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsAppTestCases android.app.cts.AlertWindowsTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class AlertWindowsTests {

    private static final String TAG = "AlertWindowsTests";

    private static final boolean DEBUG = false;
    private static final long WAIT_TIME_MS = 2 * 1000;

    private static final String SDK25_PACKAGE_NAME = "com.android.appSdk25";

    private Messenger mService;
    private String mServicePackageName;
    private int mServiceUid;

    private PackageManager mPm;

    private ActivityManager mAm;
    private ActivityManager mAm25; // ActivityManager created for an SDK 25 app context.

    private final Messenger mMessenger = new Messenger(new IncomingHandler(Looper.getMainLooper()));
    private final Object mAddedLock = new Object();
    private final Object mRemoveLock = new Object();

    @Before
    public void setUp() throws Exception {
        if (DEBUG) Log.e(TAG, "setUp");
        final Context context = InstrumentationRegistry.getTargetContext();

        mPm = context.getPackageManager();

        mAm = context.getSystemService(ActivityManager.class);
        mAm25 = context.createPackageContext(SDK25_PACKAGE_NAME, 0)
                .getSystemService(ActivityManager.class);

        final Intent intent = new Intent();
        intent.setClassName(AlertWindowService.class.getPackage().getName(),
                AlertWindowService.class.getName());
        intent.putExtra(NOTIFICATION_MESSENGER_EXTRA, mMessenger);
        // Needs to be both BIND_NOT_FOREGROUND and BIND_ALLOW_OOM_MANAGEMENT to avoid the binding
        // to this instrumentation test from increasing its importance.
        context.bindService(intent, mConnection,
                BIND_AUTO_CREATE | BIND_NOT_FOREGROUND | BIND_ALLOW_OOM_MANAGEMENT);
        synchronized (mConnection) {
            // Wait for alert window service to be connection before processing.
            mConnection.wait(WAIT_TIME_MS);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (DEBUG) Log.e(TAG, "tearDown");
        if (mService != null) {
            mService.send(Message.obtain(null, MSG_REMOVE_ALL_ALERT_WINDOWS));
        }
        final Context context = InstrumentationRegistry.getTargetContext();
        context.unbindService(mConnection);
        mAm = null;
    }

    @Test
    public void testAlertWindowOomAdj() throws Exception {
        setAlertWindowPermission(true /* allow */);


        assertPackageImportance(IMPORTANCE_PERCEPTIBLE, IMPORTANCE_PERCEPTIBLE_PRE_26);

        // TODO AM.getUidImportance() sometimes return a different value from what
        // getPackageImportance() returns... b/37950472
        // assertUidImportance(IMPORTANCE_PERCEPTIBLE, IMPORTANCE_PERCEPTIBLE_PRE_26);

        addAlertWindow();
        // Process importance should be increased to visible when the service has an alert window.
        assertPackageImportance(IMPORTANCE_VISIBLE, IMPORTANCE_VISIBLE);

        addAlertWindow();
        assertPackageImportance(IMPORTANCE_VISIBLE, IMPORTANCE_VISIBLE);

        setAlertWindowPermission(false /* allow */);
        // Process importance should no longer be visible since its alert windows are not allowed to
        // be visible.
        assertPackageImportance(IMPORTANCE_PERCEPTIBLE, IMPORTANCE_PERCEPTIBLE_PRE_26);

        setAlertWindowPermission(true /* allow */);
        // They can show again so importance should be visible again.
        assertPackageImportance(IMPORTANCE_VISIBLE, IMPORTANCE_VISIBLE);

        removeAlertWindow();
        assertPackageImportance(IMPORTANCE_VISIBLE, IMPORTANCE_VISIBLE);

        removeAlertWindow();
        // Process importance should no longer be visible when the service no longer as alert
        // windows.
        assertPackageImportance(IMPORTANCE_PERCEPTIBLE, IMPORTANCE_PERCEPTIBLE_PRE_26);
    }

    private void addAlertWindow() throws Exception {
        mService.send(Message.obtain(null, MSG_ADD_ALERT_WINDOW));
        synchronized (mAddedLock) {
            // Wait for window addition confirmation before proceeding.
            mAddedLock.wait(WAIT_TIME_MS);
        }
    }

    private void removeAlertWindow() throws Exception {
        mService.send(Message.obtain(null, MSG_REMOVE_ALERT_WINDOW));
        synchronized (mRemoveLock) {
            // Wait for window removal confirmation before proceeding.
            mRemoveLock.wait(WAIT_TIME_MS);
        }
    }

    private void setAlertWindowPermission(boolean allow) throws Exception {
        final String cmd = "appops set " + mServicePackageName
                + " android:system_alert_window " + (allow ? "allow" : "deny");
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(), cmd);
    }

    private void assertImportance(Function<ActivityManager, Integer> apiCaller,
            int expectedForO, int expectedForPreO) throws Exception {
        final long TIMEOUT = SystemClock.uptimeMillis() + TimeUnit.SECONDS.toMillis(30);
        int actual;

        do {
            // TODO: We should try to use ActivityManagerTest.UidImportanceListener here to listen
            // for changes in the uid importance. However, the way it is currently structured
            // doesn't really work for this use case right now...
            Thread.sleep(500);
            actual = apiCaller.apply(mAm);
        } while (actual != expectedForO && (SystemClock.uptimeMillis() < TIMEOUT));

        assertEquals(expectedForO, actual);

        // Check the result for pre-O apps.
        assertEquals(expectedForPreO, (int) apiCaller.apply(mAm25));
    }

    /**
     * Make sure {@link ActivityManager#getPackageImportance} returns the expected value.
     */
    private void assertPackageImportance(int expectedForO, int expectedForPreO) throws Exception {
        assertImportance(am -> am.getPackageImportance(mServicePackageName),
                expectedForO, expectedForPreO);
    }

    /**
     * Make sure {@link ActivityManager#getUidImportance(int)} returns the expected value.
     */
    private void assertUidImportance(int expectedForO, int expectedForPreO) throws Exception {
        assertImportance(am -> am.getUidImportance(mServiceUid),
                expectedForO, expectedForPreO);
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.e(TAG, "onServiceConnected");
            mService = new Messenger(service);
            mServicePackageName = name.getPackageName();
            try {
                mServiceUid = mPm.getPackageUid(mServicePackageName, 0);
            } catch (NameNotFoundException e) {
                throw new RuntimeException("getPackageUid() failed.", e);
            }
            synchronized (mConnection) {
                notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.e(TAG, "onServiceDisconnected");
            mService = null;
            mServicePackageName = null;
            mServiceUid = 0;
        }
    };

    private class IncomingHandler extends Handler {

        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_ON_ALERT_WINDOW_ADDED:
                    synchronized (mAddedLock) {
                        if (DEBUG) Log.e(TAG, "MSG_ON_ALERT_WINDOW_ADDED");
                        mAddedLock.notifyAll();
                    }
                    break;
                case MSG_ON_ALERT_WINDOW_REMOVED:
                    synchronized (mRemoveLock) {
                        if (DEBUG) Log.e(TAG, "MSG_ON_ALERT_WINDOW_REMOVED");
                        mRemoveLock.notifyAll();
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
}
