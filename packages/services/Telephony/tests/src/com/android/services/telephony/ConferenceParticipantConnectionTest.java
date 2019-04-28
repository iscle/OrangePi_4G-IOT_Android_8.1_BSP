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
 * limitations under the License
 */

package com.android.services.telephony;

import android.net.Uri;
import android.support.test.runner.AndroidJUnit4;
import android.telecom.Conference;
import android.telecom.ConferenceParticipant;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.services.telephony.ConferenceParticipantConnection.getParticipantAddress;
import static org.junit.Assert.assertEquals;

/**
 * Tests proper parsing of conference event package participant addresses.
 */
@RunWith(AndroidJUnit4.class)
public class ConferenceParticipantConnectionTest {

    @Test
    public void testParticipantParseSimpleTel() {
        assertUrisEqual(Uri.parse("tel:+16505551212"),
                getParticipantAddress(Uri.parse("tel:6505551212"), "US"));
    }

    @Test
    public void testParticipantParseTelExtended() {
        assertUrisEqual(Uri.parse("tel:+16505551212"),
                getParticipantAddress(Uri.parse("tel:6505551212;phone-context=blah"), "US"));
    }

    @Test
    public void testParticipantParseSip() {
        assertUrisEqual(Uri.parse("tel:+16505551212"),
                getParticipantAddress(Uri.parse("sip:16505551212;phone-context=blah.com@host.com"),
                        "US"));
    }

    @Test
    public void testParticipantParseSip2() {
        assertUrisEqual(Uri.parse("tel:+12125551212"),
                getParticipantAddress(Uri.parse("sip:+1-212-555-1212@something.com;user=phone"),
                        "US"));
    }

    @Test
    public void testParticipantParseTelJp() {
        assertUrisEqual(Uri.parse("tel:+819066570660"),
                getParticipantAddress(Uri.parse(
                        "tel:09066570660;phone-context=ims.mnc020.mcc440.3gppnetwork.org"),
                        "JP"));
    }

    @Test
    public void testParticipantParseSipJp() {
        assertUrisEqual(Uri.parse("tel:+819066571180"),
                getParticipantAddress(Uri.parse(
                        "sip:+819066571180@ims.mnc020.mcc440.3gppnetwork.org;user=phone"),
                        "JP"));
    }

    private void assertUrisEqual(Uri expected, Uri actual) {
        assertEquals(expected.getScheme(), actual.getScheme());
        assertEquals(expected.getSchemeSpecificPart(), actual.getSchemeSpecificPart());
    }
}
