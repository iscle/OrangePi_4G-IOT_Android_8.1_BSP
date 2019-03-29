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
package com.android.cts.deviceadmin;

import static android.app.admin.DevicePolicyManager.PASSWORD_QUALITY_COMPLEX;

import android.app.admin.DevicePolicyManager;

/**
 * Tests for {@link DevicePolicyManager#resetPassword} for complex cases.
 *
 * This needs to be run as device owner, because in NYC DA can't clear or change the password.
 */
public class DeviceOwnerPasswordTest extends BaseDeviceAdminTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertDeviceOwner();
        clearPassword();
    }

    @Override
    protected void tearDown() throws Exception {
        clearPassword();

        super.tearDown();
    }

    public void testPasswordQuality_something() {
        dpm.setPasswordQuality(mAdminComponent,
                DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_SOMETHING,
                dpm.getPasswordQuality(mAdminComponent));
        assertPasswordSufficiency(false);

        String caseDescription = "initial";
        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription); // can't change.
        assertPasswordSucceeds("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 10);
        caseDescription = "minimum password length = 10";
        assertEquals(10, dpm.getPasswordMinimumLength(mAdminComponent));
        assertPasswordSufficiency(true); // length not checked for this quality

        // TODO(ascull): fix resetPassword() logic so these succeed
        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 4);
        caseDescription = "minimum password length = 4";
        assertEquals(4, dpm.getPasswordMinimumLength(
                mAdminComponent));
        assertPasswordSufficiency(true);

        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("abcd1234", caseDescription);
    }

    public void testPasswordQuality_numeric() {
        dpm.setPasswordQuality(mAdminComponent,
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_NUMERIC,
                dpm.getPasswordQuality(mAdminComponent));
        assertPasswordSufficiency(false);            // failure

        String caseDescription = "initial";
        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 10);
        caseDescription = "minimum password length = 10";
        assertEquals(10, dpm.getPasswordMinimumLength(mAdminComponent));
        assertPasswordSufficiency(false);

        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 4);
        caseDescription = "minimum password length = 4";
        assertEquals(4, dpm.getPasswordMinimumLength(
                mAdminComponent));
        assertPasswordSufficiency(true);

        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("abcd1234", caseDescription);
    }

    public void testPasswordQuality_alphabetic() {
        dpm.setPasswordQuality(mAdminComponent,
                DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC,
                dpm.getPasswordQuality(mAdminComponent));
        assertPasswordSufficiency(false);

        String caseDescription = "initial";
        assertPasswordFails("1234", caseDescription);      // can't change
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 10);
        caseDescription = "minimum password length = 10";
        assertEquals(10, dpm.getPasswordMinimumLength(mAdminComponent));
        assertPasswordSufficiency(false);

        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 4);
        caseDescription = "minimum password length = 4";
        assertEquals(4, dpm.getPasswordMinimumLength(
                mAdminComponent));
        assertPasswordSufficiency(true);

        assertPasswordFails("1234", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("abcd1234", caseDescription);
    }

    public void testPasswordQuality_alphanumeric() {
        dpm.setPasswordQuality(mAdminComponent,
                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC);
        assertEquals(DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC,
                dpm.getPasswordQuality(mAdminComponent));
        assertPasswordSufficiency(false);

        String caseDescription = "initial";
        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("abcd", caseDescription);
        assertPasswordSucceeds("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 10);
        caseDescription = "minimum password length = 10";
        assertEquals(10, dpm.getPasswordMinimumLength(mAdminComponent));
        assertPasswordSufficiency(false);

        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("abcd1234", caseDescription);

        dpm.setPasswordMinimumLength(mAdminComponent, 4);
        caseDescription = "minimum password length = 4";
        assertEquals(4, dpm.getPasswordMinimumLength(
                mAdminComponent));
        assertPasswordSufficiency(true);

        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("abcd", caseDescription);
        assertPasswordSucceeds("abcd1234", caseDescription);
    }

    public void testPasswordQuality_complexUpperCase() {
        dpm.setPasswordQuality(mAdminComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, dpm.getPasswordQuality(mAdminComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum UpperCase=0";
        assertPasswordSucceeds("abc1", caseDescription);
        assertPasswordSucceeds("aBc1", caseDescription);
        assertPasswordSucceeds("ABC1", caseDescription);
        assertPasswordSucceeds("ABCD", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumUpperCase(mAdminComponent, 1);
        assertEquals(1, dpm.getPasswordMinimumUpperCase(mAdminComponent));
        caseDescription = "minimum UpperCase=1";
        assertPasswordFails("abc1", caseDescription);
        assertPasswordSucceeds("aBc1", caseDescription);
        assertPasswordSucceeds("ABC1", caseDescription);
        assertPasswordSucceeds("ABCD", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumUpperCase(mAdminComponent, 3);
        assertEquals(3, dpm.getPasswordMinimumUpperCase(mAdminComponent));
        caseDescription = "minimum UpperCase=3";
        assertPasswordFails("abc1", caseDescription);
        assertPasswordFails("aBC1", caseDescription);
        assertPasswordSucceeds("ABC1", caseDescription);
        assertPasswordSucceeds("ABCD", caseDescription);
        assertPasswordFails("123", caseDescription); // too short
    }

    public void testPasswordQuality_complexLowerCase() {
        dpm.setPasswordQuality(mAdminComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, dpm.getPasswordQuality(mAdminComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum LowerCase=0";
        assertPasswordSucceeds("ABCD", caseDescription);
        assertPasswordSucceeds("aBC1", caseDescription);
        assertPasswordSucceeds("abc1", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumLowerCase(mAdminComponent, 1);
        assertEquals(1, dpm.getPasswordMinimumLowerCase(mAdminComponent));
        caseDescription = "minimum LowerCase=1";
        assertPasswordFails("ABCD", caseDescription);
        assertPasswordSucceeds("aBC1", caseDescription);
        assertPasswordSucceeds("abc1", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumLowerCase(mAdminComponent, 3);
        assertEquals(3, dpm.getPasswordMinimumLowerCase(mAdminComponent));
        caseDescription = "minimum LowerCase=3";
        assertPasswordFails("ABCD", caseDescription);
        assertPasswordFails("aBC1", caseDescription);
        assertPasswordSucceeds("abc1", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordFails("123", caseDescription); // too short
    }

    public void testPasswordQuality_complexLetters() {
        dpm.setPasswordQuality(mAdminComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, dpm.getPasswordQuality(mAdminComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum Letters=0";
        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordSucceeds("a123", caseDescription);
        assertPasswordSucceeds("abc1", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumLetters(mAdminComponent, 1);
        assertEquals(1, dpm.getPasswordMinimumLetters(mAdminComponent));
        caseDescription = "minimum Letters=1";
        assertPasswordFails("1234", caseDescription);
        assertPasswordSucceeds("a123", caseDescription);
        assertPasswordSucceeds("abc1", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumLetters(mAdminComponent, 3);
        assertEquals(3, dpm.getPasswordMinimumLetters(mAdminComponent));
        caseDescription = "minimum Letters=3";
        assertPasswordFails("1234", caseDescription);
        assertPasswordFails("a123", caseDescription);
        assertPasswordSucceeds("abc1", caseDescription);
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordFails("123", caseDescription); // too short
    }

    public void testPasswordQuality_complexNumeric() {
        dpm.setPasswordQuality(mAdminComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, dpm.getPasswordQuality(mAdminComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum Numeric=0";
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("1abc", caseDescription);
        assertPasswordSucceeds("123a", caseDescription);
        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumNumeric(mAdminComponent, 1);
        assertEquals(1, dpm.getPasswordMinimumNumeric(mAdminComponent));
        caseDescription = "minimum Numeric=1";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordSucceeds("1abc", caseDescription);
        assertPasswordSucceeds("123a", caseDescription);
        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumNumeric(mAdminComponent, 3);
        assertEquals(3, dpm.getPasswordMinimumNumeric(mAdminComponent));
        caseDescription = "minimum Numeric=3";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("1abc", caseDescription);
        assertPasswordSucceeds("123a", caseDescription);
        assertPasswordSucceeds("1234", caseDescription);
        assertPasswordFails("123", caseDescription); // too short
    }

    public void testPasswordQuality_complexSymbols() {
        dpm.setPasswordQuality(mAdminComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, dpm.getPasswordQuality(mAdminComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum Symbols=0";
        assertPasswordSucceeds("abcd", caseDescription);
        assertPasswordSucceeds("_bc1", caseDescription);
        assertPasswordSucceeds("@#!1", caseDescription);
        assertPasswordSucceeds("_@#!", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumSymbols(mAdminComponent, 1);
        assertEquals(1, dpm.getPasswordMinimumSymbols(mAdminComponent));
        caseDescription = "minimum Symbols=1";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordSucceeds("_bc1", caseDescription);
        assertPasswordSucceeds("@#!1", caseDescription);
        assertPasswordSucceeds("_@#!", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumSymbols(mAdminComponent, 3);
        assertEquals(3, dpm.getPasswordMinimumSymbols(mAdminComponent));
        caseDescription = "minimum Symbols=3";
        assertPasswordFails("abcd", caseDescription);
        assertPasswordFails("_bc1", caseDescription);
        assertPasswordSucceeds("@#!1", caseDescription);
        assertPasswordSucceeds("_@#!", caseDescription);
        assertPasswordFails("123", caseDescription); // too short
    }

    public void testPasswordQuality_complexNonLetter() {
        dpm.setPasswordQuality(mAdminComponent, PASSWORD_QUALITY_COMPLEX);
        assertEquals(PASSWORD_QUALITY_COMPLEX, dpm.getPasswordQuality(mAdminComponent));
        resetComplexPasswordRestrictions();

        String caseDescription = "minimum NonLetter=0";
        assertPasswordSucceeds("Abcd", caseDescription);
        assertPasswordSucceeds("_bcd", caseDescription);
        assertPasswordSucceeds("3bcd", caseDescription);
        assertPasswordSucceeds("_@3c", caseDescription);
        assertPasswordSucceeds("_25!", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumNonLetter(mAdminComponent, 1);
        assertEquals(1, dpm.getPasswordMinimumNonLetter(mAdminComponent));
        caseDescription = "minimum NonLetter=1";
        assertPasswordFails("Abcd", caseDescription);
        assertPasswordSucceeds("_bcd", caseDescription);
        assertPasswordSucceeds("3bcd", caseDescription);
        assertPasswordSucceeds("_@3c", caseDescription);
        assertPasswordSucceeds("_25!", caseDescription);
        assertPasswordFails("123", caseDescription); // too short

        dpm.setPasswordMinimumNonLetter(mAdminComponent, 3);
        assertEquals(3, dpm.getPasswordMinimumNonLetter(mAdminComponent));
        caseDescription = "minimum NonLetter=3";
        assertPasswordFails("Abcd", caseDescription);
        assertPasswordFails("_bcd", caseDescription);
        assertPasswordFails("3bcd", caseDescription);
        assertPasswordSucceeds("_@3c", caseDescription);
        assertPasswordSucceeds("_25!", caseDescription);
        assertPasswordFails("123", caseDescription); // too short
    }

    private void assertPasswordFails(String password, String restriction) {
        try {
            boolean passwordResetResult = dpm.resetPassword(password, /* flags= */0);
            assertFalse("Password '" + password + "' should have failed on " + restriction,
                    passwordResetResult);
        } catch (IllegalArgumentException e) {
            // yesss, we have failed!
        }
    }

    private void assertPasswordSucceeds(String password, String restriction) {
        boolean passwordResetResult = dpm.resetPassword(password, /* flags= */0);
        assertTrue("Password '" + password + "' failed on " + restriction, passwordResetResult);
        assertPasswordSufficiency(true);
    }

    private void assertPasswordSufficiency(boolean expectPasswordSufficient) {
        int retries = 15;
        // isActivePasswordSufficient() gets the result asynchronously so let's retry a few times
        while (retries >= 0 && dpm.isActivePasswordSufficient() != expectPasswordSufficient) {
            retries--;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                break;
            }
        }
        assertEquals(expectPasswordSufficient, dpm.isActivePasswordSufficient());

    }
}
