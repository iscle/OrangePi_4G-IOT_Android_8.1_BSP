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

package android.externalservice.cts;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.test.AndroidTestCase;
import android.util.Log;

import android.externalservice.common.ServiceMessages;

public class ExternalServiceTest extends AndroidTestCase {
    private static final String TAG = "ExternalServiceTest";

    static final String sServicePackage = "android.externalservice.service";

    private Connection mConnection = new Connection();

    private ConditionVariable mCondition = new ConditionVariable(false);

    static final int CONDITION_TIMEOUT = 10 * 1000 /* 10 seconds */;

    public void tearDown() {
        if (mConnection.service != null)
            getContext().unbindService(mConnection);
    }

    /** Tests that an isolatedProcess service cannot be bound to by an external package. */
    public void testFailBindIsolated() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".IsolatedService"));
        try {
            getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            fail("Should not be able to bind to non-exported, non-external service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE does not work with plain isolatedProcess services. */
    public void testFailBindExternalIsolated() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".IsolatedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-exported, non-external service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE does not work with exported, isolatedProcess services (
     * requires externalService as well). */
    public void testFailBindExternalExported() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExportedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-external service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE requires that an externalService be exported. */
    public void testFailBindExternalNonExported() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage, sServicePackage+".ExternalNonExportedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-exported service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that BIND_EXTERNAL_SERVICE requires the service be an isolatedProcess. */
    public void testFailBindExternalNonIsolated() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage, sServicePackage+".ExternalNonIsolatedService"));
        try {
            getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE);
            fail("Should not be able to BIND_EXTERNAL_SERVICE to non-isolated service");
        } catch (SecurityException e) {
        }
    }

    /** Tests that an externalService can only be bound with BIND_EXTERNAL_SERVICE. */
    public void testFailBindWithoutBindExternal() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));
        try {
            getContext().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            fail("Should not be able to bind to an external service without BIND_EXTERNAL_SERVICE");
        } catch (SecurityException e) {
        }
    }

    /** Tests that an external service can be bound, and that it runs as a different principal. */
    public void testBindExternalService() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));

        mCondition.close();
        assertTrue(getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        assertEquals(getContext().getPackageName(), mConnection.name.getPackageName());
        assertNotSame(sServicePackage, mConnection.name.getPackageName());

        // Check the identity of the service.
        Messenger remote = new Messenger(mConnection.service);
        ServiceIdentity id = identifyService(remote);
        assertNotNull(id);

        assertFalse(id.uid == 0 || id.pid == 0);
        assertNotEquals(Process.myUid(), id.uid);
        assertNotEquals(Process.myPid(), id.pid);
        assertEquals(getContext().getPackageName(), id.packageName);
    }

    /** Tests that the APK providing the externalService can bind the service itself, and that
     * other APKs bind to a different instance of it. */
    public void testBindExternalServiceWithRunningOwn() {
        // Start the service that will create the externalService.
        final Connection creatorConnection = new Connection();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ServiceCreator"));

        mCondition.close();
        assertTrue(getContext().bindService(intent, creatorConnection, Context.BIND_AUTO_CREATE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Get the identity of the creator.
        Messenger remoteCreator = new Messenger(creatorConnection.service);
        ServiceIdentity creatorId = identifyService(remoteCreator);
        assertNotNull(creatorId);
        assertFalse(creatorId.uid == 0 || creatorId.pid == 0);

        // Have the creator actually start its service.
        final Message creatorMsg =
                Message.obtain(null, ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE);
        Handler creatorHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "Received message: " + msg);
                switch (msg.what) {
                    case ServiceMessages.MSG_CREATE_EXTERNAL_SERVICE_RESPONSE:
                        creatorMsg.copyFrom(msg);
                        mCondition.open();
                        break;
                }
                super.handleMessage(msg);
            }
        };
        Messenger localCreator = new Messenger(creatorHandler);
        creatorMsg.replyTo = localCreator;
        try {
            mCondition.close();
            remoteCreator.send(creatorMsg);
        } catch (RemoteException e) {
            fail("Unexpected remote exception" + e);
            return;
        }
        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        // Get the connection to the creator's service.
        assertNotNull(creatorMsg.obj);
        Messenger remoteCreatorService = (Messenger) creatorMsg.obj;
        ServiceIdentity creatorServiceId = identifyService(remoteCreatorService);
        assertNotNull(creatorServiceId);
        assertFalse(creatorServiceId.uid == 0 || creatorId.pid == 0);

        // Create an external service from this (the test) process.
        intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));

        mCondition.close();
        assertTrue(getContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));
        assertTrue(mCondition.block(CONDITION_TIMEOUT));
        ServiceIdentity serviceId = identifyService(new Messenger(mConnection.service));
        assertNotNull(serviceId);
        assertFalse(serviceId.uid == 0 || serviceId.pid == 0);

        // Make sure that all the processes are unique.
        int myUid = Process.myUid();
        int myPid = Process.myPid();
        String myPkg = getContext().getPackageName();

        assertNotEquals(myUid, creatorId.uid);
        assertNotEquals(myUid, creatorServiceId.uid);
        assertNotEquals(myUid, serviceId.uid);
        assertNotEquals(myPid, creatorId.pid);
        assertNotEquals(myPid, creatorServiceId.pid);
        assertNotEquals(myPid, serviceId.pid);

        assertNotEquals(creatorId.uid, creatorServiceId.uid);
        assertNotEquals(creatorId.uid, serviceId.uid);
        assertNotEquals(creatorId.pid, creatorServiceId.pid);
        assertNotEquals(creatorId.pid, serviceId.pid);

        assertNotEquals(creatorServiceId.uid, serviceId.uid);
        assertNotEquals(creatorServiceId.pid, serviceId.pid);

        assertNotEquals(myPkg, creatorId.packageName);
        assertNotEquals(myPkg, creatorServiceId.packageName);
        assertEquals(creatorId.packageName, creatorServiceId.packageName);
        assertEquals(myPkg, serviceId.packageName);

        getContext().unbindService(creatorConnection);
    }

    /** Tests that the binding to an externalService can be changed. */
    public void testBindExternalAboveClient() {
        // Start the service and wait for connection.
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage, sServicePackage+".ExternalService"));

        mCondition.close();
        Connection initialConn = new Connection();
        assertTrue(getContext().bindService(intent, initialConn,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        ServiceIdentity idOne = identifyService(new Messenger(initialConn.service));
        assertNotNull(idOne);
        assertFalse(idOne.uid == 0 || idOne.pid == 0);

        // Bind the service with a different priority.
        mCondition.close();
        Connection prioConn = new Connection();
        assertTrue(getContext().bindService(intent, prioConn,
                    Context.BIND_AUTO_CREATE | Context.BIND_EXTERNAL_SERVICE |
                            Context.BIND_ABOVE_CLIENT));

        assertTrue(mCondition.block(CONDITION_TIMEOUT));

        ServiceIdentity idTwo = identifyService(new Messenger(prioConn.service));
        assertNotNull(idTwo);
        assertFalse(idTwo.uid == 0 || idTwo.pid == 0);

        assertEquals(idOne.uid, idTwo.uid);
        assertEquals(idOne.pid, idTwo.pid);
        assertEquals(idOne.packageName, idTwo.packageName);
        assertNotEquals(Process.myUid(), idOne.uid);
        assertNotEquals(Process.myPid(), idOne.pid);
        assertEquals(getContext().getPackageName(), idOne.packageName);

        getContext().unbindService(prioConn);
        getContext().unbindService(initialConn);
    }

    /** Contains information about the security principal of a Service. */
    private static class ServiceIdentity {
        int uid;
        int pid;
        String packageName;
    }

    /** Given a Messenger, this will message the service to retrieve its UID, PID, and package name.
     * On success, returns a ServiceIdentity. On failure, returns null. */
    private ServiceIdentity identifyService(Messenger service) {
        final ServiceIdentity id = new ServiceIdentity();
        Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "Received message: " + msg);
                switch (msg.what) {
                    case ServiceMessages.MSG_IDENTIFY_RESPONSE:
                        id.uid = msg.arg1;
                        id.pid = msg.arg2;
                        id.packageName = msg.getData().getString(ServiceMessages.IDENTIFY_PACKAGE);
                        mCondition.open();
                        break;
                }
                super.handleMessage(msg);
            }
        };
        Messenger local = new Messenger(handler);

        Message msg = Message.obtain(null, ServiceMessages.MSG_IDENTIFY);
        msg.replyTo = local;
        try {
            mCondition.close();
            service.send(msg);
        } catch (RemoteException e) {
            fail("Unexpected remote exception: " + e);
            return null;
        }

        if (!mCondition.block(CONDITION_TIMEOUT))
            return null;
        return id;
    }

    private class Connection implements ServiceConnection {
        IBinder service = null;
        ComponentName name = null;
        boolean disconnected = false;

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected " + name);
            this.service = service;
            this.name = name;
            mCondition.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
        }
    }

    private <T> void assertNotEquals(T expected, T actual) {
        assertFalse("Expected <" + expected + "> should not be equal to actual <" + actual + ">",
                expected.equals(actual));
    }
}
