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

package android.provider.cts;


import android.content.ContentResolver;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.test.InstrumentationTestCase;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Scanner;

public class Settings_SystemTest extends InstrumentationTestCase {
    private ContentResolver cr;

    private static final String INT_FIELD = "IntField";
    private static final String LONG_FIELD = "LongField";
    private static final String FLOAT_FIELD = "FloatField";
    private static final String STRING_FIELD = "StringField";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        cr = getInstrumentation().getContext().getContentResolver();
        assertNotNull(cr);
    }

    private void deleteTestedRows() {
        String selection = System.NAME + "=\"" + INT_FIELD + "\"";
        cr.delete(System.CONTENT_URI, selection, null);

        selection = System.NAME + "=\"" + LONG_FIELD + "\"";
        cr.delete(System.CONTENT_URI, selection, null);

        selection = System.NAME + "=\"" + FLOAT_FIELD + "\"";
        cr.delete(System.CONTENT_URI, selection, null);

        selection = System.NAME + "=\"" + STRING_FIELD + "\"";
        cr.delete(System.CONTENT_URI, selection, null);
    }

    private void enableAppOps() {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(getInstrumentation().getContext().getPackageName());
        cmd.append(" android:write_settings allow");
        getInstrumentation().getUiAutomation().executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(getInstrumentation().getContext().getPackageName());
        query.append(" android:write_settings");
        String queryStr = query.toString();

        String result = "No operations.";
        while (result.contains("No operations")) {
            ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation().executeShellCommand(
                    queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    private String convertStreamToString(InputStream is) {
        try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    public void testSystemSettings() throws SettingNotFoundException {
        /**
         * first query the exist settings in System table, and then insert five
         * rows: an int, a long, a float, a String, and a ShowGTalkServiceStatus.
         * Get these six rows to check whether insert succeeded and then delete them.
         */
        // Precondition: these rows must not exist in the db when we begin
        deleteTestedRows();

        // first query exist rows
        Cursor c = cr.query(System.CONTENT_URI, null, null, null, null);

        // backup fontScale
        Configuration cfg = new Configuration();
        System.getConfiguration(cr, cfg);
        float store = cfg.fontScale;

        try {
            assertNotNull(c);
            int origCount = c.getCount();
            c.close();

            String stringValue = "cts";

            // insert 4 rows, and update 1 rows
            assertTrue(System.putInt(cr, INT_FIELD, 10));
            assertTrue(System.putLong(cr, LONG_FIELD, 20l));
            assertTrue(System.putFloat(cr, FLOAT_FIELD, 30.0f));
            assertTrue(System.putString(cr, STRING_FIELD, stringValue));

            c = cr.query(System.CONTENT_URI, null, null, null, null);
            assertNotNull(c);
            assertEquals(origCount + 4, c.getCount());
            c.close();

            // get these rows to assert
            assertEquals(10, System.getInt(cr, INT_FIELD));
            assertEquals(20l, System.getLong(cr, LONG_FIELD));
            assertEquals(30.0f, System.getFloat(cr, FLOAT_FIELD), 0.001);

            assertEquals(stringValue, System.getString(cr, STRING_FIELD));

            // delete the tested rows again
            deleteTestedRows();

            c = cr.query(System.CONTENT_URI, null, null, null, null);
            assertNotNull(c);
            assertEquals(origCount, c.getCount());

            // update fontScale row
            cfg = new Configuration();
            cfg.fontScale = 1.2f;
            assertTrue(System.putConfiguration(cr, cfg));

            System.getConfiguration(cr, cfg);
            assertEquals(1.2f, cfg.fontScale);
        } finally {
            // TODO should clean up more better
            c.close();

            // restore the fontScale
            cfg.fontScale = store;
            assertTrue(System.putConfiguration(cr, cfg));
        }
    }

    public void testGetDefaultValues() {
        assertEquals(10, System.getInt(cr, "int", 10));
        assertEquals(20, System.getLong(cr, "long", 20l));
        assertEquals(30.0f, System.getFloat(cr, "float", 30.0f), 0.001);
    }

    public void testGetUriFor() {
        String name = "table";

        Uri uri = System.getUriFor(name);
        assertNotNull(uri);
        assertEquals(Uri.withAppendedPath(System.CONTENT_URI, name), uri);
    }
}
