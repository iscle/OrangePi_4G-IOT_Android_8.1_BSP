/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.provider.cts;

import com.google.android.mms.ContentType;
import com.google.android.mms.pdu.CharacterSets;

import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.util.Log;

/**
 * CTS tests for backup and restore of blocked numbers using local transport.
 */
// To run the tests in this file w/o running all the cts tests:
// make cts
// cts-tradefed
// run cts -m CtsProviderTestCases --test android.provider.cts.SmsBackupRestoreTest
public class SmsBackupRestoreTest extends TestCaseThatRunsIfTelephonyIsEnabled {
    private static final String TAG = "SmsBackupRestoreTest";
    private static final String LOCAL_BACKUP_COMPONENT =
            "android/com.android.internal.backup.LocalTransport";
    private static final String TELEPHONY_PROVIDER_PACKAGE = "com.android.providers.telephony";

    private static final String[] smsAddressBody1 = new String[] {"+123" , "sms CTS text"};
    private static final String[] smsAddressBody2 = new String[] {"+456" , "sms CTS text 2"};
    private static final String[] mmsAddresses = new String[] {"+1223", "+43234234"};
    private static final String mmsSubject = "MMS Subject CTS";
    private static final String mmsBody = "MMS body CTS";

    private static final String[] ID_PROJECTION = new String[] { BaseColumns._ID };
    private static final String[] ID_TEXT_ONLY_PROJECTION = new String[] { BaseColumns._ID,
            Telephony.Mms.TEXT_ONLY };
    private static final String SMS_SELECTION = Telephony.Sms.ADDRESS + " = ? and "
            + Telephony.Sms.BODY + " = ?";

    private static final String MMS_SELECTION = Telephony.Mms.SUBJECT + " = ?";

    private static final String[] MMS_PART_TEXT_PROJECTION = new String[]{Telephony.Mms.Part.TEXT};
    private static final String MMS_PART_SELECTION = Telephony.Mms.Part.MSG_ID + " = ?";
    private static final String MMS_PART_TEXT_SELECTION = Telephony.Mms.Part.CONTENT_TYPE + " = ?";
    private static final String[] MMS_ADDR_PROJECTION = new String[] { Telephony.Mms.Addr.ADDRESS };
    private static final String MMS_ADDR_SELECTION = Telephony.Mms.Addr.MSG_ID + " = ?";

    private Context mContext;
    private ContentResolver mContentResolver;
    private UiAutomation mUiAutomation;
    private String mOldTransport;
    private boolean mOldBackupEnabled;
    private boolean mHasFeature;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Wait for the UI Thread to become idle.
        getInstrumentation().waitForIdleSync();
        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
        mUiAutomation = getInstrumentation().getUiAutomation();
        mHasFeature = isFeatureSupported();
        if (mHasFeature) {
            ProviderTestUtils.setDefaultSmsApp(true, mContext.getPackageName(), mUiAutomation);
            mOldTransport =
                    ProviderTestUtils.setBackupTransport(LOCAL_BACKUP_COMPONENT, mUiAutomation);
            mOldBackupEnabled = ProviderTestUtils.setBackupEnabled(true, mUiAutomation);
            clearMessages();
            wipeBackup();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            wipeBackup();
            clearMessages();
            ProviderTestUtils.setBackupEnabled(mOldBackupEnabled, mUiAutomation);
            ProviderTestUtils.setBackupTransport(mOldTransport, mUiAutomation);
            ProviderTestUtils.setDefaultSmsApp(false, mContext.getPackageName(), mUiAutomation);
        }

