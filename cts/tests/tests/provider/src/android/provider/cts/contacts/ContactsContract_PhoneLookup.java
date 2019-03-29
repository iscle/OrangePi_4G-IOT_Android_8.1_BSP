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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.AndroidTestCase;

/**
 * Test for {@link android.provider.ContactsContract.PhoneLookup}.
 * <p>
 * This covers {@link PhoneLookup#CONTENT_FILTER_URI} and
 * {@link PhoneLookup#ENTERPRISE_CONTENT_FILTER_URI}.
 *
 * TODO We don't yet have tests to cover cross-user provider access for the later, since multi-user
 * cases aren't well supported in CTS yet.  Tracking in internal bug/16462089 .
 */
public class ContactsContract_PhoneLookup extends AndroidTestCase {
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

    static class Id {
        public long contactId;
        public long dataId;

        public Id (long contactId, long dataId) {
            this.contactId = contactId;
            this.dataId = dataId;
        }
    }

    private Id[] setupTestData() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Hot Tamale")
                .insert();
        long dataId = rawContact.newDataRow(Phone.CONTENT_ITEM_TYPE)
                .with(Phone.DATA, "1111222333444")
                .with(Email.TYPE, Phone.TYPE_HOME)
                .insert().load().getId();
        rawContact.load();
        TestContact contact = rawContact.getContact().load();

        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Cold Tamago")
                .insert();
       long dataId2 =  rawContact2.newDataRow(Phone.CONTENT_ITEM_TYPE)
                .with(Phone.DATA, "2111222333444")
                .with(Phone.TYPE, Phone.TYPE_OTHER)
                .insert().load().getId();
        rawContact2.load();
        TestContact contact2 = rawContact2.getContact().load();

        // Contact with SIP address
        TestRawContact rawContact3 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact3.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Warm Tempura")
                .insert();
        long dataId3 = rawContact2.newDataRow(SipAddress.CONTENT_ITEM_TYPE)
                .with(SipAddress.SIP_ADDRESS, "777@sip.org")
                .with(SipAddress.TYPE, SipAddress.TYPE_WORK)
                .insert().load().getId();
        rawContact3.load();
        TestContact contact3 = rawContact2.getContact().load();

        return new Id[] {
                new Id(contact.getId(), dataId),
                new Id(contact2.getId(), dataId2),
                new Id(contact3.getId(), dataId3)
        };

    }

    /**
     * Test for {@link android.provider.ContactsContract.PhoneLookup#CONTENT_FILTER_URI}.
     */
    public void testPhoneLookup_nomatch() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath("no-such-phone-number").build();

        assertCursorStoredValuesWithContactsFilter(uri, ids /*, empty */);
    }

    /**
     * Test for {@link android.provider.ContactsContract.PhoneLookup#CONTENT_FILTER_URI}.
     */
    public void testPhoneLookup_found1() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath("1111222333444").build();

        final ContentValues expected = new ContentValues();
        expected.put(PhoneLookup._ID, ids[0].contactId);
        expected.put(PhoneLookup.CONTACT_ID, ids[0].contactId);
        expected.put(PhoneLookup.DATA_ID, ids[0].dataId);
        expected.put(PhoneLookup.NUMBER, "1111222333444");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    /**
     * Test for {@link android.provider.ContactsContract.PhoneLookup#CONTENT_FILTER_URI}.
     */
    public void testPhoneLookup_found2() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath("2111222333444").build();

        final ContentValues expected = new ContentValues();
        expected.put(PhoneLookup._ID, ids[1].contactId);
        expected.put(PhoneLookup.CONTACT_ID, ids[1].contactId);
        expected.put(PhoneLookup.DATA_ID, ids[1].dataId);
        expected.put(PhoneLookup.NUMBER, "2111222333444");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testPhoneLookup_sip_found() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                .appendPath("777@sip.org")
                .appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, "1")
                .build();

        final ContentValues expected = new ContentValues();
        expected.put(PhoneLookup.CONTACT_ID, ids[2].contactId);
        expected.put(PhoneLookup.DATA_ID, ids[2].dataId);
        expected.put(SipAddress.SIP_ADDRESS, "777@sip.org");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    /**
     * Test for {@link android.provider.ContactsContract.PhoneLookup#ENTERPRISE_CONTENT_FILTER_URI}.
     */
    public void testPhoneLookupEnterprise_nomatch() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("no-such-phone-number").build();

        assertCursorStoredValuesWithContactsFilter(uri, ids /*, empty */);
    }

    /**
     * Test for {@link android.provider.ContactsContract.PhoneLookup#ENTERPRISE_CONTENT_FILTER_URI}.
     */
    public void testPhoneLookupEnterprise_found1() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("1111222333444").build();

        final ContentValues expected = new ContentValues();
        expected.put(PhoneLookup._ID, ids[0].contactId);
        expected.put(PhoneLookup.CONTACT_ID, ids[0].contactId);
        expected.put(PhoneLookup.DATA_ID, ids[0].dataId);
        expected.put(PhoneLookup.NUMBER, "1111222333444");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    /**
     * Test for {@link android.provider.ContactsContract.PhoneLookup#ENTERPRISE_CONTENT_FILTER_URI}.
     */
    public void testPhoneLookupEnterprise_found2() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("2111222333444").build();

        final ContentValues expected = new ContentValues();
        expected.put(PhoneLookup._ID, ids[1].contactId);
        expected.put(PhoneLookup.CONTACT_ID, ids[1].contactId);
        expected.put(PhoneLookup.DATA_ID, ids[1].dataId);
        expected.put(PhoneLookup.NUMBER, "2111222333444");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testPhoneLookupEnterprise_sip_found() throws Exception {
        Id[] ids = setupTestData();
        final Uri uri = PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("777@sip.org")
                .appendQueryParameter(PhoneLookup.QUERY_PARAMETER_SIP_ADDRESS, "1")
                .build();

        final ContentValues expected = new ContentValues();
        expected.put(PhoneLookup._ID, ids[2].dataId);
        expected.put(PhoneLookup.CONTACT_ID, ids[2].contactId);
        expected.put(PhoneLookup.DATA_ID, ids[2].dataId);
        expected.put(SipAddress.SIP_ADDRESS, "777@sip.org");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    private void assertCursorStoredValuesWithContactsFilter(Uri uri, Id[] ids,
            ContentValues... expected) {
        // We need this helper function to add a filter for specific contacts because
        // otherwise tests will fail if performed on a device with existing contacts data
        StringBuilder sb = new StringBuilder();
        sb.append(Contacts._ID + " in ");
        sb.append("(");
        for (int i = 0; i < ids.length; i++) {
            if (i != 0) sb.append(",");
            sb.append(ids[i].contactId);
        }
        sb.append(")");
        DatabaseAsserts.assertStoredValuesInUriMatchExactly(mResolver, uri, null,
                sb.toString(), null, null, false, expected);
    }
}
