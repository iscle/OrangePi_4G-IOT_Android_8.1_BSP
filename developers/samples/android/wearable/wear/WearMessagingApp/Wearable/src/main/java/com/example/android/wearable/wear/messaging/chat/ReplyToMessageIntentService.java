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

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.RemoteInput;
import android.util.Log;
import com.example.android.wearable.wear.messaging.mock.MockDatabase;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.example.android.wearable.wear.messaging.util.Constants;
import com.example.android.wearable.wear.messaging.util.SharedPreferencesHelper;
import java.util.UUID;

/** Handles replies directly from Notification. */
public class ReplyToMessageIntentService extends IntentService {

    private static final String TAG = "ReplyToMessageIntentSvc";

    private Profile mUser;

    public ReplyToMessageIntentService() {
        super("ReplyToMessageIntentService");
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mUser = SharedPreferencesHelper.readUserFromJsonPref(getApplicationContext());
        if (mUser == null) {
            String message = "User is not stored locally.";
            Log.e(TAG, message);
            throw new RuntimeException(message);
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

        if (intent != null) {
            if (intent.hasExtra(Constants.EXTRA_CHAT)) {
                String action = intent.getAction();
                if (Constants.ACTION_REPLY.equals(action)) {
                    handleReply(getMessage(intent), extractChat(intent));
                }
            }
        }
    }

    /*
     * When we get a reply, all we need to do is convert the text of the reply
     * into an object, give it context of who sent it, and save it
     */
    private void handleReply(CharSequence reply, Chat chat) {
        Log.d(TAG, "handling reply reply: " + reply);

        Message message =
                new Message.Builder()
                        .id(UUID.randomUUID().toString())
                        .senderId(mUser.getId())
                        .text(reply.toString())
                        .build();

        MockDatabase.saveMessage(this, chat, message);
    }

    private Chat extractChat(Intent intent) {
        String chatId = intent.getStringExtra(Constants.EXTRA_CHAT);
        return MockDatabase.findChatById(this, chatId);
    }

    /*
     * Extracts CharSequence created from the RemoteInput associated with the Notification.
     */
    private CharSequence getMessage(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(Constants.EXTRA_REPLY);
        }
        return null;
    }
}
