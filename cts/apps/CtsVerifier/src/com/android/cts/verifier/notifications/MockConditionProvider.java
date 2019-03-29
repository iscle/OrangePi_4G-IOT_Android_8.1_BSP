/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.cts.verifier.notifications;


import android.app.Activity;
import android.app.AutomaticZenRule;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.Condition;
import android.service.notification.ConditionProviderService;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MockConditionProvider extends ConditionProviderService {
    static final String TAG = "MockConditionProvider";

    static final String PACKAGE_NAME = "com.android.cts.verifier.notifications";
    static final String PATH = "mock_cp";
    static final String QUERY = "query_item";

    static final String SERVICE_BASE = "android.service.notification.cts.MockConditionProvider.";
    static final String SERVICE_CHECK = SERVICE_BASE + "SERVICE_CHECK";
    static final String SERVICE_RESET = SERVICE_BASE + "SERVICE_RESET";
    static final String SERVICE_SUBSCRIBE = SERVICE_BASE + "SERVICE_SUBSCRIBE";

    static final String EXTRA_PAYLOAD = "PAYLOAD";
    static final String EXTRA_INT = "INT";
    static final String EXTRA_BOOLEAN = "BOOLEAN";
    static final String EXTRA_TAG = "TAG";
    static final String EXTRA_CODE = "CODE";

    static final int RESULT_NO_SERVER = Activity.RESULT_FIRST_USER + 1;


    private ArrayList<Uri> mSubscriptions = new ArrayList<>();
    private boolean mConnected = false;
    private BroadcastReceiver mReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "created");

        mSubscriptions = new ArrayList<Uri>();

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (SERVICE_CHECK.equals(action)) {
                    Log.d(TAG, "SERVICE_CHECK");
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(EXTRA_BOOLEAN, mConnected);
                    setResultExtras(bundle);
                    setResultCode(Activity.RESULT_OK);
                } else if (SERVICE_SUBSCRIBE.equals(action)) {
                    Log.d(TAG, "SERVICE_SUBSCRIBE");
                    Bundle bundle = new Bundle();
                    bundle.putParcelableArrayList(EXTRA_PAYLOAD, mSubscriptions);
                    setResultExtras(bundle);
                    setResultCode(Activity.RESULT_OK);
                } else if (SERVICE_RESET.equals(action)) {
                    Log.d(TAG, "SERVICE_RESET");
                    resetData();
                } else {
                    Log.w(TAG, "unknown action");
                    setResultCode(Activity.RESULT_CANCELED);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(SERVICE_CHECK);
        filter.addAction(SERVICE_SUBSCRIBE);
        filter.addAction(SERVICE_RESET);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mConnected = false;
        unregisterReceiver(mReceiver);
        mReceiver = null;
        Log.d(TAG, "destroyed");
    }

    public void resetData() {
        mSubscriptions.clear();
    }

    public static void resetData(Context context) {
        sendCommand(context, SERVICE_RESET, null, 0);
    }

    public static void probeConnected(Context context, BooleanResultCatcher catcher) {
        requestConnected(context, SERVICE_CHECK, catcher);
    }

    public static void probeSubscribe(Context context, ParcelableListResultCatcher catcher) {
        requestParcelableListResult(context, SERVICE_SUBSCRIBE, catcher);
    }

    private static void sendCommand(Context context, String action, String tag, int code) {
        Intent broadcast = new Intent(action);
        if (tag != null) {
            broadcast.putExtra(EXTRA_TAG, tag);
            broadcast.putExtra(EXTRA_CODE, code);
        }
        context.sendBroadcast(broadcast);
    }

    public static Uri toConditionId(String queryValue) {
        return new Uri.Builder().scheme("scheme")
                .appendPath(PATH)
                .appendQueryParameter(QUERY, queryValue)
                .build();
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "connected");
        mConnected = true;
    }

    @Override
    public void onSubscribe(Uri conditionId) {
        Log.d(TAG, "subscribed to " + conditionId);
        mSubscriptions.add(conditionId);
    }

    @Override
    public void onUnsubscribe(Uri conditionId) {
        Log.d(TAG, "unsubscribed from " + conditionId);
        mSubscriptions.remove(conditionId);
    }

    public abstract static class BooleanResultCatcher extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            accept(getResultExtras(true).getBoolean(EXTRA_BOOLEAN, false));
        }

        abstract public void accept(boolean result);
    }

    private static void requestConnected(Context context, String action,
            BooleanResultCatcher catcher) {
        Intent broadcast = new Intent(action);
        context.sendOrderedBroadcast(broadcast, null, catcher, null, RESULT_NO_SERVER, null, null);
    }

    public abstract static class ParcelableListResultCatcher extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            accept(getResultExtras(true).getParcelableArrayList(EXTRA_PAYLOAD));
        }

        abstract public void accept(List<Parcelable> result);
    }

    private static void requestParcelableListResult(Context context, String action,
            ParcelableListResultCatcher catcher) {
        Intent broadcast = new Intent(action);
        context.sendOrderedBroadcast(broadcast, null, catcher, null, RESULT_NO_SERVER, null, null);
    }
}
