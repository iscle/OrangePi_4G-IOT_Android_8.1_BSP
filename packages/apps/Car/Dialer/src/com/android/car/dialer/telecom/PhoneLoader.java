/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car.dialer.telecom;

import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Manage loading different types of call logs.
 * Currently supports:
 *     All calls
 *     Missed calls
 *     speed dial calls
 */
public class PhoneLoader {
    private static final String TAG = "Em.PhoneLoader";

    /** CALL_TYPE_ALL and _MISSED's values are assigned to be consistent with the Dialer **/
    public final static int CALL_TYPE_ALL = -1;
    public final static int CALL_TYPE_MISSED = CallLog.Calls.MISSED_TYPE;
    /** Starred and frequent **/
    public final static int CALL_TYPE_SPEED_DIAL = 2;

    private static final int NUM_LOGS_TO_DISPLAY = 100;
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    public static final int INCOMING_TYPE = 1;
    public static final int OUTGOING_TYPE = 2;
    public static final int MISSED_TYPE = 3;
    public static final int VOICEMAIL_TYPE = 4;

    private static HashMap<String, String> sNumberCache;

    /**
     * Hybrid Factory for creating a Contact Loader that also immediately starts its execution.
     * Note: NOT to be used wit LoaderManagers.
     */
    public static CursorLoader registerCallObserver(int type,
            Context context, Loader.OnLoadCompleteListener<Cursor> listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "registerCallObserver: type: " + type + ", listener: " + listener);
        }

        switch(type) {
            case CALL_TYPE_ALL:
            case CALL_TYPE_MISSED:
                return fetchCallLog(type, context, listener);
            case CALL_TYPE_SPEED_DIAL:
                CursorLoader loader = newStrequentContactLoader(context);
                loader.registerListener(0, listener);
                loader.startLoading();
                return loader;
            default:
                throw new UnsupportedOperationException("Unknown CALL_TYPE " + type + ".");
        }
    }

    /**
     * Factory method for creating a Loader that will fetch strequent contacts from the phone.
     */
    public static CursorLoader newStrequentContactLoader(Context context) {
        Uri uri = ContactsContract.Contacts.CONTENT_STREQUENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true")
                .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").build();

        return new CursorLoader(context, uri, null, null, null, null);
    }

    // TODO(mcrico): Separate into a factory method and move configuration to registerCallObserver
    private static CursorLoader fetchCallLog(int callType,
            Context context, Loader.OnLoadCompleteListener<Cursor> listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "fetchCallLog");
        }

        // We need to check for NULL explicitly otherwise entries with where READ is NULL
        // may not match either the query or its negation.
        // We consider the calls that are not yet consumed (i.e. IS_READ = 0) as "new".
        StringBuilder where = new StringBuilder();
        List<String> selectionArgs = new ArrayList<String>();

        if (callType > CALL_TYPE_ALL) {
            // add a filter for call type
            where.append(String.format("(%s = ?)", CallLog.Calls.TYPE));
            selectionArgs.add(Integer.toString(callType));
        }
        String selection = where.length() > 0 ? where.toString() : null;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "accessingCallLog");
        }

        Uri uri = CallLog.Calls.CONTENT_URI.buildUpon()
                .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY,
                        Integer.toString(NUM_LOGS_TO_DISPLAY))
                .build();
        CursorLoader loader = new CursorLoader(context, uri, null, selection,
                selectionArgs.toArray(EMPTY_STRING_ARRAY), CallLog.Calls.DEFAULT_SORT_ORDER);
        loader.registerListener(0, listener);
        loader.startLoading();
        return loader;
    }

    /**
     * @return The column index of the contact id. It should be {@link BaseColumns#_ID}. However,
     *         if that fails use {@link android.provider.ContactsContract.RawContacts#CONTACT_ID}.
     *         If that also fails, we use the first column in the table.
     */
    public static int getIdColumnIndex(Cursor cursor) {
        int ret = cursor.getColumnIndex(BaseColumns._ID);
        if (ret == -1) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, "Falling back to contact_id instead of _id");
            }

            // Some versions of the ContactsProvider on LG don't have an _id column but instead
            // use contact_id. If the lookup for _id fails, we fallback to contact_id.
            ret = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID);
        }
        if (ret == -1) {
            Log.e(TAG, "Neither _id or contact_id exist! Falling back to column 0. " +
                    "There is no guarantee that this will work!");
            ret = 0;
        }
        return ret;
    }

    /**
     * @return The column index of the number.
     *         Will return a valid column for call log or contacts queries.
     */
    public static int getNumberColumnIndex(Cursor cursor) {
        int numberColumn = cursor.getColumnIndex(CallLog.Calls.NUMBER);
        if (numberColumn == -1) {
            numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
        }
        return numberColumn;
    }


    /**
     * @return The column index of the number type.
     *         Will return a valid column for call log or contacts queries.
     */
    public static int getTypeColumnIndex(Cursor cursor) {
        int typeColumn = cursor.getColumnIndex(CallLog.Calls.TYPE);
        if (typeColumn == -1) {
            typeColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE);
        }
        return typeColumn;
    }

    /**
     * @return The column index of the name.
     *         Will return a valid column for call log or contacts queries.
     */
    public static int getNameColumnIndex(Cursor cursor) {
        int typeColumn = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME);
        if (typeColumn == -1) {
            typeColumn = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
        }
        return typeColumn;
    }

    /**
     * @return The phone number for the contact. Most phones will simply get the value in the
     *         column returned by {@link #getNumberColumnIndex(Cursor)}. However, some devices
     *         such as the Galaxy S6 return null for those columns. In those cases, we use the
     *         contact id (which we hopefully do have) to look up just the phone number for that
     *         specific contact.
     */
    public static String getPhoneNumber(Cursor cursor, ContentResolver cr) {
        int columnIndex = getNumberColumnIndex(cursor);
        String number = cursor.getString(columnIndex);
        if (number == null) {
            Log.w(TAG, "Phone number is null. Using fallback method.");
            int idColumnIndex = getIdColumnIndex(cursor);
            String idColumnName = cursor.getColumnName(idColumnIndex);
            String contactId = cursor.getString(idColumnIndex);
            getNumberFromContactId(cr, idColumnName, contactId);
        }
        return number;
    }

    /**
     * Return the phone number for the given contact id.
     * @param columnName On some phones, we have to use non-standard columns for the primary key.
     * @param id The value in the columnName for the desired contact.
     * @return The phone number for the given contact or empty string if there was an error.
     */
    public static String getNumberFromContactId(ContentResolver cr, String columnName, String id) {
        if (TextUtils.isEmpty(id)) {
            Log.e(TAG, "You must specify a valid id to get a contact's phone number.");
            return "";
        }
        if (sNumberCache == null) {
            sNumberCache = new HashMap<>();
        } else if (sNumberCache.containsKey(id)) {
            return sNumberCache.get(id);
        }

        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        Cursor phoneNumberCursor = cr.query(uri,
                new String[] {ContactsContract.CommonDataKinds.Phone.NUMBER},
                columnName + " = ?" , new String[] {id}, null);

        if (!phoneNumberCursor.moveToFirst()) {
            Log.e(TAG, "Unable to move phone number cursor to the first item.");
            return "";
        }
        String number = phoneNumberCursor.getString(0);
        phoneNumberCursor.close();
        return number;
    }
}
