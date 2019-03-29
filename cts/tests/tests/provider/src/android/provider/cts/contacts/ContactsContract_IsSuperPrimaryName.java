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

import junit.framework.Assert;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestData;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.AndroidTestCase;

/**
 * CTS tests for the affect that {@link ContactsContract.Data#IS_SUPER_PRIMARY} has on names inside
 * aggregated contacts. Additionally, this needs to test the affect that aggregating contacts
 * together has on IS_SUPER_PRIMARY values in order to enforce the desired IS_SUPER_PRIMARY
 * behavior.
 */
public class ContactsContract_IsSuperPrimaryName extends AndroidTestCase {

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
        mBuilder.cleanup();
        super.tearDown();
    }

    public void testIsSuperPrimary_name1SuperPrimary() throws Exception {
        testInner_displayNameFromIsSuperPrimary(/* isFirstNamePrimary = */ true, "name1", "name2");
    }

    public void testIsSuperPrimary_name2SuperPrimary() throws Exception {
        testInner_displayNameFromIsSuperPrimary(/* isFirstNamePrimary = */ false, "name2", "name1");
    }

    private void testInner_displayNameFromIsSuperPrimary(boolean isFirstNamePrimary,
            String expectedDisplayName, String otherDisplayName) throws Exception {

        // Setup: two raw contacts. One with a super primary name. One without.
        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name1 = rawContact1.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "name1")
                .with(StructuredName.IS_SUPER_PRIMARY, isFirstNamePrimary ? 1 : 0)
                .insert();
        rawContact1.load();
        name1.load();

        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name2 = rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "name2")
                .with(StructuredName.IS_SUPER_PRIMARY, !isFirstNamePrimary ? 1 : 0)
                .insert();
        rawContact2.load();
        name2.load();

        // Execute: aggregate the two raw contacts together
        setAggregationException(rawContact1.getId(), rawContact2.getId());

        // Sanity check: two contacts are aggregated
        rawContact1.load();
        rawContact2.load();
        Assert.assertEquals(rawContact1.getContactId(), rawContact2.getContactId());

        // Verify: the IS_SUPER_PRIMARY values are maintained after the merge
        name1.assertColumn(StructuredName.IS_SUPER_PRIMARY, isFirstNamePrimary ? 1 : 0);
        name2.assertColumn(StructuredName.IS_SUPER_PRIMARY, !isFirstNamePrimary ? 1 : 0);

        // Verify: the display name is taken from the name with is_super_primary
        TestContact contact = rawContact2.getContact().load();
        contact.assertColumn(Contacts.DISPLAY_NAME, expectedDisplayName);

        //
        // Now test what happens when you change IS_SUPER_PRIMARY on an existing contact
        //

        // Execute: make the non primary name IS_SUPER_PRIMARY
        TestData nonPrimaryName = !isFirstNamePrimary ? name1 : name2;
        ContentValues values = new ContentValues();
        values.put(StructuredName.IS_SUPER_PRIMARY, 1);
        mResolver.update(nonPrimaryName.getUri(), values, null, null);

        // Verify: the IS_SUPER_PRIMARY values swap
        name1.load();
        name2.load();
        name1.assertColumn(StructuredName.IS_SUPER_PRIMARY, isFirstNamePrimary ? 0 : 1);
        name2.assertColumn(StructuredName.IS_SUPER_PRIMARY, !isFirstNamePrimary ? 0 : 1);

        // Verify: the display name is taken from the name with is_super_primary
        contact.load();
        contact.assertColumn(Contacts.DISPLAY_NAME, otherDisplayName);
    }

    public void testIsSuperPrimaryName_mergeBothSuperPrimary() throws Exception {
        // Setup: two raw contacts. Both names are super primary.
        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name1 = rawContact1.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "name1")
                .with(StructuredName.IS_SUPER_PRIMARY, 1)
                .insert();
        rawContact1.load();
        name1.load();

        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name2 = rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "name2")
                .with(StructuredName.IS_SUPER_PRIMARY, 1)
                .insert();
        rawContact2.load();
        name2.load();

        // Execute: aggregate the two contacts together
        setAggregationException(rawContact1.getId(), rawContact2.getId());

        // Sanity check: two contacts are aggregated
        rawContact1.load();
        rawContact2.load();
        Assert.assertEquals(rawContact1.getContactId(), rawContact2.getContactId());

        // Verify: both names are no longer super primary.
        name1.load();
        name2.load();
        name1.assertColumn(StructuredName.IS_SUPER_PRIMARY, 0);
        name2.assertColumn(StructuredName.IS_SUPER_PRIMARY, 0);
    }

    private void setAggregationException(long rawContactId1, long rawContactId2) {
        ContentValues values = new ContentValues();
        values.put(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        values.put(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        values.put(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        mResolver.update(AggregationExceptions.CONTENT_URI, values, null, null);
    }
}
