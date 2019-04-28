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

package com.android.car.cluster.sample;

import static com.android.car.cluster.sample.DebugUtil.DEBUG;

import android.app.Notification;
import android.os.Handler;
import android.os.Message;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.android.car.cluster.sample.MessagingConverter.MessageContactDetails;

/**
 * Handles messaging {@link StatusBarNotification}.
 */
class MessagingNotificationHandler extends Handler {
    private static final String TAG = DebugUtil.getTag(MessagingNotificationHandler.class);

    private final ClusterView mClusterView;

    MessagingNotificationHandler(ClusterView cluster) {
        mClusterView = cluster;
    }

    @Override
    public void handleMessage(Message msg) {
        if (DEBUG) {
            Log.d(TAG, "NotificationHandler, handleMessage: " + msg);
        }
        if (msg.obj instanceof StatusBarNotification) {
            StatusBarNotification sbn = (StatusBarNotification) msg.obj;
            Notification notification = sbn.getNotification();
            if (notification != null) {
                if (DEBUG) {
                    Log.d(TAG, "NotificationHandler, notification extras: "
                            + notification.extras.toString());
                }
                if (MessagingConverter.canConvert(sbn)) {
                    MessageContactDetails data = MessagingConverter.convert(sbn);
                    mClusterView.handleHangoutMessage(
                            data.getContactImage(), data.getContactName());
                } else {
                    Log.w(TAG, "Can't convert message: " + sbn);
                }
            }
        } else {
            Log.w(TAG, "Unexpected message with object: " + msg.obj);
        }
    }
}