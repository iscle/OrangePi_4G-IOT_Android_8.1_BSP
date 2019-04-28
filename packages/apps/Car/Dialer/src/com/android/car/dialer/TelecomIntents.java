/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.dialer;

/**
 * Constants related to starting Dialer-specific Intents.
 */
public class TelecomIntents {
    private TelecomIntents() {}

    /**
     * Activity action to display the details of a contact. The use of this action should be
     * accompanied by an URI string extra to the contact to display. Use the key
     * {@link #CONTACT_LOOKUP_URI_EXTRA}.
     *
     * @see android.provider.ContactsContract.Contacts#getLookupUri(long, String)
     */
    public static final String ACTION_SHOW_CONTACT_DETAILS =
            "com.android.car.dialer.SHOW_CONTACT_DETAILS";

    /**
     * A key to the Intent extra that holds the contact lookup URI.
     */
    public static final String CONTACT_LOOKUP_URI_EXTRA =
            "com.android.car.dialer.CONTACT_LOOKUP_URI_EXTRA";
}
