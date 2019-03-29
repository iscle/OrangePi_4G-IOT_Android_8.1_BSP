package com.android.cts.verifier.nfc.hce;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.android.cts.verifier.R;
import com.android.cts.verifier.nfc.NfcDialogs;

@TargetApi(19)
public class SinglePaymentEmulatorActivity extends BaseEmulatorActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_text);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setupServices(this, PaymentService1.COMPONENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    void onServicesSetup(boolean result) {
        // Verify HCE service 1 is the default
        if (makePaymentDefault(PaymentService1.COMPONENT,
                R.string.nfc_hce_change_preinstalled_wallet)) {
            // Wait for callback
        } else {
	        NfcDialogs.createHceTapReaderDialog(this, null).show();
        }
    }

    @Override
    void onPaymentDefaultResult(ComponentName component, boolean success) {
        if (success) {
	        NfcDialogs.createHceTapReaderDialog(this, null).show();
        }
    }

    public static Intent buildReaderIntent(Context context) {
        Intent readerIntent = new Intent(context, SimpleReaderActivity.class);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_APDUS,
                PaymentService1.APDU_COMMAND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_RESPONSES,
                PaymentService1.APDU_RESPOND_SEQUENCE);
        readerIntent.putExtra(SimpleReaderActivity.EXTRA_LABEL,
                context.getString(R.string.nfc_hce_single_payment_reader));
        return readerIntent;
    }

    @Override
    void onApduSequenceComplete(ComponentName component, long duration) {
        if (component.equals(PaymentService1.COMPONENT)) {
            getPassButton().setEnabled(true);
        }
    }
}
