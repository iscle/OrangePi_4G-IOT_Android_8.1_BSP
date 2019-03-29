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

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.cardemulation.NfcFCardEmulation;
import android.os.Bundle;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class HceFEmulatorActivity extends PassFailButtons.Activity{
    static String ACTION_TEST_SUCCESS = "success";

    NfcAdapter mAdapter;
    NfcFCardEmulation mNfcFCardEmulation;

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_TEST_SUCCESS.equals(action)) {
                getPassButton().setEnabled(true);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mAdapter = NfcAdapter.getDefaultAdapter(this);
        mNfcFCardEmulation = NfcFCardEmulation.getInstance(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_TEST_SUCCESS);
        registerReceiver(mReceiver, filter);
        ComponentName hceFService = new ComponentName("com.android.cts.verifier",
                MyHostFelicaService.class.getName());
        mNfcFCardEmulation.enableService(this, hceFService);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

}
