/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Instrumentation;
import android.app.stubs.DialogStubActivity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.test.InstrumentationRegistry;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.MediumTest;
import android.widget.ListView;

import com.android.compatibility.common.util.PollingCheck;

import java.io.File;

import static org.mockito.Mockito.*;

@MediumTest
public class AlertDialog_BuilderCursorTest
        extends ActivityInstrumentationTestCase2<DialogStubActivity> {
    private Builder mBuilder;
    private Context mContext;
    private Instrumentation mInstrumentation;
    private AlertDialog mDialog;
    private ListView mListView;

    private static final String TEXT_COLUMN_NAME = "text";
    private static final String CHECKED_COLUMN_NAME = "checked";

    private String[] mTextContent;
    private boolean[] mCheckedContent;

    private String[] mProjectionWithChecked;
    private String[] mProjectionWithoutChecked;

    private SQLiteDatabase mDatabase;
    private File mDatabaseFile;
    private Cursor mCursor;

    private OnClickListener mOnClickListener = mock(OnClickListener.class);

    /**
     * Multi-choice click listener that is registered on our {@link AlertDialog} when it's in
     * multi-choide mode. Note that this needs to be a separate class that is also protected (not
     * private) so that Mockito can "spy" on it.
     */
    protected class MultiChoiceClickListener implements OnMultiChoiceClickListener {
        private boolean[] mCheckedTracker;

        public MultiChoiceClickListener(boolean[] checkedTracker) {
            mCheckedTracker = checkedTracker;
        }

        @Override
        public void onClick(DialogInterface dialog, int which,
                boolean isChecked) {
            // Update the underlying database with the new checked
            // state for the specific row
            mCursor.moveToPosition(which);
            ContentValues valuesToUpdate = new ContentValues();
            valuesToUpdate.put(CHECKED_COLUMN_NAME, isChecked ? 1 : 0);
            mDatabase.update("test", valuesToUpdate,
                    TEXT_COLUMN_NAME + " = ?",
                    new String[] { mCursor.getString(1) } );
            mCursor.requery();
            mCheckedTracker[which] = isChecked;
        }
    }

    public AlertDialog_BuilderCursorTest() {
        super("android.app.stubs", DialogStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBuilder = null;
        mInstrumentation = getInstrumentation();
        mContext = getActivity();

        PollingCheck.waitFor(() -> getActivity().hasWindowFocus());

        mListView = null;
        mDialog = null;

        // Local test data for the tests
        mTextContent = new String[] { "Adele", "Beyonce", "Ciara", "Dido" };
        mCheckedContent = new boolean[] { false, false, true, false };

        // Two projections - one with "checked" column and one without
        mProjectionWithChecked = new String[] {
                "_id",                       // 0
                TEXT_COLUMN_NAME,            // 1
                CHECKED_COLUMN_NAME          // 2
        };
        mProjectionWithoutChecked = new String[] {
                "_id",                       // 0
                TEXT_COLUMN_NAME             // 1
        };

        File dbDir = mContext.getDir("tests", Context.MODE_PRIVATE);
        mDatabaseFile = new File(dbDir, "database_alert_dialog_test.db");
        if (mDatabaseFile.exists()) {
            mDatabaseFile.delete();
        }
        mDatabase = SQLiteDatabase.openOrCreateDatabase(mDatabaseFile.getPath(), null);
        assertNotNull(mDatabase);
        // Create and populate a test table
        mDatabase.execSQL(
                "CREATE TABLE test (_id INTEGER PRIMARY KEY, " + TEXT_COLUMN_NAME +
                        " TEXT, " + CHECKED_COLUMN_NAME + " INTEGER);");
        for (int i = 0; i < mTextContent.length; i++) {
            mDatabase.execSQL("INSERT INTO test (" + TEXT_COLUMN_NAME + ", " +
                    CHECKED_COLUMN_NAME + ") VALUES ('" + mTextContent[i] + "', " +
                    (mCheckedContent[i] ? "1" : "0") + ");");
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mCursor != null) {
            // Close the cursor on the UI thread as the list view in the alert dialog
            // will get notified of any change to the underlying cursor.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> {
                        mCursor.close();
                        mCursor = null;
                    });
        }
        if (mDatabase != null) {
            mDatabase.close();
        }
        if (mDatabaseFile != null) {
            mDatabaseFile.delete();
        }

        super.tearDown();
    }

    public void testSetCursor() throws Throwable {
        // Use a projection without "checked" column
        mCursor = mDatabase.query("test", mProjectionWithoutChecked,
                null, null, null, null, null);
        assertNotNull(mCursor);

        runTestOnUiThread(
                () -> {
                    mBuilder = new Builder(mContext);
                    mBuilder.setCursor(mCursor, mOnClickListener, TEXT_COLUMN_NAME);
                    mDialog = mBuilder.show();
                    mListView = mDialog.getListView();
                    mListView.performItemClick(null, 0, 0);
                });
        mInstrumentation.waitForIdleSync();
        final SQLiteCursor selected = (SQLiteCursor) mListView.getSelectedItem();
        assertEquals(mCursor.getString(1), selected.getString(1));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    public void testSetSingleChoiceItemsWithParamCursor() throws Throwable {
        // Use a projection without "checked" column
        mCursor = mDatabase.query("test", mProjectionWithoutChecked,
                null, null, null, null, null);
        assertNotNull(mCursor);

        runTestOnUiThread(
                () -> {
                    mBuilder = new Builder(mContext);
                    mBuilder.setSingleChoiceItems(mCursor, 0, TEXT_COLUMN_NAME, mOnClickListener);
                    mDialog = mBuilder.show();
                    mListView = mDialog.getListView();
                    mListView.performItemClick(null, 0, 0);
                });
        mInstrumentation.waitForIdleSync();
        final SQLiteCursor selected = (SQLiteCursor) mListView.getSelectedItem();
        assertEquals(mCursor.getString(1), selected.getString(1));
        verify(mOnClickListener, times(1)).onClick(mDialog, 0);
        verifyNoMoreInteractions(mOnClickListener);
    }

    public void testSetMultiChoiceItemsWithParamCursor() throws Throwable {
        mCursor = mDatabase.query("test", mProjectionWithChecked,
                null, null, null, null, null);
        assertNotNull(mCursor);

        final boolean[] checkedTracker = mCheckedContent.clone();
        final OnMultiChoiceClickListener mockMultiChoiceClickListener =
                spy(new MultiChoiceClickListener(checkedTracker));
        runTestOnUiThread(
                () -> {
                    mBuilder = new Builder(mContext);
                    mBuilder.setMultiChoiceItems(mCursor, CHECKED_COLUMN_NAME, TEXT_COLUMN_NAME,
                            mockMultiChoiceClickListener);
                    mDialog = mBuilder.show();
                    mListView = mDialog.getListView();
                    mListView.performItemClick(null, 0, 0);
                });
        mInstrumentation.waitForIdleSync();

        SQLiteCursor selected = (SQLiteCursor) mListView.getSelectedItem();
        assertEquals(mCursor.getString(0), selected.getString(0));
        verify(mockMultiChoiceClickListener, times(1)).onClick(mDialog, 0, true);
        // Verify that our multi-choice listener was invoked to update our tracker array
        assertTrue(checkedTracker[0]);
        assertFalse(checkedTracker[1]);
        assertTrue(checkedTracker[2]);
        assertFalse(checkedTracker[3]);

        runTestOnUiThread(() -> mListView.performItemClick(null, 1, 1));
        mInstrumentation.waitForIdleSync();

        selected = (SQLiteCursor) mListView.getSelectedItem();
        assertEquals(mCursor.getString(1), selected.getString(1));
        verify(mockMultiChoiceClickListener, times(1)).onClick(mDialog, 1, true);
        // Verify that our multi-choice listener was invoked to update our tracker array
        assertTrue(checkedTracker[0]);
        assertTrue(checkedTracker[1]);
        assertTrue(checkedTracker[2]);
        assertFalse(checkedTracker[3]);

        verifyNoMoreInteractions(mockMultiChoiceClickListener);
    }
}
