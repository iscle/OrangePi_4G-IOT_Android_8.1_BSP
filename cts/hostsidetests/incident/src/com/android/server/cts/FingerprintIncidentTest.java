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

package com.android.server.cts;

import android.service.fingerprint.FingerprintActionStatsProto;
import android.service.fingerprint.FingerprintServiceDumpProto;
import android.service.fingerprint.FingerprintUserStatsProto;

import com.android.tradefed.log.LogUtil.CLog;


/**
 * Test to check that the fingerprint service properly outputs its dump state.
 */
public class FingerprintIncidentTest extends ProtoDumpTestCase {
    /**
     * Test that no fingerprints are registered.
     *
     * @throws Exception
     */
    public void testNoneRegistered() throws Exception {
        // If the device doesn't support fingerprints, then pass.
        if (!getDevice().hasFeature("android.hardware.fingerprint")) {
            CLog.d("Bypass as android.hardware.fingerprint is not supported.");
            return;
        }

        final FingerprintServiceDumpProto dump =
                getDump(FingerprintServiceDumpProto.parser(), "dumpsys fingerprint --proto");

        // One of them
        assertEquals(1, dump.getUsersCount());

        final FingerprintUserStatsProto userStats = dump.getUsers(0);
        assertEquals(0, userStats.getUserId());
        assertEquals(0, userStats.getNumFingerprints());

        final FingerprintActionStatsProto normal = userStats.getNormal();
        assertEquals(0, normal.getAccept());
        assertEquals(0, normal.getReject());
        assertEquals(0, normal.getAcquire());
        assertEquals(0, normal.getLockout());

        final FingerprintActionStatsProto crypto = userStats.getCrypto();
        assertEquals(0, crypto.getAccept());
        assertEquals(0, crypto.getReject());
        assertEquals(0, crypto.getAcquire());
        assertEquals(0, crypto.getLockout());
    }
}

