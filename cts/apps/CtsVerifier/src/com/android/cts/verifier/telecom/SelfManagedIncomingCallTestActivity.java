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

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.List;

/**
 * This test verifies functionality associated with the Self-Managed
 * {@link android.telecom.ConnectionService} APIs.  It ensures that Telecom will show an incoming
 * call UI when a new incoming self-managed call is added when there is already an ongoing managed
 * call or when there is an ongoing self-managed call in another app.
 */
public class SelfManagedIncomingCallTestActivity extends PassFailButtons.Activity {
    private Uri TEST_DIAL_NUMBER_1 = Uri.fromParts("tel", "6505551212", null);
    private Uri TEST_DIAL_NUMBER_2 = Uri.fromParts("tel", "4085551212", null);

    private ImageView mStep1Status;
    private Button mRegisterPhoneAccount;
    private ImageView mStep2Status;
    private Button mShowUi;
    private ImageView mStep3Status;
    private Button mConfirm;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.telecom_self_managed_answer, null);
        setContentView(view);
        setInfoResources(R.string.telecom_incoming_self_mgd_test,
                R.string.telecom_incoming_self_mgd_info, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mStep1Status = view.findViewById(R.id.step_1_status);
        mRegisterPhoneAccount = view.findViewById(R.id.telecom_incoming_self_mgd_register_button);
        mRegisterPhoneAccount.setOnClickListener(v -> {
            PhoneAccountUtils.registerTestSelfManagedPhoneAccount(this);
            PhoneAccount account = PhoneAccountUtils.getSelfManagedPhoneAccount(this);
            PhoneAccount account2 = PhoneAccountUtils.getSelfManagedPhoneAccount2(this);
            if (account != null &&
                    account.isEnabled() &&
                    account.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED) &&
                    account2 != null &&
                    account2.isEnabled() &&
                    account2.hasCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)) {
                mRegisterPhoneAccount.setEnabled(false);
                mShowUi.setEnabled(true);
                mStep1Status.setImageResource(R.drawable.fs_good);
            } else {
                mStep1Status.setImageResource(R.drawable.fs_error);
            }
        });

        mStep2Status = view.findViewById(R.id.step_2_status);
        mShowUi = view.findViewById(R.id.telecom_incoming_self_mgd_show_ui_button);
        mShowUi.setOnClickListener(v -> {
            (new AsyncTask<Void, Void, Throwable>() {
                @Override
                protected Throwable doInBackground(Void... params) {
                    try {
                        Bundle extras = new Bundle();
                        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                                TEST_DIAL_NUMBER_1);
                        TelecomManager telecomManager =
                                (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                        if (telecomManager == null) {
                            mStep2Status.setImageResource(R.drawable.fs_error);
                            return new Throwable("Could not get telecom service.");
                        }
                        telecomManager.addNewIncomingCall(
                                PhoneAccountUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_HANDLE, extras);

                        CtsConnectionService ctsConnectionService =
                                CtsConnectionService.waitForAndGetConnectionService();
                        if (ctsConnectionService == null) {
                            mStep2Status.setImageResource(R.drawable.fs_error);
                            return new Throwable("Could not get connection service.");
                        }

                        CtsConnection connection = ctsConnectionService.waitForAndGetConnection();
                        if (connection == null) {
                            mStep2Status.setImageResource(R.drawable.fs_error);
                            return new Throwable("Could not get connection.");
                        }
                        // Wait until the connection knows its audio state changed; at this point
                        // Telecom knows about the connection and can answer.
                        connection.waitForAudioStateChanged();
                        // Make it active to simulate an answer.
                        connection.setActive();

                        // Place the second call. It should trigger the incoming call UX.
                        Bundle extras2 = new Bundle();
                        extras2.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                                TEST_DIAL_NUMBER_2);
                        telecomManager.addNewIncomingCall(
                                PhoneAccountUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_HANDLE_2,
                                extras2);

                        return null;
                    } catch (Throwable t) {
                        return  t;
                    }
                }

                @Override
                protected void onPostExecute(Throwable t) {
                    if (t == null) {
                        mStep2Status.setImageResource(R.drawable.fs_good);
                        mShowUi.setEnabled(false);
                        mConfirm.setEnabled(true);
                    } else {
                        mStep2Status.setImageResource(R.drawable.fs_error);
                    }
                }
            }).execute();


        });

        mStep3Status = view.findViewById(R.id.step_3_status);
        mConfirm = view.findViewById(R.id.telecom_incoming_self_mgd_confirm_answer_button);
        mConfirm.setOnClickListener(v -> {
            CtsConnectionService ctsConnectionService = CtsConnectionService.getConnectionService();
            if (ctsConnectionService == null) {
                mStep3Status.setImageResource(R.drawable.fs_error);
                return;
            }
            List<CtsConnection> connections = ctsConnectionService.getConnections();
            if (connections.size() != 1) {
                mStep3Status.setImageResource(R.drawable.fs_error);
                return;
            }

            if (connections.get(0).getState() == Connection.STATE_ACTIVE) {
                connections
                        .stream()
                        .forEach((c) -> c.onDisconnect());
                mStep3Status.setImageResource(R.drawable.fs_good);
                getPassButton().setEnabled(true);
            } else {
                mStep3Status.setImageResource(R.drawable.fs_error);
            }

            PhoneAccountUtils.unRegisterTestSelfManagedPhoneAccount(this);
        });

        mShowUi.setEnabled(false);
        mConfirm.setEnabled(false);
    }
}
