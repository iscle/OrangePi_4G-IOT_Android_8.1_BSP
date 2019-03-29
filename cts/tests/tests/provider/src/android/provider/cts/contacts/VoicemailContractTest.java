/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.Instrumentation;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Status;
import android.provider.VoicemailContract.Voicemails;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * CTS tests for voicemail provider accessed through {@link VoicemailContract}.
 */
public class VoicemailContractTest extends InstrumentationTestCase {

    private static final String TAG = "VoicemailContractTest";

    private ContentResolver mContentResolver;
    private ContentProviderClient mVoicemailProvider;
    private ContentProviderClient mStatusProvider;
    private Uri mVoicemailContentUri;
    private Uri mStatusContentUri;
    private String mSourcePackageName;

    private String mPreviousDefaultDialer;

    private static final String COMMAND_SET_DEFAULT_DIALER = "telecom set-default-dialer ";
    private static final String COMMAND_GET_DEFAULT_DIALER = "telecom get-default-dialer";

    private static final String PACKAGE = "android.provider.cts";

    private final String FOREIGN_SOURCE = "android.provider.cts.contacts.foreign_source";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSourcePackageName = getInstrumentation().getTargetContext().getPackageName();
        mVoicemailContentUri = Voicemails.buildSourceUri(mSourcePackageName);
        mStatusContentUri = Status.buildSourceUri(mSourcePackageName);
        mContentResolver = getInstrumentation().getTargetContext().getContentResolver();
        mVoicemailProvider = mContentResolver.acquireContentProviderClient(mVoicemailContentUri);
        mStatusProvider = mContentResolver.acquireContentProviderClient(mStatusContentUri);
    }

    @Override
    protected void tearDown() throws Exception {
        // Clean up, just in case we failed to delete the entry when a test failed.
        // The cotentUris are specific to this package, so this will delete only the
        // entries inserted by this package.
        mStatusProvider.delete(mStatusContentUri, null, null);
        mVoicemailProvider.delete(mVoicemailContentUri, null, null);
        if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
            setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
        }
        super.tearDown();
    }

    public void testVoicemailsTable() throws Exception {
        final String[] VOICEMAILS_PROJECTION = new String[] {
                Voicemails._ID, Voicemails.NUMBER, Voicemails.DATE, Voicemails.DURATION,
                Voicemails.IS_READ, Voicemails.SOURCE_PACKAGE, Voicemails.SOURCE_DATA,
                Voicemails.HAS_CONTENT, Voicemails.MIME_TYPE, Voicemails.TRANSCRIPTION,
                Voicemails.PHONE_ACCOUNT_COMPONENT_NAME,
                Voicemails.PHONE_ACCOUNT_ID, Voicemails.DIRTY, Voicemails.DELETED,
                Voicemails.LAST_MODIFIED, Voicemails.BACKED_UP, Voicemails.RESTORED,
                Voicemails.ARCHIVED, Voicemails.IS_OMTP_VOICEMAIL};
        final int ID_INDEX = 0;
        final int NUMBER_INDEX = 1;
        final int DATE_INDEX = 2;
        final int DURATION_INDEX = 3;
        final int IS_READ_INDEX = 4;
        final int SOURCE_PACKAGE_INDEX = 5;
        final int SOURCE_DATA_INDEX = 6;
        final int HAS_CONTENT_INDEX = 7;
        final int MIME_TYPE_INDEX = 8;
        final int TRANSCRIPTION_INDEX= 9;
        final int PHONE_ACCOUNT_COMPONENT_NAME_INDEX = 10;
        final int PHONE_ACCOUNT_ID_INDEX = 11;
        final int DIRTY_INDEX = 12;
        final int DELETED_INDEX = 13;
        final int LAST_MODIFIED_INDEX = 14;
        final int BACKED_UP_INDEX = 15;
        final int RESTORED_INDEX = 16;
        final int ARCHIVED_INDEX = 17;
        final int IS_OMTP_VOICEMAIL_INDEX = 18;

        String insertCallsNumber = "0123456789";
        long insertCallsDuration = 120;
        String insertSourceData = "internal_id";
        String insertMimeType = "audio/mp3";
        long insertDate = 1324478862000L;

        String updateCallsNumber = "9876543210";
        long updateCallsDuration = 310;
        String updateSourceData = "another_id";
        long updateDate = 1324565262000L;

        // Test: insert
        ContentValues value = new ContentValues();
        value.put(Voicemails.NUMBER, insertCallsNumber);
        value.put(Voicemails.DATE, insertDate);
        value.put(Voicemails.DURATION, insertCallsDuration);
        // Source package is expected to be inserted by the provider, if not set.
        value.put(Voicemails.SOURCE_DATA, insertSourceData);
        value.put(Voicemails.MIME_TYPE, insertMimeType);
        value.put(Voicemails.IS_READ, false);
        value.put(Voicemails.HAS_CONTENT, true);
        value.put(Voicemails.TRANSCRIPTION, "foo");
        value.put(Voicemails.PHONE_ACCOUNT_COMPONENT_NAME, "com.foo");
        value.put(Voicemails.PHONE_ACCOUNT_ID, "bar");
        value.put(Voicemails.DIRTY, 0);
        value.put(Voicemails.DELETED, 0);
        value.put(Voicemails.BACKED_UP, 0);
        value.put(Voicemails.RESTORED, 0);
        value.put(Voicemails.ARCHIVED, 0);
        value.put(Voicemails.IS_OMTP_VOICEMAIL, 0);

        Uri uri = mVoicemailProvider.insert(mVoicemailContentUri, value);
        Cursor cursor = mVoicemailProvider.query(
                mVoicemailContentUri, VOICEMAILS_PROJECTION,
                Voicemails.NUMBER + " = ?",
                new String[] {insertCallsNumber}, null, null);
        assertTrue(cursor.moveToNext());
        assertEquals(insertCallsNumber, cursor.getString(NUMBER_INDEX));
        assertEquals(insertDate, cursor.getLong(DATE_INDEX));
        assertEquals(insertCallsDuration, cursor.getLong(DURATION_INDEX));
        assertEquals(mSourcePackageName, cursor.getString(SOURCE_PACKAGE_INDEX));
        assertEquals(insertSourceData, cursor.getString(SOURCE_DATA_INDEX));
        assertEquals(insertMimeType, cursor.getString(MIME_TYPE_INDEX));
        assertEquals(0, cursor.getInt(IS_READ_INDEX));
        assertEquals(1, cursor.getInt(HAS_CONTENT_INDEX));
        assertEquals("foo",cursor.getString(TRANSCRIPTION_INDEX));
        assertEquals("com.foo",cursor.getString(PHONE_ACCOUNT_COMPONENT_NAME_INDEX));
        assertEquals("bar",cursor.getString(PHONE_ACCOUNT_ID_INDEX));
        assertEquals(0,cursor.getInt(DIRTY_INDEX));
        assertEquals(0,cursor.getInt(DELETED_INDEX));
        assertEquals(0,cursor.getInt(BACKED_UP_INDEX));
        assertEquals(0,cursor.getInt(RESTORED_INDEX));
        assertEquals(0,cursor.getInt(ARCHIVED_INDEX));
        assertEquals(0,cursor.getInt(IS_OMTP_VOICEMAIL_INDEX));
        int id = cursor.getInt(ID_INDEX);
        assertEquals(id, Integer.parseInt(uri.getLastPathSegment()));
        cursor.close();

        // Test: update
        value.clear();
        value.put(Voicemails.NUMBER, updateCallsNumber);
        value.put(Voicemails.DATE, updateDate);
        value.put(Voicemails.DURATION, updateCallsDuration);
        value.put(Voicemails.SOURCE_DATA, updateSourceData);
        value.put(Voicemails.DIRTY, 1);
        value.put(Voicemails.DELETED, 1);
        value.put(Voicemails.BACKED_UP, 1);
        value.put(Voicemails.RESTORED, 1);
        value.put(Voicemails.ARCHIVED, 1);
        value.put(Voicemails.IS_OMTP_VOICEMAIL, 1);

        mVoicemailProvider.update(uri, value, null, null);
        cursor = mVoicemailProvider.query(mVoicemailContentUri, VOICEMAILS_PROJECTION,
                Voicemails._ID + " = " + id, null, null, null);
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToNext());
        assertEquals(mSourcePackageName, cursor.getString(SOURCE_PACKAGE_INDEX));
        assertEquals(updateCallsNumber, cursor.getString(NUMBER_INDEX));
        assertEquals(updateDate, cursor.getLong(DATE_INDEX));
        assertEquals(updateCallsDuration, cursor.getLong(DURATION_INDEX));
        assertEquals(updateSourceData, cursor.getString(SOURCE_DATA_INDEX));
        assertEquals(1,cursor.getInt(DIRTY_INDEX));
        assertEquals(1,cursor.getInt(DELETED_INDEX));
        assertEquals(1,cursor.getInt(BACKED_UP_INDEX));
        assertEquals(1,cursor.getInt(RESTORED_INDEX));
        assertEquals(1,cursor.getInt(ARCHIVED_INDEX));
        assertEquals(1,cursor.getInt(IS_OMTP_VOICEMAIL_INDEX));
        cursor.close();

        // Test: delete
        mVoicemailProvider.delete(mVoicemailContentUri, Voicemails._ID + " = " + id, null);
        cursor = mVoicemailProvider.query(mVoicemailContentUri, VOICEMAILS_PROJECTION,
                Voicemails._ID + " = " + id, null, null, null);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    public void testForeignUpdate_dirty() throws Exception {
        if(!hasTelephony(getInstrumentation().getContext())){
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        // only the default dialer has WRITE_VOICEMAIL permission, which can modify voicemails of
        // a foreign source package.
        setTestAsDefaultDialer();
        ContentValues values = new ContentValues();
        values.put(Voicemails.SOURCE_PACKAGE, FOREIGN_SOURCE);

        Uri uri = mVoicemailProvider.insert(Voicemails.buildSourceUri(FOREIGN_SOURCE), values);

        mVoicemailProvider.update(uri, new ContentValues(), null, null);

        try (Cursor cursor = mVoicemailProvider
                .query(uri, new String[] {Voicemails.DIRTY}, null, null, null)) {
            cursor.moveToFirst();
            assertEquals(1, cursor.getInt(0));
        }
    }

    public void testForeignUpdate_explicitNotDirty() throws Exception {
        if(!hasTelephony(getInstrumentation().getContext())){
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        setTestAsDefaultDialer();
        ContentValues values = new ContentValues();
        values.put(Voicemails.SOURCE_PACKAGE, FOREIGN_SOURCE);

        Uri uri = mVoicemailProvider.insert(Voicemails.buildSourceUri(FOREIGN_SOURCE), values);

        ContentValues updateValues = new ContentValues();
        updateValues.put(Voicemails.DIRTY,0);
        mVoicemailProvider.update(uri, updateValues, null, null);

        try (Cursor cursor = mVoicemailProvider
                .query(uri, new String[] {Voicemails.DIRTY}, null, null, null)) {
            cursor.moveToFirst();
            assertEquals(0, cursor.getInt(0));
        }
    }

    public void testForeignUpdate_null_dirty() throws Exception {
        if(!hasTelephony(getInstrumentation().getContext())){
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        setTestAsDefaultDialer();
        ContentValues values = new ContentValues();
        values.put(Voicemails.SOURCE_PACKAGE, FOREIGN_SOURCE);

        Uri uri = mVoicemailProvider.insert(Voicemails.buildSourceUri(FOREIGN_SOURCE), values);

        ContentValues updateValues = new ContentValues();
        updateValues.put(Voicemails.DIRTY, (Integer) null);
        mVoicemailProvider.update(uri, updateValues, null, null);

        try (Cursor cursor = mVoicemailProvider
                .query(uri, new String[] {Voicemails.DIRTY}, null, null, null)) {
            cursor.moveToFirst();
            assertEquals(1, cursor.getInt(0));
        }
    }

    public void testForeignUpdate_NotNormalized_normalized() throws Exception {
        if(!hasTelephony(getInstrumentation().getContext())){
            Log.d(TAG, "skipping test that requires telephony feature");
            return;
        }
        setTestAsDefaultDialer();
        ContentValues values = new ContentValues();
        values.put(Voicemails.SOURCE_PACKAGE, FOREIGN_SOURCE);

        Uri uri = mVoicemailProvider.insert(Voicemails.buildSourceUri(FOREIGN_SOURCE), values);

        ContentValues updateValues = new ContentValues();
        updateValues.put(Voicemails.DIRTY, 2);
        mVoicemailProvider.update(uri, updateValues, null, null);

        try (Cursor cursor = mVoicemailProvider
                .query(uri, new String[] {Voicemails.DIRTY}, null, null, null)) {
            cursor.moveToFirst();
            assertEquals(1, cursor.getInt(0));
        }
    }

    public void testLocalUpdate_notDirty() throws Exception {

        ContentValues values = new ContentValues();
        values.put(Voicemails.DIRTY,1);

        Uri uri = mVoicemailProvider.insert(Voicemails.buildSourceUri(mSourcePackageName), values);

        mVoicemailProvider.update(uri, new ContentValues(), null, null);

        try (Cursor cursor = mVoicemailProvider
                .query(uri, new String[] {Voicemails.DIRTY}, null, null, null)) {
            cursor.moveToFirst();
            assertEquals(cursor.getInt(0), 0);
        }
    }


    // Data column should be automatically generated during insert.
    public void testInsert_doesNotUpdateDataColumn() throws Exception {

        final String newFilePath = "my/new/file/path";
        final ContentValues value = buildContentValuesForNewVoicemail();
        value.put(Voicemails._DATA, newFilePath);
        mVoicemailProvider.insert(mVoicemailContentUri, value);

        assertDataNotEquals(newFilePath);
    }

    public void testDataColumnUpdate_throwsIllegalArgumentException() throws Exception {

        final ContentValues value = buildContentValuesForNewVoicemail();
        final Uri uri = mVoicemailProvider.insert(mVoicemailContentUri, value);

        // Test: update
        final String newFilePath = "another/file/path";

        value.clear();
        value.put(Voicemails._DATA, newFilePath);
        try {
            mVoicemailProvider.update(uri, value, null, null);
            fail("IllegalArgumentException expected but not thrown.");
        } catch (IllegalArgumentException e) {
            // pass
        }

        assertDataNotEquals(newFilePath);
    }

    private void assertDataNotEquals(String newFilePath) throws RemoteException {
        // Make sure data value is not actually updated.
        final Cursor cursor = mVoicemailProvider.query(mVoicemailContentUri,
                new String[]{Voicemails._DATA}, null, null, null);
        cursor.moveToNext();
        final String data = cursor.getString(0);
        assertFalse(data.equals(newFilePath));
    }

    private ContentValues buildContentValuesForNewVoicemail() {
        final String insertCallsNumber = "0123456789";
        final long insertCallsDuration = 120;
        final String insertSourceData = "internal_id";
        final String insertMimeType = "audio/mp3";
        final long insertDate = 1324478862000L;

        ContentValues value = new ContentValues();
        value.put(Voicemails.NUMBER, insertCallsNumber);
        value.put(Voicemails.DATE, insertDate);
        value.put(Voicemails.DURATION, insertCallsDuration);
        // Source package is expected to be inserted by the provider, if not set.
        value.put(Voicemails.SOURCE_DATA, insertSourceData);
        value.put(Voicemails.MIME_TYPE, insertMimeType);
        value.put(Voicemails.IS_READ, false);
        value.put(Voicemails.HAS_CONTENT, true);

        return value;
    }

    public void testStatusTable() throws Exception {
        final String[] STATUS_PROJECTION = new String[] {
                Status._ID, Status.SOURCE_PACKAGE, Status.CONFIGURATION_STATE,
                Status.DATA_CHANNEL_STATE, Status.NOTIFICATION_CHANNEL_STATE,
                Status.SETTINGS_URI, Status.VOICEMAIL_ACCESS_URI,
                Status.QUOTA_OCCUPIED, Status.QUOTA_TOTAL};
        final int ID_INDEX = 0;
        final int SOURCE_PACKAGE_INDEX = 1;
        final int CONFIGURATION_STATE_INDEX = 2;
        final int DATA_CHANNEL_STATE_INDEX = 3;
        final int NOTIFICATION_CHANNEL_STATE_INDEX = 4;
        final int SETTINGS_URI_INDEX = 5;
        final int VOICEMAIL_ACCESS_URI_INDEX = 6;
        final int QUOTA_OCCUPIED_INDEX = 7;
        final int QUOTA_TOTAL_INDEX = 8;

        int insertConfigurationState = Status.CONFIGURATION_STATE_OK;
        int insertDataChannelState = Status.DATA_CHANNEL_STATE_OK;
        int insertNotificationChannelState = Status.NOTIFICATION_CHANNEL_STATE_OK;
        String insertSettingsUri = "settings_uri";
        String insertVoicemailAccessUri = "tel:901";
        int quotaOccupied = 7;
        int quotaTotal = 42;

        int updateDataChannelState = Status.DATA_CHANNEL_STATE_NO_CONNECTION;
        int updateNotificationChannelState = Status.NOTIFICATION_CHANNEL_STATE_MESSAGE_WAITING;
        String updateSettingsUri = "settings_uri_2";
        int updateQuotaOccupied = 1337;
        int updateQuotaTotal = 2187;

        // Test: insert
        ContentValues value = new ContentValues();
        value.put(Status.CONFIGURATION_STATE, insertConfigurationState);
        value.put(Status.DATA_CHANNEL_STATE, insertDataChannelState);
        value.put(Status.NOTIFICATION_CHANNEL_STATE, insertNotificationChannelState);
        value.put(Status.SETTINGS_URI, insertSettingsUri);
        value.put(Status.VOICEMAIL_ACCESS_URI, insertVoicemailAccessUri);
        value.put(Status.QUOTA_OCCUPIED, quotaOccupied);
        value.put(Status.QUOTA_TOTAL, quotaTotal);

        Uri uri = mStatusProvider.insert(mStatusContentUri, value);
        Cursor cursor = mStatusProvider.query(
                mStatusContentUri, STATUS_PROJECTION, null, null, null, null);
        assertTrue(cursor.moveToNext());
        assertEquals(mSourcePackageName, cursor.getString(SOURCE_PACKAGE_INDEX));
        assertEquals(insertConfigurationState, cursor.getInt(CONFIGURATION_STATE_INDEX));
        assertEquals(insertDataChannelState, cursor.getInt(DATA_CHANNEL_STATE_INDEX));
        assertEquals(insertNotificationChannelState,
                cursor.getInt(NOTIFICATION_CHANNEL_STATE_INDEX));
        assertEquals(insertSettingsUri, cursor.getString(SETTINGS_URI_INDEX));
        assertEquals(insertVoicemailAccessUri, cursor.getString(VOICEMAIL_ACCESS_URI_INDEX));
        assertEquals(quotaOccupied, cursor.getInt(QUOTA_OCCUPIED_INDEX));
        assertEquals(quotaTotal, cursor.getInt(QUOTA_TOTAL_INDEX));
        int id = cursor.getInt(ID_INDEX);
        assertEquals(id, Integer.parseInt(uri.getLastPathSegment()));
        cursor.close();

        // Test: update
        value.clear();
        value.put(Status.DATA_CHANNEL_STATE, updateDataChannelState);
        value.put(Status.NOTIFICATION_CHANNEL_STATE, updateNotificationChannelState);
        value.put(Status.SETTINGS_URI, updateSettingsUri);
        value.put(Status.QUOTA_OCCUPIED, updateQuotaOccupied);
        value.put(Status.QUOTA_TOTAL, updateQuotaTotal);

        mStatusProvider.update(uri, value, null, null);
        cursor = mStatusProvider.query(mStatusContentUri, STATUS_PROJECTION,
                Voicemails._ID + " = " + id, null, null, null);
        assertEquals(1, cursor.getCount());
        assertTrue(cursor.moveToNext());
        assertEquals(mSourcePackageName, cursor.getString(SOURCE_PACKAGE_INDEX));
        assertEquals(updateDataChannelState, cursor.getInt(DATA_CHANNEL_STATE_INDEX));
        assertEquals(updateNotificationChannelState,
                cursor.getInt(NOTIFICATION_CHANNEL_STATE_INDEX));
        assertEquals(updateSettingsUri, cursor.getString(SETTINGS_URI_INDEX));
        assertEquals(updateQuotaOccupied, cursor.getInt(QUOTA_OCCUPIED_INDEX));
        assertEquals(updateQuotaTotal, cursor.getInt(QUOTA_TOTAL_INDEX));
        cursor.close();

        // Test: delete
        mStatusProvider.delete(mStatusContentUri, Voicemails._ID + " = " + id, null);
        cursor = mStatusProvider.query(mStatusContentUri, STATUS_PROJECTION,
                Voicemails._ID + " = " + id, null, null, null);
        assertEquals(0, cursor.getCount());
        cursor.close();
    }

    public void testVoicemailTablePermissions() throws Exception {
        ContentValues value = new ContentValues();
        value.put(Voicemails.NUMBER, "0123456789");
        value.put(Voicemails.SOURCE_PACKAGE, "some.other.package");
        try {
            mVoicemailProvider.insert(mVoicemailContentUri, value);
            fail("Expected SecurityException. None thrown.");
        } catch (SecurityException e) {
            // Expected result.
        }
    }

    public void testStatusTablePermissions() throws Exception {
        ContentValues value = new ContentValues();
        value.put(Status.CONFIGURATION_STATE, Status.CONFIGURATION_STATE_OK);
        value.put(Status.SOURCE_PACKAGE, "some.other.package");
        try {
            mStatusProvider.insert(mStatusContentUri, value);
            fail("Expected SecurityException. None thrown.");
        } catch (SecurityException e) {
            // Expected result.
        }
    }

    private static boolean hasTelephony(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) &&
                packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE);
    }

    private void setTestAsDefaultDialer() throws Exception{
        assertTrue(mPreviousDefaultDialer == null);
        mPreviousDefaultDialer = getDefaultDialer(getInstrumentation());
        setDefaultDialer(getInstrumentation(),PACKAGE);
    }

    private static String setDefaultDialer(Instrumentation instrumentation, String packageName)
            throws Exception {
        return executeShellCommand(instrumentation, COMMAND_SET_DEFAULT_DIALER + packageName);
    }

    private static String getDefaultDialer(Instrumentation instrumentation) throws Exception {
        return executeShellCommand(instrumentation, COMMAND_GET_DEFAULT_DIALER);
    }

    /**
     * Executes the given shell command and returns the output in a string. Note that even if we
     * don't care about the output, we have to read the stream completely to make the command
     * execute.
     */
    private static String executeShellCommand(Instrumentation instrumentation,
            String command) throws Exception {
        final ParcelFileDescriptor parcelFileDescriptor =
                instrumentation.getUiAutomation().executeShellCommand(command);
        BufferedReader bufferedReader = null;
        try (InputStream in = new FileInputStream(parcelFileDescriptor.getFileDescriptor())) {
            bufferedReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String string = null;
            StringBuilder out = new StringBuilder();
            while ((string = bufferedReader.readLine()) != null) {
                out.append(string);
            }
            return out.toString();
        } finally {
            if (bufferedReader != null) {
                closeQuietly(bufferedReader);
            }
            closeQuietly(parcelFileDescriptor);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
                // Quietly.
            }
        }
    }
}
