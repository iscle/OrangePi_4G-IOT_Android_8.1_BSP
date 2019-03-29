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
package com.example.android.wearable.wear.messaging.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import com.example.android.wearable.wear.messaging.model.Profile;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SharedPreferencesHelper provides static methods to set, get, delete these objects.
 *
 * <p>The user's profile details and chat details are persisted in SharedPreferences to access
 * across the app.
 */
public class SharedPreferencesHelper {

    private static final String TAG = "SharedPreferencesHelper";

    private static Gson gson = new Gson();

    /**
     * Returns logged in user or null if no user is logged in.
     *
     * @param context shared preferences context
     * @return user profile
     */
    public static Profile readUserFromJsonPref(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        String profileString = sharedPreferences.getString(Constants.PREFS_USER_KEY, null);
        if (profileString == null) {
            return null;
        }
        try {
            return gson.fromJson(profileString, Profile.class);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Could not parse user from shard preferences.", e);
            return null;
        }
    }

    /**
     * Writes a {@link Profile} to json and stores it in preferences.
     *
     * @param context used to access {@link SharedPreferences}
     * @param user to be stored in preferences
     */
    public static void writeUserToJsonPref(Context context, Profile user) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, 0);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.PREFS_USER_KEY, gson.toJson(user));
        editor.apply();
    }

    /**
     * Reads contacts from preferences.
     *
     * @param context used to access {@link SharedPreferences}
     * @return contacts from preferences
     */
    public static List<Profile> readContactsFromJsonPref(Context context) {
        try {
            return getList(context, Profile.class, Constants.PREFS_CONTACTS_KEY);
        } catch (JsonSyntaxException e) {
            String logMessage =
                    "Could not read/unmarshall the list of contacts from shared preferences.";
            Log.e(TAG, logMessage, e);
            return Collections.emptyList();
        }
    }

    /**
     * Writes a {@link List<Profile>} to json and stores it in preferences.
     *
     * @param context used to access {@link SharedPreferences}
     * @param contacts to be stored in preferences
     */
    public static void writeContactsToJsonPref(Context context, List<Profile> contacts) {
        setList(context, contacts, Constants.PREFS_CONTACTS_KEY);
    }

    /**
     * Reads chats from preferences
     *
     * @param context used to access {@link SharedPreferences}
     * @return chats from preferences
     * @throws IOException if there is an error parsing the json from preferences
     */
    public static List<Chat> readChatsFromJsonPref(Context context) throws IOException {
        try {
            return getList(context, Chat.class, Constants.PREFS_CHATS_KEY);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Could not read/unmarshall the list of chats from shared preferences", e);
            return Collections.emptyList();
        }
    }

    /**
     * Writes a {@link List<Chat>} to json and stores it in preferences.
     *
     * @param context used to access {@link SharedPreferences}
     * @param chats to be stores in preferences
     */
    public static void writeChatsToJsonPref(Context context, List<Chat> chats) {
        Log.d(TAG, String.format("Saving %d chat(s)", chats.size()));
        setList(context, chats, Constants.PREFS_CHATS_KEY);
    }

    /**
     * Reads messages for a chat from preferences.
     *
     * @param context used to access {@link SharedPreferences}
     * @param chatId for the chat the messages are from
     * @return messages from preferences
     */
    public static List<Message> readMessagesForChat(Context context, String chatId) {
        try {
            return getList(context, Message.class, Constants.PREFS_MESSAGE_PREFIX + chatId);
        } catch (JsonSyntaxException e) {
            Log.e(TAG, "Could not read/unmarshall the list of messages from shared preferences", e);
            return Collections.emptyList();
        }
    }

    /**
     * Writes a {@link List<Message>} to json and stores it in preferences.
     *
     * @param context used to access {@link SharedPreferences}
     * @param chat that the messages are from
     * @param messages to be stored into preferences
     */
    public static void writeMessagesForChatToJsonPref(
            Context context, Chat chat, List<Message> messages) {
        setList(context, messages, Constants.PREFS_MESSAGE_PREFIX + chat.getId());
    }

    /**
     * Returns List of specified class from SharedPreferences (converts from string in
     * SharedPreferences to class)
     *
     * @param context used for getting an instance of shared preferences
     * @param clazz the class that the strings will be unmarshalled into
     * @param key the key in shared preferences to access the string set
     * @param <T> the type of object that will be in the returned list, should be the same as the
     *     clazz that was supplied
     * @return a list of <T> objects that were stored in shared preferences. Returns an empty list
     *     if no data is available.
     */
    private static <T> List<T> getList(Context context, Class<T> clazz, String key)
            throws JsonSyntaxException {
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> contactsSet = sharedPreferences.getStringSet(key, new HashSet<String>());
        if (contactsSet.isEmpty()) {
            // Favoring mutability of the list over Collections.emptyList().
            return new ArrayList<>();
        }
        List<T> list = new ArrayList<>(contactsSet.size());
        for (String contactString : contactsSet) {
            list.add(gson.fromJson(contactString, clazz));
        }
        return list;
    }

    /**
     * Sets a List of specified class in SharedPreferences (converts from List of class to string
     * for SharedPreferences)
     *
     * @param context used for getting an instance of shared preferences
     * @param list of <T> object that need to be persisted
     * @param key the key in shared preferences which the string set will be stored
     * @param <T> type the of object we will be marshalling and persisting
     */
    private static <T> void setList(Context context, List<T> list, String key) {
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        Set<String> strings = new LinkedHashSet<>(list.size());
        for (T t : list) {
            strings.add(gson.toJson(t));
        }
        editor.putStringSet(key, strings);
        editor.apply();
    }
}
