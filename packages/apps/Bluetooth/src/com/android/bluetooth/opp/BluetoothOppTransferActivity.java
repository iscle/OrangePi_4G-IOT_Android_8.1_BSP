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
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.database.ContentObserver;
import android.widget.ProgressBar;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import android.app.NotificationManager;
import android.text.format.Formatter;

/**
 * Handle all transfer related dialogs: -Ongoing transfer -Receiving one file
 * dialog -Sending one file dialog -sending multiple files dialog -Complete
 * transfer -receive -receive success, will trigger corresponding handler
 * -receive fail dialog -send -send success dialog -send fail dialog -Other
 * dialogs - - DIALOG_RECEIVE_ONGOING will transition to
 * DIALOG_RECEIVE_COMPLETE_SUCCESS or DIALOG_RECEIVE_COMPLETE_FAIL
 * DIALOG_SEND_ONGOING will transition to DIALOG_SEND_COMPLETE_SUCCESS or
 * DIALOG_SEND_COMPLETE_FAIL
 */
public class BluetoothOppTransferActivity extends AlertActivity implements
        DialogInterface.OnClickListener {
    private static final String TAG = "BluetoothOppTransferActivity";
    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    private Uri mUri;

    // ongoing transfer-0 complete transfer-1
    boolean mIsComplete;

    private BluetoothOppTransferInfo mTransInfo;

    private ProgressBar mProgressTransfer;

    private TextView mPercentView;

    private AlertController.AlertParams mPara;

    private View mView = null;

    private TextView mLine1View, mLine2View, mLine3View, mLine5View;

    private int mWhichDialog;

    private BluetoothAdapter mAdapter;

    // Dialogs definition:
    // Receive progress dialog
    public static final int DIALOG_RECEIVE_ONGOING = 0;

    // Receive complete and success dialog
    public static final int DIALOG_RECEIVE_COMPLETE_SUCCESS = 1;

    // Receive complete and fail dialog: will display some fail reason
    public static final int DIALOG_RECEIVE_COMPLETE_FAIL = 2;

    // Send progress dialog
    public static final int DIALOG_SEND_ONGOING = 3;

    // Send complete and success dialog
    public static final int DIALOG_SEND_COMPLETE_SUCCESS = 4;

    // Send complete and fail dialog: will let user retry
    public static final int DIALOG_SEND_COMPLETE_FAIL = 5;

    /** Observer to get notified when the content observer's data changes */
    private BluetoothTransferContentObserver mObserver;

    // do not update button during activity creating, only update when db
    // changes after activity created
    private boolean mNeedUpdateButton = false;

    private class BluetoothTransferContentObserver extends ContentObserver {
        public BluetoothTransferContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            if (V) Log.v(TAG, "received db changes.");
            mNeedUpdateButton = true;
            updateProgressbar();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mUri = intent.getData();

        mTransInfo = new BluetoothOppTransferInfo();
        mTransInfo = BluetoothOppUtility.queryRecord(this, mUri);
        if (mTransInfo == null) {
            if (V) Log.e(TAG, "Error: Can not get data from db");
            finish();
            return;
        }

        mIsComplete = BluetoothShare.isStatusCompleted(mTransInfo.mStatus);

        displayWhichDialog();

        // update progress bar for ongoing transfer
        if (!mIsComplete) {
            mObserver = new BluetoothTransferContentObserver();
            getContentResolver().registerContentObserver(BluetoothShare.CONTENT_URI, true,
                    mObserver);
        }

        if (mWhichDialog != DIALOG_SEND_ONGOING && mWhichDialog != DIALOG_RECEIVE_ONGOING) {
            // set this record to INVISIBLE
            BluetoothOppUtility.updateVisibilityToHidden(this, mUri);
        }

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Set up the "dialog"
        setUpDialog();
    }

    @Override
    protected void onDestroy() {
        if (D) Log.d(TAG, "onDestroy()");

        if (mObserver != null) {
            getContentResolver().unregisterContentObserver(mObserver);
        }
        super.onDestroy();
    }

    private void displayWhichDialog() {
        int direction = mTransInfo.mDirection;
        boolean isSuccess = BluetoothShare.isStatusSuccess(mTransInfo.mStatus);
        boolean isComplete = BluetoothShare.isStatusCompleted(mTransInfo.mStatus);

        if (direction == BluetoothShare.DIRECTION_INBOUND) {
            if (isComplete == true) {
                if (isSuccess == true) {
                    // should not go here
                    mWhichDialog = DIALOG_RECEIVE_COMPLETE_SUCCESS;
                } else if (isSuccess == false) {
                    mWhichDialog = DIALOG_RECEIVE_COMPLETE_FAIL;
                }
            } else if (isComplete == false) {
                mWhichDialog = DIALOG_RECEIVE_ONGOING;
            }
        } else if (direction == BluetoothShare.DIRECTION_OUTBOUND) {
            if (isComplete == true) {
                if (isSuccess == true) {
                    mWhichDialog = DIALOG_SEND_COMPLETE_SUCCESS;

                } else if (isSuccess == false) {
                    mWhichDialog = DIALOG_SEND_COMPLETE_FAIL;
                }
            } else if (isComplete == false) {
                mWhichDialog = DIALOG_SEND_ONGOING;
            }
        }

        if (V) Log.v(TAG, " WhichDialog/dir/isComplete/failOrSuccess" + mWhichDialog + direction
                    + isComplete + isSuccess);
    }

    private void setUpDialog() {
        // final AlertController.AlertParams p = mAlertParams;
        mPara = mAlertParams;
        mPara.mTitle = getString(R.string.download_title);

        if ((mWhichDialog == DIALOG_RECEIVE_ONGOING) || (mWhichDialog == DIALOG_SEND_ONGOING)) {
            mPara.mPositiveButtonText = getString(R.string.download_ok);
            mPara.mPositiveButtonListener = this;
            mPara.mNegativeButtonText = getString(R.string.download_cancel);
            mPara.mNegativeButtonListener = this;
        } else if (mWhichDialog == DIALOG_RECEIVE_COMPLETE_SUCCESS) {
            mPara.mPositiveButtonText = getString(R.string.download_succ_ok);
            mPara.mPositiveButtonListener = this;
        } else if (mWhichDialog == DIALOG_RECEIVE_COMPLETE_FAIL) {
            mPara.mIconAttrId = android.R.attr.alertDialogIcon;
            mPara.mPositiveButtonText = getString(R.string.download_fail_ok);
            mPara.mPositiveButtonListener = this;
        } else if (mWhichDialog == DIALOG_SEND_COMPLETE_SUCCESS) {
            mPara.mPositiveButtonText = getString(R.string.upload_succ_ok);
            mPara.mPositiveButtonListener = this;
        } else if (mWhichDialog == DIALOG_SEND_COMPLETE_FAIL) {
            mPara.mIconAttrId = android.R.attr.alertDialogIcon;
            mPara.mPositiveButtonText = getString(R.string.upload_fail_ok);
            mPara.mPositiveButtonListener = this;
            mPara.mNegativeButtonText = getString(R.string.upload_fail_cancel);
            mPara.mNegativeButtonListener = this;
        }
        mPara.mView = createView();
        setupAlert();
    }

    private View createView() {

        mView = getLayoutInflater().inflate(R.layout.file_transfer, null);

        mProgressTransfer = (ProgressBar)mView.findViewById(R.id.progress_transfer);
        mPercentView = (TextView)mView.findViewById(R.id.progress_percent);

        customizeViewContent();

        // no need update button when activity creating
        mNeedUpdateButton = false;
        updateProgressbar();

        return mView;
    }

    /**
     * customize the content of view
     */
    private void customizeViewContent() {
        String tmp;

        if (mWhichDialog == DIALOG_RECEIVE_ONGOING
                || mWhichDialog == DIALOG_RECEIVE_COMPLETE_SUCCESS) {
            mLine1View = (TextView)mView.findViewById(R.id.line1_view);
            tmp = getString(R.string.download_line1, mTransInfo.mDeviceName);
            mLine1View.setText(tmp);
            mLine2View = (TextView)mView.findViewById(R.id.line2_view);
            tmp = getString(R.string.download_line2, mTransInfo.mFileName);
            mLine2View.setText(tmp);
            mLine3View = (TextView)mView.findViewById(R.id.line3_view);
            tmp = getString(R.string.download_line3, Formatter.formatFileSize(this,
                    mTransInfo.mTotalBytes));
            mLine3View.setText(tmp);
            mLine5View = (TextView)mView.findViewById(R.id.line5_view);
            if (mWhichDialog == DIALOG_RECEIVE_ONGOING) {
                tmp = getString(R.string.download_line5);
            } else if (mWhichDialog == DIALOG_RECEIVE_COMPLETE_SUCCESS) {
                tmp = getString(R.string.download_succ_line5);
            }
            mLine5View.setText(tmp);
        } else if (mWhichDialog == DIALOG_SEND_ONGOING
                || mWhichDialog == DIALOG_SEND_COMPLETE_SUCCESS) {
            mLine1View = (TextView)mView.findViewById(R.id.line1_view);
            tmp = getString(R.string.upload_line1, mTransInfo.mDeviceName);
            mLine1View.setText(tmp);
            mLine2View = (TextView)mView.findViewById(R.id.line2_view);
            tmp = getString(R.string.download_line2, mTransInfo.mFileName);
            mLine2View.setText(tmp);
            mLine3View = (TextView)mView.findViewById(R.id.line3_view);
            tmp = getString(R.string.upload_line3, mTransInfo.mFileType, Formatter.formatFileSize(
                    this, mTransInfo.mTotalBytes));
            mLine3View.setText(tmp);
            mLine5View = (TextView)mView.findViewById(R.id.line5_view);
            if (mWhichDialog == DIALOG_SEND_ONGOING) {
                tmp = getString(R.string.upload_line5);
            } else if (mWhichDialog == DIALOG_SEND_COMPLETE_SUCCESS) {
                tmp = getString(R.string.upload_succ_line5);
            }
            mLine5View.setText(tmp);
        } else if (mWhichDialog == DIALOG_RECEIVE_COMPLETE_FAIL) {
            if (mTransInfo.mStatus == BluetoothShare.STATUS_ERROR_SDCARD_FULL) {
                mLine1View = (TextView)mView.findViewById(R.id.line1_view);
                tmp = getString(R.string.bt_sm_2_1, mTransInfo.mDeviceName);
                mLine1View.setText(tmp);
                mLine2View = (TextView)mView.findViewById(R.id.line2_view);
                tmp = getString(R.string.download_fail_line2, mTransInfo.mFileName);
                mLine2View.setText(tmp);
                mLine3View = (TextView)mView.findViewById(R.id.line3_view);
                tmp = getString(R.string.bt_sm_2_2, Formatter.formatFileSize(this,
                        mTransInfo.mTotalBytes));
                mLine3View.setText(tmp);
            } else {
                mLine1View = (TextView)mView.findViewById(R.id.line1_view);
                tmp = getString(R.string.download_fail_line1);
                mLine1View.setText(tmp);
                mLine2View = (TextView)mView.findViewById(R.id.line2_view);
                tmp = getString(R.string.download_fail_line2, mTransInfo.mFileName);
                mLine2View.setText(tmp);
                mLine3View = (TextView)mView.findViewById(R.id.line3_view);
                tmp = getString(R.string.download_fail_line3, BluetoothOppUtility
                        .getStatusDescription(this, mTransInfo.mStatus, mTransInfo.mDeviceName));
                mLine3View.setText(tmp);
            }
            mLine5View = (TextView)mView.findViewById(R.id.line5_view);
            mLine5View.setVisibility(View.GONE);
        } else if (mWhichDialog == DIALOG_SEND_COMPLETE_FAIL) {
            mLine1View = (TextView)mView.findViewById(R.id.line1_view);
            tmp = getString(R.string.upload_fail_line1, mTransInfo.mDeviceName);
            mLine1View.setText(tmp);
            mLine2View = (TextView)mView.findViewById(R.id.line2_view);
            tmp = getString(R.string.upload_fail_line1_2, mTransInfo.mFileName);
            mLine2View.setText(tmp);
            mLine3View = (TextView)mView.findViewById(R.id.line3_view);
            tmp = getString(R.string.download_fail_line3, BluetoothOppUtility.getStatusDescription(
                    this, mTransInfo.mStatus, mTransInfo.mDeviceName));
            mLine3View.setText(tmp);
            mLine5View = (TextView)mView.findViewById(R.id.line5_view);
            mLine5View.setVisibility(View.GONE);
        }

        if (BluetoothShare.isStatusError(mTransInfo.mStatus)) {
            mProgressTransfer.setVisibility(View.GONE);
            mPercentView.setVisibility(View.GONE);
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                if (mWhichDialog == DIALOG_RECEIVE_COMPLETE_SUCCESS) {
                    // "Open" - open receive file
                    BluetoothOppUtility.openReceivedFile(this, mTransInfo.mFileName,
                            mTransInfo.mFileType, mTransInfo.mTimeStamp, mUri);

                    // make current transfer "hidden"
                    BluetoothOppUtility.updateVisibilityToHidden(this, mUri);

                    // clear correspondent notification item
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                            .cancel(mTransInfo.mID);
                } else if (mWhichDialog == DIALOG_SEND_COMPLETE_FAIL) {
                    // "try again"

                    // make current transfer "hidden"
                    BluetoothOppUtility.updateVisibilityToHidden(this, mUri);

                    // clear correspondent notification item
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                            .cancel(mTransInfo.mID);

                    // retry the failed transfer
                    BluetoothOppUtility.retryTransfer(this, mTransInfo);

                    BluetoothDevice remoteDevice = mAdapter.getRemoteDevice(mTransInfo.mDestAddr);

                    // Display toast message
                    Toast.makeText(
                            this,
                            this.getString(R.string.bt_toast_4, BluetoothOppManager.getInstance(
                                    this).getDeviceName(remoteDevice)), Toast.LENGTH_SHORT)
                            .show();

                } else if (mWhichDialog == DIALOG_SEND_COMPLETE_SUCCESS) {
                    BluetoothOppUtility.updateVisibilityToHidden(this, mUri);
                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                            .cancel(mTransInfo.mID);
                }
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                if (mWhichDialog == DIALOG_RECEIVE_ONGOING || mWhichDialog == DIALOG_SEND_ONGOING) {
                    // "Stop" button
                    this.getContentResolver().delete(mUri, null, null);

                    String msg = "";
                    if (mWhichDialog == DIALOG_RECEIVE_ONGOING) {
                        msg = getString(R.string.bt_toast_3, mTransInfo.mDeviceName);
                    } else if (mWhichDialog == DIALOG_SEND_ONGOING) {
                        msg = getString(R.string.bt_toast_6, mTransInfo.mDeviceName);
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                    ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                            .cancel(mTransInfo.mID);
                } else if (mWhichDialog == DIALOG_SEND_COMPLETE_FAIL) {

                    BluetoothOppUtility.updateVisibilityToHidden(this, mUri);
                }
                break;
        }
        finish();
    }

    /**
     * Update progress bar per data got from content provider
     */
    private void updateProgressbar() {
        mTransInfo = BluetoothOppUtility.queryRecord(this, mUri);
        if (mTransInfo == null) {
            if (V) Log.e(TAG, "Error: Can not get data from db");
            return;
        }

        // Set Transfer Max as 100. Percentage calculation would be done in setProgress API
        mProgressTransfer.setMax(100);

        if (mTransInfo.mTotalBytes != 0) {
            if (V) Log.v(TAG, "mCurrentBytes: " + mTransInfo.mCurrentBytes +
                " mTotalBytes: " + mTransInfo.mTotalBytes + " (" +
                (int)((mTransInfo.mCurrentBytes * 100) / mTransInfo.mTotalBytes) + "%)");
            mProgressTransfer.setProgress((int)((mTransInfo.mCurrentBytes * 100) /
                mTransInfo.mTotalBytes));
        } else {
            mProgressTransfer.setProgress(100);
        }

        mPercentView.setText(BluetoothOppUtility.formatProgressText(mTransInfo.mTotalBytes,
                mTransInfo.mCurrentBytes));

        // Handle the case when DIALOG_RECEIVE_ONGOING evolve to
        // DIALOG_RECEIVE_COMPLETE_SUCCESS/DIALOG_RECEIVE_COMPLETE_FAIL
        // Handle the case when DIALOG_SEND_ONGOING evolve to
        // DIALOG_SEND_COMPLETE_SUCCESS/DIALOG_SEND_COMPLETE_FAIL
        if (!mIsComplete && BluetoothShare.isStatusCompleted(mTransInfo.mStatus)
                && mNeedUpdateButton) {
            displayWhichDialog();
            updateButton();
            customizeViewContent();
        }
    }

    /**
     * Update button when one transfer goto complete from ongoing
     */
    private void updateButton() {
        if (mWhichDialog == DIALOG_RECEIVE_COMPLETE_SUCCESS) {
            mAlert.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
            mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setText(
                    getString(R.string.download_succ_ok));
        } else if (mWhichDialog == DIALOG_RECEIVE_COMPLETE_FAIL) {
            mAlert.setIcon(mAlert.getIconAttributeResId(android.R.attr.alertDialogIcon));
            mAlert.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
            mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setText(
                    getString(R.string.download_fail_ok));
        } else if (mWhichDialog == DIALOG_SEND_COMPLETE_SUCCESS) {
            mAlert.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
            mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setText(
                    getString(R.string.upload_succ_ok));
        } else if (mWhichDialog == DIALOG_SEND_COMPLETE_FAIL) {
            mAlert.setIcon(mAlert.getIconAttributeResId(android.R.attr.alertDialogIcon));
            mAlert.getButton(DialogInterface.BUTTON_POSITIVE).setText(
                    getString(R.string.upload_fail_ok));
            mAlert.getButton(DialogInterface.BUTTON_NEGATIVE).setText(
                    getString(R.string.upload_fail_cancel));
        }
    }
}
