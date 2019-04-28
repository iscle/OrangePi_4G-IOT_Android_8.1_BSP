/*
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

/**
 * Contract class for sending extras when desiring to view the entire
 * message in the case where messages can be clipped.
 */
public final class FullMessageContract {

    private FullMessageContract() {}

    /**
     * String extra used to pass in the url to load the full message.
     */
    public static final String EXTRA_PERMALINK = "permalink";
    /**
     * String extra for the account to which the message belongs.
     */
    public static final String EXTRA_ACCOUNT_NAME = "account-name";
    /**
     * (Optional) String extra for the server message id of the message.
     */
    public static final String EXTRA_SERVER_MESSAGE_ID = "server-message-id";
}