        super.tearDown();
    }

    private boolean isFeatureSupported() throws Exception {
        return (ProviderTestUtils.hasBackupTransport(LOCAL_BACKUP_COMPONENT, mUiAutomation)
                && mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
    }

    private void clearMessages() {
        mContentResolver.delete(Telephony.Sms.CONTENT_URI, SMS_SELECTION, smsAddressBody1);
        mContentResolver.delete(Telephony.Sms.CONTENT_URI, SMS_SELECTION, smsAddressBody2);
        try (Cursor mmsCursor =
                     mContentResolver.query(Telephony.Mms.CONTENT_URI, ID_PROJECTION, MMS_SELECTION,
                             new String[] {mmsSubject}, null)) {
            if (mmsCursor != null && mmsCursor.moveToFirst()) {
                final long mmsId = mmsCursor.getLong(0);
                final Uri partUri = Telephony.Mms.CONTENT_URI.buildUpon()
                        .appendPath(String.valueOf(mmsId)).appendPath("part").build();
                mContentResolver.delete(partUri, MMS_PART_SELECTION,
                        new String[]{String.valueOf(mmsId)});

                final Uri addrUri = getMmsAddrUri(mmsId);
                mContentResolver.delete(addrUri, MMS_ADDR_SELECTION,
                        new String[]{String.valueOf(mmsId)});
            }
        }
        mContentResolver.delete(Telephony.Mms.CONTENT_URI, MMS_SELECTION, new String[]{mmsSubject});
    }

    /**
     * Test adds 2 SMS messages, 1 text-only MMS messages and 1 non-text-only, runs backup,
     * deletes the messages from the provider, runs restore, check if the messages are in the
     * provider (with non-text-only one).
     * @throws Exception
     */
    public void testSmsBackupRestore() throws Exception {
        if (!mHasFeature) {
            Log.i(TAG, "skipping testSmsBackupRestore");
            return;
        }

        ContentValues smsContentValues[] = new ContentValues[] {
                createSmsValues(smsAddressBody1),
                createSmsValues(smsAddressBody2)};
        Log.i(TAG, "Put 2 SMS into the provider");
        mContentResolver.bulkInsert(Telephony.Sms.CONTENT_URI, smsContentValues);

        Log.i(TAG, "Put 1 text MMS into the provider");
        addMms(true /*isTextOnly*/, mmsBody, mmsSubject, mmsAddresses);
        Log.i(TAG, "Put 1 non-text MMS into the provider");
        addMms(false /*isTextOnly*/, mmsBody, mmsSubject, mmsAddresses);

        Log.i(TAG, "Run backup");
        ProviderTestUtils.runBackup(TELEPHONY_PROVIDER_PACKAGE, mUiAutomation);
        Log.i(TAG, "Delete the messages from the provider");
        clearMessages();
        Log.i(TAG, "Run restore");
        ProviderTestUtils.runRestore(TELEPHONY_PROVIDER_PACKAGE, mUiAutomation);

        Log.i(TAG, "Check the providers for the messages");
        assertEquals(1,
                mContentResolver.delete(Telephony.Sms.CONTENT_URI, SMS_SELECTION, smsAddressBody1));
        assertEquals(1,
                mContentResolver.delete(Telephony.Sms.CONTENT_URI, SMS_SELECTION, smsAddressBody2));

        try (Cursor mmsCursor = mContentResolver.query(Telephony.Mms.CONTENT_URI,
              ID_TEXT_ONLY_PROJECTION, MMS_SELECTION, new String[] {mmsSubject}, null)) {
            assertNotNull(mmsCursor);
            assertEquals(2, mmsCursor.getCount());
            for (mmsCursor.moveToFirst(); !mmsCursor.isAfterLast(); mmsCursor.moveToNext()) {
              final long mmsId = mmsCursor.getLong(0);
              final long mmsTextOnly = mmsCursor.getLong(1);
              assertEquals(mmsTextOnly, 1);
              final Uri partUri = Telephony.Mms.CONTENT_URI.buildUpon()
                  .appendPath(String.valueOf(mmsId)).appendPath("part").build();
              // Check the body.
              try (Cursor partCursor = mContentResolver.query(partUri, MMS_PART_TEXT_PROJECTION,
                    MMS_PART_TEXT_SELECTION, new String[]{ ContentType.TEXT_PLAIN }, null)) {
                assertNotNull(partCursor);
                assertEquals(1, partCursor.getCount());
                assertTrue(partCursor.moveToFirst());
                assertTrue(partCursor.getString(0).startsWith(mmsBody));
              }

              // Check if there are 2 parts (smil and body).
              assertEquals(2, mContentResolver.delete(partUri, MMS_PART_SELECTION,
                    new String[]{String.valueOf(mmsId)}));

              // Check addresses.
              final Uri addrUri = getMmsAddrUri(mmsId);
              try (Cursor addrCursor = mContentResolver.query(addrUri, MMS_ADDR_PROJECTION,
                    MMS_ADDR_SELECTION, new String[]{String.valueOf(mmsId)}, null)) {
                assertNotNull(addrCursor);
                for (String addr : mmsAddresses) {
                  addrCursor.moveToNext();
                  assertEquals(addr, addrCursor.getString(0));
                }
              }
              assertEquals(mmsAddresses.length, mContentResolver.delete(addrUri, MMS_ADDR_SELECTION,
                    new String[]{String.valueOf(mmsId)}));
            }
        }
    }

    private static Uri getMmsAddrUri(long mmsId) {
        Uri.Builder builder = Telephony.Mms.CONTENT_URI.buildUpon();
        builder.appendPath(String.valueOf(mmsId)).appendPath("addr");
        return builder.build();
    }

    private void addMms(boolean isTextOnly, String body, String subject, String[] addresses) {
        // Insert mms.
        final ContentValues mmsValues = new ContentValues(2);
        mmsValues.put(Telephony.Mms.TEXT_ONLY, isTextOnly ? 1 : 0);
        mmsValues.put(Telephony.Mms.MESSAGE_TYPE, 128);
        mmsValues.put(Telephony.Mms.SUBJECT, subject);
        final Uri mmsUri = mContentResolver.insert(Telephony.Mms.CONTENT_URI, mmsValues);
        assertNotNull(mmsUri);
        final long mmsId = ContentUris.parseId(mmsUri);

        final String srcName = String.format("text.%06d.txt", 0);
        // Insert body part.
        final Uri partUri = Telephony.Mms.CONTENT_URI.buildUpon()
                .appendPath(String.valueOf(mmsId)).appendPath("part").build();

        final ContentValues values = new ContentValues(8);
        values.put(Telephony.Mms.Part.MSG_ID, mmsId);
        values.put(Telephony.Mms.Part.SEQ, 0);
        values.put(Telephony.Mms.Part.CONTENT_TYPE, ContentType.TEXT_PLAIN);
        values.put(Telephony.Mms.Part.NAME, srcName);
        values.put(Telephony.Mms.Part.CONTENT_ID, "<"+srcName+">");
        values.put(Telephony.Mms.Part.CONTENT_LOCATION, srcName);
        values.put(Telephony.Mms.Part.CHARSET, CharacterSets.DEFAULT_CHARSET);
        if (!isTextOnly) {
            body = body + " Non text-only";
        }
        values.put(Telephony.Mms.Part.TEXT, body);
        mContentResolver.insert(partUri, values);

        // Insert addresses.
        final Uri addrUri = Uri.withAppendedPath(mmsUri, "addr");
        for (int i = 0; i < addresses.length; ++i) {
            ContentValues addrValues = new ContentValues(3);
            addrValues.put(Telephony.Mms.Addr.TYPE, i);
            addrValues.put(Telephony.Mms.Addr.CHARSET, CharacterSets.DEFAULT_CHARSET);
            addrValues.put(Telephony.Mms.Addr.ADDRESS, addresses[i]);
            addrValues.put(Telephony.Mms.Addr.MSG_ID, mmsId);
            mContentResolver.insert(addrUri, addrValues);
        }
    }

    private static ContentValues createSmsValues(String[] addressBody) {
        ContentValues smsRow = new ContentValues();
        smsRow.put(Telephony.Sms.ADDRESS, addressBody[0]);
        smsRow.put(Telephony.Sms.BODY, addressBody[1]);
        return smsRow;
    }

    private void wipeBackup() throws Exception {
        ProviderTestUtils.wipeBackup(LOCAL_BACKUP_COMPONENT, TELEPHONY_PROVIDER_PACKAGE,
                mUiAutomation);
    }
}
