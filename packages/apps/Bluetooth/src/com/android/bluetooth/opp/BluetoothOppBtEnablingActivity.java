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

package com.android.bluetooth.opp;

import com.android.bluetooth.R;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

/**
 * This class is designed to show BT enabling progress.
 */
public class BluetoothOppBtEnablingActivity extends AlertActivity {
    private static final String TAG = "BluetoothOppEnablingActivity";

    private static final boolean D = Constants.DEBUG;

    private static final boolean V = Constants.VERBOSE;

    private static final int BT_ENABLING_TIMEOUT = 0;

    private static final int BT_ENABLING_TIMEOUT_VALUE = 20000;

    private boolean mRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // If BT is already enabled jus return.
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled()) {
            finish();
            return;
        }

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        mRegistered = true;

        // Set up the "dialog"
        final AlertController.AlertParams p = mAlertParams;
        p.mTitle = getString(R.string.enabling_progress_title);
        p.mView = createView();
        setupAlert();

        // Add timeout for enabling progress
        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler.obtainMessage(BT_ENABLING_TIMEOUT),
                BT_ENABLING_TIMEOUT_VALUE);
    }

    private View createView() {
        View view = getLayoutInflater().inflate(R.layout.bt_enabling_progress, null);
        TextView contentView = (TextView)view.findViewById(R.id.progress_info);
        contentView.setText(getString(R.string.enabling_progress_content));

        return view;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (D) Log.d(TAG, "onKeyDown() called; Key: back key");
            mTimeoutHandler.removeMessages(BT_ENABLING_TIMEOUT);
            cancelSendingProgress();
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRegistered) {
            unregisterReceiver(mBluetoothReceiver);
        }
    }

    private final Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BT_ENABLING_TIMEOUT:
                    if (V) Log.v(TAG, "Received BT_ENABLING_TIMEOUT msg.");
                    cancelSendingProgress();
                    break;
                default:
                    break;
            }
        }
    };

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (V) Log.v(TAG, "Received intent: " + action) ;
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    case BluetoothAdapter.STATE_ON:
                        mTimeoutHandler.removeMessages(BT_ENABLING_TIMEOUT);
                        finish();
                        break;
                    default:
                        break;
                }
            }
        }
    };

    private void cancelSendingProgress() {
        BluetoothOppManager mOppManager = BluetoothOppManager.getInstance(this);
        if (mOppManager.mSendingFlag) {
            mOppManager.mSendingFlag = false;
        }
        finish();
    }
}
