/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.wear.messaging.chat;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import com.example.android.wearable.wear.messaging.R;
import com.example.android.wearable.wear.messaging.mock.MockDatabase;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.example.android.wearable.wear.messaging.util.Constants;
import java.util.UUID;

/**
 * This broadcast receiver will take a message and create a notification mocking out the behavior
 * what would be expected with a push notification backend.
 *
 * <p>It will append the original message to an introduction sentence. This way, there will be
 * enough content in the notification to demonstrate scrolling.
 */
public class MockIncomingMessageReceiver extends BroadcastReceiver {

    private static final String TAG = "MockIncomingMessageRcr";

    public static final int NOTIFICATION_ID = 888;

    private NotificationManagerCompat mNotificationManagerCompat;

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive(): " + intent);
        mNotificationManagerCompat = NotificationManagerCompat.from(context);

        String chatId = intent.getStringExtra(Constants.EXTRA_CHAT);
        String messageId = intent.getStringExtra(Constants.EXTRA_MESSAGE);

        Chat chat = MockDatabase.findChatById(context, chatId);
        if (chat == null) {
            Log.e(TAG, "Could not find chat with id " + chatId);
            return;
        }

        Message message = MockDatabase.findMessageById(context, chatId, messageId);
        if (message == null) {
            Log.d(TAG, "No message found in chat with id " + messageId);
            return;
        }

        mockReply(context, chat, message);
    }

    private void mockReply(Context context, Chat chat, Message message) {

        String replierId = chat.getParticipants().keySet().iterator().next();

        String[] mockReplyMessages =
                context.getResources().getStringArray(R.array.mock_reply_messages);
        int index = (int) (Math.random() * mockReplyMessages.length);
        String mockReplyMessage = mockReplyMessages[index];

        Message replyMessage =
                new Message.Builder()
                        .id(UUID.randomUUID().toString())
                        .senderId(replierId)
                        .text(mockReplyMessage + " " + message.getText())
                        .build();

        MockDatabase.saveMessage(context, chat, replyMessage);

        generateMessagingStyleNotification(context, chat, replyMessage);
    }

    /**
     * See https://github.com/googlesamples/android-WearNotifications for more examples.
     *
     * @param context used for obtaining resources and creating Intents
     * @param chat is the context for all of the messages. The top of the hierarchy
     * @param message that will be used to be displayed in the notification
     */
    private void generateMessagingStyleNotification(Context context, Chat chat, Message message) {

        Log.d(TAG, "generateMessagingStyleNotification()");

        NotificationCompat.MessagingStyle messagingStyle =
                new android.support.v7.app.NotificationCompat.MessagingStyle("Me")
                        .setConversationTitle(chat.getAlias());

        Profile sender = chat.getParticipants().get(message.getSenderId());
        String senderId = (sender != null) ? sender.getName() : "Me";
        messagingStyle.addMessage(message.getText(), message.getSentTime(), senderId);

        // Set up a RemoteInput Action, so users can input (keyboard, drawing, voice) directly
        // from the notification without entering the app.

        // Create the RemoteInput specifying this key.
        String replyLabel = context.getString(R.string.reply_label);
        RemoteInput remoteInput =
                new RemoteInput.Builder(Constants.EXTRA_REPLY).setLabel(replyLabel).build();

        // Create PendingIntent for service that handles input.
        Intent intent = new Intent(context, ReplyToMessageIntentService.class);
        intent.setAction(Constants.ACTION_REPLY);
        intent.putExtra(Constants.EXTRA_CHAT, chat.getId());

        PendingIntent replyActionPendingIntent =
                PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Enable action to appear inline on Wear 2.0 (24+). This means it will appear over the
        // lower portion of the Notification for easy action (only possible for one action).
        final android.support.v7.app.NotificationCompat.Action.WearableExtender inlineAction =
                new android.support.v7.app.NotificationCompat.Action.WearableExtender()
                        .setHintDisplayActionInline(true)
                        .setHintLaunchesActivity(false);
        android.support.v7.app.NotificationCompat.Action replyAction =
                new android.support.v7.app.NotificationCompat.Action.Builder(
                                R.drawable.ic_reply_white_18dp,
                                replyLabel,
                                replyActionPendingIntent)
                        .addRemoteInput(remoteInput)
                        // Allows system to generate replies by context of conversation
                        .setAllowGeneratedReplies(true)
                        // Add WearableExtender to enable inline actions
                        .extend(inlineAction)
                        .build();

        android.support.v7.app.NotificationCompat.Builder notificationCompatBuilder =
                new android.support.v7.app.NotificationCompat.Builder(context);

        // Builds and issues notification
        notificationCompatBuilder
                // MESSAGING_STYLE sets title and content for API 24+ (Wear 2.0) devices
                .setStyle(messagingStyle)
                .setSmallIcon(R.drawable.ic_launcher)
                .setLargeIcon(
                        BitmapFactory.decodeResource(
                                context.getResources(), R.drawable.ic_person_blue_48dp))
                // Set primary color (important for Wear 2.0 Notifications)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .addAction(replyAction)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setPriority(Notification.PRIORITY_HIGH)
                // Hides content on the lock-screen
                .setVisibility(Notification.VISIBILITY_PRIVATE);

        // If the phone is in "Do not disturb mode, the user will still be notified if
        // the sender(s) is starred as a favorite.
        for (Profile participant : chat.getParticipants().values()) {
            notificationCompatBuilder.addPerson(participant.getName());
        }

        Notification notification = notificationCompatBuilder.build();
        mNotificationManagerCompat.notify(NOTIFICATION_ID, notification);
    }
}
