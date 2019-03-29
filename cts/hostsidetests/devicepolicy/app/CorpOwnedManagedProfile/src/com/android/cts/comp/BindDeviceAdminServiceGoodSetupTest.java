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

package com.android.cts.comp;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.util.Log;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Testing various scenarios when a profile owner / device owner tries to bind a service
 * in the other profile, and everything is setup correctly.
 */
public class BindDeviceAdminServiceGoodSetupTest extends AndroidTestCase {

    private static final String TAG = "BindDeviceAdminTest";

    private static final String NON_MANAGING_PACKAGE = AdminReceiver.COMP_DPC_2_PACKAGE_NAME;
    private static final ServiceConnection EMPTY_SERVICE_CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };
    private static final IInterface NOT_IN_MAIN_THREAD_POISON_PILL = () -> null;

    private DevicePolicyManager mDpm;
    private List<UserHandle> mTargetUsers;

    @Override
    public void setUp() {
        mDpm = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        assertEquals(AdminReceiver.COMP_DPC_PACKAGE_NAME, mContext.getPackageName());

        mTargetUsers = mDpm.getBindDeviceAdminTargetUsers(AdminReceiver.getComponentName(mContext));
        assertTrue("No target users found", mTargetUsers.size() > 0);
    }

    public void testOnlyDeviceOwnerCanHaveMoreThanOneTargetUser() {
        if (!mDpm.isDeviceOwnerApp(AdminReceiver.getComponentName(mContext).getPackageName())) {
            assertEquals(1, mTargetUsers.size());
        }
    }

    /**
     * If the intent is implicit, expected to throw {@link IllegalArgumentException}.
     */
    public void testCannotBind_implicitIntent() throws Exception {
        final Intent implicitIntent = new Intent(Intent.ACTION_VIEW);
        for (UserHandle targetUser : mTargetUsers) {
            try {
                bind(implicitIntent, EMPTY_SERVICE_CONNECTION, targetUser);
                fail("IllegalArgumentException should be thrown for target user " + targetUser);
            } catch (IllegalArgumentException ex) {
                MoreAsserts.assertContainsRegex("Service intent must be explicit", ex.getMessage());
            }
        }
    }

    /**
     * If the intent is not resolvable, it should return {@code null}.
     */
    public void testCannotBind_notResolvableIntent() throws Exception {
        final Intent notResolvableIntent = new Intent();
        notResolvableIntent.setClassName(mContext, "NotExistService");
        for (UserHandle targetUser : mTargetUsers) {
            assertFalse("Should not be allowed to bind to target user " + targetUser,
                    bind(notResolvableIntent, EMPTY_SERVICE_CONNECTION, targetUser));
        }
    }

    /**
     * Make sure we cannot bind unprotected service.
     */
    public void testCannotBind_unprotectedCrossUserService() throws Exception {
        final Intent serviceIntent = new Intent(mContext, UnprotectedCrossUserService.class);
        for (UserHandle targetUser : mTargetUsers) {
            try {
                bind(serviceIntent, EMPTY_SERVICE_CONNECTION, targetUser);
                fail("SecurityException should be thrown for target user " + targetUser);
            } catch (SecurityException ex) {
                MoreAsserts.assertContainsRegex(
                        "must be protected by BIND_DEVICE_ADMIN", ex.getMessage());
            }
        }
    }

    /**
     * Talk to a DPC package that is neither device owner nor profile owner.
     */
    public void testCheckCannotBind_nonManagingPackage() throws Exception {
        final Intent serviceIntent = new Intent();
        serviceIntent.setClassName(NON_MANAGING_PACKAGE, ProtectedCrossUserService.class.getName());
        for (UserHandle targetUser : mTargetUsers) {
            try {
                bind(serviceIntent, EMPTY_SERVICE_CONNECTION, targetUser);
                fail("SecurityException should be thrown for target user " + targetUser);
            } catch (SecurityException ex) {
                MoreAsserts.assertContainsRegex("Only allow to bind service", ex.getMessage());
            }
        }
    }

    /**
     * Talk to the same DPC in same user, that is talking to itself.
     */
    public void testCannotBind_sameUser() throws Exception {
        try {
            final Intent serviceIntent = new Intent(mContext, ProtectedCrossUserService.class);
            bind(serviceIntent, EMPTY_SERVICE_CONNECTION, Process.myUserHandle());
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException ex) {
            MoreAsserts.assertContainsRegex("target user id must be different", ex.getMessage());
        }
    }

    /**
     * Send a String to other side and expect the exact same string is echoed back.
     */
    public void testCrossProfileCall_echo() throws Exception {
        final String ANSWER = "42";
        for (UserHandle targetUser : mTargetUsers) {
            assertCrossProfileCall(ANSWER, service -> service.echo(ANSWER), targetUser);
        }
    }

    /**
     * Make sure we are talking to the target user.
     */
    public void testCrossProfileCall_getUserHandle() throws Exception {
        for (UserHandle targetUser : mTargetUsers) {
            assertCrossProfileCall(targetUser, service -> service.getUserHandle(), targetUser);
        }
    }

    /**
     * Convenient method for you to execute a cross user call and assert the return value of it.
     * @param expected The expected result of the cross user call.
     * @param callable It is called when the service is bound, use this to make the service call.
     * @param targetUserHandle Which user are we talking to.
     * @param <T> The return type of the service call.
     */
    private <T> void assertCrossProfileCall(
            T expected, CrossUserCallable<T> callable, UserHandle targetUserHandle)
            throws Exception {
        final LinkedBlockingQueue<IInterface> queue = new LinkedBlockingQueue<>();
        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.d(TAG, "onServiceConnected is called in " + Thread.currentThread().getName());
                // Ensure onServiceConnected is running in main thread.
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    // Not running in main thread, failed the test.
                    Log.e(TAG, "onServiceConnected is not running in main thread!");
                    queue.add(NOT_IN_MAIN_THREAD_POISON_PILL);
                    return;
                }
                queue.add(ICrossUserService.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected is called");
            }
        };
        final Intent serviceIntent = new Intent(mContext, ProtectedCrossUserService.class);
        assertTrue(bind(serviceIntent, serviceConnection, targetUserHandle));
        IInterface service = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull("binding to the target service timed out", service);
        try {
            if (NOT_IN_MAIN_THREAD_POISON_PILL.equals(service)) {
                fail("onServiceConnected should be called in main thread");
            }
            ICrossUserService crossUserService = (ICrossUserService) service;
            assertEquals(expected, callable.call(crossUserService));
        } finally {
            mContext.unbindService(serviceConnection);
        }
    }

    private boolean bind(Intent serviceIntent, ServiceConnection serviceConnection,
            UserHandle userHandle) {
        return mDpm.bindDeviceAdminServiceAsUser(AdminReceiver.getComponentName(mContext),
                serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE, userHandle);
    }

    interface CrossUserCallable<T> {
        T call(ICrossUserService service) throws RemoteException;
    }
}
