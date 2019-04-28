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

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppOpsManager;
import android.app.backup.BackupManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Process;
import android.os.UserManager;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.SystemContract;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.common.content.ProjectionMap;
import com.android.internal.annotations.VisibleForTesting;
import com.android.providers.blockednumber.BlockedNumberDatabaseHelper.Tables;

import java.util.Arrays;

/**
 * Blocked phone number provider.
 *
 * <p>Note the provider allows emergency numbers.  The caller (telecom) should never call it with
 * emergency numbers.
 */
public class BlockedNumberProvider extends ContentProvider {
    static final String TAG = "BlockedNumbers";

    private static final boolean DEBUG = false; // DO NOT SUBMIT WITH TRUE.

    private static final int BLOCKED_LIST = 1000;
    private static final int BLOCKED_ID = 1001;

    private static final UriMatcher sUriMatcher;

    private static final String PREF_FILE = "block_number_provider_prefs";
    private static final String BLOCK_SUPPRESSION_EXPIRY_TIME_PREF =
            "block_suppression_expiry_time_pref";
    private static final int MAX_BLOCKING_DISABLED_DURATION_SECONDS = 7 * 24 * 3600; // 1 week
    // Normally, we allow calls from self, *except* in unit tests, where we clear this flag
    // to emulate calls from other apps.
    @VisibleForTesting
    static boolean ALLOW_SELF_CALL = true;

    static {
        sUriMatcher = new UriMatcher(0);
        sUriMatcher.addURI(BlockedNumberContract.AUTHORITY, "blocked", BLOCKED_LIST);
        sUriMatcher.addURI(BlockedNumberContract.AUTHORITY, "blocked/#", BLOCKED_ID);
    }

