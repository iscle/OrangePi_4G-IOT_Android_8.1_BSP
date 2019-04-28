/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.pbap;

import com.android.bluetooth.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputFilter.LengthFilter;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

/**
 * PbapActivity shows two dialogues: One for accepting incoming pbap request and
 * the other prompts the user to enter a session key for authentication with a
 * remote Bluetooth device.
 */
public class BluetoothPbapActivity extends AlertActivity implements
        DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener, TextWatcher {
    private static final String TAG = "BluetoothPbapActivity";

    private static final boolean V = BluetoothPbapService.VERBOSE;

    private static final int BLUETOOTH_OBEX_AUTHKEY_MAX_LENGTH = 16;

    private static final int DIALOG_YES_NO_AUTH = 1;

    private static final String KEY_USER_TIMEOUT = "user_timeout";

    private View mView;

    private EditText mKeyView;

    private TextView messageView;

    private String mSessionKey = "";

    private int mCurrentDialog;

    private Button mOkButton;

    private CheckBox mAlwaysAllowed;

    private boolean mTimeout = false;

    private boolean mAlwaysAllowedValue = true;

    private static final int DISMISS_TIMEOUT_DIALOG = 0;

    private static final int DISMISS_TIMEOUT_DIALOG_VALUE = 2000;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION.equals(intent.getAction())) {
                return;
            }
            onTimeout();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String action = i.getAction();
        if (action.equals(BluetoothPbapService.AUTH_CHALL_ACTION)) {
            showPbapDialog(DIALOG_YES_NO_AUTH);
            mCurrentDialog = DIALOG_YES_NO_AUTH;
        } else {
            Log.e(TAG, "Error: this activity may be started only with intent "
                    + "PBAP_ACCESS_REQUEST or PBAP_AUTH_CHALL ");
            finish();
        }
        registerReceiver(mReceiver, new IntentFilter(
                BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION));
    }

    private void showPbapDialog(int id) {
        final AlertController.AlertParams p = mAlertParams;
        switch (id) {
            case DIALOG_YES_NO_AUTH:
                p.mTitle = getString(R.string.pbap_session_key_dialog_header);
                p.mView = createView(DIALOG_YES_NO_AUTH);
                p.mPositiveButtonText = getString(android.R.string.ok);
                p.mPositiveButtonListener = this;
                p.mNegativeButtonText = getString(android.R.string.cancel);
                p.mNegativeButtonListener = this;
                setupAlert();
                mOkButton = mAlert.getButton(DialogInterface.BUTTON_POSITIVE);
                mOkButton.setEnabled(false);
                break;
            default:
                break;
        }
    }

    private String createDisplayText(final int id) {
        String mRemoteName = BluetoothPbapService.getRemoteDeviceName();
        switch (id) {
            case DIALOG_YES_NO_AUTH:
                String mMessage2 = getString(R.string.pbap_session_key_dialog_title, mRemoteName);
                return mMessage2;
            default:
                return null;
        }
    }

    private View createView(final int id) {
        switch (id) {
            case DIALOG_YES_NO_AUTH:
                mView = getLayoutInflater().inflate(R.layout.auth, null);
                messageView = (TextView)mView.findViewById(R.id.message);
                messageView.setText(createDisplayText(id));
                mKeyView = (EditText)mView.findViewById(R.id.text);
                mKeyView.addTextChangedListener(this);
                mKeyView.setFilters(new InputFilter[] {
                    new LengthFilter(BLUETOOTH_OBEX_AUTHKEY_MAX_LENGTH)
                });
                return mView;
            default:
                return null;
        }
    }

    private void onPositive() {
        if (!mTimeout) {
            if (mCurrentDialog == DIALOG_YES_NO_AUTH) {
                sendIntentToReceiver(BluetoothPbapService.AUTH_RESPONSE_ACTION,
                        BluetoothPbapService.EXTRA_SESSION_KEY, mSessionKey);
                mKeyView.removeTextChangedListener(this);
            }
        }
        mTimeout = false;
        finish();
    }

    private void onNegative() {
        if (mCurrentDialog == DIALOG_YES_NO_AUTH) {
            sendIntentToReceiver(BluetoothPbapService.AUTH_CANCELLED_ACTION, null, null);
            mKeyView.removeTextChangedListener(this);
        }
        finish();
    }

    private void sendIntentToReceiver(final String intentName, final String extraName,
            final String extraValue) {
        Intent intent = new Intent(intentName);
        intent.setPackage(BluetoothPbapService.THIS_PACKAGE_NAME);
        if (extraName != null) {
            intent.putExtra(extraName, extraValue);
        }
        sendBroadcast(intent);
    }

    private void sendIntentToReceiver(final String intentName, final String extraName,
            final boolean extraValue) {
        Intent intent = new Intent(intentName);
        intent.setPackage(BluetoothPbapService.THIS_PACKAGE_NAME);
        if (extraName != null) {
            intent.putExtra(extraName, extraValue);
        }
        sendBroadcast(intent);
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                if (mCurrentDialog == DIALOG_YES_NO_AUTH) {
                    mSessionKey = mKeyView.getText().toString();
                }
                onPositive();
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                onNegative();
                break;
            default:
                break;
        }
    }

    private void onTimeout() {
        mTimeout = true;
        if (mCurrentDialog == DIALOG_YES_NO_AUTH) {
            messageView.setText(getString(R.string.pbap_authentication_timeout_message,
                    BluetoothPbapService.getRemoteDeviceName()));
            mKeyView.setVisibility(View.GONE);
            mKeyView.clearFocus();
            mKeyView.removeTextChangedListener(this);
            mOkButton.setEnabled(true);
            mAlert.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
        }

        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler.obtainMessage(DISMISS_TIMEOUT_DIALOG),
                DISMISS_TIMEOUT_DIALOG_VALUE);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mTimeout = savedInstanceState.getBoolean(KEY_USER_TIMEOUT);
        if (V) Log.v(TAG, "onRestoreInstanceState() mTimeout: " + mTimeout);
        if (mTimeout) {
            onTimeout();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_USER_TIMEOUT, mTimeout);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    public void beforeTextChanged(CharSequence s, int start, int before, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(android.text.Editable s) {
        if (s.length() > 0) {
            mOkButton.setEnabled(true);
        }
    }

    private final Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISMISS_TIMEOUT_DIALOG:
                    if (V) Log.v(TAG, "Received DISMISS_TIMEOUT_DIALOG msg.");
                    finish();
                    break;
                default:
                    break;
            }
        }
    };
}
