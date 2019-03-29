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
 * limitations under the License.
 */

package com.android.cts.deviceowner;

import java.lang.Character;

public class LockScreenInfoTest extends BaseDeviceOwnerTest {

    @Override
    public void tearDown() throws Exception {
        mDevicePolicyManager.setDeviceOwnerLockScreenInfo(getWho(), null);
        super.tearDown();
    }

    public void testSetAndGetLockInfo() {
        setLockInfo("testSetAndGet");
    }

    public void testClearLockInfo() {
        setLockInfo("testClear");
        setLockInfo(null);

    }

    public void testEmptyStringClearsLockInfo() {
        final String message = "";
        mDevicePolicyManager.setDeviceOwnerLockScreenInfo(getWho(), message);
        assertNull(mDevicePolicyManager.getDeviceOwnerLockScreenInfo());
    }

    public void testWhitespaceOnly() {
        setLockInfo("\t");
    }

    public void testUnicode() {
        final String smiley = new String(Character.toChars(0x1F601));
        final String phone = new String(Character.toChars(0x1F4F1));
        setLockInfo(smiley + phone + "\t" + phone + smiley);
    }

    public void testNullInString() {
        setLockInfo("does \0 this \1 work?");
    }

    public void testReasonablyLongString() {
        final int messageLength = 128;
        setLockInfo(new String(new char[messageLength]).replace('\0', 'Z'));
    }

    public void testSetLockInfoWithNullAdminFails() {
        final String message = "nulladmin";

        // Set message
        try {
            mDevicePolicyManager.setDeviceOwnerLockScreenInfo(null, message);
            fail("Exception should have been thrown for null admin ComponentName");
        } catch (NullPointerException expected) {
        }
    }

    /**
     * Sets device owner lock screen info on behalf of the current device owner admin.
     *
     * @throws AssertionError if the setting did not take effect.
     */
    private void setLockInfo(String message) {
        mDevicePolicyManager.setDeviceOwnerLockScreenInfo(getWho(), message);
        assertEquals(message, mDevicePolicyManager.getDeviceOwnerLockScreenInfo());
    }
}
