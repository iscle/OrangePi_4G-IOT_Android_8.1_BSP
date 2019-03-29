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
package com.example.android.wearable.wear.messaging.util;

/**
 * All Constants for interaction with the Mock Database, extras between activities, and actions for
 * intents.
 */
public class Constants {

    /* Constants for Extras to be passed along through bundles */
    public static final String EXTRA_CHAT =
            "com.example.android.wearable.wear.messaging.extra.CHAT";
    public static final String EXTRA_MESSAGE =
            "com.example.android.wearable.wear.messaging.extra.MESSAGE";
    public static final String EXTRA_REPLY =
            "com.example.android.wearable.wear.messaging.extra.REPLY";

    /* Shared Preference keys for SharedPreferencesHelper */
    static final String PREFS_NAME = "com.example.android.wearable.wear.messaging";
    static final String PREFS_CHAT_ID_KEY =
            "com.example.android.wearable.wear.messaging.prefs.CHAT_ID";
    static final String PREFS_USER_KEY = "com.example.android.wearable.wear.messaging.prefs.USER";
    static final String PREFS_CONTACTS_KEY =
            "com.example.android.wearable.wear.messaging.prefs.CONTACTS";
    static final String PREFS_CHATS_KEY = "com.example.android.wearable.wear.messaging.prefs.CHATS";
    static final String PREFS_MESSAGE_PREFIX = "chat_message_";

    public static final String RESULT_CONTACTS_KEY =
            "com.example.android.wear.samples.sup.result.CONTACTS_RESULT";

    /* Action constants for services/broadcast receiver intents */
    static final String ACTION_RECEIVE_MESSAGE =
            "com.example.android.wearable.wear.messaging.action.RECEIVE_MESSAGE";
    public static final String ACTION_REPLY =
            "com.example.android.wearable.wear.messaging.action.REPLY_MESSAGE";
}
