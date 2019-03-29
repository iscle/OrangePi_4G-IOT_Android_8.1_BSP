/*
 * Copyright (c) 2017 Google Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.example.android.wearable.wear.messaging.mock;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.example.android.wearable.wear.messaging.util.SharedPreferencesHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Mock database stores data in {@link android.content.SharedPreferences} */
public class MockDatabase {

    private static final String TAG = "MockDatabase";

    /** Callback for retrieving a user asynchronously. */
    public interface RetrieveUserCallback {
        void onUserRetrieved(Profile user);

        void error(Exception e);
    }

    /** Callback for creating a user. */
    public interface CreateUserCallback {
        void onSuccess();

        void onError(Exception e);
    }

    /**
     * Creates a chat and stores it in the mock database
     *
     * @param participants of the chat
     * @param user that has started the chat
     * @return a chat with information attached to it
     */
    public static Chat createChat(Context context, Collection<Profile> participants, Profile user) {
        int size = participants.size();
        Log.d(TAG, String.format("Creating a new chat with %d participant(s)", size));

        Chat chat = new Chat();

        // Initializes chat's last message to a blank String.
        Message message = new Message.Builder().senderId(user.getId()).text("").build();

        chat.setLastMessage(message);

        Map<String, Profile> participantMap = new HashMap<>();
        for (Profile profile : participants) {
            participantMap.put(profile.getId(), profile);
        }
        chat.setParticipantsAndAlias(participantMap);

        // Create an id for the chat based on the aggregate of the participants' ids
        chat.setId(concat(participantMap.keySet()));

        // If you start a new chat with someone you already have a chat with, reuse that chat
        Collection<Chat> allChats = getAllChats(context);
        Chat exists = findChat(allChats, chat.getId());
        if (exists != null) {
            chat = exists;
        } else {
            allChats.add(chat);
        }

        persistsChats(context, allChats);

        return chat;
    }

    private static void persistsChats(Context context, Collection<Chat> chats) {
        SharedPreferencesHelper.writeChatsToJsonPref(context, new ArrayList<>(chats));
    }

    @Nullable
    private static Chat findChat(Collection<Chat> chats, String chatId) {
        for (Chat c : chats) {
            if (c.getId().equals(chatId)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Returns all of the chats stored in {@link android.content.SharedPreferences}. An empty {@link
     * Collection<Chat>} will be returned if preferences cannot be read from or cannot parse the
     * json object.
     *
     * @return a collection of chats
     */
    public static Collection<Chat> getAllChats(Context context) {
        try {
            return SharedPreferencesHelper.readChatsFromJsonPref(context);
        } catch (IOException e) {
            Log.e(TAG, "Could not read/unmarshall the list of chats from shared preferences", e);
            return Collections.emptyList();
        }
    }

    /**
     * Returns a {@link Chat} object with a given id.
     *
     * @param id of the stored chat
     * @return chat with id or null if no chat has that id
     */
    @Nullable
    public static Chat findChatById(Context context, String id) {
        return findChat(getAllChats(context), id);
    }

    /**
     * Updates the {@link Chat#lastMessage} field in the stored json object.
     *
     * @param chat to be updated.
     * @param lastMessage to be updated on the chat.
     */
    private static void updateLastMessage(Context context, Chat chat, Message lastMessage) {
        Collection<Chat> chats = getAllChats(context);
        // Update reference of chat to what it is the mock database.
        chat = findChat(chats, chat.getId());
        if (chat != null) {
            chat.setLastMessage(lastMessage);
        }

        // Save all chats since share prefs are managing them as one entity instead of individually.
        persistsChats(context, chats);
    }

    /**
     * Flattens the collection of strings into a single string. For example,
     *
     * <p>Input: ["a", "b", "c"]
     *
     * <p>Output: "abc"
     *
     * @param collection to be flattened into a string
     * @return a concatenated string
     */
    @NonNull
    private static String concat(Collection<String> collection) {
        Set<String> participantIds = new TreeSet<>(collection);
        StringBuilder sb = new StringBuilder();
        for (String id : participantIds) {
            sb.append(id);
        }
        return sb.toString();
    }

    /**
     * Saves the message to the thread of messages for the given chat. The message's sent time will
     * also be updated to preserve order.
     *
     * @param chat that the message should be added to.
     * @param message that was sent in the chat.
     * @return message with {@link Message#sentTime} updated
     */
    public static Message saveMessage(Context context, Chat chat, Message message) {

        message.setSentTime(System.currentTimeMillis());

        updateLastMessage(context, chat, message);

        Collection<Message> messages = getAllMessagesForChat(context, chat.getId());
        messages.add(message);

        SharedPreferencesHelper.writeMessagesForChatToJsonPref(
                context, chat, new ArrayList<>(messages));

        return message;
    }

    /**
     * Returns all messages related to a given chat.
     *
     * @param chatId of the conversation
     * @return messages in the conversation
     */
    public static Collection<Message> getAllMessagesForChat(Context context, String chatId) {
        return SharedPreferencesHelper.readMessagesForChat(context, chatId);
    }

    /**
     * Returns message details for a message in a particular chat.
     *
     * @param chatId that the message is in
     * @param messageId of the message to be found in the chat
     * @return message from a chat
     */
    @Nullable
    public static Message findMessageById(Context context, String chatId, String messageId) {
        for (Message message : getAllMessagesForChat(context, chatId)) {
            if (message.getId().equals(messageId)) {
                return message;
            }
        }
        return null;
    }

    /**
     * Generates a set of predefined dummy contacts. You may need to add in extra logic for
     * timestamp changes between server and local app.
     *
     * @return a list of profiles to be used as contacts
     */
    public static List<Profile> getUserContacts(Context context) {

        List<Profile> contacts = SharedPreferencesHelper.readContactsFromJsonPref(context);
        if (!contacts.isEmpty()) {
            return contacts;
        }

        // Cannot find contacts so we will persist and return a default set of contacts.
        List<Profile> defaultContacts = MockObjectGenerator.generateDefaultContacts();
        SharedPreferencesHelper.writeContactsToJsonPref(context, defaultContacts);
        return defaultContacts;
    }

    /**
     * Returns the user asynchronously to the client via a callback.
     *
     * @param id for a user
     * @param callback used for handling asynchronous responses
     */
    public static void getUser(Context context, String id, RetrieveUserCallback callback) {
        Profile user = SharedPreferencesHelper.readUserFromJsonPref(context);
        if (user != null && user.getId().equals(id)) {
            callback.onUserRetrieved(user);
        } else {
            // Could not find user with that id.
            callback.onUserRetrieved(null);
        }
    }

    /**
     * Creates a user asynchronously and notifies the client if a user has been created successfully
     * or if there were any errors.
     *
     * @param user that needs to be created
     * @param callback used for handling asynchronous responses
     */
    public static void createUser(Context context, Profile user, CreateUserCallback callback) {
        SharedPreferencesHelper.writeUserToJsonPref(context, user);
        callback.onSuccess();
    }
}
