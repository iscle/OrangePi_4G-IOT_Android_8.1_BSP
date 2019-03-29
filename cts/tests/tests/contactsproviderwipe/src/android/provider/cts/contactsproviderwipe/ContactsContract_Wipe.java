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
 * limitations under the License.
 */

package android.provider.cts.contactsproviderwipe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.support.test.InstrumentationRegistry;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CTS tests for CP2 regarding data wipe.
 *
 * <p>We can't use CtsProviderTestCases for this test because CtsProviderTestCases creates
 * a stable connection to the contacts provider, which would cause the test process to be killed
 * when the CP2 process gets killed (for "pm clear").
 */
public class ContactsContract_Wipe extends AndroidTestCase {
    public static final String TAG = "ContactsContract_PS";

    /** 1 hour in milliseconds. */
    private static final long ONE_HOUR_IN_MILLIS = 1000L * 60 * 60;

    private long getDatabaseCreationTimestamp() {
        try (Cursor cursor = getContext().getContentResolver().query(
                ProviderStatus.CONTENT_URI, null, null, null, null)) {
            assertTrue(cursor.moveToFirst());

            final Long timestamp = cursor.getLong(
                    cursor.getColumnIndexOrThrow(ProviderStatus.DATABASE_CREATION_TIMESTAMP));
            assertNotNull(timestamp);

            return timestamp;
        }
    }

    private void assertBigger(long bigger, long smaller) {
        assertTrue("Expecting " + bigger + " > " + smaller, bigger > smaller);
    }

    private String getContactsProviderPackageName() {
        final List<ProviderInfo> list = getContext().getPackageManager().queryContentProviders(
                null, 0, PackageManager.MATCH_ALL);
        assertNotNull(list);
        for (ProviderInfo pi : list) {
            if (TextUtils.isEmpty(pi.authority)) {
                continue;
            }
            for (String authority : pi.authority.split(";")) {
                Log.i(TAG, "Found " + authority);
                if (ContactsContract.AUTHORITY.equals(authority)) {
                    return pi.packageName;
                }
            }
        }
        fail("Contacts provider package not found.");
        return null;
    }

    static List<String> readAll(ParcelFileDescriptor pfd) {
        try {
            try {
                final ArrayList<String> ret = new ArrayList<>();
                try (BufferedReader r = new BufferedReader(
                        new FileReader(pfd.getFileDescriptor()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        ret.add(line);
                    }
                    r.readLine();
                }
                return ret;
            } finally {
                pfd.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String concatResult(List<String> result) {
        final StringBuilder sb = new StringBuilder();
        for (String s : result) {
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private void wipeContactsProvider() {
        final String providerPackage = getContactsProviderPackageName();

        Log.i(TAG, "Wiping "  + providerPackage + "...");

        final String result = concatResult(readAll(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                        "pm clear " + providerPackage)));
        Log.i(TAG, "Result:" + result);

        assertEquals("Success", result);
    }

    public void testCreationTimestamp() throws Exception {
        final long originalTimestamp = getDatabaseCreationTimestamp();

        Thread.sleep(1);

        final long start = System.currentTimeMillis();

        Log.i(TAG, "start="  + start);
        Log.i(TAG, "originalTimestamp="  + originalTimestamp);

        // Check: the (old) creation time should be smaller than the start time (=now).
        // Add 1 hour to compensate for possible day light saving.
        assertBigger(start + ONE_HOUR_IN_MILLIS, originalTimestamp);

        Thread.sleep(1);

        wipeContactsProvider();

        // Check: the creation time should be bigger than the start time.
        final long newTimestamp = getDatabaseCreationTimestamp();
        Log.i(TAG, "newTimestamp="  + newTimestamp);

        assertBigger(newTimestamp, start);
    }

    public void testDatabaseWipeNotification() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Uri> notifiedUri = new AtomicReference<>();

        getContext().getContentResolver().registerContentObserver(ProviderStatus.CONTENT_URI,
                /* notifyForDescendants=*/ false,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                Log.i(TAG, "Received notification on " + uri);
                notifiedUri.set(uri);
                latch.countDown();
            }
        });

        wipeContactsProvider();

        // Accessing CP2 to make sure the process starts.
        getDatabaseCreationTimestamp();

        assertTrue("Didn't receive content change notification",
                latch.await(120, TimeUnit.SECONDS));

        assertEquals(ProviderStatus.CONTENT_URI, notifiedUri.get());
    }

    public void testDatabaseWipeBroadcast() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intents.CONTACTS_DATABASE_CREATED);

        getContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Received broadcast: " + intent);
                latch.countDown();
            }
        }, filter);

        wipeContactsProvider();

        // Accessing CP2 to make sure the process starts.
        getDatabaseCreationTimestamp();

        assertTrue("Didn't receive contacts wipe broadcast",
                latch.await(120, TimeUnit.SECONDS));
    }
}
