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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

/**
 * This class is designed to show BT enable confirmation dialog;
 */
public class BluetoothOppBtEnableActivity extends AlertActivity implements
        DialogInterface.OnClickListener {
    private BluetoothOppManager mOppManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up the "dialog"
        mOppManager = BluetoothOppManager.getInstance(this);
        mOppManager.mSendingFlag = false;
        final AlertController.AlertParams p = mAlertParams;
        p.mIconAttrId = android.R.attr.alertDialogIcon;
        p.mTitle = getString(R.string.bt_enable_title);
        p.mView = createView();
        p.mPositiveButtonText = getString(R.string.bt_enable_ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(R.string.bt_enable_cancel);
        p.mNegativeButtonListener = this;
        setupAlert();
    }

    private View createView() {
        View view = getLayoutInflater().inflate(R.layout.confirm_dialog, null);
        TextView contentView = (TextView)view.findViewById(R.id.content);
        contentView.setText(getString(R.string.bt_enable_line1) + "\n\n"
                + getString(R.string.bt_enable_line2) + "\n");

        return view;
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                mOppManager.enableBluetooth(); // this is an asyn call
                mOppManager.mSendingFlag = true;

                Toast.makeText(this, getString(R.string.enabling_progress_content),
                        Toast.LENGTH_SHORT).show();

                Intent in = new Intent(this, BluetoothOppBtEnablingActivity.class);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(in);

                finish();
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                finish();
                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!mOppManager.mSendingFlag) {
            mOppManager.cleanUpSendingFileInfo();
        }
    }
}
