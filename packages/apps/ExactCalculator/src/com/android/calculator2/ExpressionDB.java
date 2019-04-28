/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// We make some strong assumptions about the databases we manipulate.
// We maintain a single table containg expressions, their indices in the sequence of
// expressions, and some data associated with each expression.
// All indices are used, except for a small gap around zero.  New rows are added
// either just below the current minimum (negative) index, or just above the current
// maximum index. Currently no rows are deleted unless we clear the whole table.

// TODO: Especially if we notice serious performance issues on rotation in the history
// view, we may need to use a CursorLoader or some other scheme to preserve the database
// across rotations.
// TODO: We may want to switch to a scheme in which all expressions saved in the database have
// a positive index, and a flag indicates whether the expression is displayed as part of
// the history or not. That would avoid potential thrashing between CursorWindows when accessing
// with a negative index. It would also make it easy to sort expressions in dependency order,
// which helps with avoiding deep recursion during evaluation. But it makes the history UI
// implementation more complicated. It should be possible to make this change without a
// database version bump.

// This ensures strong thread-safety, i.e. each call looks atomic to other threads. We need some
// such property, since expressions may be read by one thread while the main thread is updating
// another expression.

package com.android.calculator2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.database.AbstractWindowedCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.provider.BaseColumns;
import android.util.Log;
import android.view.View;

public class ExpressionDB {
    private final boolean CONTINUE_WITH_BAD_DB = false;

    /* Table contents */
    public static class ExpressionEntry implements BaseColumns {
        public static final String TABLE_NAME = "expressions";
        public static final String COLUMN_NAME_EXPRESSION = "expression";
        public static final String COLUMN_NAME_FLAGS = "flags";
        // Time stamp as returned by currentTimeMillis().
        public static final String COLUMN_NAME_TIMESTAMP = "timeStamp";
    }

    /* Data to be written to or read from a row in the table */
    public static class RowData {
        private static final int DEGREE_MODE = 2;
        private static final int LONG_TIMEOUT = 1;
        public final byte[] mExpression;
        public final int mFlags;
        public long mTimeStamp;  // 0 ==> this and next field to be filled in when written.
        private static int flagsFromDegreeAndTimeout(Boolean DegreeMode, Boolean LongTimeout) {
            return (DegreeMode ? DEGREE_MODE : 0) | (LongTimeout ? LONG_TIMEOUT : 0);
        }
        private boolean degreeModeFromFlags(int flags) {
            return (flags & DEGREE_MODE) != 0;
        }
        private boolean longTimeoutFromFlags(int flags) {
            return (flags & LONG_TIMEOUT) != 0;
        }
        private static final int MILLIS_IN_15_MINS = 15 * 60 * 1000;
        private RowData(byte[] expr, int flags, long timeStamp) {
            mExpression = expr;
            mFlags = flags;
            mTimeStamp = timeStamp;
        }
        /**
         * More client-friendly constructor that hides implementation ugliness.
         * utcOffset here is uncompressed, in milliseconds.
         * A zero timestamp will cause it to be automatically filled in.
         */
        public RowData(byte[] expr, boolean degreeMode, boolean longTimeout, long timeStamp) {
            this(expr, flagsFromDegreeAndTimeout(degreeMode, longTimeout), timeStamp);
        }
        public boolean degreeMode() {
            return degreeModeFromFlags(mFlags);
        }
        public boolean longTimeout() {
            return longTimeoutFromFlags(mFlags);
        }
        /**
         * Return a ContentValues object representing the current data.
         */
        public ContentValues toContentValues() {
            ContentValues cvs = new ContentValues();
            cvs.put(ExpressionEntry.COLUMN_NAME_EXPRESSION, mExpression);
            cvs.put(ExpressionEntry.COLUMN_NAME_FLAGS, mFlags);
            if (mTimeStamp == 0) {
                mTimeStamp = System.currentTimeMillis();
            }
            cvs.put(ExpressionEntry.COLUMN_NAME_TIMESTAMP, mTimeStamp);
            return cvs;
        }
    }

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + ExpressionEntry.TABLE_NAME + " ("
            + ExpressionEntry._ID + " INTEGER PRIMARY KEY,"
            + ExpressionEntry.COLUMN_NAME_EXPRESSION + " BLOB,"
            + ExpressionEntry.COLUMN_NAME_FLAGS + " INTEGER,"
            + ExpressionEntry.COLUMN_NAME_TIMESTAMP + " INTEGER)";
    private static final String SQL_DROP_TABLE =
            "DROP TABLE IF EXISTS " + ExpressionEntry.TABLE_NAME;
    private static final String SQL_GET_MIN = "SELECT MIN(" + ExpressionEntry._ID
            + ") FROM " + ExpressionEntry.TABLE_NAME;
    private static final String SQL_GET_MAX = "SELECT MAX(" + ExpressionEntry._ID
            + ") FROM " + ExpressionEntry.TABLE_NAME;
    private static final String SQL_GET_ROW = "SELECT * FROM " + ExpressionEntry.TABLE_NAME
            + " WHERE " + ExpressionEntry._ID + " = ?";
    private static final String SQL_GET_ALL = "SELECT * FROM " + ExpressionEntry.TABLE_NAME
            + " WHERE " + ExpressionEntry._ID + " <= ? AND " +
            ExpressionEntry._ID +  " >= ?" + " ORDER BY " + ExpressionEntry._ID + " DESC ";
    // We may eventually need an index by timestamp. We don't use it yet.
    private static final String SQL_CREATE_TIMESTAMP_INDEX =
            "CREATE INDEX timestamp_index ON " + ExpressionEntry.TABLE_NAME + "("
            + ExpressionEntry.COLUMN_NAME_TIMESTAMP + ")";
    private static final String SQL_DROP_TIMESTAMP_INDEX = "DROP INDEX IF EXISTS timestamp_index";

