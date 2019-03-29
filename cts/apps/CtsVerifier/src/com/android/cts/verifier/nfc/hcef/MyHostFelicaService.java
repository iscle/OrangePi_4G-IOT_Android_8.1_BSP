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

package com.android.cts.verifier.nfc.hcef;

import android.content.Intent;
import android.nfc.cardemulation.HostNfcFService;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.verifier.nfc.hce.HceUtils;

public class MyHostFelicaService extends HostNfcFService {

    static final String TAG = "MyHostFelicaService";
    static byte[] NFCID2 = {0x02, (byte) 0xFE, 0x00, 0x00, 0x00, 0x00, 0x14, (byte)0x81};

    static byte CMD_REQUEST_SYSTEM_CODES = 0x0C;
    static byte RESPONSE_SYSTEM_CODES = 0x0D;
    static byte CMD_ECHO = (byte) 0xFE;
    static byte RESPONSE_ECHO = (byte) 0xFF;
    static byte CMD_SUCCESS = (byte) 0x81;

    static byte[] handleSystemCodeRequest(byte[] sc_request) {
        // Request system code command
        byte[] response = new byte[13];
        response[0] = 0x0D; // length
        response[1] = RESPONSE_SYSTEM_CODES; // get system codes resp
        System.arraycopy(sc_request, 2, response, 2, 8);
        response[10] = 0x01;
        response[11] = 0x40;
        response[12] = 0x01;
        return response;
    }

    static byte[] handleEchoRequest(byte[] echo_request) {
        byte[] response = new byte[echo_request.length];
        response[0] = (byte) echo_request.length;
        response[1] = RESPONSE_ECHO;
        for (int i = 2; i < echo_request.length; i++) {
            // Copy NFCID2 and rest of data
            response[i] = echo_request[i];
        }
        return response;

    }

    @Override
    public byte[] processNfcFPacket(byte[] bytes, Bundle bundle) {
        // Verify that NFCID2 matches with this service
        if (bytes.length < 2 + NFCID2.length) {
            Log.e(TAG, "Packet not long enough.");
            return null;
        }
        for (int i = 0; i < NFCID2.length; i++) {
           if (bytes[2 + i] != NFCID2[i]) {
               Log.e(TAG, "NFCID2 does not match.");
               return null;
           }
        }
        byte cmd = bytes[1];
        if (cmd == CMD_REQUEST_SYSTEM_CODES) {
            return handleSystemCodeRequest(bytes);
        } else if (cmd == CMD_ECHO) {
            return handleEchoRequest(bytes);
        } else if (cmd == CMD_SUCCESS) {
            // Mark the test a success
            Intent successIntent = new Intent(HceFEmulatorActivity.ACTION_TEST_SUCCESS);
            sendBroadcast(successIntent);
            // And just echo cmd back
            return bytes;
        } else {
            Log.e(TAG, "Invalid command received");
        }

        return null;
    }

    @Override
    public void onDeactivated(int i) {

    }
}
