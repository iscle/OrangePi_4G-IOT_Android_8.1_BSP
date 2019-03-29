/*
 * Copyright (C) 2010 The Android Open Source Project
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


import static android.provider.ContactsContract.CommonDataKinds;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Callable;
import android.provider.ContactsContract.CommonDataKinds.Contactables;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Entity;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestData;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.InstrumentationTestCase;

import java.util.ArrayList;

public class ContactsContract_DataTest extends InstrumentationTestCase {
    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    static final String[] DATA_PROJECTION = new String[]{
            Data._ID,
            Data.RAW_CONTACT_ID,
            Data.CONTACT_ID,
            Data.NAME_RAW_CONTACT_ID,
            RawContacts.RAW_CONTACT_IS_USER_PROFILE,
            Data.DATA1,
            Data.DATA2,
            Data.DATA3,
            Data.DATA4,
            Data.DATA5,
            Data.DATA6,
            Data.DATA7,
            Data.DATA8,
            Data.DATA9,
            Data.DATA10,
            Data.DATA11,
            Data.DATA12,
            Data.DATA13,
            Data.DATA14,
            Data.DATA15,
            Data.CARRIER_PRESENCE,
            Data.DATA_VERSION,
            Data.IS_PRIMARY,
            Data.IS_SUPER_PRIMARY,
            Data.MIMETYPE,
            Data.RES_PACKAGE,
            Data.SYNC1,
            Data.SYNC2,
            Data.SYNC3,
            Data.SYNC4,
            GroupMembership.GROUP_SOURCE_ID,
            Data.PRESENCE,
            Data.CHAT_CAPABILITY,
            Data.STATUS,
            Data.STATUS_TIMESTAMP,
            Data.STATUS_RES_PACKAGE,
            Data.STATUS_LABEL,
            Data.STATUS_ICON,
            RawContacts.ACCOUNT_NAME,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
            RawContacts.DIRTY,
            RawContacts.SOURCE_ID,
            RawContacts.VERSION,
            Contacts.CUSTOM_RINGTONE,
            Contacts.DISPLAY_NAME,
            Contacts.DISPLAY_NAME_ALTERNATIVE,
            Contacts.DISPLAY_NAME_SOURCE,
            Contacts.IN_DEFAULT_DIRECTORY,
            Contacts.IN_VISIBLE_GROUP,
            Contacts.LAST_TIME_CONTACTED,
            Contacts.LOOKUP_KEY,
            Contacts.PHONETIC_NAME,
            Contacts.PHONETIC_NAME_STYLE,
            Contacts.PHOTO_ID,
            Contacts.PHOTO_FILE_ID,
            Contacts.PHOTO_URI,
            Contacts.PHOTO_THUMBNAIL_URI,
            Contacts.SEND_TO_VOICEMAIL,
            Contacts.SORT_KEY_ALTERNATIVE,
            Contacts.SORT_KEY_PRIMARY,
            Contacts.STARRED,
            Contacts.PINNED,
            Contacts.TIMES_CONTACTED,
            Contacts.HAS_PHONE_NUMBER,
            Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
            Contacts.CONTACT_PRESENCE,
            Contacts.CONTACT_CHAT_CAPABILITY,
            Contacts.CONTACT_STATUS,
            Contacts.CONTACT_STATUS_TIMESTAMP,
            Contacts.CONTACT_STATUS_RES_PACKAGE,
            Contacts.CONTACT_STATUS_LABEL,
            Contacts.CONTACT_STATUS_ICON,
            Data.TIMES_USED,
            Data.LAST_TIME_USED};

    static final String[] RAW_CONTACTS_ENTITY_PROJECTION = new String[]{
    };

    static final String[] NTITY_PROJECTION = new String[]{
    };

    private static ContentValues[] sContentValues = new ContentValues[7];
    static {
        ContentValues cv1 = new ContentValues();
        cv1.put(Contacts.DISPLAY_NAME, "Hot Tamale");
        cv1.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        cv1.put(Email.DATA, "tamale@acme.com");
        cv1.put(Email.TYPE, Email.TYPE_HOME);
        sContentValues[0] = cv1;

        ContentValues cv2 = new ContentValues();
        cv2.put(Contacts.DISPLAY_NAME, "Hot Tamale");
        cv2.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        cv2.put(Phone.DATA, "510-123-5769");
        cv2.put(Phone.TYPE, Phone.TYPE_HOME);
        sContentValues[1] = cv2;

        ContentValues cv3 = new ContentValues();
        cv3.put(Contacts.DISPLAY_NAME, "Hot Tamale");
        cv3.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        cv3.put(Email.DATA, "hot@google.com");
        cv3.put(Email.TYPE, Email.TYPE_WORK);
        sContentValues[2] = cv3;

        ContentValues cv4 = new ContentValues();
        cv4.put(Contacts.DISPLAY_NAME, "Cold Tamago");
        cv4.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        cv4.put(Email.DATA, "eggs@farmers.org");
        cv4.put(Email.TYPE, Email.TYPE_HOME);
        sContentValues[3] = cv4;

        ContentValues cv5 = new ContentValues();
        cv5.put(Contacts.DISPLAY_NAME, "John Doe");
        cv5.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        cv5.put(Email.DATA, "doeassociates@deer.com");
        cv5.put(Email.TYPE, Email.TYPE_WORK);
        sContentValues[4] = cv5;

        ContentValues cv6 = new ContentValues();
        cv6.put(Contacts.DISPLAY_NAME, "John Doe");
        cv6.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        cv6.put(Phone.DATA, "518-354-1111");
        cv6.put(Phone.TYPE, Phone.TYPE_HOME);
        sContentValues[5] = cv6;

        ContentValues cv7 = new ContentValues();
        cv7.put(Contacts.DISPLAY_NAME, "Cold Tamago");
        cv7.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
        cv7.put(SipAddress.DATA, "mysip@sipaddress.com");
        cv7.put(SipAddress.TYPE, SipAddress.TYPE_HOME);
        sContentValues[6] = cv7;
    }

    private TestRawContact[] mRawContacts = new TestRawContact[3];

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

    public void testGetLookupUriBySourceId() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.SOURCE_ID, "source_id")
                .insert();

        // TODO remove this. The method under test is currently broken: it will not
        // work without at least one data row in the raw contact.
        TestData data = rawContact.newDataRow(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .with(CommonDataKinds.StructuredName.DISPLAY_NAME, "test name")
                .insert();

        Uri lookupUri = Data.getContactLookupUri(mResolver, data.getUri());
        assertNotNull("Could not produce a lookup URI", lookupUri);

        TestContact lookupContact = mBuilder.newContact().setUri(lookupUri).load();
        assertEquals("Lookup URI matched the wrong contact",
                lookupContact.getId(), data.load().getRawContact().load().getContactId());
    }

    public void testDataProjection() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.SOURCE_ID, "source_id")
                .insert();
        TestData data = rawContact.newDataRow(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .with(CommonDataKinds.StructuredName.DISPLAY_NAME, "test name")
                .insert();

        DatabaseAsserts.checkProjection(mResolver, Data.CONTENT_URI,
                DATA_PROJECTION,
                new long[]{data.load().getId()}
        );
    }

    public void testRawContactsEntityProjection() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.SOURCE_ID, "source_id")
                .insert();
        TestData data = rawContact.newDataRow(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .with(CommonDataKinds.StructuredName.DISPLAY_NAME, "test name")
                .insert();

        DatabaseAsserts.checkProjection(mResolver, RawContactsEntity.CONTENT_URI,
                new String[]{
                        RawContacts._ID,
                        RawContacts.CONTACT_ID,
                        RawContacts.Entity.DATA_ID,
                        RawContacts.DELETED,
                        RawContacts.STARRED,
                        RawContacts.RAW_CONTACT_IS_USER_PROFILE,
                        RawContacts.ACCOUNT_NAME,
                        RawContacts.ACCOUNT_TYPE,
                        RawContacts.DATA_SET,
                        RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                        RawContacts.DIRTY,
                        RawContacts.SOURCE_ID,
                        RawContacts.BACKUP_ID,
                        RawContacts.VERSION,
                        RawContacts.SYNC1,
                        RawContacts.SYNC2,
                        RawContacts.SYNC3,
                        RawContacts.SYNC4,
                        Data.DATA1,
                        Data.DATA2,
                        Data.DATA3,
                        Data.DATA4,
                        Data.DATA5,
                        Data.DATA6,
                        Data.DATA7,
                        Data.DATA8,
                        Data.DATA9,
                        Data.DATA10,
                        Data.DATA11,
                        Data.DATA12,
                        Data.DATA13,
                        Data.DATA14,
                        Data.DATA15,
                        Data.CARRIER_PRESENCE,
                        Data.DATA_VERSION,
                        Data.IS_PRIMARY,
                        Data.IS_SUPER_PRIMARY,
                        Data.MIMETYPE,
                        Data.RES_PACKAGE,
                        Data.SYNC1,
                        Data.SYNC2,
                        Data.SYNC3,
                        Data.SYNC4,
                        GroupMembership.GROUP_SOURCE_ID},
                new long[]{rawContact.getId()}
        );
    }

    public void testEntityProjection() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.SOURCE_ID, "source_id")
                .insert();
        TestData data = rawContact.newDataRow(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .with(CommonDataKinds.StructuredName.DISPLAY_NAME, "test name")
                .insert();
        long contactId = rawContact.load().getContactId();

        DatabaseAsserts.checkProjection(mResolver, Contacts.CONTENT_URI.buildUpon().appendPath(
                        String.valueOf(contactId)).appendPath(
                        Entity.CONTENT_DIRECTORY).build(),
                new String[]{
                        Contacts.Entity._ID,
                        Contacts.Entity.CONTACT_ID,
                        Contacts.Entity.RAW_CONTACT_ID,
                        Contacts.Entity.DATA_ID,
                        Contacts.Entity.NAME_RAW_CONTACT_ID,
                        Contacts.Entity.DELETED,
                        Contacts.IS_USER_PROFILE,
                        Contacts.CUSTOM_RINGTONE,
                        Contacts.DISPLAY_NAME,
                        Contacts.DISPLAY_NAME_ALTERNATIVE,
                        Contacts.DISPLAY_NAME_SOURCE,
                        Contacts.IN_DEFAULT_DIRECTORY,
                        Contacts.IN_VISIBLE_GROUP,
                        Contacts.LAST_TIME_CONTACTED,
                        Contacts.LOOKUP_KEY,
                        Contacts.PHONETIC_NAME,
                        Contacts.PHONETIC_NAME_STYLE,
                        Contacts.PHOTO_ID,
                        Contacts.PHOTO_FILE_ID,
                        Contacts.PHOTO_URI,
                        Contacts.PHOTO_THUMBNAIL_URI,
                        Contacts.SEND_TO_VOICEMAIL,
                        Contacts.SORT_KEY_ALTERNATIVE,
                        Contacts.SORT_KEY_PRIMARY,
                        Contacts.STARRED,
                        Contacts.PINNED,
                        Contacts.TIMES_CONTACTED,
                        Contacts.HAS_PHONE_NUMBER,
                        Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
                        Contacts.CONTACT_PRESENCE,
                        Contacts.CONTACT_CHAT_CAPABILITY,
                        Contacts.CONTACT_STATUS,
                        Contacts.CONTACT_STATUS_TIMESTAMP,
                        Contacts.CONTACT_STATUS_RES_PACKAGE,
                        Contacts.CONTACT_STATUS_LABEL,
                        Contacts.CONTACT_STATUS_ICON,
                        RawContacts.ACCOUNT_NAME,
                        RawContacts.ACCOUNT_TYPE,
                        RawContacts.DATA_SET,
                        RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
                        RawContacts.DIRTY,
                        RawContacts.SOURCE_ID,
                        RawContacts.BACKUP_ID,
                        RawContacts.VERSION,
                        RawContacts.SYNC1,
                        RawContacts.SYNC2,
                        RawContacts.SYNC3,
                        RawContacts.SYNC4,
                        Data.DATA1,
                        Data.DATA2,
                        Data.DATA3,
                        Data.DATA4,
                        Data.DATA5,
                        Data.DATA6,
                        Data.DATA7,
                        Data.DATA8,
                        Data.DATA9,
                        Data.DATA10,
                        Data.DATA11,
                        Data.DATA12,
                        Data.DATA13,
                        Data.DATA14,
                        Data.DATA15,
                        Data.CARRIER_PRESENCE,
                        Data.DATA_VERSION,
                        Data.IS_PRIMARY,
                        Data.IS_SUPER_PRIMARY,
                        Data.MIMETYPE,
                        Data.RES_PACKAGE,
                        Data.SYNC1,
                        Data.SYNC2,
                        Data.SYNC3,
                        Data.SYNC4,
                        GroupMembership.GROUP_SOURCE_ID,
                        Data.PRESENCE,
                        Data.CHAT_CAPABILITY,
                        Data.STATUS,
                        Data.STATUS_TIMESTAMP,
                        Data.STATUS_RES_PACKAGE,
                        Data.STATUS_LABEL,
                        Data.STATUS_ICON,
                        Data.TIMES_USED,
                        Data.LAST_TIME_USED},
                new long[]{contactId}
        );
    }

    public void testGetLookupUriByDisplayName() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        TestData data = rawContact.newDataRow(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .with(CommonDataKinds.StructuredName.DISPLAY_NAME, "test name")
                .insert();

        Uri lookupUri = Data.getContactLookupUri(mResolver, data.getUri());
        assertNotNull("Could not produce a lookup URI", lookupUri);

        TestContact lookupContact = mBuilder.newContact().setUri(lookupUri).load();
        assertEquals("Lookup URI matched the wrong contact",
                lookupContact.getId(), data.load().getRawContact().load().getContactId());
    }

    public void testContactablesUri() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "test@test.com")
                .with(Email.TYPE, Email.TYPE_WORK)
                .insert();
        ContentValues cv = new ContentValues();
        cv.put(Email.DATA, "test@test.com");
        cv.put(Email.TYPE, Email.TYPE_WORK);

        Uri contentUri = ContactsContract.CommonDataKinds.Contactables.CONTENT_URI;
        try {
            assertCursorStoredValuesWithRawContactsFilter(contentUri,
                    new long[] {rawContact.getId()}, cv);
            rawContact.newDataRow(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .with(CommonDataKinds.StructuredPostal.DATA1, "100 Sesame Street")
                    .insert();

            rawContact.newDataRow(Phone.CONTENT_ITEM_TYPE)
                    .with(Phone.DATA, "123456789")
                    .with(Phone.TYPE, Phone.TYPE_MOBILE)
                    .insert();

            ContentValues cv2 = new ContentValues();
            cv.put(Phone.DATA, "123456789");
            cv.put(Phone.TYPE, Phone.TYPE_MOBILE);

            // Contactables Uri should return only email and phone data items.
            DatabaseAsserts.assertStoredValuesInUriMatchExactly(mResolver, contentUri, null,
                    Data.RAW_CONTACT_ID + "=?", new String[] {String.valueOf(rawContact.getId())},
                    null, false, cv, cv2);
        } finally {
            // Clean up
            rawContact.delete();
        }
    }

    public void testContactablesFilterByLastName_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri filterUri = Uri.withAppendedPath(Contactables.CONTENT_FILTER_URI, "tamale");
        assertCursorStoredValuesWithRawContactsFilter(filterUri, ids,
                ContactablesTestHelper.getContentValues(0));
    }

    public void testContactablesFilterByFirstName_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri filterUri = Uri.withAppendedPath(Contactables.CONTENT_FILTER_URI, "hot");
        assertCursorStoredValuesWithRawContactsFilter(filterUri, ids,
                ContactablesTestHelper.getContentValues(0));
        Uri filterUri2 = Uri.withAppendedPath(Contactables.CONTENT_FILTER_URI, "tam");
        assertCursorStoredValuesWithRawContactsFilter(filterUri2, ids,
                ContactablesTestHelper.getContentValues(0, 1));
    }

    public void testContactablesFilterByPhonePrefix_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri filterUri = Uri.withAppendedPath(Contactables.CONTENT_FILTER_URI, "518");
        assertCursorStoredValuesWithRawContactsFilter(filterUri, ids,
                ContactablesTestHelper.getContentValues(2));
        Uri filterUri2 = Uri.withAppendedPath(Contactables.CONTENT_FILTER_URI, "51");
        assertCursorStoredValuesWithRawContactsFilter(filterUri2, ids,
                ContactablesTestHelper.getContentValues(0, 2));
    }

    public void testContactablesFilterByEmailPrefix_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri filterUri = Uri.withAppendedPath(Contactables.CONTENT_FILTER_URI, "doeassoc");
        assertCursorStoredValuesWithRawContactsFilter(filterUri, ids,
                ContactablesTestHelper.getContentValues(2));
    }

    public void testContactablesFilter_doesNotExist_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri filterUri = Uri.withAppendedPath(Contactables.CONTENT_FILTER_URI, "doesnotexist");
        assertCursorStoredValuesWithRawContactsFilter(filterUri, ids, new ContentValues[0]);
    }

    /**
     * Verifies that Callable.CONTENT_URI returns only data items that can be called (i.e.
     * phone numbers and sip addresses)
     */
    public void testCallableUri_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri uri = Callable.CONTENT_URI;
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[1],
                sContentValues[5], sContentValues[6]);
    }

    public void testCallableFilterByNameOrOrganization_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri uri = Uri.withAppendedPath(Callable.CONTENT_FILTER_URI, "doe");
        // Only callables belonging to John Doe (name) and Cold Tamago (organization) are returned.
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[5],
                sContentValues[6]);
    }

    public void testCallableFilterByNumber_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri uri = Uri.withAppendedPath(Callable.CONTENT_FILTER_URI, "510");
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[1]);
    }

    public void testCallableFilterBySipAddress_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri uri = Uri.withAppendedPath(Callable.CONTENT_FILTER_URI, "mysip");
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[6]);
    }

    public void testEnterpriseCallableFilterByNameOrOrganization_returnsCorrectDataRows()
            throws Exception {
        long[] ids = setupContactablesTestData();
        Uri uri = Uri.withAppendedPath(Callable.ENTERPRISE_CONTENT_FILTER_URI, "doe").buildUpon()
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .build();
        // Only callables belonging to John Doe (name) and Cold Tamago (organization) are returned.
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[5],
                sContentValues[6]);
    }

    public void testEnterpriseCallableFilterByNumber_returnsCorrectDataRows() throws Exception {
        long[] ids = setupContactablesTestData();
        Uri uri = Uri.withAppendedPath(Callable.ENTERPRISE_CONTENT_FILTER_URI, "510").buildUpon()
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .build();
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[1]);
    }

    public void testEnterpriseCallableFilterBySipAddress_returnsCorrectDataRows()
            throws Exception {
        long[] ids = setupContactablesTestData();
        Uri uri = Uri.withAppendedPath(Callable.ENTERPRISE_CONTENT_FILTER_URI, "mysip").buildUpon()
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .build();
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[6]);
    }

    public void testDataInsert_updatesContactLastUpdatedTimestamp() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);
        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        SystemClock.sleep(1);
        createData(ids.mRawContactId);

        long newTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);
        assertTrue(newTime > baseTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testDataDelete_updatesContactLastUpdatedTimestamp() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long dataId = createData(ids.mRawContactId);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        SystemClock.sleep(1);
        DataUtil.delete(mResolver, dataId);

        long newTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);
        assertTrue(newTime > baseTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    /**
     * Tests that specifying the {@link android.provider.ContactsContract#REMOVE_DUPLICATE_ENTRIES}
     * boolean parameter correctly results in deduped phone numbers.
     */
    public void testPhoneQuery_removeDuplicateEntries() throws Exception{
        long[] ids = setupContactablesTestData();

        // Insert duplicate data entry for raw contact 3. (existing phone number 518-354-1111)
        mRawContacts[2].newDataRow(Phone.CONTENT_ITEM_TYPE)
                .with(Phone.DATA, "518-354-1111")
                .with(Phone.TYPE, Phone.TYPE_HOME)
                .insert();

        ContentValues dupe = new ContentValues();
        dupe.put(Contacts.DISPLAY_NAME, "John Doe");
        dupe.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        dupe.put(Phone.DATA, "518-354-1111");
        dupe.put(Phone.TYPE, Phone.TYPE_HOME);

        // Query for all phone numbers in the contacts database (without deduping).
        // The phone number above should be listed twice, in its duplicated forms.
        assertCursorStoredValuesWithRawContactsFilter(Phone.CONTENT_URI, ids, sContentValues[1],
                sContentValues[5], dupe);

        // Now query for all phone numbers in the contacts database but request deduping.
        // The phone number should now be listed only once.
        Uri uri = Phone.CONTENT_URI.buildUpon().
                appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").build();
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[1],
                sContentValues[5]);
    }

    /**
     * Tests that specifying the {@link android.provider.ContactsContract#REMOVE_DUPLICATE_ENTRIES}
     * boolean parameter correctly results in deduped email addresses.
     */
    public void testEmailQuery_removeDuplicateEntries() throws Exception{
        long[] ids = setupContactablesTestData();

        // Insert duplicate data entry for raw contact 3. (existing email doeassociates@deer.com)
        mRawContacts[2].newDataRow(Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "doeassociates@deer.com")
                .with(Email.TYPE, Email.TYPE_WORK)
                .insert();

        ContentValues dupe = new ContentValues();
        dupe.put(Contacts.DISPLAY_NAME, "John Doe");
        dupe.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        dupe.put(Email.DATA, "doeassociates@deer.com");
        dupe.put(Email.TYPE, Email.TYPE_WORK);

        // Query for all email addresses in the contacts database (without deduping).
        // The email address above should be listed twice, in its duplicated forms.
        assertCursorStoredValuesWithRawContactsFilter(Email.CONTENT_URI, ids, sContentValues[0],
                sContentValues[2], sContentValues[3], sContentValues[4], dupe);

        // Now query for all email addresses in the contacts database but request deduping.
        // The email address should now be listed only once.
        Uri uri = Email.CONTENT_URI.buildUpon().
                appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true").build();
        assertCursorStoredValuesWithRawContactsFilter(uri, ids, sContentValues[0],
                sContentValues[2], sContentValues[3], sContentValues[4]);
    }

    public void testDataUpdate_updatesContactLastUpdatedTimestamp() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);
        long dataId = createData(ids.mRawContactId);
        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        SystemClock.sleep(1);
        ContentValues values = new ContentValues();
        values.put(CommonDataKinds.Phone.NUMBER, "555-5555");
        DataUtil.update(mResolver, dataId, values);

        long newTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);
        assertTrue("Expected contact " + ids.mContactId + " last updated to be greater than " +
                baseTime + ". But was " + newTime, newTime > baseTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    private long createData(long rawContactId) {
        ContentValues values = new ContentValues();
        values.put(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        values.put(CommonDataKinds.Phone.NUMBER, "1-800-GOOG-411");
        values.put(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_CUSTOM);
        values.put(CommonDataKinds.Phone.LABEL, "free directory assistance");
        return DataUtil.insertData(mResolver, rawContactId, values);
    }

    private void assertCursorStoredValuesWithRawContactsFilter(Uri uri, long[] rawContactsId,
            ContentValues... expected) {
        // We need this helper function to add a filter for specific raw contacts because
        // otherwise tests will fail if performed on a device with existing contacts data
        StringBuilder sb = new StringBuilder();
        sb.append(Data.RAW_CONTACT_ID + " in ");
        sb.append("(");
        for (int i = 0; i < rawContactsId.length; i++) {
            if (i != 0) sb.append(",");
            sb.append(rawContactsId[i]);
        }
        sb.append(")");
        DatabaseAsserts.assertStoredValuesInUriMatchExactly(mResolver, uri, null, sb.toString(),
                null, null, false, expected);
    }


    private long[] setupContactablesTestData() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Hot Tamale")
                .insert();
        rawContact.newDataRow(Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "tamale@acme.com")
                .with(Email.TYPE, Email.TYPE_HOME)
                .insert();
        rawContact.newDataRow(Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "hot@google.com")
                .with(Email.TYPE, Email.TYPE_WORK)
                .insert();
        rawContact.newDataRow(Phone.CONTENT_ITEM_TYPE)
                .with(Phone.DATA, "510-123-5769")
                .with(Email.TYPE, Phone.TYPE_HOME)
                .insert();
        mRawContacts[0] = rawContact;

        TestRawContact rawContact2 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact2.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "Cold Tamago")
                .insert();
        rawContact2.newDataRow(Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "eggs@farmers.org")
                .with(Email.TYPE, Email.TYPE_HOME)
                .insert();
        rawContact2.newDataRow(SipAddress.CONTENT_ITEM_TYPE)
                .with(SipAddress.DATA, "mysip@sipaddress.com")
                .with(SipAddress.TYPE, SipAddress.TYPE_HOME)
                .insert();
        rawContact2.newDataRow(Organization.CONTENT_ITEM_TYPE)
                .with(Organization.COMPANY, "Doe Corp")
                .insert();
        mRawContacts[1] = rawContact2;

        TestRawContact rawContact3 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact3.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "John Doe")
                .insert();
        rawContact3.newDataRow(Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "doeassociates@deer.com")
                .with(Email.TYPE, Email.TYPE_WORK)
                .insert();
        rawContact3.newDataRow(Phone.CONTENT_ITEM_TYPE)
                .with(Phone.DATA, "518-354-1111")
                .with(Phone.TYPE, Phone.TYPE_HOME)
                .insert();
        rawContact3.newDataRow(Organization.CONTENT_ITEM_TYPE)
                .with(Organization.DATA, "Doe Industries")
                .insert();
        mRawContacts[2] = rawContact3;
        return new long[] {rawContact.getId(), rawContact2.getId(), rawContact3.getId()};
    }

    // Provides functionality to set up content values for the Contactables tests
    private static class ContactablesTestHelper {

        /**
         * @return An arraylist of contentValues that correspond to the provided raw contacts
         */
        public static ContentValues[] getContentValues(int... rawContacts) {
            ArrayList<ContentValues> cv = new ArrayList<ContentValues>();
            for (int i = 0; i < rawContacts.length; i++) {
                switch (rawContacts[i]) {
                    case 0:
                        // rawContact 0 "Hot Tamale" contains ContentValues 0, 1, and 2
                        cv.add(sContentValues[0]);
                        cv.add(sContentValues[1]);
                        cv.add(sContentValues[2]);
                        break;
                    case 1:
                        // rawContact 1 "Cold Tamago" contains ContentValues 3
                        cv.add(sContentValues[3]);
                        break;
                    case 2:
                        // rawContact 1 "John Doe" contains ContentValues 4, 5
                        cv.add(sContentValues[4]);
                        cv.add(sContentValues[5]);
                        break;
                }
            }
            ContentValues[] toReturn = new ContentValues[cv.size()];
            for (int i = 0; i < cv.size(); i++) {
                toReturn[i] = cv.get(i);
            }
            return toReturn;
        }
    }
}

