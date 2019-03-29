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

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.stubs.ActivityTestsBase;
import android.app.stubs.LocalDeniedService;
import android.app.stubs.LocalForegroundService;
import android.app.stubs.LocalGrantedService;
import android.app.stubs.LocalService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.support.test.InstrumentationRegistry;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.app.stubs.R;

import com.android.compatibility.common.util.IBinderParcelable;
import com.android.compatibility.common.util.SystemUtil;

import java.util.List;

public class ServiceTest extends ActivityTestsBase {
    private static final String TAG = "ServiceTest";
    private static final String NOTIFICATION_CHANNEL_ID = TAG;
    private static final int STATE_START_1 = 0;
    private static final int STATE_START_2 = 1;
    private static final int STATE_START_3 = 2;
    private static final int STATE_UNBIND = 3;
    private static final int STATE_DESTROY = 4;
    private static final int STATE_REBIND = 5;
    private static final int STATE_UNBIND_ONLY = 6;
    private static final int DELAY = 5000;
    private static final
        String EXIST_CONN_TO_RECEIVE_SERVICE = "existing connection to receive service";
    private static final String EXIST_CONN_TO_LOSE_SERVICE = "existing connection to lose service";
    private static final String EXTERNAL_SERVICE_PACKAGE = "com.android.app2";
    private static final String EXTERNAL_SERVICE_COMPONENT =
            EXTERNAL_SERVICE_PACKAGE + "/android.app.stubs.LocalService";
    private int mExpectedServiceState;
    private Context mContext;
    private Intent mLocalService;
    private Intent mLocalDeniedService;
    private Intent mLocalForegroundService;
    private Intent mLocalGrantedService;
    private Intent mLocalService_ApplicationHasPermission;
    private Intent mLocalService_ApplicationDoesNotHavePermission;
    private Intent mExternalService;

    private IBinder mStateReceiver;

    private static class EmptyConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    private class TestConnection implements ServiceConnection {
        private final boolean mExpectDisconnect;
        private final boolean mSetReporter;
        private boolean mMonitor;
        private int mCount;

        public TestConnection(boolean expectDisconnect, boolean setReporter) {
            mExpectDisconnect = expectDisconnect;
            mSetReporter = setReporter;
            mMonitor = !setReporter;
        }

        void setMonitor(boolean v) {
            mMonitor = v;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mSetReporter) {
                Parcel data = Parcel.obtain();
                data.writeInterfaceToken(LocalService.SERVICE_LOCAL);
                data.writeStrongBinder(mStateReceiver);
                try {
                    service.transact(LocalService.SET_REPORTER_CODE, data, null, 0);
                } catch (RemoteException e) {
                    finishBad("DeadObjectException when sending reporting object");
                }
                data.recycle();
            }

            if (mMonitor) {
                mCount++;
                if (mExpectedServiceState == STATE_START_1) {
                    if (mCount == 1) {
                        finishGood();
                    } else {
                        finishBad("onServiceConnected() again on an object when it "
                                + "should have been the first time");
                    }
                } else if (mExpectedServiceState == STATE_START_2) {
                    if (mCount == 2) {
                        finishGood();
                    } else {
                        finishBad("onServiceConnected() the first time on an object "
                                + "when it should have been the second time");
                    }
                } else {
                    finishBad("onServiceConnected() called unexpectedly");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (mMonitor) {
                if (mExpectedServiceState == STATE_DESTROY) {
                    if (mExpectDisconnect) {
                        finishGood();
                    } else {
                        finishBad("onServiceDisconnected() when it shouldn't have been");
                    }
                } else {
                    finishBad("onServiceDisconnected() called unexpectedly");
                }
            }
        }
    }

    private void startExpectResult(Intent service) {
        startExpectResult(service, new Bundle());
    }

