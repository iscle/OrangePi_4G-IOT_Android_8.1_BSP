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

package com.android.cts.widgetprovider;

import android.app.Service;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service that acts as AppWidgetHost that listens to onProvidersChanged callbacks and updates the
 * internally stored list of profile widgets. The service reacts to messages sent from the device
 * side tests and returns whether the expected widget provider is currently present or not.
 */
public class SimpleAppWidgetHostService extends Service {
    private static final String TAG = "SimpleAppWidgetHostService";

    private static final int MSG_RESULT = 0;
    private static final int MSG_PROVIDER_PRESENT = 1;
    private static final int MSG_PROVIDER_UPDATES = 2;

    private static final int RESULT_UNKNOWN = 0;
    private static final int RESULT_PRESENT = 1;
    private static final int RESULT_NOT_PRESENT = 2;
    private static final int RESULT_INTERRUPTED = 3;
    private static final int RESULT_TIMEOUT = 4;

    private static final long GET_PROVIDER_TIMEOUT_MILLIS = 30 * 1000; // 30 seconds

    public static final String USER_EXTRA = "user-extra";
    public static final String PACKAGE_EXTRA = "package-extra";
    public static final String REPLY_EXTRA = "reply-extra";

    private AppWidgetManager mAppWidgetManager;
    private SimpleAppWidgetHost mAppWidgetHost;
    private SimpleAppWidgetHostView mAppWidgetHostView;
    private int mAppWidgetId;
    private Messenger mMessenger;
    private UserHandle mUserHandle;
    private Semaphore mWidgetUpdateSemaphore = new Semaphore(0);
    private RemoteViews mRemoteViews;

