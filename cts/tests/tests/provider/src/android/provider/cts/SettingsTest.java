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

import com.android.compatibility.common.util.SystemUtil;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.provider.Settings;
import android.test.InstrumentationTestCase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SettingsTest extends InstrumentationTestCase {
    public void testSystemTable() throws RemoteException {
        final String[] SYSTEM_PROJECTION = new String[] {
                Settings.System._ID, Settings.System.NAME, Settings.System.VALUE
        };
        final int NAME_INDEX = 1;
        final int VALUE_INDEX = 2;

        String name = "name";
        String insertValue = "value_insert";
        String updateValue = "value_update";

        // get provider
        ContentResolver cr = getContext().getContentResolver();
        ContentProviderClient provider =
                cr.acquireContentProviderClient(Settings.System.CONTENT_URI);
        Cursor cursor = null;

        try {
            // Test: insert
            ContentValues value = new ContentValues();
            value.put(Settings.System.NAME, name);
            value.put(Settings.System.VALUE, insertValue);

            provider.insert(Settings.System.CONTENT_URI, value);
            cursor = provider.query(Settings.System.CONTENT_URI, SYSTEM_PROJECTION,
                    Settings.System.NAME + "=\"" + name + "\"", null, null, null);
            assertNotNull(cursor);
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(name, cursor.getString(NAME_INDEX));
            assertEquals(insertValue, cursor.getString(VALUE_INDEX));
            cursor.close();
            cursor = null;

            // Test: update
            value.clear();
            value.put(Settings.System.NAME, name);
            value.put(Settings.System.VALUE, updateValue);

            provider.update(Settings.System.CONTENT_URI, value,
                    Settings.System.NAME + "=\"" + name + "\"", null);
            cursor = provider.query(Settings.System.CONTENT_URI, SYSTEM_PROJECTION,
                    Settings.System.NAME + "=\"" + name + "\"", null, null, null);
            assertNotNull(cursor);
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertEquals(name, cursor.getString(NAME_INDEX));
            assertEquals(updateValue, cursor.getString(VALUE_INDEX));
            cursor.close();
            cursor = null;

            // Test: delete
            provider.delete(Settings.System.CONTENT_URI,
                    Settings.System.NAME + "=\"" + name + "\"", null);
            cursor = provider.query(Settings.System.CONTENT_URI, SYSTEM_PROJECTION,
                    Settings.System.NAME + "=\"" + name + "\"", null, null, null);
            assertNotNull(cursor);
            assertEquals(0, cursor.getCount());
            cursor.close();
            cursor = null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public void testSecureTable() throws Exception {
        final String[] SECURE_PROJECTION = new String[] {
                Settings.Secure._ID, Settings.Secure.NAME, Settings.Secure.VALUE
        };

        ContentResolver cr = getContext().getContentResolver();
        ContentProviderClient provider =
                cr.acquireContentProviderClient(Settings.Secure.CONTENT_URI);
        assertNotNull(provider);

        // Test that the secure table can be read from.
        Cursor cursor = null;
        try {
            cursor = provider.query(Settings.Global.CONTENT_URI, SECURE_PROJECTION,
                    Settings.Global.NAME + "=\"" + Settings.Global.ADB_ENABLED + "\"",
                    null, null, null);
            assertNotNull(cursor);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static final String[] SELECT_VALUE =
        new String[] { Settings.NameValueTable.VALUE };
    private static final String NAME_EQ_PLACEHOLDER = "name=?";

    private void tryBadTableAccess(String table, String goodtable, String name) {
        ContentResolver cr = getContext().getContentResolver();

        Uri uri = Uri.parse("content://settings/" + table);
        ContentValues cv = new ContentValues();
        cv.put("name", "name");
        cv.put("value", "xxxTESTxxx");

        try {
            cr.insert(uri, cv);
            fail("SettingsProvider didn't throw IllegalArgumentException for insert name "
                    + name + " at URI " + uri);
        } catch (IllegalArgumentException e) {
            /* ignore */
        }

        try {
            cr.update(uri, cv, NAME_EQ_PLACEHOLDER, new String[]{name});
            fail("SettingsProvider didn't throw SecurityException for update name "
                    + name + " at URI " + uri);
        } catch (IllegalArgumentException e) {
            /* ignore */
        }

        try {
            cr.query(uri, SELECT_VALUE, NAME_EQ_PLACEHOLDER,
                    new String[]{name}, null);
            fail("SettingsProvider didn't throw IllegalArgumentException for query name "
                    + name + " at URI " + uri);
        } catch (IllegalArgumentException e) {
            /* ignore */
        }


        try {
            cr.delete(uri, NAME_EQ_PLACEHOLDER, new String[]{name});
            fail("SettingsProvider didn't throw IllegalArgumentException for delete name "
                    + name + " at URI " + uri);
        } catch (IllegalArgumentException e) {
            /* ignore */
        }


        String mimeType = cr.getType(uri);
        assertNull("SettingsProvider didn't return null MIME type for getType at URI "
                + uri, mimeType);

        uri = Uri.parse("content://settings/" + goodtable);
        try {
            Cursor c = cr.query(uri, SELECT_VALUE, NAME_EQ_PLACEHOLDER,
                    new String[]{name}, null);
            assertNotNull(c);
            String value = c.moveToNext() ? c.getString(0) : null;
            if ("xxxTESTxxx".equals(value)) {
                fail("Successfully modified " + name + " at URI " + uri);
            }
            c.close();
        } catch (SQLiteException e) {
            // This is fine.
        }
    }

    public void testAccessNonTable() {
        tryBadTableAccess("SYSTEM", "system", "install_non_market_apps");
        tryBadTableAccess("SECURE", "secure", "install_non_market_apps");
        tryBadTableAccess(" secure", "secure", "install_non_market_apps");
        tryBadTableAccess("secure ", "secure", "install_non_market_apps");
        tryBadTableAccess(" secure ", "secure", "install_non_market_apps");
    }

    public void testUserDictionarySettingsExists() throws RemoteException {
        final Intent intent = new Intent(Settings.ACTION_USER_DICTIONARY_SETTINGS);
        final ResolveInfo ri = getContext().getPackageManager().resolveActivity(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        assertTrue(ri != null);
    }

    public void testNoStaleValueModifiedFromSameProcess() throws Exception {
        final int initialValue = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING);
        try {
            for (int i = 0; i < 100; i++) {
                final int expectedValue = i % 2;
                Settings.System.putInt(getInstrumentation().getContext().getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING, expectedValue);
                final int actualValue = Settings.System.getInt(getContext().getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING);
                assertSame("Settings write must be atomic", expectedValue, actualValue);
            }
        } finally {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING, initialValue);
        }
    }

    public void testNoStaleValueModifiedFromOtherProcess() throws Exception {
        final int initialValue = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING);
        try {
            for (int i = 0; i < 20; i++) {
                final int expectedValue = i % 2;
                SystemUtil.runShellCommand(getInstrumentation(), "settings put system "
                        +  Settings.System.VIBRATE_WHEN_RINGING + " " + expectedValue);
                final int actualValue = Settings.System.getInt(getContext().getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING);
                assertSame("Settings write must be atomic", expectedValue, actualValue);
            }
        } finally {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING, initialValue);
        }
    }

    public void testNoStaleValueModifiedFromMultipleProcesses() throws Exception {
        final int initialValue = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING);
        try {
            for (int i = 0; i < 20; i++) {
                final int expectedValue = i % 2;
                final int unexpectedValue = (i + 1) % 2;
                Settings.System.putInt(getInstrumentation().getContext().getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING, expectedValue);
                SystemUtil.runShellCommand(getInstrumentation(), "settings put system "
                        +  Settings.System.VIBRATE_WHEN_RINGING + " " + unexpectedValue);
                Settings.System.putInt(getInstrumentation().getContext().getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING, expectedValue);
                final int actualValue = Settings.System.getInt(getContext().getContentResolver(),
                        Settings.System.VIBRATE_WHEN_RINGING);
                assertSame("Settings write must be atomic", expectedValue, actualValue);
            }
        } finally {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING, initialValue);
        }
    }

    public void testUriChangesUpdatingFromDifferentProcesses() throws Exception {
        final int initialValue = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING);

        HandlerThread handlerThread = new HandlerThread("MyThread");
        handlerThread.start();

        CountDownLatch uriChangeCount = new CountDownLatch(4);
        Uri uri = Settings.System.getUriFor(Settings.System.VIBRATE_WHEN_RINGING);
        getContext().getContentResolver().registerContentObserver(uri,
                false, new ContentObserver(new Handler(handlerThread.getLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        uriChangeCount.countDown();
                    }
                });

        try {
            final int anotherValue = initialValue == 1 ? 0 : 1;
            Settings.System.putInt(getInstrumentation().getContext().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING, anotherValue);
            SystemUtil.runShellCommand(getInstrumentation(), "settings put system "
                    +  Settings.System.VIBRATE_WHEN_RINGING + " " + initialValue);
            Settings.System.putInt(getInstrumentation().getContext().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING, anotherValue);
            Settings.System.getInt(getContext().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING);
            SystemUtil.runShellCommand(getInstrumentation(), "settings put system "
                    +  Settings.System.VIBRATE_WHEN_RINGING + " " + initialValue);

            uriChangeCount.await(30000, TimeUnit.MILLISECONDS);

            if (uriChangeCount.getCount() > 0) {
                fail("Expected change not received for Uri: " + uri);
            }
        } finally {
            Settings.System.putInt(getContext().getContentResolver(),
                    Settings.System.VIBRATE_WHEN_RINGING, initialValue);
            handlerThread.quit();
        }
    }

    private Context getContext() {
        return getInstrumentation().getContext();
    }
}
