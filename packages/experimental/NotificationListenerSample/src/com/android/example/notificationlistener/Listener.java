/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.example.notificationlistener;


import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.Ranking;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Listener extends NotificationListenerService {
    private static final String TAG = "SampleListener";

    // Message tags
    private static final int MSG_NOTIFY = 1;
    private static final int MSG_CANCEL = 2;
    private static final int MSG_STARTUP = 3;
    private static final int MSG_ORDER = 4;
    private static final int MSG_DISMISS = 5;
    private static final int MSG_LAUNCH = 6;
    private static final int MSG_SNOOZE = 7;

    static final String ACTION_DISMISS = "com.android.example.notificationlistener.DISMISS";
    static final String ACTION_LAUNCH = "com.android.example.notificationlistener.LAUNCH";
    static final String ACTION_REFRESH = "com.android.example.notificationlistener.REFRESH";
    static final String ACTION_STATE_CHANGE = "com.android.example.notificationlistener.STATE";
    static final String EXTRA_KEY = "key";

    private static ArrayList<StatusBarNotification> sNotifications;
    private static boolean sConnected;

    public static List<StatusBarNotification> getNotifications() {
        return sNotifications;
    }

    public static boolean isConnected() {
        return sConnected;
    }

    public static void toggleSnooze(Context context) {
        if (sConnected) {
            Log.d(TAG, "scheduling snooze");
            if (sHandler != null) {
                sHandler.sendEmptyMessage(MSG_SNOOZE);
            }
        } else {
            Log.d(TAG, "trying to unsnooze");
            try {
                NotificationListenerService.requestRebind(
                        ComponentName.createRelative(context.getPackageName(),
                                Listener.class.getCanonicalName()));
            } catch (Exception e) {
                Log.e(TAG, "failed to rebind service", e);
            }
        }
    }

    private final Ranking mTmpRanking = new Ranking();

    private static Handler sHandler;

    private RankingMap mRankingMap;

    private class Delta {
        final StatusBarNotification mSbn;
        final RankingMap mRankingMap;

        public Delta(StatusBarNotification sbn, RankingMap rankingMap) {
            mSbn = sbn;
            mRankingMap = rankingMap;
        }
    }

    private final Comparator<StatusBarNotification> mRankingComparator =
            new Comparator<StatusBarNotification>() {

                private final Ranking mLhsRanking = new Ranking();
                private final Ranking mRhsRanking = new Ranking();

                @Override
                public int compare(StatusBarNotification lhs, StatusBarNotification rhs) {
                    mRankingMap.getRanking(lhs.getKey(), mLhsRanking);
                    mRankingMap.getRanking(rhs.getKey(), mRhsRanking);
                    return Integer.compare(mLhsRanking.getRank(), mRhsRanking.getRank());
                }
            };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String key = intent.getStringExtra(EXTRA_KEY);
            int what = MSG_DISMISS;
            if (ACTION_LAUNCH.equals(intent.getAction())) {
                what = MSG_LAUNCH;
            }
            Log.d(TAG, "received an action broadcast " + intent.getAction());
            if (!TextUtils.isEmpty(key)) {
                Log.d(TAG, "  on " + key);
                Message.obtain(sHandler, what, key).sendToTarget();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                Delta delta = null;
                if (msg.obj instanceof Delta) {
                    delta = (Delta) msg.obj;
                }

                switch (msg.what) {
                    case MSG_NOTIFY:
                        Log.i(TAG, "notify: " + delta.mSbn.getKey());
                        synchronized (sNotifications) {
                            boolean exists = mRankingMap.getRanking(delta.mSbn.getKey(), mTmpRanking);
                            if (!exists) {
                                sNotifications.add(delta.mSbn);
                            } else {
                                int position = mTmpRanking.getRank();
                                sNotifications.set(position, delta.mSbn);
                            }
                            mRankingMap = delta.mRankingMap;
                            Collections.sort(sNotifications, mRankingComparator);
                            Log.i(TAG, "finish with: " + sNotifications.size());
                        }
                        LocalBroadcastManager.getInstance(Listener.this)
                                .sendBroadcast(new Intent(ACTION_REFRESH)
                                        .putExtra(EXTRA_KEY, delta.mSbn.getKey()));
                        break;

                    case MSG_CANCEL:
                        final String cancelKey = delta.mSbn.getKey();
                        Log.i(TAG, "remove: " + cancelKey);
                        synchronized (sNotifications) {
                            boolean exists = mRankingMap.getRanking(cancelKey, mTmpRanking);
                            if (exists) {
                                sNotifications.remove(mTmpRanking.getRank());
                            }
                            mRankingMap = delta.mRankingMap;
                            Collections.sort(sNotifications, mRankingComparator);
                        }
                        LocalBroadcastManager.getInstance(Listener.this)
                                .sendBroadcast(new Intent(ACTION_REFRESH)
                                        .putExtra(EXTRA_KEY, cancelKey));
                        break;

                    case MSG_ORDER:
                        Log.i(TAG, "reorder");
                        synchronized (sNotifications) {
                            mRankingMap = delta.mRankingMap;
                            Collections.sort(sNotifications, mRankingComparator);
                        }
                        LocalBroadcastManager.getInstance(Listener.this)
                                .sendBroadcast(new Intent(ACTION_REFRESH));
                        break;

                    case MSG_STARTUP:
                        sConnected = true;
                        fetchActive();
                        Log.i(TAG, "start with: " + sNotifications.size() + " notifications.");
                        LocalBroadcastManager.getInstance(Listener.this)
                                .sendBroadcast(new Intent(ACTION_REFRESH));
                        LocalBroadcastManager.getInstance(Listener.this)
                                .sendBroadcast(new Intent(ACTION_STATE_CHANGE));
                        break;

                    case MSG_DISMISS:
                        if (msg.obj instanceof String) {
                            final String key = (String) msg.obj;
                            mRankingMap.getRanking(key, mTmpRanking);
                            StatusBarNotification sbn = sNotifications.get(mTmpRanking.getRank());
                            if ((sbn.getNotification().flags & Notification.FLAG_AUTO_CANCEL) != 0 &&
                                    sbn.getNotification().contentIntent != null) {
                                try {
                                    sbn.getNotification().contentIntent.send();
                                } catch (PendingIntent.CanceledException e) {
                                    Log.d(TAG, "failed to send intent for " + sbn.getKey(), e);
                                }
                            }
                            cancelNotification(key);
                        }
                        break;

                    case MSG_LAUNCH:
                        if (msg.obj instanceof String) {
                            final String key = (String) msg.obj;
                            mRankingMap.getRanking(key, mTmpRanking);
                            StatusBarNotification sbn = sNotifications.get(mTmpRanking.getRank());
                            if (sbn.getNotification().contentIntent != null) {
                                try {
                                    sbn.getNotification().contentIntent.send();
                                } catch (PendingIntent.CanceledException e) {
                                    Log.d(TAG, "failed to send intent for " + sbn.getKey(), e);
                                }
                            }
                            if ((sbn.getNotification().flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                                cancelNotification(key);
                            }
                        }
                        break;

                    case MSG_SNOOZE:
                        Log.d(TAG, "trying to snooze");
                        try {
                            requestUnbind();
                        } catch (Exception e) {
                            Log.e(TAG, "failed to unbind service", e);
                        }
                        break;
                }
            }
        };
        Log.d(TAG, "registering broadcast listener");
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_DISMISS);
        intentFilter.addAction(ACTION_LAUNCH);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        sConnected = false;
        LocalBroadcastManager.getInstance(Listener.this)
                .sendBroadcast(new Intent(ACTION_STATE_CHANGE));
        sHandler = null;
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        Log.w(TAG, "onListenerConnected: ");
        Message.obtain(sHandler, MSG_STARTUP).sendToTarget();
    }

    @Override
    public void onListenerDisconnected() {
        Log.w(TAG, "onListenerDisconnected: ");
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        Log.w(TAG, "onNotificationRankingUpdate");
        Message.obtain(sHandler, MSG_ORDER,
                new Delta(null, rankingMap)).sendToTarget();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.w(TAG, "onNotificationPosted: " + sbn.getKey());
        Message.obtain(sHandler, MSG_NOTIFY,
                new Delta(sbn, rankingMap)).sendToTarget();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.w(TAG, "onNotificationRemoved: " + sbn.getKey());
        Message.obtain(sHandler, MSG_CANCEL,
                new Delta(sbn, rankingMap)).sendToTarget();
    }

    private void fetchActive() {
        mRankingMap = getCurrentRanking();
        sNotifications = new ArrayList<StatusBarNotification>();
        for (StatusBarNotification sbn : getActiveNotifications()) {
            sNotifications.add(sbn);
            Log.w(TAG, "startup poll: " + sbn.getKey());
        }
        Collections.sort(sNotifications, mRankingComparator);
    }
}
