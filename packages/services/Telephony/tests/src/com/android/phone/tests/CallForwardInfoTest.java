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

package com.android.phone.tests;

import android.support.test.runner.AndroidJUnit4;
import android.telephony.PhoneNumberUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests Related to CallForwardInfoTest
 */
@RunWith(AndroidJUnit4.class)
public class CallForwardInfoTest {

    @Test
    public void testCallForwardNumberResponses() {
        // Test numbers in correct formats
        assertNotNull(PhoneNumberUtils.formatNumber("+12345678900", Locale.US.getCountry()));
        assertNotNull(PhoneNumberUtils.formatNumber("123-456-7890", Locale.US.getCountry()));
        assertNotNull(PhoneNumberUtils.formatNumber("#123", Locale.US.getCountry()));
        assertNotNull(PhoneNumberUtils.formatNumber("*12", Locale.US.getCountry()));
        // Test invalid numbers
        assertNull(PhoneNumberUtils.formatNumber("a", Locale.US.getCountry()));
        assertNull(PhoneNumberUtils.formatNumber("a1", Locale.US.getCountry()));
    }
}
