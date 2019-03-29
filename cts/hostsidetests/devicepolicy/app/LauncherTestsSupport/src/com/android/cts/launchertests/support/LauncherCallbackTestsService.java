/*
 * Copyright (C) 2014 The Android Open Sour *
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

package com.android.cts.launchertests.support;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * Service that registers for LauncherApps callbacks.
 *
 * Registering in a service and different process so that
 * device side code can launch it before running client
 * side test code.
 */
public class LauncherCallbackTestsService extends Service {

    public static final String USER_EXTRA = "user_extra";
    public static final String PACKAGE_EXTRA = "package_extra";

    public static final int MSG_RESULT = 0;
    public static final int MSG_CHECK_PACKAGE_ADDED = 1;
    public static final int MSG_CHECK_PACKAGE_REMOVED = 2;
    public static final int MSG_CHECK_PACKAGE_CHANGED = 3;
    public static final int MSG_CHECK_NO_PACKAGE_ADDED = 4;

    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;

    private static final String TAG = "LauncherCallbackTests";

    private static BlockingQueue<Pair<String, UserHandle>> mPackagesAdded
            = new LinkedBlockingQueue();
    private static BlockingQueue<Pair<String, UserHandle>> mPackagesRemoved
            = new LinkedBlockingQueue();
    private static BlockingQueue<Pair<String, UserHandle>> mPackagesChanged
            = new LinkedBlockingQueue();

    private TestCallback mCallback;
    private final Object mCallbackLock = new Object();
    private final Messenger mMessenger = new Messenger(new CheckHandler());
    private final HandlerThread mCallbackThread = new HandlerThread("callback");

    public LauncherCallbackTestsService() {
        mCallbackThread.start();
    }

    class CheckHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Bundle params = null;
            if (msg.obj instanceof Bundle) {
                params = (Bundle) (msg.obj);
            }
            try {
                switch (msg.what) {
                    case MSG_CHECK_PACKAGE_ADDED: {
                        Log.i(TAG, "MSG_CHECK_PACKAGE_ADDED");
                        boolean exists = eventExists(params, mPackagesAdded);
                        teardown();
                        msg.replyTo.send(Message.obtain(null, MSG_RESULT,
                                        exists ? RESULT_PASS : RESULT_FAIL, 0));
                        break;
                    }
                    case MSG_CHECK_PACKAGE_REMOVED: {
                        Log.i(TAG, "MSG_CHECK_PACKAGE_REMOVED");
                        boolean exists = eventExists(params, mPackagesRemoved);
                        teardown();
                        msg.replyTo.send(Message.obtain(null, MSG_RESULT,
                                        exists ? RESULT_PASS : RESULT_FAIL, 0));
                        break;
                    }
                    case MSG_CHECK_PACKAGE_CHANGED: {
                        Log.i(TAG, "MSG_CHECK_PACKAGE_CHANGED");
                        boolean exists = eventExists(params, mPackagesChanged);
                        teardown();
                        msg.replyTo.send(Message.obtain(null, MSG_RESULT,
                                        exists ? RESULT_PASS : RESULT_FAIL, 0));
                        break;
                    }
                    case MSG_CHECK_NO_PACKAGE_ADDED: {
                        Log.i(TAG, "MSG_CHECK_NO_PACKAGE_ADDED");
                        boolean exists = eventExists(params, mPackagesAdded);
                        teardown();
                        msg.replyTo.send(Message.obtain(null, MSG_RESULT,
                                        exists ? RESULT_FAIL : RESULT_PASS, 0));
                        break;
                    }
                    default:
                        super.handleMessage(msg);
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to report test status");
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }
        if ("com.android.cts.launchertests.support.REGISTER_CALLBACK".equals(intent.getAction())) {
            setup();
        }
        return START_STICKY;
    }

    private void setup() {
        LauncherApps launcherApps = (LauncherApps) getSystemService(
                Context.LAUNCHER_APPS_SERVICE);
        synchronized (mCallbackLock) {
            if (mCallback != null) {
                launcherApps.unregisterCallback(mCallback);
            }
            mPackagesAdded.clear();
            mPackagesRemoved.clear();
            mPackagesChanged.clear();
            mCallback = new TestCallback();
            launcherApps.registerCallback(mCallback, new Handler(mCallbackThread.getLooper()));
            Log.i(TAG, "started listening for events");
        }
    }

    private void teardown() {
        LauncherApps launcherApps = (LauncherApps) getSystemService(
                Context.LAUNCHER_APPS_SERVICE);
        synchronized (mCallbackLock) {
            if (mCallback != null) {
                launcherApps.unregisterCallback(mCallback);
                Log.i(TAG, "stopped listening for events");
                mCallback = null;
            }
            mPackagesAdded.clear();
            mPackagesRemoved.clear();
            mPackagesChanged.clear();
        }
    }

    private boolean eventExists(Bundle params, BlockingQueue<Pair<String, UserHandle>> events) {
        UserHandle user = params.getParcelable(USER_EXTRA);
        String packageName = params.getString(PACKAGE_EXTRA);
        Log.i(TAG, "checking for " + packageName + " " + user);
        try {
            Pair<String, UserHandle> event = events.poll(60, TimeUnit.SECONDS);
            while (event != null) {
                if (event.first.equals(packageName) && event.second.equals(user)) {
                    Log.i(TAG, "Event exists " + packageName + " for user " + user);
                    return true;
                }
                event = events.poll(20, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed checking for event", e);
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private class TestCallback extends LauncherApps.Callback {
        public void onPackageRemoved(String packageName, UserHandle user) {
            Log.i(TAG, "package removed event " + packageName + " " + user);
            try {
                mPackagesRemoved.put(new Pair(packageName, user));
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed saving event", e);
            }
        }

        public void onPackageAdded(String packageName, UserHandle user) {
            Log.i(TAG, "package added event " + packageName + " " + user);
            try {
                mPackagesAdded.put(new Pair(packageName, user));
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed saving event", e);
            }
        }

        public void onPackageChanged(String packageName, UserHandle user) {
            Log.i(TAG, "package changed event " + packageName + " " + user);
            try {
                mPackagesChanged.put(new Pair(packageName, user));
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed saving event", e);
            }
        }

        public void onPackagesAvailable(String[] packageNames, UserHandle user,
                boolean replacing) {
        }

        public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                boolean replacing) {
        }
    }
}
