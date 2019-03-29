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

package android.content.cts;

import android.content.Context;
import android.content.cts.util.XmlUtils;
import android.content.res.ColorStateList;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.view.WindowManager;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ContextTest extends AndroidTestCase {
    private static final String TAG = "ContextTest";

    private Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getContext();
        mContext.setTheme(R.style.Test_Theme);
    }

    public void testGetString() {
        String testString = mContext.getString(R.string.context_test_string1);
        assertEquals("This is %s string.", testString);

        testString = mContext.getString(R.string.context_test_string1, "expected");
        assertEquals("This is expected string.", testString);

        testString = mContext.getString(R.string.context_test_string2);
        assertEquals("This is test string.", testString);

        // Test wrong resource id
        try {
            testString = mContext.getString(0, "expected");
            fail("Wrong resource id should not be accepted.");
        } catch (NotFoundException e) {
        }

        // Test wrong resource id
        try {
            testString = mContext.getString(0);
            fail("Wrong resource id should not be accepted.");
        } catch (NotFoundException e) {
        }
    }

    public void testGetText() {
        CharSequence testCharSequence = mContext.getText(R.string.context_test_string2);
        assertEquals("This is test string.", testCharSequence.toString());

        // Test wrong resource id
        try {
            testCharSequence = mContext.getText(0);
            fail("Wrong resource id should not be accepted.");
        } catch (NotFoundException e) {
        }
    }

    /**
     * Ensure that default and device encrypted storage areas are stored
     * separately on disk. All devices must support these storage areas, even if
     * they don't have file-based encryption, so that apps can go through a
     * backup/restore cycle between FBE and non-FBE devices.
     */
    public void testCreateDeviceProtectedStorageContext() throws Exception {
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();

        assertFalse(mContext.isDeviceProtectedStorage());
        assertTrue(deviceContext.isDeviceProtectedStorage());

        final File defaultFile = new File(mContext.getFilesDir(), "test");
        final File deviceFile = new File(deviceContext.getFilesDir(), "test");

        assertFalse(deviceFile.equals(defaultFile));

        deviceFile.createNewFile();

        // Make sure storage areas are mutually exclusive
        assertFalse(defaultFile.exists());
        assertTrue(deviceFile.exists());
    }

    public void testMoveSharedPreferencesFrom() throws Exception {
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();

        mContext.getSharedPreferences("test", Context.MODE_PRIVATE).edit().putInt("answer", 42)
                .commit();

        // Verify that we can migrate
        assertTrue(deviceContext.moveSharedPreferencesFrom(mContext, "test"));
        assertEquals(0, mContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("answer", 0));
        assertEquals(42, deviceContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("answer", 0));

        // Trying to migrate again when already done is a no-op
        assertTrue(deviceContext.moveSharedPreferencesFrom(mContext, "test"));
        assertEquals(0, mContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("answer", 0));
        assertEquals(42, deviceContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("answer", 0));

        // Add a new value and verify that we can migrate back
        deviceContext.getSharedPreferences("test", Context.MODE_PRIVATE).edit()
                .putInt("question", 24).commit();

        assertTrue(mContext.moveSharedPreferencesFrom(deviceContext, "test"));
        assertEquals(42, mContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("answer", 0));
        assertEquals(24, mContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("question", 0));
        assertEquals(0, deviceContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("answer", 0));
        assertEquals(0, deviceContext.getSharedPreferences("test", Context.MODE_PRIVATE)
                .getInt("question", 0));
    }

    public void testMoveDatabaseFrom() throws Exception {
        final Context deviceContext = mContext.createDeviceProtectedStorageContext();

        SQLiteDatabase db = mContext.openOrCreateDatabase("test.db",
                Context.MODE_PRIVATE | Context.MODE_ENABLE_WRITE_AHEAD_LOGGING, null);
        db.execSQL("CREATE TABLE list(item TEXT);");
        db.execSQL("INSERT INTO list VALUES ('cat')");
        db.execSQL("INSERT INTO list VALUES ('dog')");
        db.close();

        // Verify that we can migrate
        assertTrue(deviceContext.moveDatabaseFrom(mContext, "test.db"));
        db = deviceContext.openOrCreateDatabase("test.db",
                Context.MODE_PRIVATE | Context.MODE_ENABLE_WRITE_AHEAD_LOGGING, null);
        Cursor c = db.query("list", null, null, null, null, null, null);
        assertEquals(2, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals("cat", c.getString(0));
        assertTrue(c.moveToNext());
        assertEquals("dog", c.getString(0));
        c.close();
        db.execSQL("INSERT INTO list VALUES ('mouse')");
        db.close();

        // Trying to migrate again when already done is a no-op
        assertTrue(deviceContext.moveDatabaseFrom(mContext, "test.db"));

        // Verify that we can migrate back
        assertTrue(mContext.moveDatabaseFrom(deviceContext, "test.db"));
        db = mContext.openOrCreateDatabase("test.db",
                Context.MODE_PRIVATE | Context.MODE_ENABLE_WRITE_AHEAD_LOGGING, null);
        c = db.query("list", null, null, null, null, null, null);
        assertEquals(3, c.getCount());
        assertTrue(c.moveToFirst());
        assertEquals("cat", c.getString(0));
        assertTrue(c.moveToNext());
        assertEquals("dog", c.getString(0));
        assertTrue(c.moveToNext());
        assertEquals("mouse", c.getString(0));
        c.close();
        db.close();
    }

    public void testAccessTheme() {
        mContext.setTheme(R.style.Test_Theme);
        final Theme testTheme = mContext.getTheme();
        assertNotNull(testTheme);

        int[] attrs = {
            android.R.attr.windowNoTitle,
            android.R.attr.panelColorForeground,
            android.R.attr.panelColorBackground
        };
        TypedArray attrArray = null;
        try {
            attrArray = testTheme.obtainStyledAttributes(attrs);
            assertTrue(attrArray.getBoolean(0, false));
            assertEquals(0xff000000, attrArray.getColor(1, 0));
            assertEquals(0xffffffff, attrArray.getColor(2, 0));
        } finally {
            if (attrArray != null) {
                attrArray.recycle();
                attrArray = null;
            }
        }

        // setTheme only works for the first time
        mContext.setTheme(android.R.style.Theme_Black);
        assertSame(testTheme, mContext.getTheme());
    }

    public void testObtainStyledAttributes() {
        // Test obtainStyledAttributes(int[])
        TypedArray testTypedArray = mContext
                .obtainStyledAttributes(android.R.styleable.View);
        assertNotNull(testTypedArray);
        assertTrue(testTypedArray.length() > 2);
        assertTrue(testTypedArray.length() > 0);
        testTypedArray.recycle();

        // Test obtainStyledAttributes(int, int[])
        testTypedArray = mContext.obtainStyledAttributes(android.R.style.TextAppearance_Small,
                android.R.styleable.TextAppearance);
        assertNotNull(testTypedArray);
        assertTrue(testTypedArray.length() > 2);
        testTypedArray.recycle();

        // Test wrong null array pointer
        try {
            testTypedArray = mContext.obtainStyledAttributes(-1, null);
            fail("obtainStyledAttributes will throw a NullPointerException here.");
        } catch (NullPointerException e) {
        }

        // Test obtainStyledAttributes(AttributeSet, int[]) with unavailable resource id.
        int testInt[] = { 0, 0 };
        testTypedArray = mContext.obtainStyledAttributes(-1, testInt);
        // fail("Wrong resource id should not be accepted.");
        assertNotNull(testTypedArray);
        assertEquals(2, testTypedArray.length());
        testTypedArray.recycle();

        // Test obtainStyledAttributes(AttributeSet, int[])
        int[] attrs = android.R.styleable.DatePicker;
        testTypedArray = mContext.obtainStyledAttributes(getAttributeSet(R.layout.context_layout),
                attrs);
        assertNotNull(testTypedArray);
        assertEquals(attrs.length, testTypedArray.length());
        testTypedArray.recycle();

        // Test obtainStyledAttributes(AttributeSet, int[], int, int)
        testTypedArray = mContext.obtainStyledAttributes(getAttributeSet(R.layout.context_layout),
                attrs, 0, 0);
        assertNotNull(testTypedArray);
        assertEquals(attrs.length, testTypedArray.length());
        testTypedArray.recycle();
    }

    public void testGetSystemService() {
        // Test invalid service name
        assertNull(mContext.getSystemService("invalid"));

        // Test valid service name
        assertNotNull(mContext.getSystemService(Context.WINDOW_SERVICE));
    }

    public void testGetSystemServiceByClass() {
        // Test invalid service class
        assertNull(mContext.getSystemService(Object.class));

        // Test valid service name
        assertNotNull(mContext.getSystemService(WindowManager.class));
        assertEquals(mContext.getSystemService(Context.WINDOW_SERVICE),
                mContext.getSystemService(WindowManager.class));
    }

    public void testGetColorStateList() {
        try {
            mContext.getColorStateList(0);
            fail("Failed at testGetColorStateList");
        } catch (NotFoundException e) {
            //expected
        }

        final ColorStateList colorStateList = mContext.getColorStateList(R.color.color2);
        final int[] focusedState = {android.R.attr.state_focused};
        final int focusColor = colorStateList.getColorForState(focusedState, R.color.failColor);
        assertEquals(0xffff0000, focusColor);
    }

    public void testGetColor() {
        try {
            mContext.getColor(0);
            fail("Failed at testGetColor");
        } catch (NotFoundException e) {
            //expected
        }

        final int color = mContext.getColor(R.color.color2);
        assertEquals(0xffffff00, color);
    }

    /**
     * Developers have come to expect at least ext4-style filename behavior, so
     * verify that the underlying filesystem supports them.
     */
    public void testFilenames() throws Exception {
        final File base = mContext.getFilesDir();
        assertValidFile(new File(base, "foo"));
        assertValidFile(new File(base, ".bar"));
        assertValidFile(new File(base, "foo.bar"));
        assertValidFile(new File(base, "\u2603"));
        assertValidFile(new File(base, "\uD83D\uDCA9"));

        final int pid = android.os.Process.myPid();
        final StringBuilder sb = new StringBuilder(255);
        while (sb.length() <= 255) {
            sb.append(pid);
            sb.append(mContext.getPackageName());
        }
        sb.setLength(255);

        final String longName = sb.toString();
        final File longDir = new File(base, longName);
        assertValidFile(longDir);
        longDir.mkdir();
        final File longFile = new File(longDir, longName);
        assertValidFile(longFile);
    }

    private void assertValidFile(File file) throws Exception {
        Log.d(TAG, "Checking " + file);
        assertTrue("Failed to create " + file, file.createNewFile());
        assertTrue("Doesn't exist after create " + file, file.exists());
        assertTrue("Failed to delete after create " + file, file.delete());
        new FileOutputStream(file).close();
        assertTrue("Doesn't exist after stream " + file, file.exists());
        assertTrue("Failed to delete after stream " + file, file.delete());
    }

    private AttributeSet getAttributeSet(int resourceId) {
        final XmlResourceParser parser = getContext().getResources().getXml(
                resourceId);

        try {
            XmlUtils.beginDocument(parser, "RelativeLayout");
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        final AttributeSet attr = Xml.asAttributeSet(parser);
        assertNotNull(attr);
        return attr;
    }
}
