/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.content.cts;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.IOException;

/**
 * Test {@link CursorWindowContentProvider} .
 */
public class ContentProviderCursorWindowTest extends AndroidTestCase {
    private static final String TAG = "ContentProviderCursorWindowTest";

    public void testQuery() throws IOException {
        Cursor cursor = getContext().getContentResolver().query(
                Uri.parse("content://cursorwindow.provider/hello"),
                null, null, null, null
        );
        try {
            cursor.moveToFirst();

            int type = cursor.getType(0);
            if (type != Cursor.FIELD_TYPE_BLOB) {
                fail("Unexpected type " + type);
            }
            byte[] blob = cursor.getBlob(0);
            Log.i(TAG,  "Blob length " + blob.length);
            fail("getBlob should fail due to invalid offset used in the field slot");
        } catch (SQLiteException expected) {
            Log.i(TAG, "Expected exception: " + expected);
        } finally {
            cursor.close();
        }
    }
}
