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

package android.provider.cts.contacts.account;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.AndroidTestCase;

public class ContactsContract_Subquery extends AndroidTestCase {
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

    public void testProviderStatus_addedContacts() throws Exception {
        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();

        // Get the total row count.
        final int allCount;
        try (Cursor cursor = mResolver.query(Contacts.CONTENT_URI, null, null, null, null)) {
            allCount = cursor.getCount();
        }

        // Make sure CP2 gives the same result with an always-true subquery.
        try (Cursor cursor = mResolver.query(Contacts.CONTENT_URI, null,
                "exists(select 1)", null, null)) {
            assertEquals(allCount, cursor.getCount());
        }

        // Make sure CP2 returns no rows with an always-false subquery.
        try (Cursor cursor = mResolver.query(Contacts.CONTENT_URI, null,
                "not exists(select 1)", null, null)) {
            assertEquals(0, cursor.getCount());
        }
    }
}
