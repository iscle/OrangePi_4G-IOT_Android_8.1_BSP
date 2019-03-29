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

package android.telecom.cts;

import static android.telecom.Call.Details.*;

import android.telecom.Call;
import android.test.AndroidTestCase;

public class CallTest extends AndroidTestCase {

    public void testCapabilitiesCan() {
        assertTrue(Call.Details.can(CAPABILITY_HOLD, CAPABILITY_HOLD));
        assertTrue(Call.Details.can(CAPABILITY_MUTE, CAPABILITY_MUTE));
        assertTrue(Call.Details.can(CAPABILITY_HOLD | CAPABILITY_DISCONNECT_FROM_CONFERENCE,
                CAPABILITY_HOLD));
        assertTrue(Call.Details.can(CAPABILITY_MERGE_CONFERENCE
                | CAPABILITY_DISCONNECT_FROM_CONFERENCE | CAPABILITY_MUTE,
                CAPABILITY_MUTE));
        assertTrue(Call.Details.can(CAPABILITY_CAN_PAUSE_VIDEO, CAPABILITY_CAN_PAUSE_VIDEO));
        assertTrue(Call.Details.can(CAPABILITY_CAN_PULL_CALL, CAPABILITY_CAN_PULL_CALL));

        assertFalse(Call.Details.can(CAPABILITY_MUTE, CAPABILITY_HOLD));
        assertFalse(Call.Details.can(CAPABILITY_MERGE_CONFERENCE
                | CAPABILITY_DISCONNECT_FROM_CONFERENCE | CAPABILITY_MUTE,
                CAPABILITY_HOLD));
    }

    public void testCapabilitiesToString() {
        assertEquals("[Capabilities: CAPABILITY_HOLD]",
                Call.Details.capabilitiesToString(CAPABILITY_HOLD));
        assertEquals("[Capabilities: CAPABILITY_SUPPORT_HOLD]",
                Call.Details.capabilitiesToString(CAPABILITY_SUPPORT_HOLD));
        assertEquals("[Capabilities: CAPABILITY_MERGE_CONFERENCE]",
                Call.Details.capabilitiesToString(CAPABILITY_MERGE_CONFERENCE));
        assertEquals("[Capabilities: CAPABILITY_SWAP_CONFERENCE]",
                Call.Details.capabilitiesToString(CAPABILITY_SWAP_CONFERENCE));
        assertEquals("[Capabilities: CAPABILITY_RESPOND_VIA_TEXT]",
                Call.Details.capabilitiesToString(CAPABILITY_RESPOND_VIA_TEXT));
        assertEquals("[Capabilities: CAPABILITY_MUTE]",
                Call.Details.capabilitiesToString(CAPABILITY_MUTE));
        assertEquals("[Capabilities: CAPABILITY_MANAGE_CONFERENCE]",
                Call.Details.capabilitiesToString(CAPABILITY_MANAGE_CONFERENCE));

        final int capabilities = CAPABILITY_SUPPORTS_VT_LOCAL_RX
                | CAPABILITY_SUPPORTS_VT_LOCAL_TX
                | CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL
                | CAPABILITY_SUPPORTS_VT_REMOTE_RX
                | CAPABILITY_SUPPORTS_VT_REMOTE_TX
                | CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL
                | CAPABILITY_CAN_PAUSE_VIDEO;
        assertEquals("[Capabilities: "
                + "CAPABILITY_SUPPORTS_VT_LOCAL_RX "
                + "CAPABILITY_SUPPORTS_VT_LOCAL_TX "
                + "CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL "
                + "CAPABILITY_SUPPORTS_VT_REMOTE_RX "
                + "CAPABILITY_SUPPORTS_VT_REMOTE_TX "
                + "CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL "
                + "CAPABILITY_CAN_PAUSE_VIDEO"
                + "]",
                Call.Details.capabilitiesToString(capabilities));
    }

    public void testHasProperty() {
        assertTrue(Call.Details.hasProperty(PROPERTY_WIFI, PROPERTY_WIFI));
        assertTrue(Call.Details.hasProperty(PROPERTY_HIGH_DEF_AUDIO, PROPERTY_HIGH_DEF_AUDIO));
        assertTrue(Call.Details.hasProperty(PROPERTY_HIGH_DEF_AUDIO | PROPERTY_CONFERENCE
                | PROPERTY_WIFI, PROPERTY_CONFERENCE));
        assertTrue(Call.Details.hasProperty(PROPERTY_IS_EXTERNAL_CALL, PROPERTY_IS_EXTERNAL_CALL));

        assertFalse(Call.Details.hasProperty(PROPERTY_WIFI, PROPERTY_CONFERENCE));
        assertFalse(Call.Details.hasProperty(PROPERTY_HIGH_DEF_AUDIO | PROPERTY_CONFERENCE
                | PROPERTY_WIFI, PROPERTY_GENERIC_CONFERENCE));
    }

    public void testPropertiesToString() {
        assertEquals("[Properties: PROPERTY_CONFERENCE]",
                Call.Details.propertiesToString(PROPERTY_CONFERENCE));
        assertEquals("[Properties: PROPERTY_GENERIC_CONFERENCE]",
                Call.Details.propertiesToString(PROPERTY_GENERIC_CONFERENCE));
        assertEquals("[Properties: PROPERTY_EMERGENCY_CALLBACK_MODE]",
                Call.Details.propertiesToString(PROPERTY_EMERGENCY_CALLBACK_MODE));
        assertEquals("[Properties: PROPERTY_WIFI]",
                Call.Details.propertiesToString(PROPERTY_WIFI));
        assertEquals("[Properties: PROPERTY_HIGH_DEF_AUDIO]",
                Call.Details.propertiesToString(PROPERTY_HIGH_DEF_AUDIO));

        final int properties = PROPERTY_CONFERENCE
                | PROPERTY_WIFI
                | PROPERTY_HIGH_DEF_AUDIO;
        assertEquals("[Properties: "
                + "PROPERTY_CONFERENCE "
                + "PROPERTY_WIFI "
                + "PROPERTY_HIGH_DEF_AUDIO"
                + "]",
                Call.Details.propertiesToString(properties));
    }
}
