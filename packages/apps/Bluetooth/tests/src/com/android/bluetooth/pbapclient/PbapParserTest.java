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

package com.android.bluetooth.pbapclient;

import android.accounts.Account;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.CallLog.Calls;
import android.test.AndroidTestCase;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

public class PbapParserTest extends AndroidTestCase {
    private Account mAccount;
    private Resources testResources;
    private static final String mTestAccountName = "PBAPTESTACCOUNT";
    private static final String mTestPackageName = "com.android.bluetooth.tests";

    @Before
    public void setUp() {
        mAccount = new Account(mTestAccountName,
                mContext.getString(com.android.bluetooth.R.string.pbap_account_type));
        try {
            testResources =
                    mContext.getPackageManager().getResourcesForApplication(mTestPackageName);
        } catch (Exception e) {
            fail("Setup Failure Unable to get resources" + e.toString());
        }
    }

    // testNoTimestamp should parse 1 poorly formed vcard and not crash.
    @Test
    public void testNoTimestamp() throws IOException {
        InputStream fileStream;
        fileStream = testResources.openRawResource(
                com.android.bluetooth.tests.R.raw.no_timestamp_call_log);
        BluetoothPbapVcardList pbapVCardList = new BluetoothPbapVcardList(
                mAccount, fileStream, PbapClientConnectionHandler.VCARD_TYPE_30);
        assertEquals(1, pbapVCardList.getCount());
        CallLogPullRequest processor =
                new CallLogPullRequest(mContext, PbapClientConnectionHandler.MCH_PATH);
        processor.setResults(pbapVCardList.getList());

        // Verify that these entries aren't in the call log to start.
        assertFalse(verifyCallLog("555-0001", null, "3"));

        // Finish processing the data and verify entries were added to the call log.
        processor.onPullComplete();
        assertTrue(verifyCallLog("555-0001", null, "3"));
    }

    // testMissedCall should parse one phonecall correctly.
    @Test
    public void testMissedCall() throws IOException {
        InputStream fileStream;
        fileStream =
                testResources.openRawResource(com.android.bluetooth.tests.R.raw.single_missed_call);
        BluetoothPbapVcardList pbapVCardList = new BluetoothPbapVcardList(
                mAccount, fileStream, PbapClientConnectionHandler.VCARD_TYPE_30);
        assertEquals(1, pbapVCardList.getCount());
        CallLogPullRequest processor =
                new CallLogPullRequest(mContext, PbapClientConnectionHandler.MCH_PATH);
        processor.setResults(pbapVCardList.getList());

        // Verify that these entries aren't in the call log to start.
        // EST is default Time Zone
        assertFalse(verifyCallLog("555-0002", "1483250460000", "3"));
        // Finish processing the data and verify entries were added to the call log.
        processor.onPullComplete();
        assertTrue(verifyCallLog("555-0002", "1483250460000", "3"));
    }

    // testUnknownCall should parse two calls with no phone number.
    @Test
    public void testUnknownCall() throws IOException {
        InputStream fileStream;
        fileStream = testResources.openRawResource(
                com.android.bluetooth.tests.R.raw.unknown_number_call);
        BluetoothPbapVcardList pbapVCardList = new BluetoothPbapVcardList(
                mAccount, fileStream, PbapClientConnectionHandler.VCARD_TYPE_30);
        assertEquals(2, pbapVCardList.getCount());
        CallLogPullRequest processor =
                new CallLogPullRequest(mContext, PbapClientConnectionHandler.MCH_PATH);
        processor.setResults(pbapVCardList.getList());

        // Verify that these entries aren't in the call log to start.
        // EST is default Time Zone
        assertFalse(verifyCallLog("", "1483250520000", "3"));
        assertFalse(verifyCallLog("", "1483250580000", "3"));

        // Finish processing the data and verify entries were added to the call log.
        processor.onPullComplete();
        assertTrue(verifyCallLog("", "1483250520000", "3"));
        assertTrue(verifyCallLog("", "1483250580000", "3"));
    }

    // Find Entries in call log with type matching number and date.
    // If number or date is null it will match any number or date respectively.
    boolean verifyCallLog(String number, String date, String type) {
        String[] query = new String[] {Calls.NUMBER, Calls.DATE, Calls.TYPE};
        Cursor cursor = mContext.getContentResolver().query(Calls.CONTENT_URI, query,
                Calls.TYPE + "= " + type, null, Calls.DATE + ", " + Calls.NUMBER);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String foundNumber = cursor.getString(cursor.getColumnIndex(Calls.NUMBER));
                String foundDate = cursor.getString(cursor.getColumnIndex(Calls.DATE));
                if ((number == null || number.equals(foundNumber))
                        && (date == null || date.equals(foundDate))) {
                    return true;
                }
            }
            cursor.close();
        }
        return false;
    }
}