    class CheckHandler extends Handler {
        public CheckHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Bundle params = null;
            if (msg.obj instanceof Bundle) {
                params = (Bundle) (msg.obj);
            }
            try {
                switch (msg.what) {
                    case MSG_PROVIDER_PRESENT: {
                        Log.d(TAG, "MSG_PROVIDER_PRESENT");
                        int result = RESULT_UNKNOWN;
                        try {
                            AppWidgetProviderInfo info = mAppWidgetHost.getProvider(params);
                            result = (info != null) ? RESULT_PRESENT : RESULT_NOT_PRESENT;
                            if (info != null) {
                                bindAppWidget(info);
                            }
                        } catch (InterruptedException e) {
                            result = RESULT_INTERRUPTED;
                        }
                        msg.replyTo.send(Message.obtain(null, MSG_RESULT, result
                                , 0 /* not used */));
                        break;
                    }
                    case MSG_PROVIDER_UPDATES: {
                        Log.d(TAG, "MSG_PROVIDER_UPDATES");
                        int result = RESULT_UNKNOWN;
                        try {
                            updateWidgetViaWidgetId();
                            boolean update = waitForUpdate();
                            result = update ? RESULT_PRESENT : RESULT_NOT_PRESENT;
                        } catch (InterruptedException e) {
                            result = RESULT_INTERRUPTED;
                        }
                        msg.replyTo.send(Message.obtain(null, MSG_RESULT, result
                                , 0 /* not used */));
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
        if ("com.android.cts.widgetprovider.REGISTER_CALLBACK".equals(intent.getAction())) {
            mUserHandle = getUserHandleArgument(this, USER_EXTRA, intent);
            Log.d(TAG, "mUserHandle=" + mUserHandle);
            setup();
        }
        return START_STICKY;
    }

    private void setup() {
        HandlerThread handlerThread = new HandlerThread("Widget test callback handler");
        handlerThread.start();
        mMessenger = new Messenger(new CheckHandler(handlerThread.getLooper()));
        mAppWidgetManager = (AppWidgetManager) getSystemService(Context.APPWIDGET_SERVICE);
        mAppWidgetHost = new SimpleAppWidgetHost(this, 0);
        mAppWidgetHost.deleteHost();
        mAppWidgetHost.startListening();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        mAppWidgetHost.stopListening();
        mAppWidgetHost.deleteAppWidgetId(mAppWidgetId);
        mAppWidgetHost.deleteHost();
    }

    private void bindAppWidget(AppWidgetProviderInfo info) {
        mAppWidgetId = mAppWidgetHost.allocateAppWidgetId();
        Log.d(TAG, "Registering app widget with id:" + mAppWidgetId);
        mAppWidgetManager.bindAppWidgetIdIfAllowed(mAppWidgetId, mUserHandle, info.provider, null);
        mAppWidgetHostView = (SimpleAppWidgetHostView) mAppWidgetHost.createView(this,
                mAppWidgetId, info);
        mRemoteViews = new RemoteViews(info.provider.getPackageName(), R.layout.simple_widget);
    }

    private UserHandle getUserHandleArgument(Context context, String key,
            Intent intent) {
        UserManager um = (UserManager) getSystemService(Context.USER_SERVICE);
        int serial = intent.getIntExtra(key, 0);
        Log.d(TAG, "userId=" + serial);
        return um.getUserForSerialNumber(serial);
    }

    private void updateWidgetViaWidgetId() {
        Log.d(TAG, "Forcing widget update via widget id");
        mWidgetUpdateSemaphore.drainPermits();
        // trigger a widget update
        mAppWidgetManager.updateAppWidget(mAppWidgetId, mRemoteViews);
    }

    private boolean waitForUpdate() throws InterruptedException {
        // wait for updateAppWidget to arrive
        return mWidgetUpdateSemaphore.tryAcquire(20, TimeUnit.SECONDS);
    }

    private class SimpleAppWidgetHost extends AppWidgetHost {
        private List<AppWidgetProviderInfo> mProviders;
        private Semaphore mSemaphore = new Semaphore(0);
        public SimpleAppWidgetHost(Context context, int hostId) {
            super(context, hostId);
            synchronized (this) {
                mProviders = mAppWidgetManager.getInstalledProvidersForProfile(mUserHandle);
            }
        }

        @Override
        protected void onProvidersChanged() {
            super.onProvidersChanged();
            Log.d(TAG, "onProvidersChanged callback received");
            synchronized (this) {
                mProviders = mAppWidgetManager.getInstalledProvidersForProfile(mUserHandle);
            }
            mSemaphore.release();
        }

        @Override
        protected AppWidgetHostView onCreateView(Context context, int id, AppWidgetProviderInfo info) {
            return new SimpleAppWidgetHostView(context);
        }

        public AppWidgetProviderInfo getProvider(Bundle params) throws InterruptedException {
            final long startTime = SystemClock.elapsedRealtime();
            long nextTimeout = GET_PROVIDER_TIMEOUT_MILLIS;
            String packageName = params.getString(PACKAGE_EXTRA);
            while ((nextTimeout > 0) && mSemaphore.tryAcquire(nextTimeout, TimeUnit.MILLISECONDS)) {
                mSemaphore.drainPermits();
                Log.d(TAG, "checking for " + packageName + " " + mUserHandle);
                synchronized (this) {
                    for (AppWidgetProviderInfo providerInfo : mProviders) {
                        if (providerInfo.provider.getPackageName().equals(packageName)) {
                            Log.d(TAG, "Provider exists " + packageName
                                    + " for user " + mUserHandle);
                            return providerInfo;
                        }
                    }
                    nextTimeout = startTime + GET_PROVIDER_TIMEOUT_MILLIS
                            - SystemClock.elapsedRealtime();
                }
            }
            return null;
        }
    }

    private class SimpleAppWidgetHostView extends AppWidgetHostView {
        public SimpleAppWidgetHostView(Context context) {
            super(context);
        }

        @Override
        public void updateAppWidget(RemoteViews views) {
            super.updateAppWidget(views);
            Log.d(TAG, "Host view received widget update");
            mWidgetUpdateSemaphore.release();
        }
    }
}
