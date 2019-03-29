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
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.AndroidTestCase;

/**
 * CTS tests for {@link android.provider.ContactsContract.ProviderStatus}.
 *
 * Unfortunately, we can't check that the value of ProviderStatus equals
 * {@link ProviderStatus#STATUS_EMPTY} initially. Some carriers pre-install
 * accounts. As a result, the value STATUS_EMPTY will never be achieved.
 */
public class ContactsContract_ProviderStatus extends AndroidTestCase {
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
        // Setup: add a contact to CP2.
        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact1.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "first1")
                .with(StructuredName.FAMILY_NAME, "last1")
                .insert();

        // Execute: fetch CP2 status
        Cursor cursor = mResolver.query(ProviderStatus.CONTENT_URI, null, null, null, null);

        // Verify: CP2 status is normal instead of STATUS_EMPTY.
        try {
            assertEquals(1, cursor.getCount());
            cursor.moveToFirst();
            ContentValues values = new ContentValues();
            values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_NORMAL);
            DatabaseAsserts.assertCursorValuesMatchExactly(cursor, values);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
