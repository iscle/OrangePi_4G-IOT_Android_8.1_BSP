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

import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Tests that a new {@link android.telecom.ConnectionService} be added and its associated
 * {@link android.telecom.PhoneAccount} enabled using the calling accounts settings screen.
 */
public class EnablePhoneAccountTestActivity extends PassFailButtons.Activity {

    private Button mRegisterPhoneAccount;
    private Button mConfirm;
    private ImageView mStep1Status;
    private ImageView mStep2Status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.telecom_enable_phone_account, null);
        setContentView(view);
        setInfoResources(R.string.telecom_enable_phone_account_test,
                R.string.telecom_enable_phone_account_info, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mRegisterPhoneAccount = (Button) view.findViewById(
                R.id.telecom_enable_phone_account_register_button);
        mRegisterPhoneAccount.setOnClickListener(v -> {
            PhoneAccountUtils.registerTestPhoneAccount(this);
            PhoneAccount account = PhoneAccountUtils.getPhoneAccount(this);
            if (account != null) {
                mConfirm.setEnabled(true);
                mRegisterPhoneAccount.setEnabled(false);
                mStep1Status.setImageResource(R.drawable.fs_good);
            } else {
                mStep1Status.setImageResource(R.drawable.fs_error);
            }
        });

        mConfirm = (Button) view.findViewById(R.id.telecom_enable_phone_account_confirm_button);
        mConfirm.setOnClickListener(v -> {
            PhoneAccount account = PhoneAccountUtils.getPhoneAccount(this);
            if (account != null && account.isEnabled()) {
                getPassButton().setEnabled(true);
                mStep2Status.setImageResource(R.drawable.fs_good);
                mConfirm.setEnabled(false);
                PhoneAccountUtils.unRegisterTestPhoneAccount(this);
            } else {
                mStep2Status.setImageResource(R.drawable.fs_error);
            }
        });
        mConfirm.setEnabled(false);

        mStep1Status = (ImageView) view.findViewById(R.id.step_1_status);
        mStep2Status = (ImageView) view.findViewById(R.id.step_2_status);
    }

}
