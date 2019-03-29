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

import android.service.diskstats.DiskStatsFreeSpaceProto;
import android.service.diskstats.DiskStatsServiceDumpProto;

/**
 * Test proto dump of diskstats
 */
public class DiskStatsProtoTest extends ProtoDumpTestCase {
    /**
     * Test that diskstats dump is reasonable
     *
     * @throws Exception
     */
    public void testDump() throws Exception {
        final DiskStatsServiceDumpProto dump = getDump(DiskStatsServiceDumpProto.parser(),
                "dumpsys diskstats --proto");

        // At least one partition listed
        assertTrue(dump.getPartitionsFreeSpaceCount() > 0);
        // Test latency
        boolean testError = dump.getHasTestError();
        if (testError) {
            assertNotNull(dump.getErrorMessage());
        } else {
            assertTrue(dump.getWrite512BLatencyMillis() < 100); // Less than 100ms
        }
        DiskStatsServiceDumpProto.EncryptionType encryptionType = dump.getEncryption();
        if ("file".equals(getDevice().getProperty("ro.crypto.type"))) {
            assertEquals(DiskStatsServiceDumpProto.EncryptionType.ENCRYPTION_FILE_BASED,
                    encryptionType);
        }
    }
}
