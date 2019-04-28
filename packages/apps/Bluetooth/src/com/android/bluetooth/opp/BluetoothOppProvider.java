/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.content.UriMatcher;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

/**
 * This provider allows application to interact with Bluetooth OPP manager
 */

public final class BluetoothOppProvider extends ContentProvider {

    private static final String TAG = "BluetoothOppProvider";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    /** Database filename */
    private static final String DB_NAME = "btopp.db";

    /** Current database version */
    private static final int DB_VERSION = 2;

    /** Database version from which upgrading is a nop */
    private static final int DB_VERSION_NOP_UPGRADE_FROM = 0;

    /** Database version to which upgrading is a nop */
    private static final int DB_VERSION_NOP_UPGRADE_TO = 1;

    /** Name of table in the database */
    private static final String DB_TABLE = "btopp";

    /** MIME type for the entire share list */
    private static final String SHARE_LIST_TYPE = "vnd.android.cursor.dir/vnd.android.btopp";

    /** MIME type for an individual share */
    private static final String SHARE_TYPE = "vnd.android.cursor.item/vnd.android.btopp";

    /** URI matcher used to recognize URIs sent by applications */
    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /** URI matcher constant for the URI of the entire share list */
    private static final int SHARES = 1;

    /** URI matcher constant for the URI of an individual share */
    private static final int SHARES_ID = 2;

    static {
        sURIMatcher.addURI("com.android.bluetooth.opp", "btopp", SHARES);
        sURIMatcher.addURI("com.android.bluetooth.opp", "btopp/#", SHARES_ID);
    }

    /** The database that lies underneath this content provider */
    private SQLiteOpenHelper mOpenHelper = null;

    /**
     * Creates and updated database on demand when opening it. Helper class to
     * create database the first time the provider is initialized and upgrade it
     * when a new version of the provider needs an updated version of the
     * database.
     */
    private final class DatabaseHelper extends SQLiteOpenHelper {

        public DatabaseHelper(final Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        /**
         * Creates database the first time we try to open it.
         */
        @Override
        public void onCreate(final SQLiteDatabase db) {
            if (V) Log.v(TAG, "populating new database");
            createTable(db);
        }

        //TODO: use this function to check garbage transfer left in db, for example,
        // a crash incoming file
        /*
         * (not a javadoc comment) Checks data integrity when opening the
         * database.
         */
        /*
         * @Override public void onOpen(final SQLiteDatabase db) {
         * super.onOpen(db); }
         */

        /**
         * Updates the database format when a content provider is used with a
         * database that was created with a different format.
         */
        // Note: technically, this could also be a downgrade, so if we want
        // to gracefully handle upgrades we should be careful about
        // what to do on downgrades.
        @Override
        public void onUpgrade(final SQLiteDatabase db, int oldV, final int newV) {
            if (oldV == DB_VERSION_NOP_UPGRADE_FROM) {
                if (newV == DB_VERSION_NOP_UPGRADE_TO) { // that's a no-op
                    // upgrade.
                    return;
                }
                // NOP_FROM and NOP_TO are identical, just in different
                // codelines. Upgrading
                // from NOP_FROM is the same as upgrading from NOP_TO.
                oldV = DB_VERSION_NOP_UPGRADE_TO;
            }
            Log.i(TAG, "Upgrading downloads database from version " + oldV + " to "
                    + newV + ", which will destroy all old data");
            dropTable(db);
            createTable(db);
        }

    }

    private void createTable(SQLiteDatabase db) {
        try {
            db.beginTransaction();
            db.execSQL("CREATE TABLE " + DB_TABLE + "(" + BluetoothShare._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT," + BluetoothShare.URI + " TEXT, "
                    + BluetoothShare.FILENAME_HINT + " TEXT, " + BluetoothShare._DATA + " TEXT, "
                    + BluetoothShare.MIMETYPE + " TEXT, " + BluetoothShare.DIRECTION + " INTEGER, "
                    + BluetoothShare.DESTINATION + " TEXT, "
                    + BluetoothShare.CARRIER_NAME + " TEXT, "
                    + BluetoothShare.VISIBILITY + " INTEGER, "
                    + BluetoothShare.USER_CONFIRMATION + " INTEGER, "
                    + BluetoothShare.STATUS + " INTEGER, " + BluetoothShare.TOTAL_BYTES
                    + " INTEGER, " + BluetoothShare.CURRENT_BYTES + " INTEGER, "
                    + BluetoothShare.TIMESTAMP + " INTEGER," + Constants.MEDIA_SCANNED
                    + " INTEGER); ");
            db.setTransactionSuccessful();
        } catch (SQLException ex) {
            Log.e(TAG, "couldn't create table in downloads database");
            throw ex;
        } finally {
            db.endTransaction();
        }
    }

