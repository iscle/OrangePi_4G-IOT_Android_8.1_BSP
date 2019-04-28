/*
* Copyright (C) 2015 Samsung System LSI
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

package com.android.bluetooth.map;

import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Pattern;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony.CanonicalAddressesColumns;
import android.provider.Telephony.MmsSms;
import android.util.Log;

/**
 * Use these functions when extracting data for listings. It caches frequently used data to
 * speed up building large listings - e.g. before applying filtering.
 */
@TargetApi(19)
public class SmsMmsContacts {

    private static final String TAG = "SmsMmsContacts";

    private HashMap<Long,String> mPhoneNumbers = null;
    private final HashMap<String,MapContact> mNames = new HashMap<String, MapContact>(10);

    private static final Uri ADDRESS_URI =
            MmsSms.CONTENT_URI.buildUpon().appendPath("canonical-addresses").build();

    private static final String[] ADDRESS_PROJECTION = { CanonicalAddressesColumns._ID,
                    CanonicalAddressesColumns.ADDRESS };
    private static final int COL_ADDR_ID =
            Arrays.asList(ADDRESS_PROJECTION).indexOf(CanonicalAddressesColumns._ID);
    private static final int COL_ADDR_ADDR =
            Arrays.asList(ADDRESS_PROJECTION).indexOf(CanonicalAddressesColumns.ADDRESS);

    private static final String[] CONTACT_PROJECTION = {Contacts._ID, Contacts.DISPLAY_NAME};
    private static final String CONTACT_SEL_VISIBLE = Contacts.IN_VISIBLE_GROUP + "=1";
    private static final int COL_CONTACT_ID =
            Arrays.asList(CONTACT_PROJECTION).indexOf(Contacts._ID);
    private static final int COL_CONTACT_NAME =
            Arrays.asList(CONTACT_PROJECTION).indexOf(Contacts.DISPLAY_NAME);

    /**
     * Get a contacts phone number based on the canonical addresses id of the contact.
     * (The ID listed in the Threads table.)
     * @param resolver the ContantResolver to be used.
     * @param id the id of the contact, as listed in the Threads table
     * @return the phone number of the contact - or null if id does not exist.
     */
    public String getPhoneNumber(ContentResolver resolver, long id) {
        String number;
        if(mPhoneNumbers != null && (number = mPhoneNumbers.get(id)) != null) {
            return number;
        }
        fillPhoneCache(resolver);
        return mPhoneNumbers.get(id);
    }

    public static String getPhoneNumberUncached(ContentResolver resolver, long id) {
        String where = CanonicalAddressesColumns._ID + " = " + id;
        Cursor c = resolver.query(ADDRESS_URI, ADDRESS_PROJECTION, where, null, null);
        try {
            if (c != null) {
                if(c.moveToPosition(0)) {
                    return c.getString(COL_ADDR_ADDR);
                }
            }
            Log.e(TAG, "query failed");
        } finally {
            if(c != null) c.close();
        }
        return null;
    }

    /**
     * Clears the local cache. Call after a listing is complete, to avoid using invalid data.
     */
    public void clearCache() {
        if(mPhoneNumbers != null) mPhoneNumbers.clear();
        if(mNames != null) mNames.clear();
    }

    /**
     * Refreshes the cache, by clearing all cached values and fill the cache with the result of
     * a new query.
     * @param resolver the ContantResolver to be used.
     */
    private void fillPhoneCache(ContentResolver resolver){
        Cursor c = resolver.query(ADDRESS_URI, ADDRESS_PROJECTION, null, null, null);
        if(mPhoneNumbers == null) {
            int size = 0;
            if(c != null)
            {
                size = c.getCount();
            }
            mPhoneNumbers = new HashMap<Long, String>(size);
        } else {
            mPhoneNumbers.clear();
        }
        try {
            if (c != null) {
                long id;
                String addr;
                c.moveToPosition(-1);
                while (c.moveToNext()) {
                    id = c.getLong(COL_ADDR_ID);
                    addr = c.getString(COL_ADDR_ADDR);
                    mPhoneNumbers.put(id, addr);
                }
            } else {
                Log.e(TAG, "query failed");
            }
        } finally {
            if(c != null) c.close();
        }
    }

    public MapContact getContactNameFromPhone(String phone, ContentResolver resolver) {
        return getContactNameFromPhone(phone, resolver, null);
    }
    /**
     * Lookup a contacts name in the Android Contacts database.
     * @param phone the phone number of the contact
     * @param resolver the ContentResolver to use.
     * @return the name of the contact or null, if no contact was found.
     */
    public MapContact getContactNameFromPhone(String phone, ContentResolver resolver,
            String contactNameFilter) {
        MapContact contact = mNames.get(phone);

        if(contact != null){
            if(contact.getId() < 0) {
                return null;
            }
            if(contactNameFilter == null) {
                return contact;
            }
            // Validate filter
            String searchString = contactNameFilter.replace("*", ".*");
            searchString = ".*" + searchString + ".*";
            Pattern p = Pattern.compile(Pattern.quote(searchString), Pattern.CASE_INSENSITIVE);
            if(p.matcher(contact.getName()).find()) {
                return contact;
            }
            return null;
        }

        // TODO: Should we change to extract both formatted name, and display name?

        Uri uri = Uri.withAppendedPath(PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI,
                Uri.encode(phone));
        String selection = CONTACT_SEL_VISIBLE;
        String[] selectionArgs = null;
        if(contactNameFilter != null) {
            selection += "AND " + ContactsContract.Contacts.DISPLAY_NAME + " like ?";
            selectionArgs = new String[]{"%" + contactNameFilter.replace("*", "%") + "%"};
        }

        Cursor c = resolver.query(uri, CONTACT_PROJECTION, selection, selectionArgs, null);
        try {
            if (c != null && c.getCount() >= 1) {
                c.moveToFirst();
                long id = c.getLong(COL_CONTACT_ID);
                String name = c.getString(COL_CONTACT_NAME);
                contact = MapContact.create(id, name);
                mNames.put(phone, contact);
            } else {
                contact = MapContact.create(-1, null);
                mNames.put(phone, contact);
                contact = null;
            }
        } finally {
            if (c != null) c.close();
        }
        return contact;
    }
}
