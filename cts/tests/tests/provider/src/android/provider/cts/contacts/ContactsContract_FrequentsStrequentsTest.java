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
 * limitations under the License.
 */

package android.provider.cts.contacts;


import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DataUsageFeedback;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.InstrumentationTestCase;

/**
 * CTS tests for {@link android.provider.ContactsContract.Contacts#CONTENT_FREQUENT_URI},
 * {@link android.provider.ContactsContract.Contacts#CONTENT_STREQUENT_URI} and
 * {@link android.provider.ContactsContract.Contacts#CONTENT_STREQUENT_FILTER_URI} apis.
 */
public class ContactsContract_FrequentsStrequentsTest extends InstrumentationTestCase {
    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    private static final String[] STREQUENT_PROJECTION = new String[]{
            Contacts._ID,
            Contacts.HAS_PHONE_NUMBER,
            Contacts.NAME_RAW_CONTACT_ID,
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
            Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
            Contacts.CONTACT_PRESENCE,
            Contacts.CONTACT_CHAT_CAPABILITY,
            Contacts.CONTACT_STATUS,
            Contacts.CONTACT_STATUS_TIMESTAMP,
            Contacts.CONTACT_STATUS_RES_PACKAGE,
            Contacts.CONTACT_STATUS_LABEL,
            Contacts.CONTACT_STATUS_ICON,
            Data.TIMES_USED,
            Data.LAST_TIME_USED,
    };

    private static final String[] STREQUENT_PHONE_ONLY_PROJECTION = new String[]{
            Data._ID,
            Contacts.HAS_PHONE_NUMBER,
            Contacts.NAME_RAW_CONTACT_ID,
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
            Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
            Contacts.CONTACT_PRESENCE,
            Contacts.CONTACT_CHAT_CAPABILITY,
            Contacts.CONTACT_STATUS,
            Contacts.CONTACT_STATUS_TIMESTAMP,
            Contacts.CONTACT_STATUS_RES_PACKAGE,
            Contacts.CONTACT_STATUS_LABEL,
            Contacts.CONTACT_STATUS_ICON,
            Data.TIMES_USED,
            Data.LAST_TIME_USED,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.IS_SUPER_PRIMARY,
            Phone.CONTACT_ID,
    };

    public static ContentValues[] sContentValues = new ContentValues[3];
    static {
        ContentValues cv1 = new ContentValues();
        cv1.put(Contacts.DISPLAY_NAME, "Hot Tamale");
        sContentValues[0] = cv1;

        ContentValues cv2 = new ContentValues();
        cv2.put(Contacts.DISPLAY_NAME, "Cold Tamago");
        sContentValues[1] = cv2;

        ContentValues cv3 = new ContentValues();
        cv3.put(Contacts.DISPLAY_NAME, "John Doe");
        sContentValues[2] = cv3;
    }

    private long[] mDataIds = new long[3];

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

    /**
     * Tests that {@link android.provider.ContactsContract.Contacts#CONTENT_STREQUENT_URI} returns
     * no contacts if there are no starred or frequent contacts in the user's contacts.
     */
    public void testStrequents_noStarredOrFrequents() throws Exception {
        long[] ids = setupTestData();
        assertCursorStoredValuesWithContactsFilter(Contacts.CONTENT_STREQUENT_URI, ids, false);
    }

    /**
     * Tests that {@link android.provider.ContactsContract.Contacts#CONTENT_STREQUENT_URI} returns
     * starred contacts in the correct order if there are only starred contacts in the user's
     * contacts.
     */
    public void testStrequents_starredOnlyInCorrectOrder() throws Exception {
        long[] ids = setupTestData();

        // Star/favorite the first and third contact.
        starContact(ids[0]);
        starContact(ids[1]);

        // Only the starred contacts should be returned, ordered alphabetically by name
        assertCursorStoredValuesWithContactsFilter(Contacts.CONTENT_STREQUENT_URI, ids,
                false, sContentValues[1], sContentValues[0]);
    }

    /**
     * Tests that {@link android.provider.ContactsContract.Contacts#CONTENT_STREQUENT_URI} returns
     * frequent contacts in the correct order if there are only frequent contacts in the user's
     * contacts.
     */
    public void testStrequents_frequentsOnlyInCorrectOrder() throws Exception {
        long[] ids = setupTestData();

        // Contact the first contact once.
        markDataAsUsed(mDataIds[0], 1);

        // Contact the second contact thrice.
        markDataAsUsed(mDataIds[1], 3);

        // Contact the third contact twice.
        markDataAsUsed(mDataIds[2], 2);

        // The strequents uri should now return contact 2, 3, 1 in order due to ranking by
        // data usage.
        assertCursorStoredValuesWithContactsFilter(Contacts.CONTENT_STREQUENT_URI, ids,
                false, sContentValues[1], sContentValues[2], sContentValues[0]);
    }

