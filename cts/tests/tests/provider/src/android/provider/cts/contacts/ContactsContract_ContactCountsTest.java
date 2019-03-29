/*
 * Copyright (C) 2014 The Android Open Source Project
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


import java.util.Arrays;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.InstrumentationTestCase;

/**
 * CTS tests for {@link android.provider.ContactsContract.ContactCounts} apis.
 */
public class ContactsContract_ContactCountsTest extends InstrumentationTestCase {
    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    final String[] TEST_PROJECTION = new String[] {Contacts.DISPLAY_NAME};

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getInstrumentation().getTargetContext().getContentResolver();
        ContentProviderClient provider =
                mResolver.acquireContentProviderClient(ContactsContract.AUTHORITY);
        mBuilder = new ContactsContract_TestDataBuilder(provider);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mBuilder.cleanup();
    }

    public void testContactCounts_noExtraNoExtrasReturned() throws Exception {
        final String filterString = getFilterString(setupTestData());
        final Cursor cursor = mResolver.query(Contacts.CONTENT_URI, TEST_PROJECTION,
                filterString, null, null);
        try {
            final Bundle extras = cursor.getExtras();
            assertFalse(extras.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS));
            assertFalse(extras.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES));
        } finally {
            cursor.close();
        }
    }

    public void testContactCounts_correctCountsReturned() throws Exception {
        final String filterString = getFilterString(setupTestData());
        final Uri uri = Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true").build();
        final Cursor cursor = mResolver.query(uri, TEST_PROJECTION,
                filterString, null, null);
        try {
            final Bundle extras = cursor.getExtras();
            assertTrue(extras.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS));
            assertTrue(extras.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES));

            final String[] expectedSections = new String[] {"A", "B", "C"};
            final String sections[] =
                    extras.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            assertTrue(Arrays.equals(expectedSections, sections));

            final int[] expectedCounts = new int[] {2, 3, 1};
            final int counts[] = extras.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            assertTrue(Arrays.equals(expectedCounts, counts));
        } finally {
            cursor.close();
        }
    }

    private String getFilterString(long... contactIds) {
        StringBuilder sb = new StringBuilder();
        sb.append(Contacts._ID + " in ");
        sb.append("(");
        for (int i = 0; i < contactIds.length; i++) {
            if (i != 0) sb.append(",");
            sb.append(contactIds[i]);
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Setup the contacts database with temporary contacts used for testing. These contacts will
     * be removed during teardown.
     *
     * @return An array of long values corresponding to the ids of the created contacts
     *
     * @throws Exception
     */
    private long[] setupTestData() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Apple Pie")
                .insert();
        rawContact.load();
        TestContact contact = rawContact.getContact().load();

        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Banana Split")
                .insert();
        rawContact2.load();
        TestContact contact2 = rawContact2.getContact().load();

        TestRawContact rawContact3 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact3.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Blackberry Shortcake")
                .insert();
        rawContact3.load();
        TestContact contact3 = rawContact3.getContact().load();

        TestRawContact rawContact4 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact4.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Cherry icecream")
                .insert();
        rawContact4.load();
        TestContact contact4 = rawContact4.getContact().load();

        TestRawContact rawContact5 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact5.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Apricot Jam ")
                .insert();
        rawContact5.load();
        TestContact contact5 = rawContact5.getContact().load();

        TestRawContact rawContact6 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact6.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Blackcurrant Pie ")
                .insert();
        rawContact6.load();
        TestContact contact6 = rawContact6.getContact().load();

        return new long[] {contact.getId(), contact2.getId(), contact3.getId(), contact4.getId(),
                contact5.getId(), contact6.getId()};
    }
}

