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
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestData;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.AndroidTestCase;

/**
 * CTS tests for {@link DisplayNameSources#STRUCTURED_PHONETIC_NAME}.
 */
public class ContactsContract_StructuredPhoneticName extends AndroidTestCase {
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

    public void testPhoneticStructuredName() throws Exception {
        // Setup: contact with only phonetic name
        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name1 = rawContact1.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.PHONETIC_FAMILY_NAME, "phonetic name")
                .insert();
        rawContact1.load();
        name1.load();

        // Verify: DISPLAY_NAME_SOURCE notices that the StructuredName only has phonetic components
        TestContact contact = rawContact1.getContact().load();
        contact.assertColumn(Contacts.DISPLAY_NAME_SOURCE,
                DisplayNameSources.STRUCTURED_PHONETIC_NAME);
    }

    public void testPhoneticNameStyleColumnName() throws Exception {
        // Make sure the column name is data11 and not phonetic_name_style
        // from the parent class.
        assertEquals(Data.DATA11, StructuredName.PHONETIC_NAME_STYLE);
    }

    public void testPhoneticStructuredName_phoneticPriority1() throws Exception {
        // Setup: one raw contact has a complex phonetic name and the other a simple given name
        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name1 = rawContact1.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "name")
                .insert();
        rawContact1.load();
        name1.load();

        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name2 = rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.PHONETIC_FAMILY_NAME, "name phonetic")
                .insert();
        rawContact2.load();
        name2.load();

        // Execute: aggregate the two raw contacts together
        ContactUtil.setAggregationException(mResolver, AggregationExceptions.TYPE_KEEP_TOGETHER,
                rawContact1.getId(), rawContact2.getId());

        // Sanity check: two contacts are aggregated
        rawContact1.load();
        rawContact2.load();
        Assert.assertEquals(rawContact1.getContactId(), rawContact2.getContactId());

        // Verify: the display name is taken from the name with more than phonetic components
        TestContact contact = rawContact2.getContact().load();
        contact.assertColumn(Contacts.DISPLAY_NAME, "name");
    }

    // Same as testPhoneticStructuredName_phoneticPriority1, but with setup order reversed
    public void testPhoneticStructuredName_phoneticPriority2() throws Exception {
        // Setup: one raw contact has a complex phonetic name and the other a simple given name
        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name2 = rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.PHONETIC_FAMILY_NAME, "name phonetic")
                .insert();
        rawContact2.load();
        name2.load();

        TestRawContact rawContact1 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData name1 = rawContact1.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "name")
                .insert();
        rawContact1.load();
        name1.load();

        // Execute: aggregate the two raw contacts together
        ContactUtil.setAggregationException(mResolver, AggregationExceptions.TYPE_KEEP_TOGETHER,
                rawContact1.getId(), rawContact2.getId());

        // Sanity check: two contacts are aggregated
        rawContact1.load();
        rawContact2.load();
        Assert.assertEquals(rawContact1.getContactId(), rawContact2.getContactId());

        // Verify: the display name is taken from the name with more than phonetic components
        TestContact contact = rawContact2.getContact().load();
        contact.assertColumn(Contacts.DISPLAY_NAME, "name");
    }
}
