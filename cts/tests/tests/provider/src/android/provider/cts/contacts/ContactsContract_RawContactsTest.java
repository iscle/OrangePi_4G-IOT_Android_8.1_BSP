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


import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

public class ContactsContract_RawContactsTest extends AndroidTestCase {
    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    private static final String[] RAW_CONTACTS_PROJECTION = new String[]{
            RawContacts._ID,
            RawContacts.CONTACT_ID,
            RawContacts.DELETED,
            RawContacts.DISPLAY_NAME_PRIMARY,
            RawContacts.DISPLAY_NAME_ALTERNATIVE,
            RawContacts.DISPLAY_NAME_SOURCE,
            RawContacts.PHONETIC_NAME,
            RawContacts.PHONETIC_NAME_STYLE,
            RawContacts.SORT_KEY_PRIMARY,
            RawContacts.SORT_KEY_ALTERNATIVE,
            RawContacts.TIMES_CONTACTED,
            RawContacts.LAST_TIME_CONTACTED,
            RawContacts.CUSTOM_RINGTONE,
            RawContacts.SEND_TO_VOICEMAIL,
            RawContacts.STARRED,
            RawContacts.PINNED,
            RawContacts.AGGREGATION_MODE,
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
            RawContacts.SYNC4
    };

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

    public void testGetLookupUriBySourceId() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.SOURCE_ID, "source_id")
                .insert();

        // TODO remove this. The method under test is currently broken: it will not
        // work without at least one data row in the raw contact.
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "test name")
                .insert();

        Uri lookupUri = RawContacts.getContactLookupUri(mResolver, rawContact.getUri());
        assertNotNull("Could not produce a lookup URI", lookupUri);

        TestContact lookupContact = mBuilder.newContact().setUri(lookupUri).load();
        assertEquals("Lookup URI matched the wrong contact",
                lookupContact.getId(), rawContact.load().getContactId());
    }

    public void testGetLookupUriByDisplayName() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "test name")
                .insert();

        Uri lookupUri = RawContacts.getContactLookupUri(mResolver, rawContact.getUri());
        assertNotNull("Could not produce a lookup URI", lookupUri);

        TestContact lookupContact = mBuilder.newContact().setUri(lookupUri).load();
        assertEquals("Lookup URI matched the wrong contact",
                lookupContact.getId(), rawContact.load().getContactId());
    }

    public void testRawContactDelete_setsDeleteFlag() {
        long rawContactid = RawContactUtil.insertRawContact(mResolver,
                StaticAccountAuthenticator.ACCOUNT_1);

        assertTrue(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        RawContactUtil.delete(mResolver, rawContactid, false);

        String[] projection = new String[]{
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.DELETED
        };
        String[] result = RawContactUtil.queryByRawContactId(mResolver, rawContactid,
                projection);

        // Contact id should be null
        assertNull(result[0]);
        // Record should be marked deleted.
        assertEquals("1", result[1]);
    }

    public void testRawContactDelete_removesRecord() {
        long rawContactid = RawContactUtil.insertRawContact(mResolver,
                StaticAccountAuthenticator.ACCOUNT_1);
        assertTrue(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        RawContactUtil.delete(mResolver, rawContactid, true);

        assertFalse(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        // already clean
    }


    // This implicitly tests the Contact create case.
    public void testRawContactCreate_updatesContactUpdatedTimestamp() {
        long startTime = System.currentTimeMillis();

        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);
        long lastUpdated = getContactLastUpdatedTimestampByRawContactId(mResolver,
                ids.mRawContactId);

        assertTrue(lastUpdated > startTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testRawContactUpdate_updatesContactUpdatedTimestamp() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.STARRED, 1);
        RawContactUtil.update(mResolver, ids.mRawContactId, values);

        long newTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);
        assertTrue(newTime > baseTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testRawContactPsuedoDelete_hasDeleteLogForContact() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        RawContactUtil.delete(mResolver, ids.mRawContactId, false);

        DatabaseAsserts.assertHasDeleteLogGreaterThan(mResolver, ids.mContactId, baseTime);

        // clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testRawContactDelete_hasDeleteLogForContact() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        RawContactUtil.delete(mResolver, ids.mRawContactId, true);

        DatabaseAsserts.assertHasDeleteLogGreaterThan(mResolver, ids.mContactId, baseTime);

        // already clean
    }

    private long getContactLastUpdatedTimestampByRawContactId(ContentResolver resolver,
            long rawContactId) {
        long contactId = RawContactUtil.queryContactIdByRawContactId(mResolver, rawContactId);
        MoreAsserts.assertNotEqual(CommonDatabaseUtils.NOT_FOUND, contactId);

        return ContactUtil.queryContactLastUpdatedTimestamp(mResolver, contactId);
    }

    public void testProjection() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "test name")
                .insert();

        DatabaseAsserts.checkProjection(mResolver, RawContacts.CONTENT_URI,
                RAW_CONTACTS_PROJECTION,
                new long[]{rawContact.getId()}
        );
    }

    public void testInsertUsageStat() throws Exception {
        final long now = System.currentTimeMillis();
        {
            TestRawContact rawContact = mBuilder.newRawContact()
                    .with(RawContacts.ACCOUNT_TYPE, "test_type")
                    .with(RawContacts.ACCOUNT_NAME, "test_name")
                    .with(RawContacts.TIMES_CONTACTED, 12345)
                    .with(RawContacts.LAST_TIME_CONTACTED, now)
                    .insert();

            rawContact.load();
            assertEquals(12340, rawContact.getLong(RawContacts.TIMES_CONTACTED));
            assertEquals(now / 86400 * 86400, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
        }

        {
            TestRawContact rawContact = mBuilder.newRawContact()
                    .with(RawContacts.ACCOUNT_TYPE, "test_type")
                    .with(RawContacts.ACCOUNT_NAME, "test_name")
                    .with(RawContacts.TIMES_CONTACTED, 5)
                    .insert();

            rawContact.load();
            assertEquals(5L, rawContact.getLong(RawContacts.TIMES_CONTACTED));
            assertEquals(0, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
        }
        {
            TestRawContact rawContact = mBuilder.newRawContact()
                    .with(RawContacts.ACCOUNT_TYPE, "test_type")
                    .with(RawContacts.ACCOUNT_NAME, "test_name")
                    .with(RawContacts.LAST_TIME_CONTACTED, now)
                    .insert();

            rawContact.load();
            assertEquals(0L, rawContact.getLong(RawContacts.TIMES_CONTACTED));
            assertEquals(now / 86400 * 86400, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
        }
    }

    public void testUpdateUsageStat() throws Exception {
        ContentValues values = new ContentValues();

        final long now = System.currentTimeMillis();
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.TIMES_CONTACTED, 12345)
                .with(RawContacts.LAST_TIME_CONTACTED, now)
                .insert();

        rawContact.load();
        assertEquals(12340L, rawContact.getLong(RawContacts.TIMES_CONTACTED));
        assertEquals(now / 86400 * 86400, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));

        values.clear();
        values.put(RawContacts.TIMES_CONTACTED, 99999);
        RawContactUtil.update(mResolver, rawContact.getId(), values);

        rawContact.load();
        assertEquals(99990L, rawContact.getLong(RawContacts.TIMES_CONTACTED));
        assertEquals(now / 86400 * 86400, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));

        values.clear();
        values.put(RawContacts.LAST_TIME_CONTACTED, now + 86400);
        RawContactUtil.update(mResolver, rawContact.getId(), values);

        rawContact.load();
        assertEquals(99990L, rawContact.getLong(RawContacts.TIMES_CONTACTED));
        assertEquals((now / 86400 * 86400) + 86400,
                rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
    }
}
