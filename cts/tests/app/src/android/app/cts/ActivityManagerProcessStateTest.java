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

package android.app.cts;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.cts.android.app.cts.tools.ServiceConnectionHandler;
import android.app.cts.android.app.cts.tools.ServiceProcessController;
import android.app.cts.android.app.cts.tools.SyncOrderedBroadcast;
import android.app.cts.android.app.cts.tools.UidImportanceListener;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.PowerManager;
import android.os.RemoteException;
import android.test.InstrumentationTestCase;

import com.android.compatibility.common.util.SystemUtil;

public class ActivityManagerProcessStateTest extends InstrumentationTestCase {
    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final int WAIT_TIME = 2000;
    // A secondary test activity from another APK.
    static final String SIMPLE_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    static final String SIMPLE_SERVICE = ".SimpleService";
    static final String SIMPLE_SERVICE2 = ".SimpleService2";
    static final String SIMPLE_RECEIVER_START_SERVICE = ".SimpleReceiverStartService";
    static final String SIMPLE_ACTIVITY_START_SERVICE = ".SimpleActivityStartService";
    public static String ACTION_SIMPLE_ACTIVITY_START_SERVICE_RESULT =
            "com.android.cts.launcherapps.simpleapp.SimpleActivityStartService.RESULT";

    private Context mContext;
    private Instrumentation mInstrumentation;
    private Intent mServiceIntent;
    private Intent mService2Intent;
    private Intent mAllProcesses[];

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mServiceIntent = new Intent();
        mServiceIntent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_SERVICE);
        mService2Intent = new Intent();
        mService2Intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_SERVICE2);
        mAllProcesses = new Intent[2];
        mAllProcesses[0] = mServiceIntent;
        mAllProcesses[1] = mService2Intent;
        mContext.stopService(mServiceIntent);
        mContext.stopService(mService2Intent);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test basic state changes as processes go up and down due to services running in them.
     */
    public void testUidImportanceListener() throws Exception {
        final Parcel data = Parcel.obtain();
        ServiceConnectionHandler conn = new ServiceConnectionHandler(mContext, mServiceIntent);
        ServiceConnectionHandler conn2 = new ServiceConnectionHandler(mContext, mService2Intent);

        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                SIMPLE_PACKAGE_NAME, 0);
        UidImportanceListener uidForegroundListener = new UidImportanceListener(appInfo.uid);

        String cmd = "pm revoke " + STUB_PACKAGE_NAME + " "
                + Manifest.permission.PACKAGE_USAGE_STATS;
        String result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        */
        boolean gotException = false;
        try {
            am.addOnUidImportanceListener(uidForegroundListener,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE);
        } catch (SecurityException e) {
            gotException = true;
        }
        assertTrue("Expected SecurityException thrown", gotException);

        cmd = "pm grant " + STUB_PACKAGE_NAME + " "
                + Manifest.permission.PACKAGE_USAGE_STATS;
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        Log.d("XXXX", SystemUtil.runShellCommand(getInstrumentation(), "dumpsys package "
                + STUB_PACKAGE_NAME));
        */
        am.addOnUidImportanceListener(uidForegroundListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE);

        UidImportanceListener uidGoneListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidGoneListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);

        WatchUidRunner uidWatcher = new WatchUidRunner(getInstrumentation(), appInfo.uid);

        try {
            // First kill the processes to start out in a stable state.
            conn.bind(WAIT_TIME);
            conn2.bind(WAIT_TIME);
            IBinder service1 = conn.getServiceIBinder();
            IBinder service2 = conn2.getServiceIBinder();
            conn.unbind(WAIT_TIME);
            conn2.unbind(WAIT_TIME);
            try {
                service1.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
            } catch (RemoteException e) {
            }
            try {
                service2.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
            } catch (RemoteException e) {
            }
            service1 = service2 = null;

            // Wait for uid's processes to go away.
            uidGoneListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // And wait for the uid report to be gone.
            uidWatcher.waitFor(WatchUidRunner.CMD_GONE, null, WAIT_TIME);

            // Now bind and see if we get told about the uid coming in to the foreground.
            conn.bind(WAIT_TIME);
            uidForegroundListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // Also make sure the uid state reports are as expected.  Wait for active because
            // there may be some intermediate states as the process comes up.
            uidWatcher.waitFor(WatchUidRunner.CMD_ACTIVE, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "FGS", WAIT_TIME);

            // Pull out the service IBinder for a kludy hack...
            IBinder service = conn.getServiceIBinder();

            // Now unbind and see if we get told about it going to the background.
            conn.unbind(WAIT_TIME);
            uidForegroundListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            uidWatcher.waitFor(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // Now kill the process and see if we are told about it being gone.
            try {
                service.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
            } catch (RemoteException e) {
                // It is okay if it is already gone for some reason.
            }

            uidGoneListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            uidWatcher.expect(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_GONE, null, WAIT_TIME);

            // Now we are going to try different combinations of binding to two processes to
            // see if they are correctly combined together for the app.

            // Bring up both services.
            conn.bind(WAIT_TIME);
            conn2.bind(WAIT_TIME);
            uidForegroundListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // Also make sure the uid state reports are as expected.
            uidWatcher.waitFor(WatchUidRunner.CMD_ACTIVE, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "FGS", WAIT_TIME);

            // Bring down one service, app state should remain foreground.
            conn2.unbind(WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // Bring down other service, app state should now be cached.  (If the processes both
            // actually get killed immediately, this is also not a correctly behaving system.)
            conn.unbind(WAIT_TIME);
            uidGoneListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            uidWatcher.waitFor(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // Bring up one service, this should be sufficient to become foreground.
            conn2.bind(WAIT_TIME);
            uidForegroundListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            uidWatcher.expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "FGS", WAIT_TIME);

            // Bring up other service, should remain foreground.
            conn.bind(WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // Bring down one service, should remain foreground.
            conn.unbind(WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // And bringing down other service should put us back to cached.
            conn2.unbind(WAIT_TIME);
            uidGoneListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            uidWatcher.waitFor(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);
        } finally {
            data.recycle();

            uidWatcher.finish();

            am.removeOnUidImportanceListener(uidForegroundListener);
            am.removeOnUidImportanceListener(uidGoneListener);
        }
    }

    /**
     * Test that background check correctly prevents idle services from running but allows
     * whitelisted apps to bypass the check.
     */
    public void testBackgroundCheckService() throws Exception {
        final Parcel data = Parcel.obtain();
        Intent serviceIntent = new Intent();
        serviceIntent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_SERVICE);
        ServiceConnectionHandler conn = new ServiceConnectionHandler(mContext, serviceIntent);

        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        String cmd = "pm grant " + STUB_PACKAGE_NAME + " "
                + Manifest.permission.PACKAGE_USAGE_STATS;
        String result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        Log.d("XXXX", SystemUtil.runShellCommand(getInstrumentation(), "dumpsys package "
                + STUB_PACKAGE_NAME));
        */

        ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                SIMPLE_PACKAGE_NAME, 0);

        UidImportanceListener uidForegroundListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidForegroundListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE);
        UidImportanceListener uidGoneListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidGoneListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY);

        WatchUidRunner uidWatcher = new WatchUidRunner(getInstrumentation(), appInfo.uid);

        // First kill the process to start out in a stable state.
        mContext.stopService(serviceIntent);
        conn.bind(WAIT_TIME);
        IBinder service = conn.getServiceIBinder();
        conn.unbind(WAIT_TIME);
        try {
            service.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
        }
        service = null;

        // Wait for uid's process to go away.
        uidGoneListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
        assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                am.getPackageImportance(SIMPLE_PACKAGE_NAME));

        // And wait for the uid report to be gone.
        uidWatcher.waitFor(WatchUidRunner.CMD_GONE, null, WAIT_TIME);

        cmd = "appops set " + SIMPLE_PACKAGE_NAME + " RUN_IN_BACKGROUND deny";
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // This is a side-effect of the app op command.
        uidWatcher.expect(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);
        uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "NONE", WAIT_TIME);

        // We don't want to wait for the uid to actually go idle, we can force it now.
        cmd = "am make-uid-idle " + SIMPLE_PACKAGE_NAME;
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // Make sure app is not yet on whitelist
        cmd = "cmd deviceidle whitelist -" + SIMPLE_PACKAGE_NAME;
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // We will use this to monitor when the service is running.
        conn.startMonitoring();

        try {
            // Try starting the service.  Should fail!
            boolean failed = false;
            try {
                mContext.startService(serviceIntent);
            } catch (IllegalStateException e) {
                failed = true;
            }
            if (!failed) {
                fail("Service was allowed to start while in the background");
            }

            // Put app on temporary whitelist to see if this allows the service start.
            cmd = "cmd deviceidle tempwhitelist -d 2000 " + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            // Try starting the service now that the app is whitelisted...  should work!
            mContext.startService(serviceIntent);
            conn.waitForConnect(WAIT_TIME);

            // Also make sure the uid state reports are as expected.
            uidWatcher.waitFor(WatchUidRunner.CMD_ACTIVE, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "SVC", WAIT_TIME);

            // Good, now stop the service and give enough time to get off the temp whitelist.
            mContext.stopService(serviceIntent);
            conn.waitForDisconnect(WAIT_TIME);

            uidWatcher.expect(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            Thread.sleep(3000);

            // Going off the temp whitelist causes a spurious proc state report...  that's
            // not ideal, but okay.
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // We don't want to wait for the uid to actually go idle, we can force it now.
            cmd = "am make-uid-idle " + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            uidWatcher.expect(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // Now that we should be off the temp whitelist, make sure we again can't start.
            failed = false;
            try {
                mContext.startService(serviceIntent);
            } catch (IllegalStateException e) {
                failed = true;
            }
            if (!failed) {
                fail("Service was allowed to start while in the background");
            }

            // Now put app on whitelist, should allow service to run.
            cmd = "cmd deviceidle whitelist +" + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            // Try starting the service now that the app is whitelisted...  should work!
            mContext.startService(serviceIntent);
            conn.waitForConnect(WAIT_TIME);

            uidWatcher.expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "SVC", WAIT_TIME);

            // Okay, bring down the service.
            mContext.stopService(serviceIntent);
            conn.waitForDisconnect(WAIT_TIME);

            uidWatcher.expect(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

        } finally {
            mContext.stopService(serviceIntent);
            conn.stopMonitoring();

            uidWatcher.finish();

            cmd = "appops set " + SIMPLE_PACKAGE_NAME + " RUN_IN_BACKGROUND allow";
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
            cmd = "cmd deviceidle whitelist -" + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            am.removeOnUidImportanceListener(uidGoneListener);
            am.removeOnUidImportanceListener(uidForegroundListener);

            data.recycle();
        }
    }

    /**
     * Test that background check behaves correctly after a process is no longer foreground:
     * first allowing a service to be started, then stopped by the system when idle.
     */
    public void testBackgroundCheckStopsService() throws Exception {
        final Parcel data = Parcel.obtain();
        ServiceConnectionHandler conn = new ServiceConnectionHandler(mContext, mServiceIntent);
        ServiceConnectionHandler conn2 = new ServiceConnectionHandler(mContext, mService2Intent);

        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        String cmd = "pm grant " + STUB_PACKAGE_NAME + " "
                + Manifest.permission.PACKAGE_USAGE_STATS;
        String result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        Log.d("XXXX", SystemUtil.runShellCommand(getInstrumentation(), "dumpsys package "
                + STUB_PACKAGE_NAME));
        */

        ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                SIMPLE_PACKAGE_NAME, 0);

        UidImportanceListener uidServiceListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidServiceListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE);
        UidImportanceListener uidGoneListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidGoneListener,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED);

        WatchUidRunner uidWatcher = new WatchUidRunner(getInstrumentation(), appInfo.uid);

        // First kill the process to start out in a stable state.
        mContext.stopService(mServiceIntent);
        mContext.stopService(mService2Intent);
        conn.bind(WAIT_TIME);
        conn2.bind(WAIT_TIME);
        IBinder service = conn.getServiceIBinder();
        IBinder service2 = conn2.getServiceIBinder();
        conn.unbind(WAIT_TIME);
        conn2.unbind(WAIT_TIME);
        try {
            service.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
        }
        try {
            service2.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
        }
        service = service2 = null;

        // Wait for uid's process to go away.
        uidGoneListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
        assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE,
                am.getPackageImportance(SIMPLE_PACKAGE_NAME));

        // And wait for the uid report to be gone.
        uidWatcher.waitFor(WatchUidRunner.CMD_GONE, null, WAIT_TIME);

        cmd = "appops set " + SIMPLE_PACKAGE_NAME + " RUN_IN_BACKGROUND deny";
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // This is a side-effect of the app op command.
        uidWatcher.expect(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);
        uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "NONE", WAIT_TIME);

        // We don't want to wait for the uid to actually go idle, we can force it now.
        cmd = "am make-uid-idle " + SIMPLE_PACKAGE_NAME;
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // Make sure app is not yet on whitelist
        cmd = "cmd deviceidle whitelist -" + SIMPLE_PACKAGE_NAME;
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // We will use this to monitor when the service is running.
        conn.startMonitoring();

        try {
            // Try starting the service.  Should fail!
            boolean failed = false;
            try {
                mContext.startService(mServiceIntent);
            } catch (IllegalStateException e) {
                failed = true;
            }
            if (!failed) {
                fail("Service was allowed to start while in the background");
            }

            // First poke the process into the foreground, so we can avoid background check.
            conn2.bind(WAIT_TIME);
            conn2.waitForConnect(WAIT_TIME);

            // Wait for process state to reflect running service.
            uidServiceListener.waitForValue(
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // Also make sure the uid state reports are as expected.
            uidWatcher.waitFor(WatchUidRunner.CMD_ACTIVE, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "FGS", WAIT_TIME);

            conn2.unbind(WAIT_TIME);

            // Wait for process to recover back down to being cached.
            uidServiceListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            uidWatcher.waitFor(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // Try starting the service now that the app is waiting to idle...  should work!
            mContext.startService(mServiceIntent);
            conn.waitForConnect(WAIT_TIME);

            uidWatcher.expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "SVC", WAIT_TIME);

            // And also start the second service.
            conn2.startMonitoring();
            mContext.startService(mService2Intent);
            conn2.waitForConnect(WAIT_TIME);

            // Force app to go idle now
            cmd = "am make-uid-idle " + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            // Wait for services to be stopped by system.
            uidServiceListener.waitForValue(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
            assertEquals(ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                    am.getPackageImportance(SIMPLE_PACKAGE_NAME));

            // And service should be stopped by system, so just make sure it is disconnected.
            conn.waitForDisconnect(WAIT_TIME);
            conn2.waitForDisconnect(WAIT_TIME);

            uidWatcher.expect(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);
            // There may be a transient 'SVC' proc state here.
            uidWatcher.waitFor(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            uidWatcher.expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

        } finally {
            mContext.stopService(mServiceIntent);
            mContext.stopService(mService2Intent);
            conn.cleanup(WAIT_TIME);
            conn2.cleanup(WAIT_TIME);

            uidWatcher.finish();

            cmd = "appops set " + SIMPLE_PACKAGE_NAME + " RUN_IN_BACKGROUND allow";
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
            cmd = "cmd deviceidle whitelist -" + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            am.removeOnUidImportanceListener(uidGoneListener);
            am.removeOnUidImportanceListener(uidServiceListener);

            data.recycle();
        }
    }

    /**
     * Test the background check doesn't allow services to be started from broadcasts except
     * when in the correct states.
     */
    public void testBackgroundCheckBroadcastService() throws Exception {
        final Intent broadcastIntent = new Intent();
        broadcastIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        broadcastIntent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_RECEIVER_START_SERVICE);

        final ServiceProcessController controller = new ServiceProcessController(mContext,
                getInstrumentation(), STUB_PACKAGE_NAME, mAllProcesses);
        final ServiceConnectionHandler conn = new ServiceConnectionHandler(mContext,
                mServiceIntent);

        try {
            // First kill the process to start out in a stable state.
            controller.ensureProcessGone(WAIT_TIME);

            // Do initial setup.
            controller.denyBackgroundOp(WAIT_TIME);
            controller.makeUidIdle();
            controller.removeFromWhitelist();

            // We will use this to monitor when the service is running.
            conn.startMonitoring();

            // Try sending broadcast to start the service.  Should fail!
            SyncOrderedBroadcast br = new SyncOrderedBroadcast();
            broadcastIntent.putExtra("service", mServiceIntent);
            br.sendAndWait(mContext, broadcastIntent, Activity.RESULT_OK, null, null, WAIT_TIME);
            int brCode = br.getReceivedCode();
            if (brCode != Activity.RESULT_CANCELED) {
                fail("Didn't fail starting service, result=" + brCode);
            }

            // Track the uid proc state changes from the broadcast (but not service execution)
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "RCVR", WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // Put app on temporary whitelist to see if this allows the service start.
            controller.tempWhitelist(2000);

            // Being on the whitelist means the uid is now active.
            controller.getUidWatcher().expect(WatchUidRunner.CMD_ACTIVE, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // Try starting the service now that the app is whitelisted...  should work!
            br.sendAndWait(mContext, broadcastIntent, Activity.RESULT_OK, null, null, WAIT_TIME);
            brCode = br.getReceivedCode();
            if (brCode != Activity.RESULT_FIRST_USER) {
                fail("Failed starting service, result=" + brCode);
            }
            conn.waitForConnect(WAIT_TIME);

            // Also make sure the uid state reports are as expected.
            controller.getUidWatcher().expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            // We are going to wait until 'SVC', because we may see an intermediate 'RCVR'
            // proc state depending on timing.
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_PROCSTATE, "SVC", WAIT_TIME);

            // Good, now stop the service and give enough time to get off the temp whitelist.
            mContext.stopService(mServiceIntent);
            conn.waitForDisconnect(WAIT_TIME);

            controller.getUidWatcher().expect(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            Thread.sleep(3000);

            // Going off the temp whitelist causes a spurious proc state report...  that's
            // not ideal, but okay.
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // We don't want to wait for the uid to actually go idle, we can force it now.
            controller.makeUidIdle();

            controller.getUidWatcher().expect(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);

            // Make sure the process is gone so we start over fresh.
            controller.ensureProcessGone(WAIT_TIME);

            // Now that we should be off the temp whitelist, make sure we again can't start.
            br.sendAndWait(mContext, broadcastIntent, Activity.RESULT_OK, null, null, WAIT_TIME);
            brCode = br.getReceivedCode();
            if (brCode != Activity.RESULT_CANCELED) {
                fail("Didn't fail starting service, result=" + brCode);
            }

            // Track the uid proc state changes from the broadcast (but not service execution)
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);
            // There could be a transient 'cached' state here before 'uncached' if uid state
            // changes are dispatched before receiver is started.
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "RCVR", WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // Now put app on whitelist, should allow service to run.
            controller.addToWhitelist();

            // Try starting the service now that the app is whitelisted...  should work!
            br.sendAndWait(mContext, broadcastIntent, Activity.RESULT_OK, null, null, WAIT_TIME);
            brCode = br.getReceivedCode();
            if (brCode != Activity.RESULT_FIRST_USER) {
                fail("Failed starting service, result=" + brCode);
            }
            conn.waitForConnect(WAIT_TIME);

            // Also make sure the uid state reports are as expected.
            controller.getUidWatcher().expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_PROCSTATE, "SVC", WAIT_TIME);

            // Okay, bring down the service.
            mContext.stopService(mServiceIntent);
            conn.waitForDisconnect(WAIT_TIME);

            controller.getUidWatcher().expect(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

        } finally {
            mContext.stopService(mServiceIntent);
            conn.stopMonitoringIfNeeded();
            controller.cleanup();
        }
    }


    /**
     * Test that background check does allow services to be started from activities.
     */
    public void testBackgroundCheckActivityService() throws Exception {
        final Intent activityIntent = new Intent();
        activityIntent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_START_SERVICE);

        final ServiceProcessController controller = new ServiceProcessController(mContext,
                getInstrumentation(), STUB_PACKAGE_NAME, mAllProcesses);
        final ServiceConnectionHandler conn = new ServiceConnectionHandler(mContext,
                mServiceIntent);

        try {
            // First kill the process to start out in a stable state.
            controller.ensureProcessGone(WAIT_TIME);

            // Do initial setup.
            controller.denyBackgroundOp(WAIT_TIME);
            controller.makeUidIdle();
            controller.removeFromWhitelist();

            // We will use this to monitor when the service is running.
            conn.startMonitoring();

            // Try starting activity that will start the service.  This should be okay.
            WaitForBroadcast waiter = new WaitForBroadcast(mInstrumentation.getTargetContext());
            waiter.prepare(ACTION_SIMPLE_ACTIVITY_START_SERVICE_RESULT);
            activityIntent.putExtra("service", mServiceIntent);
            mContext.startActivity(activityIntent);
            Intent resultIntent = waiter.doWait(WAIT_TIME);
            int brCode = resultIntent.getIntExtra("result", Activity.RESULT_CANCELED);
            if (brCode != Activity.RESULT_FIRST_USER) {
                fail("Failed starting service, result=" + brCode);
            }
            conn.waitForConnect(WAIT_TIME);

            final String expectedActivityState = (isScreenInteractive() && !isKeyguardLocked())
                    ? "TOP" : "TPSL";
            // Also make sure the uid state reports are as expected.
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_ACTIVE, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE,
                    expectedActivityState, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "SVC", WAIT_TIME);

            // Okay, bring down the service.
            mContext.stopService(mServiceIntent);
            conn.waitForDisconnect(WAIT_TIME);

            controller.getUidWatcher().expect(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // App isn't yet idle, so we should be able to start the service again.
            mContext.startService(mServiceIntent);
            conn.waitForConnect(WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_UNCACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "SVC", WAIT_TIME);

            // And now fast-forward to the app going idle, service should be stopped.
            controller.makeUidIdle();
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_IDLE, null, WAIT_TIME);

            conn.waitForDisconnect(WAIT_TIME);
            controller.getUidWatcher().waitFor(WatchUidRunner.CMD_CACHED, null, WAIT_TIME);
            controller.getUidWatcher().expect(WatchUidRunner.CMD_PROCSTATE, "CEM", WAIT_TIME);

            // No longer should be able to start service.
            boolean failed = false;
            try {
                mContext.startService(mServiceIntent);
            } catch (IllegalStateException e) {
                failed = true;
            }
            if (!failed) {
                fail("Service was allowed to start while in the background");
            }

        } finally {
            mContext.stopService(mServiceIntent);
            conn.stopMonitoringIfNeeded();
            controller.cleanup();
        }
    }

    private boolean isScreenInteractive() {
        final PowerManager powerManager =
                (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        return powerManager.isInteractive();
    }

    private boolean isKeyguardLocked() {
        final KeyguardManager keyguardManager =
                (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return keyguardManager.isKeyguardLocked();
    }
}
