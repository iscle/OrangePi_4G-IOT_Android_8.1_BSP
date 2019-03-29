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

package com.android.cts.vpnfirewall;

public final class Rfc1071 {
    static int checksum(byte[] data, int length) {
        int sum = 0;

        // High bytes (even indices)
        for (int i = 0; i < length; i += 2) {
            sum += (data[i] & 0xFF) << 8;
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        // Low bytes (odd indices)
        for (int i = 1; i < length; i += 2) {
            sum += (data[i] & 0xFF);
            sum = (sum & 0xFFFF) + (sum >> 16);
        }

        // Fix any one's-complement errors- sometimes it is necessary to rotate twice.
        sum = (sum & 0xFFFF) + (sum >> 16);
        return sum ^ 0xFFFF;
    }
}
