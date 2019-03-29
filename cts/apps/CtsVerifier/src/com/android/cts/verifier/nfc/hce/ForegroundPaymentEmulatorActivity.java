package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.nfc.cardemulation.CardEmulation;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

@TargetApi(19)
public class ForegroundPaymentEmulatorActivity extends BaseEmulatorActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mCardEmulation.categoryAllowsForegroundPreference(CardEmulation.CATEGORY_PAYMENT)) {
            // Launch tap&pay settings
            NfcDialogs.createChangeForegroundDialog(this).show();
        } else {
            setupServices(this, PaymentService2.COMPONENT, PaymentService1.COMPONENT);
        }
    }

    @Override
    void onServicesSetup(boolean result) {
        if (!makePaymentDefault(PaymentService1.COMPONENT,
                R.string.nfc_hce_change_preinstalled_wallet)) {
            mCardEmulation.setPreferredService(this, PaymentService2.COMPONENT);
            NfcDialogs.createHceTapReaderDialog(this, getString(R.string.nfc_hce_foreground_payment_help)).show();
        } // else, wait for callback
    }

    @Override
    void onPaymentDefaultResult(ComponentName component, boolean success) {
        if (success) {
            NfcDialogs.createHceTapReaderDialog(this, null).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mCardEmulation.unsetPreferredService(this);
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                PaymentService2.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                PaymentService2.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_foreground_payment_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(PaymentService2.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }
}