    private static final ProjectionMap sBlockedNumberColumns = ProjectionMap.builder()
            .add(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
            .add(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
            .add(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER)
            .build();

    private static final String ID_SELECTION =
            BlockedNumberContract.BlockedNumbers.COLUMN_ID + "=?";

    private static final String ORIGINAL_NUMBER_SELECTION =
            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "=?";

    private static final String E164_NUMBER_SELECTION =
            BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER + "=?";

    @VisibleForTesting
    protected BlockedNumberDatabaseHelper mDbHelper;
    @VisibleForTesting
    protected BackupManager mBackupManager;

    @Override
    public boolean onCreate() {
        mDbHelper = BlockedNumberDatabaseHelper.getInstance(getContext());
        mBackupManager = new BackupManager(getContext());
        return true;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case BLOCKED_LIST:
                return BlockedNumberContract.BlockedNumbers.CONTENT_TYPE;
            case BLOCKED_ID:
                return BlockedNumberContract.BlockedNumbers.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        enforceWritePermissionAndPrimaryUser();

        final int match = sUriMatcher.match(uri);
        switch (match) {
            case BLOCKED_LIST:
                Uri blockedUri = insertBlockedNumber(values);
                getContext().getContentResolver().notifyChange(blockedUri, null);
                mBackupManager.dataChanged();
                return blockedUri;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    /**
     * Implements the "blocked/" insert.
     */
    private Uri insertBlockedNumber(ContentValues cv) {
        throwIfSpecified(cv, BlockedNumberContract.BlockedNumbers.COLUMN_ID);

        final String phoneNumber = cv.getAsString(
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER);

        if (TextUtils.isEmpty(phoneNumber)) {
            throw new IllegalArgumentException("Missing a required column " +
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER);
        }

        // Fill in with autogenerated columns.
        final String e164Number = Utils.getE164Number(getContext(), phoneNumber,
                cv.getAsString(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER));
        cv.put(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER, e164Number);

        if (DEBUG) {
            Log.d(TAG, String.format("inserted blocked number: %s", cv));
        }

        // Then insert.
        final long id = mDbHelper.getWritableDatabase().insertWithOnConflict(
                BlockedNumberDatabaseHelper.Tables.BLOCKED_NUMBERS, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);

        return ContentUris.withAppendedId(BlockedNumberContract.BlockedNumbers.CONTENT_URI, id);
    }

    private static void throwIfSpecified(ContentValues cv, String column) {
        if (cv.containsKey(column)) {
            throw new IllegalArgumentException("Column " + column + " must not be specified");
        }
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        enforceWritePermissionAndPrimaryUser();

        throw new UnsupportedOperationException(
                "Update is not supported.  Use delete + insert instead");
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        enforceWritePermissionAndPrimaryUser();

        final int match = sUriMatcher.match(uri);
        int numRows;
        switch (match) {
            case BLOCKED_LIST:
                numRows = deleteBlockedNumber(selection, selectionArgs);
                break;
            case BLOCKED_ID:
                numRows = deleteBlockedNumberWithId(ContentUris.parseId(uri), selection);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        mBackupManager.dataChanged();
        return numRows;
    }

    /**
     * Implements the "blocked/#" delete.
     */
    private int deleteBlockedNumberWithId(long id, String selection) {
        throwForNonEmptySelection(selection);

        return deleteBlockedNumber(ID_SELECTION, new String[]{Long.toString(id)});
    }

    /**
     * Implements the "blocked/" delete.
     */
    private int deleteBlockedNumber(String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // When selection is specified, compile it within (...) to detect SQL injection.
        if (!TextUtils.isEmpty(selection)) {
            db.validateSql("select 1 FROM " + Tables.BLOCKED_NUMBERS + " WHERE " +
                    Utils.wrapSelectionWithParens(selection),
                    /* cancellationSignal =*/ null);
        }

        return db.delete(
                BlockedNumberDatabaseHelper.Tables.BLOCKED_NUMBERS,
                selection, selectionArgs);
    }

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        enforceReadPermissionAndPrimaryUser();

        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder,
            @Nullable CancellationSignal cancellationSignal) {
        enforceReadPermissionAndPrimaryUser();

        final int match = sUriMatcher.match(uri);
        Cursor cursor;
        switch (match) {
            case BLOCKED_LIST:
                cursor = queryBlockedList(projection, selection, selectionArgs, sortOrder,
                        cancellationSignal);
                break;
            case BLOCKED_ID:
                cursor = queryBlockedListWithId(ContentUris.parseId(uri), projection, selection,
                        cancellationSignal);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        // Tell the cursor what uri to watch, so it knows when its source data changes
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    /**
     * Implements the "blocked/#" query.
     */
    private Cursor queryBlockedListWithId(long id, String[] projection, String selection,
            CancellationSignal cancellationSignal) {
        throwForNonEmptySelection(selection);

        return queryBlockedList(projection, ID_SELECTION, new String[]{Long.toString(id)},
                null, cancellationSignal);
    }

    /**
     * Implements the "blocked/" query.
     */
    private Cursor queryBlockedList(String[] projection, String selection, String[] selectionArgs,
            String sortOrder, CancellationSignal cancellationSignal) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        qb.setTables(BlockedNumberDatabaseHelper.Tables.BLOCKED_NUMBERS);
        qb.setProjectionMap(sBlockedNumberColumns);

        return qb.query(mDbHelper.getReadableDatabase(), projection, selection, selectionArgs,
                /* groupBy =*/ null, /* having =*/null, sortOrder,
                /* limit =*/ null, cancellationSignal);
    }

    private void throwForNonEmptySelection(String selection) {
        if (!TextUtils.isEmpty(selection)) {
            throw new IllegalArgumentException(
                    "When ID is specified in URI, selection must be null");
        }
    }

    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        final Bundle res = new Bundle();
        switch (method) {
            case BlockedNumberContract.METHOD_IS_BLOCKED:
                enforceReadPermissionAndPrimaryUser();

                res.putBoolean(BlockedNumberContract.RES_NUMBER_IS_BLOCKED, isBlocked(arg));
                break;
            case BlockedNumberContract.METHOD_CAN_CURRENT_USER_BLOCK_NUMBERS:
                // No permission checks: any app should be able to access this API.
                res.putBoolean(
                        BlockedNumberContract.RES_CAN_BLOCK_NUMBERS, canCurrentUserBlockUsers());
                break;
            case BlockedNumberContract.METHOD_UNBLOCK:
                enforceWritePermissionAndPrimaryUser();

                res.putInt(BlockedNumberContract.RES_NUM_ROWS_DELETED, unblock(arg));
                break;
            case SystemContract.METHOD_NOTIFY_EMERGENCY_CONTACT:
                enforceSystemWritePermissionAndPrimaryUser();

                notifyEmergencyContact();
                break;
            case SystemContract.METHOD_END_BLOCK_SUPPRESSION:
                enforceSystemWritePermissionAndPrimaryUser();

                endBlockSuppression();
                break;
            case SystemContract.METHOD_GET_BLOCK_SUPPRESSION_STATUS:
                enforceSystemReadPermissionAndPrimaryUser();

                SystemContract.BlockSuppressionStatus status = getBlockSuppressionStatus();
                res.putBoolean(SystemContract.RES_IS_BLOCKING_SUPPRESSED, status.isSuppressed);
                res.putLong(SystemContract.RES_BLOCKING_SUPPRESSED_UNTIL_TIMESTAMP,
                        status.untilTimestampMillis);
                break;
            case SystemContract.METHOD_SHOULD_SYSTEM_BLOCK_NUMBER:
                enforceSystemReadPermissionAndPrimaryUser();
                res.putBoolean(
                        BlockedNumberContract.RES_NUMBER_IS_BLOCKED, shouldSystemBlockNumber(arg));
                break;
            default:
                enforceReadPermissionAndPrimaryUser();

                throw new IllegalArgumentException("Unsupported method " + method);
        }
        return res;
    }

    private int unblock(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return 0;
        }

        StringBuilder selectionBuilder = new StringBuilder(ORIGINAL_NUMBER_SELECTION);
        String[] selectionArgs = new String[]{phoneNumber};
        final String e164Number = Utils.getE164Number(getContext(), phoneNumber, null);
        if (!TextUtils.isEmpty(e164Number)) {
            selectionBuilder.append(" or " + E164_NUMBER_SELECTION);
            selectionArgs = new String[]{phoneNumber, e164Number};
        }
        String selection = selectionBuilder.toString();
        if (DEBUG) {
            Log.d(TAG, String.format("Unblocking numbers using selection: %s, args: %s",
                    selection, Arrays.toString(selectionArgs)));
        }
        return deleteBlockedNumber(selection, selectionArgs);
    }

    private boolean isEmergencyNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        final String e164Number = Utils.getE164Number(getContext(), phoneNumber, null);
        return PhoneNumberUtils.isEmergencyNumber(phoneNumber)
                || PhoneNumberUtils.isEmergencyNumber(e164Number);
    }

    private boolean isBlocked(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return false;
        }

        final String inE164 = Utils.getE164Number(getContext(), phoneNumber, null); // may be empty.

        if (DEBUG) {
            Log.d(TAG, String.format("isBlocked: in=%s, e164=%s", phoneNumber, inE164));
        }

        final Cursor c = mDbHelper.getReadableDatabase().rawQuery(
                "SELECT " +
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "," +
                BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER +
                " FROM " + BlockedNumberDatabaseHelper.Tables.BLOCKED_NUMBERS +
                " WHERE " + BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "=?1" +
                " OR (?2 != '' AND " +
                        BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER + "=?2)",
                new String[] {phoneNumber, inE164}
                );
        try {
            while (c.moveToNext()) {
                if (DEBUG) {
                    final String original = c.getString(0);
                    final String e164 = c.getString(1);

                    Log.d(TAG, String.format("match found: original=%s, e164=%s", original, e164));
                }
                return true;
            }
        } finally {
            c.close();
        }
        // No match found.
        return false;
    }

    private boolean canCurrentUserBlockUsers() {
        UserManager userManager = getContext().getSystemService(UserManager.class);
        return userManager.isPrimaryUser();
    }

    private void notifyEmergencyContact() {
        writeBlockSuppressionExpiryTimePref(System.currentTimeMillis() +
                getBlockSuppressSecondsFromCarrierConfig() * 1000);
        notifyBlockSuppressionStateChange();
    }

    private void endBlockSuppression() {
        // Nothing to do if blocks are not being suppressed.
        if (getBlockSuppressionStatus().isSuppressed) {
            writeBlockSuppressionExpiryTimePref(0);
            notifyBlockSuppressionStateChange();
        }
    }

    private SystemContract.BlockSuppressionStatus getBlockSuppressionStatus() {
        SharedPreferences pref = getContext().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        long blockSuppressionExpiryTimeMillis = pref.getLong(BLOCK_SUPPRESSION_EXPIRY_TIME_PREF, 0);
        return new SystemContract.BlockSuppressionStatus(System.currentTimeMillis() <
                blockSuppressionExpiryTimeMillis, blockSuppressionExpiryTimeMillis);
    }

    private boolean shouldSystemBlockNumber(String phoneNumber) {
        if (getBlockSuppressionStatus().isSuppressed) {
            return false;
        }
        /// M: Performance Improvement @{
        /*
         * Emergency number check may cost more time than blocked number check.
         * In order to improve the performance, do blocked number check first.
         */
        if (!isBlocked(phoneNumber)) {
            return false;
        }
        if (isEmergencyNumber(phoneNumber)) {
            return false;
        }
        /*
        return isBlocked(phoneNumber);
        */
        return true;
        ///@}
    }

    private void writeBlockSuppressionExpiryTimePref(long expiryTimeMillis) {
        SharedPreferences pref = getContext().getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putLong(BLOCK_SUPPRESSION_EXPIRY_TIME_PREF, expiryTimeMillis);
        editor.apply();
    }

    private long getBlockSuppressSecondsFromCarrierConfig() {
        CarrierConfigManager carrierConfigManager =
                getContext().getSystemService(CarrierConfigManager.class);
        int carrierConfigValue = carrierConfigManager.getConfig().getInt
                (CarrierConfigManager.KEY_DURATION_BLOCKING_DISABLED_AFTER_EMERGENCY_INT);
        boolean isValidValue = carrierConfigValue >=0 && carrierConfigValue <=
                MAX_BLOCKING_DISABLED_DURATION_SECONDS;
        return isValidValue ? carrierConfigValue : CarrierConfigManager.getDefaultConfig().getInt(
                CarrierConfigManager.KEY_DURATION_BLOCKING_DISABLED_AFTER_EMERGENCY_INT);
    }

    /**
     * Returns {@code false} when the caller is not root, the user selected dialer, the
     * default SMS app or a carrier app.
     */
    private boolean checkForPrivilegedApplications() {
        if (Binder.getCallingUid() == Process.ROOT_UID) {
            return true;
        }

        final String callingPackage = getCallingPackage();
        if (TextUtils.isEmpty(callingPackage)) {
            Log.w(TAG, "callingPackage not accessible");
        } else {
            final TelecomManager telecom = getContext().getSystemService(TelecomManager.class);

            if (callingPackage.equals(telecom.getDefaultDialerPackage())
                    || callingPackage.equals(telecom.getSystemDialerPackage())) {
                return true;
            }
            final AppOpsManager appOps = getContext().getSystemService(AppOpsManager.class);
            if (appOps.noteOp(AppOpsManager.OP_WRITE_SMS,
                    Binder.getCallingUid(), callingPackage) == AppOpsManager.MODE_ALLOWED) {
                return true;
            }

            final TelephonyManager telephonyManager =
                    getContext().getSystemService(TelephonyManager.class);
            return telephonyManager.checkCarrierPrivilegesForPackage(callingPackage) ==
                    TelephonyManager.CARRIER_PRIVILEGE_STATUS_HAS_ACCESS;
        }
        return false;
    }

    private void notifyBlockSuppressionStateChange() {
        Intent intent = new Intent(SystemContract.ACTION_BLOCK_SUPPRESSION_STATE_CHANGED);
        getContext().sendBroadcast(intent, Manifest.permission.READ_BLOCKED_NUMBERS);
    }

    private void enforceReadPermission() {
        checkForPermission(android.Manifest.permission.READ_BLOCKED_NUMBERS);
    }

    private void enforceReadPermissionAndPrimaryUser() {
        checkForPermissionAndPrimaryUser(android.Manifest.permission.READ_BLOCKED_NUMBERS);
    }

    private void enforceWritePermissionAndPrimaryUser() {
        checkForPermissionAndPrimaryUser(android.Manifest.permission.WRITE_BLOCKED_NUMBERS);
    }

    private void checkForPermissionAndPrimaryUser(String permission) {
        checkForPermission(permission);
        if (!canCurrentUserBlockUsers()) {
            throwCurrentUserNotPermittedSecurityException();
        }
    }

    private void checkForPermission(String permission) {
        boolean permitted = passesSystemPermissionCheck(permission)
                || checkForPrivilegedApplications() || isSelf();
        if (!permitted) {
            throwSecurityException();
        }
    }

    private void enforceSystemReadPermissionAndPrimaryUser() {
        enforceSystemPermissionAndUser(android.Manifest.permission.READ_BLOCKED_NUMBERS);
    }

    private void enforceSystemWritePermissionAndPrimaryUser() {
        enforceSystemPermissionAndUser(android.Manifest.permission.WRITE_BLOCKED_NUMBERS);
    }

    private void enforceSystemPermissionAndUser(String permission) {
        if (!canCurrentUserBlockUsers()) {
            throwCurrentUserNotPermittedSecurityException();
        }

        if (!passesSystemPermissionCheck(permission)) {
            throwSecurityException();
        }
    }

    private boolean passesSystemPermissionCheck(String permission) {
        return getContext().checkCallingPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isSelf() {
        return ALLOW_SELF_CALL && Binder.getCallingPid() == Process.myPid();
    }

    private void throwSecurityException() {
        throw new SecurityException("Caller must be system, default dialer or default SMS app");
    }

    private void throwCurrentUserNotPermittedSecurityException() {
        throw new SecurityException("The current user cannot perform this operation");
    }
}