    private class ExpressionDBHelper extends SQLiteOpenHelper {
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "Expressions.db";

        public ExpressionDBHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_ENTRIES);
            db.execSQL(SQL_CREATE_TIMESTAMP_INDEX);
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // For now just throw away history on database version upgrade/downgrade.
            db.execSQL(SQL_DROP_TIMESTAMP_INDEX);
            db.execSQL(SQL_DROP_TABLE);
            onCreate(db);
        }
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }

    private ExpressionDBHelper mExpressionDBHelper;

    private SQLiteDatabase mExpressionDB;  // Constant after initialization.

    // Expression indices between mMinAccessible and mMaxAccessible inclusive can be accessed.
    // We set these to more interesting values if a database access fails.
    // We punt on writes outside this range. We should never read outside this range.
    // If higher layers refer to an index outside this range, it will already be cached.
    // This also somewhat limits the size of the database, but only to an unreasonably
    // huge value.
    private long mMinAccessible = -10000000L;
    private long mMaxAccessible = 10000000L;

    // Never allocate new negative indicees (row ids) >= MAXIMUM_MIN_INDEX.
    public static final long MAXIMUM_MIN_INDEX = -10;

    // Minimum index value in DB.
    private long mMinIndex;
    // Maximum index value in DB.
    private long mMaxIndex;

    // A cursor that refers to the whole table, in reverse order.
    private AbstractWindowedCursor mAllCursor;

    // Expression index corresponding to a zero absolute offset for mAllCursor.
    // This is the argument we passed to the query.
    // We explicitly query only for entries that existed when we started, to avoid
    // interference from updates as we're running. It's unclear whether or not this matters.
    private int mAllCursorBase;

    // Database has been opened, mMinIndex and mMaxIndex are correct, mAllCursorBase and
    // mAllCursor have been set.
    private boolean mDBInitialized;

    // Gap between negative and positive row ids in the database.
    // Expressions with index [MAXIMUM_MIN_INDEX .. 0] are not stored.
    private static final long GAP = -MAXIMUM_MIN_INDEX + 1;

    // mLock protects mExpressionDB, mMinAccessible, and mMaxAccessible, mAllCursor,
    // mAllCursorBase, mMinIndex, mMaxIndex, and mDBInitialized. We access mExpressionDB without
    // synchronization after it's known to be initialized.  Used to wait for database
    // initialization.
    private Object mLock = new Object();

    public ExpressionDB(Context context) {
        mExpressionDBHelper = new ExpressionDBHelper(context);
        AsyncInitializer initializer = new AsyncInitializer();
        // All calls that create background database accesses are made from the UI thread, and
        // use a SERIAL_EXECUTOR. Thus they execute in order.
        initializer.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, mExpressionDBHelper);
    }

    // Is database completely unusable?
    private boolean isDBBad() {
        if (!CONTINUE_WITH_BAD_DB) {
            return false;
        }
        synchronized(mLock) {
            return mMinAccessible > mMaxAccessible;
        }
    }

    // Is the index in the accessible range of the database?
    private boolean inAccessibleRange(long index) {
        if (!CONTINUE_WITH_BAD_DB) {
            return true;
        }
        synchronized(mLock) {
            return index >= mMinAccessible && index <= mMaxAccessible;
        }
    }


    private void setBadDB() {
        if (!CONTINUE_WITH_BAD_DB) {
            Log.e("Calculator", "Database access failed");
            throw new RuntimeException("Database access failed");
        }
        displayDatabaseWarning();
        synchronized(mLock) {
            mMinAccessible = 1L;
            mMaxAccessible = -1L;
        }
    }

    /**
     * Initialize the database in the background.
     */
    private class AsyncInitializer extends AsyncTask<ExpressionDBHelper, Void, SQLiteDatabase> {
        @Override
        protected SQLiteDatabase doInBackground(ExpressionDBHelper... helper) {
            try {
                SQLiteDatabase db = helper[0].getWritableDatabase();
                synchronized(mLock) {
                    mExpressionDB = db;
                    try (Cursor minResult = db.rawQuery(SQL_GET_MIN, null)) {
                        if (!minResult.moveToFirst()) {
                            // Empty database.
                            mMinIndex = MAXIMUM_MIN_INDEX;
                        } else {
                            mMinIndex = Math.min(minResult.getLong(0), MAXIMUM_MIN_INDEX);
                        }
                    }
                    try (Cursor maxResult = db.rawQuery(SQL_GET_MAX, null)) {
                        if (!maxResult.moveToFirst()) {
                            // Empty database.
                            mMaxIndex = 0L;
                        } else {
                            mMaxIndex = Math.max(maxResult.getLong(0), 0L);
                        }
                    }
                    if (mMaxIndex > Integer.MAX_VALUE) {
                        throw new AssertionError("Expression index absurdly large");
                    }
                    mAllCursorBase = (int)mMaxIndex;
                    if (mMaxIndex != 0L || mMinIndex != MAXIMUM_MIN_INDEX) {
                        // Set up a cursor for reading the entire database.
                        String args[] = new String[]
                                { Long.toString(mAllCursorBase), Long.toString(mMinIndex) };
                        mAllCursor = (AbstractWindowedCursor) db.rawQuery(SQL_GET_ALL, args);
                        if (!mAllCursor.moveToFirst()) {
                            setBadDB();
                            return null;
                        }
                    }
                    mDBInitialized = true;
                    // We notify here, since there are unlikely cases in which the UI thread
                    // may be blocked on us, preventing onPostExecute from running.
                    mLock.notifyAll();
                }
                return db;
            } catch(SQLiteException e) {
                Log.e("Calculator", "Database initialization failed.\n", e);
                synchronized(mLock) {
                    setBadDB();
                    mLock.notifyAll();
                }
                return null;
            }
        }

        @Override
        protected void onPostExecute(SQLiteDatabase result) {
            if (result == null) {
                displayDatabaseWarning();
            } // else doInBackground already set expressionDB.
        }
        // On cancellation we do nothing;
    }

    private boolean databaseWarningIssued;

    /**
     * Display a warning message that a database access failed.
     * Do this only once. TODO: Replace with a real UI message.
     */
    void displayDatabaseWarning() {
        if (!databaseWarningIssued) {
            Log.e("Calculator", "Calculator restarting due to database error");
            databaseWarningIssued = true;
        }
    }

    /**
     * Wait until the database and mAllCursor, etc. have been initialized.
     */
    private void waitForDBInitialized() {
        synchronized(mLock) {
            // InterruptedExceptions are inconvenient here. Defer.
            boolean caught = false;
            while (!mDBInitialized && !isDBBad()) {
                try {
                    mLock.wait();
                } catch(InterruptedException e) {
                    caught = true;
                }
            }
            if (caught) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Erase the entire database. Assumes no other accesses to the database are
     * currently in progress
     * These tasks must be executed on a serial executor to avoid reordering writes.
     */
    private class AsyncEraser extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... nothings) {
            mExpressionDB.execSQL(SQL_DROP_TIMESTAMP_INDEX);
            mExpressionDB.execSQL(SQL_DROP_TABLE);
            try {
                mExpressionDB.execSQL("VACUUM");
            } catch(Exception e) {
                Log.v("Calculator", "Database VACUUM failed\n", e);
                // Should only happen with concurrent execution, which should be impossible.
            }
            mExpressionDB.execSQL(SQL_CREATE_ENTRIES);
            mExpressionDB.execSQL(SQL_CREATE_TIMESTAMP_INDEX);
            return null;
        }
        @Override
        protected void onPostExecute(Void nothing) {
            synchronized(mLock) {
                // Reinitialize everything to an empty and fully functional database.
                mMinAccessible = -10000000L;
                mMaxAccessible = 10000000L;
                mMinIndex = MAXIMUM_MIN_INDEX;
                mMaxIndex = mAllCursorBase = 0;
                mDBInitialized = true;
                mLock.notifyAll();
            }
        }
        // On cancellation we do nothing;
    }

    /**
     * Erase ALL database entries.
     * This is currently only safe if expressions that may refer to them are also erased.
     * Should only be called when concurrent references to the database are impossible.
     * TODO: Look at ways to more selectively clear the database.
     */
    public void eraseAll() {
        waitForDBInitialized();
        synchronized(mLock) {
            mDBInitialized = false;
        }
        AsyncEraser eraser = new AsyncEraser();
        eraser.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
    }

    // We track the number of outstanding writes to prevent onSaveInstanceState from
    // completing with in-flight database writes.

    private int mIncompleteWrites = 0;
    private Object mWriteCountsLock = new Object();  // Protects the preceding field.

    private void writeCompleted() {
        synchronized(mWriteCountsLock) {
            if (--mIncompleteWrites == 0) {
                mWriteCountsLock.notifyAll();
            }
        }
    }

    private void writeStarted() {
        synchronized(mWriteCountsLock) {
            ++mIncompleteWrites;
        }
    }

    /**
     * Wait for in-flight writes to complete.
     * This is not safe to call from one of our background tasks, since the writing
     * tasks may be waiting for the same underlying thread that we're using, resulting
     * in deadlock.
     */
    public void waitForWrites() {
        synchronized(mWriteCountsLock) {
            boolean caught = false;
            while (mIncompleteWrites != 0) {
                try {
                    mWriteCountsLock.wait();
                } catch (InterruptedException e) {
                    caught = true;
                }
            }
            if (caught) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Insert the given row in the database without blocking the UI thread.
     * These tasks must be executed on a serial executor to avoid reordering writes.
     */
    private class AsyncWriter extends AsyncTask<ContentValues, Void, Long> {
        @Override
        protected Long doInBackground(ContentValues... cvs) {
            long index = cvs[0].getAsLong(ExpressionEntry._ID);
            long result = mExpressionDB.insert(ExpressionEntry.TABLE_NAME, null, cvs[0]);
            writeCompleted();
            // Return 0 on success, row id on failure.
            if (result == -1) {
                return index;
            } else if (result != index) {
                throw new AssertionError("Expected row id " + index + ", got " + result);
            } else {
                return 0L;
            }
        }
        @Override
        protected void onPostExecute(Long result) {
            if (result != 0) {
                synchronized(mLock) {
                    if (result > 0) {
                        mMaxAccessible = result - 1;
                    } else {
                        mMinAccessible = result + 1;
                    }
                }
                displayDatabaseWarning();
            }
        }
        // On cancellation we do nothing;
    }

    /**
     * Add a row with index outside existing range.
     * The returned index will be just larger than any existing index unless negative_index is true.
     * In that case it will be smaller than any existing index and smaller than MAXIMUM_MIN_INDEX.
     * This ensures that prior additions have completed, but does not wait for this insertion
     * to complete.
     */
    public long addRow(boolean negativeIndex, RowData data) {
        long result;
        long newIndex;
        waitForDBInitialized();
        synchronized(mLock) {
            if (negativeIndex) {
                newIndex = mMinIndex - 1;
                mMinIndex = newIndex;
            } else {
                newIndex = mMaxIndex + 1;
                mMaxIndex = newIndex;
            }
            if (!inAccessibleRange(newIndex)) {
                // Just drop it, but go ahead and return a new index to use for the cache.
                // So long as reads of previously written expressions continue to work,
                // we should be fine. When the application is restarted, history will revert
                // to just include values between mMinAccessible and mMaxAccessible.
                return newIndex;
            }
            writeStarted();
            ContentValues cvs = data.toContentValues();
            cvs.put(ExpressionEntry._ID, newIndex);
            AsyncWriter awriter = new AsyncWriter();
            // Ensure that writes are executed in order.
            awriter.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, cvs);
        }
        return newIndex;
    }

    /**
     * Generate a fake database row that's good enough to hopefully prevent crashes,
     * but bad enough to avoid confusion with real data. In particular, the result
     * will fail to evaluate.
     */
    RowData makeBadRow() {
        CalculatorExpr badExpr = new CalculatorExpr();
        badExpr.add(R.id.lparen);
        badExpr.add(R.id.rparen);
        return new RowData(badExpr.toBytes(), false, false, 0);
    }

    /**
     * Retrieve the row with the given index using a direct query.
     * Such a row must exist.
     * We assume that the database has been initialized, and the argument has been range checked.
     */
    private RowData getRowDirect(long index) {
        RowData result;
        String args[] = new String[] { Long.toString(index) };
        try (Cursor resultC = mExpressionDB.rawQuery(SQL_GET_ROW, args)) {
            if (!resultC.moveToFirst()) {
                setBadDB();
                return makeBadRow();
            } else {
                result = new RowData(resultC.getBlob(1), resultC.getInt(2) /* flags */,
                        resultC.getLong(3) /* timestamp */);
            }
        }
        return result;
    }

    /**
     * Retrieve the row at the given offset from mAllCursorBase.
     * Note the argument is NOT an expression index!
     * We assume that the database has been initialized, and the argument has been range checked.
     */
    private RowData getRowFromCursor(int offset) {
        RowData result;
        synchronized(mLock) {
            if (!mAllCursor.moveToPosition(offset)) {
                Log.e("Calculator", "Failed to move cursor to position " + offset);
                setBadDB();
                return makeBadRow();
            }
            return new RowData(mAllCursor.getBlob(1), mAllCursor.getInt(2) /* flags */,
                        mAllCursor.getLong(3) /* timestamp */);
        }
    }

    /**
     * Retrieve the database row at the given index.
     * We currently assume that we never read data that we added since we initialized the database.
     * This makes sense, since we cache it anyway. And we should always cache recently added data.
     */
    public RowData getRow(long index) {
        waitForDBInitialized();
        if (!inAccessibleRange(index)) {
            // Even if something went wrong opening or writing the database, we should
            // not see such read requests, unless they correspond to a persistently
            // saved index, and we can't retrieve that expression.
            displayDatabaseWarning();
            return makeBadRow();
        }
        int position =  mAllCursorBase - (int)index;
        // We currently assume that the only gap between expression indices is the one around 0.
        if (index < 0) {
            position -= GAP;
        }
        if (position < 0) {
            throw new AssertionError("Database access out of range, index = " + index
                    + " rel. pos. = " + position);
        }
        if (index < 0) {
            // Avoid using mAllCursor to read data that's far away from the current position,
            // since we're likely to have to return to the current position.
            // This is a heuristic; we don't worry about doing the "wrong" thing in the race case.
            int endPosition;
            synchronized(mLock) {
                CursorWindow window = mAllCursor.getWindow();
                endPosition = window.getStartPosition() + window.getNumRows();
            }
            if (position >= endPosition) {
                return getRowDirect(index);
            }
        }
        // In the positive index case, it's probably OK to cross a cursor boundary, since
        // we're much more likely to stay in the new window.
        return getRowFromCursor(position);
    }

    public long getMinIndex() {
        waitForDBInitialized();
        synchronized(mLock) {
            return mMinIndex;
        }
    }

    public long getMaxIndex() {
        waitForDBInitialized();
        synchronized(mLock) {
            return mMaxIndex;
        }
    }

    public void close() {
        mExpressionDBHelper.close();
    }

}
