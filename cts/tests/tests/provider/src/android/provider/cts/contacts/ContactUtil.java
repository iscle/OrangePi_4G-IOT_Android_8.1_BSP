/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package android.provider.cts.contacts;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;

import junit.framework.Assert;

/**
 * Convenience methods for operating on the Contacts table.
 */
public class ContactUtil {

    private static final Uri URI = ContactsContract.Contacts.CONTENT_URI;

    public static void update(ContentResolver resolver, long contactId,
            ContentValues values) {
        Uri uri = ContentUris.withAppendedId(URI, contactId);
        resolver.update(uri, values, null, null);
    }

    public static void delete(ContentResolver resolver, long contactId) {
        Uri uri = ContentUris.withAppendedId(URI, contactId);
        resolver.delete(uri, null, null);
    }

    public static boolean recordExistsForContactId(ContentResolver resolver, long contactId) {
        String[] projection = new String[]{
                ContactsContract.Contacts._ID
        };
        Uri uri = ContentUris.withAppendedId(URI, contactId);
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        if (cursor.moveToNext()) {
            return true;
        }
        return false;
    }

    public static long queryContactLastUpdatedTimestamp(ContentResolver resolver, long contactId) {
        String[] projection = new String[]{
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
        };

        Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        try {
            if (cursor.moveToNext()) {
                return cursor.getLong(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return CommonDatabaseUtils.NOT_FOUND;
    }

    /**
     * Verifies that the number of object parameters is either zero or even, inserts them
     * into a new ContentValues object as a set of name-value pairs, and returns the newly created
     * ContentValues object. Throws an exception if the number of string parameters is odd, or a
     * single null parameter was provided.
     *
     * @param namesAndValues Zero or even number of object parameters to convert into name-value
     * pairs
     *
     * @return newly created ContentValues containing the provided name-value pairs
     */
    public static ContentValues newContentValues(Object... namesAndValues) {
        // Checks that the number of provided parameters is zero or even.
        Assert.assertEquals(0, namesAndValues.length % 2);
        final ContentValues contentValues = new ContentValues();
        for (int i = 0; i < namesAndValues.length - 1; i += 2) {
            Assert.assertNotNull(namesAndValues[i]);
            final String name = namesAndValues[i].toString();
            final Object value = namesAndValues[i + 1];
            if (value == null) {
                contentValues.putNull(name);
            } else if (value instanceof String) {
                contentValues.put(name, (String) value);
            } else if (value instanceof Integer) {
                contentValues.put(name, (Integer) value);
            } else if (value instanceof Long) {
                contentValues.put(name, (Long) value);
            } else {
                Assert.fail("Unsupported value type: " + value.getClass().getSimpleName() + " for "
                    + " name: " + name);
            }
        }
        return contentValues;
    }

    /**
     * Updates the content resolver with two given raw contact ids and an aggregation type to
     * manually trigger the forced aggregation, splitting of two raw contacts or specify that
     * the provider should automatically decide whether or not to aggregate the two raw contacts.
     *
     * @param resolver ContentResolver from a valid context
     * @param type One of the following aggregation exception types:
     * {@link AggregationExceptions#TYPE_AUTOMATIC},
     * {@link AggregationExceptions#TYPE_KEEP_SEPARATE},
     * {@link AggregationExceptions#TYPE_KEEP_TOGETHER}
     * @param rawContactId1 Id of the first raw contact
     * @param rawContactId2 Id of the second raw contact
     */
    public static void setAggregationException(ContentResolver resolver, int type,
        long rawContactId1, long rawContactId2) {
        ContentValues values = new ContentValues();
        values.put(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        values.put(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        values.put(AggregationExceptions.TYPE, type);
        // Actually set the aggregation exception in the contacts database, and check that a
        // single row was updated.
        Assert.assertEquals(1, resolver.update(AggregationExceptions.CONTENT_URI, values, null,
                  null));
    }
}