    /**
     * Tests that {@link android.provider.ContactsContract.Contacts#CONTENT_STREQUENT_URI} returns
     * first starred, then frequent contacts in their respective correct orders if there are both
     * starred and frequent contacts in the user's contacts.
     */
    public void testStrequents_starredAndFrequentsInCorrectOrder() throws Exception {
        long[] ids = setupTestData();

        // Contact the first contact once.
        markDataAsUsed(mDataIds[0], 1);

        // Contact the second contact thrice.
        markDataAsUsed(mDataIds[1], 3);

        // Contact the third contact twice, and mark it as used
        markDataAsUsed(mDataIds[2], 2);
        starContact(ids[2]);

        // The strequents uri should now return contact 3, 2, 1 in order. Contact 3 is ranked first
        // because it is starred, followed by contacts 2 and 1 due to their data usage ranking.
        // Note that contact 3 is only returned once (as a starred contact) even though it is also
        // a frequently contacted contact.
        assertCursorStoredValuesWithContactsFilter(Contacts.CONTENT_STREQUENT_URI, ids,
                false, sContentValues[2], sContentValues[1], sContentValues[0]);
    }

    /**
     * Tests that {@link android.provider.ContactsContract.Contacts#CONTENT_STREQUENT_FILTER_URI}
     * correctly filters the returned contacts with the given user input.
     */
    public void testStrequents_withFilter() throws Exception {
        long[] ids = setupTestData();

        //Star all 3 contacts
        starContact(ids[0]);
        starContact(ids[1]);
        starContact(ids[2]);

        // Construct a uri that filters for the query string "ta".
        Uri uri = Contacts.CONTENT_STREQUENT_FILTER_URI.buildUpon().appendEncodedPath("ta").build();

        // Only contact 1 and 2 should be returned (sorted in alphabetical order) due to the
        // filtered query.
        assertCursorStoredValuesWithContactsFilter(uri, ids, false, sContentValues[1], sContentValues[0]);
    }

    public void testStrequents_projection() throws Exception {
        long[] ids = setupTestData();

        // Start contact 0 and mark contact 2 as frequent
        starContact(ids[0]);
        markDataAsUsed(mDataIds[2], 1);

        DatabaseAsserts.checkProjection(mResolver, Contacts.CONTENT_STREQUENT_URI,
                STREQUENT_PROJECTION,
                new long[]{ids[0], ids[2]}
        );

        // Strequent filter.
        DatabaseAsserts.checkProjection(mResolver,
                Contacts.CONTENT_STREQUENT_FILTER_URI.buildUpon()
                        .appendEncodedPath("Hot Tamale").build(),
                STREQUENT_PROJECTION,
                new long[]{ids[0]}
        );
    }

    public void testStrequents_phoneOnly() throws Exception {
        long[] ids = setupTestData();

        // Star all 3 contacts
        starContact(ids[0]);
        starContact(ids[1]);
        starContact(ids[2]);

        // Construct a uri for phone only favorites.
        Uri uri = Contacts.CONTENT_STREQUENT_URI.buildUpon().
                appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true").build();

        // Only the contacts with phone numbers are returned, in alphabetical order. Filtering
        // is done with data ids instead of contact ids since each row contains a single data item.
        assertCursorStoredValuesWithContactsFilter(uri, mDataIds, false,
                sContentValues[0], sContentValues[2]);
    }

    public void testStrequents_phoneOnlyFrequentsOrder() throws Exception {
        long[] ids = setupTestData();

        // Contact the first contact once.
        markDataAsUsed(mDataIds[0], 1);

        // Contact the second contact twice.
        markDataAsUsed(mDataIds[1], 2);

        // Contact the third contact thrice.
        markDataAsUsed(mDataIds[2], 3);

        // Construct a uri for phone only favorites.
        Uri uri = Contacts.CONTENT_STREQUENT_URI.buildUpon().
                appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true").build();

        // Only the contacts with phone numbers are returned, in frequency ranking order.
        assertCursorStoredValuesWithContactsFilter(uri, mDataIds, false,
                sContentValues[2], sContentValues[0]);
    }

    public void testStrequents_phoneOnly_projection() throws Exception {
        long[] ids = setupTestData();

        // Start contact 0 and mark contact 2 as frequent
        starContact(ids[0]);
        markDataAsUsed(mDataIds[2], 1);

        // Construct a uri for phone only favorites.
        Uri uri = Contacts.CONTENT_STREQUENT_URI.buildUpon().
                appendQueryParameter(ContactsContract.STREQUENT_PHONE_ONLY, "true").build();

        DatabaseAsserts.checkProjection(mResolver, uri,
                STREQUENT_PHONE_ONLY_PROJECTION,
                new long[]{mDataIds[0], mDataIds[2]} // Note _id from phone_only is data._id
        );
    }