    private void startExpectResult(Intent service, Bundle bundle) {
        bundle.putParcelable(LocalService.REPORT_OBJ_NAME, new IBinderParcelable(mStateReceiver));

        boolean success = false;
        try {
            mExpectedServiceState = STATE_START_1;
            mContext.startService(new Intent(service).putExtras(bundle));
            waitForResultOrThrow(DELAY, "service to start first time");
            mExpectedServiceState = STATE_START_2;
            mContext.startService(new Intent(service).putExtras(bundle));
            waitForResultOrThrow(DELAY, "service to start second time");
            success = true;
        } finally {
            if (!success) {
                mContext.stopService(service);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(service);
        waitForResultOrThrow(DELAY, "service to be destroyed");
    }

    private NotificationManager getNotificationManager() {
        NotificationManager notificationManager =
                (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        return notificationManager;
    }

    private void sendNotification(int id, String title) {
        Notification notification = new Notification.Builder(getContext(), NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.black)
            .build();
        getNotificationManager().notify(id, notification);
    }

    private void cancelNotification(int id) {
        getNotificationManager().cancel(id);
    }

    private void assertNotification(int id, String expectedTitle) {
        String packageName = getContext().getPackageName();
        String errorMessage = null;
        for (int i = 1; i<=2; i++) {
            errorMessage = null;
            StatusBarNotification[] sbns = getNotificationManager().getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                if (sbn.getId() == id && sbn.getPackageName().equals(packageName)) {
                    String actualTitle =
                            sbn.getNotification().extras.getString(Notification.EXTRA_TITLE);
                    if (expectedTitle.equals(actualTitle)) {
                        return;
                    }
                    // It's possible the notification hasn't been updated yet, so save the error
                    // message to only fail after retrying.
                    errorMessage = String.format("Wrong title for notification #%d: "
                            + "expected '%s', actual '%s'", id, expectedTitle, actualTitle);
                    Log.w(TAG, errorMessage);
                }
            }
            // Notification might not be rendered yet, wait and try again...
            try {
                Thread.sleep(DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (errorMessage != null) {
            fail(errorMessage);
        }
        fail("No notification with id " + id + " for package " + packageName);
    }

    private void assertNoNotification(int id) {
        String packageName = getContext().getPackageName();
        StatusBarNotification found = null;
        for (int i = 1; i<=2; i++) {
            found = null;
            StatusBarNotification[] sbns = getNotificationManager().getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                if (sbn.getId() == id && sbn.getPackageName().equals(packageName)) {
                    found = sbn;
                    break;
                }
            }
            if (found != null) {
                // Notification might not be canceled yet, wait and try again...
                try {
                    Thread.sleep(DELAY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        assertNull("Found notification with id " + id + " for package " + packageName + ": "
                + found, found);
    }

    /**
     * test the service lifecycle, a service can be used in two ways:
     * 1  It can be started and allowed to run until someone stops it or it stops itself.
     *    In this mode, it's started by calling Context.startService()
     *    and stopped by calling Context.stopService().
     *    It can stop itself by calling Service.stopSelf() or Service.stopSelfResult().
     *    Only one stopService() call is needed to stop the service,
     *    no matter how many times startService() was called.
     * 2  It can be operated programmatically using an interface that it defines and exports.
     *    Clients establish a connection to the Service object
     *    and use that connection to call into the service.
     *    The connection is established by calling Context.bindService(),
     *    and is closed by calling Context.unbindService().
     *    Multiple clients can bind to the same service.
     *    If the service has not already been launched, bindService() can optionally launch it.
     */
    private void bindExpectResult(Intent service) {
        TestConnection conn = new TestConnection(true, false);
        TestConnection conn2 = new TestConnection(false, false);
        boolean success = false;
        try {
            // Expect to see the TestConnection connected.
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);

            // Expect to see the second TestConnection connected.
            mContext.bindService(service, conn2, 0);
            waitForResultOrThrow(DELAY, "new connection to receive service");

            mContext.unbindService(conn2);
            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.unbindService(conn2);
                mContext.stopService(service);
            }
        }

        // Expect to see the TestConnection disconnected.
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(service);
        waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);

        conn = new TestConnection(true, true);
        success = false;
        try {
            // Expect to see the TestConnection connected.
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);

            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.stopService(service);
            }
        }

        // Expect to see the service unbind and then destroyed.
        conn.setMonitor(false);
        mExpectedServiceState = STATE_UNBIND;
        mContext.stopService(service);
        waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);

        conn = new TestConnection(true, true);
        success = false;
        try {
            // Expect to see the TestConnection connected.
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(service, conn, 0);
            mContext.startService(service);
            waitForResultOrThrow(DELAY, EXIST_CONN_TO_RECEIVE_SERVICE);

            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
                mContext.stopService(service);
            }
        }

        // Expect to see the service unbind but not destroyed.
        conn.setMonitor(false);
        mExpectedServiceState = STATE_UNBIND_ONLY;
        mContext.unbindService(conn);
        waitForResultOrThrow(DELAY, "existing connection to unbind service");

        // Expect to see the service rebound.
        mExpectedServiceState = STATE_REBIND;
        mContext.bindService(service, conn, 0);
        waitForResultOrThrow(DELAY, "existing connection to rebind service");

        // Expect to see the service unbind and then destroyed.
        mExpectedServiceState = STATE_UNBIND;
        mContext.stopService(service);
        waitForResultOrThrow(DELAY, EXIST_CONN_TO_LOSE_SERVICE);

        mContext.unbindService(conn);
    }

    /**
     * test automatically create the service as long as the binding exists
     * and disconnect from an application service
     */
    private void bindAutoExpectResult(Intent service) {
        TestConnection conn = new TestConnection(false, true);
        boolean success = false;
        try {
            conn.setMonitor(true);
            mExpectedServiceState = STATE_START_1;
            mContext.bindService(
                    service, conn, Context.BIND_AUTO_CREATE);
            waitForResultOrThrow(DELAY, "connection to start and receive service");
            success = true;
        } finally {
            if (!success) {
                mContext.unbindService(conn);
            }
        }
        mExpectedServiceState = STATE_UNBIND;
        mContext.unbindService(conn);
        waitForResultOrThrow(DELAY, "disconnecting from service");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mLocalService = new Intent(mContext, LocalService.class);
        mExternalService = new Intent();
        mExternalService.setComponent(ComponentName.unflattenFromString(EXTERNAL_SERVICE_COMPONENT));
        mLocalForegroundService = new Intent(mContext, LocalForegroundService.class);
        mLocalDeniedService = new Intent(mContext, LocalDeniedService.class);
        mLocalGrantedService = new Intent(mContext, LocalGrantedService.class);
        mLocalService_ApplicationHasPermission = new Intent(
                LocalService.SERVICE_LOCAL_GRANTED, null /*uri*/, mContext, LocalService.class);
        mLocalService_ApplicationDoesNotHavePermission = new Intent(
                LocalService.SERVICE_LOCAL_DENIED, null /*uri*/, mContext, LocalService.class);
        mStateReceiver = new MockBinder();
        getNotificationManager().createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getNotificationManager().deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);
        mContext.stopService(mLocalService);
        mContext.stopService(mLocalForegroundService);
        mContext.stopService(mLocalGrantedService);
        mContext.stopService(mLocalService_ApplicationHasPermission);
        mContext.stopService(mExternalService);
    }

