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


package com.android.cts.verifier.voicemail;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.voicemail.VoicemailBroadcastReceiver.ReceivedListener;

/**
 * This test ask the tester to set the CTS verifier as the default dialer and leave a voicemail. The
 * test will pass if the verifier is able to receive a broadcast for the incoming voicemail. This
 * depends on telephony to send the broadcast to the default dialer when receiving a Message Waiting
 * Indicator SMS.
 */
public class VoicemailBroadcastActivity extends PassFailButtons.Activity {

    private ImageView mLeaveVoicemailImage;
    private TextView mLeaveVoicemailText;

    private DefaultDialerChanger mDefaultDialerChanger;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.voicemail_broadcast, null);
        setContentView(view);
        setInfoResources(R.string.voicemail_broadcast_test,
                R.string.voicemail_broadcast_instructions, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mLeaveVoicemailImage = (ImageView) findViewById(R.id.leave_voicemail_image);
        mLeaveVoicemailText = (TextView) findViewById(R.id.leave_voicemail_text);

        mDefaultDialerChanger = new DefaultDialerChanger(this);

        VoicemailBroadcastReceiver.setListener(new ReceivedListener() {
            @Override
            public void onReceived() {

                Toast.makeText(VoicemailBroadcastActivity.this,
                        R.string.voicemail_broadcast_received, Toast.LENGTH_SHORT).show();
                mLeaveVoicemailImage.setImageDrawable(getDrawable(R.drawable.fs_good));
                mLeaveVoicemailText.setText(R.string.voicemail_broadcast_received);
                getPassButton().setEnabled(true);
                mDefaultDialerChanger.setRestorePending(true);
            }
        });
    }

    @Override
    protected void onDestroy() {
        VoicemailBroadcastReceiver.setListener(null);
        mDefaultDialerChanger.destroy();
        super.onDestroy();
    }
}
