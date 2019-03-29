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

package com.android.cts.verifier.managedprovisioning;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;

/**
 * Tests the Settings UI for always-on VPN in the work profile.
 */
public class AlwaysOnVpnSettingsTestActivity extends DialogTestListActivity {

    public static final String ACTION_ALWAYS_ON_VPN_SETTINGS_TEST =
            "com.android.cts.verifier.managedprovisioning.action.ALWAYS_ON_VPN_SETTINGS_TEST";

    private static final Intent VPN_SETTINGS_INTENT = new Intent(Settings.ACTION_VPN_SETTINGS);
    private static final String CTS_VPN_APP_PACKAGE = "com.android.cts.vpnfirewall";
    private static final String CTS_VPN_APP_ACTION =
            "com.android.cts.vpnfirewall.action.CONNECT_AND_FINISH";

    public AlwaysOnVpnSettingsTestActivity() {
        super(R.layout.provisioning_byod,
                R.string.provisioning_byod_always_on_vpn,
                R.string.provisioning_byod_always_on_vpn_info,
                R.string.provisioning_byod_always_on_vpn_instruction);
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_always_on_vpn_api23,
                "BYOD_AlwaysOnVpnSettingsApi23Test",
                R.string.provisioning_byod_always_on_vpn_api23_instruction,
                VPN_SETTINGS_INTENT
        ));
        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_always_on_vpn_api24,
                "BYOD_AlwaysOnVpnSettingsApi24Test",
                R.string.provisioning_byod_always_on_vpn_api24_instruction,
                VPN_SETTINGS_INTENT
        ));
        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_always_on_vpn_not_always_on,
                "BYOD_AlwaysOnVpnSettingsNotAlwaysOnTest",
                R.string.provisioning_byod_always_on_vpn_not_always_on_instruction,
                VPN_SETTINGS_INTENT
        ));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrepareTestButton.setText(
                R.string.provisioning_byod_always_on_vpn_prepare_button);
        mPrepareTestButton.setOnClickListener(v -> {
            try {
                final Intent intent =
                        getPackageManager().getLaunchIntentForPackage(CTS_VPN_APP_PACKAGE);
                intent.setAction(CTS_VPN_APP_ACTION);
                startActivity(intent);
            } catch (ActivityNotFoundException unused) {
                Utils.showToast(this, R.string.provisioning_byod_always_on_vpn_vpn_not_found_note);
            }
        });
    }
}
