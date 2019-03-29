/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.numberblocking.hostside;

import android.content.ContentValues;
import android.provider.BlockedNumberContract;

/**
 * Tests number blocking features as a privileged app that can block numbers.
 */
public class NumberBlockingAppTest extends BaseNumberBlockingClientTest {
    public void testCleanupBlockedNumberAsPrimaryUserSucceeds() throws Exception {
        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));

        assertTrue(mContext.getContentResolver().delete(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                new String[]{mBlockedPhoneNumber}) <= 1);
    }

    public void testBlockNumberAsPrimaryUserSucceeds() throws Exception {
        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));

        verifyInsertBlockedNumberSucceeds();
    }

    public void testSecondaryUserCannotBlockNumbers() throws Exception {
        assertFalse(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));

        try {
            BlockedNumberContract.isBlocked(mContext, mBlockedPhoneNumber);
            fail("Expected SecurityException.");
        } catch (SecurityException expected) {
        }
    }

    public void testUnblockNumberAsPrimaryUserSucceeds() throws Exception {
        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));

        assertEquals(1, mContext.getContentResolver().delete(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                new String[]{mBlockedPhoneNumber}));
    }

    private void verifyInsertBlockedNumberSucceeds() {
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, mBlockedPhoneNumber);
        mContext.getContentResolver().insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI, cv);
        assertTrue(BlockedNumberContract.isBlocked(mContext, mBlockedPhoneNumber));
    }
}
