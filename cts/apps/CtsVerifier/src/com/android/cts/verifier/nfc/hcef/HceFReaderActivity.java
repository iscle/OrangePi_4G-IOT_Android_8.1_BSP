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

import android.annotation.TargetApi;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.ReaderCallback;
import android.nfc.Tag;
import android.nfc.tech.NfcF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;

@TargetApi(24)
public class HceFReaderActivity extends PassFailButtons.Activity implements ReaderCallback,
        OnItemSelectedListener {
    public static final String TAG = "HceFReaderActivity";

    NfcAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mAdapter = NfcAdapter.getDefaultAdapter(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.enableReaderMode(this, this, NfcAdapter.FLAG_READER_NFC_F |
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
    }

    static byte[] createEchoCommand(byte[] nfcid2, byte[] payload) {
        byte length = (byte) (2 + nfcid2.length + payload.length);

        byte[] echo_cmd = new byte[length];
        echo_cmd[0] = length;
        echo_cmd[1] = MyHostFelicaService.CMD_ECHO;
        System.arraycopy(nfcid2, 0, echo_cmd, 2, nfcid2.length);
        System.arraycopy(payload, 0, echo_cmd, 2 + nfcid2.length, payload.length);
        return echo_cmd;
    }

    static byte[] createSuccessCommand(byte[] nfcid2) {
        byte[] cmd = new byte[2 + nfcid2.length];
        cmd[0] = (byte) (2 + nfcid2.length);
        cmd[1] = MyHostFelicaService.CMD_SUCCESS;
        System.arraycopy(nfcid2, 0, cmd, 2, nfcid2.length);
        return cmd;
    }

    static boolean verifyResponse(byte[] cmd, byte[] resp) {
        if (resp == null) return false;

        // Verify length
        if (resp[0] != resp.length) return false;
        if (resp.length != cmd.length) return false;
        // Verify cmd
        if (resp[1] != MyHostFelicaService.RESPONSE_ECHO) return false;

        // Verify rest of data
        for (int i = 2; i < resp.length; i++) {
            if (resp[i] != cmd[i]) return false;
        }

        return true;
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        NfcF felica = NfcF.get(tag);
        if (felica == null) return;

        try {
            felica.connect();
	    byte[] command =
		    new byte[]{6, 0x00, (byte)0x40, (byte)0x01, 0x00, 0x00}; // in this case, SC of HCE-F is 4001h
	    felica.transceive(command);
            for (int i = 0; i < 32; i++) {
                byte[] payload = new byte[] {0x14, (byte)i};
                byte[] echo_cmd = createEchoCommand(MyHostFelicaService.NFCID2, payload);
                byte[] resp = felica.transceive(echo_cmd);
                if (!verifyResponse(echo_cmd, resp)) {
                    Log.e(TAG, "Echo response not correct.");
                    return;
                }
            }
            // All successful, send success cmd
            byte[] success_cmd = createSuccessCommand(MyHostFelicaService.NFCID2);
            felica.transceive(success_cmd);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getPassButton().setEnabled(true);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "IOException, try again.");
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}
