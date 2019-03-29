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

package com.android.cts.vpnfirewall;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;

public class VpnClient extends Activity {

    public static final String ACTION_CONNECT_AND_FINISH =
            "com.android.cts.vpnfirewall.action.CONNECT_AND_FINISH";

    private static final int REQUEST_CONNECT = 0;
    private static final int REQUEST_CONNECT_AND_FINISH = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vpn_client);

        if (ACTION_CONNECT_AND_FINISH.equals(getIntent().getAction())) {
            prepareAndStart(REQUEST_CONNECT_AND_FINISH);
        }
        findViewById(R.id.connect).setOnClickListener(v -> prepareAndStart(REQUEST_CONNECT));
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (result == RESULT_OK) {
            startService(new Intent(this, ReflectorVpnService.class));
        }
        if (request == REQUEST_CONNECT_AND_FINISH) {
            finish();
        }
    }

    private void prepareAndStart(int requestCode) {
        Intent intent = VpnService.prepare(VpnClient.this);
        if (intent != null) {
            startActivityForResult(intent, requestCode);
        } else {
            onActivityResult(requestCode, RESULT_OK, null);
        }
    }
}