    private void dropTable(SQLiteDatabase db) {
        try {
            db.beginTransaction();
            db.execSQL("DROP TABLE IF EXISTS " + DB_TABLE);
            db.setTransactionSuccessful();
        } catch (SQLException ex) {
            Log.e(TAG, "couldn't drop table in downloads database");
            throw ex;
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public String getType(Uri uri) {
        int match = sURIMatcher.match(uri);
        switch (match) {
            case SHARES: {
                return SHARE_LIST_TYPE;
            }
            case SHARES_ID: {
                return SHARE_TYPE;
            }
            default: {
                if (D) Log.d(TAG, "calling getType on an unknown URI: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
            }
        }
    }

    private static final void copyString(String key, ContentValues from, ContentValues to) {
        String s = from.getAsString(key);
        if (s != null) {
            to.put(key, s);
        }
    }

    private static final void copyInteger(String key, ContentValues from, ContentValues to) {
        Integer i = from.getAsInteger(key);
        if (i != null) {
            to.put(key, i);
        }
    }

    private static final void copyLong(String key, ContentValues from, ContentValues to) {
        Long i = from.getAsLong(key);
        if (i != null) {
            to.put(key, i);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        if (sURIMatcher.match(uri) != SHARES) {
            if (D) Log.d(TAG, "calling insert on an unknown/invalid URI: " + uri);
            throw new IllegalArgumentException("Unknown/Invalid URI " + uri);
        }

        ContentValues filteredValues = new ContentValues();

        copyString(BluetoothShare.URI, values, filteredValues);
        copyString(BluetoothShare.FILENAME_HINT, values, filteredValues);
        copyString(BluetoothShare.MIMETYPE, values, filteredValues);
        copyString(BluetoothShare.DESTINATION, values, filteredValues);
        /// M: [Carrier] Add carrier name to db
        copyString(BluetoothShare.CARRIER_NAME, values, filteredValues);

        copyInteger(BluetoothShare.VISIBILITY, values, filteredValues);
        copyLong(BluetoothShare.TOTAL_BYTES, values, filteredValues);
        if (values.getAsInteger(BluetoothShare.VISIBILITY) == null) {
            filteredValues.put(BluetoothShare.VISIBILITY, BluetoothShare.VISIBILITY_VISIBLE);
        }
        Integer dir = values.getAsInteger(BluetoothShare.DIRECTION);
        Integer con = values.getAsInteger(BluetoothShare.USER_CONFIRMATION);
        String address = values.getAsString(BluetoothShare.DESTINATION);

        if (values.getAsInteger(BluetoothShare.DIRECTION) == null) {
            dir = BluetoothShare.DIRECTION_OUTBOUND;
        }
        if (dir == BluetoothShare.DIRECTION_OUTBOUND && con == null) {
            con = BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED;
        }
        if (dir == BluetoothShare.DIRECTION_INBOUND && con == null) {
            con = BluetoothShare.USER_CONFIRMATION_PENDING;
        }
        filteredValues.put(BluetoothShare.USER_CONFIRMATION, con);
        filteredValues.put(BluetoothShare.DIRECTION, dir);

        filteredValues.put(BluetoothShare.STATUS, BluetoothShare.STATUS_PENDING);
        filteredValues.put(Constants.MEDIA_SCANNED, 0);

        Long ts = values.getAsLong(BluetoothShare.TIMESTAMP);
        if (ts == null) {
            ts = System.currentTimeMillis();
        }
        filteredValues.put(BluetoothShare.TIMESTAMP, ts);

        Context context = getContext();
        context.startService(new Intent(context, BluetoothOppService.class));

        long rowID = db.insert(DB_TABLE, null, filteredValues);

        Uri ret = null;

        if (rowID != -1) {
            context.startService(new Intent(context, BluetoothOppService.class));
            ret = Uri.parse(BluetoothShare.CONTENT_URI + "/" + rowID);
            context.getContentResolver().notifyChange(uri, null);
        } else {
            if (D) Log.d(TAG, "couldn't insert into btopp database");
            }

        return ret;
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);

        int match = sURIMatcher.match(uri);
        switch (match) {
            case SHARES: {
                qb.setTables(DB_TABLE);
                break;
            }
            case SHARES_ID: {
                qb.setTables(DB_TABLE);
                qb.appendWhere(BluetoothShare._ID + "=");
                qb.appendWhere(uri.getPathSegments().get(1));
                break;
            }
            default: {
                if (D) Log.d(TAG, "querying unknown URI: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
            }
        }

        if (V) {
            java.lang.StringBuilder sb = new java.lang.StringBuilder();
            sb.append("starting query, database is ");
            if (db != null) {
                sb.append("not ");
            }
            sb.append("null; ");
            if (projection == null) {
                sb.append("projection is null; ");
            } else if (projection.length == 0) {
                sb.append("projection is empty; ");
            } else {
                for (int i = 0; i < projection.length; ++i) {
                    sb.append("projection[");
                    sb.append(i);
                    sb.append("] is ");
                    sb.append(projection[i]);
                    sb.append("; ");
                }
            }
            sb.append("selection is ");
            sb.append(selection);
            sb.append("; ");
            if (selectionArgs == null) {
                sb.append("selectionArgs is null; ");
            } else if (selectionArgs.length == 0) {
                sb.append("selectionArgs is empty; ");
            } else {
                for (int i = 0; i < selectionArgs.length; ++i) {
                    sb.append("selectionArgs[");
                    sb.append(i);
                    sb.append("] is ");
                    sb.append(selectionArgs[i]);
                    sb.append("; ");
                }
            }
            sb.append("sort is ");
            sb.append(sortOrder);
            sb.append(".");
            Log.v(TAG, sb.toString());
        }

        Cursor ret = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        if (ret != null) {
            ret.setNotificationUri(getContext().getContentResolver(), uri);
            if (V) Log.v(TAG, "created cursor " + ret + " on behalf of ");// +
        } else {
            if (D) Log.d(TAG, "query failed in downloads database");
            }

        return ret;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        int count;
        long rowId = 0;

        int match = sURIMatcher.match(uri);
        switch (match) {
            case SHARES:
            case SHARES_ID: {
                String myWhere;
                if (selection != null) {
                    if (match == SHARES) {
                        myWhere = "( " + selection + " )";
                    } else {
                        myWhere = "( " + selection + " ) AND ";
                    }
                } else {
                    myWhere = "";
                }
                if (match == SHARES_ID) {
                    String segment = uri.getPathSegments().get(1);
                    rowId = Long.parseLong(segment);
                    myWhere += " ( " + BluetoothShare._ID + " = " + rowId + " ) ";
                }

                if (values.size() > 0) {
                    count = db.update(DB_TABLE, values, myWhere, selectionArgs);
                } else {
                    count = 0;
                }
                break;
            }
            default: {
                if (D) Log.d(TAG, "updating unknown/invalid URI: " + uri);
                throw new UnsupportedOperationException("Cannot update URI: " + uri);
            }
        }
        getContext().getContentResolver().notifyChange(uri, null);

        return count;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        int match = sURIMatcher.match(uri);
        switch (match) {
            case SHARES:
            case SHARES_ID: {
                String myWhere;
                if (selection != null) {
                    if (match == SHARES) {
                        myWhere = "( " + selection + " )";
                    } else {
                        myWhere = "( " + selection + " ) AND ";
                    }
                } else {
                    myWhere = "";
                }
                if (match == SHARES_ID) {
                    String segment = uri.getPathSegments().get(1);
                    long rowId = Long.parseLong(segment);
                    myWhere += " ( " + BluetoothShare._ID + " = " + rowId + " ) ";
                }
                try {
                    db.beginTransaction();
                    count = db.delete(DB_TABLE, myWhere, selectionArgs);
                    db.setTransactionSuccessful();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                    Log.e(TAG, "couldn't delete data from table");
                    throw ex;
                } finally {
                    db.endTransaction();
                }
                break;
            }
            default: {
                if (D) Log.d(TAG, "deleting unknown/invalid URI: " + uri);
                throw new UnsupportedOperationException("Cannot delete URI: " + uri);
            }
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
