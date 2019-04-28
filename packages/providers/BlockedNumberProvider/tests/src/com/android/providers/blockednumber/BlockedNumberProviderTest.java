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
package com.android.providers.blockednumber;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.location.Country;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.SystemProperties;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;
import android.provider.BlockedNumberContract.SystemContract;
import android.telephony.CarrierConfigManager;
import android.telephony.TelephonyManager;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.MediumTest;

import junit.framework.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * runtest --path packages/providers/BlockedNumberProvider/tests
 */
@MediumTest
public class BlockedNumberProviderTest extends AndroidTestCase {
    private MyMockContext mMockContext;
    private ContentResolver mResolver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        BlockedNumberProvider.ALLOW_SELF_CALL = false;

        mMockContext = spy(new MyMockContext(getContext()));
        mMockContext.initializeContext();
        mResolver = mMockContext.getContentResolver();

        when(mMockContext.mUserManager.isPrimaryUser()).thenReturn(true);
        when(mMockContext.mCountryDetector.detectCountry())
                .thenReturn(new Country("US", Country.COUNTRY_SOURCE_LOCATION));
        when(mMockContext.mAppOpsManager.noteOp(
                eq(AppOpsManager.OP_WRITE_SMS), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_ERRORED);
    }

    @Override
    protected void tearDown() throws Exception {
        mMockContext.shutdown();

        super.tearDown();
    }

    private static ContentValues cv(Object... namesAndValues) {
        Assert.assertTrue((namesAndValues.length % 2) == 0);

        final ContentValues ret = new ContentValues();
        for (int i = 1; i < namesAndValues.length; i += 2) {
            final String name = namesAndValues[i - 1].toString();
            final Object value = namesAndValues[i];
            if (value == null) {
                ret.putNull(name);
            } else if (value instanceof String) {
                ret.put(name, (String) value);
            } else if (value instanceof Integer) {
                ret.put(name, (Integer) value);
            } else if (value instanceof Long) {
                ret.put(name, (Long) value);
            } else {
                Assert.fail("Unsupported type: " + value.getClass().getSimpleName());
            }
        }
        return ret;
    }

    private void assertRowCount(int count, Uri uri) {
        try (Cursor c = mResolver.query(uri, null, null, null, null)) {
            assertEquals(count, c.getCount());
        }
    }

    public void testGetType() {
        assertEquals(BlockedNumbers.CONTENT_TYPE, mResolver.getType(
                BlockedNumbers.CONTENT_URI));

        assertEquals(BlockedNumbers.CONTENT_ITEM_TYPE, mResolver.getType(
                ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, 1)));

