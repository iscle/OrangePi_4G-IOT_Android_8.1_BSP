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
 * limitations under the License
 */

package android.provider.cts.contacts;

import android.content.ContentValues;
import android.database.Cursor;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Common database methods.
 */
public class CommonDatabaseUtils {

    // primitive value used when record is not found.
    public static final long NOT_FOUND = -1;

    public static String[] singleRecordToArray(Cursor cursor) {
        String[] result = null;
        try {
            if (cursor.moveToNext()) {
                result = new String[cursor.getColumnCount()];
                fillArray(cursor, result);
            }
        } finally {
            closeQuietly(cursor);
        }
        return result;
    }

    public static List<String[]> multiRecordToArray(Cursor cursor) {
        ArrayList<String[]> result = new ArrayList<String[]>();
        try {
            while (cursor.moveToNext()) {
                String[] record = new String[cursor.getColumnCount()];
                fillArray(cursor, record);
                result.add(record);
            }
        } finally {
            closeQuietly(cursor);
        }
        return result;
    }

    private static void fillArray(Cursor cursor, String[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i] = cursor.getString(i);
        }
    }

    public static void closeQuietly(Cursor cursor) {
        if (cursor != null) {
            cursor.close();
        }
    }

    /**
     * Verifies that the number of string parameters is either zero or even, and inserts them
     * into the provided ContentValues object as a set of name-value pairs. Throws an exception if
     * the number of string parameters is odd, or a single null parameter was provided.
     *
     * @param values ContentValues object to insert name-value pairs into
     * @param extras Zero or even number of string parameters
     */
    public static void extrasVarArgsToValues(ContentValues values, String... extras) {
        Assert.assertNotNull(extras);
        // Check that the number of provided string parameters is even.
        Assert.assertEquals(0, extras.length % 2);
        for (int i = 0; i < extras.length; i += 2) {
            values.put(extras[i], extras[i + 1]);
        }
    }
}
