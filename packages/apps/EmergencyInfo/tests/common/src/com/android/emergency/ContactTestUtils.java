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
package com.android.emergency;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;

/**
 * Utils to create and delete contacts.
 */
public class ContactTestUtils {

    /** Deletes contacts that match the given name and phone number. */
    public static boolean deleteContact(ContentResolver contentResolver,
                                        String name,
                                        String phone) {
        Uri contactUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phone));
        Cursor cursor = contentResolver.query(contactUri, null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                do {
                    String displayName = cursor.getString(
                            cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
                    if (displayName.equals(name)) {
                        String lookupKey = cursor.getString(
                                cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY));
                        Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                                lookupKey);
                        contentResolver.delete(uri, null, null);
                        return true;
                    }
                } while (cursor.moveToNext());
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    /**
     * Creates a new contact with the given name and phone number. Returns the
     * ContactsContract.CommonDataKinds.Phone.CONTENT_URI corresponding to the new contact.
     */
    public static Uri createContact(ContentResolver contentResolver,
                                    String name,
                                    String phoneNumber) {
        ContentValues values = new ContentValues();
        Uri rawContactUri = contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI,
                values);
        long rawContactId = ContentUris.parseId(rawContactUri);
        insertStructuredName(contentResolver, rawContactId, name, values);
        return insertPhoneNumber(contentResolver, rawContactId,
                phoneNumber,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
    }

    private static void insertStructuredName(ContentResolver contentResolver,
                                             long rawContactId,
                                             String name,
                                             ContentValues values) {
        values.put(StructuredName.DISPLAY_NAME, name);
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        contentResolver.insert(ContactsContract.Data.CONTENT_URI, values);
    }

    private static Uri insertPhoneNumber(ContentResolver contentResolver,
                                         long rawContactId,
                                         String phoneNumber,
                                         int type) {
        ContentValues values = new ContentValues();
        values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
        values.put(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber);
        values.put(ContactsContract.CommonDataKinds.Phone.TYPE, type);
        values.put(ContactsContract.CommonDataKinds.Phone.LABEL, "Mobile");
        return contentResolver.insert(ContactsContract.Data.CONTENT_URI, values);
    }

    private ContactTestUtils() {
        // Prevent instantiation
        throw new UnsupportedOperationException();
    }
}
