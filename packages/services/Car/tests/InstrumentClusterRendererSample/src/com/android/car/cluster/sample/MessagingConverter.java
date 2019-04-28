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

import android.app.Notification;
import android.graphics.Bitmap;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat.CarExtender;
import android.support.v4.app.NotificationCompat.CarExtender.UnreadConversation;
import android.text.TextUtils;
import android.util.Log;

/**
 * Convert a {@link CarExtender} notification into a {@link MessageContactDetails}
 */
public class MessagingConverter {
    private static final String TAG = DebugUtil.getTag(MessagingConverter.class);

    public static boolean canConvert(StatusBarNotification sbn) {
        Notification notification = sbn.getNotification();
        if (notification == null) {
            if (DebugUtil.DEBUG) {
                Log.d(TAG, "Notification is empty.");
            }
            return false;
        }
        CarExtender ce = new CarExtender(sbn.getNotification());
        if (ce.getUnreadConversation() == null) {
            if (DebugUtil.DEBUG) {
                Log.d(TAG, "Notification with no messaging component.");
            }
            return false;
        }

        CarExtender.UnreadConversation uc = ce.getUnreadConversation();
        String[] messages = uc.getMessages();
        if (messages == null || messages.length == 0) {
            Log.w(TAG, "Car message notification with no messages.");
            return false;
        }

        if (TextUtils.isEmpty(uc.getParticipant())) {
            Log.w(TAG, "Car message notification with no participant.");
            return false;
        }

        if (uc.getReplyPendingIntent() == null) {
            Log.w(TAG, "Car message notification with no reply intent.");
            return false;
        }

        for (String m : messages) {
            if (m == null) {
                Log.w(TAG, "Car message with null text.");
                return false;
            }
        }
        return true;
    }

    public static MessageContactDetails convert(StatusBarNotification sbn) {
        CarExtender ce = new CarExtender(sbn.getNotification());
        UnreadConversation uc = ce.getUnreadConversation();

        Bitmap largeIcon = ce.getLargeIcon();
        if (largeIcon == null) {
            largeIcon = sbn.getNotification().largeIcon;
        }
        String name = uc.getParticipant();

        return new MessageContactDetails(largeIcon, name);
    }

    public static class MessageContactDetails {
        private final Bitmap mContactImage;
        private final String mContactName;

        private MessageContactDetails(Bitmap contactImage, String contactName) {
            mContactImage = contactImage;
            mContactName = contactName;
        }

        public Bitmap getContactImage() {
            return mContactImage;
        }

        public String getContactName() {
            return mContactName;
        }
    }
}
