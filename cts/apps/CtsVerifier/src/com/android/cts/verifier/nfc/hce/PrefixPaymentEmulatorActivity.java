package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

@TargetApi(19)
public class PrefixPaymentEmulatorActivity extends BaseEmulatorActivity {
    final static int STATE_IDLE = 0;
    final static int STATE_SERVICE1_SETTING_UP = 1;
    final static int STATE_SERVICE2_SETTING_UP = 2;
    final static int STATE_MAKING_SERVICE1_DEFAULT = 3;

    int mState = STATE_IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mState = STATE_SERVICE1_SETTING_UP;
        setupServices(this, PrefixPaymentService1.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    void onServicesSetup(boolean result) {
        if (mState == STATE_SERVICE1_SETTING_UP) {
            mState = STATE_SERVICE2_SETTING_UP;
            setupServices(this, PrefixPaymentService1.COMPONENT, PrefixPaymentService2.COMPONENT);
            return;
        }
        // Verify HCE service 1 is the default
        if (makePaymentDefault(PrefixPaymentService1.COMPONENT, R.string.nfc_hce_change_preinstalled_wallet)) {
            mState = STATE_MAKING_SERVICE1_DEFAULT;
        } else {
            // Already default
            NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_hce_payment_prefix_aids_help)).show();
        }
    }

    @Override
    void onPaymentDefaultResult(ComponentName component, boolean success) {
        if (success) {
            NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_hce_payment_prefix_aids_help)).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                PrefixPaymentService1.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                PrefixPaymentService1.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_payment_prefix_aids_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(PrefixPaymentService1.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }
}
