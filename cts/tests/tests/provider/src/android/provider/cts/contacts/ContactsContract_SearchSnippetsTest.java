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
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.SearchSnippets;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.test.InstrumentationTestCase;

/**
 * CTS tests for {@link android.provider.ContactsContract.SearchSnippets} APIs.
 */
public class ContactsContract_SearchSnippetsTest extends InstrumentationTestCase {
    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    public static String[] TEST_PROJECTION = new String[] {
        Contacts._ID,
        SearchSnippets.SNIPPET
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

    public void testSearchSnippets_NoMatch() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath("nomatch").build();

        assertCursorStoredValuesWithContactsFilter(uri, ids);
    }

    public void testEnterpriseSearchSnippets_NoMatch() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT)).appendPath("nomatch").build();

        assertCursorStoredValuesWithContactsFilter(uri, ids);
    }

    public void testSearchSnippets_MatchEmailAddressCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath("farm").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[1]);
        expected.put(SearchSnippets.SNIPPET, "eggs@[farmers].org");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testEnterpriseSearchSnippets_MatchEmailAddressCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("farm").appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT)).build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[1]);
        expected.put(SearchSnippets.SNIPPET, "eggs@[farmers].org");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testSearchSnippets_MatchPhoneNumberCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath("510").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[0]);
        expected.put(SearchSnippets.SNIPPET, "[510-123-5769]");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testEnterpriseSearchSnippets_MatchPhoneNumberCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("510").appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT)).build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[0]);
        expected.put(SearchSnippets.SNIPPET, "[510-123-5769]");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testSearchSnippets_MatchPostalAddressCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath("street").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[2]);
        expected.put(SearchSnippets.SNIPPET, "123 Main [Street] Unit 3113\u2026");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testEnterpriseSearchSnippets_MatchPostalAddressCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("street").appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT)).build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[2]);
        expected.put(SearchSnippets.SNIPPET, "123 Main [Street] Unit 3113\u2026");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testSearchSnippets_LongMatchTruncation() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath("over").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[2]);
        expected.put(SearchSnippets.SNIPPET, "\u2026dog jumps [over] the quick\u2026");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testEnterpriseSearchSnippets_LongMatchTruncation() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("over").appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT)).build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[2]);
        expected.put(SearchSnippets.SNIPPET, "\u2026dog jumps [over] the quick\u2026");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    public void testSearchSnippets_MultipleMatchesCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendPath("123").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[1]);
        expected.put(SearchSnippets.SNIPPET, "[123-456-7890]");

        final ContentValues expected2 = new ContentValues();
        expected2.put(Contacts._ID, ids[2]);
        expected2.put(SearchSnippets.SNIPPET, "[123] Main Street Unit 3113\u2026");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected, expected2);
    }

    public void testEnterpriseSearchSnippets_MultipleMatchesCorrectSnippet() throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendPath("123").appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT)).build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[1]);
        expected.put(SearchSnippets.SNIPPET, "[123-456-7890]");

        final ContentValues expected2 = new ContentValues();
        expected2.put(Contacts._ID, ids[2]);
        expected2.put(SearchSnippets.SNIPPET, "[123] Main Street Unit 3113\u2026");

        assertCursorStoredValuesWithContactsFilter(uri, ids, expected, expected2);
    }

    /**
     * Tests that if deferred snippeting is indicated, the provider will not perform formatting
     * of the snippet for a single word query.
     */
    public void testSearchSnippets_DeferredSnippetingSingleWordQuerySnippetDeferred() throws
            Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendQueryParameter(SearchSnippets.DEFERRED_SNIPPETING_KEY, "1")
                .appendPath("510").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[0]);
        expected.put(SearchSnippets.SNIPPET, "510-123-5769");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    /**
     * Tests that if deferred snippeting is indicated, the provider will not perform formatting
     * of the snippet for a single word query.
     */
    public void testEnterpriseSearchSnippets_DeferredSnippetingSingleWordQuerySnippetDeferred()
            throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendQueryParameter(SearchSnippets.DEFERRED_SNIPPETING_KEY, "1")
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .appendPath("510").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[0]);
        expected.put(SearchSnippets.SNIPPET, "510-123-5769");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    /**
     * Tests that even if deferred snippeting is indicated, a multi-word query will have formatting
     * of the snippet done by the provider using SQLite
     */
    public void testSearchSnippets_DeferredSnippetingMultiWordQuerySnippetNotDeferred() throws
            Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.CONTENT_FILTER_URI.buildUpon()
                .appendQueryParameter(SearchSnippets.DEFERRED_SNIPPETING_KEY, "1")
                .appendPath("jumps over").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[2]);
        expected.put(SearchSnippets.SNIPPET, "\u2026lazy dog [jumps] [over] the\u2026");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
    }

    /**
     * Tests that even if deferred snippeting is indicated, a multi-word query will have formatting
     * of the snippet done by the provider using SQLite
     */
    public void testEnterpriseSearchSnippets_DeferredSnippetingMultiWordQuerySnippetNotDeferred()
            throws Exception {
        long[] ids = setupTestData();
        final Uri uri = ContactsContract.Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon()
                .appendQueryParameter(SearchSnippets.DEFERRED_SNIPPETING_KEY, "1")
                .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                .appendPath("jumps over").build();

        final ContentValues expected = new ContentValues();
        expected.put(Contacts._ID, ids[2]);
        expected.put(SearchSnippets.SNIPPET, "\u2026lazy dog [jumps] [over] the\u2026");
        assertCursorStoredValuesWithContactsFilter(uri, ids, expected);
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
     * @param expected An array of ContentValues corresponding to the expected output of the query
     */
    private void assertCursorStoredValuesWithContactsFilter(Uri uri, long[] contactsId,
            ContentValues... expected) {
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
        DatabaseAsserts.assertStoredValuesInUriMatchExactly(mResolver, uri, TEST_PROJECTION,
                sb.toString(), null, null, false, expected);
    }

    /**
     * Setup the contacts database with temporary contacts used for testing. These contacts will be
     * removed during teardown.
     *
     * @return An array of long values corresponding to the ids of the created contacts
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
        rawContact.newDataRow(Phone.CONTENT_ITEM_TYPE)
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
        rawContact2.newDataRow(Email.CONTENT_ITEM_TYPE)
                .with(Email.DATA, "eggs@farmers.org")
                .with(Email.TYPE, Email.TYPE_HOME)
                .insert().load();
        rawContact2.newDataRow(Phone.CONTENT_ITEM_TYPE)
        .with(Phone.DATA, "123-456-7890")
        .with(Phone.TYPE, Phone.TYPE_OTHER)
        .insert().load();

        rawContact2.load();
        TestContact contact2 = rawContact2.getContact().load();

        TestRawContact rawContact3 = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_account")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact3.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "John Doe")
                .insert();
        rawContact3.newDataRow(StructuredPostal.CONTENT_ITEM_TYPE)
                .with(StructuredPostal.DATA, "123 Main Street Unit 3113")
                .with(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME)
                .insert().load().getId();
        rawContact3.newDataRow(Note.CONTENT_ITEM_TYPE)
                .with(Note.NOTE, "The lazy dog jumps over the quick brown fox.")
                .insert().load();
        rawContact3.load();
        TestContact contact3 = rawContact3.getContact().load();

        return new long[] {
                contact.getId(), contact2.getId(), contact3.getId()
        };
    }
}

