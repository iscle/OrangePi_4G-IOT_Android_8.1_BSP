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
import android.telecom.PhoneAccountHandle;
import android.telephony.VisualVoicemailService.VisualVoicemailTask;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.voicemail.CtsVisualVoicemailService.Callback;

/**
 * This test ask the tester to remove this SIM, set the CTS verifier as the default dialer and
 * reinsert then remove the SIM again. The test will pass if the verifier is able to receive the new
 * service connected and SIM removed events with the {@link android.telephony.VisualVoicemailService}.
 */
public class VisualVoicemailServiceActivity extends PassFailButtons.Activity {

    private DefaultDialerChanger mDefaultDialerChanger;

    private ImageView mSetDefaultDialerImage;
    private ImageView mRestoreDefaultDialerImage;

    private ImageView mRemoveSimBeforeTestImage;
    private TextView mRemoveSimBeforeTestText;
    private Button mRemoveSimBeforeTestOkButton;
    private Button mRemoveSimBeforeTestNAButton;

    private TextView mInsertSimText;
    private ImageView mInsertSimImage;
    private TextView mRemoveSimText;
    private ImageView mRemoveSimImage;

    private boolean mConnectedReceived;
    private boolean mSimRemovalReceived;

    private Callback mCallback = new Callback() {
        @Override
        public void onCellServiceConnected(VisualVoicemailTask task,
                PhoneAccountHandle phoneAccountHandle) {
            mConnectedReceived = true;
            mInsertSimImage.setImageDrawable(getDrawable(R.drawable.fs_good));
            mInsertSimText.setText(getText(R.string.visual_voicemail_service_insert_sim_received));
            checkPassed();
        }

        @Override
        public void onSimRemoved(VisualVoicemailTask task, PhoneAccountHandle phoneAccountHandle) {
            mSimRemovalReceived = true;
            mRemoveSimImage.setImageDrawable(getDrawable(R.drawable.fs_good));
            mRemoveSimText.setText(getText(R.string.visual_voicemail_service_remove_sim_received));
            checkPassed();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CtsVisualVoicemailService.setCallback(mCallback);
        View view = getLayoutInflater().inflate(R.layout.visual_voicemail_service, null);
        setContentView(view);
        setInfoResources(R.string.visual_voicemail_service_test,
                R.string.visual_voicemail_service_instructions, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mDefaultDialerChanger = new DefaultDialerChanger(this);

        mSetDefaultDialerImage = (ImageView) findViewById(R.id.set_default_dialer_image);
        mRestoreDefaultDialerImage = (ImageView) findViewById(R.id.restore_default_dialer_image);

        mRemoveSimBeforeTestImage = (ImageView) findViewById(R.id.remove_sim_before_test_image);
        mRemoveSimBeforeTestText = (TextView) findViewById(R.id.remove_sim_before_test_text);
        mRemoveSimBeforeTestOkButton = (Button) findViewById(R.id.remove_sim_ok);
        mRemoveSimBeforeTestNAButton = (Button) findViewById(R.id.remove_sim_not_applicable);

        mInsertSimImage = (ImageView) findViewById(R.id.insert_sim_image);
        mInsertSimText = (TextView) findViewById(R.id.insert_sim_text);

        mRemoveSimImage = (ImageView) findViewById(R.id.remove_sim_image);
        mRemoveSimText = (TextView) findViewById(R.id.remove_sim_text);

        mRemoveSimBeforeTestNAButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getPassButton().setEnabled(true);
                mSetDefaultDialerImage.setImageDrawable(getDrawable(R.drawable.fs_warning));
                mRestoreDefaultDialerImage.setImageDrawable(getDrawable(R.drawable.fs_warning));
                mInsertSimImage.setImageDrawable(getDrawable(R.drawable.fs_warning));
                mRemoveSimImage.setImageDrawable(getDrawable(R.drawable.fs_warning));

                mRemoveSimBeforeTestOkButton.setEnabled(false);
            }
        });

        mRemoveSimBeforeTestOkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mRemoveSimBeforeTestImage.setImageDrawable(getDrawable(R.drawable.fs_good));
            }
        });
    }

    private void checkPassed() {
        if (mConnectedReceived && mSimRemovalReceived) {
            getPassButton().setEnabled(true);
            mDefaultDialerChanger.setRestorePending(true);
        }
    }

    @Override
    protected void onDestroy() {
        VoicemailBroadcastReceiver.setListener(null);
        mDefaultDialerChanger.destroy();
        CtsVisualVoicemailService.setCallback(null);
        super.onDestroy();
    }
}
