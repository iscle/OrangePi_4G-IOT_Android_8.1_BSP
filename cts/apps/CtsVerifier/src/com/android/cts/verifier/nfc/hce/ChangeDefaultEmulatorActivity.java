package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

@TargetApi(19)
public class ChangeDefaultEmulatorActivity extends BaseEmulatorActivity {
    final static int STATE_IDLE = 0;
    final static int STATE_SERVICE1_SETTING_UP = 1;
    final static int STATE_SERVICE2_SETTING_UP = 2;
    final static int STATE_MAKING_SERVICE1_DEFAULT = 3;
    final static int STATE_MAKING_SERVICE2_DEFAULT = 4;
    final static int STATE_DEFAULT_CHANGED = 5;

    int mState = STATE_IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mState = STATE_SERVICE2_SETTING_UP;
        setupServices(this, PaymentService2.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    void onServicesSetup(boolean result) {
        if (mState == STATE_SERVICE2_SETTING_UP) {
            mState = STATE_SERVICE1_SETTING_UP;
            setupServices(this, PaymentService1.COMPONENT, PaymentService2.COMPONENT);
            return;
        }
        if (!makePaymentDefault(PaymentService2.COMPONENT,
                R.string.nfc_hce_change_preinstalled_wallet)) {
            // Service 2 is already default, make one default now
            mState = STATE_MAKING_SERVICE1_DEFAULT;
            makePaymentDefault(PaymentService1.COMPONENT, R.string.nfc_hce_change_default_help);
        } else {
            mState = STATE_MAKING_SERVICE2_DEFAULT;
            // will get callback when 2 is made default
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                PaymentService1.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                PaymentService1.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_change_default_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(PaymentService1.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }

    @Override
    void onPaymentDefaultResult(ComponentName component, boolean success) {
        if (mState == STATE_MAKING_SERVICE2_DEFAULT) {
            if (success) {
                mState = STATE_MAKING_SERVICE1_DEFAULT;
	            makePaymentDefault(PaymentService1.COMPONENT, R.string.nfc_hce_change_default_help);
            }
        } else if (mState == STATE_MAKING_SERVICE1_DEFAULT) {
            if (success) {
                mState = STATE_DEFAULT_CHANGED;
                NfcDialogs.createHceTapReaderDialog(this, null).show();
            }
        }
    }
}