        assertNull(mResolver.getType(
                Uri.withAppendedPath(BlockedNumberContract.AUTHORITY_URI, "invalid")));
    }

    public void testInsert() {
        insertExpectingFailure(cv());
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, null));
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, ""));
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_ID, 1));
        insertExpectingFailure(cv(BlockedNumbers.COLUMN_E164_NUMBER, "1"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-2-3"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-408-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-454-2222"));
        // Re-inserting the same number should be ok, but the E164 number is replaced.
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-454-2222",
                BlockedNumbers.COLUMN_E164_NUMBER, "+814084542222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-4542222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "045-381-1111",
                BlockedNumbers.COLUMN_E164_NUMBER, "+81453811111"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "12345"));



        assertRowCount(7, BlockedNumbers.CONTENT_URI);

        assertContents(1, "123", "");
        assertContents(2, "+1-2-3", "");
        assertContents(3, "+1-408-454-1111", "+14084541111");
        // Missing 4 due to re-insertion of the same number.
        assertContents(5, "1-408-454-2222", "+814084542222");
        assertContents(6, "1-408-4542222", "+14084542222");
        assertContents(7, "045-381-1111", "+81453811111");
        assertContents(8, "12345", "");
    }

    public void testChangesNotified() throws Exception {
        Cursor c = mResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);

        final CountDownLatch latch = new CountDownLatch(2);
        ContentObserver contentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                Assert.assertFalse(selfChange);
                latch.notify();
            }
        };
        c.registerContentObserver(contentObserver);

        try {
            Uri uri = insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "14506507000"));
            mResolver.delete(uri, null, null);
            latch.await(10, TimeUnit.SECONDS);
            verify(mMockContext.mBackupManager, times(2)).dataChanged();
        } catch (Exception e) {
            fail(e.toString());
        } finally {
            c.unregisterContentObserver(contentObserver);
        }
    }

    private Uri insert(ContentValues cv) {
        final Uri uri = mResolver.insert(BlockedNumbers.CONTENT_URI, cv);
        assertNotNull(uri);

        // Make sure the URI exists.
        try (Cursor c = mResolver.query(uri, null, null, null, null)) {
            assertEquals(1, c.getCount());
        }
        return uri;
    }

    private void insertExpectingFailure(ContentValues cv) {
        try {
            mResolver.insert(
                    BlockedNumbers.CONTENT_URI, cv);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testDelete() {
        // Prepare test data
        Uri u1 = insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        Uri u2 = insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-2-3"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-408-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-408-454-2222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "045-381-1111",
                BlockedNumbers.COLUMN_E164_NUMBER, "12345"));

        assertRowCount(5, BlockedNumbers.CONTENT_URI);

        // Delete and check the # of remaining rows.

        mResolver.delete(u1, null, null);
        assertRowCount(4, BlockedNumbers.CONTENT_URI);

        try {
            mResolver.delete(u2, "1=1", null);
            fail();
        } catch (IllegalArgumentException expected) {
            MoreAsserts.assertContainsRegex("selection must be null", expected.getMessage());
        }

        mResolver.delete(u2, null, null);
        assertRowCount(3, BlockedNumbers.CONTENT_URI);

        mResolver.delete(BlockedNumbers.CONTENT_URI,
                BlockedNumbers.COLUMN_E164_NUMBER + "=?",
                new String[]{"12345"});
        assertRowCount(2, BlockedNumbers.CONTENT_URI);

        // SQL injection should be detected.
        try {
            mResolver.delete(BlockedNumbers.CONTENT_URI, "; DROP TABLE blocked; ", null);
            fail();
        } catch (SQLiteException expected) {
        }
        assertRowCount(2, BlockedNumbers.CONTENT_URI);

        mResolver.delete(BlockedNumbers.CONTENT_URI, null, null);
        assertRowCount(0, BlockedNumbers.CONTENT_URI);
    }

    public void testUpdate() {
        try {
            mResolver.update(BlockedNumbers.CONTENT_URI, cv(),
                    /* selection =*/ null, /* args =*/ null);
            fail();
        } catch (UnsupportedOperationException expected) {
            MoreAsserts.assertContainsRegex("Update is not supported", expected.getMessage());
        }
    }

    public void testBlockSuppressionAfterEmergencyContact() {
        int blockSuppressionSeconds = 1000;
        when(mMockContext.mCarrierConfigManager.getConfig())
                .thenReturn(getBundleWithInt(blockSuppressionSeconds));

        String phoneNumber = "5004541111";
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber));

        // No emergency contact: Blocks should not be suppressed.
        assertIsBlocked(true, phoneNumber);
        assertShouldSystemBlock(true, phoneNumber);
        verifyBlocksNotSuppressed();
        assertTrue(mMockContext.mIntentsBroadcasted.isEmpty());

        // No emergency contact yet: Ending block suppression should be a no-op.
        SystemContract.endBlockSuppression(mMockContext);
        assertIsBlocked(true, phoneNumber);
        assertShouldSystemBlock(true, phoneNumber);
        verifyBlocksNotSuppressed();
        assertTrue(mMockContext.mIntentsBroadcasted.isEmpty());

        // After emergency contact blocks should be suppressed.
        long timestampMillisBeforeEmergencyContact = System.currentTimeMillis();
        SystemContract.notifyEmergencyContact(mMockContext);
        assertIsBlocked(true, phoneNumber);
        assertShouldSystemBlock(false, phoneNumber);
        SystemContract.BlockSuppressionStatus status =
                SystemContract.getBlockSuppressionStatus(mMockContext);
        assertTrue(status.isSuppressed);
        assertValidBlockSuppressionExpiration(timestampMillisBeforeEmergencyContact,
                blockSuppressionSeconds, status.untilTimestampMillis);
        assertEquals(1, mMockContext.mIntentsBroadcasted.size());
        assertEquals(SystemContract.ACTION_BLOCK_SUPPRESSION_STATE_CHANGED,
                mMockContext.mIntentsBroadcasted.get(0));
        mMockContext.mIntentsBroadcasted.clear();

        // Ending block suppression should work.
        SystemContract.endBlockSuppression(mMockContext);
        assertIsBlocked(true, phoneNumber);
        assertShouldSystemBlock(true, phoneNumber);
        verifyBlocksNotSuppressed();
        assertEquals(1, mMockContext.mIntentsBroadcasted.size());
        assertEquals(SystemContract.ACTION_BLOCK_SUPPRESSION_STATE_CHANGED,
                mMockContext.mIntentsBroadcasted.get(0));
    }

    public void testBlockSuppressionAfterEmergencyContact_invalidCarrierConfigDefaultValueUsed() {
        int invalidBlockSuppressionSeconds = 700000; // > 1 week
        when(mMockContext.mCarrierConfigManager.getConfig())
                .thenReturn(getBundleWithInt(invalidBlockSuppressionSeconds));

        String phoneNumber = "5004541111";
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, phoneNumber));

        long timestampMillisBeforeEmergencyContact = System.currentTimeMillis();
        SystemContract.notifyEmergencyContact(mMockContext);
        assertIsBlocked(true, phoneNumber);
        assertShouldSystemBlock(false, phoneNumber);
        SystemContract.BlockSuppressionStatus status =
                SystemContract.getBlockSuppressionStatus(mMockContext);
        assertTrue(status.isSuppressed);
        assertValidBlockSuppressionExpiration(timestampMillisBeforeEmergencyContact,
                7200 /* Default value of 2 hours */, status.untilTimestampMillis);
        assertEquals(1, mMockContext.mIntentsBroadcasted.size());
        assertEquals(SystemContract.ACTION_BLOCK_SUPPRESSION_STATE_CHANGED,
                mMockContext.mIntentsBroadcasted.get(0));
    }

    public void testRegularAppCannotAccessApis() {
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mMockContext).checkCallingPermission(anyString());

        try {
            insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            mResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            mResolver.delete(BlockedNumbers.CONTENT_URI, null, null);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.isBlocked(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.unblock(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            SystemContract.notifyEmergencyContact(mMockContext);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            SystemContract.endBlockSuppression(mMockContext);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            SystemContract.shouldSystemBlockNumber(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            SystemContract.getBlockSuppressionStatus(mMockContext);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }
    }

    public void testCarrierAppCanAccessApis() {
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mMockContext).checkCallingPermission(anyString());
        when(mMockContext.mTelephonyManager.checkCarrierPrivilegesForPackage(anyString()))
                .thenReturn(TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS);

        mResolver.insert(
                BlockedNumbers.CONTENT_URI, cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        assertIsBlocked(true, "123");


        // Dialer check is executed twice: once for insert, and once for isBlocked.
        verify(mMockContext.mTelephonyManager, times(2))
                .checkCarrierPrivilegesForPackage(anyString());
    }

    public void testSelfCanAccessApis() {
        BlockedNumberProvider.ALLOW_SELF_CALL = true;
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mMockContext).checkCallingPermission(anyString());

        mResolver.insert(
                BlockedNumbers.CONTENT_URI, cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        assertIsBlocked(true, "123");
    }

    public void testDefaultDialerCanAccessApis() {
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mMockContext).checkCallingPermission(anyString());
        when(mMockContext.mTelecomManager.getDefaultDialerPackage())
                .thenReturn(getContext().getPackageName());

        mResolver.insert(
                BlockedNumbers.CONTENT_URI, cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        assertIsBlocked(true, "123");

        // Dialer check is executed twice: once for insert, and once for isBlocked.
        verify(mMockContext.mTelecomManager, times(2)).getDefaultDialerPackage();
    }

    public void testPrivilegedAppCannotUseSystemApis() {
        reset(mMockContext.mAppOpsManager);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mMockContext).checkCallingPermission(anyString());

        // Pretend to be the Default SMS app.
        when(mMockContext.mAppOpsManager.noteOp(
                eq(AppOpsManager.OP_WRITE_SMS), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_ALLOWED);

        // Public APIs should work.
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        assertIsBlocked(true, "123");

        try {
            SystemContract.notifyEmergencyContact(mMockContext);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            SystemContract.endBlockSuppression(mMockContext);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            SystemContract.shouldSystemBlockNumber(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            SystemContract.getBlockSuppressionStatus(mMockContext);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }
    }

    public void testIsBlocked() {
        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mMockContext));

        // Prepare test data
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1.2-3"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-500-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1-500-454-2222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "045-111-2222",
                BlockedNumbers.COLUMN_E164_NUMBER, "+81451112222"));

        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "abc.def@gmail.com"));

        // Check
        assertIsBlocked(false, "");
        assertIsBlocked(false, null);
        assertIsBlocked(true, "123");
        assertIsBlocked(false, "1234");
        assertIsBlocked(true, "+81451112222");
        assertIsBlocked(true, "+81 45 111 2222");
        assertIsBlocked(true, "045-111-2222");
        assertIsBlocked(false, "045 111 2222");

        assertIsBlocked(true, "500-454 1111");
        assertIsBlocked(true, "500-454 2222");
        assertIsBlocked(true, "+1 500-454 1111");
        assertIsBlocked(true, "1 500-454 1111");

        assertIsBlocked(true, "abc.def@gmail.com");
        assertIsBlocked(false, "abc.def@gmail.co");
        assertIsBlocked(false, "bc.def@gmail.com");
        assertIsBlocked(false, "abcdef@gmail.com");
    }

    public void testUnblock() {
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "+1-500-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1500-454-1111"));
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "abc.def@gmail.com"));

        // Unblocking non-existent number is a no-op.
        assertEquals(0, BlockedNumberContract.unblock(mMockContext, "12345"));

        // Both rows which map to the same E164 number are deleted.
        assertEquals(2, BlockedNumberContract.unblock(mMockContext, "5004541111"));
        assertIsBlocked(false, "1-500-454-1111");

        assertEquals(1, BlockedNumberContract.unblock(mMockContext, "abc.def@gmail.com"));
        assertIsBlocked(false, "abc.def@gmail.com");
    }

    public void testEmergencyNumbersAreNotBlockedBySystem() {
        String emergencyNumber = getEmergencyNumberFromSystemPropertiesOrDefault();
        insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, emergencyNumber));

        assertIsBlocked(true, emergencyNumber);
        assertFalse(SystemContract.shouldSystemBlockNumber(mMockContext, emergencyNumber));
    }

    public void testPrivilegedAppAccessingApisAsSecondaryUser() {
        when(mMockContext.mUserManager.isPrimaryUser()).thenReturn(false);

        assertFalse(BlockedNumberContract.canCurrentUserBlockNumbers(mMockContext));

        try {
            insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
            fail("SecurityException expected");
        } catch (SecurityException expected) {
            assertTrue(expected.getMessage().contains("current user"));
        }

        try {
            mResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            mResolver.delete(BlockedNumbers.CONTENT_URI, null, null);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.isBlocked(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.unblock(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }
    }

    public void testRegularAppAccessingApisAsSecondaryUser() {
        when(mMockContext.mUserManager.isPrimaryUser()).thenReturn(false);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mMockContext).checkCallingPermission(anyString());

        assertFalse(BlockedNumberContract.canCurrentUserBlockNumbers(mMockContext));

        try {
            insert(cv(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "123"));
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            mResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            mResolver.delete(BlockedNumbers.CONTENT_URI, null, null);
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.isBlocked(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.unblock(mMockContext, "123");
            fail("SecurityException expected");
        } catch (SecurityException expected) {
        }
    }

    private void assertIsBlocked(boolean expected, String phoneNumber) {
        assertEquals(expected, BlockedNumberContract.isBlocked(mMockContext, phoneNumber));
    }

    private void assertShouldSystemBlock(boolean expected, String phoneNumber) {
        assertEquals(expected, SystemContract.shouldSystemBlockNumber(mMockContext, phoneNumber));
    }

    private PersistableBundle getBundleWithInt(int value) {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(
                CarrierConfigManager.KEY_DURATION_BLOCKING_DISABLED_AFTER_EMERGENCY_INT, value);
        return bundle;
    }

    private void verifyBlocksNotSuppressed() {
        SystemContract.BlockSuppressionStatus status =
                SystemContract.getBlockSuppressionStatus(mMockContext);
        assertFalse(status.isSuppressed);
        assertEquals(0, status.untilTimestampMillis);
    }

    private void assertValidBlockSuppressionExpiration(long timestampMillisBeforeEmergencyContact,
                                                       int blockSuppressionSeconds,
                                                       long actualExpirationMillis) {
        assertTrue(actualExpirationMillis
                >= timestampMillisBeforeEmergencyContact + blockSuppressionSeconds * 1000);
        assertTrue(actualExpirationMillis < timestampMillisBeforeEmergencyContact +
                2 * blockSuppressionSeconds * 1000);
    }

    private void assertContents(int rowId, String originalNumber, String e164Number) {
        Uri uri = ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, rowId);
        try (Cursor c = mResolver.query(uri, null, null, null, null)) {
            assertEquals(1, c.getCount());
            c.moveToNext();
            assertEquals(3, c.getColumnCount());
            assertEquals(rowId, c.getInt(c.getColumnIndex(BlockedNumbers.COLUMN_ID)));
            assertEquals(originalNumber,
                    c.getString(c.getColumnIndex(BlockedNumbers.COLUMN_ORIGINAL_NUMBER)));
            assertEquals(e164Number,
                    c.getString(c.getColumnIndex(BlockedNumbers.COLUMN_E164_NUMBER)));
        }
    }

    private String getEmergencyNumberFromSystemPropertiesOrDefault() {
        String systemEmergencyNumbers = SystemProperties.get("ril.ecclist");
        if (systemEmergencyNumbers == null) {
            return "911";
        } else {
            return systemEmergencyNumbers.split(",")[0];
        }
    }
}
