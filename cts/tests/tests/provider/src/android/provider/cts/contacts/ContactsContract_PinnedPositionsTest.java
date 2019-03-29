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

import static android.provider.cts.contacts.ContactUtil.newContentValues;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PinnedPositions;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.CommonDatabaseUtils;
import android.provider.cts.contacts.ContactUtil;
import android.provider.cts.contacts.DatabaseAsserts;
import android.provider.cts.contacts.RawContactUtil;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.ArrayList;

/**
 * CTS tests for {@link android.provider.ContactsContract.PinnedPositions} API
 */
public class ContactsContract_PinnedPositionsTest extends AndroidTestCase {
    private static final String TAG = "ContactsContract_PinnedPositionsTest";

    private ContentResolver mResolver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getContext().getContentResolver();
    }

    /**
     * Tests that the ContactsProvider automatically stars/unstars a pinned/unpinned contact if
     * {@link PinnedPositions#STAR_WHEN_PINNING} boolean parameter is set to true, and that the
     * values are correctly propogated to the contact's constituent raw contacts.
     */
    public void testPinnedPositionsUpdate() {
        final DatabaseAsserts.ContactIdPair i1 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i2 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i3 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i4 = DatabaseAsserts.assertAndCreateContact(mResolver);

        final int unpinned = PinnedPositions.UNPINNED;

        assertValuesForContact(i1.mContactId,
                newContentValues(Contacts.PINNED, unpinned, Contacts.STARRED, 0));
        assertValuesForContact(i2.mContactId,
                newContentValues(Contacts.PINNED, unpinned, Contacts.STARRED, 0));
        assertValuesForContact(i3.mContactId,
                newContentValues(Contacts.PINNED, unpinned, Contacts.STARRED, 0));
        assertValuesForContact(i4.mContactId,
                newContentValues(Contacts.PINNED, unpinned, Contacts.STARRED, 0));

        assertValuesForRawContact(i1.mRawContactId, newContentValues(RawContacts.PINNED, unpinned));
        assertValuesForRawContact(i2.mRawContactId, newContentValues(RawContacts.PINNED, unpinned));
        assertValuesForRawContact(i3.mRawContactId, newContentValues(RawContacts.PINNED, unpinned));
        assertValuesForRawContact(i4.mRawContactId, newContentValues(RawContacts.PINNED, unpinned));

        final ArrayList<ContentProviderOperation> operations =
                new ArrayList<ContentProviderOperation>();
        operations.add(newPinningOperation(i1.mContactId, 1, true));
        operations.add(newPinningOperation(i3.mContactId, 3, true));
        operations.add(newPinningOperation(i4.mContactId, 2, false));
        applyBatch(mResolver, operations);

        assertValuesForContact(i1.mContactId,
                newContentValues(Contacts.PINNED, 1, Contacts.STARRED, 1));
        assertValuesForContact(i2.mContactId,
                newContentValues(Contacts.PINNED, unpinned, Contacts.STARRED, 0));
        assertValuesForContact(i3.mContactId,
                newContentValues(Contacts.PINNED, 3, Contacts.STARRED, 1));
        assertValuesForContact(i4.mContactId,
                newContentValues(Contacts.PINNED, 2, Contacts.STARRED, 0));

        // Make sure the values are propagated to raw contacts.
        assertValuesForRawContact(i1.mRawContactId, newContentValues(RawContacts.PINNED, 1));
        assertValuesForRawContact(i2.mRawContactId, newContentValues(RawContacts.PINNED, unpinned));
        assertValuesForRawContact(i3.mRawContactId, newContentValues(RawContacts.PINNED, 3));
        assertValuesForRawContact(i4.mRawContactId, newContentValues(RawContacts.PINNED, 2));

        operations.clear();

        // Now unpin the contact
        operations.add(newPinningOperation(i3.mContactId, unpinned, false));
        applyBatch(mResolver, operations);

        assertValuesForContact(i1.mContactId,
                newContentValues(Contacts.PINNED, 1, Contacts.STARRED, 1));
        assertValuesForContact(i2.mContactId,
                newContentValues(Contacts.PINNED, unpinned, Contacts.STARRED, 0));
        assertValuesForContact(i3.mContactId,
                newContentValues(Contacts.PINNED, unpinned, Contacts.STARRED, 0));
        assertValuesForContact(i4.mContactId,
                newContentValues(Contacts.PINNED, 2, Contacts.STARRED, 0));

        assertValuesForRawContact(i1.mRawContactId,
                newContentValues(RawContacts.PINNED, 1, RawContacts.STARRED, 1));
        assertValuesForRawContact(i2.mRawContactId,
                newContentValues(RawContacts.PINNED, unpinned, RawContacts.STARRED, 0));
        assertValuesForRawContact(i3.mRawContactId,
                newContentValues(RawContacts.PINNED, unpinned, RawContacts.STARRED, 0));
        assertValuesForRawContact(i4.mRawContactId,
                newContentValues(RawContacts.PINNED, 2, RawContacts.STARRED, 0));

        ContactUtil.delete(mResolver, i1.mContactId);
        ContactUtil.delete(mResolver, i2.mContactId);
        ContactUtil.delete(mResolver, i3.mContactId);
        ContactUtil.delete(mResolver, i4.mContactId);
    }

    /**
     * Tests that pinned positions are correctly handled after the ContactsProvider aggregates
     * and splits raw contacts.
     */
    public void testPinnedPositionsAfterJoinAndSplit() {
        final DatabaseAsserts.ContactIdPair i1 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i2 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i3 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i4 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i5 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i6 = DatabaseAsserts.assertAndCreateContact(mResolver);

        final ArrayList<ContentProviderOperation> operations =
                new ArrayList<ContentProviderOperation>();

        operations.add(newPinningOperation(i1.mContactId, 1, true));
        operations.add(newPinningOperation(i2.mContactId, 2, true));
        operations.add(newPinningOperation(i3.mContactId, 3, true));
        operations.add(newPinningOperation(i5.mContactId, 5, true));
        operations.add(newPinningOperation(i6.mContactId, 6, true));

        applyBatch(mResolver, operations);

        // Aggregate raw contact 1 and 4 together.
        ContactUtil.setAggregationException(mResolver, AggregationExceptions.TYPE_KEEP_TOGETHER,
                i1.mRawContactId, i4.mRawContactId);

        // If only one contact is pinned, the resulting contact should inherit the pinned position.
        assertValuesForContact(i1.mContactId, newContentValues(Contacts.PINNED, 1));
        assertValuesForContact(i2.mContactId, newContentValues(Contacts.PINNED, 2));
        assertValuesForContact(i3.mContactId, newContentValues(Contacts.PINNED, 3));
        assertValuesForContact(i5.mContactId, newContentValues(Contacts.PINNED, 5));
        assertValuesForContact(i6.mContactId, newContentValues(Contacts.PINNED, 6));

        assertValuesForRawContact(i1.mRawContactId,
                newContentValues(RawContacts.PINNED, 1, RawContacts.STARRED, 1));
        assertValuesForRawContact(i2.mRawContactId,
                newContentValues(RawContacts.PINNED, 2, RawContacts.STARRED, 1));
        assertValuesForRawContact(i3.mRawContactId,
                newContentValues(RawContacts.PINNED, 3, RawContacts.STARRED, 1));
        assertValuesForRawContact(i4.mRawContactId,
                newContentValues(RawContacts.PINNED, PinnedPositions.UNPINNED, RawContacts.STARRED,
                        0));
        assertValuesForRawContact(i5.mRawContactId,
                newContentValues(RawContacts.PINNED, 5, RawContacts.STARRED, 1));
        assertValuesForRawContact(i6.mRawContactId,
                newContentValues(RawContacts.PINNED, 6, RawContacts.STARRED, 1));

        // Aggregate raw contact 2 and 3 together.
        ContactUtil.setAggregationException(mResolver, AggregationExceptions.TYPE_KEEP_TOGETHER,
                i2.mRawContactId, i3.mRawContactId);

        // If both raw contacts are pinned, the resulting contact should inherit the lower
        // pinned position.
        assertValuesForContact(i1.mContactId, newContentValues(Contacts.PINNED, 1));
        assertValuesForContact(i2.mContactId, newContentValues(Contacts.PINNED, 2));
        assertValuesForContact(i5.mContactId, newContentValues(Contacts.PINNED, 5));
        assertValuesForContact(i6.mContactId, newContentValues(Contacts.PINNED, 6));

        assertValuesForRawContact(i1.mRawContactId, newContentValues(RawContacts.PINNED, 1));
        assertValuesForRawContact(i2.mRawContactId, newContentValues(RawContacts.PINNED, 2));
        assertValuesForRawContact(i3.mRawContactId, newContentValues(RawContacts.PINNED, 3));
        assertValuesForRawContact(i4.mRawContactId,
                newContentValues(RawContacts.PINNED, PinnedPositions.UNPINNED));
        assertValuesForRawContact(i5.mRawContactId, newContentValues(RawContacts.PINNED, 5));
        assertValuesForRawContact(i6.mRawContactId, newContentValues(RawContacts.PINNED, 6));

        // Split the aggregated raw contacts.
        ContactUtil.setAggregationException(mResolver, AggregationExceptions.TYPE_KEEP_SEPARATE,
            i1.mRawContactId, i4.mRawContactId);

        // Raw contacts should keep the pinned position after re-grouping, and still starred.
        assertValuesForRawContact(i1.mRawContactId,
                newContentValues(RawContacts.PINNED, 1, RawContacts.STARRED,
                        1));
        assertValuesForRawContact(i2.mRawContactId,
                newContentValues(RawContacts.PINNED, 2, RawContacts.STARRED, 1));
        assertValuesForRawContact(i3.mRawContactId,
                newContentValues(RawContacts.PINNED, 3, RawContacts.STARRED, 1));
        assertValuesForRawContact(i4.mRawContactId,
                newContentValues(RawContacts.PINNED, PinnedPositions.UNPINNED, RawContacts.STARRED,
                        0));
        assertValuesForRawContact(i5.mRawContactId,
                newContentValues(RawContacts.PINNED, 5, RawContacts.STARRED, 1));
        assertValuesForRawContact(i6.mRawContactId,
                newContentValues(RawContacts.PINNED, 6, RawContacts.STARRED, 1));

        // Now demote contact 5.
        operations.clear();
        operations.add(newPinningOperation(i5.mContactId, PinnedPositions.DEMOTED, false));
        applyBatch(mResolver, operations);

        // Get new contact Ids for contacts composing of raw contacts 1 and 4 because they have
        // changed.
        final long cId1 = RawContactUtil.queryContactIdByRawContactId(mResolver, i1.mRawContactId);
        final long cId4 = RawContactUtil.queryContactIdByRawContactId(mResolver, i4.mRawContactId);

        assertValuesForContact(cId1, newContentValues(Contacts.PINNED, 1));
        assertValuesForContact(i2.mContactId, newContentValues(Contacts.PINNED, 2));
        assertValuesForContact(cId4, newContentValues(Contacts.PINNED, PinnedPositions.UNPINNED));
        assertValuesForContact(i5.mContactId,
                newContentValues(Contacts.PINNED, PinnedPositions.DEMOTED));
        assertValuesForContact(i6.mContactId, newContentValues(Contacts.PINNED, 6));

        // Aggregate contacts 5 and 6 together.
        ContactUtil.setAggregationException(mResolver, AggregationExceptions.TYPE_KEEP_TOGETHER,
                i5.mRawContactId, i6.mRawContactId);

        // The resulting contact should have a pinned value of 6.
        assertValuesForContact(cId1, newContentValues(Contacts.PINNED, 1));
        assertValuesForContact(i2.mContactId, newContentValues(Contacts.PINNED, 2));
        assertValuesForContact(cId4, newContentValues(Contacts.PINNED, PinnedPositions.UNPINNED));
        assertValuesForContact(i5.mContactId, newContentValues(Contacts.PINNED, 6));

        ContactUtil.delete(mResolver, cId1);
        ContactUtil.delete(mResolver, i2.mContactId);
        ContactUtil.delete(mResolver, cId4);
        ContactUtil.delete(mResolver, i5.mContactId);
    }

    /**
     * Tests that calling {@link PinnedPositions#UNDEMOTE_METHOD} with an illegal argument correctly
     * throws an IllegalArgumentException.
     */
    public void testPinnedPositionsDemoteIllegalArguments() {
        try {
            mResolver.call(ContactsContract.AUTHORITY_URI, PinnedPositions.UNDEMOTE_METHOD,
                    null, null);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            mResolver.call(ContactsContract.AUTHORITY_URI, PinnedPositions.UNDEMOTE_METHOD,
                    "1.1", null);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            mResolver.call(ContactsContract.AUTHORITY_URI, PinnedPositions.UNDEMOTE_METHOD,
                    "NotANumber", null);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        // Valid contact ID that does not correspond to an actual contact is silently ignored
        mResolver.call(ContactsContract.AUTHORITY_URI, PinnedPositions.UNDEMOTE_METHOD, "999",
                null);
    }

    /**
     * Tests that pinned positions are correctly handled for contacts that have been demoted
     * or undemoted.
     */
    public void testPinnedPositionsAfterDemoteAndUndemote() {
        final DatabaseAsserts.ContactIdPair i1 = DatabaseAsserts.assertAndCreateContact(mResolver);
        final DatabaseAsserts.ContactIdPair i2 = DatabaseAsserts.assertAndCreateContact(mResolver);

        // Pin contact 1 and demote contact 2
        final ArrayList<ContentProviderOperation> operations =
                new ArrayList<ContentProviderOperation>();
        operations.add(newPinningOperation(i1.mContactId, 1, true));
        operations.add(newPinningOperation(i2.mContactId, PinnedPositions.DEMOTED, false));
        applyBatch(mResolver, operations);

        assertValuesForContact(i1.mContactId,
                newContentValues(Contacts.PINNED, 1, Contacts.STARRED, 1));
        assertValuesForContact(i2.mContactId,
                newContentValues(Contacts.PINNED, PinnedPositions.DEMOTED, Contacts.STARRED, 0));

        assertValuesForRawContact(i1.mRawContactId,
                newContentValues(RawContacts.PINNED, 1, RawContacts.STARRED, 1));
        assertValuesForRawContact(i2.mRawContactId,
                newContentValues(RawContacts.PINNED, PinnedPositions.DEMOTED, RawContacts.STARRED, 0));

        // Now undemote both contacts.
        PinnedPositions.undemote(mResolver, i1.mContactId);
        PinnedPositions.undemote(mResolver, i2.mContactId);

        // Contact 1 remains pinned at 0, while contact 2 becomes unpinned.
        assertValuesForContact(i1.mContactId,
                newContentValues(Contacts.PINNED, 1, Contacts.STARRED, 1));
        assertValuesForContact(i2.mContactId,
                newContentValues(Contacts.PINNED, PinnedPositions.UNPINNED, Contacts.STARRED, 0));

        assertValuesForRawContact(i1.mRawContactId,
                newContentValues(RawContacts.PINNED, 1, RawContacts.STARRED, 1));
        assertValuesForRawContact(i2.mRawContactId,
                newContentValues(RawContacts.PINNED, PinnedPositions.UNPINNED, RawContacts.STARRED,
                        0));

        ContactUtil.delete(mResolver, i1.mContactId);
        ContactUtil.delete(mResolver, i2.mContactId);
    }

    /**
     * Verifies that the stored values for the contact that corresponds to the given contactId
     * contain the exact same name-value pairs in the given ContentValues.
     *
     * @param contactId Id of a valid contact in the contacts database.
     * @param contentValues A valid ContentValues object.
     */
    private void assertValuesForContact(long contactId, ContentValues contentValues) {
        DatabaseAsserts.assertStoredValuesInUriMatchExactly(mResolver, Contacts.CONTENT_URI.
                buildUpon().appendEncodedPath(String.valueOf(contactId)).build(), contentValues);
    }

    /**
     * Verifies that the stored values for the raw contact that corresponds to the given
     * rawContactId contain the exact same name-value pairs in the given ContentValues.
     *
     * @param rawContactId Id of a valid contact in the contacts database
     * @param contentValues A valid ContentValues object
     */
    private void assertValuesForRawContact(long rawContactId, ContentValues contentValues) {
        DatabaseAsserts.assertStoredValuesInUriMatchExactly(mResolver, RawContacts.CONTENT_URI.
                buildUpon().appendEncodedPath(String.valueOf(rawContactId)).build(), contentValues);
    }

    /**
     * Updates the contacts provider for a contact or raw contact corresponding to the given
     * contact with key-value pairs as specified in the provided string parameters. Throws an
     * exception if the number of provided string parameters is not zero or non-even.
     *
     * @param uri base URI that the provided ID will be appended onto, in order to creating the
     * resulting URI
     * @param id id of the contact of raw contact to perform the update for
     * @param extras an even number of string parameters that correspond to name-value pairs
     *
     * @return the number of rows that were updated
     */
    private int updateItemForContact(Uri uri, long id, String... extras) {
        Uri itemUri = ContentUris.withAppendedId(uri, id);
        return updateItemForUri(itemUri, extras);
    }

    /**
     * Updates the contacts provider for the given YRU with key-value pairs as specified in the
     * provided string parameters. Throws an exception if the number of provided string parameters
     * is not zero or non-even.
     *
     * @param uri URI to perform the update for
     * @param extras an even number of string parameters that correspond to name-value pairs
     *
     * @return the number of rows that were updated
     */
    private int updateItemForUri(Uri uri, String... extras) {
        ContentValues values = new ContentValues();
        CommonDatabaseUtils.extrasVarArgsToValues(values, extras);
        return mResolver.update(uri, values, null, null);
    }

    private ContentProviderOperation newPinningOperation(long id, int pinned, boolean star) {
        final Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, String.valueOf(id));
        final ContentValues values = new ContentValues();
        values.put(Contacts.PINNED, pinned);
        values.put(Contacts.STARRED, star ? 1 : 0);
        return ContentProviderOperation.newUpdate(uri).withValues(values).build();
    }

    private static void applyBatch(ContentResolver resolver,
            ArrayList<ContentProviderOperation> operations) {
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (OperationApplicationException e) {
            Log.wtf(TAG, "ContentResolver batch operation failed.");
        } catch (RemoteException e) {
            Log.wtf(TAG, "Remote exception when performing batch operation.");
        }
    }
}

