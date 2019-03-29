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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.android.cts.verifier.R;

public class SetSupportMessageActivity extends Activity implements View.OnClickListener {
    public static final String ACTION_SET_SUPPORT_MSG =
            "com.android.cts.verifier.managedprovisioning.action.SET_SUPPORT_MSG";
    public static final String EXTRA_SUPPORT_MSG_TYPE =
            "com.android.cts.verifier.managedprovisioning.extra.SUPPORT_MSG_TYPE";

    public static final String TYPE_SHORT_MSG = "short-msg";
    public static final String TYPE_LONG_MSG = "long-msg";

    private String mType;
    private EditText mSupportMessage;
    private DevicePolicyManager mDpm;
    private ComponentName mAdmin;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_support_message);
        findViewById(R.id.set_default_message).setOnClickListener(this);
        findViewById(R.id.set_message).setOnClickListener(this);
        findViewById(R.id.clear_message).setOnClickListener(this);
        mSupportMessage = (EditText) findViewById(R.id.message_view);

        mType = getIntent().getStringExtra(EXTRA_SUPPORT_MSG_TYPE);
        setTitle(TYPE_SHORT_MSG.equals(mType)
                ? R.string.policy_transparency_short_support_msg_label
                : R.string.policy_transparency_long_support_msg_label);
        mDpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mAdmin = DeviceAdminTestReceiver.getReceiverComponentName();

        mSupportMessage.setText(TYPE_SHORT_MSG.equals(mType)
                ? mDpm.getShortSupportMessage(mAdmin)
                : mDpm.getLongSupportMessage(mAdmin));
    }

    @Override
    public void onClick(View view) {
        String message = null;
        switch (view.getId()) {
            case R.id.set_default_message: {
                message = getString(TYPE_SHORT_MSG.equals(mType)
                        ? R.string.policy_transparency_default_short_msg
                        : R.string.policy_transparency_default_long_msg);
            } break;
            case R.id.set_message: {
                message = mSupportMessage.getText().toString();
            } break;
            case R.id.clear_message: {
                message = null;
            } break;
        }
        if (TYPE_SHORT_MSG.equals(mType)) {
            mDpm.setShortSupportMessage(mAdmin, message);
        } else {
            mDpm.setLongSupportMessage(mAdmin, message);
        }
        mSupportMessage.setText(message);
    }
}