    private class MockBinder extends Binder {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply,
                int flags) throws RemoteException {
            if (code == LocalService.STARTED_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                int count = data.readInt();
                if (mExpectedServiceState == STATE_START_1) {
                    if (count == 1) {
                        finishGood();
                    } else {
                        finishBad("onStart() again on an object when it "
                                + "should have been the first time");
                    }
                } else if (mExpectedServiceState == STATE_START_2) {
                    if (count == 2) {
                        finishGood();
                    } else {
                        finishBad("onStart() the first time on an object when it "
                                + "should have been the second time");
                    }
                } else if (mExpectedServiceState == STATE_START_3) {
                    if (count == 3) {
                        finishGood();
                    } else {
                        finishBad("onStart() the first time on an object when it "
                                + "should have been the third time");
                    }
                } else {
                    finishBad("onStart() was called when not expected (state="
                            + mExpectedServiceState + ")");
                }
                return true;
            } else if (code == LocalService.DESTROYED_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                if (mExpectedServiceState == STATE_DESTROY) {
                    finishGood();
                } else {
                    finishBad("onDestroy() was called when not expected (state="
                            + mExpectedServiceState + ")");
                }
                return true;
            } else if (code == LocalService.UNBIND_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                if (mExpectedServiceState == STATE_UNBIND) {
                    mExpectedServiceState = STATE_DESTROY;
                } else if (mExpectedServiceState == STATE_UNBIND_ONLY) {
                    finishGood();
                } else {
                    finishBad("onUnbind() was called when not expected (state="
                            + mExpectedServiceState + ")");
                }
                return true;
            } else if (code == LocalService.REBIND_CODE) {
                data.enforceInterface(LocalService.SERVICE_LOCAL);
                if (mExpectedServiceState == STATE_REBIND) {
                    finishGood();
                } else {
                    finishBad("onRebind() was called when not expected (state="
                            + mExpectedServiceState + ")");
                }
                return true;
            } else {
                return super.onTransact(code, data, reply, flags);
            }
        }
    }


