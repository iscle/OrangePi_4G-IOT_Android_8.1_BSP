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

import android.app.Notification;
import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.ArrayMap;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MockListener extends NotificationListenerService {
    static final String TAG = "MockListener";

    public static final ComponentName COMPONENT_NAME =
            new ComponentName("com.android.cts.verifier", MockListener.class.getName());


    public static final String JSON_FLAGS = "flag";
    public static final String JSON_ICON = "icon";
    public static final String JSON_ID = "id";
    public static final String JSON_PACKAGE = "pkg";
    public static final String JSON_WHEN = "when";
    public static final String JSON_TAG = "tag";
    public static final String JSON_RANK = "rank";
    public static final String JSON_AMBIENT = "ambient";
    public static final String JSON_MATCHES_ZEN_FILTER = "matches_zen_filter";
    public static final String JSON_REASON = "reason";

    ArrayList<String> mPosted = new ArrayList<String>();
    ArrayMap<String, JSONObject> mNotifications = new ArrayMap<>();
    ArrayMap<String, String> mNotificationKeys = new ArrayMap<>();
    ArrayList<String> mRemoved = new ArrayList<String>();
    ArrayMap<String, JSONObject> mRemovedReason = new ArrayMap<>();
    ArrayList<String> mSnoozed = new ArrayList<>();
    ArrayList<String> mOrder = new ArrayList<>();
    Set<String> mTestPackages = new HashSet<>();
    int mDND = -1;
    ArrayList<Notification> mPostedNotifications = new ArrayList<Notification>();
    private static MockListener sNotificationListenerInstance = null;
    boolean isConnected;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "created");

        mTestPackages.add("com.android.cts.verifier");
        mTestPackages.add("com.android.cts.robot");
    }

    protected Collection<JSONObject> getPosted() {
        return mNotifications.values();
    }

    protected String getKeyForTag(String tag) {
        return mNotificationKeys.get(tag);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "destroyed");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        mDND = getCurrentInterruptionFilter();
        Log.d(TAG, "initial value of CurrentInterruptionFilter is " + mDND);
        sNotificationListenerInstance = this;
        isConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        isConnected = false;
    }

    @Override
    public void onInterruptionFilterChanged(int interruptionFilter) {
        super.onInterruptionFilterChanged(interruptionFilter);
        mDND = interruptionFilter;
        Log.d(TAG, "value of CurrentInterruptionFilter changed to " + mDND);
    }

    public static MockListener getInstance() {
        return sNotificationListenerInstance;
    }

    public void resetData() {
        mPosted.clear();
        mNotifications.clear();
        mRemoved.clear();
        mOrder.clear();
        mRemovedReason.clear();
        mSnoozed.clear();
        mPostedNotifications.clear();
    }

    @Override
    public void onNotificationRankingUpdate(RankingMap rankingMap) {
        String[] orderedKeys = rankingMap.getOrderedKeys();
        mOrder.clear();
        Ranking rank = new Ranking();
        for( int i = 0; i < orderedKeys.length; i++) {
            String key = orderedKeys[i];
            mOrder.add(key);
            rankingMap.getRanking(key, rank);
            JSONObject note = mNotifications.get(key);
            if (note != null) {
                try {
                    note.put(JSON_RANK, rank.getRank());
                    note.put(JSON_AMBIENT, rank.isAmbient());
                    note.put(JSON_MATCHES_ZEN_FILTER, rank.matchesInterruptionFilter());
                } catch (JSONException e) {
                    Log.e(TAG, "failed to pack up notification payload", e);
                }
            }
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        if (!mTestPackages.contains(sbn.getPackageName())) { return; }
        Log.d(TAG, "posted: " + sbn.getTag());
        mPosted.add(sbn.getTag());
        mPostedNotifications.add(sbn.getNotification());
        JSONObject notification = new JSONObject();
        try {
            notification.put(JSON_TAG, sbn.getTag());
            notification.put(JSON_ID, sbn.getId());
            notification.put(JSON_PACKAGE, sbn.getPackageName());
            notification.put(JSON_WHEN, sbn.getNotification().when);
            notification.put(JSON_ICON, sbn.getNotification().icon);
            notification.put(JSON_FLAGS, sbn.getNotification().flags);
            mNotifications.put(sbn.getKey(), notification);
            mNotificationKeys.put(sbn.getTag(), sbn.getKey());
        } catch (JSONException e) {
            Log.e(TAG, "failed to pack up notification payload", e);
        }
        onNotificationRankingUpdate(rankingMap);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        Log.d(TAG, "removed: " + sbn.getTag());
        mRemoved.add(sbn.getTag());
        mNotifications.remove(sbn.getKey());
        mNotificationKeys.remove(sbn.getTag());
        onNotificationRankingUpdate(rankingMap);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap,
            int reason) {
        Log.d(TAG, "removed: " + sbn.getTag() + " for reason " + reason);
        mRemoved.add(sbn.getTag());
        JSONObject removed = new JSONObject();
        try {
            removed.put(JSON_TAG, sbn.getTag());
            removed.put(JSON_REASON, reason);
        } catch (JSONException e) {
            Log.e(TAG, "failed to pack up notification payload", e);
        }
        mNotifications.remove(sbn.getKey());
        mNotificationKeys.remove(sbn.getTag());
        mRemovedReason.put(sbn.getTag(), removed);
        onNotificationRankingUpdate(rankingMap);
    }

}