    public void testFrequents_noFrequentsReturnsEmptyCursor() throws Exception {
        long[] ids = setupTestData();
        assertCursorStoredValuesWithContactsFilter(Contacts.CONTENT_FREQUENT_URI, ids, false);
    }

    public void testFrequents_CorrectOrder() throws Exception {
        long[] ids = setupTestData();

        // Contact the first contact once.
        markDataAsUsed(mDataIds[0], 1);

        // Contact the second contact thrice.
        markDataAsUsed(mDataIds[1], 3);

        // Contact the third contact twice.
        markDataAsUsed(mDataIds[2], 2);

        // The frequents uri should now return contact 2, 3, 1 in order due to ranking by
        // data usage.
        assertCursorStoredValuesWithContactsFilter(Contacts.CONTENT_FREQUENT_URI, ids,
                true /* inOrder */, sContentValues[1], sContentValues[2], sContentValues[0]);
    }

    public void testFrequent_projection() throws Exception {
        long[] ids = setupTestData();

        markDataAsUsed(mDataIds[0], 10);

        DatabaseAsserts.checkProjection(mResolver, Contacts.CONTENT_FREQUENT_URI,
                STREQUENT_PROJECTION,
                new long[]{ids[0]}
        );
    }

    /**
     * Given a uri, performs a query on the contacts provider for that uri and asserts that the
     * cursor returned from the query matches the expected results.
     *
     * @param uri Uri to perform the query for
     * @param contactsId Array of contact IDs that serves as an additional filter on the result
     * set. This is needed to limit the output to temporary test contacts that were created for
     * purposes of the test, so that the tests do not fail on devices with existing contacts on
     * them
     * @param inOrder Whether or not the returned rows in the cursor should correspond to the
     * order of the provided ContentValues
     * @param expected An array of ContentValues corresponding to the expected output of the query
     */
    private void assertCursorStoredValuesWithContactsFilter(Uri uri, long[] contactsId,
            boolean inOrder, ContentValues... expected) {
        // We need this helper function to add a filter for specific contacts because
        // otherwise tests will fail if performed on a device with existing contacts data
        StringBuilder sb = new StringBuilder();
        sb.append(Contacts._ID + " in ");
        sb.append("(");
        for (int i = 0; i < contactsId.length; i++) {
            if (i != 0) sb.append(",");
            sb.append(contactsId[i]);
        }
        sb.append(")");
        DatabaseAsserts.assertStoredValuesInUriMatchExactly(mResolver, uri, null, sb.toString(),
                null, null, inOrder, expected);
    }

    /**
     * Given a contact id, update the contact corresponding to that contactId so that it now shows
     * up in the user's favorites/starred contacts.
     *
     * @param contactId Contact ID corresponding to the contact to star
     */
    private void starContact(long contactId) {
        ContentValues values = new ContentValues();
        values.put(Contacts.STARRED, 1);
        mResolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), values,
                null, null);
    }

    /**
     * Given a data id, increment the data usage stats by a given number of usages to simulate
     * the user making a call to the given data item.
     *
     * @param dataId Id of the data item to increment data usage stats for
     * @param numTimes The number of times to increase the data usage stats by
     */
    private void markDataAsUsed(long dataId, int numTimes) {
        Uri uri = ContactsContract.DataUsageFeedback.FEEDBACK_URI.buildUpon().
                appendPath(String.valueOf(dataId)).
                appendQueryParameter(DataUsageFeedback.USAGE_TYPE,
                        DataUsageFeedback.USAGE_TYPE_CALL).build();
        for (int i = 1; i <= numTimes; i++) {
            mResolver.update(uri, new ContentValues(), null, null);
        }
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
                .with(StructuredName.DISPLAY_NAME, "Hot Tamale")
                .insert();
        mDataIds[0] = rawContact.newDataRow(Phone.CONTENT_ITEM_TYPE)
                .with(Phone.DATA, "510-123-5769")
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
        mDataIds[1] = rawContact2.newDataRow(Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "eggs@farmers.org")
                .with(Email.TYPE, Email.TYPE_HOME)
                .insert().load().getId();
        rawContact2.load();
        TestContact contact2 = rawContact2.getContact().load();

        TestRawContact rawContact3 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact3.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "John Doe")
                .insert();
        mDataIds[2] = rawContact3.newDataRow(Phone.CONTENT_ITEM_TYPE)
                .with(Phone.DATA, "518-354-1111")
                .with(Phone.TYPE, Phone.TYPE_HOME)
                .insert().load().getId();
        rawContact3.load();
        TestContact contact3 = rawContact3.getContact().load();

        return new long[] {contact.getId(), contact2.getId(), contact3.getId()};
    }
}

