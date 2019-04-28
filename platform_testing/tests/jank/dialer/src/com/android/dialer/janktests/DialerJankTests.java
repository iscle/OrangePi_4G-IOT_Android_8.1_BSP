/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.dialer.janktests;

import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.view.View;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Random;

/**
 * Jank test for Dialer app
 * open a contact, initiate call to open the dialing screen
 * fling call log
 */
public class DialerJankTests extends JankTestBase {
    private static final int TIMEOUT = 5000;
    private static final int INNER_LOOP = 5;
    private static final int EXPECTED_FRAMES = 100;
    private static final String PACKAGE_NAME = "com.google.android.dialer";
    private static final String RES_PACKAGE_NAME = "com.android.dialer";
    private static final String RES_PACKAGE_NAME2 = "com.android.contacts";
    private static final String RES_PACKAGE_NAME3 = "android";
    private static final String APP_NAME = "Phone";
    private static final String CONTACT_NAME = "A AAA Test Account";
    private static final String CONTACT_NUMBER = "2468";
    private UiDevice mDevice;
    static final int PICK_CONTACT_REQUEST = 1;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDevice.setOrientationNatural();
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    public void launchApp(String packageName) {
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        Intent appIntent = pm.getLaunchIntentForPackage(packageName);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().getContext().startActivity(appIntent);
        mDevice.waitForIdle();
    }

    public void launchDialer () throws OperationApplicationException, RemoteException {
        if (!doesContactExist()) {
            insertNewContacts();
        }
        launchApp(PACKAGE_NAME);
        mDevice.waitForIdle();

        // Open contacts list
        UiObject2 contacts = mDevice.wait(Until.findObject(By.desc("Contacts")), TIMEOUT);
        assertNotNull("Contacts can't be found", contacts);
        contacts.clickAndWait(Until.newWindow(), TIMEOUT);
        // Find a contact by a given contact-name
        UiObject2 contactName = mDevice.wait(Until.findObject(
            By.res(RES_PACKAGE_NAME, "cliv_name_textview").text(CONTACT_NAME)), TIMEOUT);
        assertNotNull("Contactname can't be found", contactName);
        contactName.clickAndWait(Until.newWindow(), TIMEOUT);
        // Click on dial-icon beside contact-number to ensure test is ready to be executed
        UiObject2 contactNumber = mDevice.wait(Until.findObject(
            By.res(RES_PACKAGE_NAME2,"header").text(CONTACT_NUMBER)), TIMEOUT);
        assertNotNull("Contact number can't be found", contactNumber);
        contactNumber.clickAndWait(Until.newWindow(), TIMEOUT);

        UiObject2 endCall = mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME,
              "floating_end_call_action_button")), 2 * TIMEOUT);
        endCall.clickAndWait(Until.newWindow(), TIMEOUT);;
        SystemClock.sleep(200);
    }

    @JankTest(beforeTest="launchDialer", expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testDialerCallInit() {
        for (int i = 0; i < INNER_LOOP; i++) {
            UiObject2 contactNumber = mDevice.wait(Until.findObject(
                    By.res(RES_PACKAGE_NAME2,"header").text(CONTACT_NUMBER)), TIMEOUT);
            assertNotNull("Contact number can't be found", contactNumber);
            contactNumber.clickAndWait(Until.newWindow(), TIMEOUT);
            UiObject2 endCall = mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME,
                      "floating_end_call_action_button")), 2 * TIMEOUT);
            endCall.clickAndWait(Until.newWindow(), TIMEOUT);
            SystemClock.sleep(200);
        }
    }

    public void launchCallLog() throws UiObjectNotFoundException {
        if (getCallLogCount() < 100) {
            for (int i = 0; i < 100; i++) {
                addNumToCalLog(getRandomPhoneNumber());
            }
        }
        launchApp(PACKAGE_NAME);
        mDevice.waitForIdle();
        // Find 'Call History' and click
        mDevice.wait(Until.findObject(By.desc("Call History")), TIMEOUT).click();
        mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME,"lists_pager")), TIMEOUT);
    }

    @JankTest(beforeTest="launchCallLog", expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testDialerCallLogFling() {
        UiObject2 callLog = mDevice.wait(Until.findObject(
                By.res(RES_PACKAGE_NAME,"lists_pager")), TIMEOUT);
        assertNotNull("Call log can't be found", callLog);
        for (int i = 0; i < INNER_LOOP; i++) {
            callLog.fling(Direction.DOWN);
            SystemClock.sleep(100);
            callLog.fling(Direction.UP);
            SystemClock.sleep(100);
        }
    }

    // Method to insert a new contact
    public void insertNewContacts() throws OperationApplicationException, RemoteException {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int rawContactID = ops.size();
        // to insert a new raw contact in the table ContactsContract.RawContacts
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "Test")
                .withValue(RawContacts.ACCOUNT_NAME, CONTACT_NAME)
                .build());

        // to insert display name in the table ContactsContract.Data
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactID)
                .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, CONTACT_NAME)
                .build());

        // to insert Mobile Number in the table ContactsContract.Data
        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactID)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, CONTACT_NUMBER)
                .withValue(Phone.TYPE, CommonDataKinds.Phone.TYPE_MOBILE)
                .build());

            // Executing all the insert operations as a single database transaction
        getInstrumentation().getContext().getContentResolver()
                .applyBatch(ContactsContract.AUTHORITY, ops);
    }

    // Checks whether certain contact exists or not
    public boolean doesContactExist() {
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(CONTACT_NUMBER));
        Cursor contactLookup = getInstrumentation().getContext().getContentResolver().query(
                uri, new String[] {
                        BaseColumns._ID,
                        ContactsContract.PhoneLookup.DISPLAY_NAME },
                        null,
                        null,
                        null);
        boolean found = false;
        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                if (contactLookup.getString(contactLookup.getColumnIndex(
                            ContactsContract.Data.DISPLAY_NAME)).equals(CONTACT_NAME))
                    found = true;
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return found;
    }

    // Inserts a new entry in the call log
    public void addNumToCalLog(String number){
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, number);
        values.put(CallLog.Calls.DATE, System.currentTimeMillis());
        values.put(CallLog.Calls.DURATION, 0);
        values.put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE);
        values.put(CallLog.Calls.NEW, 1);
        values.put(CallLog.Calls.CACHED_NAME, "");
        values.put(CallLog.Calls.CACHED_NUMBER_TYPE, 0);
        values.put(CallLog.Calls.CACHED_NUMBER_LABEL, "");
        getInstrumentation().getContext().getContentResolver()
                .insert(CallLog.Calls.CONTENT_URI, values);
    }

    // Gets call log count
    public int getCallLogCount() {
       Cursor cursor = getInstrumentation().getContext().getContentResolver()
               .query(CallLog.Calls.CONTENT_URI, null, null, null, null);
       return cursor.getCount();
    }

    // Generates a random phone number
    public String getRandomPhoneNumber() {
        Random rand = new Random();
        int num1 = (rand.nextInt(7) + 1) * 100 + (rand.nextInt(8) * 10) + rand.nextInt(8);
        int num2 = rand.nextInt(743);
        int num3 = rand.nextInt(10000);

        return String.format("%03d-%03d-%04d", num1, num2, num3);
    }
}
