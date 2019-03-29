/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.splitapp;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.ConditionVariable;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.util.DisplayMetrics;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public class SplitAppTest extends AndroidTestCase {
    private static final String TAG = "SplitAppTest";
    private static final String PKG = "com.android.cts.splitapp";

    private static final long MB_IN_BYTES = 1 * 1024 * 1024;

    public static boolean sFeatureTouched = false;
    public static String sFeatureValue = null;

    public void testNothing() throws Exception {
    }

    public void testSingleBase() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Should have untouched resources from base
        assertEquals(false, r.getBoolean(R.bool.my_receiver_enabled));

        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        assertEquals(0xff00ff00, r.getColor(R.color.my_color));
        assertEquals(123, r.getInteger(R.integer.my_integer));

        assertEquals("base", getXmlTestValue(r.getXml(R.xml.my_activity_meta)));

        // We know about drawable IDs, but they're stripped from base
        try {
            r.getDrawable(R.drawable.image);
            fail("Unexpected drawable in base");
        } catch (Resources.NotFoundException expected) {
        }

        // Should have base assets
        assertAssetContents(r, "file1.txt", "FILE1");
        assertAssetContents(r, "dir/dirfile1.txt", "DIRFILE1");

        try {
            assertAssetContents(r, "file2.txt", null);
            fail("Unexpected asset file2");
        } catch (IOException expected) {
        }

        // Should only have base manifest items
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(PKG);

        List<ResolveInfo> result = pm.queryIntentActivities(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.MyActivity", result.get(0).activityInfo.name);

        // Receiver disabled by default in base
        intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);

        result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(0, result.size());

        // We shouldn't have any native code in base
        try {
            Native.add(2, 4);
            fail("Unexpected native code in base");
        } catch (UnsatisfiedLinkError expected) {
        }
    }

    public void testDensitySingle() throws Exception {
        final Resources r = getContext().getResources();

        // We should still have base resources
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        // Now we know about drawables, but only mdpi
        final Drawable d = r.getDrawable(R.drawable.image);
        assertEquals(0xff7e00ff, getDrawableColor(d));
    }

    public void testDensityAll() throws Exception {
        final Resources r = getContext().getResources();

        // We should still have base resources
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        // Pretend that we're at each density
        updateDpi(r, DisplayMetrics.DENSITY_MEDIUM);
        assertEquals(0xff7e00ff, getDrawableColor(r.getDrawable(R.drawable.image)));

        updateDpi(r, DisplayMetrics.DENSITY_HIGH);
        assertEquals(0xff00fcff, getDrawableColor(r.getDrawable(R.drawable.image)));

        updateDpi(r, DisplayMetrics.DENSITY_XHIGH);
        assertEquals(0xff80ff00, getDrawableColor(r.getDrawable(R.drawable.image)));

        updateDpi(r, DisplayMetrics.DENSITY_XXHIGH);
        assertEquals(0xffff0000, getDrawableColor(r.getDrawable(R.drawable.image)));
    }

    public void testDensityBest1() throws Exception {
        final Resources r = getContext().getResources();

        // Pretend that we're really high density, but we only have mdpi installed
        updateDpi(r, DisplayMetrics.DENSITY_XXHIGH);
        assertEquals(0xff7e00ff, getDrawableColor(r.getDrawable(R.drawable.image)));
    }

    public void testDensityBest2() throws Exception {
        final Resources r = getContext().getResources();

        // Pretend that we're really high density, and now we have better match
        updateDpi(r, DisplayMetrics.DENSITY_XXHIGH);
        assertEquals(0xffff0000, getDrawableColor(r.getDrawable(R.drawable.image)));
    }

    public void testApi() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // We should have updated boolean, different from base
        assertEquals(true, r.getBoolean(R.bool.my_receiver_enabled));

        // Receiver should be enabled now
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);

        List<ResolveInfo> result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.MyReceiver", result.get(0).activityInfo.name);
    }

    public void testLocale() throws Exception {
        final Resources r = getContext().getResources();

        updateLocale(r, Locale.ENGLISH);
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        updateLocale(r, Locale.GERMAN);
        assertEquals("blau", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        updateLocale(r, Locale.FRENCH);
        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("pourpre", r.getString(R.string.my_string2));
    }

    public void testNative() throws Exception {
        Log.d(TAG, "testNative() thinks it's using ABI " + Native.arch());

        // Make sure we can do the maths
        assertEquals(11642, Native.add(4933, 6709));
    }

    public void testFeatureBase() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Should have untouched resources from base
        assertEquals(false, r.getBoolean(R.bool.my_receiver_enabled));

        assertEquals("blue", r.getString(R.string.my_string1));
        assertEquals("purple", r.getString(R.string.my_string2));

        assertEquals(0xff00ff00, r.getColor(R.color.my_color));
        assertEquals(123, r.getInteger(R.integer.my_integer));

        assertEquals("base", getXmlTestValue(r.getXml(R.xml.my_activity_meta)));

        // And that we can access resources from feature
        assertEquals("red", r.getString(r.getIdentifier("feature_string", "string", PKG)));
        assertEquals(123, r.getInteger(r.getIdentifier("feature_integer", "integer", PKG)));

        final Class<?> featR = Class.forName("com.android.cts.splitapp.FeatureR");
        final int boolId = (int) featR.getDeclaredField("feature_receiver_enabled").get(null);
        final int intId = (int) featR.getDeclaredField("feature_integer").get(null);
        final int stringId = (int) featR.getDeclaredField("feature_string").get(null);
        assertEquals(true, r.getBoolean(boolId));
        assertEquals(123, r.getInteger(intId));
        assertEquals("red", r.getString(stringId));

        // Should have both base and feature assets
        assertAssetContents(r, "file1.txt", "FILE1");
        assertAssetContents(r, "file2.txt", "FILE2");
        assertAssetContents(r, "dir/dirfile1.txt", "DIRFILE1");
        assertAssetContents(r, "dir/dirfile2.txt", "DIRFILE2");

        // Should have both base and feature components
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(PKG);
        List<ResolveInfo> result = pm.queryIntentActivities(intent, 0);
        assertEquals(2, result.size());
        assertEquals("com.android.cts.splitapp.MyActivity", result.get(0).activityInfo.name);
        assertEquals("com.android.cts.splitapp.FeatureActivity", result.get(1).activityInfo.name);

        // Receiver only enabled in feature
        intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);
        result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.FeatureReceiver", result.get(0).activityInfo.name);

        // And we should have a service
        intent = new Intent("com.android.cts.splitapp.service");
        intent.setPackage(PKG);
        result = pm.queryIntentServices(intent, 0);
        assertEquals(1, result.size());
        assertEquals("com.android.cts.splitapp.FeatureService", result.get(0).serviceInfo.name);

        // And a provider too
        ProviderInfo info = pm.resolveContentProvider("com.android.cts.splitapp.provider", 0);
        assertEquals("com.android.cts.splitapp.FeatureProvider", info.name);

        // And assert that we spun up the provider in this process
        final Class<?> provider = Class.forName("com.android.cts.splitapp.FeatureProvider");
        final Field field = provider.getDeclaredField("sCreated");
        assertTrue("Expected provider to have been created", (boolean) field.get(null));
        assertTrue("Expected provider to have touched us", sFeatureTouched);
        assertEquals(r.getString(R.string.my_string1), sFeatureValue);

        // Finally ensure that we can execute some code from split
        final Class<?> logic = Class.forName("com.android.cts.splitapp.FeatureLogic");
        final Method method = logic.getDeclaredMethod("mult", new Class[] {
                Integer.TYPE, Integer.TYPE });
        assertEquals(72, (int) method.invoke(null, 12, 6));

        // Make sure we didn't get an extra flag from feature split
        assertTrue("Someone parsed application flag!",
                (getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_LARGE_HEAP) == 0);

        // Make sure we have permission from base APK
        getContext().enforceCallingOrSelfPermission(android.Manifest.permission.CAMERA, null);

        try {
            // But no new permissions from the feature APK
            getContext().enforceCallingOrSelfPermission(android.Manifest.permission.INTERNET, null);
            fail("Whaaa, we somehow gained permission from feature?");
        } catch (SecurityException expected) {
        }
    }

    public void testBaseInstalled() throws Exception {
        final ConditionVariable cv = new ConditionVariable();
        final BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                assertEquals(1, intent.getIntExtra("CREATE_COUNT", -1));
                assertEquals(0, intent.getIntExtra("NEW_INTENT_COUNT", -1));
                cv.open();
            }
        };
        final IntentFilter filter = new IntentFilter("com.android.cts.norestart.BROADCAST");
        getContext().registerReceiver(r, filter);
        final Intent i = new Intent("com.android.cts.norestart.START");
        i.addCategory(Intent.CATEGORY_DEFAULT);
        getContext().startActivity(i);
        assertTrue(cv.block(2000L));
        getContext().unregisterReceiver(r);
    }

    /**
     * Tests a running activity remains active while a new feature split is installed.
     * <p>
     * Prior to running this test, the activity must be started. That is currently
     * done in {@link #testBaseInstalled()}.
     */
    public void testFeatureInstalled() throws Exception {
        final ConditionVariable cv = new ConditionVariable();
        final BroadcastReceiver r = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                assertEquals(1, intent.getIntExtra("CREATE_COUNT", -1));
                assertEquals(1, intent.getIntExtra("NEW_INTENT_COUNT", -1));
                cv.open();
            }
        };
        final IntentFilter filter = new IntentFilter("com.android.cts.norestart.BROADCAST");
        getContext().registerReceiver(r, filter);
        final Intent i = new Intent("com.android.cts.norestart.START");
        i.addCategory(Intent.CATEGORY_DEFAULT);
        getContext().startActivity(i);
        assertTrue(cv.block(2000L));
        getContext().unregisterReceiver(r);
    }

    public void testFeatureApi() throws Exception {
        final Resources r = getContext().getResources();
        final PackageManager pm = getContext().getPackageManager();

        // Should have untouched resources from base
        assertEquals(false, r.getBoolean(R.bool.my_receiver_enabled));

        // And that we can access resources from feature
        assertEquals(321, r.getInteger(r.getIdentifier("feature_integer", "integer", PKG)));

        final Class<?> featR = Class.forName("com.android.cts.splitapp.FeatureR");
        final int boolId = (int) featR.getDeclaredField("feature_receiver_enabled").get(null);
        final int intId = (int) featR.getDeclaredField("feature_integer").get(null);
        final int stringId = (int) featR.getDeclaredField("feature_string").get(null);
        assertEquals(false, r.getBoolean(boolId));
        assertEquals(321, r.getInteger(intId));
        assertEquals("red", r.getString(stringId));

        // And now both receivers should be disabled
        Intent intent = new Intent(Intent.ACTION_DATE_CHANGED);
        intent.setPackage(PKG);
        List<ResolveInfo> result = pm.queryBroadcastReceivers(intent, 0);
        assertEquals(0, result.size());
    }

    /**
     * Write app data in a number of locations that expect to remain intact over
     * long periods of time, such as across app moves.
     */
    public void testDataWrite() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        writeString(getContext().getFileStreamPath("my_int"), token);

        final SQLiteDatabase db = getContext().openOrCreateDatabase("my_db",
                Context.MODE_PRIVATE, null);
        try {
            db.execSQL("DROP TABLE IF EXISTS my_table");
            db.execSQL("CREATE TABLE my_table(value INTEGER)");
            db.execSQL("INSERT INTO my_table VALUES (101), (102), (103)");
        } finally {
            db.close();
        }
    }

    /**
     * Verify that data written by {@link #testDataWrite()} is still intact.
     */
    public void testDataRead() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        assertEquals(token, readString(getContext().getFileStreamPath("my_int")));

        final SQLiteDatabase db = getContext().openOrCreateDatabase("my_db",
                Context.MODE_PRIVATE, null);
        try {
            final Cursor cursor = db.query("my_table", null, null, null, null, null, "value ASC");
            try {
                assertEquals(3, cursor.getCount());
                assertTrue(cursor.moveToPosition(0));
                assertEquals(101, cursor.getInt(0));
                assertTrue(cursor.moveToPosition(1));
                assertEquals(102, cursor.getInt(0));
                assertTrue(cursor.moveToPosition(2));
                assertEquals(103, cursor.getInt(0));
            } finally {
                cursor.close();
            }
        } finally {
            db.close();
        }
    }

    /**
     * Verify that app is installed on internal storage.
     */
    public void testDataInternal() throws Exception {
        final StructStat internal = Os.stat(Environment.getDataDirectory().getAbsolutePath());
        final StructStat actual = Os.stat(getContext().getFilesDir().getAbsolutePath());
        assertEquals(internal.st_dev, actual.st_dev);
    }

    /**
     * Verify that app is not installed on internal storage.
     */
    public void testDataNotInternal() throws Exception {
        final StructStat internal = Os.stat(Environment.getDataDirectory().getAbsolutePath());
        final StructStat actual = Os.stat(getContext().getFilesDir().getAbsolutePath());
        MoreAsserts.assertNotEqual(internal.st_dev, actual.st_dev);
    }

    public void testPrimaryDataWrite() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        writeString(new File(getContext().getExternalFilesDir(null), "my_ext"), token);
    }

    public void testPrimaryDataRead() throws Exception {
        final String token = String.valueOf(android.os.Process.myUid());
        assertEquals(token, readString(new File(getContext().getExternalFilesDir(null), "my_ext")));
    }

    /**
     * Verify shared storage behavior when on internal storage.
     */
    public void testPrimaryInternal() throws Exception {
        assertTrue("emulated", Environment.isExternalStorageEmulated());
        assertFalse("removable", Environment.isExternalStorageRemovable());
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    /**
     * Verify shared storage behavior when on physical storage.
     */
    public void testPrimaryPhysical() throws Exception {
        assertFalse("emulated", Environment.isExternalStorageEmulated());
        assertTrue("removable", Environment.isExternalStorageRemovable());
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    /**
     * Verify shared storage behavior when on adopted storage.
     */
    public void testPrimaryAdopted() throws Exception {
        assertTrue("emulated", Environment.isExternalStorageEmulated());
        assertTrue("removable", Environment.isExternalStorageRemovable());
        assertEquals(Environment.MEDIA_MOUNTED, Environment.getExternalStorageState());
    }

    /**
     * Verify that shared storage is unmounted.
     */
    public void testPrimaryUnmounted() throws Exception {
        MoreAsserts.assertNotEqual(Environment.MEDIA_MOUNTED,
                Environment.getExternalStorageState());
    }

    /**
     * Verify that shared storage lives on same volume as app.
     */
    public void testPrimaryOnSameVolume() throws Exception {
        final File current = getContext().getFilesDir();
        final File primary = Environment.getExternalStorageDirectory();

        // Shared storage may jump through another filesystem for permission
        // enforcement, so we verify that total/free space are identical.
        final long totalDelta = Math.abs(current.getTotalSpace() - primary.getTotalSpace());
        final long freeDelta = Math.abs(current.getFreeSpace() - primary.getFreeSpace());
        if (totalDelta > MB_IN_BYTES || freeDelta > MB_IN_BYTES) {
            fail("Expected primary storage to be on same volume as app");
        }
    }

    public void testCodeCacheWrite() throws Exception {
        assertTrue(new File(getContext().getFilesDir(), "normal.raw").createNewFile());
        assertTrue(new File(getContext().getCodeCacheDir(), "cache.raw").createNewFile());
    }

    public void testCodeCacheRead() throws Exception {
        assertTrue(new File(getContext().getFilesDir(), "normal.raw").exists());
        assertFalse(new File(getContext().getCodeCacheDir(), "cache.raw").exists());
    }

    public void testRevision0_0() throws Exception {
        final PackageInfo info = getContext().getPackageManager()
                .getPackageInfo(getContext().getPackageName(), 0);
        assertEquals(0, info.baseRevisionCode);
        assertEquals(1, info.splitRevisionCodes.length);
        assertEquals(0, info.splitRevisionCodes[0]);
    }

    public void testRevision12_0() throws Exception {
        final PackageInfo info = getContext().getPackageManager()
                .getPackageInfo(getContext().getPackageName(), 0);
        assertEquals(12, info.baseRevisionCode);
        assertEquals(1, info.splitRevisionCodes.length);
        assertEquals(0, info.splitRevisionCodes[0]);
    }

    public void testRevision0_12() throws Exception {
        final PackageInfo info = getContext().getPackageManager()
                .getPackageInfo(getContext().getPackageName(), 0);
        assertEquals(0, info.baseRevisionCode);
        assertEquals(1, info.splitRevisionCodes.length);
        assertEquals(12, info.splitRevisionCodes[0]);
    }

    private static void updateDpi(Resources r, int densityDpi) {
        final Configuration c = new Configuration(r.getConfiguration());
        c.densityDpi = densityDpi;
        r.updateConfiguration(c, r.getDisplayMetrics());
    }

    private static void updateLocale(Resources r, Locale locale) {
        final Configuration c = new Configuration(r.getConfiguration());
        c.locale = locale;
        r.updateConfiguration(c, r.getDisplayMetrics());
    }

    private static int getDrawableColor(Drawable d) {
        final Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        d.draw(canvas);
        return bitmap.getPixel(0, 0);
    }

    private static String getXmlTestValue(XmlPullParser in) throws XmlPullParserException,
            IOException {
        int type;
        while ((type = in.next()) != END_DOCUMENT) {
            if (type == START_TAG) {
                final String tag = in.getName();
                if ("tag".equals(tag)) {
                    return in.getAttributeValue(null, "value");
                }
            }
        }
        return null;
    }

    private static void assertAssetContents(Resources r, String path, String expected)
            throws IOException {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(r.getAssets().open(path)));
            assertEquals(expected, in.readLine());
        } finally {
            if (in != null) in.close();
        }
    }

    private static void writeString(File file, String value) throws IOException {
        final DataOutputStream os = new DataOutputStream(new FileOutputStream(file));
        try {
            os.writeUTF(value);
        } finally {
            os.close();
        }
    }

    private static String readString(File file) throws IOException {
        final DataInputStream is = new DataInputStream(new FileInputStream(file));
        try {
            return is.readUTF();
        } finally {
            is.close();
        }
    }
}
