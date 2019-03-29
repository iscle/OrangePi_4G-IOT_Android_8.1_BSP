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

package com.android.server.cts;

import android.service.notification.NotificationRecordProto;
import android.service.notification.NotificationServiceDumpProto;
import android.service.notification.State;
import android.service.notification.ZenMode;
import android.service.notification.ZenModeProto;

/**
 * Test to check that the notification service properly outputs its dump state.
 *
 * make -j32 CtsIncidentHostTestCases
 * cts-tradefed run singleCommand cts-dev -d --module CtsIncidentHostTestCases
 */
public class NotificationTest extends ProtoDumpTestCase {
    // These constants are those in PackageManager.
    public static final String FEATURE_WATCH = "android.hardware.type.watch";

    /**
     * Tests that at least one notification is posted, and verify its properties are plausible.
     */
    public void testNotificationRecords() throws Exception {
        final NotificationServiceDumpProto dump = getDump(NotificationServiceDumpProto.parser(),
                "dumpsys notification --proto");

        assertTrue(dump.getRecordsCount() > 0);
        boolean found = false;
        for (NotificationRecordProto record : dump.getRecordsList()) {
            if (record.getKey().contains("android")) {
                found = true;
                assertEquals(State.POSTED, record.getState());
                assertTrue(record.getImportance() > 0 /* NotificationManager.IMPORTANCE_NONE */);

                // Ensure these fields exist, at least
                record.getFlags();
                record.getChannelId();
                record.getSound();
                record.getSoundUsage();
                record.getCanVibrate();
                record.getCanShowLight();
                record.getGroupKey();
            }
            assertTrue(State.SNOOZED != record.getState());
        }

        assertTrue(found);
    }

    // Tests default state: zen mode off, no suppressors
    public void testZenMode() throws Exception {
        final NotificationServiceDumpProto dump = getDump(NotificationServiceDumpProto.parser(),
                "dumpsys notification --proto");
        ZenModeProto zenProto = dump.getZen();

        assertEquals(ZenMode.ZEN_MODE_OFF, zenProto.getZenMode());
        assertEquals(0, zenProto.getEnabledActiveConditionsCount());

        // b/64606626 Watches intentionally suppress notifications always
        if (!getDevice().hasFeature(FEATURE_WATCH)) {
            assertEquals(0, zenProto.getSuppressedEffects());
            assertEquals(0, zenProto.getSuppressorsCount());
        }

        zenProto.getPolicy();
    }
}
