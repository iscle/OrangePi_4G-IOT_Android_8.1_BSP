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
 * limitations under the License
 */

package com.android.cts.verifier.telecom;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.List;

/**
 * Tests that an outgoing call made from the default dialer on the system is able to connect to
 * the CtsConnectionService.
 */
public class OutgoingCallTestActivity extends PassFailButtons.Activity {

    private Button mRegisterAndEnablePhoneAccount;
    private Button mConfirmPhoneAccountEnabled;
    private Button mDialOutgoingCall;
    private Button mConfirmOutgoingCall;

    private ImageView mStep1Status;
    private ImageView mStep2Status;
    private ImageView mStep3Status;

    private Uri TEST_DIAL_NUMBER = Uri.fromParts("tel", "5551212", null);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.telecom_outgoing_call, null);
        setContentView(view);
        setInfoResources(R.string.telecom_outgoing_call_test,
                R.string.telecom_outgoing_call_test_info, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mRegisterAndEnablePhoneAccount = view.findViewById(
                R.id.telecom_outgoing_call_register_enable_phone_account_button);
        if (mRegisterAndEnablePhoneAccount == null) {
            finish();
        }
        mRegisterAndEnablePhoneAccount.setOnClickListener(v -> {
            PhoneAccountUtils.registerTestPhoneAccount(this);
            PhoneAccount account = PhoneAccountUtils.getPhoneAccount(this);
            if (account != null) {
                // Open the phone accounts screen to have the user set CtsConnectionService as
                // the default.
                Intent intent = new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS);
                startActivity(intent);

                mConfirmPhoneAccountEnabled.setEnabled(true);
            } else {
                mStep1Status.setImageResource(R.drawable.fs_error);
            }
        });

        mConfirmPhoneAccountEnabled = view.findViewById(R.id
                .telecom_outgoing_call_confirm_register_button);
        if (mConfirmPhoneAccountEnabled == null) {
            finish();
        }
        mConfirmPhoneAccountEnabled.setOnClickListener(v -> {
            PhoneAccount account = PhoneAccountUtils.getPhoneAccount(this);
            PhoneAccountHandle defaultOutgoingAccount = PhoneAccountUtils
                    .getDefaultOutgoingPhoneAccount(this) ;
            if (account != null && account.isEnabled() &&
                    PhoneAccountUtils.TEST_PHONE_ACCOUNT_HANDLE.equals(defaultOutgoingAccount)) {
                getPassButton().setEnabled(true);
                mStep1Status.setImageResource(R.drawable.fs_good);
                mConfirmPhoneAccountEnabled.setEnabled(false);
            } else {
                mStep1Status.setImageResource(R.drawable.fs_error);
            }
        });
        mConfirmPhoneAccountEnabled.setEnabled(false);

        mDialOutgoingCall = view.findViewById(R.id.telecom_outgoing_call_dial_button);
        if (mDialOutgoingCall == null) {
            finish();
        }
        mDialOutgoingCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(TEST_DIAL_NUMBER);
            startActivity(intent);
            mStep2Status.setImageResource(R.drawable.fs_good);
        });

        mConfirmOutgoingCall = view.findViewById(R.id.telecom_outgoing_call_confirm_button);
        if (mConfirmOutgoingCall == null) {
            finish();
        }
        mConfirmOutgoingCall.setOnClickListener(v -> {
            if (confirmOutgoingCall()) {
                mStep3Status.setImageResource(R.drawable.fs_good);
            } else {
                mStep3Status.setImageResource(R.drawable.fs_error);
            }
            PhoneAccountUtils.unRegisterTestPhoneAccount(this);
        });

        mStep1Status = view.findViewById(R.id.step_1_status);
        mStep2Status = view.findViewById(R.id.step_2_status);
        mStep3Status = view.findViewById(R.id.step_3_status);
        mStep1Status.setImageResource(R.drawable.fs_indeterminate);
        mStep2Status.setImageResource(R.drawable.fs_indeterminate);
        mStep3Status.setImageResource(R.drawable.fs_indeterminate);
    }

    private boolean confirmOutgoingCall() {
        if (CtsConnectionService.getConnectionService() == null) {
            return false;
        }
        List<CtsConnection> ongoingConnections =
                CtsConnectionService.getConnectionService().getConnections();
        if (ongoingConnections == null || ongoingConnections.size() != 1) {
            return false;
        }
        CtsConnection outgoingConnection = ongoingConnections.get(0);
        if (outgoingConnection.isIncomingCall()) {
            return false;
        }
        outgoingConnection.onDisconnect();
        return true;
    }
}
