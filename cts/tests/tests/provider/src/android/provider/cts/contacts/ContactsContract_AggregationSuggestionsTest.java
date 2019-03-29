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

package android.provider.cts.contacts;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.AndroidTestCase;

/**
 * CTS tests for {@link android.provider.ContactsContract.Contacts.AggregationSuggestions} APIs.
 */
public class ContactsContract_AggregationSuggestionsTest extends AndroidTestCase {
    private static final String[] TEST_PROJECTION
            = new String[] {Contacts.DISPLAY_NAME, Contacts._ID};

    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getContext().getContentResolver();
        ContentProviderClient provider =
                mResolver.acquireContentProviderClient(ContactsContract.AUTHORITY);
        mBuilder = new ContactsContract_TestDataBuilder(provider);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mBuilder.cleanup();
    }

    // Copy of CP2's ContactAggregatorTest#testAggregationSuggestionsByName unit test.
    public void testAggregationSuggestionsByNameReversed() throws Exception {
        long [] contactIds = setupThreeContacts();

        // Setup: create query with first and last name reversed.
        Uri uri = AggregationSuggestions.builder()
                .addNameParameter("last1 first1")
                .build();

        // Execute
        Cursor cursor = mResolver.query(uri, TEST_PROJECTION, null, null, null);

        // Verify: correct contact is returned.
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Contacts._ID, contactIds[0]);
        values.put(Contacts.DISPLAY_NAME, "first1 last1");
        DatabaseAsserts.assertCursorValuesMatchExactly(cursor, values);
        cursor.close();
    }


    public void testAggregationSuggestionsByName() throws Exception {
        long [] contactIds = setupThreeContacts();

        // Setup: create query with first and last name in same order as display name.
        Uri uri = AggregationSuggestions.builder()
                .addNameParameter("first1 last1")
                .build();

        // Execute
        Cursor cursor = mResolver.query(uri, TEST_PROJECTION, null, null, null);

        // Verify: correct contact is returned.
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        ContentValues values = new ContentValues();
        values.put(Contacts._ID, contactIds[0]);
        values.put(Contacts.DISPLAY_NAME, "first1 last1");
        DatabaseAsserts.assertCursorValuesMatchExactly(cursor, values);
        cursor.close();
    }

    public void testAggregationSuggestionsByName_noMatch() throws Exception {
        setupThreeContacts();

        // Setup: query with name that is completely different than all the contacts.
        Uri uri = AggregationSuggestions.builder()
                .addNameParameter("unmatched name")
                .build();

        // Execute
        Cursor cursor = mResolver.query(
                uri, new String[] { Contacts._ID, Contacts.DISPLAY_NAME }, null, null, null);

        // Verify: no matches.
        assertEquals(0, cursor.getCount());
    }

    public void testAggregationSuggestionsByName_matchSecondNameParameter() throws Exception {
        long [] contactIds = setupThreeContacts();

        // Setup: query with two names. The first name is completely unlike all the contacts.
        Uri uri = AggregationSuggestions.builder()
                .addNameParameter("unmatched name")
                .addNameParameter("first2 last2")
                .build();

        // Execute
        Cursor cursor = mResolver.query(uri, TEST_PROJECTION, null, null, null);

        // Verify: the second name parameter matches a contact.
        assertEquals(1, cursor.getCount());
        ContentValues values = new ContentValues();
        values.put(Contacts._ID, contactIds[1]);
        values.put(Contacts.DISPLAY_NAME, "first2 last2");
        DatabaseAsserts.assertCursorValuesMatchExactly(cursor, values);
        cursor.close();
    }

    public void testAggregationSuggestionsByName_matchFirstNameParameter() throws Exception {
        long [] contactIds = setupThreeContacts();

        // Setup: query with two names. The second name is completely unlike all the contacts.
        Uri uri = AggregationSuggestions.builder()
                .addNameParameter("first2 last2")
                .addNameParameter("unmatched name")
                .build();

        // Execute
        Cursor cursor = mResolver.query(uri, TEST_PROJECTION, null, null, null);

        // Verify: the first name parameter matches a contact.
        assertEquals(1, cursor.getCount());
        ContentValues values = new ContentValues();
        values.put(Contacts._ID, contactIds[1]);
        values.put(Contacts.DISPLAY_NAME, "first2 last2");
        DatabaseAsserts.assertCursorValuesMatchExactly(cursor, values);
        cursor.close();
    }

    private long[] setupThreeContacts() throws Exception {
        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact1.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "first1")
                .with(StructuredName.FAMILY_NAME, "last1")
                .insert();
        rawContact1.load();

        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "first2")
                .with(StructuredName.FAMILY_NAME, "last2")
                .insert();
        rawContact2.load();

        TestRawContact rawContact3 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact3.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "completely")
                .with(StructuredName.FAMILY_NAME, "different")
                .insert();
        rawContact3.load();

        return new long[] {rawContact1.getContactId(),
                rawContact2.getContactId(), rawContact3.getContactId()};
    }

    public void testAggregationSuggestionsQueryBuilderWithContactId() throws Exception {
        Uri uri = new AggregationSuggestions.Builder().setContactId(12).setLimit(7).build();
        assertEquals("content://com.android.contacts/contacts/12/suggestions?limit=7",
                uri.toString());
    }

    public void testAggregationSuggestionsQueryBuilderWithValues() throws Exception {
        Uri uri = new AggregationSuggestions.Builder()
                .addNameParameter("name1")
                .addNameParameter("name2")
                .setLimit(7)
                .build();
        assertEquals("content://com.android.contacts/contacts/0/suggestions?"
                + "limit=7"
                + "&query=name%3Aname1"
                + "&query=name%3Aname2", uri.toString());
    }
}