    public void testLocalStartClass() throws Exception {
        startExpectResult(mLocalService);
    }

    public void testLocalStartAction() throws Exception {
        startExpectResult(new Intent(
                LocalService.SERVICE_LOCAL, null /*uri*/, mContext, LocalService.class));
    }

    public void testLocalBindClass() throws Exception {
        bindExpectResult(mLocalService);
    }

    /* Just the Intent for a foreground service */
    private Intent foregroundServiceIntent(int command) {
        return new Intent(mLocalForegroundService)
                .putExtras(LocalForegroundService.newCommand(mStateReceiver, command));
    }

    private void startForegroundService(int command) {
        mContext.startService(foregroundServiceIntent(command));
    }

    /* Start the service in a way that promises to go into the foreground */
    private void startRequiredForegroundService(int command) {
        mContext.startForegroundService(foregroundServiceIntent(command));
    }

    @MediumTest
    public void testForegroundService_dontRemoveNotificationOnStop() throws Exception {
        boolean success = false;
        try {
            // Start service as foreground - it should show notification #1
            mExpectedServiceState = STATE_START_1;
            startForegroundService(LocalForegroundService.COMMAND_START_FOREGROUND);
            waitForResultOrThrow(DELAY, "service to start first time");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1));

            // Stop foreground without removing notification - it should still show notification #1
            mExpectedServiceState = STATE_START_2;
            startForegroundService(
                    LocalForegroundService.COMMAND_STOP_FOREGROUND_DONT_REMOVE_NOTIFICATION);
            waitForResultOrThrow(DELAY, "service to stop foreground");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1));

            // Sends another notification reusing the same notification id.
            String newTitle = "YODA I AM";
            sendNotification(1, newTitle);
            assertNotification(1, newTitle);

            // Start service as foreground again - it should kill notification #1 and show #2
            mExpectedServiceState = STATE_START_3;
            startForegroundService(LocalForegroundService.COMMAND_START_FOREGROUND);
            waitForResultOrThrow(DELAY, "service to start foreground 2nd time");
            assertNoNotification(1);
            assertNotification(2, LocalForegroundService.getNotificationTitle(2));

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        waitForResultOrThrow(DELAY, "service to be destroyed");
        assertNoNotification(1);
        assertNoNotification(2);
    }

    @MediumTest
    public void testForegroundService_removeNotificationOnStop() throws Exception {
        testForegroundServiceRemoveNotificationOnStop(false);
    }

    @MediumTest
    public void testForegroundService_removeNotificationOnStopUsingFlags() throws Exception {
        testForegroundServiceRemoveNotificationOnStop(true);
    }

    private void testForegroundServiceRemoveNotificationOnStop(boolean usingFlags)
            throws Exception {
        boolean success = false;
        try {
            // Start service as foreground - it should show notification #1
            Log.d(TAG, "Expecting first start state...");
            mExpectedServiceState = STATE_START_1;
            startForegroundService(LocalForegroundService.COMMAND_START_FOREGROUND);
            waitForResultOrThrow(DELAY, "service to start first time");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1));

            // Stop foreground removing notification
            Log.d(TAG, "Expecting second start state...");
            mExpectedServiceState = STATE_START_2;
            if (usingFlags) {
                startForegroundService(LocalForegroundService
                        .COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION_USING_FLAGS);
            } else {
                startForegroundService(LocalForegroundService
                        .COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION);
            }
            waitForResultOrThrow(DELAY, "service to stop foreground");
            assertNoNotification(1);

            // Start service as foreground again - it should show notification #2
            mExpectedServiceState = STATE_START_3;
            startForegroundService(LocalForegroundService.COMMAND_START_FOREGROUND);
            waitForResultOrThrow(DELAY, "service to start as foreground 2nd time");
            assertNotification(2, LocalForegroundService.getNotificationTitle(2));

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        waitForResultOrThrow(DELAY, "service to be destroyed");
        assertNoNotification(1);
        assertNoNotification(2);
    }

    public void testRunningServices() throws Exception {
        final int maxReturnedServices = 10;
        final Bundle bundle = new Bundle();
        bundle.putParcelable(LocalService.REPORT_OBJ_NAME, new IBinderParcelable(mStateReceiver));

        boolean success = false;

        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        // Put target app on whitelist so we can start its services.
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist +" + EXTERNAL_SERVICE_PACKAGE);

        // No services should be reported back at the beginning
        assertEquals(0, am.getRunningServices(maxReturnedServices).size());
        try {
            mExpectedServiceState = STATE_START_1;
            // Start external service.
            mContext.startService(new Intent(mExternalService).putExtras(bundle));
            waitForResultOrThrow(DELAY, "external service to start first time");

            // Ensure we can't see service.
            assertEquals(0, am.getRunningServices(maxReturnedServices).size());

            // Start local service.
            mContext.startService(new Intent(mLocalService).putExtras(bundle));
            waitForResultOrThrow(DELAY, "local service to start first time");
            success = true;

            // Ensure we can see service and it is ours.
            List<ActivityManager.RunningServiceInfo> services = am.getRunningServices(maxReturnedServices);
            assertEquals(1, services.size());
            assertEquals(android.os.Process.myUid(), services.get(0).uid);
        } finally {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "cmd deviceidle whitelist -" + EXTERNAL_SERVICE_PACKAGE);
            if (!success) {
                mContext.stopService(mLocalService);
                mContext.stopService(mExternalService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;

        mContext.stopService(mExternalService);
        waitForResultOrThrow(DELAY, "external service to be destroyed");

        mContext.stopService(mLocalService);
        waitForResultOrThrow(DELAY, "local service to be destroyed");

        // Once our service has stopped, make sure we can't see any services.
        assertEquals(0, am.getRunningServices(maxReturnedServices).size());
    }

    @MediumTest
    public void testForegroundService_detachNotificationOnStop() throws Exception {
        String newTitle = null;
        boolean success = false;
        try {

            // Start service as foreground - it should show notification #1
            mExpectedServiceState = STATE_START_1;
            startForegroundService(LocalForegroundService.COMMAND_START_FOREGROUND);
            waitForResultOrThrow(DELAY, "service to start first time");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1));

            // Detaching notification
            mExpectedServiceState = STATE_START_2;
            startForegroundService(
                    LocalForegroundService.COMMAND_STOP_FOREGROUND_DETACH_NOTIFICATION);
            waitForResultOrThrow(DELAY, "service to stop foreground");
            assertNotification(1, LocalForegroundService.getNotificationTitle(1));

            // Sends another notification reusing the same notification id.
            newTitle = "YODA I AM";
            sendNotification(1, newTitle);
            assertNotification(1, newTitle);

            // Start service as foreground again - it should show notification #2..
            mExpectedServiceState = STATE_START_3;
            startForegroundService(LocalForegroundService.COMMAND_START_FOREGROUND);
            waitForResultOrThrow(DELAY, "service to start as foreground 2nd time");
            assertNotification(2, LocalForegroundService.getNotificationTitle(2));
            //...but keeping notification #1
            assertNotification(1, newTitle);

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }
        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        waitForResultOrThrow(DELAY, "service to be destroyed");
        if (newTitle == null) {
            assertNoNotification(1);
        } else {
            assertNotification(1, newTitle);
            cancelNotification(1);
            assertNoNotification(1);
        }
        assertNoNotification(2);
    }

    class TestSendCallback implements PendingIntent.OnFinished {
        public volatile int result = -1;

        @Override
        public void onSendFinished(PendingIntent pendingIntent, Intent intent, int resultCode,
                String resultData, Bundle resultExtras) {
            Log.i(TAG, "foreground service PendingIntent callback got " + resultCode);
            this.result = resultCode;
        }
    }

    @MediumTest
    public void testForegroundService_pendingIntentForeground() throws Exception {
        boolean success = false;

        PendingIntent pi = PendingIntent.getForegroundService(mContext, 1,
                foregroundServiceIntent(LocalForegroundService.COMMAND_START_FOREGROUND),
                PendingIntent.FLAG_CANCEL_CURRENT);
        TestSendCallback callback = new TestSendCallback();

        try {
            mExpectedServiceState = STATE_START_1;
            pi.send(5038, callback, null);
            waitForResultOrThrow(DELAY, "service to start first time");
            assertTrue(callback.result > -1);

            success = true;
        } finally {
            if (!success) {
                mContext.stopService(mLocalForegroundService);
            }
        }

        mExpectedServiceState = STATE_DESTROY;
        mContext.stopService(mLocalForegroundService);
        waitForResultOrThrow(DELAY, "pendingintent service to be destroyed");
    }

    @MediumTest
    public void testLocalBindAction() throws Exception {
        bindExpectResult(new Intent(
                LocalService.SERVICE_LOCAL, null /*uri*/, mContext, LocalService.class));
    }

    @MediumTest
    public void testLocalBindAutoClass() throws Exception {
        bindAutoExpectResult(mLocalService);
    }

    @MediumTest
    public void testLocalBindAutoAction() throws Exception {
        bindAutoExpectResult(new Intent(
                LocalService.SERVICE_LOCAL, null /*uri*/, mContext, LocalService.class));
    }

    @MediumTest
    public void testLocalStartClassPermissions() throws Exception {
        startExpectResult(mLocalGrantedService);
        startExpectResult(mLocalDeniedService);
    }

    @MediumTest
    public void testLocalStartActionPermissions() throws Exception {
        startExpectResult(mLocalService_ApplicationHasPermission);
        startExpectResult(mLocalService_ApplicationDoesNotHavePermission);
    }

    @MediumTest
    public void testLocalBindClassPermissions() throws Exception {
        bindExpectResult(mLocalGrantedService);
        bindExpectResult(mLocalDeniedService);
    }

    @MediumTest
    public void testLocalBindActionPermissions() throws Exception {
        bindExpectResult(mLocalService_ApplicationHasPermission);
        bindExpectResult(mLocalService_ApplicationDoesNotHavePermission);
    }

    @MediumTest
    public void testLocalBindAutoClassPermissionGranted() throws Exception {
        bindAutoExpectResult(mLocalGrantedService);
    }

    @MediumTest
    public void testLocalBindAutoActionPermissionGranted() throws Exception {
        bindAutoExpectResult(mLocalService_ApplicationHasPermission);
    }

    @MediumTest
    public void testLocalUnbindTwice() throws Exception {
        EmptyConnection conn = new EmptyConnection();
        mContext.bindService(
                mLocalService_ApplicationHasPermission, conn, 0);
        mContext.unbindService(conn);
        try {
            mContext.unbindService(conn);
            fail("No exception thrown on the second unbind");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @MediumTest
    public void testImplicitIntentFailsOnApiLevel21() throws Exception {
        Intent intent = new Intent(LocalService.SERVICE_LOCAL);
        EmptyConnection conn = new EmptyConnection();
        try {
            mContext.bindService(intent, conn, 0);
            mContext.unbindService(conn);
            fail("Implicit intents should be disallowed for apps targeting API 21+");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
