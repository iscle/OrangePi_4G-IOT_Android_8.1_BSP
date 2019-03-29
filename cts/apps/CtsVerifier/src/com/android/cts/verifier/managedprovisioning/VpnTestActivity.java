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

package com.android.cts.verifier.managedprovisioning;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.net.VpnService.Builder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;

/**
 * Activity to test Vpn configuration
 */
public class VpnTestActivity extends PassFailButtons.Activity {

    public static final String ACTION_VPN = "com.android.cts.verifier.managedprovisioning.VPN";

    public static class MyTestVpnService extends VpnService {
        /*
         * MyVpnTestService is just a stub. This class exists because the framework needs a class
         * inside the app to refer back to, just using VpnService itself won't work.
         */
    }

    private ParcelFileDescriptor descriptor = null;
    private ComponentName mAdminReceiverComponent;
    private DevicePolicyManager mDevicePolicyManager;
    private UserManager mUserManager;
    private static final String TAG = "DeviceOwnerPositiveTestActivity";
    private static final int REQUEST_VPN_CODE = 54321;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.vpn_test);
        setPassFailButtonClickListeners();
        mAdminReceiverComponent = new ComponentName(this, DeviceAdminTestReceiver.class.getName());
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        mDevicePolicyManager.addUserRestriction(mAdminReceiverComponent,
                UserManager.DISALLOW_CONFIG_VPN);
        mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);
        testVpnEstablishFails();
    }

    @Override
    public void finish() {
        mDevicePolicyManager.clearUserRestriction(mAdminReceiverComponent,
                UserManager.DISALLOW_CONFIG_VPN);
        super.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int result, Intent data) {
        if (requestCode == REQUEST_VPN_CODE) {
            // We don't care about the result - ideally it should automatically cancel, but if
            // some custom component doesn't do that, try to establish the connection anyway
            // and see what happens.
            establishVpn();
        } else {
            Log.w(TAG, "Unexpected request code: " + requestCode);
        }
    }

    public void testVpnEstablishFails() {
        Intent newIntent = VpnService.prepare(this);
        if (newIntent != null) {
            startActivityForResult(newIntent, REQUEST_VPN_CODE);
        } else {
            establishVpn();
        }
    }

    public void establishVpn() {
        MyTestVpnService service = new MyTestVpnService();
        descriptor = service.new Builder().addAddress("8.8.8.8", 30).establish();
        if (descriptor == null) {
            // vpn connection not established, as expected, test case succeeds
            Log.i(TAG, "Test succeeded: descriptor is null");
            populateInfo(R.string.device_owner_no_vpn_connection);
            return;
        }
        // vpn connection established, not expected, test case fails
        Log.w(TAG, "vpn connection established, not expected, test case fails");
        try {
            descriptor.close();
            populateInfo(R.string.device_owner_vpn_connection);
        } catch (IOException e) {
            Log.i(TAG, "Closing vpn connection failed. Caught exception: ", e);
            populateInfo(R.string.device_owner_vpn_connection_close_failed);
        }
    }

    private void populateInfo(int messageId) {
        TextView vpnInfoTextView = (TextView) findViewById(R.id.device_owner_vpn_info);
        vpnInfoTextView.setText(getString(messageId));
    }

}
